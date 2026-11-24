package com.example.model

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

const val FM11NT083C_USER_EEPROM_BYTES = 888
const val IST_VENDOR_NAME = "IST"

enum class AppTab(val label: String) {
    DEVICES("设备列表"),
    QUICK_PROVISION("一键配网"),
    READ_TAG("读卡调试"),
    LOGS("读写日志"),
    GUIDE("调试说明")
}

enum class ProvisionStatus(val label: String) {
    PENDING("待配网"),
    PROVISIONED("已配网")
}

enum class DeviceStatusFilter(val label: String) {
    ALL("全部"),
    PENDING("待配网"),
    PROVISIONED("已配网")
}

enum class NdefPayloadFormat(
    val id: String,
    val label: String,
    val description: String
) {
    JSON("JSON", "JSON 格式", "{\"ssid\":\"...\",\"password\":\"...\",\"uid\":\"04E28A...\",\"vendor\":\"IST\",\"ts\":\"yyyyMMddHHmmss\"}"),
    KEY_VALUE("KEY_VALUE", "键值对", "SSID=...;PWD=...;VENDOR=IST;TS=..."),
    WIFI_URI("WIFI_URI", "WIFI URI", "WIFI:T:WPA;S:...;P:...;V:IST;;");

    companion object {
        fun fromId(id: String?): NdefPayloadFormat =
            entries.firstOrNull { it.id == id } ?: JSON
    }
}

enum class NfcLogType(val label: String) {
    READ_UID("读取 UID"),
    READ_TAG("读卡调试"),
    WRITE_WIFI("写入 Wi-Fi"),
    CLEAR_TAG("清空数据"),
    ERROR("读写异常")
}

enum class NfcAvailability {
    NOT_SUPPORTED,
    DISABLED,
    ENABLED
}

data class BoundDevice(
    val id: String,
    val uid: String,
    val name: String,
    val boundAt: Long,
    val status: ProvisionStatus,
    val wifiSsid: String? = null,
    val wifiPassword: String? = null,
    val provisionedAt: Long? = null
)

data class NfcLogEntry(
    val id: String,
    val timestamp: Long,
    val type: NfcLogType,
    val uid: String?,
    val deviceName: String?,
    val summary: String,
    val payload: String? = null,
    val byteCount: Int? = null,
    val isSuccess: Boolean,
    val hexData: String? = null,
    val asciiData: String? = null
)

data class NdefFootprint(
    val payloadUtf8Bytes: Int,
    val ndefMessageBytes: Int,
    val eepromUsedBytes: Int,
    val eepromTotalBytes: Int = FM11NT083C_USER_EEPROM_BYTES
) {
    val isWithinCapacity: Boolean
        get() = eepromUsedBytes <= eepromTotalBytes

    val usageFraction: Float
        get() = (eepromUsedBytes.toFloat() / eepromTotalBytes.toFloat()).coerceIn(0f, 1f)
}

object PayloadBuilder {
    fun buildPayload(
        format: NdefPayloadFormat,
        ssid: String,
        password: String,
        uid: String,
        vendor: String = IST_VENDOR_NAME,
        timestampMillis: Long = System.currentTimeMillis()
    ): String {
        val ts = TimeFormatter.formatCompactTimestamp(timestampMillis)
        val normalizedUid = uid.replace(":", "")
        return when (format) {
            NdefPayloadFormat.JSON -> {
                "{\"ssid\":\"${escapeJson(ssid)}\",\"password\":\"${escapeJson(password)}\",\"uid\":\"${escapeJson(normalizedUid)}\",\"vendor\":\"${escapeJson(vendor)}\",\"ts\":\"${escapeJson(ts)}\"}"
            }
            NdefPayloadFormat.KEY_VALUE -> {
                "SSID=$ssid;PWD=$password;VENDOR=$vendor;TS=$ts"
            }
            NdefPayloadFormat.WIFI_URI -> {
                val authType = if (password.isEmpty()) "nopass" else "WPA"
                "WIFI:T:$authType;S:${escapeWifiUri(ssid)};P:${escapeWifiUri(password)};V:${escapeWifiUri(vendor)};;"
            }
        }
    }

    fun calculateFootprint(payload: String): NdefFootprint {
        val payloadBytes = payload.toByteArray(Charsets.UTF_8).size
        // NdefRecord.createTextRecord("en", payload):
        // Payload = 1 byte status (0x02) + 2 bytes language ("en") + payloadBytes
        val textRecordPayloadSize = 3 + payloadBytes
        // NDEF Record header: SR=1 (4 bytes) if <= 255 else Normal (7 bytes)
        val recordHeaderSize = if (textRecordPayloadSize <= 255) 4 else 7
        val ndefMessageBytes = recordHeaderSize + textRecordPayloadSize
        // Type 2 Tag NDEF TLV: Tag (0x03) + Length (1 byte if <255, else 3 bytes) + message + Terminator (0xFE)
        val tlvLengthBytes = if (ndefMessageBytes < 255) 1 else 3
        val eepromUsedBytes = 1 + tlvLengthBytes + ndefMessageBytes + 1
        return NdefFootprint(
            payloadUtf8Bytes = payloadBytes,
            ndefMessageBytes = ndefMessageBytes,
            eepromUsedBytes = eepromUsedBytes
        )
    }

    private fun escapeJson(value: String): String {
        val sb = StringBuilder(value.length)
        for (ch in value) {
            when (ch) {
                '\\' -> sb.append("\\\\")
                '"' -> sb.append("\\\"")
                '\b' -> sb.append("\\b")
                '\u000C' -> sb.append("\\f")
                '\n' -> sb.append("\\n")
                '\r' -> sb.append("\\r")
                '\t' -> sb.append("\\t")
                else -> sb.append(ch)
            }
        }
        return sb.toString()
    }

    private fun escapeWifiUri(value: String): String {
        val sb = StringBuilder(value.length)
        for (ch in value) {
            when (ch) {
                '\\', ';', ',', ':', '"' -> {
                    sb.append('\\')
                    sb.append(ch)
                }
                else -> sb.append(ch)
            }
        }
        return sb.toString()
    }
}

object TimeFormatter {
    fun formatDateTime(timestamp: Long): String {
        val sdf = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
        return sdf.format(Date(timestamp))
    }

    fun formatShortDateTime(timestamp: Long): String {
        val sdf = SimpleDateFormat("MM-dd HH:mm", Locale.getDefault())
        return sdf.format(Date(timestamp))
    }

    fun formatCompactTimestamp(timestamp: Long = System.currentTimeMillis()): String {
        val sdf = SimpleDateFormat("yyyyMMddHHmmss", Locale.getDefault())
        return sdf.format(Date(timestamp))
    }
}

data class BindDialogState(
    val isOpen: Boolean = false,
    val isScanning: Boolean = true,
    val scannedUid: String? = null,
    val uidByteCount: Int = 0,
    val duplicateDeviceName: String? = null,
    val deviceNameInput: String = "",
    val nameError: String? = null,
    val scanError: String? = null
)

enum class ProvisionStep {
    FORM,
    WAITING_TAP,
    SUCCESS,
    WAITING_CLEAR_TAP,
    CLEAR_SUCCESS
}

data class ProvisionDialogState(
    val device: BoundDevice? = null,
    val step: ProvisionStep = ProvisionStep.FORM,
    val ssid: String = "",
    val password: String = "",
    val format: NdefPayloadFormat = NdefPayloadFormat.JSON,
    val ssidError: String? = null,
    val passwordError: String? = null,
    val writeError: String? = null,
    val writtenPayload: String = "",
    val writtenByteCount: Int = 0,
    val clearedPageCount: Int = 0,
    val clearMethodSummary: String = ""
) {
    val isOpen: Boolean get() = device != null
}

enum class QuickProvisionStep {
    FORM,
    WAITING_TAP,
    SUCCESS
}

data class QuickProvisionState(
    val deviceName: String = "",
    val ssid: String = "",
    val password: String = "",
    val format: NdefPayloadFormat = NdefPayloadFormat.JSON,
    val overwriteExisting: Boolean = false,
    val step: QuickProvisionStep = QuickProvisionStep.FORM,
    val nameError: String? = null,
    val ssidError: String? = null,
    val passwordError: String? = null,
    val writeError: String? = null,
    val lastBoundDevice: BoundDevice? = null,
    val writtenPayload: String = "",
    val writtenByteCount: Int = 0,
    val isExistingUpdated: Boolean = false
)

data class ScannedWifiNetwork(
    val ssid: String,
    val rssi: Int,
    val frequencyMhz: Int,
    val isSecured: Boolean,
    val securityLabel: String
) {
    val bandLabel: String
        get() = when {
            frequencyMhz >= 5925 -> "6 GHz"
            frequencyMhz >= 4900 -> "5 GHz"
            else -> "2.4 GHz"
        }

    val signalLevel: Int
        get() = when {
            rssi >= -55 -> 4
            rssi >= -67 -> 3
            rssi >= -78 -> 2
            else -> 1
        }
}

enum class WifiScanStatus {
    IDLE,
    PERMISSION_REQUIRED,
    WIFI_DISABLED,
    LOCATION_DISABLED,
    SCANNING,
    READY
}

data class WifiScanState(
    val status: WifiScanStatus = WifiScanStatus.IDLE,
    val networks: List<ScannedWifiNetwork> = emptyList(),
    val isThrottled: Boolean = false,
    val errorMessage: String? = null
)

data class ParsedNdefRecord(
    val index: Int,
    val tnfLabel: String,
    val typeLabel: String,
    val languageCode: String?,
    val decodedText: String,
    val payloadHex: String,
    val payloadAscii: String,
    val payloadBytes: Int
)

data class RawPageEntry(
    val pageIndex: Int,
    val dataHex: String,
    val asciiPreview: String,
    val note: String
)

data class TagInspectResult(
    val readAt: Long,
    val uid: String,
    val uidRawHex: String,
    val uidByteCount: Int,
    val techList: List<String>,
    val atqaHex: String?,
    val sakHex: String?,
    val isNdefFormatted: Boolean,
    val isNdefFormatable: Boolean,
    val ndefType: String?,
    val isWritable: Boolean?,
    val canMakeReadOnly: Boolean?,
    val ndefMaxBytes: Int?,
    val ndefUsedBytes: Int,
    val records: List<ParsedNdefRecord>,
    val rawPages: List<RawPageEntry>,
    val rawEepromAscii: String = "",
    val userAreaAscii: String = "",
    val note: String? = null
)

data class TagInspectorState(
    val isListening: Boolean = true,
    val isEraseMode: Boolean = false,
    val lastResult: TagInspectResult? = null,
    val lastClearMessage: String? = null,
    val lastError: String? = null
)
