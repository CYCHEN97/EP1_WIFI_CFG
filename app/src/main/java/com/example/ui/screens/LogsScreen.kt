package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.History
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.model.NfcLogEntry
import com.example.model.NfcLogType
import com.example.model.TimeFormatter
import com.example.nfc.NfcHelper
import com.example.ui.theme.AlertRed
import com.example.ui.theme.AlertRedBg
import com.example.ui.theme.AlertRedBorder
import com.example.ui.theme.CodeSurface
import com.example.ui.theme.InkBlack
import com.example.ui.theme.MonoTextStyle
import com.example.ui.theme.MutedInk
import com.example.ui.theme.ProvisionedGreen
import com.example.ui.theme.ProvisionedGreenBg
import com.example.ui.theme.ProvisionedGreenBorder
import com.example.ui.theme.PureWhiteSurface
import com.example.ui.theme.SoftSurfaceVariant
import com.example.ui.theme.SubtleBorder
import com.example.ui.theme.TechBlue
import com.example.ui.theme.TechBlueBorder
import com.example.ui.theme.TechBlueContainer

@Composable
fun LogsScreen(
    logs: List<NfcLogEntry>,
    onClearLogs: () -> Unit,
    onCopyFeedback: (String) -> Unit = {},
    modifier: Modifier = Modifier
) {
    var showClearConfirm by remember { mutableStateOf(false) }
    var selectedLog by remember { mutableStateOf<NfcLogEntry?>(null) }

    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.TopCenter
    ) {
        if (logs.isEmpty()) {
            Column(
                modifier = Modifier
                    .widthIn(max = 600.dp)
                    .fillMaxSize()
                    .padding(20.dp),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Surface(
                    color = PureWhiteSurface,
                    shape = RoundedCornerShape(16.dp),
                    border = BorderStroke(1.dp, SubtleBorder),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(28.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(12.dp)
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
                                imageVector = Icons.Filled.History,
                                contentDescription = null,
                                tint = MutedInk,
                                modifier = Modifier.size(30.dp)
                            )
                        }
                        Text(
                            text = "暂无 NFC 读写日志",
                            style = MaterialTheme.typography.titleMedium,
                            color = InkBlack
                        )
                        Text(
                            text = "执行读取 UID 绑定、NFC 写入 Wi-Fi 或读卡调试时，将自动按时间倒序记录在此处，点击任意日志可查看 HEX 与 ASCII 详情。",
                            style = MaterialTheme.typography.bodySmall,
                            color = MutedInk
                        )
                    }
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .widthIn(max = 680.dp)
                    .fillMaxSize(),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 14.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                item {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "共 ${logs.size} 条本地记录（时间倒序）",
                                style = MaterialTheme.typography.labelLarge,
                                color = MutedInk
                            )
                            Text(
                                text = "点击任意日志可查看详细 HEX 与 ASCII 数据",
                                style = MaterialTheme.typography.labelSmall,
                                color = MutedInk
                            )
                        }
                        OutlinedButton(
                            onClick = { showClearConfirm = true },
                            border = BorderStroke(1.dp, AlertRedBorder),
                            colors = ButtonDefaults.outlinedButtonColors(
                                containerColor = AlertRedBg,
                                contentColor = AlertRed
                            ),
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                            modifier = Modifier.testTag("clear_logs_button")
                        ) {
                            Icon(
                                imageVector = Icons.Filled.DeleteSweep,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(Modifier.width(4.dp))
                            Text("清空日志", style = MaterialTheme.typography.labelMedium)
                        }
                    }
                }

                items(logs, key = { it.id }) { entry ->
                    LogEntryCard(
                        entry = entry,
                        onClick = { selectedLog = entry }
                    )
                }
            }
        }
    }

    selectedLog?.let { entry ->
        LogDetailDialog(
            entry = entry,
            onCopyFeedback = onCopyFeedback,
            onDismiss = { selectedLog = null }
        )
    }

    if (showClearConfirm) {
        AlertDialog(
            onDismissRequest = { showClearConfirm = false },
            containerColor = PureWhiteSurface,
            title = {
                Text(
                    text = "清空读写日志",
                    style = MaterialTheme.typography.titleMedium,
                    color = InkBlack
                )
            },
            text = {
                Text(
                    text = "确定要清空全部 ${logs.size} 条本地 NFC 读写记录吗？此操作不可撤销。",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MutedInk
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        onClearLogs()
                        selectedLog = null
                        showClearConfirm = false
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = AlertRed),
                    modifier = Modifier.testTag("confirm_clear_logs_button")
                ) {
                    Text("确认清空")
                }
            },
            dismissButton = {
                TextButton(onClick = { showClearConfirm = false }) {
                    Text("取消", color = MutedInk)
                }
            }
        )
    }
}

@Composable
private fun LogEntryCard(
    entry: NfcLogEntry,
    onClick: () -> Unit
) {
    val badgeColors = logBadgeColors(entry.type)

    Surface(
        onClick = onClick,
        color = PureWhiteSurface,
        shape = RoundedCornerShape(12.dp),
        border = BorderStroke(1.dp, SubtleBorder),
        modifier = Modifier
            .fillMaxWidth()
            .testTag("log_card_${entry.id}")
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(
                    color = badgeColors.bg,
                    shape = RoundedCornerShape(6.dp),
                    border = BorderStroke(1.dp, badgeColors.border)
                ) {
                    Text(
                        text = entry.type.label,
                        style = MaterialTheme.typography.labelMedium.copy(
                            fontWeight = FontWeight.SemiBold
                        ),
                        color = badgeColors.fg,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                    )
                }
                Text(
                    text = TimeFormatter.formatDateTime(entry.timestamp),
                    style = MonoTextStyle.copy(color = MutedInk)
                )
            }

            Text(
                text = entry.summary,
                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium),
                color = if (entry.isSuccess) InkBlack else AlertRed
            )

            if (!entry.deviceName.isNullOrBlank() || !entry.uid.isNullOrBlank()) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (!entry.deviceName.isNullOrBlank()) {
                        Text(
                            text = "设备：${entry.deviceName}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MutedInk
                        )
                    }
                    if (!entry.uid.isNullOrBlank()) {
                        Text(
                            text = entry.uid,
                            style = MonoTextStyle.copy(color = InkBlack)
                        )
                    }
                }
            }

            if (!entry.payload.isNullOrBlank()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .background(CodeSurface)
                        .border(1.dp, SubtleBorder, RoundedCornerShape(8.dp))
                        .padding(10.dp)
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = "报文内容",
                                style = MaterialTheme.typography.labelSmall,
                                color = MutedInk
                            )
                            entry.byteCount?.let { bytes ->
                                Text(
                                    text = "$bytes Bytes",
                                    style = MonoTextStyle.copy(color = MutedInk)
                                )
                            }
                        }
                        Text(
                            text = entry.payload,
                            style = MonoTextStyle.copy(color = InkBlack),
                            maxLines = 3,
                            overflow = TextOverflow.Ellipsis
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
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Icon(
                        imageVector = Icons.Filled.Code,
                        contentDescription = null,
                        tint = TechBlue,
                        modifier = Modifier.size(15.dp)
                    )
                    Text(
                        text = "查看 HEX 与 ASCII 详情",
                        style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Medium),
                        color = TechBlue
                    )
                }
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                    contentDescription = "查看日志详情",
                    tint = TechBlue,
                    modifier = Modifier.size(18.dp)
                )
            }
        }
    }
}

@Composable
private fun LogDetailDialog(
    entry: NfcLogEntry,
    onCopyFeedback: (String) -> Unit,
    onDismiss: () -> Unit
) {
    @Suppress("DEPRECATION")
    val clipboardManager = LocalClipboardManager.current
    val badgeColors = logBadgeColors(entry.type)
    val (hexDetail, asciiDetail) = remember(entry) {
        NfcHelper.resolveLogHexAndAscii(entry)
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 20.dp),
            contentAlignment = Alignment.Center
        ) {
            val dialogMaxHeight = maxHeight * 0.90f
            Surface(
                modifier = Modifier
                    .widthIn(max = 620.dp)
                    .fillMaxWidth()
                    .heightIn(max = dialogMaxHeight)
                    .testTag("log_detail_dialog"),
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
                            .padding(start = 18.dp, end = 8.dp, top = 14.dp, bottom = 10.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(
                            modifier = Modifier.weight(1f),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Surface(
                                    color = badgeColors.bg,
                                    shape = RoundedCornerShape(6.dp),
                                    border = BorderStroke(1.dp, badgeColors.border)
                                ) {
                                    Text(
                                        text = entry.type.label,
                                        style = MaterialTheme.typography.labelSmall.copy(
                                            fontWeight = FontWeight.SemiBold
                                        ),
                                        color = badgeColors.fg,
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                                    )
                                }
                                Text(
                                    text = "日志详情",
                                    style = MaterialTheme.typography.titleMedium,
                                    color = InkBlack
                                )
                            }
                            Text(
                                text = TimeFormatter.formatDateTime(entry.timestamp),
                                style = MonoTextStyle.copy(color = MutedInk)
                            )
                        }
                        IconButton(
                            onClick = onDismiss,
                            modifier = Modifier.testTag("log_detail_close_button")
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Close,
                                contentDescription = "关闭日志详情",
                                tint = MutedInk
                            )
                        }
                    }

                    HorizontalDivider(color = SubtleBorder, thickness = 1.dp)

                    // Scrollable Body
                    Column(
                        modifier = Modifier
                            .weight(1f, fill = false)
                            .verticalScroll(rememberScrollState())
                            .padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        // Summary & Device Meta Card
                        Surface(
                            color = SoftSurfaceVariant,
                            shape = RoundedCornerShape(10.dp),
                            border = BorderStroke(1.dp, SubtleBorder),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(
                                modifier = Modifier.padding(12.dp),
                                verticalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Text(
                                    text = entry.summary,
                                    style = MaterialTheme.typography.bodyMedium.copy(
                                        fontWeight = FontWeight.SemiBold
                                    ),
                                    color = if (entry.isSuccess) InkBlack else AlertRed
                                )
                                if (!entry.deviceName.isNullOrBlank()) {
                                    Text(
                                        text = "设备名称：${entry.deviceName}",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MutedInk
                                    )
                                }
                                if (!entry.uid.isNullOrBlank()) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            text = "UID：${entry.uid}",
                                            style = MonoTextStyle.copy(color = InkBlack),
                                            modifier = Modifier.weight(1f)
                                        )
                                        IconButton(
                                            onClick = {
                                                clipboardManager.setText(AnnotatedString(entry.uid))
                                                onCopyFeedback("已复制 UID: ${entry.uid}")
                                            },
                                            modifier = Modifier.size(32.dp)
                                        ) {
                                            Icon(
                                                imageVector = Icons.Filled.ContentCopy,
                                                contentDescription = "复制 UID",
                                                tint = MutedInk,
                                                modifier = Modifier.size(16.dp)
                                            )
                                        }
                                    }
                                }
                                entry.byteCount?.let { bytes ->
                                    Text(
                                        text = "数据长度：$bytes Bytes",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MutedInk
                                    )
                                }
                            }
                        }

                        // Decoded Payload Block
                        if (!entry.payload.isNullOrBlank()) {
                            LogSectionCard(
                                title = "明文 / 解析报文",
                                copyLabel = "复制报文",
                                copyTag = "log_detail_copy_payload_button",
                                onCopy = {
                                    clipboardManager.setText(AnnotatedString(entry.payload))
                                    onCopyFeedback("已复制报文内容")
                                }
                            ) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(CodeSurface)
                                        .border(1.dp, SubtleBorder, RoundedCornerShape(8.dp))
                                        .padding(10.dp)
                                ) {
                                    Text(
                                        text = entry.payload,
                                        style = MonoTextStyle.copy(color = InkBlack),
                                        modifier = Modifier.testTag("log_detail_payload_text")
                                    )
                                }
                            }
                        }

                        // Detailed HEX Data Block
                        LogSectionCard(
                            title = "HEX 十六进制数据",
                            copyLabel = "复制 HEX",
                            copyTag = "log_detail_copy_hex_button",
                            onCopy = {
                                clipboardManager.setText(AnnotatedString(hexDetail))
                                onCopyFeedback("已复制 HEX 数据")
                            }
                        ) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(CodeSurface)
                                    .border(1.dp, SubtleBorder, RoundedCornerShape(8.dp))
                                    .horizontalScroll(rememberScrollState())
                                    .padding(10.dp)
                            ) {
                                Text(
                                    text = hexDetail,
                                    style = MonoTextStyle.copy(
                                        fontSize = 12.sp,
                                        lineHeight = 18.sp,
                                        color = InkBlack
                                    ),
                                    modifier = Modifier.testTag("log_detail_hex_text")
                                )
                            }
                        }

                        // Detailed ASCII Data Block
                        LogSectionCard(
                            title = "ASCII 文本数据（不可打印字节以 . 表示）",
                            copyLabel = "复制 ASCII",
                            copyTag = "log_detail_copy_ascii_button",
                            onCopy = {
                                clipboardManager.setText(AnnotatedString(asciiDetail))
                                onCopyFeedback("已复制 ASCII 数据")
                            }
                        ) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(CodeSurface)
                                    .border(1.dp, SubtleBorder, RoundedCornerShape(8.dp))
                                    .padding(10.dp)
                            ) {
                                Text(
                                    text = asciiDetail,
                                    style = MonoTextStyle.copy(
                                        fontSize = 12.sp,
                                        lineHeight = 18.sp,
                                        color = TechBlue
                                    ),
                                    modifier = Modifier.testTag("log_detail_ascii_text")
                                )
                            }
                        }
                    }

                    HorizontalDivider(color = SubtleBorder, thickness = 1.dp)

                    // Pinned Footer
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        OutlinedButton(
                            onClick = {
                                val full = buildLogDetailReport(entry, hexDetail, asciiDetail)
                                clipboardManager.setText(AnnotatedString(full))
                                onCopyFeedback("已复制完整日志报告")
                            },
                            border = BorderStroke(1.dp, TechBlueBorder),
                            colors = ButtonDefaults.outlinedButtonColors(
                                containerColor = TechBlueContainer,
                                contentColor = TechBlue
                            ),
                            modifier = Modifier
                                .weight(1f)
                                .height(46.dp)
                                .testTag("log_detail_copy_all_button")
                        ) {
                            Icon(
                                imageVector = Icons.Filled.ContentCopy,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(Modifier.width(6.dp))
                            Text("复制完整日志")
                        }
                        Button(
                            onClick = onDismiss,
                            colors = ButtonDefaults.buttonColors(
                                containerColor = TechBlue,
                                contentColor = PureWhiteSurface
                            ),
                            modifier = Modifier
                                .weight(1f)
                                .height(46.dp)
                                .testTag("log_detail_confirm_close_button")
                        ) {
                            Text("关闭")
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun LogSectionCard(
    title: String,
    copyLabel: String,
    copyTag: String,
    onCopy: () -> Unit,
    content: @Composable () -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold),
                color = InkBlack,
                modifier = Modifier.weight(1f)
            )
            TextButton(
                onClick = onCopy,
                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                modifier = Modifier
                    .height(32.dp)
                    .testTag(copyTag)
            ) {
                Icon(
                    imageVector = Icons.Filled.ContentCopy,
                    contentDescription = null,
                    tint = TechBlue,
                    modifier = Modifier.size(14.dp)
                )
                Spacer(Modifier.width(4.dp))
                Text(copyLabel, style = MaterialTheme.typography.labelSmall, color = TechBlue)
            }
        }
        content()
    }
}

private data class LogBadgeColors(
    val bg: androidx.compose.ui.graphics.Color,
    val border: androidx.compose.ui.graphics.Color,
    val fg: androidx.compose.ui.graphics.Color
)

private fun logBadgeColors(type: NfcLogType): LogBadgeColors = when (type) {
    NfcLogType.READ_UID, NfcLogType.READ_TAG -> LogBadgeColors(
        bg = TechBlueContainer,
        border = TechBlueBorder,
        fg = TechBlue
    )
    NfcLogType.WRITE_WIFI -> LogBadgeColors(
        bg = ProvisionedGreenBg,
        border = ProvisionedGreenBorder,
        fg = ProvisionedGreen
    )
    NfcLogType.CLEAR_TAG -> LogBadgeColors(
        bg = com.example.ui.theme.PendingAmberBg,
        border = com.example.ui.theme.PendingAmberBorder,
        fg = com.example.ui.theme.PendingAmber
    )
    NfcLogType.ERROR -> LogBadgeColors(
        bg = AlertRedBg,
        border = AlertRedBorder,
        fg = AlertRed
    )
}

private fun buildLogDetailReport(
    entry: NfcLogEntry,
    hexDetail: String,
    asciiDetail: String
): String {
    return buildString {
        appendLine("=== 富创通 IST NFC 读写日志详情 ===")
        appendLine("操作类型: ${entry.type.label}")
        appendLine("记录时间: ${TimeFormatter.formatDateTime(entry.timestamp)}")
        appendLine("状态: ${if (entry.isSuccess) "成功" else "异常"}")
        entry.deviceName?.let { appendLine("设备名称: $it") }
        entry.uid?.let { appendLine("硬件 UID: $it") }
        entry.byteCount?.let { appendLine("字节数: $it Bytes") }
        appendLine("摘要: ${entry.summary}")
        if (!entry.payload.isNullOrBlank()) {
            appendLine("--- 明文/解析报文 ---")
            appendLine(entry.payload)
        }
        appendLine("--- HEX 数据 ---")
        appendLine(hexDetail)
        appendLine("--- ASCII 数据 ---")
        appendLine(asciiDetail)
    }.trimEnd()
}
