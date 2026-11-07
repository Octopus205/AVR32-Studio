package com.octopus.avr32studio

import android.app.Activity
import android.os.Bundle
import android.graphics.Typeface
import android.view.Gravity
import android.view.ViewGroup
import android.widget.*
import java.io.File

class MainActivity : Activity() {
    private lateinit var editor: EditText
    private lateinit var log: TextView
    private lateinit var status: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val root = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(14, 14, 14, 14) }
        root.addView(TextView(this).apply { text = "AVR32 Studio"; textSize = 25f; setTypeface(null, Typeface.BOLD) })
        root.addView(TextView(this).apply { text = "ATmega32A / ATmega32   •   8 MHz"; textSize = 13f })
        status = TextView(this).apply { text = "Ready"; setPadding(0, 10, 0, 10) }
        root.addView(status)
        editor = EditText(this).apply {
            typeface = Typeface.MONOSPACE; textSize = 13f; gravity = Gravity.TOP or Gravity.START
            setText("#define F_CPU 8000000UL\n#include <avr/io.h>\n\nint main(void) {\n    DDRB |= (1 << PB0);\n    while (1) {\n        PORTB ^= (1 << PB0);\n    }\n}\n")
        }
        root.addView(editor, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))
        root.addView(Button(this).apply { text = "Compile HEX + ELF"; setOnClickListener { compile() } })
        root.addView(TextView(this).apply { text = "Build output"; textSize = 16f; setTypeface(null, Typeface.BOLD); setPadding(0, 10, 0, 4) })
        log = TextView(this).apply { typeface = Typeface.MONOSPACE; textSize = 12f; setTextIsSelectable(true); text = "Compiler messages will appear here." }
        val scroll = ScrollView(this).apply { addView(log) }
        root.addView(scroll, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 150))
        setContentView(root)
    }

    private fun compile() {
        val dir = File(filesDir, "projects/untitled").apply { mkdirs() }
        File(dir, "main.c").writeText(editor.text.toString())
        val gcc = File(filesDir, "toolchain/bin/avr-gcc")
        val objcopy = File(filesDir, "toolchain/bin/avr-objcopy")
        if (!gcc.isFile || !objcopy.isFile) {
            status.text = "Compiler toolchain not installed"
            log.text = "Your source was saved inside the app.\n\nAVR-GCC for Android ARM 32-bit has not yet been integrated. No HEX or ELF was generated.\n\nThis build is an interface/prototype only."
            return
        }
        try {
            val elf = File(dir, "firmware.elf")
            val hex = File(dir, "firmware.hex")
            val p = ProcessBuilder(gcc.absolutePath, "-mmcu=atmega32", "-DF_CPU=8000000UL", "-Os", "-std=gnu11", File(dir,"main.c").absolutePath, "-o", elf.absolutePath).redirectErrorStream(true).start()
            val messages = p.inputStream.bufferedReader().readText()
            if (p.waitFor() != 0) { status.text = "Compile failed"; log.text = messages; return }
            val q = ProcessBuilder(objcopy.absolutePath, "-O", "ihex", "-R", ".eeprom", elf.absolutePath, hex.absolutePath).redirectErrorStream(true).start()
            val conversion = q.inputStream.bufferedReader().readText()
            if (q.waitFor() != 0) { status.text = "HEX conversion failed"; log.text = messages + "\n" + conversion; return }
            status.text = "Build successful"
            log.text = "HEX and ELF generated in app storage.\n${hex.absolutePath}\n${elf.absolutePath}\n$messages"
        } catch (e: Exception) { status.text = "Build error"; log.text = e.toString() }
    }
}
