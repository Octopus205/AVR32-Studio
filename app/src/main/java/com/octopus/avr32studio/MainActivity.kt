package com.octopus.avr32studio

import android.app.Activity
import android.content.Intent
import android.net.Uri
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
import java.util.zip.ZipInputStream

class MainActivity : Activity() {
    private lateinit var root: LinearLayout
    private lateinit var editor: EditText
    private lateinit var gutter: TextView
    private lateinit var log: TextView
    private lateinit var status: TextView
    private lateinit var target: Spinner
    private lateinit var themeButton: TextView
    private var dark = true
    private var lastHexFile: File? = null
    private var lastElfFile: File? = null
    private var sourceExtension = ".c"

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
        installAvrToolchain()
        installMightyCoreSources()
    }


    // Installs the pinned MightyCore source package bundled by the APK workflow.
    // This is the Arduino board/core source layer; it is not the AVR-GCC compiler.
    private fun installMightyCoreSources() {
        val destination = File(filesDir, "arduino/mightycore")
        val marker = File(destination, ".octo-installed-v3.1.0")
        if (marker.isFile) return
        try {
            destination.deleteRecursively()
            destination.mkdirs()
            val archive = File(cacheDir, "mightycore-v3.1.0.zip")
            assets.open("mightycore-v3.1.0.zip").use { input ->
                archive.outputStream().use { output -> input.copyTo(output) }
            }
            val root = destination.canonicalFile
            ZipInputStream(archive.inputStream().buffered()).use { zip ->
                var entry = zip.nextEntry
                while (entry != null) {
                    val out = File(destination, entry.name).canonicalFile
                    if (out.path != root.path && !out.path.startsWith(root.path + File.separator)) {
                        throw SecurityException("Invalid MightyCore archive path")
                    }
                    if (entry.isDirectory) out.mkdirs() else {
                        out.parentFile?.mkdirs()
                        out.outputStream().use { output -> zip.copyTo(output) }
                    }
                    zip.closeEntry()
                    entry = zip.nextEntry
                }
            }
            archive.delete()
            if (!File(destination, "avr/boards.txt").isFile) {
                throw IllegalStateException("MightyCore board definitions are missing")
            }
            marker.writeText("MightyCore v3.1.0 source package")
        } catch (e: Exception) {
            destination.deleteRecursively()
            android.util.Log.e("OctoAVR32A", "MightyCore source installation failed", e)
        }
    }

    // Extracts the offline ARMv7 AVR toolchain from the APK assets.
    private fun installAvrToolchain() {
        val destination = File(filesDir, "toolchain")
        val marker = File(destination, ".octo-toolchain-installed")
        if (marker.isFile && File(destination, "bin/avr-gcc").canExecute() &&
            File(destination, "bin/avr-objcopy").canExecute()) return
        try {
            destination.deleteRecursively()
            destination.mkdirs()
            val archive = File(cacheDir, "avr-toolchain-armv7.zip")
            assets.open("avr-toolchain-armv7.zip").use { input ->
                archive.outputStream().use { output -> input.copyTo(output) }
            }
            val root = destination.canonicalFile
            ZipInputStream(archive.inputStream().buffered()).use { zip ->
                var entry = zip.nextEntry
                while (entry != null) {
                    val relative = entry.name.removePrefix("toolchain/")
                    if (relative.isNotEmpty()) {
                        val out = File(destination, relative).canonicalFile
                        if (out.path != root.path && !out.path.startsWith(root.path + File.separator)) {
                            throw SecurityException("Invalid AVR toolchain archive path")
                        }
                        if (entry.isDirectory) out.mkdirs() else {
                            out.parentFile?.mkdirs()
                            out.outputStream().use { output -> zip.copyTo(output) }
                            if (relative.startsWith("bin/") || relative.startsWith("libexec/")) {
                                out.setExecutable(true, false)
                            }
                        }
                    }
                    zip.closeEntry()
                    entry = zip.nextEntry
                }
            }
            archive.delete()
            val gcc = File(destination, "bin/avr-gcc")
            val objcopy = File(destination, "bin/avr-objcopy")
            val cc1 = File(destination, "libexec/gcc/avr/7.3.0/cc1")
            if (!gcc.isFile || !objcopy.isFile || !cc1.isFile ||
                !File(destination, "avr/include/avr/io.h").isFile) {
                throw IllegalStateException("AVR compiler package is incomplete")
            }
            gcc.setExecutable(true, false)
            objcopy.setExecutable(true, false)
            cc1.setExecutable(true, false)
            marker.writeText("Octo AVR ARMv7 toolchain package")
            log.text = "AVR toolchain installed. Compiler runtime checks will run when Verify/Build is selected."
        } catch (e: Exception) {
            destination.deleteRecursively()
            android.util.Log.e("OctoAVR32A", "AVR toolchain installation failed", e)
            log.text = "AVR toolchain installation failed: ${e.message}"
        }
    }

    private fun avrProcessBuilder(vararg args: String): ProcessBuilder {
        val toolchain = File(filesDir, "toolchain")
        val command = arrayListOf(File(toolchain, "bin/avr-gcc").absolutePath)
        command.addAll(args)
        return ProcessBuilder(command).apply {
            environment()["COMPILER_PATH"] = File(toolchain, "libexec/gcc/avr/7.3.0").absolutePath
            environment()["PATH"] = File(toolchain, "bin").absolutePath + File.pathSeparator + (environment()["PATH"] ?: "")
            environment()["LIBRARY_PATH"] = File(toolchain, "avr/lib").absolutePath
        }
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

        val fileRow = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        val openButton = smallAction("OPEN", muted()) { openSource() }
        val saveButton = smallAction("SAVE AS", accent()) { saveSourceAs() }
        fileRow.addView(openButton, LinearLayout.LayoutParams(0, dp(42), 1f))
        fileRow.addView(saveButton, LinearLayout.LayoutParams(0, dp(42), 1f).apply { leftMargin = dp(8) })
        root.addView(fileRow, marginTop(10))

        val actionRow = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        val verify = TextView(this).apply {
            text = "✓  VERIFY"; textSize = 12f; typeface = Typeface.DEFAULT_BOLD
            setTextColor(accent()); gravity = Gravity.CENTER
            background = bg(panel(), 12, accent()); setPadding(dp(10), dp(14), dp(10), dp(14))
            setOnClickListener { verifySource() }
        }
        val compile = TextView(this).apply {
            text = "▶  BUILD"; textSize = 13f; typeface = Typeface.DEFAULT_BOLD
            setTextColor(Color.WHITE); gravity = Gravity.CENTER
            background = bg(accent(), 12); setPadding(dp(10), dp(14), dp(10), dp(14))
            setOnClickListener { compile() }
        }
        actionRow.addView(verify, LinearLayout.LayoutParams(0, dp(50), 1f))
        actionRow.addView(compile, LinearLayout.LayoutParams(0, dp(50), 1f).apply { leftMargin = dp(8) })
        root.addView(actionRow, marginTop(10))

        val exportRow = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        exportRow.addView(smallAction("EXPORT HEX", muted()) { exportArtifact(lastHexFile, "application/octet-stream", "firmware.hex") }, LinearLayout.LayoutParams(0, dp(40), 1f))
        exportRow.addView(smallAction("EXPORT ELF", muted()) { exportArtifact(lastElfFile, "application/octet-stream", "firmware.elf") }, LinearLayout.LayoutParams(0, dp(40), 1f).apply { leftMargin = dp(8) })
        root.addView(exportRow, marginTop(6))

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

    private fun smallAction(value: String, color: Int, action: () -> Unit) = TextView(this).apply {
        text = value; textSize = 11f; typeface = Typeface.DEFAULT_BOLD
        setTextColor(color); gravity = Gravity.CENTER
        background = bg(panel(), 10, if (dark) Color.rgb(45, 65, 92) else Color.rgb(215, 225, 238))
        setOnClickListener { action() }
    }

    private fun saveSourceAs() {
        val options = arrayOf("C source (.c)", "Arduino sketch (.ino)")
        android.app.AlertDialog.Builder(this).setTitle("Save source as")
            .setItems(options) { _, which ->
                sourceExtension = if (which == 0) ".c" else ".ino"
                val mime = if (which == 0) "text/x-c" else "text/plain"
                val intent = Intent(Intent.ACTION_CREATE_DOCUMENT).apply {
                    addCategory(Intent.CATEGORY_OPENABLE); type = mime
                    putExtra(Intent.EXTRA_TITLE, if (which == 0) "main.c" else "sketch.ino")
                }
                startActivityForResult(intent, 4101)
            }.show()
    }

    private fun openSource() {
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE); type = "*/*"
            putExtra(Intent.EXTRA_MIME_TYPES, arrayOf("text/plain", "text/x-c", "application/octet-stream"))
        }
        startActivityForResult(intent, 4102)
    }

    private fun exportArtifact(file: File?, mime: String, name: String) {
        if (file == null || !file.exists()) {
            Toast.makeText(this, "No generated file available yet.", Toast.LENGTH_SHORT).show()
            return
        }
        val intent = Intent(Intent.ACTION_CREATE_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE); type = mime
            putExtra(Intent.EXTRA_TITLE, name)
        }
        startActivityForResult(intent, if (name.endsWith(".hex")) 4103 else 4104)
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (resultCode != RESULT_OK) return
        val uri = data?.data ?: return
        try {
            when (requestCode) {
                4101 -> {
                    contentResolver.openOutputStream(uri)?.bufferedWriter()?.use { it.write(editor.text.toString()) }
                    Toast.makeText(this, "Source saved.", Toast.LENGTH_SHORT).show()
                }
                4102 -> {
                    sourceExtension = if (uri.lastPathSegment?.substringAfterLast('.', "")?.equals("ino", true) == true) ".ino" else ".c"
                    val source = contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() } ?: ""
                    editor.setText(source)
                    Toast.makeText(this, "Source opened.", Toast.LENGTH_SHORT).show()
                }
                4103 -> copyArtifact(uri, lastHexFile)
                4104 -> copyArtifact(uri, lastElfFile)
            }
        } catch (e: Exception) {
            Toast.makeText(this, "File operation failed: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }

    private fun copyArtifact(uri: Uri, file: File?) {
        if (file == null || !file.exists()) return
        contentResolver.openOutputStream(uri)?.use { out -> file.inputStream().use { input -> input.copyTo(out) } }
        Toast.makeText(this, "Export completed.", Toast.LENGTH_SHORT).show()
    }

    private fun verifySource() {
        val gcc = File(filesDir, "toolchain/bin/avr-gcc")
        if (!gcc.isFile) {
            status.text = "●  COMPILER NOT INSTALLED"
            status.setTextColor(Color.rgb(238, 165, 65))
            log.text = "Verification cannot run yet. The AVR-GCC ARM 32-bit toolchain is not bundled in this build. No syntax-check result is being claimed."
            return
        }
        if (sourceExtension == ".ino") {
            status.text = "●  ARDUINO CORE REQUIRED"
            status.setTextColor(Color.rgb(238, 165, 65))
            log.text = "Arduino .ino verification requires Arduino AVR Core and the sketch preprocessor, which are not integrated yet."
            return
        }
        val dir = File(filesDir, "projects/verify").apply { mkdirs() }
        val source = File(dir, "verify.c")
        source.writeText(editor.text.toString())
        try {
            val toolchain = File(filesDir, "toolchain")
            val process = avrProcessBuilder("-B${File(toolchain, "libexec/gcc/avr/7.3.0").absolutePath}/", "-I${File(toolchain, "avr/include").absolutePath}", "-mmcu=" + (if (target.selectedItem?.toString() == "ATmega32A") "atmega32a" else "atmega32"), "-DF_CPU=8000000UL", "-std=gnu11", "-fsyntax-only", source.absolutePath).redirectErrorStream(true).start()
            val output = process.inputStream.bufferedReader().readText()
            val code = process.waitFor()
            status.text = if (code == 0) "●  VERIFY PASSED" else "●  VERIFY FAILED"
            status.setTextColor(if (code == 0) Color.rgb(46, 190, 130) else Color.RED)
            log.text = if (output.isBlank()) "No compiler errors found." else output
        } catch (e: Exception) {
            status.text = "●  VERIFY ERROR"; status.setTextColor(Color.RED); log.text = e.toString()
        }
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
            Pair("//[^\\n]*", muted())
        )
        for ((pattern, color) in patterns) {
            Regex(pattern).findAll(source).forEach { editable.setSpan(ForegroundColorSpan(color), it.range.first, it.range.last + 1, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE) }
        }
    }

    private fun compile() {
        val dir = File(filesDir, "projects/untitled").apply { mkdirs() }
        val selectedName = if (sourceExtension == ".ino") "sketch.ino" else "main.c"
        val source = File(dir, selectedName)
        source.writeText(editor.text.toString())
        if (sourceExtension == ".ino") {
            status.text = "●  ARDUINO CORE REQUIRED"
            status.setTextColor(Color.rgb(238, 165, 65))
            log.text = "The .ino sketch was saved, but Arduino AVR Core and the sketch preprocessor are not integrated yet. Build is intentionally stopped to avoid treating an Arduino sketch as plain C."
            return
        }
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
            val mcu = if (target.selectedItem?.toString() == "ATmega32A") "atmega32a" else "atmega32"
            val toolchain = File(filesDir, "toolchain")
            val p = avrProcessBuilder("-B${File(toolchain, "libexec/gcc/avr/7.3.0").absolutePath}/", "-B${File(toolchain, "bin").absolutePath}/", "-B${File(toolchain, "avr/lib").absolutePath}/", "-I${File(toolchain, "avr/include").absolutePath}", "-L${File(toolchain, "lib/gcc/avr/7.3.0").absolutePath}", "-L${File(toolchain, "avr/lib").absolutePath}", "-mmcu=$mcu", "-DF_CPU=8000000UL", "-Os", "-std=gnu11", source.absolutePath, "-o", elf.absolutePath).redirectErrorStream(true).start()
            val messages = p.inputStream.bufferedReader().readText()
            if (p.waitFor() != 0) { status.text = "●  BUILD FAILED"; status.setTextColor(Color.RED); log.text = messages; return }
            val q = ProcessBuilder(objcopy.absolutePath, "-O", "ihex", "-R", ".eeprom", elf.absolutePath, hex.absolutePath).redirectErrorStream(true).start()
            val conversion = q.inputStream.bufferedReader().readText()
            if (q.waitFor() != 0) { status.text = "●  HEX EXPORT FAILED"; status.setTextColor(Color.RED); log.text = messages + "\n" + conversion; return }
            lastHexFile = hex
            lastElfFile = elf
            status.text = "●  BUILD SUCCESS"; status.setTextColor(Color.rgb(46, 190, 130))
            log.text = "Firmware generated:\n${hex.absolutePath}\n${elf.absolutePath}\n\n$messages"
        } catch (e: Exception) { status.text = "●  BUILD ERROR"; status.setTextColor(Color.RED); log.text = e.toString() }
    }
}
