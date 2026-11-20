package com.example.octoavr.files

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import androidx.core.content.FileProvider
import java.io.File

class FirmwareExportManager(private val context: Context) {

    enum class LaunchOutcome {
        OPENED_ZFLASHER,
        OPENED_CHOOSER,
        FAILED
    }

    data class ExportLaunchResult(
        val outcome: LaunchOutcome,
        val message: String
    )

    fun isZFlasherInstalled(): Boolean {
        val pm = context.packageManager
        return runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                pm.getPackageInfo(ZFLASHER_PACKAGE, PackageManager.PackageInfoFlags.of(0))
            } else {
                @Suppress("DEPRECATION")
                pm.getPackageInfo(ZFLASHER_PACKAGE, 0)
            }
            true
        }.getOrDefault(false)
    }

    fun copyFileToUri(sourceFile: File, targetUri: Uri): Result<Long> = runCatching {
        require(sourceFile.exists()) { "Firmware file does not exist: ${sourceFile.name}" }
        context.contentResolver.openOutputStream(targetUri)?.use { out ->
            sourceFile.inputStream().use { input ->
                input.copyTo(out)
            }
        } ?: throw IllegalStateException("Unable to open destination URI for writing")
        sourceFile.length()
    }

    fun createShareableCopyAndUri(sourceFile: File): Uri {
        val exportsDir = File(context.filesDir, "exports").apply { mkdirs() }
        val target = File(exportsDir, sourceFile.name)
        sourceFile.copyTo(target, overwrite = true)
        return FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            target
        )
    }

    fun launchWithZFlasherOrChooser(hexFile: File): ExportLaunchResult {
        if (!hexFile.exists()) {
            return ExportLaunchResult(
                LaunchOutcome.FAILED,
                "HEX file not found. Build the firmware first."
            )
        }

        val uri = runCatching { createShareableCopyAndUri(hexFile) }.getOrElse { err ->
            return ExportLaunchResult(
                LaunchOutcome.FAILED,
                "Failed to prepare FileProvider URI: ${err.message}"
            )
        }

        if (isZFlasherInstalled()) {
            val zIntent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, "application/octet-stream")
                setPackage(ZFLASHER_PACKAGE)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            val started = runCatching {
                context.startActivity(zIntent)
                true
            }.getOrDefault(false)

            if (started) {
                return ExportLaunchResult(
                    LaunchOutcome.OPENED_ZFLASHER,
                    "Handed ${hexFile.name} to ZFlasher AVR. Complete flashing inside ZFlasher."
                )
            }

            // Try ACTION_SEND to ZFlasher as secondary intent contract
            val sendIntent = Intent(Intent.ACTION_SEND).apply {
                type = "application/octet-stream"
                putExtra(Intent.EXTRA_STREAM, uri)
                setPackage(ZFLASHER_PACKAGE)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            val sent = runCatching {
                context.startActivity(sendIntent)
                true
            }.getOrDefault(false)

            if (sent) {
                return ExportLaunchResult(
                    LaunchOutcome.OPENED_ZFLASHER,
                    "Sent ${hexFile.name} to ZFlasher AVR. Confirm flash status in ZFlasher."
                )
            }
        }

        val chooserBase = Intent(Intent.ACTION_SEND).apply {
            type = "application/octet-stream"
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_SUBJECT, hexFile.name)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        val chooser = Intent.createChooser(chooserBase, "Open ${hexFile.name} with AVR Flasher").apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        return runCatching {
            context.startActivity(chooser)
            ExportLaunchResult(
                LaunchOutcome.OPENED_CHOOSER,
                "Opened system share sheet for ${hexFile.name}. Select ZFlasher AVR or another flasher."
            )
        }.getOrElse { err ->
            ExportLaunchResult(
                LaunchOutcome.FAILED,
                "No compatible handler found for ${hexFile.name}: ${err.message}"
            )
        }
    }

    companion object {
        const val ZFLASHER_PACKAGE = "ru.zdevs.zflasher.avr"
    }
}
