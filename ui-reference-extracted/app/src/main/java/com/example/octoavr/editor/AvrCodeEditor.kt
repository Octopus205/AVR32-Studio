package com.example.octoavr.editor

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Redo
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material.icons.filled.AutoFixHigh
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.FindReplace
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.SelectAll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.octoavr.model.EditorSettings
import com.example.octoavr.model.ProjectFile
import com.example.ui.theme.EditorCurrentLineBg
import com.example.ui.theme.EditorErrorLineBg
import com.example.ui.theme.EditorGutterBg
import com.example.ui.theme.JetBrainsMonoFamily
import com.example.ui.theme.OctoAbyssNavy
import com.example.ui.theme.OctoBorderSubtle
import com.example.ui.theme.OctoCyan
import com.example.ui.theme.OctoError
import com.example.ui.theme.OctoSurfaceCard
import com.example.ui.theme.OctoSurfaceDark
import com.example.ui.theme.OctoSurfaceElevated
import com.example.ui.theme.OctoTextMuted
import com.example.ui.theme.OctoTextPrimary
import com.example.ui.theme.OctoTextSecondary
import com.example.ui.theme.OctoWarning
import kotlinx.coroutines.launch

@Composable
fun AvrCodeEditorPane(
    files: List<ProjectFile>,
    activeFilePath: String,
    editorSettings: EditorSettings,
    highlightedErrorLine: Int?,
    focusLineRequest: Pair<Int, Long>?,
    snippetInsertRequest: Pair<String, Long>? = null,
    onSelectFile: (String) -> Unit,
    onContentChanged: (String, String) -> Unit,
    modifier: Modifier = Modifier
) {
    val activeFile = files.firstOrNull { it.relativePath == activeFilePath } ?: files.firstOrNull()
    val coroutineScope = rememberCoroutineScope()

    var textFieldValue by remember(activeFile?.relativePath) {
        mutableStateOf(TextFieldValue(activeFile?.content ?: ""))
    }
    val undoHistory = remember(activeFile?.relativePath) {
        EditorUndoHistory(TextFieldValue(activeFile?.content ?: ""))
    }

    LaunchedEffect(activeFile?.relativePath, activeFile?.content) {
        val diskText = activeFile?.content ?: ""
        if (diskText != textFieldValue.text) {
            val next = TextFieldValue(diskText, TextRange(textFieldValue.selection.start.coerceIn(0, diskText.length)))
            textFieldValue = next
            undoHistory.reset(next)
        }
    }

    var showSearchBar by remember { mutableStateOf(false) }
    var searchQuery by remember { mutableStateOf("") }
    var replaceQuery by remember { mutableStateOf("") }
    var matchIndex by remember { mutableIntStateOf(0) }

    val verticalScroll = rememberScrollState()
    val horizontalScroll = rememberScrollState()

    val lineHeightSp = (editorSettings.fontSizeSp + 6).sp
    val lineHeightPxApprox = (editorSettings.fontSizeSp + 6) * 2.6f

    // Jump to line when requested from compiler diagnostic click
    LaunchedEffect(focusLineRequest) {
        val req = focusLineRequest ?: return@LaunchedEffect
        val targetLine = req.first.coerceAtLeast(1)
        val lines = textFieldValue.text.lines()
        var offset = 0
        for (i in 0 until (targetLine - 1).coerceAtMost(lines.size)) {
            offset += lines[i].length + 1
        }
        val clamped = offset.coerceIn(0, textFieldValue.text.length)
        textFieldValue = textFieldValue.copy(selection = TextRange(clamped))
        val targetY = ((targetLine - 3).coerceAtLeast(0) * lineHeightPxApprox).toInt()
        verticalScroll.animateScrollTo(targetY)
    }

    // Insert snippet at current cursor/selection when requested from ATmega32A Reference dialog
    LaunchedEffect(snippetInsertRequest) {
        val req = snippetInsertRequest ?: return@LaunchedEffect
        val snippet = req.first
        val sel = textFieldValue.selection
        val start = sel.min.coerceIn(0, textFieldValue.text.length)
        val end = sel.max.coerceIn(0, textFieldValue.text.length)
        val updated = textFieldValue.text.replaceRange(start, end, snippet)
        val next = TextFieldValue(updated, TextRange(start + snippet.length))
        undoHistory.recordChange(next)
        textFieldValue = next
        activeFile?.let { onContentChanged(it.relativePath, updated) }
    }

    val lines = remember(textFieldValue.text) {
        textFieldValue.text.split("\n")
    }
    val currentLine = remember(textFieldValue.text, textFieldValue.selection) {
        val pos = textFieldValue.selection.start.coerceIn(0, textFieldValue.text.length)
        textFieldValue.text.substring(0, pos).count { it == '\n' } + 1
    }
    val currentColumn = remember(textFieldValue.text, textFieldValue.selection) {
        val pos = textFieldValue.selection.start.coerceIn(0, textFieldValue.text.length)
        val lastNl = textFieldValue.text.lastIndexOf('\n', (pos - 1).coerceAtLeast(0))
        if (lastNl < 0) pos + 1 else pos - lastNl
    }

    val searchMatches = remember(textFieldValue.text, searchQuery) {
        EditorFindReplace.findAllOffsets(textFieldValue.text, searchQuery, matchCase = false)
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(OctoSurfaceDark)
    ) {
        // File tabs + quick editor actions
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(OctoSurfaceElevated)
                .padding(horizontal = 6.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            LazyRow(
                modifier = Modifier.weight(1f),
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                items(files, key = { it.relativePath }) { pf ->
                    val isSelected = pf.relativePath == activeFile?.relativePath
                    Surface(
                        color = if (isSelected) OctoSurfaceCard else OctoAbyssNavy,
                        shape = RoundedCornerShape(6.dp),
                        border = androidx.compose.foundation.BorderStroke(
                            1.dp,
                            if (isSelected) OctoCyan.copy(alpha = 0.7f) else OctoBorderSubtle
                        ),
                        modifier = Modifier
                            .testTag("file_tab_${pf.name}")
                            .clickable { onSelectFile(pf.relativePath) }
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = pf.relativePath,
                                style = MaterialTheme.typography.labelMedium.copy(fontFamily = JetBrainsMonoFamily),
                                color = if (isSelected) OctoCyan else OctoTextSecondary,
                                maxLines = 1
                            )
                            if (pf.isDirty) {
                                Spacer(Modifier.width(5.dp))
                                Box(
                                    modifier = Modifier
                                        .size(6.dp)
                                        .background(OctoWarning, RoundedCornerShape(3.dp))
                                )
                            }
                        }
                    }
                }
            }

            IconButton(
                onClick = {
                    undoHistory.undo()?.let { restored ->
                        textFieldValue = restored
                        activeFile?.let { onContentChanged(it.relativePath, restored.text) }
                    }
                },
                enabled = undoHistory.canUndo,
                modifier = Modifier
                    .size(36.dp)
                    .testTag("editor_undo_btn")
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.Undo,
                    contentDescription = "Undo",
                    tint = if (undoHistory.canUndo) OctoTextPrimary else OctoTextMuted,
                    modifier = Modifier.size(18.dp)
                )
            }

            IconButton(
                onClick = {
                    undoHistory.redo()?.let { restored ->
                        textFieldValue = restored
                        activeFile?.let { onContentChanged(it.relativePath, restored.text) }
                    }
                },
                enabled = undoHistory.canRedo,
                modifier = Modifier
                    .size(36.dp)
                    .testTag("editor_redo_btn")
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.Redo,
                    contentDescription = "Redo",
                    tint = if (undoHistory.canRedo) OctoTextPrimary else OctoTextMuted,
                    modifier = Modifier.size(18.dp)
                )
            }

            IconButton(
                onClick = {
                    val formatted = CCodeAssistant.formatCSource(textFieldValue.text, editorSettings.tabWidth)
                    val next = TextFieldValue(formatted, TextRange(textFieldValue.selection.start.coerceIn(0, formatted.length)))
                    undoHistory.recordChange(next)
                    textFieldValue = next
                    activeFile?.let { onContentChanged(it.relativePath, formatted) }
                },
                modifier = Modifier
                    .size(36.dp)
                    .testTag("editor_format_btn")
            ) {
                Icon(
                    imageVector = Icons.Default.AutoFixHigh,
                    contentDescription = "Format C/C++ Code",
                    tint = OctoTextSecondary,
                    modifier = Modifier.size(18.dp)
                )
            }

            IconButton(
                onClick = {
                    textFieldValue = textFieldValue.copy(selection = TextRange(0, textFieldValue.text.length))
                },
                modifier = Modifier
                    .size(36.dp)
                    .testTag("editor_select_all_btn")
            ) {
                Icon(
                    imageVector = Icons.Default.SelectAll,
                    contentDescription = "Select All",
                    tint = OctoTextSecondary,
                    modifier = Modifier.size(18.dp)
                )
            }

            IconButton(
                onClick = { showSearchBar = !showSearchBar },
                modifier = Modifier
                    .size(36.dp)
                    .testTag("editor_find_replace_btn")
            ) {
                Icon(
                    imageVector = Icons.Default.FindReplace,
                    contentDescription = "Find and Replace",
                    tint = if (showSearchBar) OctoCyan else OctoTextSecondary,
                    modifier = Modifier.size(18.dp)
                )
            }
        }

        HorizontalDivider(color = OctoBorderSubtle)

        // Find & Replace Bar
        AnimatedVisibility(visible = showSearchBar) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(OctoAbyssNavy)
                    .padding(horizontal = 10.dp, vertical = 6.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    CompactInputField(
                        value = searchQuery,
                        onValueChange = {
                            searchQuery = it
                            matchIndex = 0
                        },
                        placeholder = "Find…",
                        modifier = Modifier
                            .weight(1f)
                            .testTag("editor_find_input")
                    )
                    Text(
                        text = if (searchQuery.isEmpty()) "" else "${if (searchMatches.isEmpty()) 0 else (matchIndex % searchMatches.size) + 1}/${searchMatches.size}",
                        style = MaterialTheme.typography.labelSmall,
                        color = OctoTextSecondary
                    )
                    IconButton(
                        onClick = {
                            if (searchMatches.isNotEmpty()) {
                                matchIndex = (matchIndex - 1 + searchMatches.size) % searchMatches.size
                                val r = searchMatches[matchIndex]
                                textFieldValue = textFieldValue.copy(selection = TextRange(r.first, r.last + 1))
                            }
                        },
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(Icons.Default.KeyboardArrowUp, "Previous match", tint = OctoTextPrimary)
                    }
                    IconButton(
                        onClick = {
                            if (searchMatches.isNotEmpty()) {
                                matchIndex = (matchIndex + 1) % searchMatches.size
                                val r = searchMatches[matchIndex]
                                textFieldValue = textFieldValue.copy(selection = TextRange(r.first, r.last + 1))
                            }
                        },
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(Icons.Default.KeyboardArrowDown, "Next match", tint = OctoTextPrimary)
                    }
                    IconButton(
                        onClick = { showSearchBar = false },
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(Icons.Default.Close, "Close find", tint = OctoTextSecondary)
                    }
                }
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    CompactInputField(
                        value = replaceQuery,
                        onValueChange = { replaceQuery = it },
                        placeholder = "Replace with…",
                        modifier = Modifier
                            .weight(1f)
                            .testTag("editor_replace_input")
                    )
                    TextButton(
                        onClick = {
                            if (searchMatches.isNotEmpty()) {
                                val safeIdx = matchIndex % searchMatches.size
                                val r = searchMatches[safeIdx]
                                val updated = textFieldValue.text.replaceRange(r.first, r.last + 1, replaceQuery)
                                val next = TextFieldValue(updated, TextRange(r.first + replaceQuery.length))
                                undoHistory.recordChange(next)
                                textFieldValue = next
                                activeFile?.let { onContentChanged(it.relativePath, updated) }
                            }
                        },
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)
                    ) {
                        Text("Replace", color = OctoCyan, style = MaterialTheme.typography.labelMedium)
                    }
                    TextButton(
                        onClick = {
                            if (searchQuery.isNotEmpty()) {
                                val updated = textFieldValue.text.replace(searchQuery, replaceQuery, ignoreCase = false)
                                val next = TextFieldValue(updated, TextRange(0))
                                undoHistory.recordChange(next)
                                textFieldValue = next
                                activeFile?.let { onContentChanged(it.relativePath, updated) }
                            }
                        },
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)
                    ) {
                        Text("All", color = OctoCyan, style = MaterialTheme.typography.labelMedium)
                    }
                }
            }
        }

        // Main Code Editor Surface with synchronized line-number gutter
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
        ) {
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(verticalScroll)
            ) {
                if (editorSettings.showLineNumbers) {
                    Column(
                        modifier = Modifier
                            .background(EditorGutterBg)
                            .padding(horizontal = 8.dp, vertical = 10.dp),
                        horizontalAlignment = Alignment.End
                    ) {
                        for (lineNum in 1..lines.size) {
                            val isCurrent = lineNum == currentLine
                            val isError = lineNum == highlightedErrorLine
                            Text(
                                text = lineNum.toString(),
                                style = TextStyle(
                                    fontFamily = JetBrainsMonoFamily,
                                    fontSize = editorSettings.fontSizeSp.sp,
                                    lineHeight = lineHeightSp,
                                    fontWeight = if (isCurrent || isError) FontWeight.Bold else FontWeight.Normal,
                                    color = when {
                                        isError -> OctoError
                                        isCurrent -> OctoCyan
                                        else -> OctoTextMuted
                                    }
                                ),
                                modifier = Modifier.widthIn(min = 28.dp)
                            )
                        }
                    }
                    Box(
                        modifier = Modifier
                            .width(1.dp)
                            .fillMaxHeight()
                            .background(OctoBorderSubtle)
                    )
                }

                Box(
                    modifier = Modifier
                        .weight(1f)
                        .horizontalScroll(horizontalScroll)
                        .padding(horizontal = 10.dp, vertical = 10.dp)
                ) {
                    BasicTextField(
                        value = textFieldValue,
                        onValueChange = { rawNext ->
                            val assisted = CCodeAssistant.applySmartEdit(
                                previous = textFieldValue,
                                incoming = rawNext,
                                tabWidth = editorSettings.tabWidth,
                                autoCloseBrackets = editorSettings.autoCloseBrackets,
                                autoIndent = editorSettings.autoIndent
                            )
                            undoHistory.recordChange(assisted)
                            textFieldValue = assisted
                            activeFile?.let { onContentChanged(it.relativePath, assisted.text) }
                        },
                        textStyle = TextStyle(
                            fontFamily = JetBrainsMonoFamily,
                            fontSize = editorSettings.fontSizeSp.sp,
                            lineHeight = lineHeightSp,
                            color = OctoTextPrimary
                        ),
                        cursorBrush = SolidColor(OctoCyan),
                        visualTransformation = AvrSyntaxHighlighter,
                        keyboardOptions = KeyboardOptions(
                            capitalization = KeyboardCapitalization.None,
                            autoCorrectEnabled = false,
                            keyboardType = KeyboardType.Ascii
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .widthIn(min = 700.dp)
                            .testTag("code_editor_textfield")
                            .drawBehind {
                                val lineH = lineHeightSp.toPx()
                                if (editorSettings.highlightCurrentLine && currentLine in 1..lines.size) {
                                    drawRect(
                                        color = EditorCurrentLineBg,
                                        topLeft = Offset(0f, (currentLine - 1) * lineH),
                                        size = Size(size.width, lineH)
                                    )
                                }
                                if (highlightedErrorLine != null && highlightedErrorLine in 1..lines.size) {
                                    drawRect(
                                        color = EditorErrorLineBg,
                                        topLeft = Offset(0f, (highlightedErrorLine - 1) * lineH),
                                        size = Size(size.width, lineH)
                                    )
                                }
                            }
                    )
                }
            }
        }

        // Quick Embedded C Symbol Strip + Cursor Status
        HorizontalDivider(color = OctoBorderSubtle)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(OctoSurfaceElevated)
                .padding(horizontal = 6.dp, vertical = 3.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            val symbols = remember {
                listOf("TAB", "{", "}", "(", ")", ";", "#", "<", ">", "=", "&", "|", "~", "1 << ")
            }
            LazyRow(
                modifier = Modifier.weight(1f),
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                items(symbols) { sym ->
                    val insertStr = if (sym == "TAB") " ".repeat(editorSettings.tabWidth) else sym
                    Surface(
                        color = OctoAbyssNavy,
                        shape = RoundedCornerShape(4.dp),
                        border = androidx.compose.foundation.BorderStroke(1.dp, OctoBorderSubtle),
                        modifier = Modifier.clickable {
                            val sel = textFieldValue.selection
                            val start = sel.min.coerceIn(0, textFieldValue.text.length)
                            val end = sel.max.coerceIn(0, textFieldValue.text.length)
                            val updated = textFieldValue.text.replaceRange(start, end, insertStr)
                            val next = TextFieldValue(updated, TextRange(start + insertStr.length))
                            undoHistory.recordChange(next)
                            textFieldValue = next
                            activeFile?.let { onContentChanged(it.relativePath, updated) }
                        }
                    ) {
                        Text(
                            text = sym,
                            style = MaterialTheme.typography.labelSmall.copy(fontFamily = JetBrainsMonoFamily),
                            color = OctoCyan,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 5.dp)
                        )
                    }
                }
            }
            Spacer(Modifier.width(8.dp))
            Text(
                text = "Ln $currentLine, Col $currentColumn",
                style = MaterialTheme.typography.labelSmall,
                color = OctoTextSecondary,
                modifier = Modifier.padding(end = 4.dp)
            )
        }
    }
}

@Composable
private fun CompactInputField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .height(34.dp)
            .background(OctoSurfaceDark, RoundedCornerShape(6.dp))
            .border(1.dp, OctoBorderSubtle, RoundedCornerShape(6.dp))
            .padding(horizontal = 8.dp),
        contentAlignment = Alignment.CenterStart
    ) {
        if (value.isEmpty()) {
            Text(placeholder, style = MaterialTheme.typography.bodySmall, color = OctoTextMuted)
        }
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            singleLine = true,
            textStyle = TextStyle(
                fontFamily = JetBrainsMonoFamily,
                fontSize = 12.sp,
                color = OctoTextPrimary
            ),
            cursorBrush = SolidColor(OctoCyan),
            modifier = Modifier.fillMaxWidth()
        )
    }
}
