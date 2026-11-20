package com.example.octoavr.compiler

import com.example.octoavr.toolchain.AvrToolchainManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.File
import java.io.InputStreamReader
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference

class CompilationEngine {

    data class ProcessExecutionResult(
        val exitCode: Int,
        val outputLines: List<String>,
        val cancelled: Boolean
    )

    private val activeProcess = AtomicReference<Process?>(null)
    private val cancelRequested = AtomicBoolean(false)

    fun resetCancellation() {
        cancelRequested.set(false)
    }

    fun isCancelled(): Boolean = cancelRequested.get()

    fun cancelActiveProcess() {
        cancelRequested.set(true)
        activeProcess.getAndSet(null)?.let { proc ->
            runCatching {
                proc.destroy()
                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                    proc.destroyForcibly()
                }
            }
        }
    }

    suspend fun runTool(
        executable: File,
        args: List<String>,
        workingDir: File,
        toolchain: AvrToolchainManager.ResolvedToolchain,
        onLine: (String) -> Unit
    ): ProcessExecutionResult = withContext(Dispatchers.IO) {
        if (cancelRequested.get()) {
            return@withContext ProcessExecutionResult(-1, emptyList(), cancelled = true)
        }

        val command = listOf(executable.absolutePath) + args
        val pb = ProcessBuilder(command).apply {
            directory(workingDir)
            redirectErrorStream(true)
            val env = environment()
            env["TMPDIR"] = toolchain.tempDir.absolutePath
            env["TMP"] = toolchain.tempDir.absolutePath
            env["TEMP"] = toolchain.tempDir.absolutePath
            if (!toolchain.isHostFallback) {
                val pathPrefix = "${toolchain.binDir.absolutePath}:${toolchain.avrBinDir.absolutePath}"
                val existingPath = env["PATH"] ?: "/system/bin:/system/xbin"
                env["PATH"] = "$pathPrefix:$existingPath"
                env["COMPILER_PATH"] = "${toolchain.libexecGccDir.absolutePath}:${toolchain.avrBinDir.absolutePath}"
            }
        }

        val outputLines = mutableListOf<String>()
        val process = try {
            pb.start()
        } catch (e: Exception) {
            val msg = "Process execution failed (${executable.name}): ${e.message}"
            onLine(msg)
            return@withContext ProcessExecutionResult(-1, listOf(msg), cancelled = false)
        }

        activeProcess.set(process)

        try {
            BufferedReader(InputStreamReader(process.inputStream)).use { reader ->
                while (true) {
                    if (cancelRequested.get()) {
                        process.destroyForcibly()
                        break
                    }
                    val line = reader.readLine() ?: break
                    outputLines += line
                    onLine(line)
                }
            }
            val exitCode = if (cancelRequested.get()) {
                runCatching { process.destroyForcibly() }
                -1
            } else {
                process.waitFor()
            }
            ProcessExecutionResult(
                exitCode = exitCode,
                outputLines = outputLines,
                cancelled = cancelRequested.get()
            )
        } finally {
            activeProcess.compareAndSet(process, null)
        }
    }
}
