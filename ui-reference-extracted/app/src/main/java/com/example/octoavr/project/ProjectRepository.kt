package com.example.octoavr.project

import android.content.Context
import com.example.octoavr.model.AvrProject
import com.example.octoavr.model.BuildConfigSettings
import com.example.octoavr.model.OptimizationLevel
import com.example.octoavr.model.OutputFormat
import com.example.octoavr.model.ProjectFile
import com.example.octoavr.model.TargetMcu
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.UUID

class ProjectRepository(private val context: Context) {

    private val workspacesRoot: File
        get() = File(context.filesDir, "workspaces").apply { mkdirs() }

    private val metadataFileName = ".octoavr_project.json"

    suspend fun listProjects(): List<AvrProject> = withContext(Dispatchers.IO) {
        workspacesRoot.listFiles()
            ?.filter { it.isDirectory && File(it, metadataFileName).exists() }
            ?.mapNotNull { dir -> runCatching { readProjectFromDir(dir) }.getOrNull() }
            ?.sortedByDescending { it.updatedAtMs }
            ?: emptyList()
    }

    suspend fun loadOrCreateDefaultProject(preferredId: String?): AvrProject = withContext(Dispatchers.IO) {
        if (preferredId != null) {
            val dir = File(workspacesRoot, preferredId)
            if (dir.isDirectory && File(dir, metadataFileName).exists()) {
                runCatching { return@withContext readProjectFromDir(dir) }
            }
        }
        val existing = listProjects()
        if (existing.isNotEmpty()) {
            return@withContext existing.first()
        }
        createProjectFromTemplate(
            projectName = "ATmega32A_Blink",
            template = Atmega32aExamples.all.first()
        )
    }

    suspend fun loadProject(projectId: String): AvrProject? = withContext(Dispatchers.IO) {
        val dir = File(workspacesRoot, projectId)
        if (!dir.isDirectory || !File(dir, metadataFileName).exists()) return@withContext null
        runCatching { readProjectFromDir(dir) }.getOrNull()
    }

    suspend fun createProjectFromTemplate(
        projectName: String,
        template: ProjectExampleTemplate,
        buildConfig: BuildConfigSettings = BuildConfigSettings()
    ): AvrProject = withContext(Dispatchers.IO) {
        val safeName = sanitizeName(projectName).ifBlank { "AVR_Project" }
        val id = "${safeName.lowercase().replace(Regex("[^a-z0-9]+"), "_").trim('_')}_${UUID.randomUUID().toString().take(6)}"
        val projectDir = File(workspacesRoot, id).apply { mkdirs() }
        File(projectDir, "include").mkdirs()
        File(projectDir, "src").mkdirs()
        File(projectDir, "build").mkdirs()

        val projectFiles = template.files.map { (relPath, content) ->
            val file = File(projectDir, relPath)
            file.parentFile?.mkdirs()
            file.writeText(content)
            buildProjectFile(relPath, content, dirty = false)
        }.sortedWith(compareBy({ !it.relativePath.startsWith("main.") }, { it.relativePath }))

        val project = AvrProject(
            id = id,
            name = safeName,
            rootPath = projectDir.absolutePath,
            files = projectFiles,
            activeFilePath = template.defaultActiveFile.takeIf { path ->
                projectFiles.any { it.relativePath == path }
            } ?: projectFiles.first().relativePath,
            buildConfig = buildConfig,
            updatedAtMs = System.currentTimeMillis()
        )
        writeMetadata(projectDir, project)
        project
    }

    suspend fun createBlankProject(
        projectName: String,
        mcu: TargetMcu = TargetMcu.ATMEGA32A,
        clockHz: Long = mcu.defaultClockHz
    ): AvrProject {
        val template = ProjectExampleTemplate(
            id = "blank",
            title = "Blank ATmega32A",
            category = "Custom",
            summary = "Minimal AVR C entry point",
            pinsNote = "",
            defaultActiveFile = "main.c",
            files = mapOf(
                "main.c" to """
                    #ifndef F_CPU
                    #define F_CPU ${clockHz}UL
                    #endif

                    #include <avr/io.h>
                    #include <util/delay.h>

                    int main(void) {
                        DDRB |= (1 << PB0);

                        while (1) {
                            PORTB ^= (1 << PB0);
                            _delay_ms(500);
                        }
                        return 0;
                    }
                """.trimIndent() + "\n"
            )
        )
        return createProjectFromTemplate(
            projectName = projectName,
            template = template,
            buildConfig = BuildConfigSettings(targetMcu = mcu, cpuFrequencyHz = clockHz)
        )
    }

    suspend fun saveProject(project: AvrProject): AvrProject = withContext(Dispatchers.IO) {
        val dir = File(project.rootPath).apply { mkdirs() }
        val cleanFiles = project.files.map { pf ->
            val target = File(dir, pf.relativePath)
            target.parentFile?.mkdirs()
            target.writeText(pf.content)
            pf.copy(isDirty = false)
        }
        val updated = project.copy(
            files = cleanFiles,
            updatedAtMs = System.currentTimeMillis()
        )
        writeMetadata(dir, updated)
        updated
    }

    suspend fun saveProjectAs(sourceProject: AvrProject, newName: String): AvrProject = withContext(Dispatchers.IO) {
        val safeName = sanitizeName(newName).ifBlank { "${sourceProject.name}_Copy" }
        val newId = "${safeName.lowercase().replace(Regex("[^a-z0-9]+"), "_").trim('_')}_${UUID.randomUUID().toString().take(6)}"
        val newDir = File(workspacesRoot, newId).apply { mkdirs() }
        File(newDir, "include").mkdirs()
        File(newDir, "src").mkdirs()
        File(newDir, "build").mkdirs()

        val copiedFiles = sourceProject.files.map { pf ->
            val dest = File(newDir, pf.relativePath)
            dest.parentFile?.mkdirs()
            dest.writeText(pf.content)
            pf.copy(isDirty = false)
        }

        val cloned = sourceProject.copy(
            id = newId,
            name = safeName,
            rootPath = newDir.absolutePath,
            files = copiedFiles,
            updatedAtMs = System.currentTimeMillis(),
            lastElfPath = null,
            lastHexPath = null
        )
        writeMetadata(newDir, cloned)
        cloned
    }

    suspend fun renameProject(project: AvrProject, newName: String): AvrProject = withContext(Dispatchers.IO) {
        val safeName = sanitizeName(newName).ifBlank { project.name }
        val updated = project.copy(
            name = safeName,
            updatedAtMs = System.currentTimeMillis()
        )
        writeMetadata(File(project.rootPath), updated)
        updated
    }

    suspend fun deleteProject(projectId: String) = withContext(Dispatchers.IO) {
        val dir = File(workspacesRoot, projectId)
        if (dir.exists()) {
            dir.deleteRecursively()
        }
    }

    suspend fun addFile(
        project: AvrProject,
        relativePath: String,
        initialContent: String
    ): AvrProject = withContext(Dispatchers.IO) {
        val cleanRel = relativePath.trim().trimStart('/').replace("..", "")
        val dir = File(project.rootPath)
        val file = File(dir, cleanRel)
        file.parentFile?.mkdirs()
        file.writeText(initialContent)

        val newEntry = buildProjectFile(cleanRel, initialContent, dirty = false)
        val updatedFiles = (project.files.filterNot { it.relativePath == cleanRel } + newEntry)
            .sortedWith(compareBy({ !it.relativePath.startsWith("main.") }, { it.relativePath }))

        val updated = project.copy(
            files = updatedFiles,
            activeFilePath = cleanRel,
            updatedAtMs = System.currentTimeMillis()
        )
        writeMetadata(dir, updated)
        updated
    }

    suspend fun deleteFile(project: AvrProject, relativePath: String): AvrProject = withContext(Dispatchers.IO) {
        if (project.files.size <= 1) return@withContext project
        val dir = File(project.rootPath)
        val target = File(dir, relativePath)
        if (target.exists()) target.delete()

        val remaining = project.files.filterNot { it.relativePath == relativePath }
        val nextActive = if (project.activeFilePath == relativePath) {
            remaining.first().relativePath
        } else {
            project.activeFilePath
        }
        val updated = project.copy(
            files = remaining,
            activeFilePath = nextActive,
            updatedAtMs = System.currentTimeMillis()
        )
        writeMetadata(dir, updated)
        updated
    }

    suspend fun updateBuildConfig(project: AvrProject, config: BuildConfigSettings): AvrProject = withContext(Dispatchers.IO) {
        val updated = project.copy(
            buildConfig = config,
            updatedAtMs = System.currentTimeMillis()
        )
        writeMetadata(File(project.rootPath), updated)
        updated
    }

    suspend fun updateBuildOutputs(project: AvrProject, elfPath: String?, hexPath: String?): AvrProject = withContext(Dispatchers.IO) {
        val updated = project.copy(
            lastElfPath = elfPath ?: project.lastElfPath,
            lastHexPath = hexPath ?: project.lastHexPath,
            updatedAtMs = System.currentTimeMillis()
        )
        writeMetadata(File(project.rootPath), updated)
        updated
    }

    private fun readProjectFromDir(dir: File): AvrProject {
        val json = JSONObject(File(dir, metadataFileName).readText())
        val id = json.optString("id", dir.name)
        val name = json.optString("name", dir.name)
        val activeFile = json.optString("activeFilePath", "main.c")
        val updatedAt = json.optLong("updatedAtMs", dir.lastModified())
        val lastElf = json.optString("lastElfPath", "").takeIf { it.isNotBlank() && File(it).exists() }
        val lastHex = json.optString("lastHexPath", "").takeIf { it.isNotBlank() && File(it).exists() }

        val cfgJson = json.optJSONObject("buildConfig") ?: JSONObject()
        val includeDirs = mutableListOf<String>()
        cfgJson.optJSONArray("includeDirectories")?.let { arr ->
            for (i in 0 until arr.length()) includeDirs += arr.getString(i)
        }
        val extraSources = mutableListOf<String>()
        cfgJson.optJSONArray("additionalSourceFiles")?.let { arr ->
            for (i in 0 until arr.length()) extraSources += arr.getString(i)
        }

        val config = BuildConfigSettings(
            targetMcu = TargetMcu.fromGccMcu(cfgJson.optString("targetMcu", "atmega32a")),
            cpuFrequencyHz = cfgJson.optLong("cpuFrequencyHz", 8_000_000L),
            optimizationLevel = OptimizationLevel.fromFlag(cfgJson.optString("optimizationLevel", "-Os")),
            extraCFlags = cfgJson.optString("extraCFlags", "-Wall -Wextra -std=gnu11"),
            extraCppFlags = cfgJson.optString("extraCppFlags", "-Wall -Wextra -std=gnu++14 -fno-exceptions"),
            extraLdFlags = cfgJson.optString("extraLdFlags", ""),
            includeDebugInfo = cfgJson.optBoolean("includeDebugInfo", false),
            includeDirectories = includeDirs.ifEmpty { listOf("include") },
            additionalSourceFiles = extraSources,
            outputFormat = OutputFormat.fromName(cfgJson.optString("outputFormat", OutputFormat.ELF_AND_HEX.name)),
            generateDisassembly = cfgJson.optBoolean("generateDisassembly", false),
            verboseOutput = cfgJson.optBoolean("verboseOutput", false)
        )

        val discoveredFiles = mutableListOf<ProjectFile>()
        dir.walkTopDown()
            .filter { file ->
                file.isFile &&
                    !file.name.startsWith(".") &&
                    !file.relativeTo(dir).path.startsWith("build${File.separator}") &&
                    !file.relativeTo(dir).path.startsWith("build/")
            }
            .forEach { file ->
                val rel = file.relativeTo(dir).invariantSeparatorsPath
                if (rel.substringAfterLast('.').lowercase() in ALLOWED_EXTENSIONS) {
                    discoveredFiles += buildProjectFile(rel, file.readText(), dirty = false)
                }
            }

        val sorted = discoveredFiles.sortedWith(compareBy({ !it.relativePath.startsWith("main.") }, { it.relativePath }))
        val resolvedActive = sorted.firstOrNull { it.relativePath == activeFile }?.relativePath
            ?: sorted.firstOrNull()?.relativePath
            ?: "main.c"

        return AvrProject(
            id = id,
            name = name,
            rootPath = dir.absolutePath,
            files = sorted,
            activeFilePath = resolvedActive,
            buildConfig = config,
            updatedAtMs = updatedAt,
            lastElfPath = lastElf,
            lastHexPath = lastHex
        )
    }

    private fun writeMetadata(dir: File, project: AvrProject) {
        val cfg = project.buildConfig
        val cfgJson = JSONObject().apply {
            put("targetMcu", cfg.targetMcu.gccMcu)
            put("cpuFrequencyHz", cfg.cpuFrequencyHz)
            put("optimizationLevel", cfg.optimizationLevel.flag)
            put("extraCFlags", cfg.extraCFlags)
            put("extraCppFlags", cfg.extraCppFlags)
            put("extraLdFlags", cfg.extraLdFlags)
            put("includeDebugInfo", cfg.includeDebugInfo)
            put("includeDirectories", JSONArray(cfg.includeDirectories))
            put("additionalSourceFiles", JSONArray(cfg.additionalSourceFiles))
            put("outputFormat", cfg.outputFormat.name)
            put("generateDisassembly", cfg.generateDisassembly)
            put("verboseOutput", cfg.verboseOutput)
        }
        val root = JSONObject().apply {
            put("id", project.id)
            put("name", project.name)
            put("activeFilePath", project.activeFilePath)
            put("updatedAtMs", project.updatedAtMs)
            put("lastElfPath", project.lastElfPath ?: "")
            put("lastHexPath", project.lastHexPath ?: "")
            put("buildConfig", cfgJson)
        }
        File(dir, metadataFileName).writeText(root.toString(2))
    }

    private fun buildProjectFile(relPath: String, content: String, dirty: Boolean): ProjectFile {
        val name = relPath.substringAfterLast('/')
        val ext = name.substringAfterLast('.', "")
        return ProjectFile(
            relativePath = relPath,
            name = name,
            extension = ext,
            content = content,
            isDirty = dirty
        )
    }

    private fun sanitizeName(raw: String): String =
        raw.trim().replace(Regex("[^A-Za-z0-9_\\- ]"), "").take(40).trim()

    companion object {
        val ALLOWED_EXTENSIONS = setOf("c", "cpp", "cc", "cxx", "h", "hpp", "ino", "s")
    }
}
