package com.example.nfc

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.nfc.FormatException
import android.nfc.NdefMessage
import android.nfc.NdefRecord
import android.nfc.NfcAdapter
import android.nfc.Tag
import android.nfc.TagLostException
import android.nfc.tech.Ndef
import android.nfc.tech.NdefFormatable
import android.nfc.tech.NfcA
import android.provider.Settings
import com.example.model.FM11NT083C_USER_EEPROM_BYTES
import com.example.model.NfcAvailability
import com.example.model.NfcLogEntry
import com.example.model.NfcLogType
import com.example.model.ParsedNdefRecord
import com.example.model.RawPageEntry
import com.example.model.TagInspectResult
import java.io.IOException
import java.nio.charset.Charset

sealed class NfcWriteResult {
    data class Success(
        val uid: String,
        val payload: String,
        val ndefMessageBytes: Int,
        val rawNdefBytes: ByteArray = ByteArray(0)
    ) : NfcWriteResult() {
        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (other !is Success) return false
            return uid == other.uid &&
                payload == other.payload &&
                ndefMessageBytes == other.ndefMessageBytes &&
                rawNdefBytes.contentEquals(other.rawNdefBytes)
        }

        override fun hashCode(): Int {
            var result = uid.hashCode()
            result = 31 * result + payload.hashCode()
            result = 31 * result + ndefMessageBytes
            result = 31 * result + rawNdefBytes.contentHashCode()
            return result
        }
    }

    data class Failure(
        val scannedUid: String?,
        val message: String
    ) : NfcWriteResult()
}

sealed class TagReadOutcome {
    data class Success(val result: TagInspectResult) : TagReadOutcome()
    data class Failure(
        val scannedUid: String?,
        val message: String
    ) : TagReadOutcome()
}

sealed class NfcClearResult {
    data class Success(
        val uid: String,
        val clearedPageCount: Int,
        val writtenBytes: ByteArray = ByteArray(0),
        val methodDescription: String
    ) : NfcClearResult() {
        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (other !is Success) return false
            return uid == other.uid &&
                clearedPageCount == other.clearedPageCount &&
                writtenBytes.contentEquals(other.writtenBytes) &&
                methodDescription == other.methodDescription
        }

        override fun hashCode(): Int {
            var result = uid.hashCode()
            result = 31 * result + clearedPageCount
            result = 31 * result + writtenBytes.contentHashCode()
            result = 31 * result + methodDescription.hashCode()
            return result
        }
    }

    data class Failure(
        val scannedUid: String?,
        val message: String
    ) : NfcClearResult()
}

object NfcHelper {

    const val READER_FLAGS =
        NfcAdapter.FLAG_READER_NFC_A or
            NfcAdapter.FLAG_READER_NFC_B or
            NfcAdapter.FLAG_READER_NFC_F or
            NfcAdapter.FLAG_READER_NFC_V

    fun getNfcAvailability(context: Context): NfcAvailability {
        val adapter = NfcAdapter.getDefaultAdapter(context) ?: return NfcAvailability.NOT_SUPPORTED
        return if (adapter.isEnabled) NfcAvailability.ENABLED else NfcAvailability.DISABLED
    }

    fun openNfcSettings(context: Context) {
        val nfcIntent = Intent(Settings.ACTION_NFC_SETTINGS).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        try {
            context.startActivity(nfcIntent)
        } catch (_: Exception) {
            val wirelessIntent = Intent(Settings.ACTION_WIRELESS_SETTINGS).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            runCatching { context.startActivity(wirelessIntent) }
        }
    }

    fun enableReaderMode(
        activity: Activity,
        callback: NfcAdapter.ReaderCallback
    ): Boolean {
        val adapter = NfcAdapter.getDefaultAdapter(activity) ?: return false
        if (!adapter.isEnabled) return false
        return try {
            adapter.enableReaderMode(activity, callback, READER_FLAGS, null)
            true
        } catch (_: Exception) {
            false
        }
    }

    fun disableReaderMode(activity: Activity) {
        val adapter = NfcAdapter.getDefaultAdapter(activity) ?: return
        runCatching { adapter.disableReaderMode(activity) }
    }

    fun formatUid(idBytes: ByteArray?): String {
        if (idBytes == null || idBytes.isEmpty()) return ""
        return idBytes.joinToString(":") { "%02X".format(it) }
    }

    fun writeNdefTextToTag(
        tag: Tag,
        expectedUid: String,
        payload: String
    ): NfcWriteResult {
        val actualUid = formatUid(tag.id)
        if (actualUid.isEmpty()) {
            return NfcWriteResult.Failure(
                scannedUid = null,
                message = "未能读取到芯片 UID，请将手机重新贴紧设备 NFC 感应区"
            )
        }
        if (!actualUid.equals(expectedUid, ignoreCase = true)) {
            return NfcWriteResult.Failure(
                scannedUid = actualUid,
                message = "UID 不匹配：检测到 $actualUid，目标设备为 $expectedUid，请刷取对应设备"
            )
        }

        val record = NdefRecord.createTextRecord("en", payload)
        val message = NdefMessage(arrayOf(record))
        val messageBytes = message.toByteArray()

        if (messageBytes.size > FM11NT083C_USER_EEPROM_BYTES) {
            return NfcWriteResult.Failure(
                scannedUid = actualUid,
                message = "报文超出 FM11NT083C 用户区容量 (${messageBytes.size} / $FM11NT083C_USER_EEPROM_BYTES Bytes)，请精简 SSID 或密码"
            )
        }

        val ndef = Ndef.get(tag)
        if (ndef != null) {
            return try {
                ndef.connect()
                if (!ndef.isWritable) {
                    NfcWriteResult.Failure(
                        scannedUid = actualUid,
                        message = "芯片处于只读/写保护状态，无法写入配网信息"
                    )
                } else if (ndef.maxSize > 0 && messageBytes.size > ndef.maxSize) {
                    NfcWriteResult.Failure(
                        scannedUid = actualUid,
                        message = "数据大小 (${messageBytes.size} B) 超过芯片可用 NDEF 容量 (${ndef.maxSize} B)"
                    )
                } else {
                    ndef.writeNdefMessage(message)
                    NfcWriteResult.Success(
                        uid = actualUid,
                        payload = payload,
                        ndefMessageBytes = messageBytes.size,
                        rawNdefBytes = messageBytes
                    )
                }
            } catch (_: TagLostException) {
                NfcWriteResult.Failure(
                    scannedUid = actualUid,
                    message = "写入中断：NFC 芯片移开过快，请贴紧感应区保持 1~2 秒重试"
                )
            } catch (_: FormatException) {
                NfcWriteResult.Failure(
                    scannedUid = actualUid,
                    message = "NDEF 报文格式异常，写入失败"
                )
            } catch (e: IOException) {
                NfcWriteResult.Failure(
                    scannedUid = actualUid,
                    message = "NFC 通信 I/O 异常（${e.localizedMessage ?: "连接断开"}），请重新贴近写入"
                )
            } catch (e: Exception) {
                NfcWriteResult.Failure(
                    scannedUid = actualUid,
                    message = "写入失败：${e.localizedMessage ?: "未知错误"}"
                )
            } finally {
                runCatching { ndef.close() }
            }
        }

        val formatable = NdefFormatable.get(tag)
        if (formatable != null) {
            return try {
                formatable.connect()
                formatable.format(message)
                NfcWriteResult.Success(
                    uid = actualUid,
                    payload = payload,
                    ndefMessageBytes = messageBytes.size,
                    rawNdefBytes = messageBytes
                )
            } catch (_: TagLostException) {
                NfcWriteResult.Failure(
                    scannedUid = actualUid,
                    message = "格式化写入中断：NFC 芯片移开过快，请贴紧感应区重试"
                )
            } catch (_: FormatException) {
                NfcWriteResult.Failure(
                    scannedUid = actualUid,
                    message = "芯片 NDEF 格式化失败，请确认标签符合 Type 2 Tag 规范"
                )
            } catch (e: IOException) {
                NfcWriteResult.Failure(
                    scannedUid = actualUid,
                    message = "空白芯片格式化 I/O 异常（${e.localizedMessage ?: "连接断开"}），请重试"
                )
            } catch (e: Exception) {
                NfcWriteResult.Failure(
                    scannedUid = actualUid,
                    message = "芯片格式化写入失败：${e.localizedMessage ?: "未知错误"}"
                )
            } finally {
                runCatching { formatable.close() }
            }
        }

        return NfcWriteResult.Failure(
            scannedUid = actualUid,
            message = "当前 NFC 标签不支持 NDEF 写入或未初始化 CC 区（请检查 FM11NT083C）"
        )
    }

    fun clearTagData(
        tag: Tag,
        expectedUid: String? = null
    ): NfcClearResult {
        val actualUid = formatUid(tag.id)
        if (actualUid.isEmpty()) {
            return NfcClearResult.Failure(
                scannedUid = null,
                message = "未能读取到芯片 UID，请将手机重新贴紧设备 NFC 感应区"
            )
        }
        if (expectedUid != null && !actualUid.equals(expectedUid, ignoreCase = true)) {
            return NfcClearResult.Failure(
                scannedUid = actualUid,
                message = "UID 不匹配：检测到 $actualUid，目标设备为 $expectedUid，请刷取对应设备"
            )
        }

        val ndef = Ndef.get(tag)
        if (ndef != null) {
            val writableCheck = try {
                ndef.connect()
                if (!ndef.isWritable) {
                    "芯片处于只读/写保护状态，无法清空数据"
                } else {
                    null
                }
            } catch (_: TagLostException) {
                return NfcClearResult.Failure(
                    scannedUid = actualUid,
                    message = "检测芯片状态中断：请贴紧感应区保持 1~2 秒重试"
                )
            } catch (e: IOException) {
                return NfcClearResult.Failure(
                    scannedUid = actualUid,
                    message = "NFC 通信 I/O 异常（${e.localizedMessage ?: "连接断开"}），请重试"
                )
            } finally {
                runCatching { ndef.close() }
            }
            if (writableCheck != null) {
                return NfcClearResult.Failure(scannedUid = actualUid, message = writableCheck)
            }
        }

        // Primary path: Type 2 Tag page-level EEPROM wipe (Page 0x04 -> Empty NDEF TLV 03 00 FE 00, Page 0x05+ -> 00 00 00 00)
        val nfcA = NfcA.get(tag)
        if (nfcA != null) {
            var pagesCleared = 0
            val writtenBuffer = mutableListOf<Byte>()
            var type2Supported = false
            try {
                nfcA.connect()
                nfcA.timeout = 500

                var maxUserPageExclusive = 0xE0
                val headerRead = nfcA.transceive(byteArrayOf(0x30.toByte(), 0x00.toByte()))
                if (headerRead != null && headerRead.size >= 16) {
                    type2Supported = true
                    if ((headerRead[12].toInt() and 0xFF) == 0xE1) {
                        val userBytes = (headerRead[14].toInt() and 0xFF) * 8
                        if (userBytes >= 16) {
                            maxUserPageExclusive = minOf(4 + userBytes / 4, 0xE0)
                        }
                    }
                }

                if (type2Supported) {
                    val pagesToWipe = linkedSetOf(4)
                    var consecutiveZeroBlocks = 0
                    var startPage = 4
                    while (startPage < maxUserPageExclusive) {
                        val block = runCatching {
                            nfcA.transceive(byteArrayOf(0x30.toByte(), startPage.toByte()))
                        }.getOrNull() ?: break
                        if (block.size < 16) break

                        var blockAllZero = true
                        for (offset in 0 until 4) {
                            val pageIdx = startPage + offset
                            if (pageIdx >= maxUserPageExclusive) break
                            val slice = block.copyOfRange(offset * 4, (offset + 1) * 4)
                            if (slice.any { it.toInt() != 0 }) {
                                blockAllZero = false
                                pagesToWipe.add(pageIdx)
                            }
                        }
                        if (blockAllZero && startPage >= 16) {
                            consecutiveZeroBlocks++
                            if (consecutiveZeroBlocks >= 3) break
                        } else {
                            consecutiveZeroBlocks = 0
                        }
                        startPage += 4
                    }

                    val emptyTlvPage = byteArrayOf(0x03.toByte(), 0x00.toByte(), 0xFE.toByte(), 0x00.toByte())
                    val zeroPage = byteArrayOf(0x00.toByte(), 0x00.toByte(), 0x00.toByte(), 0x00.toByte())

                    for (pageIdx in pagesToWipe.sorted()) {
                        val content = if (pageIdx == 4) emptyTlvPage else zeroPage
                        val cmd = byteArrayOf(0xA2.toByte(), pageIdx.toByte()) + content
                        nfcA.transceive(cmd)
                        pagesCleared++
                        writtenBuffer.addAll(content.toList())
                    }

                    return NfcClearResult.Success(
                        uid = actualUid,
                        clearedPageCount = pagesCleared,
                        writtenBytes = writtenBuffer.toByteArray(),
                        methodDescription = "已擦除 $pagesCleared 页用户区 EEPROM，并重置为空 NDEF (03 00 FE 00)"
                    )
                }
            } catch (_: TagLostException) {
                return NfcClearResult.Failure(
                    scannedUid = actualUid,
                    message = "清空中断：NFC 芯片移开过快（已擦除 $pagesCleared 页），请贴紧保持 1~2 秒重试"
                )
            } catch (_: IOException) {
                // Fall through to NDEF empty-record fallback if Type 2 0x30/0xA2 is unsupported
            } finally {
                runCatching { nfcA.close() }
            }
        }

        // Fallback path for non-Type-2 NDEF tags
        val emptyMsg = NdefMessage(
            arrayOf(NdefRecord(NdefRecord.TNF_EMPTY, ByteArray(0), ByteArray(0), ByteArray(0)))
        )
        val emptyBytes = emptyMsg.toByteArray()
        if (ndef != null) {
            return try {
                ndef.connect()
                ndef.writeNdefMessage(emptyMsg)
                NfcClearResult.Success(
                    uid = actualUid,
                    clearedPageCount = 0,
                    writtenBytes = emptyBytes,
                    methodDescription = "已写入空 NDEF 记录以覆盖原有信息"
                )
            } catch (_: TagLostException) {
                NfcClearResult.Failure(
                    scannedUid = actualUid,
                    message = "清空中断：NFC 芯片移开过快，请重新贴近保持 1~2 秒"
                )
            } catch (e: Exception) {
                NfcClearResult.Failure(
                    scannedUid = actualUid,
                    message = "清空失败：${e.localizedMessage ?: "未知错误"}"
                )
            } finally {
                runCatching { ndef.close() }
            }
        }

        val formatable = NdefFormatable.get(tag)
        if (formatable != null) {
            return try {
                formatable.connect()
                formatable.format(emptyMsg)
                NfcClearResult.Success(
                    uid = actualUid,
                    clearedPageCount = 0,
                    writtenBytes = emptyBytes,
                    methodDescription = "已将空白芯片格式化为空 NDEF 状态"
                )
            } catch (e: Exception) {
                NfcClearResult.Failure(
                    scannedUid = actualUid,
                    message = "格式化清空失败：${e.localizedMessage ?: "未知错误"}"
                )
            } finally {
                runCatching { formatable.close() }
            }
        }

        return NfcClearResult.Failure(
            scannedUid = actualUid,
            message = "当前标签不支持清空操作，请确认芯片为可读写的 FM11NT083C / Type 2 标签"
        )
    }

    fun readTagDetails(tag: Tag): TagReadOutcome {
        val rawId = tag.id
        val uid = formatUid(rawId)
        if (uid.isEmpty()) {
            return TagReadOutcome.Failure(
                scannedUid = null,
                message = "未能读取到芯片 UID，请重新贴紧 NFC 感应区"
            )
        }
        val uidRawHex = rawId.joinToString("") { "%02X".format(it) }
        val techList = tag.techList.map { it.substringAfterLast('.') }

        val nfcA = NfcA.get(tag)
        val atqaHex = nfcA?.atqa?.joinToString(" ") { "%02X".format(it) }
        val sakHex = nfcA?.sak?.let { "%02X".format(it.toInt() and 0xFF) }

        var isNdefFormatted = false
        val isNdefFormatable = NdefFormatable.get(tag) != null
        var ndefType: String? = null
        var isWritable: Boolean? = null
        var canMakeReadOnly: Boolean? = null
        var ndefMaxBytes: Int? = null
        var ndefUsedBytes = 0
        var records = emptyList<ParsedNdefRecord>()
        val notes = mutableListOf<String>()

        val ndef = Ndef.get(tag)
        if (ndef != null) {
            isNdefFormatted = true
            ndefType = ndef.type
            isWritable = ndef.isWritable
            canMakeReadOnly = ndef.canMakeReadOnly()
            ndefMaxBytes = ndef.maxSize
            try {
                ndef.connect()
                val msg = ndef.ndefMessage ?: ndef.cachedNdefMessage
                if (msg != null) {
                    ndefUsedBytes = msg.toByteArray().size
                    records = msg.records.mapIndexed { index, record ->
                        decodeNdefRecord(index + 1, record)
                    }
                }
            } catch (_: TagLostException) {
                return TagReadOutcome.Failure(
                    scannedUid = uid,
                    message = "读取 NDEF 中断：芯片移开过快，请贴紧感应区保持 1 秒重试"
                )
            } catch (e: FormatException) {
                notes.add("NDEF 内容解析异常：${e.localizedMessage ?: "格式损坏"}")
            } catch (e: IOException) {
                notes.add("读取 NDEF I/O 异常：${e.localizedMessage ?: "连接断开"}")
            } finally {
                runCatching { ndef.close() }
            }
        }

        // Read Type 2 Tag raw EEPROM pages (up to Page 0x00 ~ 0x3F, 4 pages / 16 bytes per READ 0x30 command)
        val rawPages = mutableListOf<RawPageEntry>()
        val allEepromBytes = mutableListOf<Byte>()
        val userAreaBytes = mutableListOf<Byte>()
        if (nfcA != null) {
            try {
                nfcA.connect()
                nfcA.timeout = 400
                var seenTerminator = false
                for (startPage in 0 until 64 step 4) {
                    val cmd = byteArrayOf(0x30.toByte(), startPage.toByte())
                    val response = nfcA.transceive(cmd)
                    if (response != null && response.size >= 16) {
                        var blockAllZero = true
                        for (offset in 0 until 4) {
                            val pageIdx = startPage + offset
                            val slice = response.copyOfRange(offset * 4, (offset + 1) * 4)
                            if (slice.any { it.toInt() != 0 }) {
                                blockAllZero = false
                            }
                            if (pageIdx >= 4 && slice.any { (it.toInt() and 0xFF) == 0xFE }) {
                                seenTerminator = true
                            }
                            rawPages.add(
                                RawPageEntry(
                                    pageIndex = pageIdx,
                                    dataHex = slice.joinToString(" ") { "%02X".format(it) },
                                    asciiPreview = formatAsciiPreview(slice),
                                    note = pageRoleNote(pageIdx)
                                )
                            )
                            allEepromBytes.addAll(slice.toList())
                            if (pageIdx >= 4) {
                                userAreaBytes.addAll(slice.toList())
                            }
                        }
                        if (seenTerminator && blockAllZero && startPage >= 16) {
                            break
                        }
                    } else {
                        break
                    }
                }
            } catch (_: TagLostException) {
                if (rawPages.isEmpty()) {
                    notes.add("卡片移开较快，未完整读出 EEPROM 页数据")
                }
            } catch (_: IOException) {
                // Tag may not support Type 2 READ 0x30 (e.g., ISO-DEP / Mifare Classic); ignore gracefully
            } catch (_: Exception) {
                // Keep already-read NDEF & UID info
            } finally {
                runCatching { nfcA.close() }
            }
        }

        val rawEepromAscii = formatAsciiPreview(allEepromBytes.toByteArray())
        val userAreaAscii = formatAsciiPreview(userAreaBytes.toByteArray())

        return TagReadOutcome.Success(
            TagInspectResult(
                readAt = System.currentTimeMillis(),
                uid = uid,
                uidRawHex = uidRawHex,
                uidByteCount = rawId.size,
                techList = techList,
                atqaHex = atqaHex,
                sakHex = sakHex,
                isNdefFormatted = isNdefFormatted,
                isNdefFormatable = isNdefFormatable,
                ndefType = ndefType,
                isWritable = isWritable,
                canMakeReadOnly = canMakeReadOnly,
                ndefMaxBytes = ndefMaxBytes,
                ndefUsedBytes = ndefUsedBytes,
                records = records,
                rawPages = rawPages,
                rawEepromAscii = rawEepromAscii,
                userAreaAscii = userAreaAscii,
                note = notes.takeIf { it.isNotEmpty() }?.joinToString("；")
            )
        )
    }

    fun decodeNdefRecord(index: Int, record: NdefRecord): ParsedNdefRecord {
        val payload = record.payload ?: ByteArray(0)
        val payloadHex = payload.joinToString(" ") { "%02X".format(it) }
        val payloadAscii = formatAsciiPreview(payload)
        val tnfLabel = when (record.tnf) {
            NdefRecord.TNF_EMPTY -> "Empty"
            NdefRecord.TNF_WELL_KNOWN -> "Well-Known"
            NdefRecord.TNF_MIME_MEDIA -> "MIME Media"
            NdefRecord.TNF_ABSOLUTE_URI -> "Absolute URI"
            NdefRecord.TNF_EXTERNAL_TYPE -> "External"
            NdefRecord.TNF_UNCHANGED -> "Unchanged"
            else -> "Unknown (${record.tnf})"
        }
        val typeBytes = record.type ?: ByteArray(0)
        val typeText = String(typeBytes, Charsets.US_ASCII)

        if (record.tnf == NdefRecord.TNF_WELL_KNOWN && typeBytes.contentEquals(NdefRecord.RTD_TEXT)) {
            val (lang, text) = decodeTextRecordPayload(payload)
            return ParsedNdefRecord(
                index = index,
                tnfLabel = tnfLabel,
                typeLabel = "T (Text)",
                languageCode = lang,
                decodedText = text,
                payloadHex = payloadHex,
                payloadAscii = payloadAscii,
                payloadBytes = payload.size
            )
        }

        if (record.tnf == NdefRecord.TNF_WELL_KNOWN && typeBytes.contentEquals(NdefRecord.RTD_URI)) {
            val uriText = runCatching { record.toUri()?.toString() }.getOrNull()
                ?: String(payload, Charsets.UTF_8)
            return ParsedNdefRecord(
                index = index,
                tnfLabel = tnfLabel,
                typeLabel = "U (URI)",
                languageCode = null,
                decodedText = uriText,
                payloadHex = payloadHex,
                payloadAscii = payloadAscii,
                payloadBytes = payload.size
            )
        }

        val fallbackText = if (payload.isEmpty()) {
            "(空负载)"
        } else {
            String(payload, Charsets.UTF_8)
        }
        return ParsedNdefRecord(
            index = index,
            tnfLabel = tnfLabel,
            typeLabel = typeText.ifBlank { "未知类型" },
            languageCode = null,
            decodedText = fallbackText,
            payloadHex = payloadHex,
            payloadAscii = payloadAscii,
            payloadBytes = payload.size
        )
    }

    fun decodeTextRecordPayload(payload: ByteArray): Pair<String?, String> {
        if (payload.isEmpty()) return null to ""
        val status = payload[0].toInt() and 0xFF
        val isUtf16 = (status and 0x80) != 0
        val langLen = status and 0x3F
        if (1 + langLen > payload.size) {
            return null to String(payload, Charsets.UTF_8)
        }
        val lang = if (langLen > 0) {
            String(payload, 1, langLen, Charsets.US_ASCII)
        } else {
            null
        }
        val charset: Charset = if (isUtf16) Charsets.UTF_16 else Charsets.UTF_8
        val text = String(payload, 1 + langLen, payload.size - 1 - langLen, charset)
        return lang to text
    }

    fun formatAsciiPreview(bytes: ByteArray): String {
        return bytes.joinToString("") { byte ->
            val code = byte.toInt() and 0xFF
            if (code in 0x20..0x7E) code.toChar().toString() else "."
        }
    }

    fun formatHexDump(bytes: ByteArray, bytesPerLine: Int = 8): String {
        if (bytes.isEmpty()) return ""
        val lines = mutableListOf<String>()
        var offset = 0
        while (offset < bytes.size) {
            val end = minOf(offset + bytesPerLine, bytes.size)
            val hexPart = (offset until end).joinToString(" ") { idx -> "%02X".format(bytes[idx]) }
            lines.add("%04X: %s".format(offset, hexPart))
            offset += bytesPerLine
        }
        return lines.joinToString("\n")
    }

    fun parseColonHexUid(uid: String?): ByteArray? {
        if (uid.isNullOrBlank()) return null
        val tokens = uid.trim().split(":", " ", "-").filter { it.isNotEmpty() }
        if (tokens.isEmpty()) return null
        return try {
            ByteArray(tokens.size) { i -> tokens[i].toInt(16).toByte() }
        } catch (_: Exception) {
            null
        }
    }

    fun buildType2TlvFrame(ndefMessageBytes: ByteArray): ByteArray {
        val len = ndefMessageBytes.size
        val header = if (len < 255) {
            byteArrayOf(0x03.toByte(), len.toByte())
        } else {
            byteArrayOf(
                0x03.toByte(),
                0xFF.toByte(),
                ((len ushr 8) and 0xFF).toByte(),
                (len and 0xFF).toByte()
            )
        }
        return header + ndefMessageBytes + byteArrayOf(0xFE.toByte())
    }

    fun buildBindLogHexAndAscii(uid: String, byteCount: Int): Pair<String, String> {
        val uidBytes = parseColonHexUid(uid) ?: ByteArray(0)
        val label = "UID=$uid"
        val labelBytes = label.toByteArray(Charsets.UTF_8)

        val hex = buildString {
            if (uidBytes.isNotEmpty()) {
                appendLine("[硬件 UID 原始字节 ($byteCount B)]")
                appendLine(formatHexDump(uidBytes, 8))
                appendLine()
            }
            appendLine("[记录文本 UTF-8 HEX (${labelBytes.size} B)]")
            append(formatHexDump(labelBytes, 8))
        }

        val ascii = buildString {
            if (uidBytes.isNotEmpty()) {
                appendLine("[硬件 UID ASCII 预览]")
                appendLine(formatAsciiPreview(uidBytes))
                appendLine()
            }
            appendLine("[记录文本 ASCII]")
            append(formatAsciiPreview(labelBytes))
        }
        return hex to ascii
    }

    fun buildWriteLogHexAndAscii(
        uid: String,
        payload: String,
        ndefMessageBytes: ByteArray = ByteArray(0)
    ): Pair<String, String> {
        val payloadUtf8 = payload.toByteArray(Charsets.UTF_8)
        val actualNdef = if (ndefMessageBytes.isNotEmpty()) {
            ndefMessageBytes
        } else {
            runCatching {
                NdefMessage(arrayOf(NdefRecord.createTextRecord("en", payload))).toByteArray()
            }.getOrDefault(ByteArray(0))
        }
        val tlvFrame = if (actualNdef.isNotEmpty()) buildType2TlvFrame(actualNdef) else ByteArray(0)

        val hex = buildString {
            if (tlvFrame.isNotEmpty()) {
                appendLine("[FM11NT083C TLV 帧 HEX (Page 0x04 起, ${tlvFrame.size} B)]")
                appendLine(formatHexDump(tlvFrame, 8))
                appendLine()
                appendLine("[NDEF 报文体 HEX (${actualNdef.size} B)]")
                appendLine(formatHexDump(actualNdef, 8))
                appendLine()
            }
            appendLine("[明文负载 UTF-8 HEX (${payloadUtf8.size} B)]")
            append(formatHexDump(payloadUtf8, 8))
        }

        val ascii = buildString {
            if (tlvFrame.isNotEmpty()) {
                appendLine("[FM11NT083C TLV 帧连续 ASCII]")
                appendLine(formatAsciiPreview(tlvFrame))
                appendLine()
            }
            appendLine("[明文负载 ASCII (${payloadUtf8.size} B)]")
            append(formatAsciiPreview(payloadUtf8))
        }
        return hex to ascii
    }

    fun buildInspectLogHexAndAscii(result: TagInspectResult): Pair<String, String> {
        val hex = buildString {
            val uidBytes = parseColonHexUid(result.uid)
            if (uidBytes != null && uidBytes.isNotEmpty()) {
                appendLine("[硬件 UID (${result.uidByteCount} B)]")
                appendLine(uidBytes.joinToString(" ") { "%02X".format(it) })
            }
            if (result.records.isNotEmpty()) {
                if (isNotEmpty()) appendLine()
                appendLine("[NDEF 记录负载 HEX]")
                result.records.forEach { rec ->
                    if (rec.payloadHex.isNotEmpty()) {
                        appendLine("#${rec.index} (${rec.payloadBytes} B): ${rec.payloadHex}")
                    }
                }
            }
            if (result.rawPages.isNotEmpty()) {
                if (isNotEmpty()) appendLine()
                appendLine("[EEPROM 分页 HEX (Page 0x00 ~ 0x%02X)]".format(result.rawPages.last().pageIndex))
                result.rawPages.forEach { page ->
                    appendLine("0x%02X: %-11s | %s".format(page.pageIndex, page.dataHex, page.note))
                }
            }
        }.trimEnd()

        val ascii = buildString {
            if (result.userAreaAscii.isNotEmpty()) {
                appendLine("[用户区连续 ASCII (Page 0x04 起)]")
                appendLine(result.userAreaAscii)
            } else if (result.rawEepromAscii.isNotEmpty()) {
                appendLine("[芯片连续 ASCII]")
                appendLine(result.rawEepromAscii)
            }
            if (result.records.isNotEmpty()) {
                if (isNotEmpty()) appendLine()
                appendLine("[NDEF 记录负载 ASCII]")
                result.records.forEach { rec ->
                    appendLine("#${rec.index}: ${rec.payloadAscii}")
                }
            }
            if (result.rawPages.isNotEmpty()) {
                if (isNotEmpty()) appendLine()
                appendLine("[EEPROM 分页 ASCII]")
                result.rawPages.forEach { page ->
                    appendLine("0x%02X: |%-4s| %s".format(page.pageIndex, page.asciiPreview, page.note))
                }
            }
        }.trimEnd()

        return hex to ascii
    }

    fun buildClearLogHexAndAscii(
        uid: String,
        clearedPageCount: Int,
        writtenBytes: ByteArray
    ): Pair<String, String> {
        val uidBytes = parseColonHexUid(uid) ?: ByteArray(0)
        val hex = buildString {
            if (uidBytes.isNotEmpty()) {
                appendLine("[硬件 UID (${uidBytes.size} B)]")
                appendLine(uidBytes.joinToString(" ") { "%02X".format(it) })
            }
            if (writtenBytes.isNotEmpty()) {
                if (isNotEmpty()) appendLine()
                val title = if (clearedPageCount > 0) {
                    "[清空后写入用户区数据 ($clearedPageCount 页, ${writtenBytes.size} B)]"
                } else {
                    "[清空后写入空 NDEF 数据 (${writtenBytes.size} B)]"
                }
                appendLine(title)
                append(formatHexDump(writtenBytes, 8))
            }
        }.trimEnd()

        val ascii = buildString {
            if (writtenBytes.isNotEmpty()) {
                appendLine("[清空后连续 ASCII (${writtenBytes.size} B)]")
                append(formatAsciiPreview(writtenBytes))
            } else {
                append("[已清空标签用户区写入数据]")
            }
        }.trimEnd()

        return hex to ascii
    }

    fun resolveLogHexAndAscii(entry: NfcLogEntry): Pair<String, String> {
        if (!entry.hexData.isNullOrBlank() && !entry.asciiData.isNullOrBlank()) {
            return entry.hexData to entry.asciiData
        }
        val uidBytes = parseColonHexUid(entry.uid)
        val payloadText = entry.payload
        if (entry.type == NfcLogType.WRITE_WIFI && !payloadText.isNullOrBlank()) {
            return buildWriteLogHexAndAscii(
                uid = entry.uid.orEmpty(),
                payload = payloadText
            )
        }
        if (entry.type == NfcLogType.READ_UID && !entry.uid.isNullOrBlank()) {
            return buildBindLogHexAndAscii(
                uid = entry.uid,
                byteCount = entry.byteCount ?: uidBytes?.size ?: 0
            )
        }

        val hex = buildString {
            if (uidBytes != null && uidBytes.isNotEmpty()) {
                appendLine("[硬件 UID 原始字节 (${uidBytes.size} B)]")
                appendLine(formatHexDump(uidBytes, 8))
            }
            if (!payloadText.isNullOrBlank()) {
                val pBytes = payloadText.toByteArray(Charsets.UTF_8)
                if (isNotEmpty()) appendLine()
                appendLine("[报文 UTF-8 HEX (${pBytes.size} B)]")
                append(formatHexDump(pBytes, 8))
            }
            if (isEmpty()) {
                val sBytes = entry.summary.toByteArray(Charsets.UTF_8)
                appendLine("[摘要 UTF-8 HEX (${sBytes.size} B)]")
                append(formatHexDump(sBytes, 8))
            }
        }.trimEnd()

        val ascii = buildString {
            if (uidBytes != null && uidBytes.isNotEmpty()) {
                appendLine("[硬件 UID ASCII 预览]")
                appendLine(formatAsciiPreview(uidBytes))
            }
            if (!payloadText.isNullOrBlank()) {
                val pBytes = payloadText.toByteArray(Charsets.UTF_8)
                if (isNotEmpty()) appendLine()
                appendLine("[报文 ASCII (${pBytes.size} B)]")
                append(formatAsciiPreview(pBytes))
            }
            if (isEmpty()) {
                val sBytes = entry.summary.toByteArray(Charsets.UTF_8)
                appendLine("[摘要 ASCII (${sBytes.size} B)]")
                append(formatAsciiPreview(sBytes))
            }
        }.trimEnd()

        return hex to ascii
    }

    private fun pageRoleNote(pageIndex: Int): String = when (pageIndex) {
        0 -> "UID0~2 / BCC0"
        1 -> "UID3~6"
        2 -> "BCC1 / Internal / Lock"
        3 -> "CC (能力容器)"
        4 -> "用户区起始 (NDEF TLV)"
        else -> "用户区 EEPROM"
    }
}
