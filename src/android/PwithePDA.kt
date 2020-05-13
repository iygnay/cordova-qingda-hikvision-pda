package qingda.cordova

import android.graphics.BitmapFactory
import android.util.Base64
import com.rsk.api.RskApi
import org.apache.cordova.CallbackContext
import org.apache.cordova.CordovaPlugin
import org.json.JSONArray
import org.json.JSONObject
import java.util.*

const val PRINT_LAYOUT_CENTER: Byte = 0x49;
const val PRINT_LAYOUT_CANCEL: Byte = 0x4b;
const val PRINT_FONT_24_24: Byte = 0x1;
const val PRINT_FONT_16_16: Byte = 0x2;
const val PRINT_FONT_16_32: Byte = 0x3;
const val PRINT_FONT_20_40: Byte = 0x4;

class PwithePDAPlugin : CordovaPlugin() {
    private var zgDeviceInited = false;

    override fun execute(action: String?, args: JSONArray?, callbackContext: CallbackContext?): Boolean {
        if (action == "hello") {
            hello(callbackContext!!)
            return true
        } else if (action == "printPage") {
            printPage(args!!, callbackContext!!)
            return true
        } else return false
    }

    private fun initZGAndPrinter() {
        if (!zgDeviceInited) {
            var r = RskApi.ZGOpenPower()
            if (r != 0) {
                throw Exception("不支持的设备型号, 此模块仅能在警翼设备中使用");
            }

            // note(杨逸):
            // 按照文档ZG模块上电后需要等待2秒才能继续.
            Thread.sleep(2000)

            // completed
            zgDeviceInited = true;
        }

        // note(杨逸):
        // 重启打印机, 因为警翼打印机没有提供重置的功能
        // 但是有发现打印有时会遇到换行异常的问题 (重启设备后恢复)
        // 所以每次打印之前, 我们主动关闭再重启打印机, 以期获得 "重置" 的效果 (不知道是否真的有效)
        RskApi.PrintClose()
        Thread.sleep(300)

        var r = RskApi.PrintOpen()
        if (r != 0) {
            throw Exception("打印机上电失败, code=$r");
        }
        // note(杨逸):
        // 虽然文档中没有说明, 但这里我们也稍微暂停一下等待打印机上电完成.
        Thread.sleep(300)
    }

    /**
     * 打印:
     */
    private fun printPage(args: JSONArray, callbackContext: CallbackContext) {
        cordova.threadPool.run {
            try {
                // 初始化
                initZGAndPrinter()

                // 预备
                RskApi.PrintSetGray(200.toByte())
                RskApi.PrintSetColor(0)
                RskApi.PrintSetSpeed(40)

                val data = args.getJSONObject(0)
                val elements = data.getJSONArray("elements")

                for (i in 0 until elements.length()) {
                    val element = elements.getJSONObject(i)

                    when (element.getString("tagName")) {
                        "text" -> printTextElement(element)
                        "image" -> printImageElement(element)
                    }
                }

                // 结尾符号
                RskApi.PrintDotLines(10)
                RskApi.PrintSetLayout(PRINT_LAYOUT_CENTER)
                RskApi.PrintChars("****************\n")
                RskApi.PrintDotLines(10)
                RskApi.PrintLine()

                // return
                callbackContext.success("ok")
            } catch (error: Exception) {
                callbackContext.error(error.message)
            }
        }
    }

    /**
     * 重置文本打印设置.
     * - 例如: 字体大小和行高等
     * - 每次打印文本前都调用此方法
     */
    private fun resetPrintTextSettings() {
        RskApi.PrintFontSet(PRINT_FONT_24_24)
        RskApi.PrintFontMagnify(1)
        RskApi.PrintSetDotLine(12)
        RskApi.PrintSetLayout(PRINT_LAYOUT_CANCEL)
    }

    /**
     * 打印图片
     */
    private fun printImageElement(element: JSONObject) {
        val base64 = element.getString("base64")
        var qrcode = element.getString("qrcode")
        // val width = element.getString("width")
        // val height = element.getInt("height")

        if (qrcode != null && qrcode != "") {
            // 二维码模式
            Thread.sleep(1000)
            RskApi.PrintDotLines(1)
            RskApi.PrintSetLayout(PRINT_LAYOUT_CENTER)
            RskApi.PrintQRWidth(256)
            RskApi.PrintQR(qrcode)
            RskApi.PrintSetLayout(PRINT_LAYOUT_CANCEL)
            RskApi.PrintDotLines(1)
        } else {
            // 图片模式
            val bytes = Base64.decode(base64, Base64.DEFAULT)
            val bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)

            Thread.sleep(1000)
            RskApi.PrintSetLayout(PRINT_LAYOUT_CENTER)
            RskApi.PrintBitmap(bitmap)
            RskApi.PrintSetLayout(PRINT_LAYOUT_CANCEL)
        }
    }

    /**
     * 打印文本
     */
    private fun printTextElement(element: JSONObject) {
        val text = element.getString("text")
        val fontSize = element.getString("fontSize")
        val indent = element.getInt("indent")
        val align = element.getString("align");

        resetPrintTextSettings()
        if (text == "_"  || text == "") {
            RskApi.PrintDotLines(2)
        } else {
            RskApi.PrintSetLayout(if (align == "center") PRINT_LAYOUT_CENTER else PRINT_LAYOUT_CANCEL)
            if (fontSize == "large") {
                RskApi.PrintFontSet(PRINT_FONT_16_16)
                RskApi.PrintFontMagnify(2)
            }

            val prefixChars = CharArray(indent)
            Arrays.fill(prefixChars, ' ')
            val prefix = String(prefixChars)
            RskApi.PrintChars(prefix + text + "\n")
            RskApi.PrintLine()
        }
    }

    private fun hello(callbackContext: CallbackContext) {
        try {
            var version = RskApi.GetVersion();
            callbackContext.success("hello, RskApi version is $version");
        } catch (error: Exception) {
            callbackContext.error(error.message);
        }
    }
}