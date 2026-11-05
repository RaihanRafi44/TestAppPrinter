package com.raihan.testappprinter.printer

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothSocket
import android.content.Context
import android.hardware.usb.UsbConstants
import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbDeviceConnection
import android.hardware.usb.UsbEndpoint
import android.hardware.usb.UsbInterface
import android.hardware.usb.UsbManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.withContext
import java.io.IOException
import java.io.OutputStream
import java.util.UUID

sealed class ConnectionState {
    data object Disconnected : ConnectionState()
    data class Connecting(val deviceName: String) : ConnectionState()
    data class Connected(val deviceName: String, val type: ConnectionType) : ConnectionState()
    data class Error(val message: String) : ConnectionState()
}

enum class ConnectionType {
    BLUETOOTH, USB
}

class PrinterManager(private val context: Context) {

    private val bluetoothAdapter: BluetoothAdapter? by lazy {
        val manager = context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager
        manager?.adapter
    }

    private val usbManager: UsbManager by lazy {
        context.getSystemService(Context.USB_SERVICE) as UsbManager
    }

    private val _connectionState = MutableStateFlow<ConnectionState>(ConnectionState.Disconnected)
    val connectionState: StateFlow<ConnectionState> = _connectionState

    // Active Bluetooth Connection
    private var bluetoothSocket: BluetoothSocket? = null
    private var bluetoothOutputStream: OutputStream? = null

    // Active USB Connection
    private var usbConnection: UsbDeviceConnection? = null
    private var usbInterface: UsbInterface? = null
    private var usbEndpointOut: UsbEndpoint? = null

    private val sppUuid: UUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB")

    /**
     * List bonded/paired Bluetooth devices
     */
    @SuppressLint("MissingPermission")
    fun getPairedBluetoothDevices(): List<BluetoothDevice> {
        return bluetoothAdapter?.bondedDevices?.toList() ?: emptyList()
    }

    /**
     * List connected USB devices
     */
    fun getConnectedUsbDevices(): List<UsbDevice> {
        val deviceList = usbManager.deviceList
        return deviceList.values.toList()
    }

    /**
     * Check if USB device has permission
     */
    fun hasUsbPermission(device: UsbDevice): Boolean {
        return usbManager.hasPermission(device)
    }

    /**
     * Request USB Permission
     */
    fun requestUsbPermission(device: UsbDevice, pendingIntent: android.app.PendingIntent) {
        usbManager.requestPermission(device, pendingIntent)
    }

    /**
     * Connect to Bluetooth device
     */
    @SuppressLint("MissingPermission")
    suspend fun connectBluetooth(device: BluetoothDevice): Boolean = withContext(Dispatchers.IO) {
        val deviceName = device.name ?: device.address
        _connectionState.value = ConnectionState.Connecting(deviceName)
        disconnect()

        try {
            val socket = device.createRfcommSocketToServiceRecord(sppUuid)
            bluetoothAdapter?.cancelDiscovery()
            socket.connect()

            bluetoothSocket = socket
            bluetoothOutputStream = socket.outputStream
            _connectionState.value = ConnectionState.Connected(deviceName, ConnectionType.BLUETOOTH)
            true
        } catch (e: Exception) {
            disconnect()
            _connectionState.value = ConnectionState.Error("Gagal terhubung Bluetooth: ${e.localizedMessage}")
            false
        }
    }

    /**
     * Connect to USB device
     */
    suspend fun connectUsb(device: UsbDevice): Boolean = withContext(Dispatchers.IO) {
        val deviceName = device.productName ?: device.deviceName
        _connectionState.value = ConnectionState.Connecting(deviceName)
        disconnect()

        if (!usbManager.hasPermission(device)) {
            _connectionState.value = ConnectionState.Error("Tidak ada izin USB untuk $deviceName")
            return@withContext false
        }

        try {
            val connection = usbManager.openDevice(device)
                ?: throw IOException("Gagal membuka USB Device Connection")

            var foundInterface: UsbInterface? = null
            var foundEndpoint: UsbEndpoint? = null

            // Find printer interface or default interface with Bulk OUT endpoint
            for (i in 0 until device.interfaceCount) {
                val iface = device.getInterface(i)
                if (iface.interfaceClass == UsbConstants.USB_CLASS_PRINTER || device.interfaceCount == 1) {
                    for (j in 0 until iface.endpointCount) {
                        val ep = iface.getEndpoint(j)
                        if (ep.type == UsbConstants.USB_ENDPOINT_XFER_BULK && ep.direction == UsbConstants.USB_DIR_OUT) {
                            foundInterface = iface
                            foundEndpoint = ep
                            break
                        }
                    }
                }
                if (foundEndpoint != null) break
            }

            if (foundInterface == null || foundEndpoint == null) {
                connection.close()
                _connectionState.value = ConnectionState.Error("Endpoint USB Printer OUT tidak ditemukan")
                return@withContext false
            }

            connection.claimInterface(foundInterface, true)
            usbConnection = connection
            usbInterface = foundInterface
            usbEndpointOut = foundEndpoint

            _connectionState.value = ConnectionState.Connected(deviceName, ConnectionType.USB)
            true
        } catch (e: Exception) {
            disconnect()
            _connectionState.value = ConnectionState.Error("Gagal terhubung USB: ${e.localizedMessage}")
            false
        }
    }

    /**
     * Send byte array data to connected printer
     */
    suspend fun sendData(data: ByteArray): Boolean = withContext(Dispatchers.IO) {
        when (val state = _connectionState.value) {
            is ConnectionState.Connected -> {
                try {
                    when (state.type) {
                        ConnectionType.BLUETOOTH -> {
                            val stream = bluetoothOutputStream
                                ?: throw IOException("Bluetooth output stream null")
                            stream.write(data)
                            stream.flush()
                            true
                        }
                        ConnectionType.USB -> {
                            val connection = usbConnection
                                ?: throw IOException("USB connection null")
                            val endpoint = usbEndpointOut
                                ?: throw IOException("USB endpoint null")

                            val chunkSize = 4096
                            var offset = 0
                            while (offset < data.size) {
                                val length = minOf(chunkSize, data.size - offset)
                                val chunk = data.copyOfRange(offset, offset + length)
                                val transferred = connection.bulkTransfer(endpoint, chunk, length, 5000)
                                if (transferred < 0) {
                                    throw IOException("Gagal mentransfer data USB (bulkTransfer returned $transferred)")
                                }
                                offset += length
                            }
                            true
                        }
                    }
                } catch (e: Exception) {
                    _connectionState.value = ConnectionState.Error("Gagal mengirim data ke printer: ${e.localizedMessage}")
                    false
                }
            }
            else -> {
                _connectionState.value = ConnectionState.Error("Printer tidak terhubung")
                false
            }
        }
    }

    /**
     * Disconnect active connection
     */
    fun disconnect() {
        try {
            bluetoothOutputStream?.close()
            bluetoothSocket?.close()
        } catch (_: Exception) {}
        bluetoothOutputStream = null
        bluetoothSocket = null

        try {
            usbInterface?.let { usbConnection?.releaseInterface(it) }
            usbConnection?.close()
        } catch (_: Exception) {}
        usbInterface = null
        usbEndpointOut = null
        usbConnection = null

        _connectionState.value = ConnectionState.Disconnected
    }
}
