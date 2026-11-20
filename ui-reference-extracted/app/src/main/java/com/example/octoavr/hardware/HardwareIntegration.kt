package com.example.octoavr.hardware

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbManager
import android.os.Build

enum class SerialParity(val label: String) {
    NONE("None (N)"),
    EVEN("Even (E)"),
    ODD("Odd (O)")
}

data class UartConfig(
    val baudRate: Int = 9600,
    val dataBits: Int = 8,
    val parity: SerialParity = SerialParity.NONE,
    val stopBits: Int = 1
) {
    val summary: String get() = "$baudRate ${dataBits}${parity.label.first()}$stopBits"

    companion object {
        val SUPPORTED_BAUDS = listOf(1200, 2400, 4800, 9600, 19200, 38400, 57600, 115200)
    }
}

enum class UsbBridgeType(val label: String, val isProgrammer: Boolean) {
    USBASP("USBasp ISP Programmer", isProgrammer = true),
    USBTINY("USBtinyISP Programmer", isProgrammer = true),
    AVRISP_MKII("Atmel AVRISP mkII / STK500", isProgrammer = true),
    CH340("WCH CH340/CH341 USB-UART", isProgrammer = false),
    CP210X("Silicon Labs CP210x USB-UART", isProgrammer = false),
    FTDI("FTDI FT232R USB-UART", isProgrammer = false),
    ARDUINO_CDC("Arduino / ATmega16U2 CDC", isProgrammer = false),
    UNKNOWN("Generic USB Device", isProgrammer = false)
}

data class UsbOtgDeviceInfo(
    val deviceName: String,
    val vendorId: Int,
    val productId: Int,
    val productName: String,
    val manufacturerName: String,
    val bridgeType: UsbBridgeType,
    val hasPermission: Boolean
) {
    val vidPidHex: String
        get() = "%04X:%04X".format(vendorId, productId)
}

interface SerialMonitorTransport {
    val isSupported: Boolean
    val statusDescription: String
}

class UsbOtgInspector(private val context: Context) {

    private val usbManager: UsbManager?
        get() = context.getSystemService(Context.USB_SERVICE) as? UsbManager

    fun isUsbHostSupported(): Boolean =
        context.packageManager.hasSystemFeature("android.hardware.usb.host")

    fun scanConnectedDevices(): List<UsbOtgDeviceInfo> {
        val manager = usbManager ?: return emptyList()
        return runCatching {
            manager.deviceList.values.map { dev ->
                UsbOtgDeviceInfo(
                    deviceName = dev.deviceName,
                    vendorId = dev.vendorId,
                    productId = dev.productId,
                    productName = dev.productName ?: "USB Peripheral",
                    manufacturerName = dev.manufacturerName ?: "Unknown Vendor",
                    bridgeType = classifyDevice(dev),
                    hasPermission = manager.hasPermission(dev)
                )
            }.sortedBy { it.deviceName }
        }.getOrDefault(emptyList())
    }

    fun requestUsbPermission(deviceName: String): Boolean {
        val manager = usbManager ?: return false
        val device = manager.deviceList.values.firstOrNull { it.deviceName == deviceName } ?: return false
        val flags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            PendingIntent.FLAG_MUTABLE
        } else {
            0
        }
        val intent = Intent(ACTION_USB_PERMISSION).setPackage(context.packageName)
        val pending = PendingIntent.getBroadcast(context, 0, intent, flags)
        manager.requestPermission(device, pending)
        return true
    }

    private fun classifyDevice(device: UsbDevice): UsbBridgeType {
        return when (device.vendorId) {
            0x16C0 -> if (device.productId == 0x05DC) UsbBridgeType.USBASP else UsbBridgeType.UNKNOWN
            0x1781 -> UsbBridgeType.USBTINY
            0x03EB -> UsbBridgeType.AVRISP_MKII
            0x1A86 -> UsbBridgeType.CH340
            0x10C4 -> UsbBridgeType.CP210X
            0x0403 -> UsbBridgeType.FTDI
            0x2341 -> UsbBridgeType.ARDUINO_CDC
            else -> UsbBridgeType.UNKNOWN
        }
    }

    companion object {
        const val ACTION_USB_PERMISSION = "com.example.octoavr.USB_PERMISSION"
    }
}
