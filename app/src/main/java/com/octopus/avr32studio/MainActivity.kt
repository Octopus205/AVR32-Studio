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
        dark = (resources.configuration.uiMode and android.content.res.Configuration.UI_MODE_NIGHT_MASK) == android.content.res.Configuration.UI_MODE_NIGHT_YES
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
        if (marker.isFile && marker.readText().trim() == "Octo AVR ARMv7 toolchain package v4" &&
            File(destination, "bin/avr-gcc").canExecute() &&
            File(destination, "bin/avr-objcopy").canExecute() &&
            File(destination, "lib/libc++_shared.so").isFile &&
            File(destination, "libexec/gcc/avr/7.3.0/liblto_plugin.so").isFile &&
            File(destination, "bin/as").canExecute() &&
            File(destination, "bin/ld").canExecute()) return
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
            marker.writeText("Octo AVR ARMv7 toolchain package v4")
            log.text = "AVR toolchain installed. Compiler runtime checks will run when Verify/Build is selected."
        } catch (e: Exception) {
            destination.deleteRecursively()
            android.util.Log.e("OctoAVR32A", "AVR toolchain installation failed", e)
            log.text = "AVR toolchain installation failed: ${e.message}"
        }
    }

    private fun avrProcessBuilder(vararg args: String): ProcessBuilder =
        avrToolProcessBuilder(File(filesDir, "toolchain/bin/avr-gcc"), *args)

    private fun avrObjcopyProcessBuilder(vararg args: String): ProcessBuilder =
        avrToolProcessBuilder(File(filesDir, "toolchain/bin/avr-objcopy"), *args)

    private fun avrToolProcessBuilder(executable: File, vararg args: String): ProcessBuilder {
        val toolchain = File(filesDir, "toolchain")
        val command = arrayListOf(executable.absolutePath)
        command.addAll(args)
        val workDir = File(filesDir, "compiler-work").apply { mkdirs() }
        return ProcessBuilder(command).apply {
            directory(workDir)
            environment()["TMPDIR"] = workDir.absolutePath
            environment()["TMP"] = workDir.absolutePath
            environment()["TEMP"] = workDir.absolutePath
            environment()["COMPILER_PATH"] = File(toolchain, "libexec/gcc/avr/7.3.0").absolutePath
            environment()["PATH"] = File(toolchain, "bin").absolutePath + File.pathSeparator + (environment()["PATH"] ?: "")
            environment()["LIBRARY_PATH"] = File(toolchain, "avr/lib").absolutePath
            environment()["LD_LIBRARY_PATH"] = File(toolchain, "lib").absolutePath + File.pathSeparator + (environment()["LD_LIBRARY_PATH"] ?: "")
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
            setPadding(dp(12), dp(8), dp(12), dp(8))
            setBackgroundColor(page())
        }

        // Compact IDE toolbar: navigation, Verify (lightning), Build and Search.
        val toolbar = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        val menu = label("☰", 25f, true, text()).apply {
            gravity = Gravity.CENTER
            setPadding(dp(10), dp(8), dp(14), dp(8))
            setOnClickListener { showDrawer(it) }
        }
        toolbar.addView(menu)
        val brand = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        brand.addView(label("OctoAVR32A", 19f, true, text()))
        brand.addView(label("AVR DEVELOPMENT STUDIO", 9f, true, muted()))
        toolbar.addView(brand, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        val search = toolbarAction("⌕", "Search in source") { showSearchDialog() }
        val verify = toolbarAction("ϟ", "Verify source") { verifySource() }
        val build = toolbarAction("▶", "Build firmware") { compile() }
        toolbar.addView(search)
        toolbar.addView(verify)
        toolbar.addView(build)
        root.addView(toolbar)

        val targetCard = card().apply { orientation = LinearLayout.VERTICAL }
        val targetRow = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        targetRow.addView(label("TARGET", 10f, true, muted()))
        target = Spinner(this).apply {
            adapter = ArrayAdapter(this@MainActivity, android.R.layout.simple_spinner_dropdown_item, listOf("ATmega32A", "ATmega32"))
            setSelection(0)
        }
        targetRow.addView(target, LinearLayout.LayoutParams(0, dp(42), 1f))
        targetRow.addView(label("8 MHz", 12f, true, accent()).apply { gravity = Gravity.CENTER; setPadding(dp(8), 0, dp(8), 0) })
        targetCard.addView(targetRow)
        root.addView(targetCard, marginTop(8))

        val editorHeader = LinearLayout(this).apply { gravity = Gravity.CENTER_VERTICAL }
        editorHeader.addView(label("EDITOR", 10f, true, muted()), LinearLayout.LayoutParams(0, dp(30), 1f))
        editorHeader.addView(label(if (sourceExtension == ".ino") "sketch.ino" else "main.c", 11f, true, accent()).apply {
            setPadding(dp(10), dp(5), dp(10), dp(5))
            background = bg(if (dark) Color.rgb(13, 52, 86) else Color.rgb(225, 242, 255), 8)
        })
        root.addView(editorHeader, marginTop(4))

        // The editor occupies the available screen height and scrolls internally.
        val editorCard = card().apply { orientation = LinearLayout.HORIZONTAL }
        gutter = TextView(this).apply {
            typeface = Typeface.MONOSPACE
            textSize = 12f
            setTextColor(muted())
            setPadding(dp(8), dp(12), dp(8), dp(12))
            gravity = Gravity.TOP or Gravity.END
            setBackgroundColor(if (dark) Color.rgb(13, 25, 44) else Color.rgb(239, 244, 250))
            text = "1"
        }
        editorCard.addView(gutter, LinearLayout.LayoutParams(dp(38), ViewGroup.LayoutParams.MATCH_PARENT))
        editor = EditText(this).apply {
            typeface = Typeface.MONOSPACE
            textSize = 13f
            setTextColor(text())
            setHintTextColor(muted())
            setHint("Write AVR C code...")
            setPadding(dp(12), dp(12), dp(12), dp(12))
            gravity = Gravity.TOP or Gravity.START
            setBackgroundColor(Color.TRANSPARENT)
            setSingleLine(false)
            isVerticalScrollBarEnabled = true
            overScrollMode = View.OVER_SCROLL_IF_CONTENT_SCROLLS
            setHorizontallyScrolling(true)
            setText(codeSample)
        }
        editorCard.addView(editor, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1f))
        editor.setOnScrollChangeListener { _, _, scrollY, _, _ -> gutter.scrollTo(0, scrollY) }
        editor.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                val lines = (s?.count { it == '\n' } ?: 0) + 1
                gutter.text = (1..lines).joinToString("\n")
            }
            override fun afterTextChanged(s: Editable?) { highlight(s) }
        })
        root.addView(editorCard, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))

        // Console remains visible at the bottom; the drawer also opens it as a full page.
        val consoleHeader = LinearLayout(this).apply { gravity = Gravity.CENTER_VERTICAL }
        consoleHeader.addView(label("CONSOLE", 10f, true, muted()), LinearLayout.LayoutParams(0, dp(28), 1f))
        status = label("● READY", 10f, true, Color.rgb(46, 190, 130))
        consoleHeader.addView(status)
        root.addView(consoleHeader, marginTop(6))
        val outputCard = card()
        log = label("Build messages will appear here.", 11f, false, muted()).apply {
            typeface = Typeface.MONOSPACE
            setPadding(dp(10), dp(8), dp(10), dp(8))
            setTextIsSelectable(true)
        }
        val logScroll = ScrollView(this).apply {
            isFillViewport = true
            addView(log, ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        }
        outputCard.addView(logScroll, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(104)))
        root.addView(outputCard)
        setContentView(root)
        highlight(editor.text)
    }

    private fun toolbarAction(symbol: String, description: String, action: () -> Unit) =
        TextView(this).apply {
            text = symbol
            contentDescription = description
            textSize = 23f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(accent())
            gravity = Gravity.CENTER
            background = bg(panel(), 10)
            setPadding(dp(11), dp(7), dp(11), dp(7))
            setOnClickListener { action() }
        }

    private fun showDrawer(anchor: View) {
        val drawer = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(20), dp(16), dp(16))
            setBackgroundColor(panel())
        }
        drawer.addView(label("OctoAVR32A", 21f, true, text()))
        drawer.addView(label("PROJECT WORKSPACE", 9f, true, muted()).apply { setPadding(0, dp(4), 0, dp(18)) })
        fun item(title: String, subtitle: String? = null, action: () -> Unit) {
            val row = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(dp(10), dp(11), dp(8), dp(11))
                background = bg(if (dark) Color.rgb(23, 39, 63) else Color.rgb(246, 249, 253), 10)
                setOnClickListener { action() }
            }
            row.addView(label(title, 14f, true, text()))
            if (subtitle != null) row.addView(label(subtitle, 10f, false, muted()))
            drawer.addView(row, marginTop(6))
        }
        lateinit var popup: PopupWindow
        popup = PopupWindow(drawer, dp(285), ViewGroup.LayoutParams.MATCH_PARENT, true).apply {
            elevation = dp(18).toFloat()
            setBackgroundDrawable(bg(panel(), 0))
            isOutsideTouchable = true
        }
        item("New / Reset source", "Start with the AVR example") {
            popup.dismiss()
            editor.setText(codeSample)
            sourceExtension = ".c"
        }
        item("Open source", "Open .c or .ino") { popup.dismiss(); openSource() }
        item("Save As", "Save source file") { popup.dismiss(); saveSourceAs() }
        item("Verify", "Check source syntax") { popup.dismiss(); verifySource() }
        item("Build", "Generate firmware.elf and firmware.hex") { popup.dismiss(); compile() }
        item("Console", "Open the full console page") { popup.dismiss(); showConsolePage() }
        item("Export HEX", "Export Intel HEX") { popup.dismiss(); exportArtifact(lastHexFile, "application/octet-stream", "firmware.hex") }
        item("Export ELF", "Export ELF file") { popup.dismiss(); exportArtifact(lastElfFile, "application/octet-stream", "firmware.elf") }
        item("Settings", "Target clock: 8 MHz · MCU: ATmega32 family") {
            popup.dismiss()
            android.app.AlertDialog.Builder(this)
                .setTitle("Settings")
                .setMessage("Target devices: ATmega32A and ATmega32 only.\nCPU frequency: 8 MHz.\nAppearance: follows Android system theme.")
                .setPositiveButton("OK", null).show()
        }
        item("About", "AVR compiler for Android") {
            popup.dismiss()
            android.app.AlertDialog.Builder(this)
                .setTitle("About OctoAVR32A")
                .setMessage("A focused AVR development environment for ATmega32A / ATmega32.\n\nOffline AVR-GCC toolchain · Verify · Build · HEX / ELF export.")
                .setPositiveButton("Close", null).show()
        }
        popup.showAtLocation(root, Gravity.START or Gravity.TOP, 0, 0)
    }

    private fun showConsolePage() {
        val consoleRoot = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(16), dp(16), dp(16))
            setBackgroundColor(page())
        }
        val header = LinearLayout(this).apply { gravity = Gravity.CENTER_VERTICAL }
        header.addView(label("BUILD CONSOLE", 18f, true, text()), LinearLayout.LayoutParams(0, dp(48), 1f))
        val close = toolbarAction("×", "Back to editor") { setContentView(root) }
        header.addView(close)
        consoleRoot.addView(header)
        val content = TextView(this).apply {
            text = log.text
            typeface = Typeface.MONOSPACE
            textSize = 12f
            setTextColor(text())
            setTextIsSelectable(true)
            setPadding(dp(12), dp(12), dp(12), dp(12))
            background = bg(panel(), 12)
        }
        val scroll = ScrollView(this).apply { addView(content) }
        consoleRoot.addView(scroll, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))
        val closeButton = smallAction("BACK TO EDITOR", accent()) { setContentView(root) }
        consoleRoot.addView(closeButton, marginTop(10))
        setContentView(consoleRoot)
    }

    private fun showSearchDialog() {
        val query = EditText(this).apply { hint = "Find text in source" }
        android.app.AlertDialog.Builder(this)
            .setTitle("Search in editor")
            .setView(query)
            .setPositiveButton("Find") { _, _ ->
                val term = query.text.toString()
                if (term.isNotEmpty()) {
                    val index = editor.text.toString().indexOf(term, ignoreCase = true)
                    if (index >= 0) {
                        editor.requestFocus()
                        editor.setSelection(index, index + term.length)
                    } else Toast.makeText(this, "Text not found.", Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
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
            val process = avrProcessBuilder("-B${File(toolchain, "libexec/gcc/avr/7.3.0").absolutePath}/", "-I${File(toolchain, "avr/include").absolutePath}", "-mmcu=" + (if (target.selectedItem?.toString() == "ATmega32A") "atmega32a" else "atmega32"), "-DF_CPU=8000000UL", "-std=gnu11", "-Os", "-fsyntax-only", source.absolutePath).redirectErrorStream(true).start()
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

    @Volatile private var buildInProgress = false

    private fun compile() {
        if (buildInProgress) {
            Toast.makeText(this, "Build is already running.", Toast.LENGTH_SHORT).show()
            return
        }
        buildInProgress = true
        status.text = "●  BUILD RUNNING"
        status.setTextColor(accent())
        log.text = "Preparing compiler...\\nPlease wait. You can keep the app open."

        Thread {
            fun publish(state: String, color: Int, message: String) {
                runOnUiThread {
                    status.text = state
                    status.setTextColor(color)
                    log.text = message
                    buildInProgress = false
                }
            }

            try {
                val dir = File(filesDir, "projects/untitled").apply { mkdirs() }
                val selectedName = if (sourceExtension == ".ino") "sketch.ino" else "main.c"
                val source = File(dir, selectedName)
                val sourceText = editor.text.toString()
                source.writeText(sourceText)

                if (sourceExtension == ".ino") {
                    publish("●  ARDUINO CORE REQUIRED", Color.rgb(238, 165, 65),
                        "The .ino sketch was saved, but Arduino AVR Core and the sketch preprocessor are not integrated yet. Build is intentionally stopped.")
                    return@Thread
                }

                val gcc = File(filesDir, "toolchain/bin/avr-gcc")
                val objcopy = File(filesDir, "toolchain/bin/avr-objcopy")
                if (!gcc.isFile || !objcopy.isFile) {
                    publish("●  TOOLCHAIN MISSING", Color.rgb(238, 165, 65),
                        "Source saved: ${source.absolutePath}\\n\\nAVR-GCC for Android ARM 32-bit is not integrated yet. No HEX or ELF was generated.")
                    return@Thread
                }

                val elf = File(dir, "firmware.elf")
                val hex = File(dir, "firmware.hex")
                val mcu = if (target.selectedItem?.toString() == "ATmega32A") "atmega32a" else "atmega32"
                val toolchain = File(filesDir, "toolchain")
                runOnUiThread { log.text = "Compiling and linking AVR firmware...\\nThis may take a little while." }

                val objectFile = File(dir, "firmware.o")
                val toolchainBin = File(toolchain, "bin").absolutePath
                val compilerPrefix = File(toolchain, "libexec/gcc/avr/7.3.0").absolutePath
                val includePath = File(toolchain, "avr/include").absolutePath
                val libraryPath = File(toolchain, "avr/lib").absolutePath
                val gccLibraryPath = File(toolchain, "lib/gcc/avr/7.3.0").absolutePath

                fun runBuildStage(label: String, args: List<String>, timeoutSeconds: Long, outputFile: File, useObjcopy: Boolean = false): Pair<Int?, String> {
                    runOnUiThread { log.text = "$label\\nPlease wait..." }
                    val builder = if (useObjcopy) avrObjcopyProcessBuilder(*args.toTypedArray()) else avrProcessBuilder(*args.toTypedArray())
                    val process = builder
                        .redirectErrorStream(true)
                        .redirectOutput(outputFile)
                        .start()
                    val finished = process.waitFor(timeoutSeconds, java.util.concurrent.TimeUnit.SECONDS)
                    if (!finished) {
                        process.destroyForcibly()
                        process.waitFor()
                        val details = if (outputFile.isFile) outputFile.readText() else ""
                        return Pair(null, "$label timed out after $timeoutSeconds seconds.\\n" +
                            (if (details.isBlank()) "No diagnostic output was produced." else details))
                    }
                    val details = if (outputFile.isFile) outputFile.readText() else ""
                    return Pair(process.exitValue(), details)
                }

                // Keep the first Android ARMv7 compile conservative: disable optimizer/LTO
                // until the cc1 runtime is proven stable on older 32-bit Android devices.
                val commonArgs = listOf(
                    "-B$compilerPrefix/",
                    "-B$toolchainBin/",
                    "-B$libraryPath/",
                    "-I$includePath",
                    "-L$gccLibraryPath",
                    "-L$libraryPath",
                    "-mmcu=$mcu", "-DF_CPU=8000000UL", "-O0", "-fno-lto", "-std=gnu11"
                )

                val compileStage = runBuildStage(
                    "Stage 1/3: compiling C source to object file...",
                    commonArgs + listOf("-c", source.absolutePath, "-o", objectFile.absolutePath),
                    60L,
                    File(dir, "compile-stage.log")
                )
                if (compileStage.first != 0) {
                    publish(if (compileStage.first == null) "●  COMPILE STAGE TIMEOUT" else "●  COMPILE STAGE FAILED",
                        Color.RED, compileStage.second)
                    return@Thread
                }

                val linkStage = runBuildStage(
                    "Stage 2/3: linking object file to ELF...",
                    commonArgs + listOf(objectFile.absolutePath, "-o", elf.absolutePath),
                    60L,
                    File(dir, "link-stage.log")
                )
                if (linkStage.first != 0) {
                    publish(if (linkStage.first == null) "●  LINK STAGE TIMEOUT" else "●  LINK STAGE FAILED",
                        Color.RED, linkStage.second)
                    return@Thread
                }

                val objcopyOutput = File(dir, "objcopy-build.log")
                val objcopyStage = runBuildStage(
                    "Stage 3/3: generating Intel HEX file...",
                    listOf("-O", "ihex", "-R", ".eeprom",
                        elf.absolutePath, hex.absolutePath),
                    30L,
                    objcopyOutput,
                    useObjcopy = true
                )
                if (objcopyStage.first != 0) {
                    publish(if (objcopyStage.first == null) "●  HEX EXPORT TIMEOUT" else "●  HEX EXPORT FAILED",
                        Color.RED, objcopyStage.second)
                    return@Thread
                }
                val messages = compileStage.second + "\\n" + linkStage.second
                if (!elf.isFile || elf.length() == 0L || !hex.isFile || hex.length() == 0L) {
                    publish("●  BUILD FAILED", Color.RED, "Compiler finished but HEX/ELF output is missing or empty.\\n" + messages)
                    return@Thread
                }

                lastHexFile = hex
                lastElfFile = elf
                publish("●  BUILD SUCCESS", Color.rgb(46, 190, 130),
                    "Firmware generated:\\n${hex.absolutePath}\\n${elf.absolutePath}\\n\\n$messages")
            } catch (e: Exception) {
                publish("●  BUILD ERROR", Color.RED, e.toString())
            }
        }.start()
    }
}
