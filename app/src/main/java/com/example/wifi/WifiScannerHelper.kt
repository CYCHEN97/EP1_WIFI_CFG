package com.example.wifi

import android.Manifest
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.location.LocationManager
import android.net.wifi.ScanResult
import android.net.wifi.WifiManager
import android.provider.Settings
import androidx.core.content.ContextCompat
import androidx.core.location.LocationManagerCompat
import com.example.model.ScannedWifiNetwork
import com.example.model.WifiScanState
import com.example.model.WifiScanStatus

object WifiScannerHelper {

    val REQUIRED_PERMISSIONS = arrayOf(
        Manifest.permission.ACCESS_FINE_LOCATION,
        Manifest.permission.ACCESS_COARSE_LOCATION
    )

    fun hasLocationPermission(context: Context): Boolean {
        return ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
    }

    fun isWifiEnabled(context: Context): Boolean {
        val wifiManager = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as? WifiManager
            ?: return false
        return runCatching { wifiManager.isWifiEnabled }.getOrDefault(false)
    }

    fun isLocationEnabled(context: Context): Boolean {
        val locationManager = context.applicationContext.getSystemService(Context.LOCATION_SERVICE) as? LocationManager
            ?: return false
        return runCatching { LocationManagerCompat.isLocationEnabled(locationManager) }.getOrDefault(false)
    }

    fun openWifiSettings(context: Context) {
        val intent = Intent(Settings.ACTION_WIFI_SETTINGS).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        runCatching { context.startActivity(intent) }
    }

    fun openLocationSettings(context: Context) {
        val intent = Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        runCatching { context.startActivity(intent) }
    }

    fun startAutoScan(
        context: Context,
        onStateUpdate: (WifiScanState) -> Unit
    ): () -> Unit {
        val appContext = context.applicationContext
        if (!hasLocationPermission(appContext)) {
            onStateUpdate(WifiScanState(status = WifiScanStatus.PERMISSION_REQUIRED))
            return {}
        }
        val wifiManager = appContext.getSystemService(Context.WIFI_SERVICE) as? WifiManager
        if (wifiManager == null || !isWifiEnabled(appContext)) {
            onStateUpdate(WifiScanState(status = WifiScanStatus.WIFI_DISABLED))
            return {}
        }
        if (!isLocationEnabled(appContext)) {
            onStateUpdate(WifiScanState(status = WifiScanStatus.LOCATION_DISABLED))
            return {}
        }

        val initialCached = readCachedNetworks(wifiManager)
        onStateUpdate(
            WifiScanState(
                status = WifiScanStatus.SCANNING,
                networks = initialCached
            )
        )

        var registered = true
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(ctx: Context?, intent: Intent?) {
                val updated = intent?.getBooleanExtra(WifiManager.EXTRA_RESULTS_UPDATED, false) ?: false
                val latest = readCachedNetworks(wifiManager)
                onStateUpdate(
                    WifiScanState(
                        status = WifiScanStatus.READY,
                        networks = latest,
                        isThrottled = !updated
                    )
                )
            }
        }

        val filter = IntentFilter(WifiManager.SCAN_RESULTS_AVAILABLE_ACTION)
        ContextCompat.registerReceiver(
            appContext,
            receiver,
            filter,
            ContextCompat.RECEIVER_EXPORTED
        )

        @Suppress("DEPRECATION")
        val started = runCatching { wifiManager.startScan() }.getOrDefault(false)
        if (!started) {
            // Android throttles active scans (4 times / 2 min); fall back immediately to cached results
            val fallback = readCachedNetworks(wifiManager)
            onStateUpdate(
                WifiScanState(
                    status = WifiScanStatus.READY,
                    networks = fallback,
                    isThrottled = true
                )
            )
        }

        return {
            if (registered) {
                registered = false
                runCatching { appContext.unregisterReceiver(receiver) }
            }
        }
    }

    private fun readCachedNetworks(wifiManager: WifiManager): List<ScannedWifiNetwork> {
        return try {
            @Suppress("DEPRECATION")
            val rawResults = wifiManager.scanResults ?: emptyList()
            normalizeScanResults(rawResults)
        } catch (_: SecurityException) {
            emptyList()
        } catch (_: Exception) {
            emptyList()
        }
    }

    fun normalizeScanResults(rawResults: List<ScanResult>): List<ScannedWifiNetwork> {
        val rawEntries = rawResults.mapNotNull { result ->
            @Suppress("DEPRECATION")
            val ssid = result.SSID?.trim().orEmpty()
            if (ssid.isEmpty() || ssid == "<unknown ssid>") {
                return@mapNotNull null
            }
            val (secured, securityLabel) = parseSecurity(result.capabilities)
            ScannedWifiNetwork(
                ssid = ssid,
                rssi = result.level,
                frequencyMhz = result.frequency,
                isSecured = secured,
                securityLabel = securityLabel
            )
        }
        return deduplicateAndSort(rawEntries)
    }

    fun deduplicateAndSort(entries: List<ScannedWifiNetwork>): List<ScannedWifiNetwork> {
        val bestBySsid = linkedMapOf<String, ScannedWifiNetwork>()
        for (item in entries) {
            val current = bestBySsid[item.ssid]
            if (current == null || item.rssi > current.rssi) {
                bestBySsid[item.ssid] = item
            }
        }
        return bestBySsid.values.sortedWith(
            compareByDescending<ScannedWifiNetwork> { it.rssi }
                .thenBy { it.ssid.lowercase() }
        )
    }

    fun parseSecurity(capabilities: String?): Pair<Boolean, String> {
        val caps = capabilities.orEmpty().uppercase()
        return when {
            caps.contains("SAE") || caps.contains("WPA3") -> true to "WPA3"
            caps.contains("WPA2") -> true to "WPA2"
            caps.contains("WPA") -> true to "WPA"
            caps.contains("WEP") -> true to "WEP"
            else -> false to "开放"
        }
    }
}
