package com.example.octoavr.compiler

import com.example.octoavr.model.CompilerDiagnostic
import com.example.octoavr.model.DiagnosticSeverity
import com.example.octoavr.model.HexValidationResult
import com.example.octoavr.model.MemoryUsage
import com.example.octoavr.model.TargetMcu
import java.io.File

object HexAndElfParser {

    private val GCC_DIAG_REGEX = Regex("""^(.+?):(\d+):(?:(\d+):)?\s*(fatal error|error|warning|note):\s*(.+)$""")

    fun parseDiagnostics(outputLines: List<String>, projectRootPath: String): List<CompilerDiagnostic> {
        val results = mutableListOf<CompilerDiagnostic>()
        val normalizedRoot = projectRootPath.trimEnd('/') + "/"
        for (line in outputLines) {
            val match = GCC_DIAG_REGEX.matchEntire(line.trim())
            if (match != null) {
                val rawFile = match.groupValues[1]
                val displayFile = rawFile.removePrefix(normalizedRoot)
                val lineNum = match.groupValues[2].toIntOrNull() ?: 1
                val colNum = match.groupValues[3].toIntOrNull() ?: 1
                val sevStr = match.groupValues[4].lowercase()
                val msg = match.groupValues[5]
                val severity = when {
                    sevStr.contains("error") -> DiagnosticSeverity.ERROR
                    sevStr == "warning" -> DiagnosticSeverity.WARNING
                    else -> DiagnosticSeverity.NOTE
                }
                results += CompilerDiagnostic(
                    file = displayFile,
                    line = lineNum,
                    column = colNum,
                    severity = severity,
                    message = msg,
                    rawLine = line
                )
            } else if (line.contains("undefined reference to", ignoreCase = true) ||
                line.contains("ld returned", ignoreCase = true)
            ) {
                results += CompilerDiagnostic(
                    file = "linker",
                    line = 1,
                    column = 1,
                    severity = DiagnosticSeverity.ERROR,
                    message = line.trim(),
                    rawLine = line
                )
            }
        }
        return results
    }

    fun parseAvrSizeOutput(stdout: String, mcu: TargetMcu): MemoryUsage? {
        // Supports both SysV (`avr-size` default berkeley format):
        //    text    data     bss     dec     hex filename
        //     186       0       0     186      ba firmware.elf
        // and `--format=avr` output.
        val lines = stdout.lines().map { it.trim() }.filter { it.isNotEmpty() }
        for (line in lines) {
            val tokens = line.split(Regex("\\s+"))
            if (tokens.size >= 4 &&
                tokens[0].toIntOrNull() != null &&
                tokens[1].toIntOrNull() != null &&
                tokens[2].toIntOrNull() != null
            ) {
                val text = tokens[0].toInt()
                val data = tokens[1].toInt()
                val bss = tokens[2].toInt()
                return MemoryUsage(
                    textBytes = text,
                    dataBytes = data,
                    bssBytes = bss,
                    flashBytes = text + data,
                    flashMaxBytes = mcu.flashBytes,
                    sramBytes = data + bss,
                    sramMaxBytes = mcu.sramBytes
                )
            }
        }
        return null
    }

    fun isValidAvrElf(elfFile: File): Boolean {
        if (!elfFile.exists() || elfFile.length() < 52) return false
        val header = elfFile.inputStream().use { input ->
            val buf = ByteArray(20)
            val read = input.read(buf)
            if (read < 20) return false
            buf
        }
        // ELF magic: 0x7F 'E' 'L' 'F', EI_CLASS=1 (32-bit), EI_DATA=1 (LSB)
        if (header[0] != 0x7F.toByte() ||
            header[1] != 'E'.code.toByte() ||
            header[2] != 'L'.code.toByte() ||
            header[3] != 'F'.code.toByte()
        ) {
            return false
        }
        if (header[4] != 1.toByte() || header[5] != 1.toByte()) return false
        // e_machine at offset 18..19 (little endian): EM_AVR = 83 (0x53)
        val eMachine = (header[18].toInt() and 0xFF) or ((header[19].toInt() and 0xFF) shl 8)
        return eMachine == 83
    }

    fun validateIntelHex(hexFile: File): HexValidationResult {
        if (!hexFile.exists()) {
            return HexValidationResult(false, 0, 0, false, "HEX file does not exist")
        }
        val lines = hexFile.readLines().map { it.trim() }.filter { it.isNotEmpty() }
        if (lines.isEmpty()) {
            return HexValidationResult(false, 0, 0, false, "HEX file is empty")
        }

        var recordCount = 0
        var totalDataBytes = 0
        var hasEof = false

        for ((index, line) in lines.withIndex()) {
            if (!line.startsWith(":")) {
                return HexValidationResult(false, recordCount, totalDataBytes, hasEof, "Line ${index + 1} missing ':' prefix")
            }
            val hex = line.substring(1)
            if (hex.length < 10 || hex.length % 2 != 0) {
                return HexValidationResult(false, recordCount, totalDataBytes, hasEof, "Line ${index + 1} invalid record length")
            }

            val bytes = try {
                ByteArray(hex.length / 2) { i ->
                    hex.substring(i * 2, i * 2 + 2).toInt(16).toByte()
                }
            } catch (e: NumberFormatException) {
                return HexValidationResult(false, recordCount, totalDataBytes, hasEof, "Line ${index + 1} non-hex digits")
            }

            val byteCount = bytes[0].toInt() and 0xFF
            if (bytes.size != byteCount + 5) {
                return HexValidationResult(false, recordCount, totalDataBytes, hasEof, "Line ${index + 1} byte count mismatch")
            }

            var sum = 0
            for (b in bytes) {
                sum = (sum + (b.toInt() and 0xFF)) and 0xFF
            }
            if (sum != 0) {
                return HexValidationResult(false, recordCount, totalDataBytes, hasEof, "Line ${index + 1} checksum mismatch")
            }

            val recordType = bytes[3].toInt() and 0xFF
            recordCount++
            if (recordType == 0x00) {
                totalDataBytes += byteCount
            } else if (recordType == 0x01) {
                hasEof = true
            }
        }

        if (!hasEof) {
            return HexValidationResult(false, recordCount, totalDataBytes, false, "Missing Intel HEX EOF (:00000001FF) record")
        }

        return HexValidationResult(
            isValid = recordCount > 0 && hasEof,
            recordCount = recordCount,
            dataByteCount = totalDataBytes,
            hasEofRecord = hasEof
        )
    }
}
