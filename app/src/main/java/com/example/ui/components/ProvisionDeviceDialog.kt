package com.example.ui.components

import android.app.Activity
import android.nfc.NfcAdapter
import android.os.Handler
import android.os.Looper
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.Nfc
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material.icons.filled.WarningAmber
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
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
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
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
import com.example.model.WifiScanState
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

