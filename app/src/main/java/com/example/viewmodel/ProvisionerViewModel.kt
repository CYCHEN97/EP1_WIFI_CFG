package com.example.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.data.ProvisioningRepository
import com.example.model.AppTab
import com.example.model.BindDialogState
import com.example.model.BoundDevice
import com.example.model.DeviceStatusFilter
import com.example.model.NdefPayloadFormat
import com.example.model.NfcAvailability
import com.example.model.NfcLogEntry
import com.example.model.NfcLogType
import com.example.model.PayloadBuilder
import com.example.model.ProvisionDialogState
import com.example.model.ProvisionStatus
import com.example.model.ProvisionStep
import com.example.model.TagInspectorState
import com.example.nfc.NfcClearResult
import com.example.nfc.NfcHelper
import com.example.nfc.NfcWriteResult
import com.example.nfc.TagReadOutcome
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import java.util.UUID

data class DeviceStats(
    val total: Int = 0,
    val provisioned: Int = 0,
    val pending: Int = 0
)

class ProvisionerViewModel(
    private val repository: ProvisioningRepository
) : ViewModel() {

    private val _devices = MutableStateFlow(repository.loadDevices())
    val devices: StateFlow<List<BoundDevice>> = _devices.asStateFlow()

    private val _logs = MutableStateFlow(repository.loadLogs())
    val logs: StateFlow<List<NfcLogEntry>> = _logs.asStateFlow()

    private val _preferredFormat = MutableStateFlow(repository.loadPreferredFormat())
    val preferredFormat: StateFlow<NdefPayloadFormat> = _preferredFormat.asStateFlow()

    private val _activeTab = MutableStateFlow(AppTab.DEVICES)
    val activeTab: StateFlow<AppTab> = _activeTab.asStateFlow()

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    private val _statusFilter = MutableStateFlow(DeviceStatusFilter.ALL)
    val statusFilter: StateFlow<DeviceStatusFilter> = _statusFilter.asStateFlow()

    private val _nfcAvailability = MutableStateFlow(NfcAvailability.DISABLED)
    val nfcAvailability: StateFlow<NfcAvailability> = _nfcAvailability.asStateFlow()

    private val _bindDialog = MutableStateFlow(BindDialogState())
    val bindDialog: StateFlow<BindDialogState> = _bindDialog.asStateFlow()

    private val _provisionDialog = MutableStateFlow(ProvisionDialogState())
    val provisionDialog: StateFlow<ProvisionDialogState> = _provisionDialog.asStateFlow()

    private val _tagInspector = MutableStateFlow(TagInspectorState())
    val tagInspector: StateFlow<TagInspectorState> = _tagInspector.asStateFlow()

    private val _snackbarMessage = MutableStateFlow<String?>(null)
    val snackbarMessage: StateFlow<String?> = _snackbarMessage.asStateFlow()

    val deviceStats: StateFlow<DeviceStats> = _devices
        .combine(_searchQuery) { list, _ ->
            val provisioned = list.count { it.status == ProvisionStatus.PROVISIONED }
            DeviceStats(
                total = list.size,
                provisioned = provisioned,
                pending = list.size - provisioned
            )
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), DeviceStats())

    val filteredDevices: StateFlow<List<BoundDevice>> = combine(
        _devices,
        _searchQuery,
        _statusFilter
    ) { list, query, filter ->
        val trimmed = query.trim().lowercase()
        list.filter { device ->
            val matchesFilter = when (filter) {
                DeviceStatusFilter.ALL -> true
                DeviceStatusFilter.PENDING -> device.status == ProvisionStatus.PENDING
                DeviceStatusFilter.PROVISIONED -> device.status == ProvisionStatus.PROVISIONED
            }
            val matchesQuery = if (trimmed.isEmpty()) {
                true
            } else {
                device.name.lowercase().contains(trimmed) ||
                    device.uid.lowercase().contains(trimmed) ||
                    (device.wifiSsid?.lowercase()?.contains(trimmed) == true)
            }
            matchesFilter && matchesQuery
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), _devices.value)

    fun updateNfcAvailability(availability: NfcAvailability) {
        _nfcAvailability.value = availability
    }

    fun selectTab(tab: AppTab) {
        _activeTab.value = tab
    }

    fun updateSearchQuery(query: String) {
        _searchQuery.value = query
    }

    fun updateStatusFilter(filter: DeviceStatusFilter) {
        _statusFilter.value = filter
    }

    fun showMessage(message: String) {
        _snackbarMessage.value = message
    }

    fun clearMessage() {
        _snackbarMessage.value = null
    }

    // --- Bind Dialog Flow ---

    fun openBindDialog() {
        _bindDialog.value = BindDialogState(
            isOpen = true,
            isScanning = true
        )
    }

    fun closeBindDialog() {
        _bindDialog.value = BindDialogState(isOpen = false)
    }

    fun resetBindScan() {
        _bindDialog.value = BindDialogState(
            isOpen = true,
            isScanning = true
        )
    }

    fun onTagScannedForBind(uid: String, byteCount: Int) {
        if (!_bindDialog.value.isOpen) return
        if (uid.isBlank()) {
            appendLog(
                NfcLogEntry(
                    id = UUID.randomUUID().toString(),
                    timestamp = System.currentTimeMillis(),
                    type = NfcLogType.ERROR,
                    uid = null,
                    deviceName = null,
                    summary = "读取 NFC 卡片 UID 失败：未能获取到有效字节序列",
                    isSuccess = false
                )
            )
            _bindDialog.update {
                it.copy(
                    isScanning = false,
                    scannedUid = null,
                    scanError = "未能读取到芯片 UID，请点击「重刷」重试"
                )
            }
            return
        }

        val existing = _devices.value.firstOrNull { it.uid.equals(uid, ignoreCase = true) }
        _bindDialog.update {
            it.copy(
                isScanning = false,
                scannedUid = uid,
                uidByteCount = byteCount,
                duplicateDeviceName = existing?.name,
                scanError = null,
                nameError = null
            )
        }
    }

    fun onBindScanError(errorMessage: String) {
        if (!_bindDialog.value.isOpen) return
        appendLog(
            NfcLogEntry(
                id = UUID.randomUUID().toString(),
                timestamp = System.currentTimeMillis(),
                type = NfcLogType.ERROR,
                uid = null,
                deviceName = null,
                summary = errorMessage,
                isSuccess = false
            )
        )
        _bindDialog.update {
            it.copy(
                isScanning = false,
                scanError = errorMessage
            )
        }
    }

    fun updateBindDeviceName(name: String) {
        _bindDialog.update {
            it.copy(
                deviceNameInput = name,
                nameError = null
            )
        }
    }

    fun confirmBindDevice() {
        val state = _bindDialog.value
        val uid = state.scannedUid ?: return
        if (state.duplicateDeviceName != null) {
            return
        }
        val trimmedName = state.deviceNameInput.trim()
        if (trimmedName.isEmpty()) {
            _bindDialog.update { it.copy(nameError = "请输入设备名称") }
            return
        }

        val existingAgain = _devices.value.firstOrNull { it.uid.equals(uid, ignoreCase = true) }
        if (existingAgain != null) {
            _bindDialog.update { it.copy(duplicateDeviceName = existingAgain.name) }
            return
        }

        val now = System.currentTimeMillis()
        val newDevice = BoundDevice(
            id = UUID.randomUUID().toString(),
            uid = uid,
            name = trimmedName,
            boundAt = now,
            status = ProvisionStatus.PENDING
        )
        val updatedDevices = listOf(newDevice) + _devices.value
        _devices.value = updatedDevices
        repository.saveDevices(updatedDevices)

        val (bindHex, bindAscii) = NfcHelper.buildBindLogHexAndAscii(uid, state.uidByteCount)
        appendLog(
            NfcLogEntry(
                id = UUID.randomUUID().toString(),
                timestamp = now,
                type = NfcLogType.READ_UID,
                uid = uid,
                deviceName = trimmedName,
                summary = "读取 FM11NT083C UID (${state.uidByteCount} B) 并绑定设备「$trimmedName」",
                payload = "UID=$uid",
                byteCount = state.uidByteCount,
                isSuccess = true,
                hexData = bindHex,
                asciiData = bindAscii
            )
        )

        _bindDialog.value = BindDialogState(isOpen = false)
        _snackbarMessage.value = "已绑定设备「$trimmedName」"
    }

    // --- Device Management ---

    fun renameDevice(deviceId: String, newName: String): Boolean {
        val trimmed = newName.trim()
        if (trimmed.isEmpty()) return false
        val updated = _devices.value.map { device ->
            if (device.id == deviceId) device.copy(name = trimmed) else device
        }
        _devices.value = updated
        repository.saveDevices(updated)
        _snackbarMessage.value = "已重命名为「$trimmed」"
        return true
    }

    fun deleteDevice(deviceId: String) {
        val target = _devices.value.firstOrNull { it.id == deviceId }
        val updated = _devices.value.filterNot { it.id == deviceId }
        _devices.value = updated
        repository.saveDevices(updated)
        if (target != null) {
            _snackbarMessage.value = "已解绑设备「${target.name}」"
        }
    }

    // --- Provisioning Dialog Flow ---

    fun openProvisionDialog(device: BoundDevice) {
        _provisionDialog.value = ProvisionDialogState(
            device = device,
            step = ProvisionStep.FORM,
            ssid = device.wifiSsid.orEmpty(),
            password = device.wifiPassword.orEmpty(),
            format = _preferredFormat.value
        )
    }

    fun closeProvisionDialog() {
        _provisionDialog.value = ProvisionDialogState()
    }

    fun updateProvisionSsid(ssid: String) {
        _provisionDialog.update {
            it.copy(ssid = ssid, ssidError = null, writeError = null)
        }
    }

    fun updateProvisionPassword(password: String) {
        _provisionDialog.update {
            it.copy(password = password, passwordError = null, writeError = null)
        }
    }

    fun updateProvisionFormat(format: NdefPayloadFormat) {
        _preferredFormat.value = format
        repository.savePreferredFormat(format)
        _provisionDialog.update {
            it.copy(format = format, writeError = null)
        }
    }

    fun startWaitingForWriteTap() {
        val state = _provisionDialog.value
        val device = state.device ?: return
        val trimmedSsid = state.ssid.trim()
        var ssidErr: String? = null
        var pwdErr: String? = null

        if (trimmedSsid.isEmpty()) {
            ssidErr = "请输入 Wi-Fi 名称 (SSID)"
        }
        if (state.password.isNotEmpty() && state.password.length < 8) {
            pwdErr = "Wi-Fi 密码至少需要 8 位字符（无密码开放网络可留空）"
        }

        if (ssidErr != null || pwdErr != null) {
            _provisionDialog.update {
                it.copy(ssidError = ssidErr, passwordError = pwdErr)
            }
            return
        }

        val payload = PayloadBuilder.buildPayload(
            format = state.format,
            ssid = trimmedSsid,
            password = state.password,
            uid = device.uid
        )
        val footprint = PayloadBuilder.calculateFootprint(payload)
        if (!footprint.isWithinCapacity) {
            _provisionDialog.update {
                it.copy(
                    writeError = "报文占用 (${footprint.eepromUsedBytes} B) 超出 FM11NT083C 888 Bytes 用户区上限，请缩短输入或切换格式"
                )
            }
            return
        }

        _provisionDialog.update {
            it.copy(
                ssid = trimmedSsid,
                step = ProvisionStep.WAITING_TAP,
                ssidError = null,
                passwordError = null,
                writeError = null
            )
        }
    }

    fun backToProvisionForm() {
        _provisionDialog.update {
            it.copy(
                step = ProvisionStep.FORM,
                writeError = null
            )
        }
    }

    fun currentWritePayload(): String? {
        val state = _provisionDialog.value
        val device = state.device ?: return null
        return PayloadBuilder.buildPayload(
            format = state.format,
            ssid = state.ssid.trim(),
            password = state.password,
            uid = device.uid
        )
    }

    fun onWriteResult(result: NfcWriteResult) {
        val state = _provisionDialog.value
        val device = state.device ?: return
        if (state.step != ProvisionStep.WAITING_TAP) return

        val now = System.currentTimeMillis()
        when (result) {
            is NfcWriteResult.Success -> {
                val updatedDevices = _devices.value.map { item ->
                    if (item.id == device.id) {
                        item.copy(
                            status = ProvisionStatus.PROVISIONED,
                            wifiSsid = state.ssid.trim(),
                            wifiPassword = state.password,
                            provisionedAt = now
                        )
                    } else {
                        item
                    }
                }
                _devices.value = updatedDevices
                repository.saveDevices(updatedDevices)

                val (writeHex, writeAscii) = NfcHelper.buildWriteLogHexAndAscii(
                    uid = device.uid,
                    payload = result.payload,
                    ndefMessageBytes = result.rawNdefBytes
                )
                appendLog(
                    NfcLogEntry(
                        id = UUID.randomUUID().toString(),
                        timestamp = now,
                        type = NfcLogType.WRITE_WIFI,
                        uid = device.uid,
                        deviceName = device.name,
                        summary = "写入 Wi-Fi「${state.ssid.trim()}」成功 (${state.format.label})",
                        payload = result.payload,
                        byteCount = result.ndefMessageBytes,
                        isSuccess = true,
                        hexData = writeHex,
                        asciiData = writeAscii
                    )
                )

                val refreshedDevice = updatedDevices.firstOrNull { it.id == device.id } ?: device
                _provisionDialog.update {
                    it.copy(
                        device = refreshedDevice,
                        step = ProvisionStep.SUCCESS,
                        writeError = null,
                        writtenPayload = result.payload,
                        writtenByteCount = result.ndefMessageBytes
                    )
                }
            }

            is NfcWriteResult.Failure -> {
                val attemptedPayload = currentWritePayload()
                val errUid = result.scannedUid ?: device.uid
                val (errHex, errAscii) = if (!attemptedPayload.isNullOrBlank()) {
                    NfcHelper.buildWriteLogHexAndAscii(uid = errUid, payload = attemptedPayload)
                } else {
                    null to null
                }
                appendLog(
                    NfcLogEntry(
                        id = UUID.randomUUID().toString(),
                        timestamp = now,
                        type = NfcLogType.ERROR,
                        uid = errUid,
                        deviceName = device.name,
                        summary = result.message,
                        payload = attemptedPayload,
                        isSuccess = false,
                        hexData = errHex,
                        asciiData = errAscii
                    )
                )
                _provisionDialog.update {
                    it.copy(writeError = result.message)
                }
            }
        }
    }

    fun startWaitingForClearTap() {
        val state = _provisionDialog.value
        if (state.device == null) return
        _provisionDialog.update {
            it.copy(
                step = ProvisionStep.WAITING_CLEAR_TAP,
                ssidError = null,
                passwordError = null,
                writeError = null
            )
        }
    }

    fun onClearResult(result: NfcClearResult) {
        val state = _provisionDialog.value
        val device = state.device ?: return
        if (state.step != ProvisionStep.WAITING_CLEAR_TAP) return

        val now = System.currentTimeMillis()
        when (result) {
            is NfcClearResult.Success -> {
                val updatedDevices = _devices.value.map { item ->
                    if (item.id == device.id) {
                        item.copy(
                            status = ProvisionStatus.PENDING,
                            wifiSsid = null,
                            wifiPassword = null,
                            provisionedAt = null
                        )
                    } else {
                        item
                    }
                }
                _devices.value = updatedDevices
                repository.saveDevices(updatedDevices)

                val (clearHex, clearAscii) = NfcHelper.buildClearLogHexAndAscii(
                    uid = device.uid,
                    clearedPageCount = result.clearedPageCount,
                    writtenBytes = result.writtenBytes
                )
                appendLog(
                    NfcLogEntry(
                        id = UUID.randomUUID().toString(),
                        timestamp = now,
                        type = NfcLogType.CLEAR_TAG,
                        uid = device.uid,
                        deviceName = device.name,
                        summary = "清空设备「${device.name}」NFC 写入数据（${result.methodDescription}）",
                        payload = if (result.clearedPageCount > 0) {
                            "EMPTY_NDEF (03 00 FE 00), 已擦除 ${result.clearedPageCount} 页"
                        } else {
                            "EMPTY_NDEF"
                        },
                        byteCount = result.writtenBytes.size,
                        isSuccess = true,
                        hexData = clearHex,
                        asciiData = clearAscii
                    )
                )

                val refreshedDevice = updatedDevices.firstOrNull { it.id == device.id } ?: device
                _provisionDialog.update {
                    it.copy(
                        device = refreshedDevice,
                        step = ProvisionStep.CLEAR_SUCCESS,
                        ssid = "",
                        password = "",
                        writeError = null,
                        clearedPageCount = result.clearedPageCount,
                        clearMethodSummary = result.methodDescription
                    )
                }
            }

            is NfcClearResult.Failure -> {
                val errUid = result.scannedUid ?: device.uid
                appendLog(
                    NfcLogEntry(
                        id = UUID.randomUUID().toString(),
                        timestamp = now,
                        type = NfcLogType.ERROR,
                        uid = errUid,
                        deviceName = device.name,
                        summary = "清空芯片数据失败：${result.message}",
                        isSuccess = false
                    )
                )
                _provisionDialog.update {
                    it.copy(writeError = result.message)
                }
            }
        }
    }

    // --- Tag Inspector (Debug Read & Clear) ---

    fun setTagInspectorListening(listening: Boolean) {
        _tagInspector.update {
            it.copy(
                isListening = listening,
                isEraseMode = if (listening) it.isEraseMode else false
            )
        }
    }

    fun setTagInspectorEraseMode(eraseMode: Boolean) {
        _tagInspector.update {
            it.copy(
                isEraseMode = eraseMode,
                isListening = if (eraseMode) true else it.isListening,
                lastError = null,
                lastClearMessage = if (eraseMode) null else it.lastClearMessage
            )
        }
    }

    fun clearTagInspectorResult() {
        _tagInspector.update {
            it.copy(lastResult = null, lastClearMessage = null, lastError = null)
        }
    }

    fun onTagClearedInInspector(
        clearResult: NfcClearResult,
        refreshedOutcome: TagReadOutcome? = null
    ) {
        val now = System.currentTimeMillis()
        when (clearResult) {
            is NfcClearResult.Success -> {
                val matchedDevice = _devices.value.firstOrNull {
                    it.uid.equals(clearResult.uid, ignoreCase = true)
                }
                if (matchedDevice != null) {
                    val updatedDevices = _devices.value.map { item ->
                        if (item.id == matchedDevice.id) {
                            item.copy(
                                status = ProvisionStatus.PENDING,
                                wifiSsid = null,
                                wifiPassword = null,
                                provisionedAt = null
                            )
                        } else {
                            item
                        }
                    }
                    _devices.value = updatedDevices
                    repository.saveDevices(updatedDevices)
                }

                val (clearHex, clearAscii) = NfcHelper.buildClearLogHexAndAscii(
                    uid = clearResult.uid,
                    clearedPageCount = clearResult.clearedPageCount,
                    writtenBytes = clearResult.writtenBytes
                )
                appendLog(
                    NfcLogEntry(
                        id = UUID.randomUUID().toString(),
                        timestamp = now,
                        type = NfcLogType.CLEAR_TAG,
                        uid = clearResult.uid,
                        deviceName = matchedDevice?.name,
                        summary = "读卡调试清空芯片数据：${clearResult.methodDescription}",
                        payload = if (clearResult.clearedPageCount > 0) {
                            "EMPTY_NDEF (03 00 FE 00), 已擦除 ${clearResult.clearedPageCount} 页"
                        } else {
                            "EMPTY_NDEF"
                        },
                        byteCount = clearResult.writtenBytes.size,
                        isSuccess = true,
                        hexData = clearHex,
                        asciiData = clearAscii
                    )
                )

                val refreshedResult = (refreshedOutcome as? TagReadOutcome.Success)?.result
                val syncNote = if (matchedDevice != null) {
                    "（已同步将「${matchedDevice.name}」重置为待配网）"
                } else {
                    ""
                }
                val clearMsg = "${clearResult.methodDescription}$syncNote"
                _tagInspector.update {
                    it.copy(
                        isEraseMode = false,
                        lastResult = refreshedResult ?: it.lastResult,
                        lastClearMessage = clearMsg,
                        lastError = null
                    )
                }
                _snackbarMessage.value = "已清空 NFC 卡片写入数据"
            }

            is NfcClearResult.Failure -> {
                appendLog(
                    NfcLogEntry(
                        id = UUID.randomUUID().toString(),
                        timestamp = now,
                        type = NfcLogType.ERROR,
                        uid = clearResult.scannedUid,
                        deviceName = null,
                        summary = "清空芯片数据异常：${clearResult.message}",
                        isSuccess = false
                    )
                )
                _tagInspector.update {
                    it.copy(
                        lastError = clearResult.message,
                        lastClearMessage = null
                    )
                }
            }
        }
    }

    fun onTagInspected(outcome: TagReadOutcome) {
        val now = System.currentTimeMillis()
        when (outcome) {
            is TagReadOutcome.Success -> {
                val res = outcome.result
                val matchedDevice = _devices.value.firstOrNull { it.uid.equals(res.uid, ignoreCase = true) }
                val firstText = res.records.firstOrNull()?.decodedText
                _tagInspector.update {
                    it.copy(
                        lastResult = res,
                        lastClearMessage = null,
                        lastError = null
                    )
                }
                val (inspectHex, inspectAscii) = NfcHelper.buildInspectLogHexAndAscii(res)
                appendLog(
                    NfcLogEntry(
                        id = UUID.randomUUID().toString(),
                        timestamp = now,
                        type = NfcLogType.READ_TAG,
                        uid = res.uid,
                        deviceName = matchedDevice?.name,
                        summary = if (res.records.isNotEmpty()) {
                            "读卡调试：读取到 ${res.records.size} 条 NDEF 记录 (${res.ndefUsedBytes} B)"
                        } else {
                            "读卡调试：读取到芯片 UID 与底层页信息（无 NDEF 数据）"
                        },
                        payload = firstText,
                        byteCount = res.ndefUsedBytes.takeIf { it > 0 } ?: res.uidByteCount,
                        isSuccess = true,
                        hexData = inspectHex,
                        asciiData = inspectAscii
                    )
                )
            }

            is TagReadOutcome.Failure -> {
                _tagInspector.update {
                    it.copy(lastError = outcome.message)
                }
                appendLog(
                    NfcLogEntry(
                        id = UUID.randomUUID().toString(),
                        timestamp = now,
                        type = NfcLogType.ERROR,
                        uid = outcome.scannedUid,
                        deviceName = null,
                        summary = "读卡调试异常：${outcome.message}",
                        isSuccess = false
                    )
                )
            }
        }
    }

    fun openBindWithScannedUid(uid: String, byteCount: Int) {
        val existing = _devices.value.firstOrNull { it.uid.equals(uid, ignoreCase = true) }
        _bindDialog.value = BindDialogState(
            isOpen = true,
            isScanning = false,
            scannedUid = uid,
            uidByteCount = byteCount,
            duplicateDeviceName = existing?.name
        )
    }

    // --- Logs ---

    fun clearLogs() {
        _logs.value = emptyList()
        repository.saveLogs(emptyList())
        _snackbarMessage.value = "已清空读写日志"
    }

    private fun appendLog(entry: NfcLogEntry) {
        val updated = listOf(entry) + _logs.value
        _logs.value = updated
        repository.saveLogs(updated)
    }

    class Factory(
        private val repository: ProvisioningRepository
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return ProvisionerViewModel(repository) as T
        }
    }
}
