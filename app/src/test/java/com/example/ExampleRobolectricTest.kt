package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.data.ProvisioningRepository
import com.example.model.NdefPayloadFormat
import com.example.model.NfcLogType
import com.example.model.ProvisionStatus
import com.example.model.ProvisionStep
import com.example.nfc.NfcWriteResult
import com.example.viewmodel.ProvisionerViewModel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class ExampleRobolectricTest {

    @Test
    fun `app name matches IST provisioner`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val appName = context.getString(R.string.app_name)
        assertEquals("富创通 IST 配网助手", appName)
    }

    @Test
    fun `initial repository state is completely empty`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val repo = ProvisioningRepository(context)
        assertTrue(repo.loadDevices().isEmpty())
        assertTrue(repo.loadLogs().isEmpty())
        assertEquals(NdefPayloadFormat.JSON, repo.loadPreferredFormat())
    }

    @Test
    fun `bind provision rename persist across simulated app restart`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val repo1 = ProvisioningRepository(context)
        val vm1 = ProvisionerViewModel(repo1)

        // 1. Bind a new device via scanned UID
        vm1.openBindDialog()
        vm1.onTagScannedForBind("04:E2:8A:19:4C:72:01", 7)
        vm1.updateBindDeviceName("前台迎宾屏")
        vm1.confirmBindDevice()

        assertEquals(1, vm1.devices.value.size)
        assertEquals(ProvisionStatus.PENDING, vm1.devices.value.first().status)
        assertEquals(1, vm1.logs.value.size)
        assertEquals(NfcLogType.READ_UID, vm1.logs.value.first().type)

        // 2. Duplicate UID must be blocked
        vm1.openBindDialog()
        vm1.onTagScannedForBind("04:E2:8A:19:4C:72:01", 7)
        assertEquals("前台迎宾屏", vm1.bindDialog.value.duplicateDeviceName)
        vm1.closeBindDialog()

        // 3. Provision Wi-Fi (including validation checks)
        val device = vm1.devices.value.first()
        vm1.openProvisionDialog(device)
        vm1.updateProvisionSsid("IST_Office_5G")
        vm1.updateProvisionPassword("1234567") // too short (< 8)
        vm1.startWaitingForWriteTap()
        assertNotNull(vm1.provisionDialog.value.passwordError)
        assertEquals(ProvisionStep.FORM, vm1.provisionDialog.value.step)

        vm1.updateProvisionPassword("ist123456")
        vm1.updateProvisionFormat(NdefPayloadFormat.KEY_VALUE)
        vm1.startWaitingForWriteTap()
        assertEquals(ProvisionStep.WAITING_TAP, vm1.provisionDialog.value.step)

        val payload = vm1.currentWritePayload()!!
        vm1.onWriteResult(
            NfcWriteResult.Success(
                uid = device.uid,
                payload = payload,
                ndefMessageBytes = 34
            )
        )
        assertEquals(ProvisionStep.SUCCESS, vm1.provisionDialog.value.step)
        vm1.closeProvisionDialog()

        // 4. Simulate app kill & cold restart with a brand new Repository & ViewModel
        val repo2 = ProvisioningRepository(context)
        val vm2 = ProvisionerViewModel(repo2)

        assertEquals(1, vm2.devices.value.size)
        val restored = vm2.devices.value.first()
        assertEquals("04:E2:8A:19:4C:72:01", restored.uid)
        assertEquals("前台迎宾屏", restored.name)
        assertEquals(ProvisionStatus.PROVISIONED, restored.status)
        assertEquals("IST_Office_5G", restored.wifiSsid)
        assertEquals("ist123456", restored.wifiPassword)
        assertEquals(NdefPayloadFormat.KEY_VALUE, vm2.preferredFormat.value)
        assertEquals(2, vm2.logs.value.size)
        assertEquals(NfcLogType.WRITE_WIFI, vm2.logs.value.first().type)

        // 5. Rename & delete persist across restart
        assertTrue(vm2.renameDevice(restored.id, "展厅主屏"))
        val vm3 = ProvisionerViewModel(ProvisioningRepository(context))
        assertEquals("展厅主屏", vm3.devices.value.first().name)

        vm3.deleteDevice(restored.id)
        vm3.clearLogs()
        val vm4 = ProvisionerViewModel(ProvisioningRepository(context))
        assertTrue(vm4.devices.value.isEmpty())
        assertTrue(vm4.logs.value.isEmpty())
        assertNull(vm4.provisionDialog.value.device)
    }

    @Test
    fun `decodeNdefRecord populates both payloadHex and payloadAscii`() {
        val payloadText = "{\"ssid\":\"IST_Display\",\"password\":\"12345678\"}"
        val record = android.nfc.NdefRecord.createTextRecord("en", payloadText)
        val parsed = com.example.nfc.NfcHelper.decodeNdefRecord(1, record)

        assertEquals(payloadText, parsed.decodedText)
        assertEquals("en", parsed.languageCode)
        assertTrue(parsed.payloadHex.startsWith("02 65 6E "))
        assertEquals(".en$payloadText", parsed.payloadAscii)
    }

    @Test
    fun `saved logs persist detailed hexData and asciiData across restart`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val vm1 = ProvisionerViewModel(ProvisioningRepository(context))

        vm1.openBindDialog()
        vm1.onTagScannedForBind("1D:8F:EA:48:00:00:12", 7)
        vm1.updateBindDeviceName("测试商显屏")
        vm1.confirmBindDevice()

        val device = vm1.devices.value.first()
        vm1.openProvisionDialog(device)
        vm1.updateProvisionSsid("ist-network")
        vm1.updateProvisionPassword("12344321")
        vm1.startWaitingForWriteTap()
        val payload = vm1.currentWritePayload()!!
        assertTrue(payload.contains("\"uid\":\"1D8FEA48000012\""))
        assertTrue(payload.contains("\"vendor\":\"IST\""))
        assertTrue(payload.contains("\"ts\":\""))
        val ndefBytes = android.nfc.NdefMessage(
            arrayOf(android.nfc.NdefRecord.createTextRecord("en", payload))
        ).toByteArray()

        vm1.onWriteResult(
            NfcWriteResult.Success(
                uid = device.uid,
                payload = payload,
                ndefMessageBytes = ndefBytes.size,
                rawNdefBytes = ndefBytes
            )
        )

        val vm2 = ProvisionerViewModel(ProvisioningRepository(context))
        val writeLog = vm2.logs.value.first { it.type == NfcLogType.WRITE_WIFI }
        assertNotNull(writeLog.hexData)
        assertNotNull(writeLog.asciiData)
        assertTrue(writeLog.hexData!!.contains("03 "))
        assertTrue(writeLog.asciiData!!.contains("1D8FEA48000012"))
        assertTrue(writeLog.asciiData.contains("IST"))
        assertTrue(writeLog.asciiData.contains("\"ts\":\""))
        assertTrue(writeLog.asciiData.contains("ist-network"))
        assertTrue(writeLog.asciiData.contains("12344321"))

        val bindLog = vm2.logs.value.first { it.type == NfcLogType.READ_UID }
        assertTrue(bindLog.hexData!!.contains("1D 8F EA 48 00 00 12"))
        assertTrue(bindLog.asciiData!!.contains("UID=1D:8F:EA:48:00:00:12"))
    }

    @Test
    fun `clearing NFC data resets provisioned device to pending and logs CLEAR_TAG`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val vm1 = ProvisionerViewModel(ProvisioningRepository(context))

        // Bind & provision
        vm1.openBindDialog()
        vm1.onTagScannedForBind("1D:8F:EA:48:00:00:12", 7)
        vm1.updateBindDeviceName("会议室门牌")
        vm1.confirmBindDevice()

        val device = vm1.devices.value.first()
        vm1.openProvisionDialog(device)
        vm1.updateProvisionSsid("IST_Guest")
        vm1.updateProvisionPassword("12345678")
        vm1.startWaitingForWriteTap()
        vm1.onWriteResult(
            NfcWriteResult.Success(
                uid = device.uid,
                payload = vm1.currentWritePayload()!!,
                ndefMessageBytes = 40
            )
        )
        assertEquals(ProvisionStatus.PROVISIONED, vm1.devices.value.first().status)

        // Clear via ProvisionDialog
        vm1.openProvisionDialog(vm1.devices.value.first())
        vm1.startWaitingForClearTap()
        assertEquals(ProvisionStep.WAITING_CLEAR_TAP, vm1.provisionDialog.value.step)
        val clearedBytes = byteArrayOf(0x03, 0x00, 0xFE.toByte(), 0x00, 0x00, 0x00, 0x00, 0x00)
        vm1.onClearResult(
            com.example.nfc.NfcClearResult.Success(
                uid = device.uid,
                clearedPageCount = 2,
                writtenBytes = clearedBytes,
                methodDescription = "已擦除 2 页用户区 EEPROM，并重置为空 NDEF (03 00 FE 00)"
            )
        )
        assertEquals(ProvisionStep.CLEAR_SUCCESS, vm1.provisionDialog.value.step)
        assertEquals(ProvisionStatus.PENDING, vm1.devices.value.first().status)
        assertNull(vm1.devices.value.first().wifiSsid)
        assertNull(vm1.devices.value.first().wifiPassword)
        vm1.closeProvisionDialog()

        // Verify persistence after restart
        val vm2 = ProvisionerViewModel(ProvisioningRepository(context))
        assertEquals(ProvisionStatus.PENDING, vm2.devices.value.first().status)
        val clearLog = vm2.logs.value.first { it.type == NfcLogType.CLEAR_TAG }
        assertTrue(clearLog.hexData!!.contains("03 00 FE 00"))
        assertTrue(clearLog.asciiData!!.contains("........"))

        // Also test clearing from TagInspector in erase mode
        vm2.setTagInspectorEraseMode(true)
        assertTrue(vm2.tagInspector.value.isEraseMode)
        vm2.onTagClearedInInspector(
            com.example.nfc.NfcClearResult.Success(
                uid = "04:AA:BB:CC:DD:EE:01",
                clearedPageCount = 4,
                writtenBytes = clearedBytes,
                methodDescription = "已擦除 4 页用户区 EEPROM"
            )
        )
        assertTrue(!vm2.tagInspector.value.isEraseMode)
        assertNotNull(vm2.tagInspector.value.lastClearMessage)
    }
}
