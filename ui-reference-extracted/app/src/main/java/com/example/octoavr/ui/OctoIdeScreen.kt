package com.example.octoavr.ui

import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.octoavr.editor.AvrCodeEditorPane
import com.example.ui.theme.OctoAbyssNavy
import com.example.ui.theme.OctoBorderSubtle
import com.example.ui.theme.OctoCyan
import com.example.ui.theme.OctoSurfaceDark
import com.example.ui.theme.OctoSurfaceElevated
import com.example.ui.theme.OctoTextPrimary

private enum class IdeOverlay {
    NONE,
    PROJECT_EXPLORER,
    BUILD_CONFIG,
    EXAMPLES,
    EXPORT_FLASH,
    HARDWARE,
    ATMEGA32A_REF,
    FIRMWARE_INSPECTOR
}

@Composable
fun OctoIdeScreen(
    viewModel: OctoIdeViewModel,
    modifier: Modifier = Modifier
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    var activeOverlay by remember { mutableStateOf(IdeOverlay.NONE) }
    var tabletExplorerVisible by remember { mutableStateOf(true) }

    val hexExportLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/octet-stream")
    ) { uri ->
        if (uri != null) {
            viewModel.exportFirmwareToUri(isHex = true, uri = uri)
        }
    }

    val elfExportLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/octet-stream")
    ) { uri ->
        if (uri != null) {
            viewModel.exportFirmwareToUri(isHex = false, uri = uri)
        }
    }

    LaunchedEffect(state.statusBannerMessage) {
        val msg = state.statusBannerMessage ?: return@LaunchedEffect
        snackbarHostState.showSnackbar(msg)
        viewModel.clearBannerMessage()
    }

    if (activeOverlay != IdeOverlay.NONE) {
        BackHandler { activeOverlay = IdeOverlay.NONE }
    }

    Scaffold(
        modifier = modifier
            .fillMaxSize()
            .background(OctoAbyssNavy),
        containerColor = OctoAbyssNavy,
        snackbarHost = { SnackbarHost(hostState = snackbarHostState) }
    ) { innerPadding ->
        if (state.isInitializing) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator(color = OctoCyan)
            }
            return@Scaffold
        }

        BoxWithConstraints(
            modifier = Modifier
                .fillMaxSize()
                .navigationBarsPadding()
                .imePadding()
        ) {
            val isWideTablet = maxWidth >= 840.dp
            val project = state.activeProject

            Column(modifier = Modifier.fillMaxSize()) {
                IdeTopToolbar(
                    project = project,
                    hasUnsavedChanges = state.hasUnsavedChanges,
                    buildState = state.buildState,
                    isWideLayout = isWideTablet,
                    onOpenProjectExplorer = {
                        if (isWideTablet) {
                            tabletExplorerVisible = !tabletExplorerVisible
                        } else {
                            activeOverlay = IdeOverlay.PROJECT_EXPLORER
                        }
                    },
                    onSave = { viewModel.saveCurrentProject() },
                    onVerify = { viewModel.runVerify() },
                    onBuild = { viewModel.runBuild() },
                    onStop = { viewModel.stopCompilation() },
                    onOpenExportFlash = {
                        viewModel.refreshUsbDevices()
                        activeOverlay = IdeOverlay.EXPORT_FLASH
                    },
                    onOpenBuildConfig = { activeOverlay = IdeOverlay.BUILD_CONFIG },
                    onOpenExamples = { activeOverlay = IdeOverlay.EXAMPLES },
                    onOpenHardware = {
                        viewModel.refreshUsbDevices()
                        activeOverlay = IdeOverlay.HARDWARE
                    },
                    onOpenReference = { activeOverlay = IdeOverlay.ATMEGA32A_REF },
                    onOpenFirmwareInspector = { activeOverlay = IdeOverlay.FIRMWARE_INSPECTOR }
                )

                Row(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                ) {
                    if (isWideTablet) {
                        AnimatedVisibility(visible = tabletExplorerVisible) {
                            Row(modifier = Modifier.fillMaxHeight()) {
                                ProjectExplorerContent(
                                    activeProject = project,
                                    allProjects = state.allProjects,
                                    onSelectFile = { viewModel.selectFile(it) },
                                    onAddFile = { viewModel.addSourceFile(it) },
                                    onDeleteFile = { viewModel.deleteSourceFile(it) },
                                    onOpenProject = { viewModel.openProject(it) },
                                    onCreateNewProject = { name, mcu, clk ->
                                        viewModel.createNewBlankProject(name, mcu, clk)
                                    },
                                    onSaveAs = { viewModel.saveProjectAs(it) },
                                    onRenameProject = { viewModel.renameCurrentProject(it) },
                                    onDeleteProject = { viewModel.deleteProject(it) },
                                    modifier = Modifier
                                        .width(260.dp)
                                        .fillMaxHeight()
                                )
                                VerticalDivider(color = OctoBorderSubtle)
                            }
                        }
                    }

                    // Main Code Editor + Collapsible Build Console
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight()
                    ) {
                        if (project != null) {
                            AvrCodeEditorPane(
                                files = project.files,
                                activeFilePath = project.activeFilePath,
                                editorSettings = state.editorSettings,
                                highlightedErrorLine = state.highlightedErrorLine,
                                focusLineRequest = state.focusLineRequest,
                                snippetInsertRequest = state.snippetInsertRequest,
                                onSelectFile = { viewModel.selectFile(it) },
                                onContentChanged = { path, text ->
                                    viewModel.updateFileContent(path, text)
                                },
                                modifier = Modifier
                                    .weight(1f)
                                    .fillMaxWidth()
                            )
                        } else {
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .fillMaxWidth()
                                    .background(OctoSurfaceDark),
                                contentAlignment = Alignment.Center
                            ) {
                                Text("No project loaded", color = OctoTextPrimary)
                            }
                        }

                        CollapsibleBuildConsole(
                            isExpanded = state.isConsoleExpanded,
                            buildState = state.buildState,
                            logs = state.consoleLogs,
                            hasBuiltHex = project?.lastHexPath != null,
                            diagnosticsCount = state.diagnostics.size,
                            onToggleExpand = { viewModel.toggleConsole() },
                            onClear = { viewModel.clearConsole() },
                            onDiagnosticClick = { diag -> viewModel.jumpToDiagnostic(diag) },
                            onJumpDiagnostic = { forward -> viewModel.jumpToNextDiagnostic(forward) },
                            onOpenInspector = { activeOverlay = IdeOverlay.FIRMWARE_INSPECTOR },
                            onOpenExportFlash = {
                                viewModel.refreshUsbDevices()
                                activeOverlay = IdeOverlay.EXPORT_FLASH
                            }
                        )
                    }
                }
            }

            // Phone Project Explorer Dialog
            if (activeOverlay == IdeOverlay.PROJECT_EXPLORER && !isWideTablet) {
                AlertDialog(
                    onDismissRequest = { activeOverlay = IdeOverlay.NONE },
                    containerColor = OctoSurfaceElevated,
                    text = {
                        ProjectExplorerContent(
                            activeProject = project,
                            allProjects = state.allProjects,
                            onSelectFile = {
                                viewModel.selectFile(it)
                                activeOverlay = IdeOverlay.NONE
                            },
                            onAddFile = { viewModel.addSourceFile(it) },
                            onDeleteFile = { viewModel.deleteSourceFile(it) },
                            onOpenProject = {
                                viewModel.openProject(it)
                                activeOverlay = IdeOverlay.NONE
                            },
                            onCreateNewProject = { name, mcu, clk ->
                                viewModel.createNewBlankProject(name, mcu, clk)
                                activeOverlay = IdeOverlay.NONE
                            },
                            onSaveAs = { viewModel.saveProjectAs(it) },
                            onRenameProject = { viewModel.renameCurrentProject(it) },
                            onDeleteProject = { viewModel.deleteProject(it) },
                            onDismiss = { activeOverlay = IdeOverlay.NONE },
                            modifier = Modifier.fillMaxWidth()
                        )
                    },
                    confirmButton = {}
                )
            }

            if (activeOverlay == IdeOverlay.BUILD_CONFIG && project != null) {
                BuildConfigDialog(
                    config = project.buildConfig,
                    editorSettings = state.editorSettings,
                    toolchainStatus = state.toolchainStatus,
                    onSaveConfig = { viewModel.updateBuildConfig(it) },
                    onSaveEditorSettings = { viewModel.updateEditorSettings(it) },
                    onDismiss = { activeOverlay = IdeOverlay.NONE }
                )
            }

            if (activeOverlay == IdeOverlay.EXAMPLES) {
                ExamplesBrowserDialog(
                    onSelectExample = { template ->
                        viewModel.createProjectFromExample(template)
                    },
                    onDismiss = { activeOverlay = IdeOverlay.NONE }
                )
            }

            if (activeOverlay == IdeOverlay.EXPORT_FLASH) {
                ExportAndFlashDialog(
                    project = project,
                    isZFlasherInstalled = state.isZFlasherInstalled,
                    onExportHexSaf = { suggestedName -> hexExportLauncher.launch(suggestedName) },
                    onExportElfSaf = { suggestedName -> elfExportLauncher.launch(suggestedName) },
                    onLaunchZFlasher = { viewModel.launchZFlasher() },
                    onDismiss = { activeOverlay = IdeOverlay.NONE }
                )
            }

            if (activeOverlay == IdeOverlay.HARDWARE) {
                HardwareIntegrationDialog(
                    uartConfig = state.uartConfig,
                    usbDevices = state.usbDevices,
                    isUsbHostSupported = state.isUsbHostSupported,
                    onUpdateUart = { viewModel.updateUartConfig(it) },
                    onRefreshUsb = { viewModel.refreshUsbDevices() },
                    onRequestUsbPermission = { viewModel.requestUsbPermission(it) },
                    onDismiss = { activeOverlay = IdeOverlay.NONE }
                )
            }

            if (activeOverlay == IdeOverlay.ATMEGA32A_REF) {
                Atmega32aReferenceDialog(
                    cpuFrequencyHz = project?.buildConfig?.cpuFrequencyHz ?: 8_000_000L,
                    onInsertSnippet = { snippet -> viewModel.insertSnippet(snippet) },
                    onDismiss = { activeOverlay = IdeOverlay.NONE }
                )
            }

            if (activeOverlay == IdeOverlay.FIRMWARE_INSPECTOR) {
                FirmwareInspectorDialog(
                    project = project,
                    buildState = state.buildState,
                    disassemblyText = state.disassemblyText,
                    onGenerateDisassembly = { viewModel.generateDisassemblyNow() },
                    onDismiss = { activeOverlay = IdeOverlay.NONE }
                )
            }
        }
    }
}
