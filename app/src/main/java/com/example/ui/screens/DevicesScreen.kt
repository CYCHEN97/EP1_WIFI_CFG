package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.DevicesOther
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Nfc
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.model.BoundDevice
import com.example.model.DeviceStatusFilter
import com.example.model.ProvisionStatus
import com.example.model.TimeFormatter
import com.example.ui.theme.AlertRed
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
import com.example.viewmodel.DeviceStats

@Composable
fun DevicesScreen(
    devices: List<BoundDevice>,
    stats: DeviceStats,
    searchQuery: String,
    statusFilter: DeviceStatusFilter,
    onSearchQueryChange: (String) -> Unit,
    onStatusFilterChange: (DeviceStatusFilter) -> Unit,
    onOpenBindDialog: () -> Unit,
    onDeviceClick: (BoundDevice) -> Unit,
    onRenameDevice: (String, String) -> Boolean,
    onDeleteDevice: (String) -> Unit,
    onCopyUid: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    @Suppress("DEPRECATION")
    val clipboardManager = LocalClipboardManager.current
    var renamingTarget by remember { mutableStateOf<BoundDevice?>(null) }
    var renameInput by remember { mutableStateOf("") }
    var renameError by remember { mutableStateOf<String?>(null) }
    var deletingTarget by remember { mutableStateOf<BoundDevice?>(null) }

    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.TopCenter
    ) {
        if (stats.total == 0) {
            EmptyDevicesState(
                onScanBindClick = onOpenBindDialog,
                modifier = Modifier
                    .widthIn(max = 600.dp)
                    .fillMaxSize()
                    .padding(20.dp)
            )
        } else {
            LazyColumn(
                modifier = Modifier
                    .widthIn(max = 680.dp)
                    .fillMaxSize(),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 14.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Statistics summary row
                item {
                    DeviceStatsRow(stats = stats)
                }

                // Search bar
                item {
                    OutlinedTextField(
                        value = searchQuery,
                        onValueChange = onSearchQueryChange,
                        placeholder = { Text("搜索设备名称、UID 或已配 Wi-Fi...") },
                        leadingIcon = {
                            Icon(
                                imageVector = Icons.Filled.Search,
                                contentDescription = "搜索",
                                tint = MutedInk
                            )
                        },
                        trailingIcon = {
                            if (searchQuery.isNotEmpty()) {
                                IconButton(
                                    onClick = { onSearchQueryChange("") },
                                    modifier = Modifier.testTag("clear_search_button")
                                ) {
                                    Icon(
                                        imageVector = Icons.Filled.Clear,
                                        contentDescription = "清除搜索",
                                        tint = MutedInk
                                    )
                                }
                            }
                        },
                        singleLine = true,
                        shape = RoundedCornerShape(10.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedContainerColor = PureWhiteSurface,
                            unfocusedContainerColor = PureWhiteSurface,
                            focusedBorderColor = TechBlue,
                            unfocusedBorderColor = SubtleBorder
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("device_search_input")
                    )
                }

                // Filter chips
                item {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        DeviceStatusFilter.entries.forEach { filter ->
                            val selected = statusFilter == filter
                            val countLabel = when (filter) {
                                DeviceStatusFilter.ALL -> "${filter.label} (${stats.total})"
                                DeviceStatusFilter.PENDING -> "${filter.label} (${stats.pending})"
                                DeviceStatusFilter.PROVISIONED -> "${filter.label} (${stats.provisioned})"
                            }
                            Surface(
                                onClick = { onStatusFilterChange(filter) },
                                shape = RoundedCornerShape(8.dp),
                                color = if (selected) TechBlueContainer else PureWhiteSurface,
                                border = BorderStroke(
                                    1.dp,
                                    if (selected) TechBlue else SubtleBorder
                                ),
                                modifier = Modifier
                                    .height(40.dp)
                                    .testTag("filter_chip_${filter.name}")
                            ) {
                                Box(
                                    contentAlignment = Alignment.Center,
                                    modifier = Modifier.padding(horizontal = 14.dp)
                                ) {
                                    Text(
                                        text = countLabel,
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

                if (devices.isEmpty()) {
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
                                    .padding(24.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Text(
                                    text = "未找到匹配的设备",
                                    style = MaterialTheme.typography.titleSmall,
                                    color = InkBlack
                                )
                                Text(
                                    text = "尝试更换搜索关键词或切换状态筛选条件",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MutedInk
                                )
                                OutlinedButton(
                                    onClick = {
                                        onSearchQueryChange("")
                                        onStatusFilterChange(DeviceStatusFilter.ALL)
                                    },
                                    border = BorderStroke(1.dp, SubtleBorder)
                                ) {
                                    Text("重置筛选条件", color = InkBlack)
                                }
                            }
                        }
                    }
                } else {
                    items(devices, key = { it.id }) { device ->
                        DeviceCard(
                            device = device,
                            onProvisionClick = { onDeviceClick(device) },
                            onRenameClick = {
                                renamingTarget = device
                                renameInput = device.name
                                renameError = null
                            },
                            onDeleteClick = { deletingTarget = device },
                            onCopyUidClick = {
                                clipboardManager.setText(AnnotatedString(device.uid))
                                onCopyUid(device.uid)
                            }
                        )
                    }
                }
            }
        }
    }

    // Rename Dialog
    renamingTarget?.let { target ->
        AlertDialog(
            onDismissRequest = { renamingTarget = null },
            containerColor = PureWhiteSurface,
            title = {
                Text(
                    text = "重命名设备",
                    style = MaterialTheme.typography.titleMedium,
                    color = InkBlack
                )
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = "UID: ${target.uid}",
                        style = MonoTextStyle.copy(color = MutedInk)
                    )
                    OutlinedTextField(
                        value = renameInput,
                        onValueChange = {
                            renameInput = it
                            renameError = null
                        },
                        label = { Text("设备名称") },
                        isError = renameError != null,
                        supportingText = renameError?.let { err -> { Text(err) } },
                        singleLine = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("rename_device_input")
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (onRenameDevice(target.id, renameInput)) {
                            renamingTarget = null
                        } else {
                            renameError = "设备名称不能为空"
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = TechBlue),
                    modifier = Modifier.testTag("rename_confirm_button")
                ) {
                    Text("保存")
                }
            },
            dismissButton = {
                TextButton(onClick = { renamingTarget = null }) {
                    Text("取消", color = MutedInk)
                }
            }
        )
    }

    // Delete / Unbind Confirmation Dialog
    deletingTarget?.let { target ->
        AlertDialog(
            onDismissRequest = { deletingTarget = null },
            containerColor = PureWhiteSurface,
            title = {
                Text(
                    text = "解绑设备",
                    style = MaterialTheme.typography.titleMedium,
                    color = InkBlack
                )
            },
            text = {
                Text(
                    text = "确定要删除并解绑「${target.name}」(${target.uid}) 吗？解绑后可重新刷 NFC 绑定。",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MutedInk
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        onDeleteDevice(target.id)
                        deletingTarget = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = AlertRed),
                    modifier = Modifier.testTag("delete_confirm_button")
                ) {
                    Text("确认解绑")
                }
            },
            dismissButton = {
                TextButton(onClick = { deletingTarget = null }) {
                    Text("取消", color = MutedInk)
                }
            }
        )
    }
}

@Composable
private fun DeviceStatsRow(stats: DeviceStats) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        StatBox(
            label = "已绑定设备",
            value = "${stats.total}",
            accentColor = InkBlack,
            modifier = Modifier.weight(1f)
        )
        StatBox(
            label = "已配网",
            value = "${stats.provisioned}",
            accentColor = ProvisionedGreen,
            modifier = Modifier.weight(1f)
        )
        StatBox(
            label = "待配网",
            value = "${stats.pending}",
            accentColor = PendingAmber,
            modifier = Modifier.weight(1f)
        )
    }
}

@Composable
private fun StatBox(
    label: String,
    value: String,
    accentColor: Color,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier,
        color = PureWhiteSurface,
        shape = RoundedCornerShape(10.dp),
        border = BorderStroke(1.dp, SubtleBorder)
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = MutedInk
            )
            Text(
                text = value,
                style = MaterialTheme.typography.titleLarge.copy(color = accentColor)
            )
        }
    }
}

@Composable
private fun DeviceCard(
    device: BoundDevice,
    onProvisionClick: () -> Unit,
    onRenameClick: () -> Unit,
    onDeleteClick: () -> Unit,
    onCopyUidClick: () -> Unit
) {
    val isProvisioned = device.status == ProvisionStatus.PROVISIONED

    Surface(
        onClick = onProvisionClick,
        color = PureWhiteSurface,
        shape = RoundedCornerShape(12.dp),
        border = BorderStroke(1.dp, SubtleBorder),
        modifier = Modifier
            .fillMaxWidth()
            .testTag("device_card_${device.uid}")
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // Title + Status Badge
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = device.name,
                    style = MaterialTheme.typography.titleMedium,
                    color = InkBlack,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                Spacer(Modifier.width(8.dp))
                Surface(
                    color = if (isProvisioned) ProvisionedGreenBg else PendingAmberBg,
                    shape = RoundedCornerShape(999.dp),
                    border = BorderStroke(
                        1.dp,
                        if (isProvisioned) ProvisionedGreenBorder else PendingAmberBorder
                    )
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Icon(
                            imageVector = if (isProvisioned) {
                                Icons.Filled.CheckCircle
                            } else {
                                Icons.Filled.Schedule
                            },
                            contentDescription = null,
                            tint = if (isProvisioned) ProvisionedGreen else PendingAmber,
                            modifier = Modifier.size(14.dp)
                        )
                        Text(
                            text = device.status.label,
                            style = MaterialTheme.typography.labelMedium.copy(
                                fontWeight = FontWeight.SemiBold
                            ),
                            color = if (isProvisioned) ProvisionedGreen else PendingAmber
                        )
                    }
                }
            }

            // UID row
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp))
                    .background(SoftSurfaceVariant)
                    .border(1.dp, SubtleBorder, RoundedCornerShape(8.dp))
                    .padding(start = 10.dp, end = 4.dp, top = 4.dp, bottom = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "芯片 UID",
                        style = MaterialTheme.typography.labelSmall,
                        color = MutedInk
                    )
                    Text(
                        text = device.uid,
                        style = MonoTextStyle.copy(color = InkBlack)
                    )
                }
                IconButton(
                    onClick = onCopyUidClick,
                    modifier = Modifier.testTag("copy_uid_button_${device.uid}")
                ) {
                    Icon(
                        imageVector = Icons.Filled.ContentCopy,
                        contentDescription = "复制 UID",
                        tint = MutedInk,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }

            // Metadata: bound time & provisioned Wi-Fi
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    text = "绑定时间：${TimeFormatter.formatDateTime(device.boundAt)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MutedInk
                )
                if (isProvisioned && !device.wifiSsid.isNullOrBlank()) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Wifi,
                            contentDescription = null,
                            tint = ProvisionedGreen,
                            modifier = Modifier.size(15.dp)
                        )
                        Text(
                            text = "已配 Wi-Fi：${device.wifiSsid}",
                            style = MaterialTheme.typography.bodySmall.copy(
                                fontWeight = FontWeight.SemiBold,
                                color = InkBlack
                            ),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        device.provisionedAt?.let { pTime ->
                            Text(
                                text = "· ${TimeFormatter.formatShortDateTime(pTime)}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MutedInk
                            )
                        }
                    }
                }
            }

            HorizontalDivider(color = SubtleBorder, thickness = 1.dp)

            // Action row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    TextButton(
                        onClick = onRenameClick,
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 6.dp),
                        modifier = Modifier.testTag("rename_button_${device.uid}")
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Edit,
                            contentDescription = null,
                            tint = MutedInk,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(Modifier.width(4.dp))
                        Text("重命名", color = InkBlack, style = MaterialTheme.typography.labelMedium)
                    }
                    TextButton(
                        onClick = onDeleteClick,
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 6.dp),
                        modifier = Modifier.testTag("delete_button_${device.uid}")
                    ) {
                        Icon(
                            imageVector = Icons.Filled.DeleteOutline,
                            contentDescription = null,
                            tint = AlertRed,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(Modifier.width(4.dp))
                        Text("解绑", color = AlertRed, style = MaterialTheme.typography.labelMedium)
                    }
                }

                if (isProvisioned) {
                    OutlinedButton(
                        onClick = onProvisionClick,
                        border = BorderStroke(1.dp, TechBlueBorder),
                        colors = ButtonDefaults.outlinedButtonColors(
                            containerColor = TechBlueContainer,
                            contentColor = TechBlue
                        ),
                        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp),
                        modifier = Modifier
                            .height(40.dp)
                            .testTag("reprovision_button_${device.uid}")
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Nfc,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(Modifier.width(6.dp))
                        Text("重新配网", style = MaterialTheme.typography.labelLarge)
                    }
                } else {
                    Button(
                        onClick = onProvisionClick,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = TechBlue,
                            contentColor = PureWhiteSurface
                        ),
                        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp),
                        modifier = Modifier
                            .height(40.dp)
                            .testTag("provision_button_${device.uid}")
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Nfc,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(Modifier.width(6.dp))
                        Text("去配网", style = MaterialTheme.typography.labelLarge)
                    }
                }
            }
        }
    }
}

@Composable
private fun EmptyDevicesState(
    onScanBindClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier,
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
                    .padding(horizontal = 24.dp, vertical = 32.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .size(72.dp)
                        .clip(CircleShape)
                        .background(TechBlueContainer)
                        .border(1.dp, TechBlueBorder, CircleShape)
                ) {
                    Icon(
                        imageVector = Icons.Filled.DevicesOther,
                        contentDescription = null,
                        tint = TechBlue,
                        modifier = Modifier.size(34.dp)
                    )
                }

                Text(
                    text = "暂无已绑定商显设备",
                    style = MaterialTheme.typography.titleMedium,
                    color = InkBlack,
                    textAlign = TextAlign.Center
                )

                Text(
                    text = "当前列表为空，所有数据仅保存在本机本地。请点击下方按钮，将手机靠近设备 FM11NT083C NFC 感应区读取 7 字节 UID 并完成绑定。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MutedInk,
                    textAlign = TextAlign.Center
                )

                Spacer(Modifier.height(4.dp))

                Button(
                    onClick = onScanBindClick,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = TechBlue,
                        contentColor = PureWhiteSurface
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                        .testTag("empty_scan_bind_button")
                ) {
                    Icon(
                        imageVector = Icons.Filled.Nfc,
                        contentDescription = null,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(Modifier.width(8.dp))
                    Text("扫描 NFC 绑定设备")
                }
            }
        }
    }
}
