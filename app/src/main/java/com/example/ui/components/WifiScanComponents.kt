package com.example.ui.components

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material.icons.filled.Nfc
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material.icons.filled.WifiFind
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.model.ScannedWifiNetwork
import com.example.model.WifiScanState
import com.example.model.WifiScanStatus
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
import com.example.ui.theme.PureWhiteSurface
import com.example.ui.theme.SoftSurfaceVariant
import com.example.ui.theme.SubtleBorder
import com.example.ui.theme.TechBlue
import com.example.ui.theme.TechBlueBorder
import com.example.ui.theme.TechBlueContainer

@Composable
fun WaitingWritePulseCard(
    title: String = "请再次刷取 NFC 写入配网信息",
    subtitle: String = "将手机贴紧目标设备感应区并保持 1~2 秒，直到提示配网成功",
    accentColor: Color = TechBlue,
    containerColor: Color = TechBlueContainer,
    borderColor: Color = TechBlueBorder
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
fun ErrorBanner(message: String) {
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
fun DetailRow(label: String, value: String, mono: Boolean = false) {
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
        Spacer(Modifier.width(12.dp))
        Text(
            text = value,
            style = if (mono) {
                MonoTextStyle.copy(color = InkBlack)
            } else {
                MaterialTheme.typography.bodySmall.copy(
                    fontWeight = FontWeight.SemiBold,
                    color = InkBlack
                )
            }
        )
    }
}

@Composable
fun WifiAutoScanSection(
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
fun WifiPickerDialog(
    scanState: WifiScanState,
    selectedSsid: String,
    onSelectNetwork: (ScannedWifiNetwork) -> Unit,
    onRescanClick: () -> Unit,
    onDismiss: () -> Unit
) {
    var query by remember { mutableStateOf("") }
    var bandFilter by remember { mutableStateOf("ALL") }

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
