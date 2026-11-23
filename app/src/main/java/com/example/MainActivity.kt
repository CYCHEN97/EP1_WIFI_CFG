package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ListAlt
import androidx.compose.material.icons.automirrored.outlined.ListAlt
import androidx.compose.material.icons.filled.DeveloperBoard
import androidx.compose.material.icons.filled.Devices
import androidx.compose.material.icons.filled.MenuBook
import androidx.compose.material.icons.filled.Nfc
import androidx.compose.material.icons.filled.WarningAmber
import androidx.compose.material.icons.outlined.Devices
import androidx.compose.material.icons.outlined.MenuBook
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.data.ProvisioningRepository
import com.example.model.AppTab
import com.example.model.NfcAvailability
import com.example.nfc.NfcHelper
import com.example.ui.components.BindDeviceDialog
import com.example.ui.components.ProvisionDeviceDialog
import com.example.ui.screens.DevicesScreen
import com.example.ui.screens.GuideScreen
import com.example.ui.screens.LogsScreen
import com.example.ui.screens.TagInspectorScreen
import com.example.ui.theme.AlertRed
import com.example.ui.theme.AlertRedBg
import com.example.ui.theme.AlertRedBorder
import com.example.ui.theme.InkBlack
import com.example.ui.theme.IstNfcProvisionerTheme
import com.example.ui.theme.MutedInk
import com.example.ui.theme.PendingAmber
import com.example.ui.theme.PendingAmberBg
import com.example.ui.theme.PendingAmberBorder
import com.example.ui.theme.PureWhiteSurface
import com.example.ui.theme.SubtleBorder
import com.example.ui.theme.TechBlue
import com.example.ui.theme.TechBlueContainer
import com.example.ui.theme.WarmGrayBackground
import com.example.viewmodel.ProvisionerViewModel

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            IstNfcProvisionerTheme {
                val context = LocalContext.current
                val repository = remember(context) { ProvisioningRepository(context) }
                val viewModel: ProvisionerViewModel = viewModel(
                    factory = ProvisionerViewModel.Factory(repository)
                )
                ProvisionerApp(viewModel = viewModel)
            }
        }
    }
}

@Composable
fun ProvisionerApp(viewModel: ProvisionerViewModel) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    val allDevices by viewModel.devices.collectAsStateWithLifecycle()
    val filteredDevices by viewModel.filteredDevices.collectAsStateWithLifecycle()
    val stats by viewModel.deviceStats.collectAsStateWithLifecycle()
    val logs by viewModel.logs.collectAsStateWithLifecycle()
    val activeTab by viewModel.activeTab.collectAsStateWithLifecycle()
    val searchQuery by viewModel.searchQuery.collectAsStateWithLifecycle()
    val statusFilter by viewModel.statusFilter.collectAsStateWithLifecycle()
    val nfcAvailability by viewModel.nfcAvailability.collectAsStateWithLifecycle()
    val bindDialogState by viewModel.bindDialog.collectAsStateWithLifecycle()
    val provisionDialogState by viewModel.provisionDialog.collectAsStateWithLifecycle()
    val tagInspectorState by viewModel.tagInspector.collectAsStateWithLifecycle()
    val snackbarMessage by viewModel.snackbarMessage.collectAsStateWithLifecycle()

    val snackbarHostState = remember { SnackbarHostState() }

    // Refresh NFC status whenever activity resumes (e.g. returning from system NFC settings)
    DisposableEffect(lifecycleOwner, context) {
        viewModel.updateNfcAvailability(NfcHelper.getNfcAvailability(context))
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                viewModel.updateNfcAvailability(NfcHelper.getNfcAvailability(context))
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    LaunchedEffect(snackbarMessage) {
        snackbarMessage?.let { msg ->
            snackbarHostState.showSnackbar(msg)
            viewModel.clearMessage()
        }
    }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        containerColor = WarmGrayBackground,
        contentWindowInsets = WindowInsets.navigationBars,
        snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
        topBar = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(PureWhiteSurface)
                    .statusBarsPadding()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        modifier = Modifier.weight(1f),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier
                                .size(38.dp)
                                .background(TechBlue, RoundedCornerShape(10.dp))
                        ) {
                            Icon(
                                imageVector = Icons.Filled.DeveloperBoard,
                                contentDescription = null,
                                tint = PureWhiteSurface,
                                modifier = Modifier.size(22.dp)
                            )
                        }
                        Column {
                            Text(
                                text = "富创通 IST 配网助手",
                                style = MaterialTheme.typography.titleMedium.copy(
                                    fontWeight = FontWeight.Bold
                                ),
                                color = InkBlack,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = "商显本地 NFC 调试 · FM11NT083C",
                                style = MaterialTheme.typography.labelSmall,
                                color = MutedInk,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }

                    Spacer(Modifier.width(8.dp))

                    Button(
                        onClick = { viewModel.openBindDialog() },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = TechBlue,
                            contentColor = PureWhiteSurface
                        ),
                        shape = RoundedCornerShape(10.dp),
                        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 10.dp),
                        modifier = Modifier
                            .height(44.dp)
                            .testTag("top_scan_bind_button")
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Nfc,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(Modifier.width(6.dp))
                        Text(
                            text = "刷 NFC 绑定",
                            style = MaterialTheme.typography.labelLarge
                        )
                    }
                }

                HorizontalDivider(color = SubtleBorder, thickness = 1.dp)

                // NFC status alert banner when hardware is unsupported or disabled
                AnimatedVisibility(visible = nfcAvailability != NfcAvailability.ENABLED) {
                    NfcStatusBanner(
                        availability = nfcAvailability,
                        onOpenSettings = { NfcHelper.openNfcSettings(context) }
                    )
                }
            }
        },
        bottomBar = {
            Column(modifier = Modifier.background(PureWhiteSurface)) {
                HorizontalDivider(color = SubtleBorder, thickness = 1.dp)
                NavigationBar(
                    containerColor = PureWhiteSurface,
                    tonalElevation = 0.dp
                ) {
                    AppTab.entries.forEach { tab ->
                        val selected = activeTab == tab
                        val icon = when (tab) {
                            AppTab.DEVICES -> if (selected) Icons.Filled.Devices else Icons.Outlined.Devices
                            AppTab.READ_TAG -> Icons.Filled.Nfc
                            AppTab.LOGS -> if (selected) Icons.AutoMirrored.Filled.ListAlt else Icons.AutoMirrored.Outlined.ListAlt
                            AppTab.GUIDE -> if (selected) Icons.Filled.MenuBook else Icons.Outlined.MenuBook
                        }
                        NavigationBarItem(
                            selected = selected,
                            onClick = { viewModel.selectTab(tab) },
                            icon = {
                                Icon(
                                    imageVector = icon,
                                    contentDescription = tab.label
                                )
                            },
                            label = {
                                Text(
                                    text = tab.label,
                                    style = MaterialTheme.typography.labelMedium
                                )
                            },
                            colors = NavigationBarItemDefaults.colors(
                                selectedIconColor = TechBlue,
                                selectedTextColor = TechBlue,
                                unselectedIconColor = MutedInk,
                                unselectedTextColor = MutedInk,
                                indicatorColor = TechBlueContainer
                            ),
                            modifier = Modifier.testTag("nav_tab_${tab.name}")
                        )
                    }
                }
            }
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            when (activeTab) {
                AppTab.DEVICES -> {
                    DevicesScreen(
                        devices = filteredDevices,
                        stats = stats,
                        searchQuery = searchQuery,
                        statusFilter = statusFilter,
                        onSearchQueryChange = viewModel::updateSearchQuery,
                        onStatusFilterChange = viewModel::updateStatusFilter,
                        onOpenBindDialog = viewModel::openBindDialog,
                        onDeviceClick = viewModel::openProvisionDialog,
                        onRenameDevice = viewModel::renameDevice,
                        onDeleteDevice = viewModel::deleteDevice,
                        onCopyUid = { uid ->
                            viewModel.showMessage("已复制 UID: $uid")
                        }
                    )
                }

                AppTab.READ_TAG -> {
                    TagInspectorScreen(
                        state = tagInspectorState,
                        devices = allDevices,
                        nfcAvailability = nfcAvailability,
                        isModalBlockingNfc = bindDialogState.isOpen || provisionDialogState.isOpen,
                        onToggleListening = viewModel::setTagInspectorListening,
                        onToggleEraseMode = viewModel::setTagInspectorEraseMode,
                        onTagReadOutcome = viewModel::onTagInspected,
                        onTagCleared = viewModel::onTagClearedInInspector,
                        onClearResult = viewModel::clearTagInspectorResult,
                        onQuickBindUid = viewModel::openBindWithScannedUid,
                        onCopyFeedback = viewModel::showMessage
                    )
                }

                AppTab.LOGS -> {
                    LogsScreen(
                        logs = logs,
                        onClearLogs = viewModel::clearLogs,
                        onCopyFeedback = viewModel::showMessage
                    )
                }

                AppTab.GUIDE -> {
                    GuideScreen(
                        onCodeCopied = {
                            viewModel.showMessage("已复制 ESP32S31 C 解析示例代码")
                        }
                    )
                }
            }
        }
    }

    BindDeviceDialog(
        state = bindDialogState,
        nfcAvailability = nfcAvailability,
        onTagScanned = viewModel::onTagScannedForBind,
        onScanError = viewModel::onBindScanError,
        onRescan = viewModel::resetBindScan,
        onNameChange = viewModel::updateBindDeviceName,
        onConfirmBind = viewModel::confirmBindDevice,
        onOpenNfcSettings = { NfcHelper.openNfcSettings(context) },
        onDismiss = viewModel::closeBindDialog
    )

    ProvisionDeviceDialog(
        state = provisionDialogState,
        nfcAvailability = nfcAvailability,
        onSsidChange = viewModel::updateProvisionSsid,
        onPasswordChange = viewModel::updateProvisionPassword,
        onFormatChange = viewModel::updateProvisionFormat,
        onStartWriteTap = viewModel::startWaitingForWriteTap,
        onStartClearTap = viewModel::startWaitingForClearTap,
        onBackToForm = viewModel::backToProvisionForm,
        onWriteResult = viewModel::onWriteResult,
        onClearResult = viewModel::onClearResult,
        onOpenNfcSettings = { NfcHelper.openNfcSettings(context) },
        onDismiss = viewModel::closeProvisionDialog
    )
}

@Composable
private fun NfcStatusBanner(
    availability: NfcAvailability,
    onOpenSettings: () -> Unit
) {
    val isUnsupported = availability == NfcAvailability.NOT_SUPPORTED
    Surface(
        color = if (isUnsupported) AlertRedBg else PendingAmberBg,
        border = BorderStroke(1.dp, if (isUnsupported) AlertRedBorder else PendingAmberBorder),
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp)
            .testTag("nfc_status_banner"),
        shape = RoundedCornerShape(10.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                modifier = Modifier.weight(1f),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    imageVector = Icons.Filled.WarningAmber,
                    contentDescription = null,
                    tint = if (isUnsupported) AlertRed else PendingAmber,
                    modifier = Modifier.size(20.dp)
                )
                Text(
                    text = if (isUnsupported) {
                        "当前手机不支持 NFC 硬件，仅可浏览本地记录"
                    } else {
                        "NFC 功能未开启，请开启后刷卡配网"
                    },
                    style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Medium),
                    color = InkBlack
                )
            }
            if (!isUnsupported) {
                Spacer(Modifier.width(8.dp))
                OutlinedButton(
                    onClick = onOpenSettings,
                    border = BorderStroke(1.dp, PendingAmber),
                    colors = ButtonDefaults.outlinedButtonColors(
                        containerColor = PureWhiteSurface,
                        contentColor = PendingAmber
                    ),
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                    modifier = Modifier
                        .height(36.dp)
                        .testTag("banner_open_nfc_settings_button")
                ) {
                    Text("开启 NFC", style = MaterialTheme.typography.labelMedium)
                }
            }
        }
    }
}
