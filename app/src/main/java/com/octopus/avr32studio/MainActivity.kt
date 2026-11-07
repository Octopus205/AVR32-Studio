package com.octopus.avr32studio

import android.app.Activity
import android.os.Bundle
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.text.Editable
import android.text.Spannable
import android.text.TextWatcher
import android.text.style.ForegroundColorSpan
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.*
import java.io.File

class MainActivity : Activity() {
    private lateinit var root: LinearLayout
    private lateinit var editor: EditText
    private lateinit var gutter: TextView
    private lateinit var log: TextView
    private lateinit var status: TextView
    private lateinit var target: Spinner
    private lateinit var themeButton: TextView
    private var dark = true

    private val codeSample = """#define F_CPU 8000000UL
#include <avr/io.h>

int main(void) {
    DDRB |= (1 << PB0);
    while (1) {
        PORTB ^= (1 << PB0);
    }
}
"""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        dark = getPreferences(0).getBoolean("dark_theme", true)
        buildUi()
    }

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()
    private fun bg(color: Int, radius: Int = 16, stroke: Int? = null): GradientDrawable =
        GradientDrawable().apply {
            setColor(color); cornerRadius = dp(radius).toFloat()
            if (stroke != null) setStroke(dp(1), stroke)
        }

    private fun page() = if (dark) Color.rgb(8, 17, 35) else Color.rgb(244, 247, 252)
    private fun panel() = if (dark) Color.rgb(17, 31, 55) else Color.WHITE
    private fun text() = if (dark) Color.rgb(232, 241, 255) else Color.rgb(22, 39, 64)
    private fun muted() = if (dark) Color.rgb(143, 164, 194) else Color.rgb(103, 119, 143)
    private fun accent() = Color.rgb(28, 157, 238)

    private fun buildUi() {
        root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(12), dp(16), dp(12))
            setBackgroundColor(page())
        }

        val header = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        val brand = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        brand.addView(label("🐙  OctoAVR32A", 23f, true, text()))
        brand.addView(label("AVR DEVELOPMENT STUDIO", 10f, true, muted()))
        header.addView(brand, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        themeButton = label(if (dark) "☀" else "☾", 22f, true, accent()).apply {
            gravity = Gravity.CENTER; background = bg(panel(), 14, muted()); setPadding(dp(14), dp(8), dp(14), dp(8))
            setOnClickListener {
                dark = !dark
                getPreferences(0).edit().putBoolean("dark_theme", dark).apply()
                buildUi()
            }
        }
        header.addView(themeButton)
        root.addView(header)

        val targetCard = card()
        targetCard.orientation = LinearLayout.VERTICAL
        targetCard.addView(label("PROJECT TARGET", 10f, true, muted()))
        val targetRow = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        target = Spinner(this).apply {
            adapter = ArrayAdapter(this@MainActivity, android.R.layout.simple_spinner_dropdown_item, listOf("ATmega32A", "ATmega32"))
            setSelection(0)
        }
        targetRow.addView(target, LinearLayout.LayoutParams(0, dp(48), 1f))
        targetRow.addView(label("●  8 MHz", 13f, true, accent()).apply { gravity = Gravity.CENTER; setPadding(dp(10), 0, dp(8), 0) })
        targetCard.addView(targetRow)
        root.addView(targetCard, marginTop(16))

        val editorHeader = LinearLayout(this).apply { gravity = Gravity.CENTER_VERTICAL }
        editorHeader.addView(label("SOURCE CODE", 11f, true, muted()), LinearLayout.LayoutParams(0, dp(32), 1f))
        editorHeader.addView(label("main.c", 11f, true, accent()).apply { setPadding(dp(10), dp(5), dp(10), dp(5)); background = bg(if (dark) Color.rgb(13, 52, 86) else Color.rgb(225, 242, 255), 8) })
        root.addView(editorHeader, marginTop(12))

        val editorCard = card()
        editorCard.orientation = LinearLayout.HORIZONTAL
        gutter = TextView(this).apply {
            typeface = Typeface.MONOSPACE; textSize = 13f; setTextColor(muted())
            setPadding(dp(10), dp(12), dp(8), dp(12)); gravity = Gravity.TOP or Gravity.END
            setBackgroundColor(if (dark) Color.rgb(13, 25, 44) else Color.rgb(239, 244, 250))
            text = "1"
        }
        editorCard.addView(gutter, LinearLayout.LayoutParams(dp(38), ViewGroup.LayoutParams.MATCH_PARENT))
        editor = EditText(this).apply {
            typeface = Typeface.MONOSPACE; textSize = 13f; setTextColor(text())
            setHintTextColor(muted()); setHint("Write AVR C code...")
            setPadding(dp(10), dp(12), dp(10), dp(12))
            gravity = Gravity.TOP or Gravity.START
            setBackgroundColor(Color.TRANSPARENT)
            setSingleLine(false); isVerticalScrollBarEnabled = true
            setText(codeSample)
        }
        editorCard.addView(editor, LinearLayout.LayoutParams(0, dp(300), 1f))
        editor.setOnScrollChangeListener { _, _, scrollY, _, _ -> gutter.scrollTo(0, scrollY) }
        editor.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                val lines = (s?.count { it == '\n' } ?: 0) + 1
                gutter.text = (1..lines).joinToString("\n")
            }
            override fun afterTextChanged(s: Editable?) { highlight(s) }
        })
        root.addView(editorCard)

        val actionRow = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        val compile = TextView(this).apply {
            text = "▶   BUILD FIRMWARE"; textSize = 14f; typeface = Typeface.DEFAULT_BOLD
            setTextColor(Color.WHITE); gravity = Gravity.CENTER
            background = bg(accent(), 14); setPadding(dp(12), dp(15), dp(12), dp(15))
            setOnClickListener { compile() }
        }
        actionRow.addView(compile, LinearLayout.LayoutParams(0, dp(52), 1f))
        actionRow.addView(label("HEX  •  ELF", 10f, true, muted()).apply { setPadding(dp(12), 0, 0, 0) })
        root.addView(actionRow, marginTop(12))

        val outputHeader = LinearLayout(this).apply { gravity = Gravity.CENTER_VERTICAL }
        outputHeader.addView(label("BUILD CONSOLE", 11f, true, muted()), LinearLayout.LayoutParams(0, dp(32), 1f))
        status = label("●  READY", 10f, true, Color.rgb(46, 190, 130))
        outputHeader.addView(status)
        root.addView(outputHeader, marginTop(10))
        val outputCard = card()
        log = label("Build messages will appear here.", 11f, false, muted()).apply {
            typeface = Typeface.MONOSPACE; setPadding(dp(12), dp(12), dp(12), dp(12)); setTextIsSelectable(true)
        }
        val logScroll = ScrollView(this).apply { addView(log) }
        outputCard.addView(logScroll, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(116)))
        root.addView(outputCard)
        root.addView(label("OctoAVR32A  •  ATmega family only", 9f, false, muted()).apply { gravity = Gravity.CENTER }, marginTop(8))

        setContentView(root)
        highlight(editor.text)
    }

    private fun label(value: String, size: Float, bold: Boolean, color: Int) = TextView(this).apply {
        text = value; textSize = size; setTextColor(color)
        if (bold) setTypeface(null, Typeface.BOLD)
    }

    private fun card() = LinearLayout(this).apply {
        setPadding(dp(10), dp(10), dp(10), dp(10))
        background = bg(panel(), 16, if (dark) Color.rgb(36, 56, 82) else Color.rgb(222, 230, 240))
    }

    private fun marginTop(v: Int) = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { topMargin = dp(v) }

    private fun highlight(editable: Editable?) {
        if (editable == null) return
        val spans = editable.getSpans(0, editable.length, ForegroundColorSpan::class.java)
        spans.forEach { editable.removeSpan(it) }
        val source = editable.toString()
        val patterns = listOf(
            Pair("\\b(int|void|char|long|short|unsigned|volatile|const|return|while|for|if|else)\\b", Color.rgb(86, 174, 255)),
            Pair("\\b(#define|#include)\\b", Color.rgb(190, 140, 255)),
            Pair("\\b(DDRB|PORTB|PINA|DDRA|PORTA|PB[0-7]|PA[0-7]|F_CPU)\\b", Color.rgb(73, 205, 173)),
            Pair(""[^"\\n]*"", Color.rgb(235, 185, 105)),
            Pair("//[^\\n]*", muted())
        )
        for ((pattern, color) in patterns) {
            Regex(pattern).findAll(source).forEach { editable.setSpan(ForegroundColorSpan(color), it.range.first, it.range.last + 1, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE) }
        }
    }

    private fun compile() {
        val dir = File(filesDir, "projects/untitled").apply { mkdirs() }
        val source = File(dir, "main.c")
        source.writeText(editor.text.toString())
        val gcc = File(filesDir, "toolchain/bin/avr-gcc")
        val objcopy = File(filesDir, "toolchain/bin/avr-objcopy")
        if (!gcc.isFile || !objcopy.isFile) {
            status.text = "●  TOOLCHAIN MISSING"
            status.setTextColor(Color.rgb(238, 165, 65))
            log.text = "Source saved: ${source.absolutePath}\n\nAVR-GCC for Android ARM 32-bit is not integrated yet. No HEX or ELF was generated."
            return
        }
        try {
            val elf = File(dir, "firmware.elf")
            val hex = File(dir, "firmware.hex")
            val mcu = "atmega32"
            val p = ProcessBuilder(gcc.absolutePath, "-mmcu=$mcu", "-DF_CPU=8000000UL", "-Os", "-std=gnu11", source.absolutePath, "-o", elf.absolutePath).redirectErrorStream(true).start()
            val messages = p.inputStream.bufferedReader().readText()
            if (p.waitFor() != 0) { status.text = "●  BUILD FAILED"; status.setTextColor(Color.RED); log.text = messages; return }
            val q = ProcessBuilder(objcopy.absolutePath, "-O", "ihex", "-R", ".eeprom", elf.absolutePath, hex.absolutePath).redirectErrorStream(true).start()
            val conversion = q.inputStream.bufferedReader().readText()
            if (q.waitFor() != 0) { status.text = "●  HEX EXPORT FAILED"; status.setTextColor(Color.RED); log.text = messages + "\n" + conversion; return }
            status.text = "●  BUILD SUCCESS"; status.setTextColor(Color.rgb(46, 190, 130))
            log.text = "Firmware generated:\n${hex.absolutePath}\n${elf.absolutePath}\n\n$messages"
        } catch (e: Exception) { status.text = "●  BUILD ERROR"; status.setTextColor(Color.RED); log.text = e.toString() }
    }
}
