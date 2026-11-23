package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.CodeSurface
import com.example.ui.theme.InkBlack
import com.example.ui.theme.MonoTextStyle
import com.example.ui.theme.MutedInk
import com.example.ui.theme.PureWhiteSurface
import com.example.ui.theme.SoftSurfaceVariant
import com.example.ui.theme.SubtleBorder
import com.example.ui.theme.TechBlue
import com.example.ui.theme.TechBlueBorder
import com.example.ui.theme.TechBlueContainer

private val ESP32S31_C_SAMPLE_CODE = """
/**
 * 富创通 (IST) 商显设备 ESP32S31 + FM11NT083C NFC 配网解析示例 (ESP-IDF)
 * 流程：FD 引脚中断触发 -> 延时防抖 -> I2C 从 Page 0x04 读取 NDEF TLV -> 解析明文 Text Record
 */
#include <stdint.h>
#include <stdbool.h>
#include <string.h>
#include "driver/i2c_master.h"
#include "driver/gpio.h"
#include "esp_log.h"
#include "cJSON.h"

#define TAG                     "IST_NFC_PROV"
#define FM11NT083C_I2C_ADDR     0x57    // FM11NT083C 默认 7-bit I2C 地址（请按硬件配置核对）
#define FM11NT083C_PAGE_USER    0x04    // 用户区 EEPROM 起始页 (4 Bytes/Page, 共 888 Bytes)
#define FM11NT083C_MAX_READ_LEN 256     // 单次读取最大覆盖 NDEF 配网报文长度

// 从 FM11NT083C 指定页地址读取连续字节 (16-bit 字节地址 = page * 4)
static esp_err_t fm11_read_eeprom(i2c_master_dev_handle_t dev, uint8_t start_page,
                                  uint8_t *out_buf, size_t len) {
    uint16_t byte_addr = (uint16_t)start_page * 4;
    uint8_t addr_buf[2] = { (uint8_t)(byte_addr >> 8), (uint8_t)(byte_addr & 0xFF) };
    return i2c_master_transmit_receive(dev, addr_buf, sizeof(addr_buf), out_buf, len, 200);
}

// 解析 Type 2 Tag NDEF TLV (0x03) 中的 UTF-8 Text Record ("en")
bool parse_ist_nfc_wifi_payload(const uint8_t *eeprom_page04, size_t buf_len,
                                char *out_ssid, size_t ssid_max,
                                char *out_pwd, size_t pwd_max) {
    size_t idx = 0;
    // 1. 查找 NDEF Message TLV (Tag = 0x03)
    while (idx < buf_len) {
        uint8_t tlv_tag = eeprom_page04[idx++];
        if (tlv_tag == 0x00) continue;      // NULL TLV
        if (tlv_tag == 0xFE) return false;  // Terminator TLV
        if (idx >= buf_len) return false;

        uint16_t tlv_len = eeprom_page04[idx++];
        if (tlv_len == 0xFF) {
            if (idx + 2 > buf_len) return false;
            tlv_len = ((uint16_t)eeprom_page04[idx] << 8) | eeprom_page04[idx + 1];
            idx += 2;
        }
        if (tlv_tag == 0x03) {
            if (idx + tlv_len > buf_len) return false;
            const uint8_t *ndef = &eeprom_page04[idx];
            // 2. 解析 NDEF Record Header (TNF=0x01 Well-Known, Type='T')
            uint8_t flags = ndef[0];
            bool sr = (flags & 0x10) != 0;   // Short Record
            uint8_t type_len = ndef[1];
            uint32_t payload_len = 0;
            size_t hdr_size = 0;

            if (sr) {
                payload_len = ndef[2];
                hdr_size = 3 + type_len;
            } else {
                payload_len = ((uint32_t)ndef[2] << 24) | ((uint32_t)ndef[3] << 16) |
                              ((uint32_t)ndef[4] << 8) | ndef[5];
                hdr_size = 6 + type_len;
            }
            if (type_len != 1 || ndef[hdr_size - 1] != 'T' || hdr_size + payload_len > tlv_len) {
                return false;
            }

            // 3. 解析 Text Record: [Status(1B)][Lang("en")][UTF-8 明文]
            const uint8_t *txt = &ndef[hdr_size];
            uint8_t lang_len = txt[0] & 0x3F;
            if (1 + lang_len >= payload_len) return false;

            const char *plain = (const char *)&txt[1 + lang_len];
            size_t plain_len = payload_len - 1 - lang_len;
            char text_buf[512] = {0};
            if (plain_len >= sizeof(text_buf)) plain_len = sizeof(text_buf) - 1;
            memcpy(text_buf, plain, plain_len);

            ESP_LOGI(TAG, "NFC 明文负载: %s", text_buf);

            // 4. 识别格式: JSON / 键值对 / WIFI URI (包含厂商 IST 与系统时间 ts)
            if (text_buf[0] == '{') {
                cJSON *root = cJSON_Parse(text_buf);
                if (!root) return false;
                const cJSON *s = cJSON_GetObjectItem(root, "ssid");
                const cJSON *p = cJSON_GetObjectItem(root, "password");
                const cJSON *v = cJSON_GetObjectItem(root, "vendor");
                const cJSON *ts = cJSON_GetObjectItem(root, "ts");
                if (cJSON_IsString(v)) ESP_LOGI(TAG, "厂商信息: %s", v->valuestring);
                if (cJSON_IsString(ts)) ESP_LOGI(TAG, "下发时间: %s", ts->valuestring);
                if (cJSON_IsString(s)) strncpy(out_ssid, s->valuestring, ssid_max - 1);
                if (cJSON_IsString(p)) strncpy(out_pwd, p->valuestring, pwd_max - 1);
                cJSON_Delete(root);
                return out_ssid[0] != '\0';
            } else if (strncmp(text_buf, "SSID=", 5) == 0) {
                char *pwd_ptr = strstr(text_buf, ";PWD=");
                if (!pwd_ptr) return false;
                *pwd_ptr = '\0';
                strncpy(out_ssid, text_buf + 5, ssid_max - 1);
                char *pwd_val = pwd_ptr + 5;
                char *vendor_ptr = strstr(pwd_val, ";VENDOR=");
                if (vendor_ptr) {
                    *vendor_ptr = '\0';
                    char *ts_ptr = strstr(vendor_ptr + 8, ";TS=");
                    if (ts_ptr) {
                        *ts_ptr = '\0';
                        ESP_LOGI(TAG, "下发时间: %s", ts_ptr + 4);
                    }
                    ESP_LOGI(TAG, "厂商信息: %s", vendor_ptr + 8);
                }
                strncpy(out_pwd, pwd_val, pwd_max - 1);
                return out_ssid[0] != '\0';
            }
            return false;
        }
        idx += tlv_len;
    }
    return false;
}

// FD 引脚中断唤醒后调用此任务处理函数
void ist_nfc_on_field_detected(i2c_master_dev_handle_t fm11_dev) {
    vTaskDelay(pdMS_TO_TICKS(300)); // 等待手机端写入完成并移开射频场
    uint8_t buf[FM11NT083C_MAX_READ_LEN] = {0};
    if (fm11_read_eeprom(fm11_dev, FM11NT083C_PAGE_USER, buf, sizeof(buf)) == ESP_OK) {
        char ssid[64] = {0}, pwd[64] = {0};
        if (parse_ist_nfc_wifi_payload(buf, sizeof(buf), ssid, sizeof(ssid), pwd, sizeof(pwd))) {
            ESP_LOGI(TAG, "配网解析成功 -> SSID: %s", ssid);
            // TODO: 调用 esp_wifi_set_config() & esp_wifi_connect()
        }
    }
}
""".trimIndent()

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun GuideScreen(
    onCodeCopied: () -> Unit,
    modifier: Modifier = Modifier
) {
    @Suppress("DEPRECATION")
    val clipboardManager = LocalClipboardManager.current

    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.TopCenter
    ) {
        LazyColumn(
            modifier = Modifier
                .widthIn(max = 680.dp)
                .fillMaxSize(),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 14.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Hardware summary card
            item {
                Surface(
                    color = PureWhiteSurface,
                    shape = RoundedCornerShape(12.dp),
                    border = BorderStroke(1.dp, SubtleBorder),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Memory,
                                contentDescription = null,
                                tint = TechBlue
                            )
                            Text(
                                text = "硬件架构与协议规范",
                                style = MaterialTheme.typography.titleMedium,
                                color = InkBlack
                            )
                        }
                        Text(
                            text = "富创通 (IST) 商显设备采用 ESP32S31 作为主控芯片，外挂复旦微 FM11NT083C 双界面 NFC 芯片（I2C + RF），通过明文 UTF-8 NDEF Text 记录完成近场无感配网。",
                            style = MaterialTheme.typography.bodySmall,
                            color = MutedInk
                        )
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            SpecBadge("主控：ESP32S31")
                            SpecBadge("NFC：FM11NT083C")
                            SpecBadge("协议：ISO14443-A / Type 2")
                            SpecBadge("UID：7 字节")
                            SpecBadge("EEPROM：888 Bytes")
                            SpecBadge("总线：I2C + FD 中断")
                        }
                    }
                }
            }

            // Workflow steps
            item {
                Surface(
                    color = PureWhiteSurface,
                    shape = RoundedCornerShape(12.dp),
                    border = BorderStroke(1.dp, SubtleBorder),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Text(
                            text = "ESP32S31 + FM11NT083C 明文配网流程",
                            style = MaterialTheme.typography.titleMedium,
                            color = InkBlack
                        )
                        WorkflowStepItem(
                            step = "1",
                            title = "手机读取 UID 绑定设备",
                            desc = "APP 通过 ReaderMode 读取 FM11NT083C 出厂固化的 7 字节只读 UID（如 04:E2:8A:19:4C:72:01），关联商显屏幕资产名称并保存在手机本地。"
                        )
                        WorkflowStepItem(
                            step = "2",
                            title = "写入 UTF-8 明文 NDEF Text 记录",
                            desc = "二次刷卡时，手机通过 Ndef.writeNdefMessage() 将 JSON / 键值对 / WIFI URI 明文写入 FM11NT083C 用户区 EEPROM（起始页 Page 0x04）。"
                        )
                        WorkflowStepItem(
                            step = "3",
                            title = "FD 引脚触发 ESP32S31 中断",
                            desc = "手机靠近写入时射频能量驱动 FM11NT083C 的 FD（Field Detect）引脚输出跳变信号，唤醒或通知 ESP32S31 准备读取配网信息。"
                        )
                        WorkflowStepItem(
                            step = "4",
                            title = "I2C 从 Page 0x04 读取并解析 NDEF TLV",
                            desc = "ESP32S31 延时防抖后通过 I2C 总线从 Page 0x04（字节偏移 0x0010）读取 NDEF TLV (0x03 ... 0xFE)，剥离 Text Record 头与 \"en\" 语言码后提取 SSID 与密码并连接 Wi-Fi。"
                        )
                    }
                }
            }

            // Memory layout card
            item {
                Surface(
                    color = PureWhiteSurface,
                    shape = RoundedCornerShape(12.dp),
                    border = BorderStroke(1.dp, SubtleBorder),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = "FM11NT083C Type 2 Tag 存储分布速查",
                            style = MaterialTheme.typography.titleSmall,
                            color = InkBlack
                        )
                        MemoryRow("Page 0x00 ~ 0x02", "7 字节硬件 UID + 内部锁定位 (BCC / Lock)")
                        MemoryRow("Page 0x03", "Capability Container (CC: E1 10 6F 00，指示 888B 容量)")
                        MemoryRow("Page 0x04 ~ 0xE1", "用户区 EEPROM (888 Bytes)：存放 0x03 NDEF TLV + 0xFE 结束符")
                    }
                }
            }

            // ESP32S31 C Sample Code card
            item {
                Surface(
                    color = PureWhiteSurface,
                    shape = RoundedCornerShape(12.dp),
                    border = BorderStroke(1.dp, SubtleBorder),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "ESP32S31 端 C 语言解析示例代码",
                                    style = MaterialTheme.typography.titleMedium,
                                    color = InkBlack
                                )
                                Text(
                                    text = "ESP-IDF I2C 读取 Page 0x04 + NDEF Text 解析",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MutedInk
                                )
                            }
                            Button(
                                onClick = {
                                    clipboardManager.setText(AnnotatedString(ESP32S31_C_SAMPLE_CODE))
                                    onCodeCopied()
                                },
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = TechBlue,
                                    contentColor = PureWhiteSurface
                                ),
                                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                                modifier = Modifier.testTag("copy_sample_code_button")
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.ContentCopy,
                                    contentDescription = "复制代码",
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(Modifier.width(6.dp))
                                Text("复制代码", style = MaterialTheme.typography.labelMedium)
                            }
                        }

                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(10.dp))
                                .background(CodeSurface)
                                .border(1.dp, SubtleBorder, RoundedCornerShape(10.dp))
                                .horizontalScroll(rememberScrollState())
                                .padding(12.dp)
                        ) {
                            Text(
                                text = ESP32S31_C_SAMPLE_CODE,
                                style = MonoTextStyle.copy(
                                    fontSize = 12.sp,
                                    lineHeight = 18.sp,
                                    color = InkBlack
                                ),
                                modifier = Modifier.testTag("sample_code_block")
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SpecBadge(text: String) {
    Surface(
        color = SoftSurfaceVariant,
        shape = RoundedCornerShape(6.dp),
        border = BorderStroke(1.dp, SubtleBorder)
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelMedium,
            color = InkBlack,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
        )
    }
}

@Composable
private fun WorkflowStepItem(
    step: String,
    title: String,
    desc: String
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.Top
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(26.dp)
                .clip(CircleShape)
                .background(TechBlueContainer)
                .border(1.dp, TechBlueBorder, CircleShape)
        ) {
            Text(
                text = step,
                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                color = TechBlue
            )
        }
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(3.dp)
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall,
                color = InkBlack
            )
            Text(
                text = desc,
                style = MaterialTheme.typography.bodySmall,
                color = MutedInk
            )
        }
    }
}

@Composable
private fun MemoryRow(pageRange: String, detail: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(CodeSurface)
            .border(1.dp, SubtleBorder, RoundedCornerShape(8.dp))
            .padding(horizontal = 10.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = pageRange,
            style = MonoTextStyle.copy(color = TechBlue, fontWeight = FontWeight.Bold)
        )
        Text(
            text = detail,
            style = MaterialTheme.typography.bodySmall,
            color = InkBlack,
            modifier = Modifier.weight(1f)
        )
    }
}
