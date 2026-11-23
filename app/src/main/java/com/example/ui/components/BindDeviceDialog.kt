package com.example.ui.components

import android.app.Activity
import android.nfc.NfcAdapter
import android.os.Handler
import android.os.Looper
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
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Nfc
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.WarningAmber
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.model.BindDialogState
import com.example.model.NfcAvailability
import com.example.nfc.NfcHelper
import com.example.ui.theme.AlertRed
import com.example.ui.theme.AlertRedBg
import com.example.ui.theme.AlertRedBorder
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

private val QUICK_DEVICE_NAMES = listOf(
    "前台迎宾屏",
    "大厅广告机",
    "会议室门牌",
    "展厅信息屏",
    "电梯口导视屏"
)

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun BindDeviceDialog(
    state: BindDialogState,
    nfcAvailability: NfcAvailability,
    onTagScanned: (uid: String, byteCount: Int) -> Unit,
    onScanError: (String) -> Unit,
    onRescan: () -> Unit,
    onNameChange: (String) -> Unit,
    onConfirmBind: () -> Unit,
    onOpenNfcSettings: () -> Unit,
    onDismiss: () -> Unit
) {
    if (!state.isOpen) return

    val context = LocalContext.current
    val activity = context as? Activity
    val mainHandler = remember { Handler(Looper.getMainLooper()) }

    DisposableEffect(state.isOpen, state.isScanning, nfcAvailability) {
        if (activity != null && state.isOpen && state.isScanning && nfcAvailability == NfcAvailability.ENABLED) {
            val callback = NfcAdapter.ReaderCallback { tag ->
                val rawBytes = tag.id
                val formatted = NfcHelper.formatUid(rawBytes)
                val length = rawBytes?.size ?: 0
                mainHandler.post {
                    if (formatted.isNotEmpty()) {
                        onTagScanned(formatted, length)
                    } else {
                        onScanError("读取到的 NFC 芯片 UID 为空，请确认芯片型号为 FM11NT083C")
                    }
                }
            }
            val started = NfcHelper.enableReaderMode(activity, callback)
            if (!started) {
                mainHandler.post {
                    onScanError("启用 NFC ReaderMode 失败，请确认手机 NFC 功能正常")
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
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp),
            shape = RoundedCornerShape(16.dp),
            color = PureWhiteSurface,
            border = BorderStroke(1.dp, SubtleBorder),
            tonalElevation = 0.dp
        ) {
            Column(
                modifier = Modifier
                    .verticalScroll(rememberScrollState())
                    .padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = "刷 NFC 读取并绑定设备",
                            style = MaterialTheme.typography.titleMedium,
                            color = InkBlack
                        )
                        Text(
                            text = "FM11NT083C · ISO14443-A · 7 字节 UID",
                            style = MaterialTheme.typography.bodySmall,
                            color = MutedInk
                        )
                    }
                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier.testTag("bind_dialog_close_button")
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Close,
                            contentDescription = "关闭绑定弹窗",
                            tint = MutedInk
                        )
                    }
                }

                // NFC unavailable warning inside dialog
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
                                        "当前手机硬件不支持 NFC"
                                    } else {
                                        "手机 NFC 尚未开启"
                                    },
                                    style = MaterialTheme.typography.titleSmall,
                                    color = PendingAmber
                                )
                            }
                            Text(
                                text = if (nfcAvailability == NfcAvailability.NOT_SUPPORTED) {
                                    "无法启用 ReaderMode 监听商显设备 NFC 标签。"
                                } else {
                                    "请先开启系统 NFC 功能，再将手机背部贴近设备感应区。"
                                },
                                style = MaterialTheme.typography.bodySmall,
                                color = InkBlack
                            )
                            if (nfcAvailability == NfcAvailability.DISABLED) {
                                OutlinedButton(
                                    onClick = onOpenNfcSettings,
                                    modifier = Modifier.testTag("bind_open_nfc_settings_button")
                                ) {
                                    Text("前往系统 NFC 设置")
                                }
                            }
                        }
                    }
                } else if (state.isScanning) {
                    NfcScanningRadarCard()
                } else if (state.scanError != null) {
                    Surface(
                        color = AlertRedBg,
                        shape = RoundedCornerShape(12.dp),
                        border = BorderStroke(1.dp, AlertRedBorder)
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(14.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.ErrorOutline,
                                    contentDescription = null,
                                    tint = AlertRed
                                )
                                Text(
                                    text = state.scanError,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = AlertRed
                                )
                            }
                            OutlinedButton(
                                onClick = onRescan,
                                modifier = Modifier.testTag("bind_retry_scan_button")
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.Refresh,
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(Modifier.width(6.dp))
                                Text("重新刷卡读取")
                            }
                        }
                    }
                }

                // Scanned UID Result Section
                if (state.scannedUid != null) {
                    val isDuplicate = state.duplicateDeviceName != null
                    Surface(
                        color = if (isDuplicate) AlertRedBg else ProvisionedGreenBg,
                        shape = RoundedCornerShape(12.dp),
                        border = BorderStroke(
                            1.dp,
                            if (isDuplicate) AlertRedBorder else ProvisionedGreenBorder
                        )
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(14.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Icon(
                                        imageVector = if (isDuplicate) {
                                            Icons.Filled.WarningAmber
                                        } else {
                                            Icons.Filled.CheckCircle
                                        },
                                        contentDescription = null,
                                        tint = if (isDuplicate) AlertRed else ProvisionedGreen,
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Text(
                                        text = if (isDuplicate) {
                                            "UID 已存在，禁止重复绑定"
                                        } else {
                                            "已读取芯片 UID (${state.uidByteCount} 字节)"
                                        },
                                        style = MaterialTheme.typography.labelLarge,
                                        color = if (isDuplicate) AlertRed else ProvisionedGreen
                                    )
                                }
                                OutlinedButton(
                                    onClick = onRescan,
                                    modifier = Modifier.testTag("bind_rescan_button"),
                                    border = BorderStroke(1.dp, SubtleBorder),
                                    contentPadding = androidx.compose.foundation.layout.PaddingValues(
                                        horizontal = 10.dp,
                                        vertical = 4.dp
                                    )
                                ) {
                                    Icon(
                                        imageVector = Icons.Filled.Refresh,
                                        contentDescription = "重刷 UID",
                                        modifier = Modifier.size(16.dp),
                                        tint = InkBlack
                                    )
                                    Spacer(Modifier.width(4.dp))
                                    Text(
                                        text = "重刷",
                                        style = MaterialTheme.typography.labelMedium,
                                        color = InkBlack
                                    )
                                }
                            }

                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(PureWhiteSurface)
                                    .border(1.dp, SubtleBorder, RoundedCornerShape(8.dp))
                                    .padding(horizontal = 12.dp, vertical = 10.dp)
                            ) {
                                Text(
                                    text = state.scannedUid,
                                    style = MonoTextStyle.copy(
                                        fontWeight = FontWeight.Bold,
                                        color = InkBlack
                                    ),
                                    modifier = Modifier.testTag("bind_scanned_uid_text")
                                )
                            }

                            if (isDuplicate) {
                                Text(
                                    text = "该芯片 UID 已绑定为设备「${state.duplicateDeviceName}」，请更换其他未绑定设备或点击「重刷」。",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = AlertRed
                                )
                            }
                        }
                    }

                    if (!isDuplicate) {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedTextField(
                                value = state.deviceNameInput,
                                onValueChange = onNameChange,
                                label = { Text("设备名称") },
                                placeholder = { Text("例如：前台迎宾屏") },
                                isError = state.nameError != null,
                                supportingText = state.nameError?.let { err -> { Text(err) } },
                                singleLine = true,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("bind_device_name_input")
                            )

                            Text(
                                text = "快捷常用名称",
                                style = MaterialTheme.typography.labelMedium,
                                color = MutedInk
                            )
                            FlowRow(
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                QUICK_DEVICE_NAMES.forEach { preset ->
                                    val selected = state.deviceNameInput == preset
                                    Surface(
                                        onClick = { onNameChange(preset) },
                                        shape = RoundedCornerShape(8.dp),
                                        color = if (selected) TechBlueContainer else SoftSurfaceVariant,
                                        border = BorderStroke(
                                            1.dp,
                                            if (selected) TechBlueBorder else SubtleBorder
                                        ),
                                        modifier = Modifier.testTag("quick_name_chip_$preset")
                                    ) {
                                        Text(
                                            text = preset,
                                            style = MaterialTheme.typography.labelMedium,
                                            color = if (selected) TechBlue else InkBlack,
                                            modifier = Modifier.padding(
                                                horizontal = 10.dp,
                                                vertical = 6.dp
                                            )
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                // Footer Actions
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    OutlinedButton(
                        onClick = onDismiss,
                        modifier = Modifier
                            .weight(1f)
                            .height(48.dp)
                            .testTag("bind_cancel_button"),
                        border = BorderStroke(1.dp, SubtleBorder)
                    ) {
                        Text("取消", color = InkBlack)
                    }
                    Button(
                        onClick = onConfirmBind,
                        enabled = state.scannedUid != null && state.duplicateDeviceName == null,
                        modifier = Modifier
                            .weight(1f)
                            .height(48.dp)
                            .testTag("bind_confirm_button"),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = TechBlue,
                            contentColor = PureWhiteSurface
                        )
                    ) {
                        Text("确认绑定")
                    }
                }
            }
        }
    }
}

@Composable
private fun NfcScanningRadarCard() {
    val infiniteTransition = rememberInfiniteTransition(label = "nfc_scan_pulse")
    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 0.92f,
        targetValue = 1.15f,
        animationSpec = infiniteRepeatable(
            animation = tween(900),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulse_scale"
    )
    val pulseAlpha by infiniteTransition.animateFloat(
        initialValue = 0.95f,
        targetValue = 0.45f,
        animationSpec = infiniteRepeatable(
            animation = tween(900),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulse_alpha"
    )

    Surface(
        color = TechBlueContainer,
        shape = RoundedCornerShape(12.dp),
        border = BorderStroke(1.dp, TechBlueBorder)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 22.dp, horizontal = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier.size(68.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(64.dp)
                        .scale(pulseScale)
                        .alpha(pulseAlpha)
                        .clip(CircleShape)
                        .background(TechBlue.copy(alpha = 0.16f))
                )
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .size(48.dp)
                        .clip(CircleShape)
                        .background(TechBlue)
                ) {
                    Icon(
                        imageVector = Icons.Filled.Nfc,
                        contentDescription = "等待 NFC 刷卡",
                        tint = PureWhiteSurface,
                        modifier = Modifier.size(26.dp)
                    )
                }
            }
            Text(
                text = "正在监听 NFC 感应区...",
                style = MaterialTheme.typography.titleSmall,
                color = TechBlue
            )
            Text(
                text = "请将手机背部 NFC 区域贴近商显设备 FM11NT083C 标签，自动读取 7 字节硬件 UID",
                style = MaterialTheme.typography.bodySmall,
                color = MutedInk
            )
        }
    }
}
