package com.example

import com.example.model.FM11NT083C_USER_EEPROM_BYTES
import com.example.model.NdefPayloadFormat
import com.example.model.PayloadBuilder
import com.example.model.ScannedWifiNetwork
import com.example.nfc.NfcHelper
import com.example.wifi.WifiScannerHelper
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ProvisioningUnitTest {

    @Test
    fun `formatUid formats 7-byte FM11NT083C UID to uppercase colon-separated hex`() {
        val raw = byteArrayOf(
            0x04,
            0xE2.toByte(),
            0x8A.toByte(),
            0x19,
            0x4C,
            0x72,
            0x01
        )
        assertEquals("04:E2:8A:19:4C:72:01", NfcHelper.formatUid(raw))
    }

    @Test
    fun `buildPayload formats JSON, KEY_VALUE, and WIFI_URI accurately`() {
        val uid = "1D:55:7A:D1:03:00:10"
        val sampleMillis = 1795345046000L
        val expectedTs = com.example.model.TimeFormatter.formatCompactTimestamp(sampleMillis)
        assertEquals(14, expectedTs.length)
        assertTrue(expectedTs.all { it.isDigit() })

        val json = PayloadBuilder.buildPayload(
            format = NdefPayloadFormat.JSON,
            ssid = "123",
            password = "1234567890",
            uid = uid,
            timestampMillis = sampleMillis
        )
        assertEquals(
            "{\"ssid\":\"123\",\"password\":\"1234567890\",\"uid\":\"1D557AD1030010\",\"vendor\":\"IST\",\"ts\":\"$expectedTs\"}",
            json
        )

        val kv = PayloadBuilder.buildPayload(
            format = NdefPayloadFormat.KEY_VALUE,
            ssid = "IST_Display",
            password = "pass12345",
            uid = uid,
            timestampMillis = sampleMillis
        )
        assertEquals("SSID=IST_Display;PWD=pass12345;VENDOR=IST;TS=$expectedTs", kv)

        val uri = PayloadBuilder.buildPayload(
            format = NdefPayloadFormat.WIFI_URI,
            ssid = "IST_Display",
            password = "pass12345",
            uid = uid,
            timestampMillis = sampleMillis
        )
        assertEquals("WIFI:T:WPA;S:IST_Display;P:pass12345;V:IST;;", uri)

        val openUri = PayloadBuilder.buildPayload(
            format = NdefPayloadFormat.WIFI_URI,
            ssid = "IST_Guest",
            password = "",
            uid = uid,
            timestampMillis = sampleMillis
        )
        assertEquals("WIFI:T:nopass;S:IST_Guest;P:;V:IST;;", openUri)
    }

    @Test
    fun `calculateFootprint validates FM11NT083C 888 byte limit`() {
        val small = PayloadBuilder.calculateFootprint("SSID=IST;PWD=12345678")
        assertTrue(small.isWithinCapacity)
        assertTrue(small.eepromUsedBytes < FM11NT083C_USER_EEPROM_BYTES)

        val oversizedPayload = "A".repeat(900)
        val oversized = PayloadBuilder.calculateFootprint(oversizedPayload)
        assertFalse(oversized.isWithinCapacity)
    }

    @Test
    fun `deduplicateAndSort keeps strongest AP per SSID and sorts by RSSI descending`() {
        val entries = listOf(
            ScannedWifiNetwork("IST_Office", -75, 2437, true, "WPA2"),
            ScannedWifiNetwork("IST_Guest", -60, 5180, false, "开放"),
            ScannedWifiNetwork("IST_Office", -48, 2412, true, "WPA2"),
            ScannedWifiNetwork("IST_Lab", -52, 2462, true, "WPA3")
        )
        val sorted = WifiScannerHelper.deduplicateAndSort(entries)
        assertEquals(3, sorted.size)
        assertEquals("IST_Office", sorted[0].ssid)
        assertEquals(-48, sorted[0].rssi)
        assertEquals("2.4 GHz", sorted[0].bandLabel)
        assertEquals("IST_Lab", sorted[1].ssid)
        assertEquals("IST_Guest", sorted[2].ssid)
        assertEquals("5 GHz", sorted[2].bandLabel)
    }

    @Test
    fun `parseSecurity identifies WPA3, WPA2, WPA, WEP, and open networks`() {
        assertEquals(true to "WPA3", WifiScannerHelper.parseSecurity("[WPA2-PSK-CCMP][RSN-SAE-CCMP][ESS]"))
        assertEquals(true to "WPA2", WifiScannerHelper.parseSecurity("[WPA2-PSK-CCMP][ESS]"))
        assertEquals(true to "WPA", WifiScannerHelper.parseSecurity("[WPA-PSK-TKIP][ESS]"))
        assertEquals(true to "WEP", WifiScannerHelper.parseSecurity("[WEP][ESS]"))
        assertEquals(false to "开放", WifiScannerHelper.parseSecurity("[ESS]"))
    }

    @Test
    fun `decodeTextRecordPayload decodes UTF-8 NDEF Text record with language code`() {
        val text = "{\"ssid\":\"IST_Demo\",\"password\":\"12345678\",\"uid\":\"04:E2:8A:19:4C:72:01\"}"
        val langBytes = "en".toByteArray(Charsets.US_ASCII)
        val textBytes = text.toByteArray(Charsets.UTF_8)
        val payload = ByteArray(1 + langBytes.size + textBytes.size)
        payload[0] = langBytes.size.toByte()
        System.arraycopy(langBytes, 0, payload, 1, langBytes.size)
        System.arraycopy(textBytes, 0, payload, 1 + langBytes.size, textBytes.size)

        val (lang, decoded) = NfcHelper.decodeTextRecordPayload(payload)
        assertEquals("en", lang)
        assertEquals(text, decoded)
    }

    @Test
    fun `formatAsciiPreview masks non-printable bytes with dots`() {
        val sample = byteArrayOf(0x03, 0x10, 'I'.code.toByte(), 'S'.code.toByte(), 'T'.code.toByte(), 0xFE.toByte())
        assertEquals("..IST.", NfcHelper.formatAsciiPreview(sample))
    }

    @Test
    fun `formatAsciiPreview extracts readable continuous ASCII from NDEF TLV sequence`() {
        val text = "{\"ssid\":\"IST\"}"
        val header = byteArrayOf(0x03, 0x14, 0xD1.toByte(), 0x01, 0x10, 0x54, 0x02, 'e'.code.toByte(), 'n'.code.toByte())
        val terminator = byteArrayOf(0xFE.toByte(), 0x00, 0x00)
        val fullTlv = header + text.toByteArray(Charsets.UTF_8) + terminator
        assertEquals(".....T.en{\"ssid\":\"IST\"}...", NfcHelper.formatAsciiPreview(fullTlv))
    }

    @Test
    fun `formatHexDump and parseColonHexUid format bytes cleanly`() {
        val uidBytes = NfcHelper.parseColonHexUid("04:E2:8A:19:4C:72:01")!!
        assertEquals(7, uidBytes.size)
        assertEquals("0000: 04 E2 8A 19 4C 72 01", NfcHelper.formatHexDump(uidBytes, 8))

        val tlv = NfcHelper.buildType2TlvFrame(byteArrayOf(0xD1.toByte(), 0x01, 0x02))
        assertEquals("0000: 03 03 D1 01 02 FE", NfcHelper.formatHexDump(tlv, 8))
    }

    @Test
    fun `resolveLogHexAndAscii derives HEX and ASCII for legacy logs missing saved fields`() {
        val legacy = com.example.model.NfcLogEntry(
            id = "legacy-1",
            timestamp = 1700000000000L,
            type = com.example.model.NfcLogType.READ_TAG,
            uid = "1D:8F:EA:48:00:00:12",
            deviceName = null,
            summary = "旧版本读卡记录",
            payload = "SSID=IST;PWD=12345678",
            byteCount = 21,
            isSuccess = true
        )
        val (hex, ascii) = NfcHelper.resolveLogHexAndAscii(legacy)
        assertTrue(hex.contains("1D 8F EA 48 00 00 12"))
        assertTrue(hex.contains("53 53 49 44 3D 49 53 54")) // "SSID=IST"
        assertTrue(ascii.contains("SSID=IST;PWD=12345678"))
    }

    @Test
    fun `buildClearLogHexAndAscii formats empty NDEF TLV and zeroed pages`() {
        val cleared = byteArrayOf(0x03, 0x00, 0xFE.toByte(), 0x00, 0x00, 0x00, 0x00, 0x00)
        val (hex, ascii) = NfcHelper.buildClearLogHexAndAscii(
            uid = "1D:8F:EA:48:00:00:12",
            clearedPageCount = 2,
            writtenBytes = cleared
        )
        assertTrue(hex.contains("1D 8F EA 48 00 00 12"))
        assertTrue(hex.contains("0000: 03 00 FE 00 00 00 00 00"))
        assertTrue(ascii.contains("........"))
    }
}
