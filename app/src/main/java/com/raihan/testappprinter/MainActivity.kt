package com.raihan.testappprinter

import android.Manifest
import android.annotation.SuppressLint
import android.app.PendingIntent
import android.bluetooth.BluetoothDevice
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Bitmap
import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbManager
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.raihan.testappprinter.printer.ConnectionState
import com.raihan.testappprinter.printer.ImageProcessor
import com.raihan.testappprinter.printer.PrinterManager
import com.raihan.testappprinter.printer.TsplCommand
import com.raihan.testappprinter.ui.theme.TestAppPrinterTheme
import kotlinx.coroutines.launch

private const val ACTION_USB_PERMISSION = "com.raihan.testappprinter.USB_PERMISSION"

class MainActivity : ComponentActivity() {

    private lateinit var printerManager: PrinterManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        printerManager = PrinterManager(this)
        enableEdgeToEdge()

        setContent {
            TestAppPrinterTheme {
                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    PrinterTestAppScreen(
                        printerManager = printerManager,
                        modifier = Modifier.padding(innerPadding)
                    )
                }
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        printerManager.disconnect()
    }
}

@SuppressLint("MissingPermission")
@Composable
fun PrinterTestAppScreen(
    printerManager: PrinterManager?,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    val connectionState = printerManager?.connectionState?.collectAsState()?.value
        ?: ConnectionState.Disconnected

    var selectedTab by remember { mutableIntStateOf(0) } // 0: Bluetooth, 1: USB

    var bluetoothDevices by remember { mutableStateOf<List<BluetoothDevice>>(emptyList()) }
    var usbDevices by remember { mutableStateOf<List<UsbDevice>>(emptyList()) }

    var isPrinting by remember { mutableStateOf(false) }

    val sampleBitmap by remember {
        mutableStateOf(ImageProcessor.createSamplePhotoboothStrip(800))
    }

    // Bluetooth Permission Launcher
    val bluetoothPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val allGranted = permissions.values.all { it }
        if (allGranted) {
            bluetoothDevices = printerManager?.getPairedBluetoothDevices() ?: emptyList()
            Toast.makeText(context, "Izin Bluetooth diberikan", Toast.LENGTH_SHORT).show()
        } else {
            Toast.makeText(context, "Izin Bluetooth diperlukan untuk mencari printer", Toast.LENGTH_SHORT).show()
        }
    }

    fun refreshDevices() {
        if (selectedTab == 0) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                bluetoothPermissionLauncher.launch(
                    arrayOf(
                        Manifest.permission.BLUETOOTH_CONNECT,
                        Manifest.permission.BLUETOOTH_SCAN
                    )
                )
            } else {
                bluetoothDevices = printerManager?.getPairedBluetoothDevices() ?: emptyList()
            }
        } else {
            usbDevices = printerManager?.getConnectedUsbDevices() ?: emptyList()
        }
    }

    LaunchedEffect(selectedTab) {
        refreshDevices()
    }

    // USB Permission Broadcast Receiver
    DisposableEffect(context) {
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                if (ACTION_USB_PERMISSION == intent?.action) {
                    val device: UsbDevice? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        intent.getParcelableExtra(UsbManager.EXTRA_DEVICE, UsbDevice::class.java)
                    } else {
                        @Suppress("DEPRECATION")
                        intent.getParcelableExtra(UsbManager.EXTRA_DEVICE)
                    }

                    if (intent.getBooleanExtra(UsbManager.EXTRA_PERMISSION_GRANTED, false) && (device != null)) {
                        scope.launch {
                            printerManager?.connectUsb(device)
                        }
                    } else {
                        Toast.makeText(context, "Izin USB ditolak oleh pengguna", Toast.LENGTH_SHORT).show()
                    }
                }
            }
        }

        val filter = IntentFilter(ACTION_USB_PERMISSION)
        ContextCompat.registerReceiver(
            context,
            receiver,
            filter,
            ContextCompat.RECEIVER_NOT_EXPORTED
        )

        onDispose {
            context.unregisterReceiver(receiver)
        }
    }

    BoxWithConstraints(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        val isLandscapeTablet = maxWidth >= 600.dp && maxWidth > maxHeight
        val isPortraitTablet = maxWidth >= 600.dp && maxHeight >= maxWidth

        Column(modifier = Modifier.fillMaxSize()) {
            // Header
            Text(
                text = when {
                    isLandscapeTablet -> "Xprinter XP-410B / XP-4601B Test (Tablet Landscape)"
                    isPortraitTablet -> "Xprinter XP-410B / XP-4601B Test (Tablet Portrait)"
                    else -> "Xprinter XP-410B / XP-4601B Test"
                },
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )
            Text(
                text = "Penguji Koneksi USB/Bluetooth & Preview Strip Photobooth TSPL (203 DPI)",
                style = MaterialTheme.typography.bodySmall,
                color = Color.Gray
            )

            Spacer(modifier = Modifier.height(12.dp))

            if (isLandscapeTablet) {
                // TABLET / WIDE LANDSCAPE LAYOUT (2 COLUMNS SIDE-BY-SIDE)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    // Left Column: Connection Status & Devices List
                    Column(
                        modifier = Modifier
                            .weight(1.1f)
                            .fillMaxHeight()
                    ) {
                        PrinterStatusCard(
                            connectionState = connectionState,
                            onDisconnect = { printerManager?.disconnect() }
                        )

                        Spacer(modifier = Modifier.height(12.dp))

                        TabRow(selectedTabIndex = selectedTab) {
                            Tab(selected = selectedTab == 0, onClick = { selectedTab = 0 }, text = { Text("Bluetooth") })
                            Tab(selected = selectedTab == 1, onClick = { selectedTab = 1 }, text = { Text("USB OTG") })
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = if (selectedTab == 0) "Device Bluetooth Paired:" else "Device USB Terhubung:",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.SemiBold
                            )
                            OutlinedButton(onClick = { refreshDevices() }) {
                                Text("Refresh", fontSize = 12.sp)
                            }
                        }

                        Spacer(modifier = Modifier.height(6.dp))

                        DeviceListContainer(
                            modifier = Modifier.weight(1f),
                            selectedTab = selectedTab,
                            bluetoothDevices = bluetoothDevices,
                            usbDevices = usbDevices,
                            connectionState = connectionState,
                            printerManager = printerManager,
                            context = context,
                            scope = scope
                        )
                    }

                    // Right Column: Photobooth Preview & Action Buttons
                    Column(
                        modifier = Modifier
                            .weight(0.9f)
                            .fillMaxHeight()
                            .verticalScroll(rememberScrollState())
                    ) {
                        PrintActionSection(
                            connectionState = connectionState,
                            isPrinting = isPrinting,
                            printerManager = printerManager,
                            photoboothBitmap = sampleBitmap,
                            context = context,
                            scope = scope,
                            onPrintStateChange = { isPrinting = it }
                        )

                        Spacer(modifier = Modifier.height(12.dp))

                        PhotoboothPreviewCard(sampleBitmap)
                    }
                }
            } else {
                // PORTRAIT LAYOUT (PHONE OR PORTRAIT TABLET - STACKED)
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                ) {
                    PrinterStatusCard(
                        connectionState = connectionState,
                        onDisconnect = { printerManager?.disconnect() }
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    TabRow(selectedTabIndex = selectedTab) {
                        Tab(selected = selectedTab == 0, onClick = { selectedTab = 0 }, text = { Text("Bluetooth") })
                        Tab(selected = selectedTab == 1, onClick = { selectedTab = 1 }, text = { Text("USB OTG") })
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = if (selectedTab == 0) "Device Bluetooth Paired:" else "Device USB Terhubung:",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.SemiBold
                        )
                        OutlinedButton(onClick = { refreshDevices() }) {
                            Text("Refresh", fontSize = 12.sp)
                        }
                    }

                    Spacer(modifier = Modifier.height(6.dp))

                    val deviceListHeight = if (isPortraitTablet) 280.dp else 180.dp

                    DeviceListContainer(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(deviceListHeight),
                        selectedTab = selectedTab,
                        bluetoothDevices = bluetoothDevices,
                        usbDevices = usbDevices,
                        connectionState = connectionState,
                        printerManager = printerManager,
                        context = context,
                        scope = scope
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    PrintActionSection(
                        connectionState = connectionState,
                        isPrinting = isPrinting,
                        printerManager = printerManager,
                        photoboothBitmap = sampleBitmap,
                        context = context,
                        scope = scope,
                        onPrintStateChange = { isPrinting = it }
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    PhotoboothPreviewCard(
                        bitmap = sampleBitmap,
                        imageWidthFraction = if (isPortraitTablet) 0.5f else 0.75f
                    )
                }
            }
        }
    }
}

@Composable
fun PrinterStatusCard(
    connectionState: ConnectionState,
    onDisconnect: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = when (connectionState) {
                is ConnectionState.Connected -> Color(0xFFE8F5E9)
                is ConnectionState.Connecting -> Color(0xFFFFF8E1)
                is ConnectionState.Error -> Color(0xFFFFEBEE)
                is ConnectionState.Disconnected -> Color(0xFFF5F5F5)
            }
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Status Printer:",
                    fontWeight = FontWeight.SemiBold,
                    style = MaterialTheme.typography.bodyMedium
                )

                when (connectionState) {
                    is ConnectionState.Connected -> {
                        val state = connectionState
                        Text(
                            text = "TERHUBUNG (${state.type})",
                            color = Color(0xFF2E7D32),
                            fontWeight = FontWeight.Bold
                        )
                    }
                    is ConnectionState.Connecting -> {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(text = "MENGHUBUNGKAN...", color = Color(0xFFF57F17), fontWeight = FontWeight.Bold)
                        }
                    }
                    is ConnectionState.Error -> {
                        Text(text = "ERROR", color = Color(0xFFC62828), fontWeight = FontWeight.Bold)
                    }
                    is ConnectionState.Disconnected -> {
                        Text(text = "TERPUTUS", color = Color.Gray, fontWeight = FontWeight.Bold)
                    }
                }
            }

            when (val state = connectionState) {
                is ConnectionState.Connected -> {
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(text = "Device: ${state.deviceName}", style = MaterialTheme.typography.bodySmall)
                    Spacer(modifier = Modifier.height(6.dp))
                    OutlinedButton(
                        onClick = onDisconnect,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Putuskan Koneksi", fontSize = 12.sp)
                    }
                }
                is ConnectionState.Error -> {
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(text = state.message, color = Color.Red, style = MaterialTheme.typography.bodySmall)
                }
                else -> {}
            }
        }
    }
}

@SuppressLint("MissingPermission")
@Composable
fun DeviceListContainer(
    modifier: Modifier = Modifier,
    selectedTab: Int,
    bluetoothDevices: List<BluetoothDevice>,
    usbDevices: List<UsbDevice>,
    connectionState: ConnectionState,
    printerManager: PrinterManager?,
    context: Context,
    scope: kotlinx.coroutines.CoroutineScope
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .background(Color(0xFFFAFAFA), shape = RoundedCornerShape(8.dp))
            .border(1.dp, Color(0xFFE0E0E0), shape = RoundedCornerShape(8.dp))
            .padding(8.dp)
    ) {
        if (selectedTab == 0) {
            if (bluetoothDevices.isEmpty()) {
                Text(
                    text = "Tidak ada device Bluetooth paired. Pastikan Bluetooth aktif dan Xprinter sudah di-pair di HP.",
                    modifier = Modifier.align(Alignment.Center),
                    textAlign = TextAlign.Center,
                    color = Color.Gray,
                    style = MaterialTheme.typography.bodySmall
                )
            } else {
                LazyColumn {
                    items(bluetoothDevices) { device ->
                        DeviceItem(
                            name = device.name ?: "Unknown Bluetooth Device",
                            address = device.address,
                            isConnected = (connectionState as? ConnectionState.Connected)?.deviceName == (device.name ?: device.address),
                            onConnectClick = {
                                scope.launch {
                                    printerManager?.connectBluetooth(device)
                                }
                            }
                        )
                        HorizontalDivider()
                    }
                }
            }
        } else {
            if (usbDevices.isEmpty()) {
                Text(
                    text = "Tidak ada device USB terdeteksi. Hubungkan Xprinter menggunakan kabel USB OTG.",
                    modifier = Modifier.align(Alignment.Center),
                    textAlign = TextAlign.Center,
                    color = Color.Gray,
                    style = MaterialTheme.typography.bodySmall
                )
            } else {
                LazyColumn {
                    items(usbDevices) { device ->
                        val hasPermission = printerManager?.hasUsbPermission(device) == true
                        DeviceItem(
                            name = device.productName ?: device.deviceName,
                            address = "VID:${device.vendorId} PID:${device.productId}",
                            isConnected = (connectionState as? ConnectionState.Connected)?.deviceName == (device.productName ?: device.deviceName),
                            actionText = if (hasPermission) "Hubungkan" else "Minta Izin",
                            onConnectClick = {
                                if (hasPermission) {
                                    scope.launch {
                                        printerManager?.connectUsb(device)
                                    }
                                } else {
                                    val permissionIntent = Intent(ACTION_USB_PERMISSION).apply {
                                        setPackage(context.packageName)
                                    }
                                    val pendingIntent = PendingIntent.getBroadcast(
                                        context,
                                        0,
                                        permissionIntent,
                                        PendingIntent.FLAG_MUTABLE
                                    )
                                    printerManager?.requestUsbPermission(device, pendingIntent)
                                }
                            }
                        )
                        HorizontalDivider()
                    }
                }
            }
        }
    }
}

@Composable
fun PrintActionSection(
    connectionState: ConnectionState,
    isPrinting: Boolean,
    printerManager: PrinterManager?,
    photoboothBitmap: Bitmap,
    context: Context,
    scope: kotlinx.coroutines.CoroutineScope,
    onPrintStateChange: (Boolean) -> Unit
) {
    val isConnected = connectionState is ConnectionState.Connected

    Column(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = "Pengujian Cetak TSPL:",
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Bold
        )

        Spacer(modifier = Modifier.height(8.dp))

        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(
                onClick = {
                    scope.launch {
                        onPrintStateChange(true)
                        val tsplData = TsplCommand()
                            .size(100, 80)
                            .gap(2, 0)
                            .direction(1)
                            .cls()
                            .text(40, 40, "3", 0, 1, 1, "=================================")
                            .text(40, 80, "3", 0, 2, 2, "XPRINTER TEST OK!")
                            .text(40, 150, "3", 0, 1, 1, "Model: XP-410B / XP-4601B")
                            .text(40, 185, "3", 0, 1, 1, "Lebar Kertas: 110mm / 4 Inci")
                            .text(40, 220, "3", 0, 1, 1, "=================================")
                            .qrCode(40, 270, "https://xprinter.test.app", cellWidth = 6)
                            .barcode(450, 270, "PHOTOBOOTH123", height = 90)
                            .text(40, 450, "3", 0, 1, 1, "Command TSPL Berhasil Diterima.")
                            .print(1, 1)
                            .build()

                        val success = printerManager?.sendData(tsplData) == true
                        onPrintStateChange(false)
                        if (success) {
                            Toast.makeText(context, "Cetak Teks Test Berhasil Dikirim!", Toast.LENGTH_SHORT).show()
                        }
                    }
                },
                enabled = isConnected && !isPrinting,
                modifier = Modifier.weight(1f)
            ) {
                Text("Cetak Teks & QR", fontSize = 12.sp)
            }

            Button(
                onClick = {
                    scope.launch {
                        onPrintStateChange(true)
                        Toast.makeText(context, "Membuat & Memproses Foto Photobooth Dithering...", Toast.LENGTH_SHORT).show()

                        val heightMm = (photoboothBitmap.height / 8) + 10

                        val tsplData = TsplCommand()
                            .size(100, heightMm)
                            .gap(2, 0)
                            .direction(1)
                            .cls()
                            .bitmap(0, 0, photoboothBitmap)
                            .print(1, 1)
                            .build()

                        val success = printerManager?.sendData(tsplData) == true
                        onPrintStateChange(false)
                        if (success) {
                            Toast.makeText(context, "Cetak Photobooth Strip Berhasil Dikirim!", Toast.LENGTH_SHORT).show()
                        }
                    }
                },
                enabled = isConnected && !isPrinting,
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.tertiary),
                modifier = Modifier.weight(1f)
            ) {
                Text("Cetak Strip Foto", fontSize = 12.sp)
            }
        }
    }
}

@Composable
fun PhotoboothPreviewCard(
    bitmap: Bitmap,
    imageWidthFraction: Float = 0.75f
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Live Preview Strip Photobooth (203 DPI)",
                    fontWeight = FontWeight.Bold,
                    style = MaterialTheme.typography.bodyMedium
                )
                Text(
                    text = "Dithering 1-Bit",
                    style = MaterialTheme.typography.bodySmall,
                    color = Color(0xFF2E7D32),
                    fontWeight = FontWeight.SemiBold
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, Color.LightGray, RoundedCornerShape(6.dp))
                    .padding(8.dp),
                contentAlignment = Alignment.Center
            ) {
                Image(
                    bitmap = bitmap.asImageBitmap(),
                    contentDescription = "Preview Strip Photobooth",
                    modifier = Modifier.fillMaxWidth(imageWidthFraction)
                )
            }
        }
    }
}

@Composable
fun DeviceItem(
    name: String,
    address: String,
    isConnected: Boolean,
    actionText: String = "Hubungkan",
    onConnectClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp, horizontal = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(text = name, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodyMedium)
            Text(text = address, style = MaterialTheme.typography.bodySmall, color = Color.Gray)
        }

        if (isConnected) {
            Text(
                text = "Terhubung",
                color = Color(0xFF2E7D32),
                fontWeight = FontWeight.Bold,
                style = MaterialTheme.typography.bodySmall
            )
        } else {
            Button(onClick = onConnectClick) {
                Text(actionText, fontSize = 12.sp)
            }
        }
    }
}

// ==========================================
// COMPOSE PREVIEWS FOR PHONE & TABLET
// ==========================================

@Preview(name = "Phone Portrait", showBackground = true, widthDp = 380, heightDp = 800)
@Composable
fun PrinterTestAppScreenPhonePreview() {
    TestAppPrinterTheme {
        Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
            PrinterTestAppScreen(
                printerManager = null,
                modifier = Modifier.padding(innerPadding)
            )
        }
    }
}

@Preview(name = "Tablet Landscape", showBackground = true, widthDp = 1000, heightDp = 700)
@Composable
fun PrinterTestAppScreenTabletLandscapePreview() {
    TestAppPrinterTheme {
        Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
            PrinterTestAppScreen(
                printerManager = null,
                modifier = Modifier.padding(innerPadding)
            )
        }
    }
}

@Preview(name = "Tablet Portrait", showBackground = true, widthDp = 800, heightDp = 1200)
@Composable
fun PrinterTestAppScreenTabletPortraitPreview() {
    TestAppPrinterTheme {
        Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
            PrinterTestAppScreen(
                printerManager = null,
                modifier = Modifier.padding(innerPadding)
            )
        }
    }
}
