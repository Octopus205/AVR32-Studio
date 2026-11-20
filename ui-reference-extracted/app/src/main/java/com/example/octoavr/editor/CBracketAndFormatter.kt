package com.example.octoavr.editor

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.OffsetMapping
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.input.TransformedText
import androidx.compose.ui.text.input.VisualTransformation
import com.example.ui.theme.OctoTextPrimary
import com.example.ui.theme.SyntaxComment
import com.example.ui.theme.SyntaxKeyword
import com.example.ui.theme.SyntaxNumber
import com.example.ui.theme.SyntaxPreprocessor
import com.example.ui.theme.SyntaxRegister
import com.example.ui.theme.SyntaxString
import com.example.ui.theme.SyntaxType

object AvrSyntaxHighlighter : VisualTransformation {

    private val keywords = setOf(
        "if", "else", "for", "while", "do", "switch", "case", "default",
        "break", "continue", "return", "goto", "sizeof", "typedef",
        "struct", "union", "enum", "extern", "static", "inline",
        "const", "volatile", "register", "auto", "class", "public",
        "private", "protected", "namespace", "using", "template",
        "true", "false", "nullptr", "NULL"
    )

    private val types = setOf(
        "void", "int", "char", "short", "long", "float", "double",
        "signed", "unsigned", "bool", "uint8_t", "int8_t", "uint16_t",
        "int16_t", "uint32_t", "int32_t", "uint64_t", "int64_t", "size_t",
        "ISR", "SIGNAL", "PROGMEM", "EEMEM"
    )

    private val avrRegisters = setOf(
        "F_CPU", "DDRA", "DDRB", "DDRC", "DDRD",
        "PORTA", "PORTB", "PORTC", "PORTD",
        "PINA", "PINB", "PINC", "PIND",
        "PA0", "PA1", "PA2", "PA3", "PA4", "PA5", "PA6", "PA7",
        "PB0", "PB1", "PB2", "PB3", "PB4", "PB5", "PB6", "PB7",
        "PC0", "PC1", "PC2", "PC3", "PC4", "PC5", "PC6", "PC7",
        "PD0", "PD1", "PD2", "PD3", "PD4", "PD5", "PD6", "PD7",
        "MCUCR", "MCUCSR", "GICR", "GIFR", "INT0", "INT1", "INT2",
        "ISC00", "ISC01", "ISC10", "ISC11",
        "TCCR0", "TCNT0", "OCR0", "TCCR1A", "TCCR1B", "TCNT1", "OCR1A", "OCR1B", "ICR1",
        "TCCR2", "TCNT2", "OCR2", "TIMSK", "TIFR", "OCIE0", "OCIE1A", "TOIE0",
        "WGM00", "WGM01", "WGM12", "COM01", "COM1A0", "CS00", "CS01", "CS02", "CS12",
        "UCSRA", "UCSRB", "UCSRC", "UBRRH", "UBRRL", "UDR",
        "RXEN", "TXEN", "UDRE", "RXC", "TXC", "URSEL", "UCSZ0", "UCSZ1",
        "SPCR", "SPSR", "SPDR", "SPE", "MSTR", "SPR0", "SPR1", "SPIF",
        "ADMUX", "ADCSRA", "ADCL", "ADCH", "ADC", "REFS0", "REFS1", "ADEN", "ADSC", "ADPS0", "ADPS1", "ADPS2",
        "SREG", "INT0_vect", "TIMER0_COMP_vect", "TIMER0_OVF_vect", "TIMER1_COMPA_vect", "USART_RXC_vect", "ADC_vect",
        "sei", "cli", "_delay_ms", "_delay_us", "_BV", "bit_is_set", "bit_is_clear"
    )

    override fun filter(text: AnnotatedString): TransformedText {
        val raw = text.text
        val builder = AnnotatedString.Builder(raw)
        builder.addStyle(SpanStyle(color = OctoTextPrimary), 0, raw.length)

        var i = 0
        val n = raw.length
        while (i < n) {
            val c = raw[i]
            when {
                // Line comment
                c == '/' && i + 1 < n && raw[i + 1] == '/' -> {
                    val start = i
                    while (i < n && raw[i] != '\n') i++
                    builder.addStyle(SpanStyle(color = SyntaxComment), start, i)
                }
                // Block comment
                c == '/' && i + 1 < n && raw[i + 1] == '*' -> {
                    val start = i
                    i += 2
                    while (i + 1 < n && !(raw[i] == '*' && raw[i + 1] == '/')) i++
                    i = (i + 2).coerceAtMost(n)
                    builder.addStyle(SpanStyle(color = SyntaxComment), start, i)
                }
                // Preprocessor directive at line start
                c == '#' && isLineStart(raw, i) -> {
                    val start = i
                    while (i < n && raw[i] != '\n') {
                        if (raw[i] == '<') {
                            val headerStart = i
                            while (i < n && raw[i] != '>' && raw[i] != '\n') i++
                            if (i < n && raw[i] == '>') i++
                            builder.addStyle(SpanStyle(color = SyntaxPreprocessor), start, headerStart)
                            builder.addStyle(SpanStyle(color = SyntaxString), headerStart, i)
                            break
                        }
                        i++
                    }
                    if (i > start && raw.substring(start, i).indexOf('<') < 0) {
                        builder.addStyle(SpanStyle(color = SyntaxPreprocessor), start, i)
                    }
                }
                // String or char literal
                c == '"' || c == '\'' -> {
                    val quote = c
                    val start = i++
                    while (i < n && raw[i] != quote && raw[i] != '\n') {
                        if (raw[i] == '\\' && i + 1 < n) i++
                        i++
                    }
                    if (i < n && raw[i] == quote) i++
                    builder.addStyle(SpanStyle(color = SyntaxString), start, i)
                }
                // Number literal
                c.isDigit() -> {
                    val start = i
                    while (i < n && (raw[i].isLetterOrDigit() || raw[i] == '.' || raw[i] == '_')) i++
                    builder.addStyle(SpanStyle(color = SyntaxNumber), start, i)
                }
                // Identifier / keyword / AVR register
                c.isLetter() || c == '_' -> {
                    val start = i
                    while (i < n && (raw[i].isLetterOrDigit() || raw[i] == '_')) i++
                    val token = raw.substring(start, i)
                    val style: SpanStyle? = when {
                        token in keywords -> SpanStyle(color = SyntaxKeyword, fontWeight = FontWeight.Medium)
                        token in types -> SpanStyle(color = SyntaxType, fontWeight = FontWeight.Medium)
                        token in avrRegisters -> SpanStyle(color = SyntaxRegister)
                        else -> null
                    }
                    if (style != null) {
                        builder.addStyle(style, start, i)
                    }
                }
                else -> i++
            }
        }
        return TransformedText(builder.toAnnotatedString(), OffsetMapping.Identity)
    }

    private fun isLineStart(text: String, idx: Int): Boolean {
        var j = idx - 1
        while (j >= 0 && (text[j] == ' ' || text[j] == '\t')) j--
        return j < 0 || text[j] == '\n'
    }
}

object CCodeAssistant {

    fun applySmartEdit(
        previous: TextFieldValue,
        incoming: TextFieldValue,
        tabWidth: Int,
        autoCloseBrackets: Boolean,
        autoIndent: Boolean
    ): TextFieldValue {
        val oldText = previous.text
        val newText = incoming.text
        if (newText.length == oldText.length + 1 && incoming.selection.collapsed) {
            val cursor = incoming.selection.start
            if (cursor > 0 && cursor <= newText.length) {
                val inserted = newText[cursor - 1]
                // Auto-indent on newline
                if (autoIndent && inserted == '\n') {
                    val lineStart = oldText.lastIndexOf('\n', (cursor - 2).coerceAtLeast(0)) + 1
                    val prevLine = oldText.substring(lineStart, (cursor - 1).coerceAtLeast(lineStart))
                    val baseIndent = prevLine.takeWhile { it == ' ' || it == '\t' }
                    val trimmed = prevLine.trimEnd()
                    val extra = if (trimmed.endsWith("{")) " ".repeat(tabWidth) else ""
                    val indent = baseIndent + extra

                    val nextChar = newText.getOrNull(cursor)
                    return if (trimmed.endsWith("{") && nextChar == '}') {
                        val combined = "$indent\n$baseIndent"
                        val updated = StringBuilder(newText).insert(cursor, combined).toString()
                        val newCursor = cursor + indent.length
                        incoming.copy(
                            text = updated,
                            selection = androidx.compose.ui.text.TextRange(newCursor)
                        )
                    } else if (indent.isNotEmpty()) {
                        val updated = StringBuilder(newText).insert(cursor, indent).toString()
                        val newCursor = cursor + indent.length
                        incoming.copy(
                            text = updated,
                            selection = androidx.compose.ui.text.TextRange(newCursor)
                        )
                    } else incoming
                }

                // Auto-closing brackets & quotes
                if (autoCloseBrackets) {
                    val closing: Char? = when (inserted) {
                        '(' -> ')'
                        '{' -> '}'
                        '[' -> ']'
                        '"' -> '"'
                        '\'' -> '\''
                        else -> null
                    }
                    if (closing != null) {
                        val nextChar = newText.getOrNull(cursor)
                        if (nextChar == null || nextChar.isWhitespace() || nextChar in setOf(')', '}', ']', ';', ',')) {
                            val updated = StringBuilder(newText).insert(cursor, closing).toString()
                            return incoming.copy(
                                text = updated,
                                selection = androidx.compose.ui.text.TextRange(cursor)
                            )
                        }
                    }
                }
            }
        }
        return incoming
    }

    fun formatCSource(source: String, tabWidth: Int = 4): String {
        val indentUnit = " ".repeat(tabWidth)
        val out = mutableListOf<String>()
        var indentLevel = 0
        var inBlockComment = false

        for (rawLine in source.lines()) {
            val trimmed = rawLine.trim()
            if (trimmed.isEmpty()) {
                if (out.lastOrNull()?.isNotEmpty() == true) {
                    out += ""
                }
                continue
            }
            if (inBlockComment) {
                out += "${indentUnit.repeat(indentLevel)} $trimmed".trimEnd()
                if (trimmed.contains("*/")) inBlockComment = false
                continue
            }
            if (trimmed.startsWith("/*") && !trimmed.contains("*/")) {
                out += indentUnit.repeat(indentLevel) + trimmed
                inBlockComment = true
                continue
            }
            if (trimmed.startsWith("#")) {
                out += trimmed
                continue
            }

            val leadingCloses = trimmed.takeWhile { it == '}' }.length
            val lineIndent = (indentLevel - leadingCloses).coerceAtLeast(0)
            out += indentUnit.repeat(lineIndent) + trimmed

            val codeNoStrings = stripStringsAndLineComments(trimmed)
            val opens = codeNoStrings.count { it == '{' }
            val closes = codeNoStrings.count { it == '}' }
            indentLevel = (indentLevel + opens - closes).coerceAtLeast(0)
        }

        while (out.isNotEmpty() && out.last().isEmpty()) {
            out.removeAt(out.lastIndex)
        }
        return out.joinToString("\n") + "\n"
    }

    private fun stripStringsAndLineComments(line: String): String {
        val sb = StringBuilder()
        var i = 0
        var inString = false
        var inChar = false
        while (i < line.length) {
            val c = line[i]
            if (!inString && !inChar && c == '/' && i + 1 < line.length && line[i + 1] == '/') {
                break
            }
            if (c == '"' && !inChar && (i == 0 || line[i - 1] != '\\')) {
                inString = !inString
            } else if (c == '\'' && !inString && (i == 0 || line[i - 1] != '\\')) {
                inChar = !inChar
            } else if (!inString && !inChar) {
                sb.append(c)
            }
            i++
        }
        return sb.toString()
    }
}

class EditorUndoHistory(initialValue: TextFieldValue) {
    private val undoStack = ArrayDeque<TextFieldValue>()
    private val redoStack = ArrayDeque<TextFieldValue>()
    private var current: TextFieldValue = initialValue

    val canUndo: Boolean get() = undoStack.isNotEmpty()
    val canRedo: Boolean get() = redoStack.isNotEmpty()

    fun reset(value: TextFieldValue) {
        undoStack.clear()
        redoStack.clear()
        current = value
    }

    fun recordChange(next: TextFieldValue) {
        if (next.text == current.text) {
            current = next
            return
        }
        undoStack.addLast(current)
        if (undoStack.size > 120) undoStack.removeFirst()
        redoStack.clear()
        current = next
    }

    fun undo(): TextFieldValue? {
        if (undoStack.isEmpty()) return null
        redoStack.addLast(current)
        current = undoStack.removeLast()
        return current
    }

    fun redo(): TextFieldValue? {
        if (redoStack.isEmpty()) return null
        undoStack.addLast(current)
        current = redoStack.removeLast()
        return current
    }
}

object EditorFindReplace {
    fun findAllOffsets(text: String, query: String, matchCase: Boolean): List<IntRange> {
        if (query.isEmpty()) return emptyList()
        val matches = mutableListOf<IntRange>()
        var idx = 0
        while (idx <= text.length - query.length) {
            val found = text.indexOf(query, startIndex = idx, ignoreCase = !matchCase)
            if (found < 0) break
            matches += found until (found + query.length)
            idx = found + query.length.coerceAtLeast(1)
        }
        return matches
    }
}
