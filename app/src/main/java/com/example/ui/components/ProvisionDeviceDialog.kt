package com.example.ui.components

import android.app.Activity
import android.nfc.NfcAdapter
import android.os.Handler
import android.os.Looper
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material.icons.filled.Nfc
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material.icons.filled.WarningAmber
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material.icons.filled.WifiFind
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.example.model.FM11NT083C_USER_EEPROM_BYTES
import com.example.model.IST_VENDOR_NAME
import com.example.model.NdefPayloadFormat
import com.example.model.NfcAvailability
import com.example.model.PayloadBuilder
import com.example.model.ProvisionDialogState
import com.example.model.ProvisionStep
import com.example.model.ScannedWifiNetwork
import com.example.model.WifiScanState
import com.example.model.WifiScanStatus
import com.example.nfc.NfcClearResult
import com.example.nfc.NfcHelper
import com.example.nfc.NfcWriteResult
import com.example.ui.theme.AlertRed
import com.example.ui.theme.AlertRedBg
import com.example.ui.theme.AlertRedBorder
import com.example.ui.theme.CodeSurface
import com.example.ui.theme.InkBlack
import com.example.ui.theme.MonoTextStyle
import com.example.ui.theme.MutedInk
import com.example.ui.theme.PendingAmber
import com.example.ui.theme.PendingAmberBg
import com.example.ui.theme.PendingAmberBorder
import com.example.ui.theme.ProvisionedGreen
import com.example.ui.theme.ProvisionedGreenBg
import com.example.ui.theme.ProvisionedGreenBorder
import com.example.ui.theme.PureWhiteSurface
import com.example.ui.theme.SoftSurfaceVariant
import com.example.ui.theme.SubtleBorder
import com.example.ui.theme.TechBlue
import com.example.ui.theme.TechBlueBorder
import com.example.ui.theme.TechBlueContainer
import com.example.wifi.WifiScannerHelper

@Composable
fun ProvisionDeviceDialog(
    state: ProvisionDialogState,
    nfcAvailability: NfcAvailability,
    onSsidChange: (String) -> Unit,
    onPasswordChange: (String) -> Unit,
    onFormatChange: (NdefPayloadFormat) -> Unit,
    onStartWriteTap: () -> Unit,
    onStartClearTap: () -> Unit,
    onBackToForm: () -> Unit,
    onWriteResult: (NfcWriteResult) -> Unit,
    onClearResult: (NfcClearResult) -> Unit,
    onOpenNfcSettings: () -> Unit,
    onDismiss: () -> Unit
) {
    val device = state.device ?: return
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val activity = context as? Activity
    val mainHandler = remember { Handler(Looper.getMainLooper()) }
    var passwordVisible by remember(device.id) { mutableStateOf(false) }

    var wifiScanState by remember(device.id) { mutableStateOf(WifiScanState()) }
    var wifiScanTrigger by remember(device.id) { mutableIntStateOf(0) }
    var showAllWifiPicker by remember(device.id) { mutableStateOf(false) }

    val wifiPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { _ ->
        wifiScanTrigger++
    }

    // Re-trigger Wi-Fi scan check when returning from system Wi-Fi/Location settings
    DisposableEffect(lifecycleOwner, state.isOpen, state.step) {
        if (state.isOpen && state.step == ProvisionStep.FORM) {
            val observer = LifecycleEventObserver { _, event ->
                if (event == Lifecycle.Event.ON_RESUME) {
                    wifiScanTrigger++
                }
            }
            lifecycleOwner.lifecycle.addObserver(observer)
            onDispose {
                lifecycleOwner.lifecycle.removeObserver(observer)
            }
        } else {
            onDispose { }
        }
    }

    // Automatically scan nearby Wi-Fi networks when opening the provisioning form
    DisposableEffect(state.isOpen, state.step, device.id, wifiScanTrigger) {
        if (state.isOpen && state.step == ProvisionStep.FORM) {
            val cleanup = WifiScannerHelper.startAutoScan(context) { updated ->
                mainHandler.post {
                    wifiScanState = updated
                }
            }
            onDispose {
                cleanup()
            }
        } else {
            onDispose { }
        }
    }

    val previewTimestampMillis = remember(device.id, state.step, state.format, state.ssid, state.password) {
        System.currentTimeMillis()
    }
    val previewPayload = remember(state.format, state.ssid, state.password, device.uid, previewTimestampMillis) {
        PayloadBuilder.buildPayload(
            format = state.format,
            ssid = state.ssid.trim(),
            password = state.password,
            uid = device.uid,
            timestampMillis = previewTimestampMillis
        )
    }
    val footprint = remember(previewPayload) {
        PayloadBuilder.calculateFootprint(previewPayload)
    }

    // Activate NFC ReaderMode when waiting to write NDEF payload or clear tag data
    val isWaitingTap =
        state.step == ProvisionStep.WAITING_TAP || state.step == ProvisionStep.WAITING_CLEAR_TAP
    DisposableEffect(state.isOpen, state.step, nfcAvailability, state.format, state.ssid, state.password, device.uid) {
        if (
            activity != null &&
            state.isOpen &&
            isWaitingTap &&
            nfcAvailability == NfcAvailability.ENABLED
        ) {
            val isClearTap = state.step == ProvisionStep.WAITING_CLEAR_TAP
            val callback = NfcAdapter.ReaderCallback { tag ->
                if (isClearTap) {
                    val result = NfcHelper.clearTagData(
                        tag = tag,
                        expectedUid = device.uid
                    )
                    mainHandler.post {
                        onClearResult(result)
                    }
                } else {
                    val writePayload = PayloadBuilder.buildPayload(
                        format = state.format,
                        ssid = state.ssid.trim(),
                        password = state.password,
                        uid = device.uid,
                        timestampMillis = System.currentTimeMillis()
                    )
                    val result = NfcHelper.writeNdefTextToTag(
                        tag = tag,
                        expectedUid = device.uid,
                        payload = writePayload
                    )
                    mainHandler.post {
                        onWriteResult(result)
                    }
                }
            }
            val started = NfcHelper.enableReaderMode(activity, callback)
            if (!started) {
                mainHandler.post {
                    if (isClearTap) {
                        onClearResult(
                            NfcClearResult.Failure(
                                scannedUid = null,
                                message = "无法启动 NFC 清空监听，请检查系统 NFC 状态"
                            )
                        )
                    } else {
                        onWriteResult(
                            NfcWriteResult.Failure(
                                scannedUid = null,
                                message = "无法启动 NFC 写入监听，请检查系统 NFC 状态"
                            )
                        )
                    }
                }
            }
            onDispose {
                NfcHelper.disableReaderMode(activity)
            }
        } else {
            onDispose { }
        }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxWidth()
                .imePadding()
                .padding(horizontal = 16.dp, vertical = 20.dp),
            contentAlignment = Alignment.Center
        ) {
            val dialogMaxHeight = maxHeight * 0.92f
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = dialogMaxHeight),
                shape = RoundedCornerShape(16.dp),
                color = PureWhiteSurface,
                border = BorderStroke(1.dp, SubtleBorder),
                tonalElevation = 0.dp
            ) {
                Column(modifier = Modifier.fillMaxWidth()) {
                    // Pinned Header
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(start = 20.dp, end = 12.dp, top = 16.dp, bottom = 12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.Top
                    ) {
                        Column(
                            modifier = Modifier.weight(1f),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Text(
                                text = device.name,
                                style = MaterialTheme.typography.titleMedium,
                                color = InkBlack
                            )
                            Surface(
                                color = SoftSurfaceVariant,
                                shape = RoundedCornerShape(6.dp),
                                border = BorderStroke(1.dp, SubtleBorder)
                            ) {
                                Text(
                                    text = "UID: ${device.uid}",
                                    style = MonoTextStyle.copy(color = MutedInk),
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                )
                            }
                        }
                        IconButton(
                            onClick = onDismiss,
                            modifier = Modifier.testTag("provision_dialog_close_button")
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Close,
                                contentDescription = "关闭配网弹窗",
                                tint = MutedInk
                            )
                        }
                    }

                    HorizontalDivider(color = SubtleBorder, thickness = 1.dp)

                    // Scrollable Middle Content
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f, fill = false)
                            .verticalScroll(rememberScrollState())
                            .padding(horizontal = 20.dp, vertical = 14.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        when (state.step) {
                            ProvisionStep.FORM -> {
                                OutlinedTextField(
                                    value = state.ssid,
                                    onValueChange = onSsidChange,
                                    label = { Text("Wi-Fi 名称 (SSID)") },
                                    placeholder = { Text("输入或点选下方自动扫描的热点") },
                                    leadingIcon = {
                                        Icon(
                                            imageVector = Icons.Filled.Wifi,
                                            contentDescription = null,
                                            tint = TechBlue
                                        )
                                    },
                                    isError = state.ssidError != null,
                                    supportingText = state.ssidError?.let { err -> { Text(err) } },
                                    singleLine = true,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .testTag("provision_ssid_input")
                                )

                                WifiAutoScanSection(
                                    scanState = wifiScanState,
                                    selectedSsid = state.ssid.trim(),
                                    onSelectNetwork = { network ->
                                        onSsidChange(network.ssid)
                                    },
                                    onShowAllClick = { showAllWifiPicker = true },
                                    onRescanClick = { wifiScanTrigger++ },
                                    onRequestPermission = {
                                        wifiPermissionLauncher.launch(WifiScannerHelper.REQUIRED_PERMISSIONS)
                                    },
                                    onOpenWifiSettings = { WifiScannerHelper.openWifiSettings(context) },
                                    onOpenLocationSettings = { WifiScannerHelper.openLocationSettings(context) }
                                )

                                OutlinedTextField(
                                    value = state.password,
                                    onValueChange = onPasswordChange,
                                    label = { Text("Wi-Fi 密码（开放网络可留空）") },
                                    placeholder = { Text("至少 8 位字符，无密码请留空") },
                                    visualTransformation = if (passwordVisible) {
                                        VisualTransformation.None
                                    } else {
                                        PasswordVisualTransformation()
                                    },
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                                    trailingIcon = {
                                        IconButton(
                                            onClick = { passwordVisible = !passwordVisible },
                                            modifier = Modifier.testTag("provision_password_toggle")
                                        ) {
                                            Icon(
                                                imageVector = if (passwordVisible) {
                                                    Icons.Filled.VisibilityOff
                                                } else {
                                                    Icons.Filled.Visibility
                                                },
                                                contentDescription = if (passwordVisible) "隐藏密码" else "显示密码"
                                            )
                                        }
                                    },
                                    isError = state.passwordError != null,
                                    supportingText = state.passwordError?.let { err -> { Text(err) } },
                                    singleLine = true,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .testTag("provision_password_input")
                                )

                                // NDEF format switcher
                                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Text(
                                        text = "NDEF 明文写入格式",
                                        style = MaterialTheme.typography.labelLarge,
                                        color = InkBlack
                                    )
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        NdefPayloadFormat.entries.forEach { format ->
                                            val selected = state.format == format
                                            Surface(
                                                onClick = { onFormatChange(format) },
                                                shape = RoundedCornerShape(8.dp),
                                                color = if (selected) TechBlueContainer else SoftSurfaceVariant,
                                                border = BorderStroke(
                                                    1.dp,
                                                    if (selected) TechBlue else SubtleBorder
                                                ),
                                                modifier = Modifier
                                                    .weight(1f)
                                                    .height(42.dp)
                                                    .testTag("format_option_${format.id}")
                                            ) {
                                                Box(contentAlignment = Alignment.Center) {
                                                    Text(
                                                        text = format.label,
                                                        style = MaterialTheme.typography.labelMedium.copy(
                                                            fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium
                                                        ),
                                                        color = if (selected) TechBlue else InkBlack
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }

                                // Real-time payload preview + FM11NT083C 888B capacity bar
                                Surface(
                                    color = CodeSurface,
                                    shape = RoundedCornerShape(10.dp),
                                    border = BorderStroke(1.dp, SubtleBorder)
                                ) {
                                    Column(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(12.dp),
                                        verticalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Text(
                                                text = "实时报文预览 (含厂商 $IST_VENDOR_NAME 与系统时间)",
                                                style = MaterialTheme.typography.labelMedium,
                                                color = MutedInk
                                            )
                                            Text(
                                                text = "${footprint.eepromUsedBytes} / $FM11NT083C_USER_EEPROM_BYTES Bytes",
                                                style = MonoTextStyle.copy(
                                                    color = if (footprint.isWithinCapacity) TechBlue else AlertRed,
                                                    fontWeight = FontWeight.Bold
                                                ),
                                                modifier = Modifier.testTag("provision_eeprom_usage_text")
                                            )
                                        }

                                        Text(
                                            text = previewPayload,
                                            style = MonoTextStyle.copy(color = InkBlack),
                                            modifier = Modifier.testTag("provision_payload_preview")
                                        )

                                        LinearProgressIndicator(
                                            progress = { footprint.usageFraction },
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .height(6.dp)
                                                .clip(RoundedCornerShape(3.dp)),
                                            color = if (footprint.isWithinCapacity) TechBlue else AlertRed,
                                            trackColor = SubtleBorder
                                        )

                                        Text(
                                            text = "纯文本负载 ${footprint.payloadUtf8Bytes} B · NDEF 报文 ${footprint.ndefMessageBytes} B · FM11NT083C 用户区共 888 B",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MutedInk
                                        )
                                    }
                                }

                                OutlinedButton(
                                    onClick = onStartClearTap,
                                    border = BorderStroke(1.dp, AlertRedBorder),
                                    colors = ButtonDefaults.outlinedButtonColors(
                                        containerColor = AlertRedBg,
                                        contentColor = AlertRed
                                    ),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(44.dp)
                                        .testTag("provision_start_clear_button")
                                ) {
                                    Icon(
                                        imageVector = Icons.Filled.DeleteSweep,
                                        contentDescription = null,
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Spacer(Modifier.width(6.dp))
                                    Text("清空芯片已写入数据")
                                }

                                if (state.writeError != null) {
                                    ErrorBanner(message = state.writeError)
                                }
                            }

                            ProvisionStep.WAITING_TAP -> {
                                if (nfcAvailability != NfcAvailability.ENABLED) {
                                    Surface(
                                        color = PendingAmberBg,
                                        shape = RoundedCornerShape(12.dp),
                                        border = BorderStroke(1.dp, PendingAmberBorder)
                                    ) {
                                        Column(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(14.dp),
                                            verticalArrangement = Arrangement.spacedBy(8.dp)
                                        ) {
                                            Row(
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                                            ) {
                                                Icon(
                                                    imageVector = Icons.Filled.WarningAmber,
                                                    contentDescription = null,
                                                    tint = PendingAmber
                                                )
                                                Text(
                                                    text = if (nfcAvailability == NfcAvailability.NOT_SUPPORTED) {
                                                        "手机不支持 NFC，无法写入"
                                                    } else {
                                                        "NFC 未开启，请先开启 NFC"
                                                    },
                                                    style = MaterialTheme.typography.titleSmall,
                                                    color = PendingAmber
                                                )
                                            }
                                            if (nfcAvailability == NfcAvailability.DISABLED) {
                                                OutlinedButton(onClick = onOpenNfcSettings) {
                                                    Text("前往系统 NFC 设置")
                                                }
                                            }
                                        }
                                    }
                                } else {
                                    WaitingWritePulseCard()
                                }

                                Surface(
                                    color = CodeSurface,
                                    shape = RoundedCornerShape(10.dp),
                                    border = BorderStroke(1.dp, SubtleBorder)
                                ) {
                                    Column(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(12.dp),
                                        verticalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        DetailRow("厂商信息", "$IST_VENDOR_NAME (富创通)")
                                        DetailRow("系统时间 (ts)", "刷卡写入时自动获取")
                                        DetailRow("目标设备 UID", device.uid, mono = true)
                                        DetailRow("待写入 Wi-Fi", state.ssid.trim())
                                        DetailRow("报文格式", "${state.format.label} (${footprint.ndefMessageBytes} B)")
                                    }
                                }

                                if (state.writeError != null) {
                                    ErrorBanner(message = state.writeError)
                                }
                            }

                            ProvisionStep.SUCCESS -> {
                                Surface(
                                    color = ProvisionedGreenBg,
                                    shape = RoundedCornerShape(12.dp),
                                    border = BorderStroke(1.dp, ProvisionedGreenBorder)
                                ) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(14.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Filled.CheckCircle,
                                            contentDescription = null,
                                            tint = ProvisionedGreen,
                                            modifier = Modifier.size(28.dp)
                                        )
                                        Column {
                                            Text(
                                                text = "配网成功",
                                                style = MaterialTheme.typography.titleMedium,
                                                color = ProvisionedGreen
                                            )
                                            Text(
                                                text = "已写入明文 NDEF Text 记录到 FM11NT083C",
                                                style = MaterialTheme.typography.bodySmall,
                                                color = InkBlack
                                            )
                                        }
                                    }
                                }

                                Surface(
                                    color = CodeSurface,
                                    shape = RoundedCornerShape(10.dp),
                                    border = BorderStroke(1.dp, SubtleBorder)
                                ) {
                                    Column(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(12.dp),
                                        verticalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        DetailRow("厂商信息", "$IST_VENDOR_NAME (富创通)")
                                        DetailRow("设备 UID", device.uid, mono = true)
                                        DetailRow("写入 Wi-Fi", state.ssid.trim())
                                        DetailRow("写入字节数", "${state.writtenByteCount} Bytes (NDEF)")
                                        Text(
                                            text = "完整写入内容",
                                            style = MaterialTheme.typography.labelMedium,
                                            color = MutedInk
                                        )
                                        Box(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .clip(RoundedCornerShape(8.dp))
                                                .background(PureWhiteSurface)
                                                .border(1.dp, SubtleBorder, RoundedCornerShape(8.dp))
                                                .padding(10.dp)
                                        ) {
                                            Text(
                                                text = state.writtenPayload,
                                                style = MonoTextStyle.copy(color = InkBlack),
                                                modifier = Modifier.testTag("provision_success_payload")
                                            )
                                        }
                                    }
                                }
                            }

                            ProvisionStep.WAITING_CLEAR_TAP -> {
                                if (nfcAvailability != NfcAvailability.ENABLED) {
                                    Surface(
                                        color = PendingAmberBg,
                                        shape = RoundedCornerShape(12.dp),
                                        border = BorderStroke(1.dp, PendingAmberBorder)
                                    ) {
                                        Column(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(14.dp),
                                            verticalArrangement = Arrangement.spacedBy(8.dp)
                                        ) {
                                            Row(
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                                            ) {
                                                Icon(
                                                    imageVector = Icons.Filled.WarningAmber,
                                                    contentDescription = null,
                                                    tint = PendingAmber
                                                )
                                                Text(
                                                    text = if (nfcAvailability == NfcAvailability.NOT_SUPPORTED) {
                                                        "手机不支持 NFC，无法清空"
                                                    } else {
                                                        "NFC 未开启，请先开启 NFC"
                                                    },
                                                    style = MaterialTheme.typography.titleSmall,
                                                    color = PendingAmber
                                                )
                                            }
                                            if (nfcAvailability == NfcAvailability.DISABLED) {
                                                OutlinedButton(onClick = onOpenNfcSettings) {
                                                    Text("前往系统 NFC 设置")
                                                }
                                            }
                                        }
                                    }
                                } else {
                                    WaitingWritePulseCard(
                                        title = "请刷取 NFC 清空芯片数据",
                                        subtitle = "将手机贴紧目标设备感应区保持 1~2 秒，将擦除已写入的 Wi-Fi/NDEF 信息",
                                        accentColor = AlertRed,
                                        containerColor = AlertRedBg,
                                        borderColor = AlertRedBorder
                                    )
                                }

                                Surface(
                                    color = CodeSurface,
                                    shape = RoundedCornerShape(10.dp),
                                    border = BorderStroke(1.dp, SubtleBorder)
                                ) {
                                    Column(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(12.dp),
                                        verticalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        DetailRow("目标设备 UID", device.uid, mono = true)
                                        DetailRow("执行操作", "擦除用户区写入数据并重置为空 NDEF")
                                    }
                                }

                                if (state.writeError != null) {
                                    ErrorBanner(message = state.writeError)
                                }
                            }

                            ProvisionStep.CLEAR_SUCCESS -> {
                                Surface(
                                    color = ProvisionedGreenBg,
                                    shape = RoundedCornerShape(12.dp),
                                    border = BorderStroke(1.dp, ProvisionedGreenBorder)
                                ) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(14.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Filled.CheckCircle,
                                            contentDescription = null,
                                            tint = ProvisionedGreen,
                                            modifier = Modifier.size(28.dp)
                                        )
                                        Column {
                                            Text(
                                                text = "已清空芯片数据",
                                                style = MaterialTheme.typography.titleMedium,
                                                color = ProvisionedGreen
                                            )
                                            Text(
                                                text = "设备已重置为「待配网」状态，可重新写入 Wi-Fi",
                                                style = MaterialTheme.typography.bodySmall,
                                                color = InkBlack
                                            )
                                        }
                                    }
                                }

                                Surface(
                                    color = CodeSurface,
                                    shape = RoundedCornerShape(10.dp),
                                    border = BorderStroke(1.dp, SubtleBorder)
                                ) {
                                    Column(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(12.dp),
                                        verticalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        DetailRow("设备 UID", device.uid, mono = true)
                                        if (state.clearedPageCount > 0) {
                                            DetailRow("擦除页数", "${state.clearedPageCount} 页 (EEPROM 用户区)")
                                        }
                                        DetailRow("执行结果", state.clearMethodSummary.ifBlank { "已清空" })
                                    }
                                }
                            }
                        }
                    }

                    HorizontalDivider(color = SubtleBorder, thickness = 1.dp)

                    // Pinned Footer Actions (always visible regardless of scroll)
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 20.dp, vertical = 14.dp)
                    ) {
                        when (state.step) {
                            ProvisionStep.FORM -> {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    OutlinedButton(
                                        onClick = onDismiss,
                                        modifier = Modifier
                                            .weight(1f)
                                            .height(48.dp)
                                            .testTag("provision_cancel_button"),
                                        border = BorderStroke(1.dp, SubtleBorder)
                                    ) {
                                        Text("取消", color = InkBlack)
                                    }
                                    Button(
                                        onClick = onStartWriteTap,
                                        modifier = Modifier
                                            .weight(1.4f)
                                            .height(48.dp)
                                            .testTag("provision_start_write_button"),
                                        colors = ButtonDefaults.buttonColors(
                                            containerColor = TechBlue,
                                            contentColor = PureWhiteSurface
                                        )
                                    ) {
                                        Icon(
                                            imageVector = Icons.Filled.Nfc,
                                            contentDescription = null,
                                            modifier = Modifier.size(18.dp)
                                        )
                                        Spacer(Modifier.width(6.dp))
                                        Text("NFC 配网")
                                    }
                                }
                            }

                            ProvisionStep.WAITING_TAP, ProvisionStep.WAITING_CLEAR_TAP -> {
                                OutlinedButton(
                                    onClick = onBackToForm,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(48.dp)
                                        .testTag("provision_back_to_form_button"),
                                    border = BorderStroke(1.dp, SubtleBorder)
                                ) {
                                    Icon(
                                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                        contentDescription = null,
                                        modifier = Modifier.size(18.dp),
                                        tint = InkBlack
                                    )
                                    Spacer(Modifier.width(6.dp))
                                    Text("返回", color = InkBlack)
                                }
                            }

                            ProvisionStep.SUCCESS, ProvisionStep.CLEAR_SUCCESS -> {
                                Button(
                                    onClick = onDismiss,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(48.dp)
                                        .testTag("provision_done_button"),
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = TechBlue,
                                        contentColor = PureWhiteSurface
                                    )
                                ) {
                                    Text("完成")
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (showAllWifiPicker && state.step == ProvisionStep.FORM) {
        WifiPickerDialog(
            scanState = wifiScanState,
            selectedSsid = state.ssid.trim(),
            onSelectNetwork = { network ->
                onSsidChange(network.ssid)
                showAllWifiPicker = false
            },
            onRescanClick = { wifiScanTrigger++ },
            onDismiss = { showAllWifiPicker = false }
        )
    }
}

@Composable
private fun WaitingWritePulseCard(
    title: String = "请再次刷取 NFC 写入配网信息",
    subtitle: String = "将手机贴紧目标设备感应区并保持 1~2 秒，直到提示配网成功",
    accentColor: androidx.compose.ui.graphics.Color = TechBlue,
    containerColor: androidx.compose.ui.graphics.Color = TechBlueContainer,
    borderColor: androidx.compose.ui.graphics.Color = TechBlueBorder
) {
    val infiniteTransition = rememberInfiniteTransition(label = "nfc_write_pulse")
    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 0.92f,
        targetValue = 1.15f,
        animationSpec = infiniteRepeatable(
            animation = tween(850),
            repeatMode = RepeatMode.Reverse
        ),
        label = "write_pulse_scale"
    )
    val pulseAlpha by infiniteTransition.animateFloat(
        initialValue = 0.95f,
        targetValue = 0.45f,
        animationSpec = infiniteRepeatable(
            animation = tween(850),
            repeatMode = RepeatMode.Reverse
        ),
        label = "write_pulse_alpha"
    )

    Surface(
        color = containerColor,
        shape = RoundedCornerShape(12.dp),
        border = BorderStroke(1.dp, borderColor)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 20.dp, horizontal = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier.size(64.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(62.dp)
                        .scale(pulseScale)
                        .alpha(pulseAlpha)
                        .clip(CircleShape)
                        .background(accentColor.copy(alpha = 0.16f))
                )
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .size(46.dp)
                        .clip(CircleShape)
                        .background(accentColor)
                ) {
                    Icon(
                        imageVector = Icons.Filled.Nfc,
                        contentDescription = title,
                        tint = PureWhiteSurface,
                        modifier = Modifier.size(24.dp)
                    )
                }
            }
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall,
                color = accentColor
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MutedInk
            )
        }
    }
}

@Composable
private fun ErrorBanner(message: String) {
    Surface(
        color = AlertRedBg,
        shape = RoundedCornerShape(10.dp),
        border = BorderStroke(1.dp, AlertRedBorder)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Icon(
                imageVector = Icons.Filled.ErrorOutline,
                contentDescription = null,
                tint = AlertRed,
                modifier = Modifier.size(20.dp)
            )
            Text(
                text = message,
                style = MaterialTheme.typography.bodySmall,
                color = AlertRed
            )
        }
    }
}

@Composable
private fun DetailRow(label: String, value: String, mono: Boolean = false) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = MutedInk
        )
        Text(
            text = value,
            style = if (mono) MonoTextStyle.copy(color = InkBlack) else MaterialTheme.typography.bodySmall.copy(
                fontWeight = FontWeight.SemiBold,
                color = InkBlack
            )
        )
    }
}

@Composable
private fun WifiAutoScanSection(
    scanState: WifiScanState,
    selectedSsid: String,
    onSelectNetwork: (ScannedWifiNetwork) -> Unit,
    onShowAllClick: () -> Unit,
    onRescanClick: () -> Unit,
    onRequestPermission: () -> Unit,
    onOpenWifiSettings: () -> Unit,
    onOpenLocationSettings: () -> Unit
) {
    val topNetworks = remember(scanState.networks) {
        scanState.networks.take(5)
    }

    Surface(
        color = SoftSurfaceVariant,
        shape = RoundedCornerShape(10.dp),
        border = BorderStroke(1.dp, SubtleBorder),
        modifier = Modifier
            .fillMaxWidth()
            .testTag("wifi_auto_scan_section")
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // Compact Header Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(
                        imageVector = Icons.Filled.WifiFind,
                        contentDescription = null,
                        tint = TechBlue,
                        modifier = Modifier.size(16.dp)
                    )
                    Text(
                        text = when (scanState.status) {
                            WifiScanStatus.SCANNING -> "正在扫描附近 Wi-Fi..."
                            WifiScanStatus.READY -> "附近 Wi-Fi (${scanState.networks.size})"
                            else -> "自动扫描 Wi-Fi"
                        },
                        style = MaterialTheme.typography.labelLarge,
                        color = InkBlack,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    if (scanState.status == WifiScanStatus.SCANNING) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(13.dp),
                            strokeWidth = 2.dp,
                            color = TechBlue
                        )
                    }
                }

                if (scanState.status == WifiScanStatus.SCANNING || scanState.status == WifiScanStatus.READY) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(2.dp)
                    ) {
                        TextButton(
                            onClick = onRescanClick,
                            contentPadding = PaddingValues(horizontal = 6.dp, vertical = 2.dp),
                            modifier = Modifier
                                .height(32.dp)
                                .testTag("wifi_rescan_button")
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Refresh,
                                contentDescription = "重新扫描 Wi-Fi",
                                tint = TechBlue,
                                modifier = Modifier.size(15.dp)
                            )
                            Spacer(Modifier.width(3.dp))
                            Text(
                                text = "刷新",
                                style = MaterialTheme.typography.labelMedium,
                                color = TechBlue
                            )
                        }

                        if (scanState.networks.isNotEmpty()) {
                            Surface(
                                onClick = onShowAllClick,
                                color = PureWhiteSurface,
                                shape = RoundedCornerShape(6.dp),
                                border = BorderStroke(1.dp, TechBlueBorder),
                                modifier = Modifier
                                    .height(32.dp)
                                    .testTag("wifi_show_all_button")
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.padding(horizontal = 8.dp)
                                ) {
                                    Text(
                                        text = "全部(${scanState.networks.size})",
                                        style = MaterialTheme.typography.labelMedium,
                                        color = TechBlue
                                    )
                                    Icon(
                                        imageVector = Icons.Filled.ExpandMore,
                                        contentDescription = "查看全部 Wi-Fi",
                                        tint = TechBlue,
                                        modifier = Modifier.size(15.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }

            when (scanState.status) {
                WifiScanStatus.PERMISSION_REQUIRED -> {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "需定位权限以读取附近 Wi-Fi",
                            style = MaterialTheme.typography.bodySmall,
                            color = MutedInk,
                            modifier = Modifier.weight(1f)
                        )
                        Spacer(Modifier.width(8.dp))
                        OutlinedButton(
                            onClick = onRequestPermission,
                            border = BorderStroke(1.dp, TechBlueBorder),
                            colors = ButtonDefaults.outlinedButtonColors(
                                containerColor = TechBlueContainer,
                                contentColor = TechBlue
                            ),
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                            modifier = Modifier
                                .height(36.dp)
                                .testTag("wifi_grant_permission_button")
                        ) {
                            Text("授权扫描", style = MaterialTheme.typography.labelMedium)
                        }
                    }
                }

                WifiScanStatus.WIFI_DISABLED -> {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "手机 Wi-Fi 未开启",
                            style = MaterialTheme.typography.bodySmall,
                            color = PendingAmber,
                            modifier = Modifier.weight(1f)
                        )
                        OutlinedButton(
                            onClick = onOpenWifiSettings,
                            border = BorderStroke(1.dp, PendingAmberBorder),
                            colors = ButtonDefaults.outlinedButtonColors(
                                containerColor = PendingAmberBg,
                                contentColor = PendingAmber
                            ),
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                            modifier = Modifier
                                .height(36.dp)
                                .testTag("wifi_open_settings_button")
                        ) {
                            Text("开启 Wi-Fi", style = MaterialTheme.typography.labelMedium)
                        }
                    }
                }

                WifiScanStatus.LOCATION_DISABLED -> {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "需开启系统定位开关才能扫描 Wi-Fi",
                            style = MaterialTheme.typography.bodySmall,
                            color = PendingAmber,
                            modifier = Modifier.weight(1f)
                        )
                        OutlinedButton(
                            onClick = onOpenLocationSettings,
                            border = BorderStroke(1.dp, PendingAmberBorder),
                            colors = ButtonDefaults.outlinedButtonColors(
                                containerColor = PendingAmberBg,
                                contentColor = PendingAmber
                            ),
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                            modifier = Modifier
                                .height(36.dp)
                                .testTag("wifi_open_location_button")
                        ) {
                            Text("开启定位", style = MaterialTheme.typography.labelMedium)
                        }
                    }
                }

                WifiScanStatus.SCANNING, WifiScanStatus.READY, WifiScanStatus.IDLE -> {
                    if (topNetworks.isEmpty()) {
                        Text(
                            text = if (scanState.status == WifiScanStatus.SCANNING) {
                                "正在搜索周边无线网络..."
                            } else {
                                "暂未发现附近 Wi-Fi，可点击刷新或直接手动输入"
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = MutedInk
                        )
                    } else {
                        // Single horizontal row of top 5 strongest Wi-Fi networks keeps dialog height fixed
                        LazyRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            items(topNetworks, key = { it.ssid }) { network ->
                                val isSelected = selectedSsid == network.ssid
                                val is24Ghz = network.bandLabel == "2.4 GHz"
                                Surface(
                                    onClick = { onSelectNetwork(network) },
                                    color = if (isSelected) TechBlueContainer else PureWhiteSurface,
                                    shape = RoundedCornerShape(8.dp),
                                    border = BorderStroke(1.dp, if (isSelected) TechBlue else SubtleBorder),
                                    modifier = Modifier
                                        .height(40.dp)
                                        .testTag("wifi_quick_chip_${network.ssid}")
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(5.dp),
                                        modifier = Modifier.padding(horizontal = 10.dp)
                                    ) {
                                        Icon(
                                            imageVector = if (network.isSecured) Icons.Filled.Lock else Icons.Filled.Wifi,
                                            contentDescription = null,
                                            tint = if (isSelected) TechBlue else MutedInk,
                                            modifier = Modifier.size(13.dp)
                                        )
                                        Text(
                                            text = network.ssid,
                                            style = MaterialTheme.typography.labelMedium.copy(
                                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
                                            ),
                                            color = if (isSelected) TechBlue else InkBlack,
                                            maxLines = 1
                                        )
                                        Text(
                                            text = if (is24Ghz) "2.4G" else "5G",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = if (is24Ghz) ProvisionedGreen else MutedInk
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun WifiPickerDialog(
    scanState: WifiScanState,
    selectedSsid: String,
    onSelectNetwork: (ScannedWifiNetwork) -> Unit,
    onRescanClick: () -> Unit,
    onDismiss: () -> Unit
) {
    var query by remember { mutableStateOf("") }
    var bandFilter by remember { mutableStateOf("ALL") } // ALL, 2.4 GHz, 5 GHz

    val filtered = remember(scanState.networks, query, bandFilter) {
        val q = query.trim().lowercase()
        scanState.networks.filter { net ->
            val matchBand = when (bandFilter) {
                "2.4 GHz" -> net.bandLabel == "2.4 GHz"
                "5 GHz" -> net.bandLabel == "5 GHz"
                else -> true
            }
            val matchQuery = q.isEmpty() || net.ssid.lowercase().contains(q)
            matchBand && matchQuery
        }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxWidth()
                .imePadding()
                .padding(horizontal = 18.dp, vertical = 24.dp),
            contentAlignment = Alignment.Center
        ) {
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = maxHeight * 0.82f),
                shape = RoundedCornerShape(16.dp),
                color = PureWhiteSurface,
                border = BorderStroke(1.dp, SubtleBorder),
                tonalElevation = 0.dp
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "选择附近 Wi-Fi (${scanState.networks.size})",
                                style = MaterialTheme.typography.titleMedium,
                                color = InkBlack
                            )
                            if (scanState.isThrottled) {
                                Text(
                                    text = "系统扫描频率受限，当前展示最近缓存结果",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MutedInk
                                )
                            }
                        }
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            IconButton(
                                onClick = onRescanClick,
                                modifier = Modifier.testTag("wifi_picker_rescan_button")
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.Refresh,
                                    contentDescription = "刷新 Wi-Fi 列表",
                                    tint = TechBlue
                                )
                            }
                            IconButton(
                                onClick = onDismiss,
                                modifier = Modifier.testTag("wifi_picker_close_button")
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.Close,
                                    contentDescription = "关闭选择框",
                                    tint = MutedInk
                                )
                            }
                        }
                    }

                    OutlinedTextField(
                        value = query,
                        onValueChange = { query = it },
                        placeholder = { Text("搜索 Wi-Fi 名称...") },
                        leadingIcon = {
                            Icon(
                                imageVector = Icons.Filled.Search,
                                contentDescription = null,
                                tint = MutedInk
                            )
                        },
                        trailingIcon = {
                            if (query.isNotEmpty()) {
                                IconButton(onClick = { query = "" }) {
                                    Icon(
                                        imageVector = Icons.Filled.Clear,
                                        contentDescription = "清除",
                                        tint = MutedInk
                                    )
                                }
                            }
                        },
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = TechBlue,
                            unfocusedBorderColor = SubtleBorder
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("wifi_picker_search_input")
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        listOf("ALL" to "全部", "2.4 GHz" to "仅 2.4G", "5 GHz" to "仅 5G").forEach { (key, label) ->
                            val selected = bandFilter == key
                            Surface(
                                onClick = { bandFilter = key },
                                shape = RoundedCornerShape(8.dp),
                                color = if (selected) TechBlueContainer else SoftSurfaceVariant,
                                border = BorderStroke(1.dp, if (selected) TechBlue else SubtleBorder),
                                modifier = Modifier.height(34.dp)
                            ) {
                                Box(
                                    contentAlignment = Alignment.Center,
                                    modifier = Modifier.padding(horizontal = 12.dp)
                                ) {
                                    Text(
                                        text = label,
                                        style = MaterialTheme.typography.labelMedium,
                                        color = if (selected) TechBlue else InkBlack
                                    )
                                }
                            }
                        }
                    }

                    if (filtered.isEmpty()) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 28.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "无匹配的 Wi-Fi 热点",
                                style = MaterialTheme.typography.bodySmall,
                                color = MutedInk
                            )
                        }
                    } else {
                        LazyColumn(
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(1f, fill = false),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            items(filtered, key = { it.ssid }) { network ->
                                WifiNetworkRow(
                                    network = network,
                                    isSelected = selectedSsid == network.ssid,
                                    onClick = { onSelectNetwork(network) }
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun WifiNetworkRow(
    network: ScannedWifiNetwork,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    val is24Ghz = network.bandLabel == "2.4 GHz"
    Surface(
        onClick = onClick,
        color = if (isSelected) TechBlueContainer else PureWhiteSurface,
        shape = RoundedCornerShape(8.dp),
        border = BorderStroke(1.dp, if (isSelected) TechBlue else SubtleBorder),
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp)
            .testTag("wifi_item_${network.ssid}")
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 10.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Icon(
                imageVector = Icons.Filled.Wifi,
                contentDescription = null,
                tint = if (isSelected) TechBlue else MutedInk,
                modifier = Modifier.size(18.dp)
            )
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                Text(
                    text = network.ssid,
                    style = MaterialTheme.typography.bodySmall.copy(
                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
                    ),
                    color = if (isSelected) TechBlue else InkBlack,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Surface(
                        color = if (is24Ghz) ProvisionedGreenBg else SoftSurfaceVariant,
                        shape = RoundedCornerShape(4.dp)
                    ) {
                        Text(
                            text = network.bandLabel,
                            style = MaterialTheme.typography.labelSmall,
                            color = if (is24Ghz) ProvisionedGreen else MutedInk,
                            modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.dp)
                        )
                    }
                    Icon(
                        imageVector = if (network.isSecured) Icons.Filled.Lock else Icons.Filled.LockOpen,
                        contentDescription = null,
                        tint = MutedInk,
                        modifier = Modifier.size(12.dp)
                    )
                    Text(
                        text = "${network.securityLabel} · ${network.rssi} dBm",
                        style = MaterialTheme.typography.labelSmall,
                        color = MutedInk
                    )
                }
            }
            if (isSelected) {
                Icon(
                    imageVector = Icons.Filled.CheckCircle,
                    contentDescription = "已选择",
                    tint = TechBlue,
                    modifier = Modifier.size(18.dp)
                )
            }
        }
    }
}
