package com.example.octoavr.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.TabRowDefaults
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp
import com.example.octoavr.compiler.HexAndElfParser
import com.example.octoavr.model.AvrProject
import com.example.octoavr.model.BuildState
import com.example.ui.theme.JetBrainsMonoFamily
import com.example.ui.theme.OctoAbyssNavy
import com.example.ui.theme.OctoBorderSubtle
import com.example.ui.theme.OctoCyan
import com.example.ui.theme.OctoSuccess
import com.example.ui.theme.OctoSurfaceCard
import com.example.ui.theme.OctoSurfaceDark
import com.example.ui.theme.OctoSurfaceElevated
import com.example.ui.theme.OctoTextPrimary
import com.example.ui.theme.OctoTextSecondary
import com.example.ui.theme.OctoWarning
import java.io.File

@Composable
fun FirmwareInspectorDialog(
    project: AvrProject?,
    buildState: BuildState,
    disassemblyText: String?,
    onGenerateDisassembly: () -> Unit,
    onDismiss: () -> Unit
) {
    var selectedTab by remember { mutableIntStateOf(0) }
    val tabs = listOf("Sections & Info", "Intel HEX", "Disassembly")
    val clipboard = LocalClipboardManager.current

    val hexFile = project?.lastHexPath?.let(::File)?.takeIf { it.exists() }
    val elfFile = project?.lastElfPath?.let(::File)?.takeIf { it.exists() }
    val hexText = remember(hexFile?.absolutePath, hexFile?.lastModified()) {
        hexFile?.readText()
    }
    val hexValidation = remember(hexFile?.absolutePath, hexFile?.lastModified()) {
        hexFile?.let { HexAndElfParser.validateIntelHex(it) }
    }
    val elfValid = remember(elfFile?.absolutePath, elfFile?.lastModified()) {
        elfFile?.let { HexAndElfParser.isValidAvrElf(it) } ?: false
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = OctoSurfaceElevated,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Memory, null, tint = OctoCyan)
                Spacer(Modifier.width(8.dp))
                Text("Firmware Inspector", color = OctoTextPrimary)
            }
        },
        text = {
            Column(modifier = Modifier.fillMaxWidth()) {
                TabRow(
                    selectedTabIndex = selectedTab,
                    containerColor = OctoAbyssNavy,
                    contentColor = OctoCyan,
                    indicator = { positions ->
                        TabRowDefaults.SecondaryIndicator(
                            modifier = Modifier.tabIndicatorOffset(positions[selectedTab]),
                            color = OctoCyan
                        )
                    }
                ) {
                    tabs.forEachIndexed { idx, title ->
                        Tab(
                            selected = selectedTab == idx,
                            onClick = { selectedTab = idx },
                            text = {
                                Text(
                                    title,
                                    style = MaterialTheme.typography.labelMedium,
                                    color = if (selectedTab == idx) OctoCyan else OctoTextSecondary
                                )
                            }
                        )
                    }
                }

                Spacer(Modifier.height(10.dp))

                when (selectedTab) {
                    0 -> {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(max = 380.dp)
                                .verticalScroll(rememberScrollState()),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            val usage = (buildState as? BuildState.Success)?.memoryUsage
                            if (usage != null) {
                                Surface(
                                    color = OctoSurfaceDark,
                                    shape = RoundedCornerShape(8.dp),
                                    border = BorderStroke(1.dp, OctoBorderSubtle)
                                ) {
                                    Column(Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                        Text("Memory Sections (${project?.buildConfig?.targetMcu?.displayName ?: "ATmega32A"})", style = MaterialTheme.typography.titleSmall, color = OctoCyan)
                                        Text(".text (Code): ${usage.textBytes} B", style = MaterialTheme.typography.labelSmall, color = OctoTextPrimary)
                                        Text(".data (Init SRAM): ${usage.dataBytes} B", style = MaterialTheme.typography.labelSmall, color = OctoTextPrimary)
                                        Text(".bss (Zero-Init SRAM): ${usage.bssBytes} B", style = MaterialTheme.typography.labelSmall, color = OctoTextPrimary)
                                        Spacer(Modifier.height(4.dp))
                                        Text("Flash: ${usage.flashBytes} / ${usage.flashMaxBytes} B (${"%.1f".format(usage.flashPercent)}%)", style = MaterialTheme.typography.labelSmall, color = OctoCyan)
                                        LinearProgressIndicator(
                                            progress = { (usage.flashPercent / 100f).coerceIn(0f, 1f) },
                                            color = OctoCyan,
                                            trackColor = OctoSurfaceCard,
                                            modifier = Modifier.fillMaxWidth().height(5.dp).clip(RoundedCornerShape(3.dp))
                                        )
                                        Text("SRAM: ${usage.sramBytes} / ${usage.sramMaxBytes} B (${"%.1f".format(usage.sramPercent)}%)", style = MaterialTheme.typography.labelSmall, color = OctoCyan)
                                        LinearProgressIndicator(
                                            progress = { (usage.sramPercent / 100f).coerceIn(0f, 1f) },
                                            color = OctoCyan,
                                            trackColor = OctoSurfaceCard,
                                            modifier = Modifier.fillMaxWidth().height(5.dp).clip(RoundedCornerShape(3.dp))
                                        )
                                    }
                                }
                            } else {
                                Text("Run Build to inspect ELF memory sections and Intel HEX firmware.", style = MaterialTheme.typography.bodySmall, color = OctoWarning)
                            }

                            if (elfFile != null) {
                                Surface(
                                    color = OctoAbyssNavy,
                                    shape = RoundedCornerShape(8.dp),
                                    border = BorderStroke(1.dp, OctoBorderSubtle)
                                ) {
                                    Row(Modifier.padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
                                        Icon(Icons.Default.CheckCircle, null, tint = if (elfValid) OctoSuccess else OctoWarning, modifier = Modifier.size(16.dp))
                                        Spacer(Modifier.width(8.dp))
                                        Column {
                                            Text("ELF: ${elfFile.name} (${elfFile.length()} B)", style = MaterialTheme.typography.labelMedium, color = OctoTextPrimary)
                                            Text(if (elfValid) "Valid 32-bit AVR ELF (EM_AVR=83)" else "Invalid ELF header", style = MaterialTheme.typography.labelSmall, color = OctoTextSecondary)
                                        }
                                    }
                                }
                            }

                            if (hexFile != null && hexValidation != null) {
                                Surface(
                                    color = OctoAbyssNavy,
                                    shape = RoundedCornerShape(8.dp),
                                    border = BorderStroke(1.dp, OctoBorderSubtle)
                                ) {
                                    Row(Modifier.padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
                                        Icon(Icons.Default.CheckCircle, null, tint = if (hexValidation.isValid) OctoSuccess else OctoWarning, modifier = Modifier.size(16.dp))
                                        Spacer(Modifier.width(8.dp))
                                        Column {
                                            Text("HEX: ${hexFile.name} (${hexFile.length()} B)", style = MaterialTheme.typography.labelMedium, color = OctoTextPrimary)
                                            Text("${hexValidation.recordCount} records • ${hexValidation.dataByteCount} program bytes • EOF=${hexValidation.hasEofRecord}", style = MaterialTheme.typography.labelSmall, color = OctoTextSecondary)
                                        }
                                    }
                                }
                            }
                        }
                    }

                    1 -> {
                        if (hexText == null) {
                            Text("No .hex file generated yet. Run Build first.", style = MaterialTheme.typography.bodySmall, color = OctoWarning)
                        } else {
                            Column(modifier = Modifier.fillMaxWidth()) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text("${hexFile?.name}", style = MaterialTheme.typography.labelSmall, color = OctoCyan, modifier = Modifier.weight(1f))
                                    IconButton(onClick = { clipboard.setText(AnnotatedString(hexText)) }, modifier = Modifier.size(30.dp)) {
                                        Icon(Icons.Default.ContentCopy, "Copy HEX", tint = OctoTextSecondary, modifier = Modifier.size(16.dp))
                                    }
                                }
                                SelectionContainer {
                                    Text(
                                        text = hexText,
                                        style = MaterialTheme.typography.labelSmall.copy(fontFamily = JetBrainsMonoFamily),
                                        color = OctoTextPrimary,
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .heightIn(max = 350.dp)
                                            .background(OctoAbyssNavy, RoundedCornerShape(6.dp))
                                            .verticalScroll(rememberScrollState())
                                            .horizontalScroll(rememberScrollState())
                                            .padding(8.dp)
                                    )
                                }
                            }
                        }
                    }

                    2 -> {
                        if (disassemblyText == null) {
                            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Text(
                                    "Generate assembly listing (avr-objdump -h -S) from the compiled .elf binary.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = OctoTextSecondary
                                )
                                Button(
                                    onClick = onGenerateDisassembly,
                                    enabled = elfFile != null,
                                    colors = ButtonDefaults.buttonColors(containerColor = OctoCyan, contentColor = OctoAbyssNavy)
                                ) {
                                    Text("Disassemble ELF")
                                }
                            }
                        } else {
                            Column(modifier = Modifier.fillMaxWidth()) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text("avr-objdump -h -S", style = MaterialTheme.typography.labelSmall, color = OctoCyan, modifier = Modifier.weight(1f))
                                    IconButton(onClick = { clipboard.setText(AnnotatedString(disassemblyText)) }, modifier = Modifier.size(30.dp)) {
                                        Icon(Icons.Default.ContentCopy, "Copy Disassembly", tint = OctoTextSecondary, modifier = Modifier.size(16.dp))
                                    }
                                }
                                SelectionContainer {
                                    Text(
                                        text = disassemblyText.take(24_000),
                                        style = MaterialTheme.typography.labelSmall.copy(fontFamily = JetBrainsMonoFamily),
                                        color = OctoTextPrimary,
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .heightIn(max = 350.dp)
                                            .background(OctoAbyssNavy, RoundedCornerShape(6.dp))
                                            .verticalScroll(rememberScrollState())
                                            .horizontalScroll(rememberScrollState())
                                            .padding(8.dp)
                                    )
                                }
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
