package com.example.octoavr.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.DeveloperBoard
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material.icons.filled.Usb
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.octoavr.model.AvrProject
import com.example.octoavr.model.BuildState
import com.example.octoavr.model.CompilerDiagnostic
import com.example.octoavr.model.ConsoleLineLevel
import com.example.octoavr.model.ConsoleLogLine
import com.example.octoavr.model.MemoryUsage
import com.example.ui.theme.JetBrainsMonoFamily
import com.example.ui.theme.OctoAbyssNavy
import com.example.ui.theme.OctoBorderSubtle
import com.example.ui.theme.OctoCobalt
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

@Composable
fun IdeTopToolbar(
    project: AvrProject?,
    hasUnsavedChanges: Boolean,
    buildState: BuildState,
    isWideLayout: Boolean,
    onOpenProjectExplorer: () -> Unit,
    onSave: () -> Unit,
    onVerify: () -> Unit,
    onBuild: () -> Unit,
    onStop: () -> Unit,
    onOpenExportFlash: () -> Unit,
    onOpenBuildConfig: () -> Unit,
    onOpenExamples: () -> Unit,
    onOpenHardware: () -> Unit,
    onOpenReference: () -> Unit = {},
    onOpenFirmwareInspector: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    var menuExpanded by remember { mutableStateOf(false) }
    val isCompiling = buildState is BuildState.Running

    Surface(
        color = OctoAbyssNavy,
        modifier = modifier
            .fillMaxWidth()
            .statusBarsPadding()
    ) {
        Column {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Brand Octopus Icon + Project Name
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(8.dp))
                        .clickable(onClick = onOpenProjectExplorer)
                        .padding(horizontal = 4.dp, vertical = 4.dp)
                        .testTag("toolbar_project_button")
                ) {
                    Image(
                        painter = painterResource(id = R.drawable.ic_octo_logo_1795185943972),
                        contentDescription = "OctoAVR32A Logo",
                        modifier = Modifier
                            .size(30.dp)
                            .clip(RoundedCornerShape(7.dp))
                    )
                    Spacer(Modifier.width(8.dp))
                    Column {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = project?.name ?: "OctoAVR32A",
                                style = MaterialTheme.typography.titleMedium,
                                color = OctoTextPrimary,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            if (hasUnsavedChanges) {
                                Text(
                                    text = " *",
                                    style = MaterialTheme.typography.titleMedium,
                                    color = OctoWarning
                                )
                            }
                        }
                        val cfg = project?.buildConfig
                        if (cfg != null) {
                            Text(
                                text = "${cfg.targetMcu.displayName} • ${cfg.cpuFrequencyHz / 1_000_000.0}MHz • ${cfg.optimizationLevel.flag}",
                                style = MaterialTheme.typography.labelSmall,
                                color = OctoCyan,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }

                // Save Button
                IconButton(
                    onClick = onSave,
                    modifier = Modifier.testTag("toolbar_save_btn")
                ) {
                    Icon(
                        imageVector = Icons.Default.Save,
                        contentDescription = "Save Project",
                        tint = if (hasUnsavedChanges) OctoWarning else OctoTextSecondary
                    )
                }

                if (isCompiling) {
                    // Stop Button during active compilation
                    Surface(
                        color = OctoError.copy(alpha = 0.18f),
                        shape = RoundedCornerShape(8.dp),
                        border = BorderStroke(1.dp, OctoError),
                        modifier = Modifier
                            .clickable(onClick = onStop)
                            .testTag("toolbar_stop_btn")
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 7.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.Stop,
                                contentDescription = "Stop Build",
                                tint = OctoError,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(Modifier.width(4.dp))
                            Text("Stop", color = OctoError, style = MaterialTheme.typography.labelMedium)
                        }
                    }
                } else {
                    // Verify Button
                    ToolbarActionChip(
                        icon = Icons.Default.CheckCircle,
                        label = if (isWideLayout) "Verify" else null,
                        contentDesc = "Verify Code",
                        tint = OctoCyan,
                        onClick = onVerify,
                        modifier = Modifier.testTag("toolbar_verify_btn")
                    )
                    Spacer(Modifier.width(6.dp))
                    // Build Firmware Button
                    ToolbarActionChip(
                        icon = Icons.Default.Build,
                        label = if (isWideLayout) "Build" else "Build",
                        contentDesc = "Build ELF and HEX",
                        tint = OctoAbyssNavy,
                        containerColor = OctoCyan,
                        onClick = onBuild,
                        modifier = Modifier.testTag("toolbar_build_btn")
                    )
                }

                // Overflow More Menu
                Box {
                    IconButton(
                        onClick = { menuExpanded = true },
                        modifier = Modifier.testTag("toolbar_more_btn")
                    ) {
                        Icon(
                            imageVector = Icons.Default.MoreVert,
                            contentDescription = "More Options",
                            tint = OctoTextPrimary
                        )
                    }
                    DropdownMenu(
                        expanded = menuExpanded,
                        onDismissRequest = { menuExpanded = false },
                        containerColor = OctoSurfaceElevated
                    ) {
                        DropdownMenuItem(
                            text = { Text("Export / Flash HEX", color = OctoTextPrimary) },
                            leadingIcon = { Icon(Icons.Default.PlayArrow, null, tint = OctoCyan) },
                            onClick = {
                                menuExpanded = false
                                onOpenExportFlash()
                            },
                            modifier = Modifier.testTag("menu_export_flash")
                        )
                        DropdownMenuItem(
                            text = { Text("Project Explorer", color = OctoTextPrimary) },
                            leadingIcon = { Icon(Icons.Default.FolderOpen, null, tint = OctoTextSecondary) },
                            onClick = {
                                menuExpanded = false
                                onOpenProjectExplorer()
                            },
                            modifier = Modifier.testTag("menu_project_explorer")
                        )
                        DropdownMenuItem(
                            text = { Text("ATmega32A Examples", color = OctoTextPrimary) },
                            leadingIcon = { Icon(Icons.Default.Code, null, tint = OctoTextSecondary) },
                            onClick = {
                                menuExpanded = false
                                onOpenExamples()
                            },
                            modifier = Modifier.testTag("menu_examples")
                        )
                        DropdownMenuItem(
                            text = { Text("Build Configuration", color = OctoTextPrimary) },
                            leadingIcon = { Icon(Icons.Default.Settings, null, tint = OctoTextSecondary) },
                            onClick = {
                                menuExpanded = false
                                onOpenBuildConfig()
                            },
                            modifier = Modifier.testTag("menu_build_config")
                        )
                        DropdownMenuItem(
                            text = { Text("Serial & USB OTG", color = OctoTextPrimary) },
                            leadingIcon = { Icon(Icons.Default.Usb, null, tint = OctoTextSecondary) },
                            onClick = {
                                menuExpanded = false
                                onOpenHardware()
                            },
                            modifier = Modifier.testTag("menu_hardware")
                        )
                        DropdownMenuItem(
                            text = { Text("ATmega32A Pinout & Snippets", color = OctoTextPrimary) },
                            leadingIcon = { Icon(Icons.Default.DeveloperBoard, null, tint = OctoCyan) },
                            onClick = {
                                menuExpanded = false
                                onOpenReference()
                            },
                            modifier = Modifier.testTag("menu_atmega32a_ref")
                        )
                        DropdownMenuItem(
                            text = { Text("Firmware Inspector (HEX / ASM)", color = OctoTextPrimary) },
                            leadingIcon = { Icon(Icons.Default.Memory, null, tint = OctoTextSecondary) },
                            onClick = {
                                menuExpanded = false
                                onOpenFirmwareInspector()
                            },
                            modifier = Modifier.testTag("menu_firmware_inspector")
                        )
                    }
                }
            }
            HorizontalDivider(color = OctoBorderSubtle)
        }
    }
}

@Composable
private fun ToolbarActionChip(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String?,
    contentDesc: String,
    tint: androidx.compose.ui.graphics.Color,
    containerColor: androidx.compose.ui.graphics.Color = OctoSurfaceCard,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        color = containerColor,
        shape = RoundedCornerShape(8.dp),
        border = BorderStroke(1.dp, if (containerColor == OctoCyan) OctoCyan else OctoBorderSubtle),
        modifier = modifier.clickable(onClick = onClick)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 7.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = icon,
                contentDescription = contentDesc,
                tint = tint,
                modifier = Modifier.size(17.dp)
            )
            if (label != null) {
                Spacer(Modifier.width(5.dp))
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
                    color = tint
                )
            }
        }
    }
}

@Composable
fun CollapsibleBuildConsole(
    isExpanded: Boolean,
    buildState: BuildState,
    logs: List<ConsoleLogLine>,
    hasBuiltHex: Boolean,
    diagnosticsCount: Int = 0,
    onToggleExpand: () -> Unit,
    onClear: () -> Unit,
    onDiagnosticClick: (CompilerDiagnostic) -> Unit,
    onJumpDiagnostic: ((Boolean) -> Unit)? = null,
    onOpenInspector: () -> Unit = {},
    onOpenExportFlash: () -> Unit,
    modifier: Modifier = Modifier
) {
    val clipboard = LocalClipboardManager.current
    val listState = rememberLazyListState()

    LaunchedEffect(logs.size, isExpanded) {
        if (isExpanded && logs.isNotEmpty()) {
            listState.scrollToItem(logs.lastIndex)
        }
    }

    Surface(
        color = OctoAbyssNavy,
        modifier = modifier.fillMaxWidth()
    ) {
        Column {
            HorizontalDivider(color = OctoBorderSubtle)
            // Console Status Header
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(OctoSurfaceElevated)
                    .clickable(onClick = onToggleExpand)
                    .padding(horizontal = 10.dp, vertical = 6.dp)
                    .testTag("console_header"),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Default.Terminal,
                    contentDescription = null,
                    tint = OctoCyan,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(Modifier.width(6.dp))
                Text(
                    text = "Console",
                    style = MaterialTheme.typography.labelMedium,
                    color = OctoTextPrimary
                )
                Spacer(Modifier.width(8.dp))

                BuildStatusPill(buildState = buildState, modifier = Modifier.weight(1f))

                if (diagnosticsCount > 0 && onJumpDiagnostic != null) {
                    IconButton(
                        onClick = { onJumpDiagnostic(false) },
                        modifier = Modifier
                            .size(28.dp)
                            .testTag("console_prev_diag_btn")
                    ) {
                        Icon(
                            imageVector = Icons.Default.KeyboardArrowUp,
                            contentDescription = "Previous diagnostic",
                            tint = OctoWarning,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                    IconButton(
                        onClick = { onJumpDiagnostic(true) },
                        modifier = Modifier
                            .size(28.dp)
                            .testTag("console_next_diag_btn")
                    ) {
                        Icon(
                            imageVector = Icons.Default.KeyboardArrowDown,
                            contentDescription = "Next diagnostic",
                            tint = OctoWarning,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }

                if (hasBuiltHex && buildState !is BuildState.Running) {
                    Surface(
                        color = OctoSurfaceCard,
                        shape = RoundedCornerShape(6.dp),
                        border = BorderStroke(1.dp, OctoBorderSubtle),
                        modifier = Modifier
                            .clickable(onClick = onOpenInspector)
                            .padding(end = 6.dp)
                            .testTag("console_inspect_chip")
                    ) {
                        Text(
                            text = "Inspect",
                            style = MaterialTheme.typography.labelSmall,
                            color = OctoTextPrimary,
                            modifier = Modifier.padding(horizontal = 7.dp, vertical = 4.dp)
                        )
                    }
                    Surface(
                        color = OctoCobalt.copy(alpha = 0.22f),
                        shape = RoundedCornerShape(6.dp),
                        border = BorderStroke(1.dp, OctoCobalt.copy(alpha = 0.7f)),
                        modifier = Modifier
                            .clickable(onClick = onOpenExportFlash)
                            .padding(end = 6.dp)
                            .testTag("console_flash_export_chip")
                    ) {
                        Text(
                            text = "HEX / Flash",
                            style = MaterialTheme.typography.labelSmall,
                            color = OctoCyan,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                        )
                    }
                }

                if (isExpanded) {
                    IconButton(
                        onClick = {
                            val allText = logs.joinToString("\n") { it.text }
                            clipboard.setText(AnnotatedString(allText))
                        },
                        modifier = Modifier
                            .size(30.dp)
                            .testTag("console_copy_btn")
                    ) {
                        Icon(
                            imageVector = Icons.Default.ContentCopy,
                            contentDescription = "Copy Console Output",
                            tint = OctoTextSecondary,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                    IconButton(
                        onClick = onClear,
                        modifier = Modifier
                            .size(30.dp)
                            .testTag("console_clear_btn")
                    ) {
                        Icon(
                            imageVector = Icons.Default.DeleteSweep,
                            contentDescription = "Clear Console",
                            tint = OctoTextSecondary,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }

                Icon(
                    imageVector = if (isExpanded) Icons.Default.KeyboardArrowDown else Icons.Default.KeyboardArrowUp,
                    contentDescription = if (isExpanded) "Collapse Console" else "Expand Console",
                    tint = OctoTextSecondary
                )
            }

            AnimatedVisibility(visible = isExpanded) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(OctoAbyssNavy)
                ) {
                    // Memory Usage Strip if available
                    if (buildState is BuildState.Success && buildState.memoryUsage != null) {
                        MemoryUsageStrip(usage = buildState.memoryUsage)
                        HorizontalDivider(color = OctoBorderSubtle)
                    }

                    if (logs.isEmpty()) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(100.dp)
                                .padding(12.dp),
                            contentAlignment = Alignment.CenterStart
                        ) {
                            Text(
                                text = "Run Verify or Build to view avr-gcc output, warnings, and memory usage.",
                                style = MaterialTheme.typography.bodySmall,
                                color = OctoTextMuted
                            )
                        }
                    } else {
                        SelectionContainer {
                            LazyColumn(
                                state = listState,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .heightIn(min = 120.dp, max = 220.dp)
                                    .padding(horizontal = 10.dp, vertical = 6.dp)
                                    .testTag("console_output_list"),
                                verticalArrangement = Arrangement.spacedBy(2.dp)
                            ) {
                                items(logs, key = { it.id }) { item ->
                                    val color = when (item.level) {
                                        ConsoleLineLevel.ERROR -> OctoError
                                        ConsoleLineLevel.WARNING -> OctoWarning
                                        ConsoleLineLevel.SUCCESS -> OctoSuccess
                                        ConsoleLineLevel.COMMAND -> OctoCyan
                                        ConsoleLineLevel.INFO -> OctoTextSecondary
                                    }
                                    val diag = item.diagnostic
                                    Text(
                                        text = item.text,
                                        style = MaterialTheme.typography.labelSmall.copy(fontFamily = JetBrainsMonoFamily),
                                        color = color,
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .then(
                                                if (diag != null) {
                                                    Modifier
                                                        .clickable { onDiagnosticClick(diag) }
                                                        .padding(vertical = 2.dp)
                                                } else Modifier
                                            )
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun BuildStatusPill(
    buildState: BuildState,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically
    ) {
        when (buildState) {
            BuildState.Idle -> {
                Text(
                    text = "Idle",
                    style = MaterialTheme.typography.labelSmall,
                    color = OctoTextMuted
                )
            }
            is BuildState.Running -> {
                CircularProgressIndicator(
                    modifier = Modifier.size(12.dp),
                    strokeWidth = 2.dp,
                    color = OctoCyan
                )
                Spacer(Modifier.width(6.dp))
                Text(
                    text = buildState.stage,
                    style = MaterialTheme.typography.labelSmall,
                    color = OctoCyan,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            is BuildState.Success -> {
                Icon(Icons.Default.CheckCircle, null, tint = OctoSuccess, modifier = Modifier.size(14.dp))
                Spacer(Modifier.width(4.dp))
                Text(
                    text = "${buildState.action.name.lowercase().replaceFirstChar { it.uppercase() }} OK (${buildState.durationMs}ms)",
                    style = MaterialTheme.typography.labelSmall,
                    color = OctoSuccess,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            is BuildState.Failure -> {
                Icon(Icons.Default.Error, null, tint = OctoError, modifier = Modifier.size(14.dp))
                Spacer(Modifier.width(4.dp))
                Text(
                    text = buildState.summary,
                    style = MaterialTheme.typography.labelSmall,
                    color = OctoError,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            is BuildState.Cancelled -> {
                Text(
                    text = "Cancelled (${buildState.durationMs}ms)",
                    style = MaterialTheme.typography.labelSmall,
                    color = OctoWarning
                )
            }
        }
    }
}

@Composable
private fun MemoryUsageStrip(usage: MemoryUsage) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(OctoSurfaceDark)
            .padding(horizontal = 10.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(Icons.Default.Memory, null, tint = OctoCyan, modifier = Modifier.size(15.dp))
        MemoryBar(
            label = "Flash",
            used = usage.flashBytes,
            max = usage.flashMaxBytes,
            percent = usage.flashPercent,
            modifier = Modifier.weight(1f)
        )
        MemoryBar(
            label = "SRAM",
            used = usage.sramBytes,
            max = usage.sramMaxBytes,
            percent = usage.sramPercent,
            modifier = Modifier.weight(1f)
        )
    }
}

@Composable
private fun MemoryBar(
    label: String,
    used: Int,
    max: Int,
    percent: Float,
    modifier: Modifier = Modifier
) {
    val barColor = when {
        percent > 90f -> OctoError
        percent > 75f -> OctoWarning
        else -> OctoCyan
    }
    Column(modifier = modifier) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(label, style = MaterialTheme.typography.labelSmall, color = OctoTextSecondary)
            Text(
                "$used / $max B (${"%.1f".format(percent)}%)",
                style = MaterialTheme.typography.labelSmall,
                color = OctoTextPrimary
            )
        }
        Spacer(Modifier.height(3.dp))
        LinearProgressIndicator(
            progress = { (percent / 100f).coerceIn(0f, 1f) },
            color = barColor,
            trackColor = OctoSurfaceCard,
            modifier = Modifier
                .fillMaxWidth()
                .height(4.dp)
                .clip(RoundedCornerShape(2.dp))
        )
    }
}
