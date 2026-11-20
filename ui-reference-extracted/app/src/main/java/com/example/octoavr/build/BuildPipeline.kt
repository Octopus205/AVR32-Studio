package com.example.octoavr.build

import com.example.octoavr.compiler.CompilationEngine
import com.example.octoavr.compiler.HexAndElfParser
import com.example.octoavr.model.AvrProject
import com.example.octoavr.model.BuildAction
import com.example.octoavr.model.BuildConfigSettings
import com.example.octoavr.model.BuildState
import com.example.octoavr.model.CompilerDiagnostic
import com.example.octoavr.model.ConsoleLineLevel
import com.example.octoavr.model.DiagnosticSeverity
import com.example.octoavr.model.HexValidationResult
import com.example.octoavr.model.MemoryUsage
import com.example.octoavr.model.ProjectFile
import com.example.octoavr.model.TargetMcu
import com.example.octoavr.toolchain.AvrToolchainManager
import java.io.File

class BuildPipeline(
    private val toolchainManager: AvrToolchainManager,
    private val engine: CompilationEngine
) {

    fun cancel() {
        engine.cancelActiveProcess()
    }

    suspend fun disassembleElf(elfFile: File, workingDir: File): Result<String> {
        if (!elfFile.exists()) {
            return Result.failure(IllegalStateException("ELF file does not exist. Build firmware first."))
        }
        val status = toolchainManager.ensureToolchainReady()
        val toolchain = toolchainManager.resolveToolchain()
        if (!status.isReady || toolchain == null || !toolchain.objdumpExecutable.exists()) {
            return Result.failure(IllegalStateException(status.errorDetail ?: "avr-objdump is unavailable"))
        }
        engine.resetCancellation()
        val dumpRes = engine.runTool(
            executable = toolchain.objdumpExecutable,
            args = listOf("-h", "-S", elfFile.absolutePath),
            workingDir = workingDir,
            toolchain = toolchain
        ) { }
        return if (dumpRes.exitCode == 0) {
            Result.success(dumpRes.outputLines.joinToString("\n"))
        } else {
            Result.failure(IllegalStateException("avr-objdump failed (exit ${dumpRes.exitCode})"))
        }
    }

    suspend fun execute(
        project: AvrProject,
        action: BuildAction,
        onStageChanged: (String) -> Unit,
        onLog: (String, ConsoleLineLevel, CompilerDiagnostic?) -> Unit
    ): BuildState {
        val startMs = System.currentTimeMillis()
        engine.resetCancellation()

        onStageChanged("Preparing toolchain…")
        val status = toolchainManager.ensureToolchainReady()
        val toolchain = toolchainManager.resolveToolchain()
        if (!status.isReady || toolchain == null) {
            val detail = status.errorDetail ?: "AVR toolchain unavailable"
            onLog("ERROR: $detail", ConsoleLineLevel.ERROR, null)
            return BuildState.Failure(
                action = action,
                durationMs = System.currentTimeMillis() - startMs,
                summary = detail,
                diagnostics = emptyList()
            )
        }

        onLog("Toolchain: ${status.gccVersion ?: status.modeLabel}", ConsoleLineLevel.INFO, null)
        onLog(
            "Target: ${project.buildConfig.targetMcu.displayName} (-mmcu=${project.buildConfig.targetMcu.gccMcu}) @ ${project.buildConfig.cpuFrequencyHz} Hz [${project.buildConfig.optimizationLevel.flag}]",
            ConsoleLineLevel.INFO,
            null
        )

        val projectDir = File(project.rootPath)
        val buildDir = File(projectDir, "build").apply { mkdirs() }
        val objDir = File(buildDir, "obj").apply {
            if (exists()) deleteRecursively()
            mkdirs()
        }
        val genDir = File(buildDir, "generated").apply {
            if (exists()) deleteRecursively()
            mkdirs()
        }

        val compilableFiles = collectCompilableSources(project, projectDir)
        if (compilableFiles.isEmpty()) {
            val msg = "No compilable source files (.c, .cpp, .ino, .s) found in project."
            onLog(msg, ConsoleLineLevel.ERROR, null)
            return BuildState.Failure(action, System.currentTimeMillis() - startMs, msg, emptyList())
        }

        val allDiagnostics = mutableListOf<CompilerDiagnostic>()
        val objectFiles = mutableListOf<File>()
        var usesCppLinker = false

        val sysrootArgs = buildSysrootCompilerArgs(toolchain, project.buildConfig)
        val includeArgs = buildIncludeArgs(projectDir, project.buildConfig)

        for ((index, sourceEntry) in compilableFiles.withIndex()) {
            if (engine.isCancelled()) {
                return BuildState.Cancelled(action, System.currentTimeMillis() - startMs)
            }

            val actualFile = prepareSourceFile(sourceEntry, genDir)
            val isCpp = actualFile.extension.lowercase() in setOf("cpp", "cc", "cxx")
            if (isCpp) usesCppLinker = true

            val compiler = if (isCpp) toolchain.gppExecutable else toolchain.gccExecutable
            val outObj = File(objDir, "${index}_${actualFile.nameWithoutExtension}.o")
            objectFiles += outObj

            onStageChanged("Compiling ${sourceEntry.relativePath} (${index + 1}/${compilableFiles.size})…")

            val args = mutableListOf<String>().apply {
                addAll(sysrootArgs)
                add("-c")
                add("-mmcu=${project.buildConfig.targetMcu.gccMcu}")
                add("-DF_CPU=${project.buildConfig.fCpuDefine}")
                add(project.buildConfig.optimizationLevel.flag)
                add("-ffunction-sections")
                add("-fdata-sections")
                if (project.buildConfig.includeDebugInfo) add("-g")
                addAll(parseFlags(if (isCpp) project.buildConfig.extraCppFlags else project.buildConfig.extraCFlags))
                addAll(includeArgs)
                if (project.buildConfig.verboseOutput) add("-v")
                add(actualFile.absolutePath)
                add("-o")
                add(outObj.absolutePath)
            }

            if (project.buildConfig.verboseOutput) {
                onLog("${compiler.name} ${args.joinToString(" ")}", ConsoleLineLevel.COMMAND, null)
            } else {
                onLog("Compiling ${sourceEntry.relativePath}…", ConsoleLineLevel.COMMAND, null)
            }

            val res = engine.runTool(
                executable = compiler,
                args = args,
                workingDir = projectDir,
                toolchain = toolchain
            ) { line ->
                val diags = HexAndElfParser.parseDiagnostics(listOf(line), projectDir.absolutePath)
                val diag = diags.firstOrNull()
                val level = when (diag?.severity) {
                    DiagnosticSeverity.ERROR -> ConsoleLineLevel.ERROR
                    DiagnosticSeverity.WARNING -> ConsoleLineLevel.WARNING
                    else -> ConsoleLineLevel.INFO
                }
                onLog(line, level, diag)
            }

            if (res.cancelled) {
                return BuildState.Cancelled(action, System.currentTimeMillis() - startMs)
            }

            val parsed = HexAndElfParser.parseDiagnostics(res.outputLines, projectDir.absolutePath)
            allDiagnostics += parsed

            if (res.exitCode != 0) {
                val errCount = allDiagnostics.count { it.severity == DiagnosticSeverity.ERROR }.coerceAtLeast(1)
                val summary = "Compilation failed with $errCount error(s) in ${sourceEntry.relativePath}."
                onLog(summary, ConsoleLineLevel.ERROR, null)
                return BuildState.Failure(
                    action = action,
                    durationMs = System.currentTimeMillis() - startMs,
                    summary = summary,
                    diagnostics = allDiagnostics
                )
            }
        }

        if (action == BuildAction.VERIFY) {
            val warnCount = allDiagnostics.count { it.severity == DiagnosticSeverity.WARNING }
            val duration = System.currentTimeMillis() - startMs
            onLog(
                "Verify succeeded in ${duration} ms ($warnCount warning(s)).",
                ConsoleLineLevel.SUCCESS,
                null
            )
            return BuildState.Success(
                action = action,
                durationMs = duration,
                diagnostics = allDiagnostics,
                memoryUsage = null,
                elfPath = null,
                hexPath = null
            )
        }

        onStageChanged("Linking ELF firmware…")
        val safeBase = project.name.replace(Regex("[^A-Za-z0-9_\\-]"), "_").ifBlank { "firmware" }
        val elfFile = File(buildDir, "$safeBase.elf")
        val hexFile = File(buildDir, "$safeBase.hex")
        val lssFile = File(buildDir, "$safeBase.lss")
        elfFile.delete()
        hexFile.delete()
        lssFile.delete()

        val linker = if (usesCppLinker) toolchain.gppExecutable else toolchain.gccExecutable
        val linkArgs = mutableListOf<String>().apply {
            addAll(sysrootArgs)
            addAll(buildSysrootLinkerArgs(toolchain, project.buildConfig.targetMcu))
            add("-mmcu=${project.buildConfig.targetMcu.gccMcu}")
            add(project.buildConfig.optimizationLevel.flag)
            add("-Wl,--gc-sections")
            addAll(parseFlags(project.buildConfig.extraLdFlags))
            addAll(objectFiles.map { it.absolutePath })
            add("-lm")
            add("-o")
            add(elfFile.absolutePath)
        }

        onLog("Linking ${elfFile.name}…", ConsoleLineLevel.COMMAND, null)
        val linkRes = engine.runTool(
            executable = linker,
            args = linkArgs,
            workingDir = projectDir,
            toolchain = toolchain
        ) { line ->
            val diags = HexAndElfParser.parseDiagnostics(listOf(line), projectDir.absolutePath)
            val diag = diags.firstOrNull()
            val level = if (diag?.severity == DiagnosticSeverity.ERROR) ConsoleLineLevel.ERROR else ConsoleLineLevel.INFO
            onLog(line, level, diag)
        }

        if (linkRes.cancelled) {
            return BuildState.Cancelled(action, System.currentTimeMillis() - startMs)
        }

        val linkDiags = HexAndElfParser.parseDiagnostics(linkRes.outputLines, projectDir.absolutePath)
        allDiagnostics += linkDiags

        if (linkRes.exitCode != 0 || !elfFile.exists()) {
            val summary = "Linker failed (exit ${linkRes.exitCode})."
            onLog(summary, ConsoleLineLevel.ERROR, null)
            return BuildState.Failure(
                action = action,
                durationMs = System.currentTimeMillis() - startMs,
                summary = summary,
                diagnostics = allDiagnostics
            )
        }

        if (!HexAndElfParser.isValidAvrElf(elfFile)) {
            val summary = "Generated ELF failed AVR ELF header validation (EM_AVR=83)."
            onLog(summary, ConsoleLineLevel.ERROR, null)
            return BuildState.Failure(
                action = action,
                durationMs = System.currentTimeMillis() - startMs,
                summary = summary,
                diagnostics = allDiagnostics
            )
        }

        var hexValidation: HexValidationResult? = null
        val emittedHexPath: String? = if (project.buildConfig.outputFormat.emitHex) {
            onStageChanged("Generating Intel HEX…")
            val objcopyArgs = listOf(
                "-O", "ihex",
                "-R", ".eeprom",
                elfFile.absolutePath,
                hexFile.absolutePath
            )
            onLog("Generating ${hexFile.name} (avr-objcopy -O ihex)…", ConsoleLineLevel.COMMAND, null)
            val objcopyRes = engine.runTool(
                executable = toolchain.objcopyExecutable,
                args = objcopyArgs,
                workingDir = projectDir,
                toolchain = toolchain
            ) { line ->
                onLog(line, ConsoleLineLevel.INFO, null)
            }
            if (objcopyRes.cancelled) {
                return BuildState.Cancelled(action, System.currentTimeMillis() - startMs)
            }
            if (objcopyRes.exitCode != 0 || !hexFile.exists()) {
                val summary = "avr-objcopy failed to generate ${hexFile.name}."
                onLog(summary, ConsoleLineLevel.ERROR, null)
                return BuildState.Failure(action, System.currentTimeMillis() - startMs, summary, allDiagnostics)
            }

            val validation = HexAndElfParser.validateIntelHex(hexFile)
            hexValidation = validation
            if (!validation.isValid) {
                val summary = "Intel HEX validation failed: ${validation.errorReason}"
                onLog(summary, ConsoleLineLevel.ERROR, null)
                return BuildState.Failure(action, System.currentTimeMillis() - startMs, summary, allDiagnostics)
            }
            onLog(
                "Intel HEX verified: ${validation.recordCount} records, ${validation.dataByteCount} firmware bytes.",
                ConsoleLineLevel.INFO,
                null
            )
            hexFile.absolutePath
        } else null

        var emittedLssPath: String? = null
        if (project.buildConfig.generateDisassembly && toolchain.objdumpExecutable.exists()) {
            onStageChanged("Generating disassembly…")
            val dumpRes = engine.runTool(
                executable = toolchain.objdumpExecutable,
                args = listOf("-h", "-S", elfFile.absolutePath),
                workingDir = projectDir,
                toolchain = toolchain
            ) { }
            if (dumpRes.exitCode == 0) {
                lssFile.writeText(dumpRes.outputLines.joinToString("\n"))
                emittedLssPath = lssFile.absolutePath
            }
        }

        onStageChanged("Measuring Flash & SRAM…")
        var memoryUsage: MemoryUsage? = null
        val sizeRes = engine.runTool(
            executable = toolchain.sizeExecutable,
            args = listOf(elfFile.absolutePath),
            workingDir = projectDir,
            toolchain = toolchain
        ) { line ->
            onLog(line, ConsoleLineLevel.INFO, null)
        }
        if (sizeRes.exitCode == 0) {
            memoryUsage = HexAndElfParser.parseAvrSizeOutput(
                sizeRes.outputLines.joinToString("\n"),
                project.buildConfig.targetMcu
            )
        }

        val emittedElfPath = if (project.buildConfig.outputFormat.emitElf) {
            elfFile.absolutePath
        } else {
            elfFile.delete()
            null
        }

        val duration = System.currentTimeMillis() - startMs
        val memSummary = memoryUsage?.let {
            "Flash: ${it.flashBytes}/${it.flashMaxBytes} B (${"%.1f".format(it.flashPercent)}%), " +
                "SRAM: ${it.sramBytes}/${it.sramMaxBytes} B (${"%.1f".format(it.sramPercent)}%)"
        } ?: "Memory usage unavailable"
        onLog("Build succeeded in ${duration} ms • $memSummary", ConsoleLineLevel.SUCCESS, null)

        return BuildState.Success(
            action = action,
            durationMs = duration,
            diagnostics = allDiagnostics,
            memoryUsage = memoryUsage,
            elfPath = emittedElfPath,
            hexPath = emittedHexPath,
            lssPath = emittedLssPath,
            hexValidation = hexValidation
        )
    }

    private fun collectCompilableSources(project: AvrProject, projectDir: File): List<ProjectFile> {
        val projectSources = project.files.filter { it.isCompilableSource }
        val extra = project.buildConfig.additionalSourceFiles
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .mapNotNull { rel ->
                val f = File(projectDir, rel)
                if (f.exists() && f.isFile) {
                    ProjectFile(rel, f.name, f.extension, f.readText(), false)
                } else null
            }
            .filter { extraFile -> projectSources.none { it.relativePath == extraFile.relativePath } }
        return projectSources + extra
    }

    private fun prepareSourceFile(source: ProjectFile, genDir: File): File {
        val diskFile = File(genDir.parentFile!!.parentFile!!, source.relativePath)
        if (source.extension.equals("ino", ignoreCase = true)) {
            val content = diskFile.readText()
            val wrapped = buildString {
                if (!content.contains("<avr/io.h>")) {
                    appendLine("#include <avr/io.h>")
                }
                if (!content.contains("<stdint.h>")) {
                    appendLine("#include <stdint.h>")
                }
                appendLine("#line 1 \"${source.relativePath}\"")
                append(content)
            }
            val cppOut = File(genDir, "${source.nameWithoutExt()}.cpp")
            cppOut.writeText(wrapped)
            return cppOut
        }
        return diskFile
    }

    private fun ProjectFile.nameWithoutExt(): String = name.substringBeforeLast('.')

    private fun buildSysrootCompilerArgs(
        toolchain: AvrToolchainManager.ResolvedToolchain,
        config: BuildConfigSettings
    ): List<String> {
        if (toolchain.isHostFallback) {
            val localInc = File(toolchain.sysrootDir, "avr/include")
            return if (localInc.exists()) listOf("-isystem", localInc.absolutePath) else emptyList()
        }
        val gccSysrootDir = File(toolchain.sysrootDir, "lib/gcc/avr/${AvrToolchainManager.GCC_VERSION}")
        val args = mutableListOf(
            "-B", "${toolchain.libexecGccDir.absolutePath}/",
            "-B", "${toolchain.avrBinDir.absolutePath}/"
        )
        if (gccSysrootDir.exists()) {
            args += listOf("-B", "${gccSysrootDir.absolutePath}/")
        }
        val avrInc = File(toolchain.sysrootDir, "avr/include")
        if (avrInc.exists()) {
            args += listOf("-isystem", avrInc.absolutePath)
        }
        val gccInc = File(gccSysrootDir, "include")
        if (gccInc.exists()) {
            args += listOf("-isystem", gccInc.absolutePath)
        }
        val gccFixedInc = File(gccSysrootDir, "include-fixed")
        if (gccFixedInc.exists()) {
            args += listOf("-isystem", gccFixedInc.absolutePath)
        }
        val cppInc = File(toolchain.sysrootDir, "avr/include/c++")
        if (cppInc.exists()) {
            args += listOf("-isystem", cppInc.absolutePath)
        }
        return args
    }

    private fun buildSysrootLinkerArgs(
        toolchain: AvrToolchainManager.ResolvedToolchain,
        targetMcu: TargetMcu
    ): List<String> {
        if (toolchain.isHostFallback) return emptyList()
        val args = mutableListOf<String>()
        val avrLibBase = File(toolchain.sysrootDir, "avr/lib")
        val gccLibBase = File(toolchain.sysrootDir, "lib/gcc/avr/${AvrToolchainManager.GCC_VERSION}")
        val ldScripts = File(toolchain.sysrootDir, "avr/lib/ldscripts")

        // Target-specific multilib directory first (e.g. avr5 for ATmega32A) so libc/libm/libgcc match MCU
        val avrArchDir = File(avrLibBase, targetMcu.avrArch)
        if (avrArchDir.exists()) {
            args += listOf("-B", "${avrArchDir.absolutePath}/", "-L", avrArchDir.absolutePath)
        }
        val gccArchDir = File(gccLibBase, targetMcu.avrArch)
        if (gccArchDir.exists()) {
            args += listOf("-B", "${gccArchDir.absolutePath}/", "-L", gccArchDir.absolutePath)
        }
        if (avrLibBase.exists()) {
            args += listOf("-B", "${avrLibBase.absolutePath}/", "-L", avrLibBase.absolutePath)
        }
        if (gccLibBase.exists()) {
            args += listOf("-B", "${gccLibBase.absolutePath}/", "-L", gccLibBase.absolutePath)
        }
        if (ldScripts.exists()) {
            args += listOf("-Wl,-L${ldScripts.absolutePath}")
        }
        return args
    }

    private fun buildIncludeArgs(projectDir: File, config: BuildConfigSettings): List<String> {
        val args = mutableListOf("-I", projectDir.absolutePath)
        config.includeDirectories
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .forEach { rel ->
                val dir = File(projectDir, rel)
                if (dir.exists() && dir.isDirectory) {
                    args += listOf("-I", dir.absolutePath)
                }
            }
        return args
    }

    private fun parseFlags(raw: String): List<String> =
        raw.trim().split(Regex("\\s+")).filter { it.isNotBlank() }
}
