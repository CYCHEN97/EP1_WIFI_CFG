package com.example.ui.screens

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
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AddLink
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.Nfc
import androidx.compose.material.icons.filled.PauseCircleOutline
import androidx.compose.material.icons.filled.PlayCircleOutline
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.model.BoundDevice
import com.example.model.NfcAvailability
import com.example.model.ParsedNdefRecord
import com.example.model.TagInspectResult
import com.example.model.TagInspectorState
import com.example.model.TimeFormatter
import com.example.nfc.NfcClearResult
import com.example.nfc.NfcHelper
import com.example.nfc.TagReadOutcome
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

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun TagInspectorScreen(
    state: TagInspectorState,
    devices: List<BoundDevice>,
    nfcAvailability: NfcAvailability,
    isModalBlockingNfc: Boolean,
    onToggleListening: (Boolean) -> Unit,
    onToggleEraseMode: (Boolean) -> Unit,
    onTagReadOutcome: (TagReadOutcome) -> Unit,
    onTagCleared: (NfcClearResult, TagReadOutcome?) -> Unit,
    onClearResult: () -> Unit,
    onQuickBindUid: (uid: String, byteCount: Int) -> Unit,
    onCopyFeedback: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val activity = context as? Activity
    val mainHandler = remember { Handler(Looper.getMainLooper()) }
    @Suppress("DEPRECATION")
    val clipboardManager = LocalClipboardManager.current

    var expandAllPages by remember { mutableStateOf(false) }

    val shouldListen =
        (state.isListening || state.isEraseMode) &&
            nfcAvailability == NfcAvailability.ENABLED &&
            !isModalBlockingNfc

    DisposableEffect(shouldListen, state.isEraseMode, activity) {
        if (activity != null && shouldListen) {
            val callback = NfcAdapter.ReaderCallback { tag ->
                if (state.isEraseMode) {
                    val clearResult = NfcHelper.clearTagData(tag)
                    val refreshedOutcome = if (clearResult is NfcClearResult.Success) {
                        runCatching { NfcHelper.readTagDetails(tag) }.getOrNull()
                    } else {
                        null
                    }
                    mainHandler.post {
                        onTagCleared(clearResult, refreshedOutcome)
                    }
                } else {
                    val outcome = NfcHelper.readTagDetails(tag)
                    mainHandler.post {
                        onTagReadOutcome(outcome)
                    }
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
            // Top Reader Status Control Card
            item {
                ReaderControlCard(
                    isListening = state.isListening,
                    isEraseMode = state.isEraseMode,
                    nfcAvailability = nfcAvailability,
                    hasResult = state.lastResult != null,
                    onToggleListening = onToggleListening,
                    onToggleEraseMode = onToggleEraseMode,
                    onClearResult = onClearResult
                )
            }

            // Clear success banner
            state.lastClearMessage?.let { clearMsg ->
                item {
                    Surface(
                        color = ProvisionedGreenBg,
                        shape = RoundedCornerShape(10.dp),
                        border = BorderStroke(1.dp, ProvisionedGreenBorder),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("inspector_clear_success_banner")
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Filled.CheckCircle,
                                contentDescription = null,
                                tint = ProvisionedGreen,
                                modifier = Modifier.size(20.dp)
                            )
                            Text(
                                text = clearMsg,
                                style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Medium),
                                color = ProvisionedGreen
                            )
                        }
                    }
                }
            }

            // Read / Clear error banner
            state.lastError?.let { errorMsg ->
                item {
                    Surface(
                        color = AlertRedBg,
                        shape = RoundedCornerShape(10.dp),
                        border = BorderStroke(1.dp, AlertRedBorder),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp),
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
                                text = errorMsg,
                                style = MaterialTheme.typography.bodySmall,
                                color = AlertRed
                            )
                        }
                    }
                }
            }

            val result = state.lastResult
            if (result == null) {
                item {
                    EmptyInspectorPromptCard()
                }
            } else {
                val boundDevice = devices.firstOrNull { it.uid.equals(result.uid, ignoreCase = true) }

                // 1. Chip Identity & RF Specs Card
                item {
                    ChipIdentityCard(
                        result = result,
                        boundDevice = boundDevice,
                        onCopyUid = {
                            clipboardManager.setText(AnnotatedString(result.uid))
                            onCopyFeedback("已复制 UID: ${result.uid}")
                        },
                        onQuickBind = {
                            onQuickBindUid(result.uid, result.uidByteCount)
                        },
                        onCopyFullDump = {
                            val report = buildDebugReport(result)
                            clipboardManager.setText(AnnotatedString(report))
                            onCopyFeedback("已复制完整读卡调试报告")
                        }
                    )
                }

                // 2. NDEF Payload & Records Card
                item {
                    NdefInspectCard(
                        result = result,
                        onCopyRecordText = { text ->
                            clipboardManager.setText(AnnotatedString(text))
                            onCopyFeedback("已复制 NDEF 明文负载")
                        }
                    )
                }

                // 3. Raw EEPROM Page Dump Card (HEX + ASCII)
                if (result.rawPages.isNotEmpty()) {
                    item {
                        RawPagesDumpCard(
                            result = result,
                            expanded = expandAllPages,
                            onToggleExpand = { expandAllPages = !expandAllPages },
                            onCopyHexDump = {
                                val hexDump = result.rawPages.joinToString("\n") { page ->
                                    "Page 0x%02X: %-11s | %-4s | %s".format(
                                        page.pageIndex,
                                        page.dataHex,
                                        page.asciiPreview,
                                        page.note
                                    )
                                }
                                clipboardManager.setText(AnnotatedString(hexDump))
                                onCopyFeedback("已复制 EEPROM 页 HEX + ASCII 数据")
                            },
                            onCopyAscii = {
                                val asciiText = result.userAreaAscii.ifEmpty { result.rawEepromAscii }
                                clipboardManager.setText(AnnotatedString(asciiText))
                                onCopyFeedback("已复制 EEPROM ASCII 信息")
                            }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ReaderControlCard(
    isListening: Boolean,
    isEraseMode: Boolean,
    nfcAvailability: NfcAvailability,
    hasResult: Boolean,
    onToggleListening: (Boolean) -> Unit,
    onToggleEraseMode: (Boolean) -> Unit,
    onClearResult: () -> Unit
) {
    val active = (isListening || isEraseMode) && nfcAvailability == NfcAvailability.ENABLED
    val accentColor = if (isEraseMode) AlertRed else TechBlue
    val containerColor = when {
        nfcAvailability != NfcAvailability.ENABLED -> PureWhiteSurface
        isEraseMode -> AlertRedBg
        isListening -> TechBlueContainer
        else -> PureWhiteSurface
    }
    val borderColor = when {
        nfcAvailability != NfcAvailability.ENABLED -> SubtleBorder
        isEraseMode -> AlertRedBorder
        isListening -> TechBlueBorder
        else -> SubtleBorder
    }

    val infiniteTransition = rememberInfiniteTransition(label = "inspector_pulse")
    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 0.9f,
        targetValue = 1.18f,
        animationSpec = infiniteRepeatable(
            animation = tween(900),
            repeatMode = RepeatMode.Reverse
        ),
        label = "inspector_pulse_scale"
    )
    val pulseAlpha by infiniteTransition.animateFloat(
        initialValue = 0.9f,
        targetValue = 0.35f,
        animationSpec = infiniteRepeatable(
            animation = tween(900),
            repeatMode = RepeatMode.Reverse
        ),
        label = "inspector_pulse_alpha"
    )

    Surface(
        color = containerColor,
        shape = RoundedCornerShape(12.dp),
        border = BorderStroke(1.dp, borderColor),
        modifier = Modifier
            .fillMaxWidth()
            .testTag("inspector_control_card")
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier.size(44.dp)
                ) {
                    if (active) {
                        Box(
                            modifier = Modifier
                                .size(44.dp)
                                .scale(pulseScale)
                                .alpha(pulseAlpha)
                                .clip(CircleShape)
                                .background(accentColor.copy(alpha = 0.18f))
                        )
                    }
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                            .background(if (active) accentColor else SoftSurfaceVariant)
                    ) {
                        Icon(
                            imageVector = if (isEraseMode) Icons.Filled.DeleteSweep else Icons.Filled.Nfc,
                            contentDescription = null,
                            tint = if (active) PureWhiteSurface else MutedInk,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = when {
                            nfcAvailability != NfcAvailability.ENABLED -> "NFC 未就绪，无法读写"
                            isEraseMode -> "待刷卡清空写入数据"
                            isListening -> "读卡调试监听中"
                            else -> "已暂停读卡监听"
                        },
                        style = MaterialTheme.typography.titleSmall,
                        color = if (active) accentColor else InkBlack
                    )
                    Text(
                        text = when {
                            nfcAvailability != NfcAvailability.ENABLED -> "请先在系统设置开启手机 NFC 功能"
                            isEraseMode -> "请将手机贴近 NFC 芯片保持 1~2 秒，将擦除用户区写入信息并重置为空 NDEF"
                            isListening -> "将手机贴近 FM11NT083C 或任意 NFC 标签即可实时读出数据"
                            else -> "点击下方恢复监听以继续刷卡读取，或切换为清空卡片数据"
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MutedInk
                    )
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                if (isEraseMode) {
                    OutlinedButton(
                        onClick = { onToggleEraseMode(false) },
                        border = BorderStroke(1.dp, AlertRedBorder),
                        colors = ButtonDefaults.outlinedButtonColors(
                            containerColor = PureWhiteSurface,
                            contentColor = AlertRed
                        ),
                        modifier = Modifier
                            .weight(1f)
                            .height(42.dp)
                            .testTag("inspector_cancel_erase_button")
                    ) {
                        Text("取消清空，返回读卡")
                    }
                } else {
                    OutlinedButton(
                        onClick = { onToggleListening(!isListening) },
                        border = BorderStroke(1.dp, if (isListening) SubtleBorder else TechBlueBorder),
                        colors = ButtonDefaults.outlinedButtonColors(
                            containerColor = PureWhiteSurface,
                            contentColor = if (isListening) InkBlack else TechBlue
                        ),
                        contentPadding = PaddingValues(horizontal = 10.dp),
                        modifier = Modifier
                            .weight(1f)
                            .height(42.dp)
                            .testTag("inspector_toggle_listen_button")
                    ) {
                        Icon(
                            imageVector = if (isListening) {
                                Icons.Filled.PauseCircleOutline
                            } else {
                                Icons.Filled.PlayCircleOutline
                            },
                            contentDescription = null,
                            modifier = Modifier.size(17.dp)
                        )
                        Spacer(Modifier.width(4.dp))
                        Text(
                            text = if (isListening) "暂停监听" else "恢复监听",
                            style = MaterialTheme.typography.labelMedium
                        )
                    }

                    OutlinedButton(
                        onClick = { onToggleEraseMode(true) },
                        enabled = nfcAvailability == NfcAvailability.ENABLED,
                        border = BorderStroke(1.dp, AlertRedBorder),
                        colors = ButtonDefaults.outlinedButtonColors(
                            containerColor = AlertRedBg,
                            contentColor = AlertRed
                        ),
                        contentPadding = PaddingValues(horizontal = 10.dp),
                        modifier = Modifier
                            .weight(1f)
                            .height(42.dp)
                            .testTag("inspector_erase_mode_button")
                    ) {
                        Icon(
                            imageVector = Icons.Filled.DeleteSweep,
                            contentDescription = null,
                            modifier = Modifier.size(17.dp)
                        )
                        Spacer(Modifier.width(4.dp))
                        Text("清空数据", style = MaterialTheme.typography.labelMedium)
                    }

                    if (hasResult) {
                        OutlinedButton(
                            onClick = onClearResult,
                            border = BorderStroke(1.dp, SubtleBorder),
                            colors = ButtonDefaults.outlinedButtonColors(
                                containerColor = PureWhiteSurface,
                                contentColor = MutedInk
                            ),
                            contentPadding = PaddingValues(horizontal = 10.dp),
                            modifier = Modifier
                                .height(42.dp)
                                .testTag("inspector_clear_button")
                        ) {
                            Icon(
                                imageVector = Icons.Filled.DeleteOutline,
                                contentDescription = "清空屏幕显示",
                                modifier = Modifier.size(17.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun EmptyInspectorPromptCard() {
    Surface(
        color = PureWhiteSurface,
        shape = RoundedCornerShape(12.dp),
        border = BorderStroke(1.dp, SubtleBorder),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 22.dp, vertical = 28.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(64.dp)
                    .clip(CircleShape)
                    .background(SoftSurfaceVariant)
                    .border(1.dp, SubtleBorder, CircleShape)
            ) {
                Icon(
                    imageVector = Icons.Filled.Memory,
                    contentDescription = null,
                    tint = TechBlue,
                    modifier = Modifier.size(30.dp)
                )
            }
            Text(
                text = "等待刷取 NFC 卡片",
                style = MaterialTheme.typography.titleMedium,
                color = InkBlack
            )
            Text(
                text = "贴近设备后将自动读出芯片 UID、ATQA/SAK、支持协议、NDEF 明文记录及 FM11NT083C 用户区原始 HEX 与 ASCII 内容，方便验证写入是否生效。",
                style = MaterialTheme.typography.bodySmall,
                color = MutedInk,
                textAlign = TextAlign.Center
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ChipIdentityCard(
    result: TagInspectResult,
    boundDevice: BoundDevice?,
    onCopyUid: () -> Unit,
    onQuickBind: () -> Unit,
    onCopyFullDump: () -> Unit
) {
    Surface(
        color = PureWhiteSurface,
        shape = RoundedCornerShape(12.dp),
        border = BorderStroke(1.dp, SubtleBorder),
        modifier = Modifier
            .fillMaxWidth()
            .testTag("inspector_chip_card")
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "芯片基础信息",
                        style = MaterialTheme.typography.titleMedium,
                        color = InkBlack
                    )
                    Text(
                        text = "读取时间：${TimeFormatter.formatDateTime(result.readAt)}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MutedInk
                    )
                }
                OutlinedButton(
                    onClick = onCopyFullDump,
                    border = BorderStroke(1.dp, TechBlueBorder),
                    colors = ButtonDefaults.outlinedButtonColors(
                        containerColor = TechBlueContainer,
                        contentColor = TechBlue
                    ),
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                    modifier = Modifier
                        .height(36.dp)
                        .testTag("inspector_copy_report_button")
                ) {
                    Icon(
                        imageVector = Icons.Filled.ContentCopy,
                        contentDescription = null,
                        modifier = Modifier.size(14.dp)
                    )
                    Spacer(Modifier.width(4.dp))
                    Text("复制报告", style = MaterialTheme.typography.labelMedium)
                }
            }

            // UID block
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp))
                    .background(SoftSurfaceVariant)
                    .border(1.dp, SubtleBorder, RoundedCornerShape(8.dp))
                    .padding(start = 12.dp, end = 4.dp, top = 6.dp, bottom = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "硬件 UID (${result.uidByteCount} 字节 · HEX: ${result.uidRawHex})",
                        style = MaterialTheme.typography.labelSmall,
                        color = MutedInk
                    )
                    Text(
                        text = result.uid,
                        style = MonoTextStyle.copy(
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold,
                            color = InkBlack
                        ),
                        modifier = Modifier.testTag("inspector_uid_text")
                    )
                }
                IconButton(
                    onClick = onCopyUid,
                    modifier = Modifier.testTag("inspector_copy_uid_button")
                ) {
                    Icon(
                        imageVector = Icons.Filled.ContentCopy,
                        contentDescription = "复制 UID",
                        tint = MutedInk,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }

            // Binding status row
            if (boundDevice != null) {
                Surface(
                    color = ProvisionedGreenBg,
                    shape = RoundedCornerShape(8.dp),
                    border = BorderStroke(1.dp, ProvisionedGreenBorder)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 10.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Filled.CheckCircle,
                            contentDescription = null,
                            tint = ProvisionedGreen,
                            modifier = Modifier.size(16.dp)
                        )
                        Text(
                            text = "已绑定设备：「${boundDevice.name}」(${boundDevice.status.label})",
                            style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Medium),
                            color = ProvisionedGreen
                        )
                    }
                }
            } else {
                Surface(
                    color = PendingAmberBg,
                    shape = RoundedCornerShape(8.dp),
                    border = BorderStroke(1.dp, PendingAmberBorder)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 10.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "该 UID 尚未在本地绑定设备",
                            style = MaterialTheme.typography.bodySmall,
                            color = PendingAmber
                        )
                        Button(
                            onClick = onQuickBind,
                            colors = ButtonDefaults.buttonColors(containerColor = TechBlue),
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                            modifier = Modifier
                                .height(34.dp)
                                .testTag("inspector_quick_bind_button")
                        ) {
                            Icon(
                                imageVector = Icons.Filled.AddLink,
                                contentDescription = null,
                                modifier = Modifier.size(14.dp)
                            )
                            Spacer(Modifier.width(4.dp))
                            Text("绑定此卡", style = MaterialTheme.typography.labelMedium)
                        }
                    }
                }
            }

            // RF parameters
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                result.atqaHex?.let { InspectorChip("ATQA: $it") }
                result.sakHex?.let { InspectorChip("SAK: 0x$it") }
                result.techList.forEach { tech ->
                    InspectorChip(tech)
                }
            }

            result.note?.let { note ->
                Text(
                    text = "提示：$note",
                    style = MaterialTheme.typography.bodySmall,
                    color = PendingAmber
                )
            }
        }
    }
}

@Composable
private fun NdefInspectCard(
    result: TagInspectResult,
    onCopyRecordText: (String) -> Unit
) {
    Surface(
        color = PureWhiteSurface,
        shape = RoundedCornerShape(12.dp),
        border = BorderStroke(1.dp, SubtleBorder),
        modifier = Modifier
            .fillMaxWidth()
            .testTag("inspector_ndef_card")
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "NDEF 报文信息",
                    style = MaterialTheme.typography.titleMedium,
                    color = InkBlack
                )
                val statusLabel = when {
                    result.isNdefFormatted -> "已格式化 NDEF"
                    result.isNdefFormatable -> "空白芯片 (可格式化)"
                    else -> "非 NDEF 标签"
                }
                Surface(
                    color = if (result.isNdefFormatted) TechBlueContainer else SoftSurfaceVariant,
                    shape = RoundedCornerShape(6.dp),
                    border = BorderStroke(
                        1.dp,
                        if (result.isNdefFormatted) TechBlueBorder else SubtleBorder
                    )
                ) {
                    Text(
                        text = statusLabel,
                        style = MaterialTheme.typography.labelSmall,
                        color = if (result.isNdefFormatted) TechBlue else MutedInk,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                    )
                }
            }

            if (result.isNdefFormatted) {
                val maxB = result.ndefMaxBytes ?: 0
                val usedB = result.ndefUsedBytes
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "类型：${result.ndefType ?: "未知"} · ${if (result.isWritable == true) "可读写" else "只读"}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MutedInk
                        )
                        if (maxB > 0) {
                            Text(
                                text = "$usedB / $maxB Bytes",
                                style = MonoTextStyle.copy(color = TechBlue, fontWeight = FontWeight.Bold)
                            )
                        }
                    }
                    if (maxB > 0) {
                        LinearProgressIndicator(
                            progress = { (usedB.toFloat() / maxB.toFloat()).coerceIn(0f, 1f) },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(6.dp)
                                .clip(RoundedCornerShape(3.dp)),
                            color = TechBlue,
                            trackColor = SubtleBorder
                        )
                    }
                }
            }

            if (result.records.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .background(CodeSurface)
                        .border(1.dp, SubtleBorder, RoundedCornerShape(8.dp))
                        .padding(12.dp)
                ) {
                    Text(
                        text = if (result.isNdefFormatted) {
                            "当前 NDEF 区为空，尚未写入任何记录。"
                        } else {
                            "当前芯片未写入 NDEF 结构，首次配网时 APP 将自动调用 NdefFormatable 格式化写入。"
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MutedInk
                    )
                }
            } else {
                result.records.forEach { rec ->
                    NdefRecordBlock(
                        record = rec,
                        onCopyText = { onCopyRecordText(rec.decodedText) }
                    )
                }
            }
        }
    }
}

@Composable
private fun NdefRecordBlock(
    record: ParsedNdefRecord,
    onCopyText: () -> Unit
) {
    Surface(
        color = CodeSurface,
        shape = RoundedCornerShape(10.dp),
        border = BorderStroke(1.dp, SubtleBorder),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Record #${record.index} · ${record.tnfLabel} · ${record.typeLabel}" +
                        (record.languageCode?.let { " · 语言=$it" } ?: ""),
                    style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
                    color = TechBlue,
                    modifier = Modifier.weight(1f)
                )
                IconButton(
                    onClick = onCopyText,
                    modifier = Modifier.size(32.dp)
                ) {
                    Icon(
                        imageVector = Icons.Filled.ContentCopy,
                        contentDescription = "复制记录明文",
                        tint = MutedInk,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(6.dp))
                    .background(PureWhiteSurface)
                    .border(1.dp, SubtleBorder, RoundedCornerShape(6.dp))
                    .padding(10.dp)
            ) {
                Text(
                    text = record.decodedText,
                    style = MonoTextStyle.copy(color = InkBlack),
                    modifier = Modifier.testTag("inspector_record_text_${record.index}")
                )
            }

            if (record.payloadHex.isNotEmpty()) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(6.dp))
                        .background(PureWhiteSurface)
                        .border(1.dp, SubtleBorder, RoundedCornerShape(6.dp))
                        .padding(10.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(
                        text = "原始负载 HEX (${record.payloadBytes} B)",
                        style = MaterialTheme.typography.labelSmall,
                        color = MutedInk
                    )
                    Text(
                        text = record.payloadHex,
                        style = MonoTextStyle.copy(fontSize = 11.sp, lineHeight = 16.sp, color = InkBlack),
                        modifier = Modifier.testTag("inspector_record_hex_${record.index}")
                    )
                    HorizontalDivider(color = SubtleBorder, thickness = 1.dp)
                    Text(
                        text = "原始负载 ASCII",
                        style = MaterialTheme.typography.labelSmall,
                        color = MutedInk
                    )
                    Text(
                        text = record.payloadAscii,
                        style = MonoTextStyle.copy(fontSize = 12.sp, lineHeight = 17.sp, color = TechBlue),
                        modifier = Modifier.testTag("inspector_record_ascii_${record.index}")
                    )
                }
            }
        }
    }
}

@Composable
private fun RawPagesDumpCard(
    result: TagInspectResult,
    expanded: Boolean,
    onToggleExpand: () -> Unit,
    onCopyHexDump: () -> Unit,
    onCopyAscii: () -> Unit
) {
    val visiblePages = if (expanded) result.rawPages else result.rawPages.take(12)
    val asciiContent = result.userAreaAscii.ifEmpty { result.rawEepromAscii }

    Surface(
        color = PureWhiteSurface,
        shape = RoundedCornerShape(12.dp),
        border = BorderStroke(1.dp, SubtleBorder),
        modifier = Modifier
            .fillMaxWidth()
            .testTag("inspector_raw_pages_card")
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "EEPROM 原始数据 (HEX / ASCII)",
                        style = MaterialTheme.typography.titleSmall,
                        color = InkBlack
                    )
                    Text(
                        text = "已读出 ${result.rawPages.size} 页 (每页 4 Bytes，Page 0x04 起为用户 NDEF 区)",
                        style = MaterialTheme.typography.labelSmall,
                        color = MutedInk
                    )
                }
                TextButton(
                    onClick = onCopyHexDump,
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)
                ) {
                    Icon(
                        imageVector = Icons.Filled.ContentCopy,
                        contentDescription = null,
                        tint = TechBlue,
                        modifier = Modifier.size(15.dp)
                    )
                    Spacer(Modifier.width(4.dp))
                    Text("复制页表", style = MaterialTheme.typography.labelMedium, color = TechBlue)
                }
            }

            // Continuous User-Area ASCII block (wrapped, immediately readable)
            if (asciiContent.isNotEmpty()) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .background(CodeSurface)
                        .border(1.dp, SubtleBorder, RoundedCornerShape(8.dp))
                        .padding(10.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = if (result.userAreaAscii.isNotEmpty()) {
                                "用户区连续 ASCII (Page 0x04 起，不可打印字节以 . 表示)"
                            } else {
                                "芯片连续 ASCII 预览"
                            },
                            style = MaterialTheme.typography.labelSmall,
                            color = MutedInk,
                            modifier = Modifier.weight(1f)
                        )
                        TextButton(
                            onClick = onCopyAscii,
                            contentPadding = PaddingValues(horizontal = 6.dp, vertical = 2.dp),
                            modifier = Modifier
                                .height(28.dp)
                                .testTag("inspector_copy_ascii_button")
                        ) {
                            Text("复制 ASCII", style = MaterialTheme.typography.labelSmall, color = TechBlue)
                        }
                    }
                    Text(
                        text = asciiContent,
                        style = MonoTextStyle.copy(fontSize = 12.sp, lineHeight = 18.sp, color = InkBlack),
                        modifier = Modifier.testTag("inspector_eeprom_ascii_text")
                    )
                }
            }

            // Per-page HEX + ASCII table
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp))
                    .background(CodeSurface)
                    .border(1.dp, SubtleBorder, RoundedCornerShape(8.dp))
                    .horizontalScroll(rememberScrollState())
                    .padding(10.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(
                    text = "页号   HEX (4 Bytes)   ASCII   区域说明",
                    style = MonoTextStyle.copy(
                        fontSize = 11.sp,
                        color = MutedInk,
                        fontWeight = FontWeight.Bold
                    )
                )
                HorizontalDivider(color = SubtleBorder, thickness = 1.dp)
                visiblePages.forEach { page ->
                    val highlightPage = page.pageIndex == 3 || page.pageIndex == 4
                    Text(
                        text = "0x%02X  %-11s   |%-4s|  %s".format(
                            page.pageIndex,
                            page.dataHex,
                            page.asciiPreview,
                            page.note
                        ),
                        style = MonoTextStyle.copy(
                            fontSize = 12.sp,
                            lineHeight = 17.sp,
                            color = if (highlightPage) TechBlue else InkBlack,
                            fontWeight = if (highlightPage) FontWeight.Bold else FontWeight.Medium
                        )
                    )
                }
            }

            if (result.rawPages.size > 12) {
                HorizontalDivider(color = SubtleBorder, thickness = 1.dp)
                TextButton(
                    onClick = onToggleExpand,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(
                        imageVector = if (expanded) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore,
                        contentDescription = null,
                        tint = TechBlue
                    )
                    Spacer(Modifier.width(4.dp))
                    Text(
                        text = if (expanded) "收起部分页数据" else "展开全部 ${result.rawPages.size} 页数据",
                        color = TechBlue
                    )
                }
            }
        }
    }
}

@Composable
private fun InspectorChip(text: String) {
    Surface(
        color = SoftSurfaceVariant,
        shape = RoundedCornerShape(6.dp),
        border = BorderStroke(1.dp, SubtleBorder)
    ) {
        Text(
            text = text,
            style = MonoTextStyle.copy(fontSize = 12.sp, color = InkBlack),
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
        )
    }
}

private fun buildDebugReport(result: TagInspectResult): String {
    return buildString {
        appendLine("=== 富创通 IST NFC 读卡调试报告 ===")
        appendLine("读取时间: ${TimeFormatter.formatDateTime(result.readAt)}")
        appendLine("UID (${result.uidByteCount}B): ${result.uid} (HEX: ${result.uidRawHex})")
        result.atqaHex?.let { appendLine("ATQA: $it") }
        result.sakHex?.let { appendLine("SAK: 0x$it") }
        appendLine("TechList: ${result.techList.joinToString(", ")}")
        appendLine("NDEF 格式化: ${result.isNdefFormatted}, 类型: ${result.ndefType ?: "-"}, 可写: ${result.isWritable ?: "-"}")
        appendLine("NDEF 容量: ${result.ndefUsedBytes} / ${result.ndefMaxBytes ?: "-"} Bytes")
        if (result.records.isNotEmpty()) {
            appendLine("--- NDEF Records ---")
            result.records.forEach { rec ->
                appendLine("#${rec.index} [${rec.tnfLabel} / ${rec.typeLabel}]: ${rec.decodedText}")
                if (rec.payloadHex.isNotEmpty()) {
                    appendLine("  HEX: ${rec.payloadHex}")
                    appendLine("  ASCII: ${rec.payloadAscii}")
                }
            }
        }
        if (result.userAreaAscii.isNotEmpty()) {
            appendLine("--- User Area ASCII (Page 0x04+) ---")
            appendLine(result.userAreaAscii)
        }
        if (result.rawPages.isNotEmpty()) {
            appendLine("--- EEPROM Pages (HEX | ASCII) ---")
            result.rawPages.forEach { p ->
                appendLine("0x%02X: %-11s |%-4s| %s".format(p.pageIndex, p.dataHex, p.asciiPreview, p.note))
            }
        }
    }.trimEnd()
}
