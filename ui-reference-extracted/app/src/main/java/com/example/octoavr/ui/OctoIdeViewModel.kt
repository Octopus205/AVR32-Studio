package com.example.octoavr.ui

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.octoavr.build.BuildPipeline
import com.example.octoavr.compiler.CompilationEngine
import com.example.octoavr.files.FirmwareExportManager
import com.example.octoavr.hardware.UartConfig
import com.example.octoavr.hardware.UsbOtgDeviceInfo
import com.example.octoavr.hardware.UsbOtgInspector
import com.example.octoavr.model.AvrProject
import com.example.octoavr.model.BuildAction
import com.example.octoavr.model.BuildConfigSettings
import com.example.octoavr.model.BuildState
import com.example.octoavr.model.CompilerDiagnostic
import com.example.octoavr.model.ConsoleLineLevel
import com.example.octoavr.model.ConsoleLogLine
import com.example.octoavr.model.EditorSettings
import com.example.octoavr.model.TargetMcu
import com.example.octoavr.model.ToolchainStatus
import com.example.octoavr.project.ProjectExampleTemplate
import com.example.octoavr.project.ProjectRepository
import com.example.octoavr.settings.SettingsRepository
import com.example.octoavr.toolchain.AvrToolchainManager
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.File
import java.util.concurrent.atomic.AtomicLong

data class IdeUiState(
    val isInitializing: Boolean = true,
    val activeProject: AvrProject? = null,
    val allProjects: List<AvrProject> = emptyList(),
    val toolchainStatus: ToolchainStatus? = null,
    val buildState: BuildState = BuildState.Idle,
    val consoleLogs: List<ConsoleLogLine> = emptyList(),
    val isConsoleExpanded: Boolean = false,
    val editorSettings: EditorSettings = EditorSettings(),
    val highlightedErrorLine: Int? = null,
    val focusLineRequest: Pair<Int, Long>? = null,
    val snippetInsertRequest: Pair<String, Long>? = null,
    val disassemblyText: String? = null,
    val selectedDiagnosticIndex: Int = -1,
    val uartConfig: UartConfig = UartConfig(),
    val usbDevices: List<UsbOtgDeviceInfo> = emptyList(),
    val isUsbHostSupported: Boolean = false,
    val isZFlasherInstalled: Boolean = false,
    val statusBannerMessage: String? = null
) {
    val isCompiling: Boolean get() = buildState is BuildState.Running
    val hasUnsavedChanges: Boolean get() = activeProject?.files?.any { it.isDirty } == true
    val diagnostics: List<CompilerDiagnostic>
        get() = consoleLogs.mapNotNull { it.diagnostic }
}

class OctoIdeViewModel(application: Application) : AndroidViewModel(application) {

    private val settingsRepo = SettingsRepository(application)
    private val projectRepo = ProjectRepository(application)
    private val toolchainManager = AvrToolchainManager(application)
    private val compilationEngine = CompilationEngine()
    private val buildPipeline = BuildPipeline(toolchainManager, compilationEngine)
    private val exportManager = FirmwareExportManager(application)
    private val usbInspector = UsbOtgInspector(application)

    private val logIdCounter = AtomicLong(1L)
    private var activeBuildJob: Job? = null

    private val _uiState = MutableStateFlow(IdeUiState())
    val uiState: StateFlow<IdeUiState> = _uiState.asStateFlow()

    init {
        initializeIde()
        observeSettings()
    }

    private fun observeSettings() {
        viewModelScope.launch {
            settingsRepo.editorSettingsFlow.collect { editorCfg ->
                _uiState.update { it.copy(editorSettings = editorCfg) }
            }
        }
        viewModelScope.launch {
            settingsRepo.uartBaudRateFlow.collect { baud ->
                _uiState.update { it.copy(uartConfig = it.uartConfig.copy(baudRate = baud)) }
            }
        }
    }

    private fun initializeIde() {
        viewModelScope.launch {
            val preferredId = settingsRepo.getActiveProjectId()
            val active = projectRepo.loadOrCreateDefaultProject(preferredId)
            settingsRepo.setActiveProjectId(active.id)
            val all = projectRepo.listProjects()

            _uiState.update {
                it.copy(
                    isInitializing = false,
                    activeProject = active,
                    allProjects = all,
                    usbDevices = usbInspector.scanConnectedDevices(),
                    isUsbHostSupported = usbInspector.isUsbHostSupported(),
                    isZFlasherInstalled = exportManager.isZFlasherInstalled()
                )
            }

            val toolchain = toolchainManager.ensureToolchainReady()
            _uiState.update { it.copy(toolchainStatus = toolchain) }
            appendLog(
                if (toolchain.isReady) {
                    "OctoAVR32A ready • ${toolchain.gccVersion ?: toolchain.modeLabel}"
                } else {
                    "Toolchain notice: ${toolchain.errorDetail ?: "Not initialized"}"
                },
                if (toolchain.isReady) ConsoleLineLevel.INFO else ConsoleLineLevel.WARNING
            )
        }
    }

    fun clearBannerMessage() {
        _uiState.update { it.copy(statusBannerMessage = null) }
    }

    fun toggleConsole(expanded: Boolean? = null) {
        _uiState.update { state ->
            state.copy(isConsoleExpanded = expanded ?: !state.isConsoleExpanded)
        }
    }

    fun clearConsole() {
        _uiState.update { it.copy(consoleLogs = emptyList(), highlightedErrorLine = null) }
    }

    fun selectFile(relativePath: String) {
        val proj = _uiState.value.activeProject ?: return
        if (proj.files.none { it.relativePath == relativePath }) return
        _uiState.update {
            it.copy(
                activeProject = proj.copy(activeFilePath = relativePath),
                highlightedErrorLine = null
            )
        }
    }

    fun updateFileContent(relativePath: String, newContent: String) {
        val proj = _uiState.value.activeProject ?: return
        val updatedFiles = proj.files.map { pf ->
            if (pf.relativePath == relativePath && pf.content != newContent) {
                pf.copy(content = newContent, isDirty = true)
            } else {
                pf
            }
        }
        _uiState.update {
            it.copy(activeProject = proj.copy(files = updatedFiles))
        }
    }

    fun saveCurrentProject() {
        val proj = _uiState.value.activeProject ?: return
        viewModelScope.launch {
            val saved = projectRepo.saveProject(proj)
            val all = projectRepo.listProjects()
            _uiState.update {
                it.copy(
                    activeProject = saved,
                    allProjects = all,
                    statusBannerMessage = "Saved ${saved.name}"
                )
            }
        }
    }

    fun saveProjectAs(newName: String) {
        val proj = _uiState.value.activeProject ?: return
        viewModelScope.launch {
            val savedSource = projectRepo.saveProject(proj)
            val cloned = projectRepo.saveProjectAs(savedSource, newName)
            settingsRepo.setActiveProjectId(cloned.id)
            val all = projectRepo.listProjects()
            _uiState.update {
                it.copy(
                    activeProject = cloned,
                    allProjects = all,
                    buildState = BuildState.Idle,
                    statusBannerMessage = "Saved copy as ${cloned.name}"
                )
            }
        }
    }

    fun createNewBlankProject(name: String, mcu: TargetMcu, clockHz: Long) {
        viewModelScope.launch {
            _uiState.value.activeProject?.let { projectRepo.saveProject(it) }
            val created = projectRepo.createBlankProject(name, mcu, clockHz)
            settingsRepo.setActiveProjectId(created.id)
            val all = projectRepo.listProjects()
            _uiState.update {
                it.copy(
                    activeProject = created,
                    allProjects = all,
                    buildState = BuildState.Idle,
                    highlightedErrorLine = null,
                    disassemblyText = null,
                    selectedDiagnosticIndex = -1,
                    statusBannerMessage = "Created ${created.name}"
                )
            }
        }
    }

    fun createProjectFromExample(template: ProjectExampleTemplate) {
        viewModelScope.launch {
            _uiState.value.activeProject?.let { projectRepo.saveProject(it) }
            val created = projectRepo.createProjectFromTemplate(
                projectName = template.title.substringAfter(". ").replace(" ", "_").replace("/", "_"),
                template = template
            )
            settingsRepo.setActiveProjectId(created.id)
            val all = projectRepo.listProjects()
            _uiState.update {
                it.copy(
                    activeProject = created,
                    allProjects = all,
                    buildState = BuildState.Idle,
                    highlightedErrorLine = null,
                    disassemblyText = null,
                    selectedDiagnosticIndex = -1,
                    statusBannerMessage = "Loaded example: ${template.title}"
                )
            }
            appendLog("Loaded ATmega32A example: ${template.title}", ConsoleLineLevel.INFO)
        }
    }

    fun openProject(projectId: String) {
        viewModelScope.launch {
            _uiState.value.activeProject?.let { projectRepo.saveProject(it) }
            val loaded = projectRepo.loadProject(projectId) ?: return@launch
            settingsRepo.setActiveProjectId(loaded.id)
            val all = projectRepo.listProjects()
            _uiState.update {
                it.copy(
                    activeProject = loaded,
                    allProjects = all,
                    buildState = BuildState.Idle,
                    highlightedErrorLine = null,
                    disassemblyText = null,
                    selectedDiagnosticIndex = -1,
                    statusBannerMessage = "Opened ${loaded.name}"
                )
            }
        }
    }

    fun renameCurrentProject(newName: String) {
        val proj = _uiState.value.activeProject ?: return
        viewModelScope.launch {
            val renamed = projectRepo.renameProject(proj, newName)
            val all = projectRepo.listProjects()
            _uiState.update {
                it.copy(
                    activeProject = renamed,
                    allProjects = all,
                    statusBannerMessage = "Renamed to ${renamed.name}"
                )
            }
        }
    }

    fun deleteProject(projectId: String) {
        viewModelScope.launch {
            projectRepo.deleteProject(projectId)
            settingsRepo.removeRecentProjectId(projectId)
            val remaining = projectRepo.listProjects()
            val nextActive = if (_uiState.value.activeProject?.id == projectId) {
                projectRepo.loadOrCreateDefaultProject(remaining.firstOrNull()?.id)
            } else {
                _uiState.value.activeProject
            }
            nextActive?.let { settingsRepo.setActiveProjectId(it.id) }
            _uiState.update {
                it.copy(
                    activeProject = nextActive,
                    allProjects = projectRepo.listProjects(),
                    buildState = BuildState.Idle,
                    statusBannerMessage = "Project deleted"
                )
            }
        }
    }

    fun addSourceFile(relativePath: String) {
        val proj = _uiState.value.activeProject ?: return
        val normalized = relativePath.trim()
        if (normalized.isBlank()) return
        val ext = normalized.substringAfterLast('.', "").lowercase()
        val templateContent = when (ext) {
            "h", "hpp" -> {
                val guard = normalized.uppercase().replace(Regex("[^A-Z0-9]"), "_")
                "#ifndef $guard\n#define $guard\n\n#include <stdint.h>\n\n#endif /* $guard */\n"
            }
            else -> "#include <avr/io.h>\n#include <stdint.h>\n\n"
        }
        viewModelScope.launch {
            val saved = projectRepo.saveProject(proj)
            val updated = projectRepo.addFile(saved, normalized, templateContent)
            _uiState.update {
                it.copy(
                    activeProject = updated,
                    allProjects = projectRepo.listProjects(),
                    statusBannerMessage = "Added $normalized"
                )
            }
        }
    }

    fun deleteSourceFile(relativePath: String) {
        val proj = _uiState.value.activeProject ?: return
        if (proj.files.size <= 1) {
            _uiState.update { it.copy(statusBannerMessage = "Project must retain at least one file.") }
            return
        }
        viewModelScope.launch {
            val updated = projectRepo.deleteFile(proj, relativePath)
            _uiState.update {
                it.copy(
                    activeProject = updated,
                    allProjects = projectRepo.listProjects()
                )
            }
        }
    }

    fun updateBuildConfig(newConfig: BuildConfigSettings) {
        val proj = _uiState.value.activeProject ?: return
        viewModelScope.launch {
            val updated = projectRepo.updateBuildConfig(proj, newConfig)
            _uiState.update {
                it.copy(
                    activeProject = updated,
                    allProjects = projectRepo.listProjects(),
                    statusBannerMessage = "Build config updated (${newConfig.targetMcu.displayName}, ${newConfig.cpuFrequencyHz / 1_000_000.0} MHz)"
                )
            }
        }
    }

    fun updateEditorSettings(settings: EditorSettings) {
        viewModelScope.launch {
            settingsRepo.updateEditorSettings(settings)
        }
    }

    fun runVerify() = startBuildAction(BuildAction.VERIFY)

    fun runBuild() = startBuildAction(BuildAction.BUILD)

    private fun startBuildAction(action: BuildAction) {
        val currentProj = _uiState.value.activeProject ?: return
        if (_uiState.value.isCompiling) return

        activeBuildJob = viewModelScope.launch {
            _uiState.update {
                it.copy(
                    isConsoleExpanded = true,
                    highlightedErrorLine = null,
                    disassemblyText = null,
                    selectedDiagnosticIndex = -1,
                    consoleLogs = emptyList(),
                    buildState = BuildState.Running(action, "Saving project…")
                )
            }
            val savedProj = projectRepo.saveProject(currentProj)
            _uiState.update { it.copy(activeProject = savedProj) }

            val result = buildPipeline.execute(
                project = savedProj,
                action = action,
                onStageChanged = { stage ->
                    _uiState.update { it.copy(buildState = BuildState.Running(action, stage)) }
                },
                onLog = { line, level, diag ->
                    appendLog(line, level, diag)
                }
            )

            var finalProj = savedProj
            var loadedDisasm: String? = null
            if (result is BuildState.Success && action == BuildAction.BUILD) {
                finalProj = projectRepo.updateBuildOutputs(savedProj, result.elfPath, result.hexPath)
                loadedDisasm = result.lssPath?.let(::File)?.takeIf { it.exists() }?.readText()
            }

            _uiState.update {
                it.copy(
                    activeProject = finalProj,
                    allProjects = projectRepo.listProjects(),
                    buildState = result,
                    disassemblyText = loadedDisasm
                )
            }
        }
    }

    fun stopCompilation() {
        if (!_uiState.value.isCompiling) return
        buildPipeline.cancel()
        appendLog("Compilation cancelled by user.", ConsoleLineLevel.WARNING)
    }

    fun insertSnippet(code: String) {
        _uiState.update {
            it.copy(
                snippetInsertRequest = code to System.nanoTime(),
                statusBannerMessage = "Inserted ATmega32A snippet into active file"
            )
        }
    }

    fun generateDisassemblyNow() {
        val proj = _uiState.value.activeProject ?: return
        val elfPath = proj.lastElfPath
        val elfFile = elfPath?.let(::File)
        if (elfFile == null || !elfFile.exists()) {
            _uiState.update { it.copy(statusBannerMessage = "Build firmware first to generate an .elf binary.") }
            return
        }
        viewModelScope.launch {
            val res = buildPipeline.disassembleElf(elfFile, File(proj.rootPath))
            res.onSuccess { listing ->
                _uiState.update { it.copy(disassemblyText = listing) }
            }.onFailure { err ->
                _uiState.update { it.copy(statusBannerMessage = "Disassembly failed: ${err.message}") }
            }
        }
    }

    fun jumpToNextDiagnostic(forward: Boolean) {
        val diags = _uiState.value.diagnostics
        if (diags.isEmpty()) return
        val cur = _uiState.value.selectedDiagnosticIndex
        val nextIdx = if (forward) {
            if (cur < 0) 0 else (cur + 1) % diags.size
        } else {
            if (cur <= 0) diags.lastIndex else cur - 1
        }
        _uiState.update { it.copy(selectedDiagnosticIndex = nextIdx) }
        jumpToDiagnostic(diags[nextIdx])
    }

    fun jumpToDiagnostic(diag: CompilerDiagnostic) {
        val proj = _uiState.value.activeProject ?: return
        val matchingFile = proj.files.firstOrNull { pf ->
            pf.relativePath == diag.file ||
                diag.file.endsWith(pf.relativePath) ||
                pf.name == diag.file.substringAfterLast('/')
        }
        if (matchingFile != null) {
            _uiState.update {
                it.copy(
                    activeProject = proj.copy(activeFilePath = matchingFile.relativePath),
                    highlightedErrorLine = diag.line,
                    focusLineRequest = diag.line to System.nanoTime()
                )
            }
        }
    }

    fun exportFirmwareToUri(isHex: Boolean, uri: Uri) {
        val proj = _uiState.value.activeProject ?: return
        val path = if (isHex) proj.lastHexPath else proj.lastElfPath
        val file = path?.let(::File)
        if (file == null || !file.exists()) {
            _uiState.update {
                it.copy(statusBannerMessage = "Build the project first to export ${if (isHex) "HEX" else "ELF"}.")
            }
            return
        }
        val res = exportManager.copyFileToUri(file, uri)
        val msg = res.fold(
            onSuccess = { bytes -> "Exported ${file.name} ($bytes bytes)" },
            onFailure = { err -> "Export failed: ${err.message}" }
        )
        appendLog(msg, if (res.isSuccess) ConsoleLineLevel.SUCCESS else ConsoleLineLevel.ERROR)
        _uiState.update { it.copy(statusBannerMessage = msg) }
    }

    fun launchZFlasher() {
        val proj = _uiState.value.activeProject ?: return
        val hexFile = proj.lastHexPath?.let(::File)
        if (hexFile == null || !hexFile.exists()) {
            _uiState.update {
                it.copy(statusBannerMessage = "Build firmware first to generate a .hex file for ZFlasher.")
            }
            return
        }
        val outcome = exportManager.launchWithZFlasherOrChooser(hexFile)
        appendLog(outcome.message, ConsoleLineLevel.INFO)
        _uiState.update {
            it.copy(
                isZFlasherInstalled = exportManager.isZFlasherInstalled(),
                statusBannerMessage = outcome.message
            )
        }
    }

    fun refreshUsbDevices() {
        _uiState.update {
            it.copy(
                usbDevices = usbInspector.scanConnectedDevices(),
                isUsbHostSupported = usbInspector.isUsbHostSupported(),
                isZFlasherInstalled = exportManager.isZFlasherInstalled()
            )
        }
    }

    fun requestUsbPermission(deviceName: String) {
        val requested = usbInspector.requestUsbPermission(deviceName)
        _uiState.update {
            it.copy(
                statusBannerMessage = if (requested) {
                    "Requested Android USB OTG permission for $deviceName"
                } else {
                    "USB device unavailable"
                }
            )
        }
    }

    fun updateUartConfig(config: UartConfig) {
        viewModelScope.launch {
            settingsRepo.setUartBaudRate(config.baudRate)
        }
        _uiState.update { it.copy(uartConfig = config) }
    }

    private fun appendLog(
        text: String,
        level: ConsoleLineLevel,
        diag: CompilerDiagnostic? = null
    ) {
        val entry = ConsoleLogLine(
            id = logIdCounter.getAndIncrement(),
            text = text,
            level = level,
            diagnostic = diag
        )
        _uiState.update { state ->
            state.copy(consoleLogs = (state.consoleLogs + entry).takeLast(600))
        }
    }
}
