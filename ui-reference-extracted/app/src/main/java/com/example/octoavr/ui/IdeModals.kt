package com.example.octoavr.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.DriveFileRenameOutline
import androidx.compose.material.icons.filled.FlashOn
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.SaveAs
import androidx.compose.material.icons.filled.Usb
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.octoavr.compiler.HexAndElfParser
import com.example.octoavr.hardware.SerialParity
import com.example.octoavr.hardware.UartConfig
import com.example.octoavr.hardware.UsbOtgDeviceInfo
import com.example.octoavr.model.AvrProject
import com.example.octoavr.model.BuildConfigSettings
import com.example.octoavr.model.EditorSettings
import com.example.octoavr.model.OptimizationLevel
import com.example.octoavr.model.OutputFormat
import com.example.octoavr.model.TargetMcu
import com.example.octoavr.model.ToolchainStatus
import com.example.octoavr.project.Atmega32aExamples
import com.example.octoavr.project.ProjectExampleTemplate
import com.example.ui.theme.JetBrainsMonoFamily
import com.example.ui.theme.OctoAbyssNavy
import com.example.ui.theme.OctoBorderSubtle
import com.example.ui.theme.OctoCyan
import com.example.ui.theme.OctoError
import com.example.ui.theme.OctoSuccess
import com.example.ui.theme.OctoSurfaceCard
import com.example.ui.theme.OctoSurfaceDark
import com.example.ui.theme.OctoSurfaceElevated
import com.example.ui.theme.OctoTextMuted
import com.example.ui.theme.OctoTextPrimary
import com.example.ui.theme.OctoTextSecondary
import com.example.ui.theme.OctoWarning
import java.io.File

@Composable
fun ProjectExplorerContent(
    activeProject: AvrProject?,
    allProjects: List<AvrProject>,
    onSelectFile: (String) -> Unit,
    onAddFile: (String) -> Unit,
    onDeleteFile: (String) -> Unit,
    onOpenProject: (String) -> Unit,
    onCreateNewProject: (String, TargetMcu, Long) -> Unit,
    onSaveAs: (String) -> Unit,
    onRenameProject: (String) -> Unit,
    onDeleteProject: (String) -> Unit,
    onDismiss: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    var showNewProjectDialog by remember { mutableStateOf(false) }
    var showAddFileDialog by remember { mutableStateOf(false) }
    var showSaveAsDialog by remember { mutableStateOf(false) }
    var showRenameDialog by remember { mutableStateOf(false) }

    Column(
        modifier = modifier
            .background(OctoSurfaceDark)
            .padding(12.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(Icons.Default.Folder, null, tint = OctoCyan, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Text(
                "Project Explorer",
                style = MaterialTheme.typography.titleMedium,
                color = OctoTextPrimary,
                modifier = Modifier.weight(1f)
            )
            if (onDismiss != null) {
                IconButton(onClick = onDismiss, modifier = Modifier.size(32.dp)) {
                    Icon(Icons.Default.Close, "Close Explorer", tint = OctoTextSecondary)
                }
            }
        }

        Spacer(Modifier.height(8.dp))

        // Quick Project Actions
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            OutlinedButton(
                onClick = { showNewProjectDialog = true },
                border = BorderStroke(1.dp, OctoCyan),
                modifier = Modifier
                    .weight(1f)
                    .testTag("new_project_btn")
            ) {
                Icon(Icons.Default.Add, null, tint = OctoCyan, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(4.dp))
                Text("New", color = OctoCyan, style = MaterialTheme.typography.labelMedium)
            }
            IconButton(onClick = { showSaveAsDialog = true }, modifier = Modifier.testTag("save_as_btn")) {
                Icon(Icons.Default.SaveAs, "Save Project As", tint = OctoTextSecondary)
            }
            IconButton(onClick = { showRenameDialog = true }, modifier = Modifier.testTag("rename_project_btn")) {
                Icon(Icons.Default.DriveFileRenameOutline, "Rename Project", tint = OctoTextSecondary)
            }
        }

        Spacer(Modifier.height(10.dp))

        // Active Project Source Files
        if (activeProject != null) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    "FILES • ${activeProject.name}",
                    style = MaterialTheme.typography.labelSmall,
                    color = OctoTextSecondary,
                    modifier = Modifier.weight(1f)
                )
                IconButton(
                    onClick = { showAddFileDialog = true },
                    modifier = Modifier
                        .size(32.dp)
                        .testTag("add_file_btn")
                ) {
                    Icon(Icons.Default.Add, "Add Source/Header File", tint = OctoCyan)
                }
            }

            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                activeProject.files.forEach { pf ->
                    val isSelected = pf.relativePath == activeProject.activeFilePath
                    Surface(
                        color = if (isSelected) OctoSurfaceCard else OctoSurfaceElevated,
                        shape = RoundedCornerShape(6.dp),
                        border = BorderStroke(1.dp, if (isSelected) OctoCyan.copy(alpha = 0.7f) else OctoBorderSubtle),
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onSelectFile(pf.relativePath) }
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                Icons.Default.Description,
                                null,
                                tint = if (pf.isHeader) OctoWarning else OctoCyan,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(Modifier.width(8.dp))
                            Text(
                                text = pf.relativePath,
                                style = MaterialTheme.typography.bodySmall.copy(fontFamily = JetBrainsMonoFamily),
                                color = OctoTextPrimary,
                                modifier = Modifier.weight(1f)
                            )
                            if (activeProject.files.size > 1) {
                                IconButton(
                                    onClick = { onDeleteFile(pf.relativePath) },
                                    modifier = Modifier.size(28.dp)
                                ) {
                                    Icon(Icons.Default.Delete, "Delete File", tint = OctoTextMuted, modifier = Modifier.size(15.dp))
                                }
                            }
                        }
                    }
                }
            }
        }

        Spacer(Modifier.height(14.dp))
        HorizontalDivider(color = OctoBorderSubtle)
        Spacer(Modifier.height(10.dp))

        Text(
            "RECENT PROJECTS (${allProjects.size})",
            style = MaterialTheme.typography.labelSmall,
            color = OctoTextSecondary
        )
        Spacer(Modifier.height(6.dp))

        LazyColumn(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = 260.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            items(allProjects, key = { it.id }) { proj ->
                val isCurrent = proj.id == activeProject?.id
                Surface(
                    color = if (isCurrent) OctoSurfaceCard else OctoAbyssNavy,
                    shape = RoundedCornerShape(6.dp),
                    border = BorderStroke(1.dp, if (isCurrent) OctoCyan else OctoBorderSubtle),
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onOpenProject(proj.id) }
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(proj.name, style = MaterialTheme.typography.bodyMedium, color = OctoTextPrimary)
                            Text(
                                "${proj.buildConfig.targetMcu.displayName} • ${proj.files.size} file(s)",
                                style = MaterialTheme.typography.labelSmall,
                                color = OctoTextMuted
                            )
                        }
                        if (allProjects.size > 1) {
                            IconButton(
                                onClick = { onDeleteProject(proj.id) },
                                modifier = Modifier.size(30.dp)
                            ) {
                                Icon(Icons.Default.Delete, "Delete Project", tint = OctoTextMuted, modifier = Modifier.size(16.dp))
                            }
                        }
                    }
                }
            }
        }
    }

    if (showNewProjectDialog) {
        NewProjectDialog(
            onConfirm = { name, mcu, clock ->
                showNewProjectDialog = false
                onCreateNewProject(name, mcu, clock)
            },
            onDismiss = { showNewProjectDialog = false }
        )
    }

    if (showAddFileDialog) {
        SingleNameDialog(
            title = "Add Source or Header File",
            label = "Relative path (e.g. src/uart.c or include/uart.h)",
            initial = "src/module.c",
            onConfirm = { path ->
                showAddFileDialog = false
                onAddFile(path)
            },
            onDismiss = { showAddFileDialog = false }
        )
    }

    if (showSaveAsDialog && activeProject != null) {
        SingleNameDialog(
            title = "Save Project As",
            label = "New Project Name",
            initial = "${activeProject.name}_v2",
            onConfirm = { name ->
                showSaveAsDialog = false
                onSaveAs(name)
            },
            onDismiss = { showSaveAsDialog = false }
        )
    }

    if (showRenameDialog && activeProject != null) {
        SingleNameDialog(
            title = "Rename Project",
            label = "Project Name",
            initial = activeProject.name,
            onConfirm = { name ->
                showRenameDialog = false
                onRenameProject(name)
            },
            onDismiss = { showRenameDialog = false }
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun BuildConfigDialog(
    config: BuildConfigSettings,
    editorSettings: EditorSettings,
    toolchainStatus: ToolchainStatus?,
    onSaveConfig: (BuildConfigSettings) -> Unit,
    onSaveEditorSettings: (EditorSettings) -> Unit,
    onDismiss: () -> Unit
) {
    var mcu by remember { mutableStateOf(config.targetMcu) }
    var freqText by remember { mutableStateOf(config.cpuFrequencyHz.toString()) }
    var opt by remember { mutableStateOf(config.optimizationLevel) }
    var outputFmt by remember { mutableStateOf(config.outputFormat) }
    var cFlags by remember { mutableStateOf(config.extraCFlags) }
    var cppFlags by remember { mutableStateOf(config.extraCppFlags) }
    var ldFlags by remember { mutableStateOf(config.extraLdFlags) }
    var incDirs by remember { mutableStateOf(config.includeDirectories.joinToString(", ")) }
    var extraSrc by remember { mutableStateOf(config.additionalSourceFiles.joinToString(", ")) }
    var debugInfo by remember { mutableStateOf(config.includeDebugInfo) }
    var disasm by remember { mutableStateOf(config.generateDisassembly) }
    var verbose by remember { mutableStateOf(config.verboseOutput) }

    var fontSize by remember { mutableStateOf(editorSettings.fontSizeSp.toFloat()) }
    var autoBrackets by remember { mutableStateOf(editorSettings.autoCloseBrackets) }
    var autoIndent by remember { mutableStateOf(editorSettings.autoIndent) }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = OctoSurfaceElevated,
        title = { Text("Build & Editor Configuration", color = OctoTextPrimary) },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 460.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                if (toolchainStatus != null) {
                    Surface(
                        color = OctoAbyssNavy,
                        shape = RoundedCornerShape(8.dp),
                        border = BorderStroke(1.dp, if (toolchainStatus.isReady) OctoSuccess.copy(0.5f) else OctoError.copy(0.5f))
                    ) {
                        Column(Modifier.padding(10.dp)) {
                            Text(
                                text = "Toolchain: ${toolchainStatus.modeLabel}",
                                style = MaterialTheme.typography.labelMedium,
                                color = if (toolchainStatus.isReady) OctoSuccess else OctoError
                            )
                            Text(
                                text = toolchainStatus.gccVersion ?: toolchainStatus.errorDetail.orEmpty(),
                                style = MaterialTheme.typography.labelSmall,
                                color = OctoTextSecondary
                            )
                        }
                    }
                }

                Text("Target MCU", style = MaterialTheme.typography.labelMedium, color = OctoCyan)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    TargetMcu.entries.forEach { item ->
                        FilterChip(
                            selected = mcu == item,
                            onClick = { mcu = item },
                            label = { Text(item.displayName) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = OctoCyan.copy(alpha = 0.2f),
                                selectedLabelColor = OctoCyan
                            )
                        )
                    }
                }

                Text("CPU Frequency (F_CPU Hz)", style = MaterialTheme.typography.labelMedium, color = OctoCyan)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf(1_000_000L, 4_000_000L, 8_000_000L, 12_000_000L, 16_000_000L, 20_000_000L).forEach { hz ->
                        FilterChip(
                            selected = freqText == hz.toString(),
                            onClick = { freqText = hz.toString() },
                            label = { Text("${hz / 1_000_000} MHz") },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = OctoCyan.copy(alpha = 0.2f),
                                selectedLabelColor = OctoCyan
                            )
                        )
                    }
                }
                OutlinedTextField(
                    value = freqText,
                    onValueChange = { freqText = it.filter { ch -> ch.isDigit() } },
                    label = { Text("Custom F_CPU (Hz)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                Text("Optimization Level", style = MaterialTheme.typography.labelMedium, color = OctoCyan)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    OptimizationLevel.entries.forEach { level ->
                        FilterChip(
                            selected = opt == level,
                            onClick = { opt = level },
                            label = { Text(level.label) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = OctoCyan.copy(alpha = 0.2f),
                                selectedLabelColor = OctoCyan
                            )
                        )
                    }
                }

                Text("Output Format", style = MaterialTheme.typography.labelMedium, color = OctoCyan)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    OutputFormat.entries.forEach { fmt ->
                        FilterChip(
                            selected = outputFmt == fmt,
                            onClick = { outputFmt = fmt },
                            label = { Text(fmt.label) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = OctoCyan.copy(alpha = 0.2f),
                                selectedLabelColor = OctoCyan
                            )
                        )
                    }
                }

                OutlinedTextField(
                    value = cFlags,
                    onValueChange = { cFlags = it },
                    label = { Text("AVR-GCC C Flags") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = cppFlags,
                    onValueChange = { cppFlags = it },
                    label = { Text("AVR-G++ C++ Flags") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = ldFlags,
                    onValueChange = { ldFlags = it },
                    label = { Text("Linker Flags") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = incDirs,
                    onValueChange = { incDirs = it },
                    label = { Text("Include Directories (comma-separated)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = extraSrc,
                    onValueChange = { extraSrc = it },
                    label = { Text("Additional Source Files (comma-separated)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                SwitchRow("Debug Symbols (-g)", debugInfo) { debugInfo = it }
                SwitchRow("Generate Disassembly (.lss)", disasm) { disasm = it }
                SwitchRow("Verbose Compiler Output (-v)", verbose) { verbose = it }

                HorizontalDivider(color = OctoBorderSubtle)
                Text("Editor Preferences", style = MaterialTheme.typography.labelMedium, color = OctoCyan)
                Text("Code Font Size: ${fontSize.toInt()} sp", style = MaterialTheme.typography.bodySmall, color = OctoTextSecondary)
                Slider(
                    value = fontSize,
                    onValueChange = { fontSize = it },
                    valueRange = 10f..20f,
                    colors = SliderDefaults.colors(thumbColor = OctoCyan, activeTrackColor = OctoCyan)
                )
                SwitchRow("Auto-Close Brackets & Quotes", autoBrackets) { autoBrackets = it }
                SwitchRow("Auto-Indent on Newline", autoIndent) { autoIndent = it }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val parsedHz = freqText.toLongOrNull()?.coerceIn(100_000L, 32_000_000L) ?: 8_000_000L
                    onSaveConfig(
                        config.copy(
                            targetMcu = mcu,
                            cpuFrequencyHz = parsedHz,
                            optimizationLevel = opt,
                            outputFormat = outputFmt,
                            extraCFlags = cFlags,
                            extraCppFlags = cppFlags,
                            extraLdFlags = ldFlags,
                            includeDirectories = incDirs.split(",").map { it.trim() }.filter { it.isNotEmpty() },
                            additionalSourceFiles = extraSrc.split(",").map { it.trim() }.filter { it.isNotEmpty() },
                            includeDebugInfo = debugInfo,
                            generateDisassembly = disasm,
                            verboseOutput = verbose
                        )
                    )
                    onSaveEditorSettings(
                        editorSettings.copy(
                            fontSizeSp = fontSize.toInt(),
                            autoCloseBrackets = autoBrackets,
                            autoIndent = autoIndent
                        )
                    )
                    onDismiss()
                },
                colors = ButtonDefaults.buttonColors(containerColor = OctoCyan, contentColor = OctoAbyssNavy)
            ) {
                Text("Apply", fontWeight = FontWeight.SemiBold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel", color = OctoTextSecondary)
            }
        }
    )
}

@Composable
fun ExamplesBrowserDialog(
    onSelectExample: (ProjectExampleTemplate) -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = OctoSurfaceElevated,
        title = { Text("ATmega32A Examples", color = OctoTextPrimary) },
        text = {
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 440.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(Atmega32aExamples.all, key = { it.id }) { example ->
                    Surface(
                        color = OctoSurfaceDark,
                        shape = RoundedCornerShape(8.dp),
                        border = BorderStroke(1.dp, OctoBorderSubtle),
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                onSelectExample(example)
                                onDismiss()
                            }
                            .testTag("example_${example.id}")
                    ) {
                        Column(Modifier.padding(10.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    example.title,
                                    style = MaterialTheme.typography.titleSmall,
                                    color = OctoCyan,
                                    modifier = Modifier.weight(1f)
                                )
                                Text(
                                    example.category,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = OctoTextMuted
                                )
                            }
                            Spacer(Modifier.height(4.dp))
                            Text(example.summary, style = MaterialTheme.typography.bodySmall, color = OctoTextPrimary)
                            if (example.pinsNote.isNotEmpty()) {
                                Spacer(Modifier.height(4.dp))
                                Text(
                                    "Pins: ${example.pinsNote}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = OctoTextSecondary
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Close", color = OctoCyan)
            }
        }
    )
}

@Composable
fun ExportAndFlashDialog(
    project: AvrProject?,
    isZFlasherInstalled: Boolean,
    onExportHexSaf: (String) -> Unit,
    onExportElfSaf: (String) -> Unit,
    onLaunchZFlasher: () -> Unit,
    onDismiss: () -> Unit
) {
    val hexFile = project?.lastHexPath?.let(::File)?.takeIf { it.exists() }
    val elfFile = project?.lastElfPath?.let(::File)?.takeIf { it.exists() }
    val hexValidation = remember(hexFile?.absolutePath, hexFile?.lastModified()) {
        hexFile?.let { HexAndElfParser.validateIntelHex(it) }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = OctoSurfaceElevated,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.FlashOn, null, tint = OctoCyan)
                Spacer(Modifier.width(8.dp))
                Text("Export & Flash Firmware", color = OctoTextPrimary)
            }
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                if (hexFile == null && elfFile == null) {
                    Text(
                        "No compiled firmware artifacts found yet. Run Build first to generate .elf and .hex outputs.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = OctoWarning
                    )
                } else {
                    if (hexFile != null && hexValidation != null) {
                        Surface(
                            color = OctoAbyssNavy,
                            shape = RoundedCornerShape(8.dp),
                            border = BorderStroke(1.dp, if (hexValidation.isValid) OctoSuccess.copy(0.6f) else OctoError)
                        ) {
                            Column(Modifier.padding(10.dp)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Default.CheckCircle, null, tint = OctoSuccess, modifier = Modifier.size(16.dp))
                                    Spacer(Modifier.width(6.dp))
                                    Text(hexFile.name, style = MaterialTheme.typography.titleSmall, color = OctoTextPrimary)
                                }
                                Spacer(Modifier.height(4.dp))
                                Text(
                                    "Intel HEX valid • ${hexValidation.recordCount} records • ${hexValidation.dataByteCount} program bytes",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = OctoSuccess
                                )
                            }
                        }
                    }

                    if (elfFile != null) {
                        Text(
                            "ELF Output: ${elfFile.name} (${elfFile.length()} bytes)",
                            style = MaterialTheme.typography.labelSmall,
                            color = OctoTextSecondary
                        )
                    }

                    Button(
                        onClick = onLaunchZFlasher,
                        enabled = hexFile != null,
                        colors = ButtonDefaults.buttonColors(containerColor = OctoCyan, contentColor = OctoAbyssNavy),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("launch_zflasher_btn")
                    ) {
                        Icon(Icons.Default.FlashOn, null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text(
                            if (isZFlasherInstalled) "Flash with ZFlasher AVR" else "Open / Share HEX with Flasher",
                            fontWeight = FontWeight.SemiBold
                        )
                    }

                    Text(
                        if (isZFlasherInstalled) {
                            "ZFlasher AVR detected. Flashing runs inside ZFlasher AVR; OctoAVR32A hands over the verified HEX via FileProvider."
                        } else {
                            "ZFlasher AVR (ru.zdevs.zflasher.avr) not detected. Tapping above opens Android's flasher/share sheet."
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = OctoTextSecondary
                    )

                    if (hexFile != null) {
                        OutlinedButton(
                            onClick = { onExportHexSaf(hexFile.name) },
                            border = BorderStroke(1.dp, OctoBorderSubtle),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("export_hex_saf_btn")
                        ) {
                            Icon(Icons.Default.Download, null, tint = OctoCyan, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("Save HEX to Downloads / Folder", color = OctoTextPrimary)
                        }
                    }

                    if (elfFile != null) {
                        OutlinedButton(
                            onClick = { onExportElfSaf(elfFile.name) },
                            border = BorderStroke(1.dp, OctoBorderSubtle),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("export_elf_saf_btn")
                        ) {
                            Icon(Icons.Default.Download, null, tint = OctoTextSecondary, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("Save ELF to Downloads / Folder", color = OctoTextPrimary)
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Done", color = OctoCyan)
            }
        }
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun HardwareIntegrationDialog(
    uartConfig: UartConfig,
    usbDevices: List<UsbOtgDeviceInfo>,
    isUsbHostSupported: Boolean,
    onUpdateUart: (UartConfig) -> Unit,
    onRefreshUsb: () -> Unit,
    onRequestUsbPermission: (String) -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = OctoSurfaceElevated,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Usb, null, tint = OctoCyan)
                Spacer(Modifier.width(8.dp))
                Text("Serial & USB OTG Hardware", color = OctoTextPrimary)
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 440.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        "USB OTG Host: ${if (isUsbHostSupported) "Supported" else "Not Advertised"}",
                        style = MaterialTheme.typography.labelMedium,
                        color = if (isUsbHostSupported) OctoSuccess else OctoWarning,
                        modifier = Modifier.weight(1f)
                    )
                    IconButton(onClick = onRefreshUsb, modifier = Modifier.size(32.dp)) {
                        Icon(Icons.Default.Refresh, "Scan USB OTG Devices", tint = OctoCyan)
                    }
                }

                if (usbDevices.isEmpty()) {
                    Surface(
                        color = OctoAbyssNavy,
                        shape = RoundedCornerShape(8.dp),
                        border = BorderStroke(1.dp, OctoBorderSubtle)
                    ) {
                        Text(
                            "No USB OTG programmer (USBasp, AVRISP) or USB-UART bridge (CH340, CP2102, FT232R) currently attached.",
                            style = MaterialTheme.typography.bodySmall,
                            color = OctoTextSecondary,
                            modifier = Modifier.padding(10.dp)
                        )
                    }
                } else {
                    usbDevices.forEach { dev ->
                        Surface(
                            color = OctoAbyssNavy,
                            shape = RoundedCornerShape(8.dp),
                            border = BorderStroke(1.dp, OctoCyan.copy(0.5f))
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(10.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(Modifier.weight(1f)) {
                                    Text(dev.bridgeType.label, style = MaterialTheme.typography.labelMedium, color = OctoCyan)
                                    Text(
                                        "${dev.productName} (${dev.vidPidHex})",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = OctoTextSecondary
                                    )
                                }
                                if (!dev.hasPermission) {
                                    TextButton(onClick = { onRequestUsbPermission(dev.deviceName) }) {
                                        Text("Grant USB", color = OctoCyan)
                                    }
                                } else {
                                    Text("Permitted", style = MaterialTheme.typography.labelSmall, color = OctoSuccess)
                                }
                            }
                        }
                    }
                }

                HorizontalDivider(color = OctoBorderSubtle)
                Text("UART Serial Configuration (${uartConfig.summary})", style = MaterialTheme.typography.labelMedium, color = OctoCyan)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    UartConfig.SUPPORTED_BAUDS.forEach { baud ->
                        FilterChip(
                            selected = uartConfig.baudRate == baud,
                            onClick = { onUpdateUart(uartConfig.copy(baudRate = baud)) },
                            label = { Text("$baud") },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = OctoCyan.copy(alpha = 0.2f),
                                selectedLabelColor = OctoCyan
                            )
                        )
                    }
                }
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    SerialParity.entries.forEach { parity ->
                        FilterChip(
                            selected = uartConfig.parity == parity,
                            onClick = { onUpdateUart(uartConfig.copy(parity = parity)) },
                            label = { Text(parity.label) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = OctoCyan.copy(alpha = 0.2f),
                                selectedLabelColor = OctoCyan
                            )
                        )
                    }
                }

                Surface(
                    color = OctoSurfaceDark,
                    shape = RoundedCornerShape(8.dp),
                    border = BorderStroke(1.dp, OctoBorderSubtle)
                ) {
                    Column(Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text("Hardware Pipeline Status", style = MaterialTheme.typography.labelMedium, color = OctoTextPrimary)
                        Text(
                            "• Implemented: USB OTG device detection, Android USB permission request, UART baud/parity settings, and FileProvider HEX export to ZFlasher AVR.",
                            style = MaterialTheme.typography.bodySmall,
                            color = OctoSuccess
                        )
                        Text(
                            "• Planned: Direct USB-CDC/CH340 bulk transfer Serial Monitor RX/TX stream and native STK500v1/USBasp flashing.",
                            style = MaterialTheme.typography.bodySmall,
                            color = OctoTextSecondary
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Close", color = OctoCyan)
            }
        }
    )
}

@Composable
private fun SwitchRow(
    label: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, style = MaterialTheme.typography.bodySmall, color = OctoTextPrimary, modifier = Modifier.weight(1f))
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.colors(checkedThumbColor = OctoAbyssNavy, checkedTrackColor = OctoCyan)
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun NewProjectDialog(
    onConfirm: (String, TargetMcu, Long) -> Unit,
    onDismiss: () -> Unit
) {
    var name by remember { mutableStateOf("ATmega32A_App") }
    var mcu by remember { mutableStateOf(TargetMcu.ATMEGA32A) }
    var clockHz by remember { mutableStateOf(8_000_000L) }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = OctoSurfaceElevated,
        title = { Text("New AVR Project", color = OctoTextPrimary) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Project Name") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Text("Target MCU", style = MaterialTheme.typography.labelMedium, color = OctoCyan)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    TargetMcu.entries.forEach { item ->
                        FilterChip(
                            selected = mcu == item,
                            onClick = {
                                mcu = item
                                clockHz = item.defaultClockHz
                            },
                            label = { Text(item.displayName) }
                        )
                    }
                }
                Text("Clock Frequency", style = MaterialTheme.typography.labelMedium, color = OctoCyan)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf(1_000_000L, 8_000_000L, 16_000_000L).forEach { hz ->
                        FilterChip(
                            selected = clockHz == hz,
                            onClick = { clockHz = hz },
                            label = { Text("${hz / 1_000_000} MHz") }
                        )
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = { onConfirm(name, mcu, clockHz) },
                colors = ButtonDefaults.buttonColors(containerColor = OctoCyan, contentColor = OctoAbyssNavy)
            ) {
                Text("Create")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel", color = OctoTextSecondary) }
        }
    )
}

@Composable
private fun SingleNameDialog(
    title: String,
    label: String,
    initial: String,
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit
) {
    var value by remember { mutableStateOf(initial) }
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = OctoSurfaceElevated,
        title = { Text(title, color = OctoTextPrimary) },
        text = {
            OutlinedTextField(
                value = value,
                onValueChange = { value = it },
                label = { Text(label) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
        },
        confirmButton = {
            Button(
                onClick = { onConfirm(value) },
                colors = ButtonDefaults.buttonColors(containerColor = OctoCyan, contentColor = OctoAbyssNavy)
            ) {
                Text("Confirm")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel", color = OctoTextSecondary) }
        }
    )
}
