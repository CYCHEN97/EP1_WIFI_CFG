package com.example.ui.screens

import android.app.Activity
import android.nfc.NfcAdapter
import android.os.Handler
import android.os.Looper
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Devices
import androidx.compose.material.icons.filled.Nfc
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material.icons.filled.WarningAmber
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
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.example.model.BoundDevice
import com.example.model.FM11NT083C_USER_EEPROM_BYTES
import com.example.model.IST_VENDOR_NAME
import com.example.model.NdefPayloadFormat
import com.example.model.NfcAvailability
import com.example.model.PayloadBuilder
import com.example.model.QuickProvisionState
import com.example.model.QuickProvisionStep
import com.example.model.WifiScanState
import com.example.nfc.NfcHelper
import com.example.nfc.NfcWriteResult
import com.example.ui.components.DetailRow
import com.example.ui.components.ErrorBanner
import com.example.ui.components.WaitingWritePulseCard
import com.example.ui.components.WifiAutoScanSection
import com.example.ui.components.WifiPickerDialog
import com.example.ui.theme.AlertRed
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

private val QUICK_NAME_PRESETS = listOf(
    "前台迎宾屏",
    "大厅广告机",
    "会议室门牌",
    "展厅信息屏",
    "电梯口导视屏"
)

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun QuickProvisionScreen(
    state: QuickProvisionState,
    nfcAvailability: NfcAvailability,
    isModalBlockingNfc: Boolean,
    findDuplicateByUid: (String) -> BoundDevice?,
    onNameChange: (String) -> Unit,
    onSsidChange: (String) -> Unit,
    onPasswordChange: (String) -> Unit,
    onFormatChange: (NdefPayloadFormat) -> Unit,
    onOverwriteChange: (Boolean) -> Unit,
    onStartTap: () -> Unit,
    onBackToForm: () -> Unit,
    onDuplicateBlocked: (scannedUid: String, existingDeviceName: String) -> Unit,
    onWriteResult: (NfcWriteResult) -> Unit,
    onContinueNext: () -> Unit,
    onGoToDevices: () -> Unit,
    onOpenNfcSettings: () -> Unit,
    onCopyFeedback: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val activity = context as? Activity
    val mainHandler = remember { Handler(Looper.getMainLooper()) }
    @Suppress("DEPRECATION")
    val clipboardManager = LocalClipboardManager.current

    var passwordVisible by remember { mutableStateOf(false) }
    var wifiScanState by remember { mutableStateOf(WifiScanState()) }
    var wifiScanTrigger by remember { mutableIntStateOf(0) }
    var showAllWifiPicker by remember { mutableStateOf(false) }

    val latestState by rememberUpdatedState(state)
    val latestFindDuplicate by rememberUpdatedState(findDuplicateByUid)

    BackHandler(enabled = state.step == QuickProvisionStep.WAITING_TAP) {
        onBackToForm()
    }

    val wifiPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { _ ->
        wifiScanTrigger++
    }

    // Refresh nearby Wi-Fi list while on FORM step
    DisposableEffect(state.step, wifiScanTrigger, lifecycleOwner, context) {
        if (state.step == QuickProvisionStep.FORM) {
            var currentCleanup: (() -> Unit)? = null
            val triggerScan: () -> Unit = {
                currentCleanup?.invoke()
                currentCleanup = WifiScannerHelper.startAutoScan(context) { updated ->
                    mainHandler.post {
                        wifiScanState = updated
                    }
                }
            }
            val observer = LifecycleEventObserver { _, event ->
                if (event == Lifecycle.Event.ON_RESUME) {
                    triggerScan()
                }
            }
            lifecycleOwner.lifecycle.addObserver(observer)
            triggerScan()
            onDispose {
                lifecycleOwner.lifecycle.removeObserver(observer)
                currentCleanup?.invoke()
            }
        } else {
            onDispose { }
        }
    }

    // Enable NFC ReaderMode during WAITING_TAP when no modal dialog is active
    val shouldListenNfc =
        state.step == QuickProvisionStep.WAITING_TAP &&
            nfcAvailability == NfcAvailability.ENABLED &&
            !isModalBlockingNfc

    DisposableEffect(shouldListenNfc, activity) {
        if (activity != null && shouldListenNfc) {
            val callback = NfcAdapter.ReaderCallback { tag ->
                val actualUid = NfcHelper.formatUid(tag.id)
                if (actualUid.isBlank()) {
                    mainHandler.post {
                        onWriteResult(
                            NfcWriteResult.Failure(
                                scannedUid = null,
                                message = "未能读取到芯片 UID，请将手机重新贴紧设备 NFC 感应区"
                            )
                        )
                    }
                    return@ReaderCallback
                }

                val snapshot = latestState
                if (!snapshot.overwriteExisting) {
                    val existing = latestFindDuplicate(actualUid)
                    if (existing != null) {
                        mainHandler.post {
                            onDuplicateBlocked(actualUid, existing.name)
                        }
                        return@ReaderCallback
                    }
                }

                val livePayload = PayloadBuilder.buildPayload(
                    format = snapshot.format,
                    ssid = snapshot.ssid.trim(),
                    password = snapshot.password,
                    uid = actualUid,
                    timestampMillis = System.currentTimeMillis()
                )
                val result = NfcHelper.writeNdefTextToTag(
                    tag = tag,
                    expectedUid = actualUid,
                    payload = livePayload
                )
                mainHandler.post {
                    onWriteResult(result)
                }
            }
            NfcHelper.enableReaderMode(activity, callback)
            onDispose {
                NfcHelper.disableReaderMode(activity)
            }
        } else {
            onDispose { }
        }
    }

    val previewPayload = remember(state.format, state.ssid, state.password) {
        PayloadBuilder.buildPayload(
            format = state.format,
            ssid = state.ssid.trim().ifEmpty { "IST_Office_5G" },
            password = state.password,
            uid = "04:E2:8A:19:4C:72:01"
        )
    }
    val footprint = remember(previewPayload) {
        PayloadBuilder.calculateFootprint(previewPayload)
    }

    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.TopCenter
    ) {
        LazyColumn(
            modifier = Modifier
                .widthIn(max = 680.dp)
                .fillMaxSize(),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 14.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Top Mode Header Card
            item {
                Surface(
                    color = PureWhiteSurface,
                    shape = RoundedCornerShape(12.dp),
                    border = BorderStroke(1.dp, SubtleBorder),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier
                                .size(42.dp)
                                .background(TechBlueContainer, RoundedCornerShape(10.dp))
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Bolt,
                                contentDescription = null,
                                tint = TechBlue,
                                modifier = Modifier.size(24.dp)
                            )
                        }
                        Column(
                            modifier = Modifier.weight(1f),
                            verticalArrangement = Arrangement.spacedBy(2.dp)
                        ) {
                            Text(
                                text = "一键绑定与 NFC 配网",
                                style = MaterialTheme.typography.titleMedium,
                                color = InkBlack
                            )
                            Text(
                                text = "填写设备名称与 Wi-Fi 后单次刷卡，自动读取芯片 UID 建档并同步写入配网信息",
                                style = MaterialTheme.typography.bodySmall,
                                color = MutedInk
                            )
                        }
                    }
                }
            }

            when (state.step) {
                QuickProvisionStep.FORM -> {
                    // 1. Device Name Card
                    item {
                        Surface(
                            color = PureWhiteSurface,
                            shape = RoundedCornerShape(12.dp),
                            border = BorderStroke(1.dp, SubtleBorder),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(14.dp),
                                verticalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Text(
                                    text = "1. 设备建档信息",
                                    style = MaterialTheme.typography.titleSmall,
                                    color = InkBlack
                                )
                                OutlinedTextField(
                                    value = state.deviceName,
                                    onValueChange = onNameChange,
                                    label = { Text("设备名称 *") },
                                    placeholder = { Text("例如：一楼大厅迎宾屏") },
                                    singleLine = true,
                                    isError = state.nameError != null,
                                    supportingText = state.nameError?.let { err -> { Text(err) } },
                                    trailingIcon = {
                                        if (state.deviceName.isNotEmpty()) {
                                            IconButton(onClick = { onNameChange("") }) {
                                                Icon(
                                                    imageVector = Icons.Filled.Clear,
                                                    contentDescription = "清除设备名称",
                                                    tint = MutedInk
                                                )
                                            }
                                        }
                                    },
                                    colors = OutlinedTextFieldDefaults.colors(
                                        focusedBorderColor = TechBlue,
                                        unfocusedBorderColor = SubtleBorder
                                    ),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .testTag("quick_provision_name_input")
                                )

                                Text(
                                    text = "常用命名快捷填充",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MutedInk
                                )
                                FlowRow(
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    verticalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    QUICK_NAME_PRESETS.forEach { preset ->
                                        val selected = state.deviceName.trim() == preset
                                        Surface(
                                            onClick = { onNameChange(preset) },
                                            color = if (selected) TechBlueContainer else SoftSurfaceVariant,
                                            shape = RoundedCornerShape(8.dp),
                                            border = BorderStroke(1.dp, if (selected) TechBlue else SubtleBorder),
                                            modifier = Modifier
                                                .height(36.dp)
                                                .testTag("quick_name_preset_$preset")
                                        ) {
                                            Box(
                                                contentAlignment = Alignment.Center,
                                                modifier = Modifier.padding(horizontal = 10.dp)
                                            ) {
                                                Text(
                                                    text = preset,
                                                    style = MaterialTheme.typography.labelMedium,
                                                    color = if (selected) TechBlue else InkBlack
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }

                    // 2. Wi-Fi Card
                    item {
                        Surface(
                            color = PureWhiteSurface,
                            shape = RoundedCornerShape(12.dp),
                            border = BorderStroke(1.dp, SubtleBorder),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(14.dp),
                                verticalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Text(
                                    text = "2. Wi-Fi 网络配置",
                                    style = MaterialTheme.typography.titleSmall,
                                    color = InkBlack
                                )

                                WifiAutoScanSection(
                                    scanState = wifiScanState,
                                    selectedSsid = state.ssid.trim(),
                                    onSelectNetwork = { network ->
                                        onSsidChange(network.ssid)
                                        if (!network.isSecured) {
                                            onPasswordChange("")
                                        }
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
                                    value = state.ssid,
                                    onValueChange = onSsidChange,
                                    label = { Text("Wi-Fi 名称 (SSID) *") },
                                    placeholder = { Text("点击上方自动选择或手动输入 SSID") },
                                    singleLine = true,
                                    isError = state.ssidError != null,
                                    supportingText = state.ssidError?.let { err -> { Text(err) } },
                                    trailingIcon = {
                                        if (state.ssid.isNotEmpty()) {
                                            IconButton(onClick = { onSsidChange("") }) {
                                                Icon(
                                                    imageVector = Icons.Filled.Clear,
                                                    contentDescription = "清除 Wi-Fi 名称",
                                                    tint = MutedInk
                                                )
                                            }
                                        }
                                    },
                                    colors = OutlinedTextFieldDefaults.colors(
                                        focusedBorderColor = TechBlue,
                                        unfocusedBorderColor = SubtleBorder
                                    ),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .testTag("quick_provision_ssid_input")
                                )

                                OutlinedTextField(
                                    value = state.password,
                                    onValueChange = onPasswordChange,
                                    label = { Text("Wi-Fi 密码（至少 8 位，开放网络可留空）") },
                                    placeholder = { Text("请输入 Wi-Fi 密码") },
                                    singleLine = true,
                                    isError = state.passwordError != null,
                                    supportingText = state.passwordError?.let { err -> { Text(err) } },
                                    visualTransformation = if (passwordVisible) {
                                        VisualTransformation.None
                                    } else {
                                        PasswordVisualTransformation()
                                    },
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                                    trailingIcon = {
                                        IconButton(onClick = { passwordVisible = !passwordVisible }) {
                                            Icon(
                                                imageVector = if (passwordVisible) Icons.Filled.VisibilityOff else Icons.Filled.Visibility,
                                                contentDescription = if (passwordVisible) "隐藏密码" else "显示密码",
                                                tint = MutedInk
                                            )
                                        }
                                    },
                                    colors = OutlinedTextFieldDefaults.colors(
                                        focusedBorderColor = TechBlue,
                                        unfocusedBorderColor = SubtleBorder
                                    ),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .testTag("quick_provision_password_input")
                                )
                            }
                        }
                    }

                    // 3. Payload Format & Strategy Card
                    item {
                        Surface(
                            color = PureWhiteSurface,
                            shape = RoundedCornerShape(12.dp),
                            border = BorderStroke(1.dp, SubtleBorder),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(14.dp),
                                verticalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Text(
                                    text = "3. 报文格式与防重策略",
                                    style = MaterialTheme.typography.titleSmall,
                                    color = InkBlack
                                )

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    NdefPayloadFormat.entries.forEach { fmt ->
                                        val selected = state.format == fmt
                                        Surface(
                                            onClick = { onFormatChange(fmt) },
                                            color = if (selected) TechBlueContainer else SoftSurfaceVariant,
                                            shape = RoundedCornerShape(8.dp),
                                            border = BorderStroke(1.dp, if (selected) TechBlue else SubtleBorder),
                                            modifier = Modifier
                                                .weight(1f)
                                                .height(42.dp)
                                                .testTag("quick_format_${fmt.id}")
                                        ) {
                                            Box(contentAlignment = Alignment.Center) {
                                                Text(
                                                    text = fmt.label,
                                                    style = MaterialTheme.typography.labelLarge,
                                                    color = if (selected) TechBlue else InkBlack,
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Ellipsis
                                                )
                                            }
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
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Text(
                                                text = "下发报文预览（含 vendor=$IST_VENDOR_NAME 与实时 UID/ts）",
                                                style = MaterialTheme.typography.labelSmall,
                                                color = MutedInk,
                                                modifier = Modifier.weight(1f)
                                            )
                                            Text(
                                                text = "${footprint.eepromUsedBytes} / $FM11NT083C_USER_EEPROM_BYTES B",
                                                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                                color = if (footprint.isWithinCapacity) TechBlue else AlertRed
                                            )
                                        }
                                        LinearProgressIndicator(
                                            progress = { footprint.usageFraction },
                                            color = if (footprint.isWithinCapacity) TechBlue else AlertRed,
                                            trackColor = SubtleBorder,
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .height(6.dp)
                                                .clip(RoundedCornerShape(3.dp))
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
                                                text = previewPayload,
                                                style = MonoTextStyle.copy(color = InkBlack),
                                                modifier = Modifier.testTag("quick_provision_payload_preview")
                                            )
                                        }
                                    }
                                }

                                HorizontalDivider(color = SubtleBorder, thickness = 1.dp)

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(
                                        modifier = Modifier.weight(1f),
                                        verticalArrangement = Arrangement.spacedBy(2.dp)
                                    ) {
                                        Text(
                                            text = "已绑定 UID 直接覆盖重配",
                                            style = MaterialTheme.typography.labelLarge,
                                            color = InkBlack
                                        )
                                        Text(
                                            text = if (state.overwriteExisting) {
                                                "开启：若芯片已绑定，将自动更新名称与 Wi-Fi 并重配"
                                            } else {
                                                "关闭：若芯片已绑定将拦截提醒，防止误覆盖旧设备"
                                            },
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MutedInk
                                        )
                                    }
                                    Spacer(Modifier.width(12.dp))
                                    Switch(
                                        checked = state.overwriteExisting,
                                        onCheckedChange = onOverwriteChange,
                                        colors = SwitchDefaults.colors(
                                            checkedThumbColor = PureWhiteSurface,
                                            checkedTrackColor = TechBlue
                                        ),
                                        modifier = Modifier.testTag("quick_provision_overwrite_switch")
                                    )
                                }
                            }
                        }
                    }

                    if (state.writeError != null) {
                        item {
                            ErrorBanner(message = state.writeError)
                        }
                    }

                    item {
                        Button(
                            onClick = onStartTap,
                            colors = ButtonDefaults.buttonColors(
                                containerColor = TechBlue,
                                contentColor = PureWhiteSurface
                            ),
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(50.dp)
                                .testTag("quick_provision_start_button")
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Nfc,
                                contentDescription = null,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(Modifier.width(8.dp))
                            Text(
                                text = "开始刷卡（一键绑定并配网）",
                                style = MaterialTheme.typography.labelLarge
                            )
                        }
                    }
                }

                QuickProvisionStep.WAITING_TAP -> {
                    item {
                        Surface(
                            color = PureWhiteSurface,
                            shape = RoundedCornerShape(12.dp),
                            border = BorderStroke(1.dp, SubtleBorder),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(14.dp),
                                verticalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
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
                                                        "当前手机不支持 NFC，无法刷卡写卡"
                                                    } else {
                                                        "NFC 未开启，请开启后贴近设备"
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
                                        title = "请刷取设备 NFC 芯片（1 次完成绑定+配网）",
                                        subtitle = "紧贴目标商显设备 NFC 感应区 1~2 秒，系统将读取 UID 并写入 Wi-Fi 参数"
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
                                        DetailRow("待绑定设备名", state.deviceName.trim())
                                        DetailRow("目标 Wi-Fi", state.ssid.trim())
                                        DetailRow("报文格式", state.format.label)
                                        DetailRow("厂商字段", "$IST_VENDOR_NAME (含实时系统时间戳)")
                                        DetailRow(
                                            "重复 UID 策略",
                                            if (state.overwriteExisting) "允许覆盖并更新" else "拦截重复绑定"
                                        )
                                    }
                                }

                                if (state.writeError != null) {
                                    ErrorBanner(message = state.writeError)
                                }

                                OutlinedButton(
                                    onClick = onBackToForm,
                                    border = BorderStroke(1.dp, SubtleBorder),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(48.dp)
                                        .testTag("quick_provision_back_button")
                                ) {
                                    Icon(
                                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                        contentDescription = null,
                                        tint = InkBlack,
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Spacer(Modifier.width(6.dp))
                                    Text("返回修改参数", color = InkBlack)
                                }
                            }
                        }
                    }
                }

                QuickProvisionStep.SUCCESS -> {
                    item {
                        Surface(
                            color = ProvisionedGreenBg,
                            shape = RoundedCornerShape(12.dp),
                            border = BorderStroke(1.dp, ProvisionedGreenBorder),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("quick_provision_success_banner")
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
                                        text = if (state.isExistingUpdated) {
                                            "已覆盖更新并配网成功"
                                        } else {
                                            "一键绑定并配网成功"
                                        },
                                        style = MaterialTheme.typography.titleMedium,
                                        color = ProvisionedGreen
                                    )
                                    Text(
                                        text = "已完成设备建档与 FM11NT083C NDEF Wi-Fi 写入",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = InkBlack
                                    )
                                }
                            }
                        }
                    }

                    item {
                        val bound = state.lastBoundDevice
                        Surface(
                            color = PureWhiteSurface,
                            shape = RoundedCornerShape(12.dp),
                            border = BorderStroke(1.dp, SubtleBorder),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(14.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                DetailRow("厂商信息", "$IST_VENDOR_NAME (富创通)")
                                DetailRow("设备名称", bound?.name ?: state.deviceName.trim())
                                DetailRow("设备 UID", bound?.uid.orEmpty(), mono = true)
                                DetailRow("写入 Wi-Fi", state.ssid.trim())
                                DetailRow("写入字节数", "${state.writtenByteCount} Bytes (NDEF)")

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = "实际下发报文",
                                        style = MaterialTheme.typography.labelMedium,
                                        color = MutedInk
                                    )
                                    TextButton(
                                        onClick = {
                                            clipboardManager.setText(AnnotatedString(state.writtenPayload))
                                            onCopyFeedback("已复制下发报文内容")
                                        },
                                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                                        modifier = Modifier.height(34.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Filled.ContentCopy,
                                            contentDescription = "复制报文",
                                            tint = TechBlue,
                                            modifier = Modifier.size(14.dp)
                                        )
                                        Spacer(Modifier.width(4.dp))
                                        Text("复制", style = MaterialTheme.typography.labelMedium, color = TechBlue)
                                    }
                                }

                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(CodeSurface)
                                        .border(1.dp, SubtleBorder, RoundedCornerShape(8.dp))
                                        .padding(10.dp)
                                ) {
                                    Text(
                                        text = state.writtenPayload,
                                        style = MonoTextStyle.copy(color = InkBlack),
                                        modifier = Modifier.testTag("quick_provision_success_payload")
                                    )
                                }
                            }
                        }
                    }

                    item {
                        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            Button(
                                onClick = onContinueNext,
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = TechBlue,
                                    contentColor = PureWhiteSurface
                                ),
                                shape = RoundedCornerShape(10.dp),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(48.dp)
                                    .testTag("quick_provision_next_button")
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.Nfc,
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(Modifier.width(6.dp))
                                Text("继续配下一台（保留 Wi-Fi）")
                            }

                            OutlinedButton(
                                onClick = onGoToDevices,
                                border = BorderStroke(1.dp, SubtleBorder),
                                shape = RoundedCornerShape(10.dp),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(48.dp)
                                    .testTag("quick_provision_view_devices_button")
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.Devices,
                                    contentDescription = null,
                                    tint = InkBlack,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(Modifier.width(6.dp))
                                Text("去设备列表查看", color = InkBlack)
                            }
                        }
                    }
                }
            }

            item { Spacer(Modifier.height(12.dp)) }
        }
    }

    if (showAllWifiPicker && state.step == QuickProvisionStep.FORM) {
        WifiPickerDialog(
            scanState = wifiScanState,
            selectedSsid = state.ssid.trim(),
            onSelectNetwork = { network ->
                onSsidChange(network.ssid)
                if (!network.isSecured) {
                    onPasswordChange("")
                }
                showAllWifiPicker = false
            },
            onRescanClick = { wifiScanTrigger++ },
            onDismiss = { showAllWifiPicker = false }
        )
    }
}
