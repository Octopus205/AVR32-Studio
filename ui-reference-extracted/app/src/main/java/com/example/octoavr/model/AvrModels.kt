package com.example.octoavr.model

enum class TargetMcu(
    val gccMcu: String,
    val avrArch: String,
    val displayName: String,
    val flashBytes: Int,
    val sramBytes: Int,
    val eepromBytes: Int,
    val defaultClockHz: Long
) {
    ATMEGA32A(
        gccMcu = "atmega32a",
        avrArch = "avr5",
        displayName = "ATmega32A",
        flashBytes = 32768,
        sramBytes = 2048,
        eepromBytes = 1024,
        defaultClockHz = 8_000_000L
    ),
    ATMEGA328P(
        gccMcu = "atmega328p",
        avrArch = "avr5",
        displayName = "ATmega328P",
        flashBytes = 32768,
        sramBytes = 2048,
        eepromBytes = 1024,
        defaultClockHz = 16_000_000L
    ),
    ATMEGA16A(
        gccMcu = "atmega16a",
        avrArch = "avr5",
        displayName = "ATmega16A",
        flashBytes = 16384,
        sramBytes = 1024,
        eepromBytes = 512,
        defaultClockHz = 8_000_000L
    ),
    ATMEGA8A(
        gccMcu = "atmega8a",
        avrArch = "avr4",
        displayName = "ATmega8A",
        flashBytes = 8192,
        sramBytes = 1024,
        eepromBytes = 512,
        defaultClockHz = 8_000_000L
    ),
    ATMEGA128A(
        gccMcu = "atmega128a",
        avrArch = "avr51",
        displayName = "ATmega128A",
        flashBytes = 131072,
        sramBytes = 4096,
        eepromBytes = 4096,
        defaultClockHz = 16_000_000L
    );

    companion object {
        fun fromGccMcu(mcu: String): TargetMcu =
            entries.firstOrNull { it.gccMcu.equals(mcu, ignoreCase = true) } ?: ATMEGA32A
    }
}

enum class OptimizationLevel(val flag: String, val label: String) {
    OS("-Os", "Size (-Os)"),
    O2("-O2", "Balanced (-O2)"),
    O1("-O1", "Basic (-O1)"),
    O3("-O3", "Speed (-O3)"),
    O0("-O0", "Debug (-O0)");

    companion object {
        fun fromFlag(flag: String): OptimizationLevel =
            entries.firstOrNull { it.flag == flag } ?: OS
    }
}

enum class OutputFormat(val label: String, val emitElf: Boolean, val emitHex: Boolean) {
    ELF_AND_HEX("ELF + Intel HEX", emitElf = true, emitHex = true),
    HEX_ONLY("Intel HEX Only", emitElf = false, emitHex = true),
    ELF_ONLY("ELF Only", emitElf = true, emitHex = false);

    companion object {
        fun fromName(name: String): OutputFormat =
            entries.firstOrNull { it.name == name } ?: ELF_AND_HEX
    }
}

data class BuildConfigSettings(
    val targetMcu: TargetMcu = TargetMcu.ATMEGA32A,
    val cpuFrequencyHz: Long = 8_000_000L,
    val optimizationLevel: OptimizationLevel = OptimizationLevel.OS,
    val extraCFlags: String = "-Wall -Wextra -std=gnu11",
    val extraCppFlags: String = "-Wall -Wextra -std=gnu++14 -fno-exceptions",
    val extraLdFlags: String = "",
    val includeDebugInfo: Boolean = false,
    val includeDirectories: List<String> = listOf("include"),
    val additionalSourceFiles: List<String> = emptyList(),
    val outputFormat: OutputFormat = OutputFormat.ELF_AND_HEX,
    val generateDisassembly: Boolean = false,
    val verboseOutput: Boolean = false
) {
    val fCpuDefine: String
        get() = "${cpuFrequencyHz}UL"
}

data class ProjectFile(
    val relativePath: String,
    val name: String,
    val extension: String,
    val content: String = "",
    val isDirty: Boolean = false
) {
    val isCompilableSource: Boolean
        get() = extension.lowercase() in setOf("c", "cpp", "cc", "cxx", "ino", "s")

    val isHeader: Boolean
        get() = extension.lowercase() in setOf("h", "hpp", "hh")
}

data class AvrProject(
    val id: String,
    val name: String,
    val rootPath: String,
    val files: List<ProjectFile>,
    val activeFilePath: String,
    val buildConfig: BuildConfigSettings = BuildConfigSettings(),
    val updatedAtMs: Long = System.currentTimeMillis(),
    val lastElfPath: String? = null,
    val lastHexPath: String? = null
)

enum class DiagnosticSeverity {
    ERROR,
    WARNING,
    NOTE
}

data class CompilerDiagnostic(
    val file: String,
    val line: Int,
    val column: Int,
    val severity: DiagnosticSeverity,
    val message: String,
    val rawLine: String
)

data class MemoryUsage(
    val textBytes: Int,
    val dataBytes: Int,
    val bssBytes: Int,
    val flashBytes: Int,
    val flashMaxBytes: Int,
    val sramBytes: Int,
    val sramMaxBytes: Int,
    val eepromBytes: Int = 0
) {
    val flashPercent: Float
        get() = if (flashMaxBytes > 0) (flashBytes * 100f / flashMaxBytes) else 0f

    val sramPercent: Float
        get() = if (sramMaxBytes > 0) (sramBytes * 100f / sramMaxBytes) else 0f
}

enum class BuildAction {
    VERIFY,
    BUILD
}

sealed interface BuildState {
    data object Idle : BuildState
    data class Running(val action: BuildAction, val stage: String) : BuildState
    data class Success(
        val action: BuildAction,
        val durationMs: Long,
        val diagnostics: List<CompilerDiagnostic>,
        val memoryUsage: MemoryUsage?,
        val elfPath: String?,
        val hexPath: String?,
        val lssPath: String? = null,
        val hexValidation: HexValidationResult? = null
    ) : BuildState

    data class Failure(
        val action: BuildAction,
        val durationMs: Long,
        val summary: String,
        val diagnostics: List<CompilerDiagnostic>
    ) : BuildState

    data class Cancelled(val action: BuildAction, val durationMs: Long) : BuildState
}

data class HexValidationResult(
    val isValid: Boolean,
    val recordCount: Int,
    val dataByteCount: Int,
    val hasEofRecord: Boolean,
    val errorReason: String? = null
)

enum class ConsoleLineLevel {
    INFO,
    COMMAND,
    WARNING,
    ERROR,
    SUCCESS
}

data class ConsoleLogLine(
    val id: Long,
    val text: String,
    val level: ConsoleLineLevel,
    val diagnostic: CompilerDiagnostic? = null
)

data class ToolchainStatus(
    val isReady: Boolean,
    val modeLabel: String,
    val abi: String,
    val gccVersion: String?,
    val binDir: String,
    val sysrootDir: String,
    val missingComponents: List<String> = emptyList(),
    val errorDetail: String? = null
)

data class EditorSettings(
    val fontSizeSp: Int = 13,
    val tabWidth: Int = 4,
    val autoCloseBrackets: Boolean = true,
    val autoIndent: Boolean = true,
    val showLineNumbers: Boolean = true,
    val highlightCurrentLine: Boolean = true
)
