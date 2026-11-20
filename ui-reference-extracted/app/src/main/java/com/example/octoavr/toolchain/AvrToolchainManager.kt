package com.example.octoavr.toolchain

import android.content.Context
import android.os.Build
import android.system.Os
import com.example.octoavr.model.ToolchainStatus
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.BufferedInputStream
import java.io.File
import java.io.FileOutputStream
import java.util.zip.ZipInputStream

class AvrToolchainManager(private val context: Context) {

    data class ResolvedToolchain(
        val gccExecutable: File,
        val gppExecutable: File,
        val asExecutable: File,
        val ldExecutable: File,
        val arExecutable: File,
        val objcopyExecutable: File,
        val objdumpExecutable: File,
        val sizeExecutable: File,
        val binDir: File,
        val libexecGccDir: File,
        val avrBinDir: File,
        val sysrootDir: File,
        val tempDir: File,
        val isHostFallback: Boolean
    )

    private val toolchainRoot: File
        get() = File(context.filesDir, "toolchain").apply { mkdirs() }

    val sysrootDir: File
        get() = File(toolchainRoot, "sysroot")

    val binDir: File
        get() = File(toolchainRoot, "bin")

    val libexecGccDir: File
        get() = File(toolchainRoot, "libexec/gcc/avr/$GCC_VERSION")

    val avrBinDir: File
        get() = File(toolchainRoot, "avr/bin")

    val tempDir: File
        get() = File(context.filesDir, "tmp").apply { mkdirs() }

    private val setupMutex = Mutex()

    suspend fun ensureToolchainReady(): ToolchainStatus = withContext(Dispatchers.IO) {
        setupMutex.withLock {
            val abi = Build.SUPPORTED_ABIS.firstOrNull() ?: "unknown"
        runCatching {
            extractSysrootIfNeeded()
        }.onFailure { err ->
            return@withContext ToolchainStatus(
                isReady = false,
                modeLabel = "Sysroot Extraction Error",
                abi = abi,
                gccVersion = null,
                binDir = binDir.absolutePath,
                sysrootDir = sysrootDir.absolutePath,
                errorDetail = err.message ?: "Failed to extract AVR sysroot"
            )
        }

        val resolved = resolveToolchain()
        if (resolved == null) {
            val missing = checkMissingNativeLibs()
            return@withContext ToolchainStatus(
                isReady = false,
                modeLabel = "Missing Native Binaries ($abi)",
                abi = abi,
                gccVersion = null,
                binDir = binDir.absolutePath,
                sysrootDir = sysrootDir.absolutePath,
                missingComponents = missing,
                errorDetail = "Native AVR toolchain binaries for ABI '$abi' not found in ${context.applicationInfo.nativeLibraryDir}"
            )
        }

            val version = queryGccVersion(resolved)
            ToolchainStatus(
                isReady = version != null,
                modeLabel = if (resolved.isHostFallback) "Host JVM Toolchain (Test)" else "Offline Android Native ($abi)",
                abi = abi,
                gccVersion = version,
                binDir = resolved.binDir.absolutePath,
                sysrootDir = resolved.sysrootDir.absolutePath,
                errorDetail = if (version == null) "avr-gcc failed to execute (--version check)" else null
            )
        }
    }

    fun resolveToolchain(): ResolvedToolchain? {
        val nativeDirPath = context.applicationInfo.nativeLibraryDir
        val nativeDir = nativeDirPath?.let(::File)
        val missing = checkMissingNativeLibs()
        if (nativeDir != null && missing.isEmpty()) {
            setupSymlinks(nativeDir)
            return ResolvedToolchain(
                gccExecutable = File(binDir, "avr-gcc"),
                gppExecutable = File(binDir, "avr-g++"),
                asExecutable = File(binDir, "avr-as"),
                ldExecutable = File(binDir, "avr-ld"),
                arExecutable = File(binDir, "avr-ar"),
                objcopyExecutable = File(binDir, "avr-objcopy"),
                objdumpExecutable = File(binDir, "avr-objdump"),
                sizeExecutable = File(binDir, "avr-size"),
                binDir = binDir,
                libexecGccDir = libexecGccDir,
                avrBinDir = avrBinDir,
                sysrootDir = sysrootDir,
                tempDir = tempDir,
                isHostFallback = false
            )
        }

        // Only allow host /usr/bin/avr-gcc fallback inside Robolectric JVM unit tests
        if (isRobolectricEnvironment()) {
            val hostGcc = File("/usr/bin/avr-gcc")
            val hostGpp = File("/usr/bin/avr-g++").takeIf { it.exists() } ?: hostGcc
            val hostAs = File("/usr/bin/avr-as")
            val hostLd = File("/usr/bin/avr-ld")
            val hostAr = File("/usr/bin/avr-ar")
            val hostObjcopy = File("/usr/bin/avr-objcopy")
            val hostObjdump = File("/usr/bin/avr-objdump")
            val hostSize = File("/usr/bin/avr-size")
            if (hostGcc.canExecute() && hostObjcopy.canExecute() && hostSize.canExecute()) {
                val effectiveSysroot = if (File(sysrootDir, "avr/include/avr/io.h").exists()) {
                    sysrootDir
                } else {
                    File("/usr/lib")
                }
                return ResolvedToolchain(
                    gccExecutable = hostGcc,
                    gppExecutable = hostGpp,
                    asExecutable = hostAs,
                    ldExecutable = hostLd,
                    arExecutable = hostAr,
                    objcopyExecutable = hostObjcopy,
                    objdumpExecutable = hostObjdump,
                    sizeExecutable = hostSize,
                    binDir = File("/usr/bin"),
                    libexecGccDir = File("/usr/lib/gcc/avr"),
                    avrBinDir = File("/usr/avr/bin"),
                    sysrootDir = effectiveSysroot,
                    tempDir = tempDir,
                    isHostFallback = true
                )
            }
        }
        return null
    }

    fun checkMissingNativeLibs(): List<String> {
        val nativeDirPath = context.applicationInfo.nativeLibraryDir ?: return REQUIRED_NATIVE_LIBS
        val nativeDir = File(nativeDirPath)
        if (!nativeDir.exists()) return REQUIRED_NATIVE_LIBS
        return REQUIRED_NATIVE_LIBS.filter { libName ->
            val f = File(nativeDir, libName)
            !f.exists() || !f.canExecute()
        }
    }

    private fun setupSymlinks(nativeDir: File) {
        binDir.mkdirs()
        libexecGccDir.mkdirs()
        avrBinDir.mkdirs()

        val mapping = listOf(
            File(binDir, "avr-gcc") to File(nativeDir, "libavr_gcc.so"),
            File(binDir, "avr-g++") to File(nativeDir, "libavr_gpp.so"),
            File(binDir, "avr-as") to File(nativeDir, "libavr_as.so"),
            File(binDir, "avr-ld") to File(nativeDir, "libavr_ld.so"),
            File(binDir, "avr-ar") to File(nativeDir, "libavr_ar.so"),
            File(binDir, "avr-objcopy") to File(nativeDir, "libavr_objcopy.so"),
            File(binDir, "avr-objdump") to File(nativeDir, "libavr_objdump.so"),
            File(binDir, "avr-size") to File(nativeDir, "libavr_size.so"),
            File(libexecGccDir, "cc1") to File(nativeDir, "libavr_cc1.so"),
            File(libexecGccDir, "cc1plus") to File(nativeDir, "libavr_cc1plus.so"),
            File(libexecGccDir, "collect2") to File(nativeDir, "libavr_collect2.so"),
            File(libexecGccDir, "as") to File(nativeDir, "libavr_as.so"),
            File(libexecGccDir, "ld") to File(nativeDir, "libavr_ld.so"),
            File(libexecGccDir, "real-ld") to File(nativeDir, "libavr_ld.so"),
            File(avrBinDir, "as") to File(nativeDir, "libavr_as.so"),
            File(avrBinDir, "ld") to File(nativeDir, "libavr_ld.so"),
            File(avrBinDir, "ar") to File(nativeDir, "libavr_ar.so")
        )

        for ((link, target) in mapping) {
            if (!target.exists()) continue
            val needsRelink = runCatching {
                !link.exists() || Os.readlink(link.absolutePath) != target.absolutePath
            }.getOrDefault(true)
            if (needsRelink) {
                link.delete()
                Os.symlink(target.absolutePath, link.absolutePath)
            }
        }

        // Link toolchainRoot/avr/lib -> sysrootDir/avr/lib so avr-ld resolves ldscripts automatically
        val avrLibLink = File(toolchainRoot, "avr/lib")
        val avrLibTarget = File(sysrootDir, "avr/lib")
        if (avrLibTarget.exists()) {
            val needsLibLink = runCatching {
                !avrLibLink.exists() || Os.readlink(avrLibLink.absolutePath) != avrLibTarget.absolutePath
            }.getOrDefault(true)
            if (needsLibLink) {
                avrLibLink.delete()
                Os.symlink(avrLibTarget.absolutePath, avrLibLink.absolutePath)
            }
        }
    }

    private fun extractSysrootIfNeeded() {
        val marker = File(sysrootDir, ".sysroot_version")
        if (marker.exists() && marker.readText().trim() == SYSROOT_VERSION &&
            File(sysrootDir, "avr/include/avr/io.h").exists() &&
            File(sysrootDir, "lib/gcc/avr/$GCC_VERSION/device-specs/specs-atmega32a").exists()
        ) {
            return
        }

        val assetNames = context.assets.list("")?.toSet() ?: emptySet()
        if (!assetNames.contains(SYSROOT_ASSET_NAME)) {
            if (isRobolectricEnvironment() && File("/usr/lib/avr/include/avr/io.h").exists()) {
                return
            }
            throw IllegalStateException("Missing $SYSROOT_ASSET_NAME in application assets")
        }

        if (sysrootDir.exists()) {
            sysrootDir.deleteRecursively()
        }
        sysrootDir.mkdirs()

        context.assets.open(SYSROOT_ASSET_NAME).use { rawStream ->
            ZipInputStream(BufferedInputStream(rawStream)).use { zis ->
                val buffer = ByteArray(16384)
                while (true) {
                    val entry = zis.nextEntry ?: break
                    val outFile = File(sysrootDir, entry.name)
                    if (!outFile.canonicalPath.startsWith(sysrootDir.canonicalPath)) {
                        throw SecurityException("Invalid zip entry path: ${entry.name}")
                    }
                    if (entry.isDirectory) {
                        outFile.mkdirs()
                    } else {
                        outFile.parentFile?.mkdirs()
                        FileOutputStream(outFile).use { fos ->
                            while (true) {
                                val count = zis.read(buffer)
                                if (count <= 0) break
                                fos.write(buffer, 0, count)
                            }
                        }
                    }
                    zis.closeEntry()
                }
            }
        }
        marker.writeText(SYSROOT_VERSION)
    }

    private fun queryGccVersion(resolved: ResolvedToolchain): String? {
        return runCatching {
            val pb = ProcessBuilder(resolved.gccExecutable.absolutePath, "--version")
            pb.redirectErrorStream(true)
            pb.environment()["TMPDIR"] = resolved.tempDir.absolutePath
            val proc = pb.start()
            val out = proc.inputStream.bufferedReader().readText().trim()
            val code = proc.waitFor()
            if (code == 0) out.lines().firstOrNull()?.trim() else null
        }.getOrNull()
    }

    private fun isRobolectricEnvironment(): Boolean =
        Build.FINGERPRINT.equals("robolectric", ignoreCase = true)

    companion object {
        const val GCC_VERSION = "7.3.0"
        const val SYSROOT_VERSION = "avr-gcc-7.3.0-atmel3.6.1-v2"
        const val SYSROOT_ASSET_NAME = "avr_sysroot.zip"

        val REQUIRED_NATIVE_LIBS = listOf(
            "libavr_gcc.so",
            "libavr_gpp.so",
            "libavr_cc1.so",
            "libavr_cc1plus.so",
            "libavr_collect2.so",
            "libavr_as.so",
            "libavr_ld.so",
            "libavr_ar.so",
            "libavr_objcopy.so",
            "libavr_objdump.so",
            "libavr_size.so"
        )
    }
}
