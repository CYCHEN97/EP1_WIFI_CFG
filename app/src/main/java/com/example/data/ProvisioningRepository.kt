package com.example.data

import android.content.Context
import android.content.SharedPreferences
import com.example.model.BoundDevice
import com.example.model.NdefPayloadFormat
import com.example.model.NfcLogEntry
import com.example.model.NfcLogType
import com.example.model.ProvisionStatus
import org.json.JSONArray
import org.json.JSONObject

class ProvisioningRepository(context: Context) {

    private val prefs: SharedPreferences =
        context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun loadDevices(): List<BoundDevice> {
        val raw = prefs.getString(KEY_DEVICES, null) ?: return emptyList()
        if (raw.isBlank()) return emptyList()
        return try {
            val array = JSONArray(raw)
            buildList(array.length()) {
                for (i in 0 until array.length()) {
                    val obj = array.optJSONObject(i) ?: continue
                    val statusStr = obj.optString("status", ProvisionStatus.PENDING.name)
                    val status = ProvisionStatus.entries.firstOrNull { it.name == statusStr }
                        ?: ProvisionStatus.PENDING
                    add(
                        BoundDevice(
                            id = obj.getString("id"),
                            uid = obj.getString("uid"),
                            name = obj.getString("name"),
                            boundAt = obj.getLong("boundAt"),
                            status = status,
                            wifiSsid = obj.optString("wifiSsid", "").takeIf { it.isNotEmpty() },
                            wifiPassword = if (obj.has("wifiPassword") && !obj.isNull("wifiPassword")) {
                                obj.optString("wifiPassword", "")
                            } else {
                                null
                            },
                            provisionedAt = if (obj.has("provisionedAt") && !obj.isNull("provisionedAt")) {
                                obj.optLong("provisionedAt")
                            } else {
                                null
                            }
                        )
                    )
                }
            }
        } catch (_: Exception) {
            emptyList()
        }
    }

    fun saveDevices(devices: List<BoundDevice>): Boolean {
        val array = JSONArray()
        devices.forEach { device ->
            val obj = JSONObject().apply {
                put("id", device.id)
                put("uid", device.uid)
                put("name", device.name)
                put("boundAt", device.boundAt)
                put("status", device.status.name)
                if (device.wifiSsid != null) {
                    put("wifiSsid", device.wifiSsid)
                } else {
                    put("wifiSsid", JSONObject.NULL)
                }
                if (device.wifiPassword != null) {
                    put("wifiPassword", device.wifiPassword)
                } else {
                    put("wifiPassword", JSONObject.NULL)
                }
                if (device.provisionedAt != null) {
                    put("provisionedAt", device.provisionedAt)
                } else {
                    put("provisionedAt", JSONObject.NULL)
                }
            }
            array.put(obj)
        }
        return prefs.edit().putString(KEY_DEVICES, array.toString()).commit()
    }

    fun loadLogs(): List<NfcLogEntry> {
        val raw = prefs.getString(KEY_LOGS, null) ?: return emptyList()
        if (raw.isBlank()) return emptyList()
        return try {
            val array = JSONArray(raw)
            buildList(array.length()) {
                for (i in 0 until array.length()) {
                    val obj = array.optJSONObject(i) ?: continue
                    val typeStr = obj.optString("type", NfcLogType.READ_UID.name)
                    val type = NfcLogType.entries.firstOrNull { it.name == typeStr }
                        ?: NfcLogType.READ_UID
                    add(
                        NfcLogEntry(
                            id = obj.getString("id"),
                            timestamp = obj.getLong("timestamp"),
                            type = type,
                            uid = obj.optString("uid", "").takeIf { it.isNotEmpty() },
                            deviceName = obj.optString("deviceName", "").takeIf { it.isNotEmpty() },
                            summary = obj.optString("summary", ""),
                            payload = obj.optString("payload", "").takeIf { it.isNotEmpty() },
                            byteCount = if (obj.has("byteCount") && !obj.isNull("byteCount")) {
                                obj.optInt("byteCount")
                            } else {
                                null
                            },
                            isSuccess = obj.optBoolean("isSuccess", true),
                            hexData = obj.optString("hexData", "").takeIf { it.isNotEmpty() },
                            asciiData = obj.optString("asciiData", "").takeIf { it.isNotEmpty() }
                        )
                    )
                }
            }
        } catch (_: Exception) {
            emptyList()
        }
    }

    fun saveLogs(logs: List<NfcLogEntry>): Boolean {
        val trimmed = logs.take(MAX_LOG_COUNT)
        val array = JSONArray()
        trimmed.forEach { log ->
            val obj = JSONObject().apply {
                put("id", log.id)
                put("timestamp", log.timestamp)
                put("type", log.type.name)
                put("uid", log.uid ?: JSONObject.NULL)
                put("deviceName", log.deviceName ?: JSONObject.NULL)
                put("summary", log.summary)
                put("payload", log.payload ?: JSONObject.NULL)
                put("byteCount", log.byteCount ?: JSONObject.NULL)
                put("isSuccess", log.isSuccess)
                put("hexData", log.hexData ?: JSONObject.NULL)
                put("asciiData", log.asciiData ?: JSONObject.NULL)
            }
            array.put(obj)
        }
        return prefs.edit().putString(KEY_LOGS, array.toString()).commit()
    }

    fun loadPreferredFormat(): NdefPayloadFormat {
        val raw = prefs.getString(KEY_PREFERRED_FORMAT, NdefPayloadFormat.JSON.id)
        return NdefPayloadFormat.fromId(raw)
    }

    fun savePreferredFormat(format: NdefPayloadFormat): Boolean {
        return prefs.edit().putString(KEY_PREFERRED_FORMAT, format.id).commit()
    }

    companion object {
        private const val PREFS_NAME = "ist_nfc_provisioner_prefs"
        private const val KEY_DEVICES = "bound_devices_json"
        private const val KEY_LOGS = "nfc_logs_json"
        private const val KEY_PREFERRED_FORMAT = "preferred_ndef_format"
        private const val MAX_LOG_COUNT = 200
    }
}
