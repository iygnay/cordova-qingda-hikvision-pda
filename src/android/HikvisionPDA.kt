package qingda.cordova

import HPRTAndroidSDK.HPRTPrinterHelper
import HPRTAndroidSDK.PublicFunction
import android.graphics.*
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import android.util.Base64
import android.util.Log
import org.apache.cordova.CallbackContext
import org.apache.cordova.CordovaPlugin
import org.json.JSONArray
import org.json.JSONObject
import java.io.FileWriter
import java.util.*

class HikvisionPDAPlugin : CordovaPlugin() {
    private var printer: HPRTPrinterHelper? = null
    private var pfunc: PublicFunction? = null

    override fun execute(action: String?, args: JSONArray?, callbackContext: CallbackContext?): Boolean {
        return when (action) {
            "hello" -> {
                hello(callbackContext!!)
                true
            }
            "printPage" -> {
                printPage(args!!, callbackContext!!)
                true
            }
            else -> false
        }
    }

    /**
     * 打印:
     */
    private fun printPage(args: JSONArray, callbackContext: CallbackContext) {
        cordova.threadPool.run {
            try {
                Log.d("HikvisionPDAPlugin", "printPage start")
                if (printer == null) {
                    val context = cordova.activity
                    printer = HPRTPrinterHelper(context, "TP801")
                    pfunc = PublicFunction(context)
                }

                // 1. 打开打印机
                var ret = openPrinterPort()
                Log.d("HikvisionPDAPlugin", "openPrinterPort ret: $ret")
                if (ret == -1) {
                    callbackContext.error("打印机初始化失败, 请确认你的设备型号是否为受支持的海康威视手持终端, 或者检查设备的配件设置是否已正确连接背夹 (${ret})")
                    return;
                }

                // 2. 预备 (清除缓存)
                ret = HPRTPrinterHelper.ClearBuffer()
                Log.d("HikvisionPDAPlugin", "openPrinterPort clearBuffer: $ret")

                // 3. 打印内容
                val data = args.getJSONObject(0)
                val elements = data.getJSONArray("elements")

                for (i in 0 until elements.length()) {
                    val element = elements.getJSONObject(i)

                    when (element.getString("tagName")) {
                        "text" -> printTextElement(element)
                        "image" -> printImageElement(element)
                    }
                }

                // 4. 结束 (走纸)
                HPRTPrinterHelper.PrintData(createTextData("　", 48f, Layout.Alignment.ALIGN_NORMAL))
                HPRTPrinterHelper.PrintData(createTextData("****************", 24f, Layout.Alignment.ALIGN_CENTER))
                HPRTPrinterHelper.PrintData(createTextData("　", 48f, Layout.Alignment.ALIGN_NORMAL))

                // 3. 关闭打印机
                Thread.sleep(300)
                closePrinterPort()
                Log.e("HikvisionPDAPlugin", "printPage ok")
                callbackContext.success("print success, $ret");
            } catch (error: Exception) {
                Log.e("HikvisionPDAPlugin", "printPage error: ${error.message}")
                callbackContext.error(error.message);
            }
        }
    }

    /**
     * 打印图片
     */
    private fun printImageElement(element: JSONObject) {
        Log.d("HikvisionPDAPlugin", "printImageElement start")
        val base64 = element.getString("base64")
        val ret = HPRTPrinterHelper.PrintData(createBase64ImageData(base64))
        Log.d("HikvisionPDAPlugin", "printImageElement ok: $ret")
    }

    /**
     * 打印文本
     */
    private fun printTextElement(element: JSONObject) {
        var text = element.getString("text")
        Log.d("HikvisionPDAPlugin", "printTextElement start: $text")

        val fontSize = element.getString("fontSize")
        val indent = element.getInt("indent")
        val align = element.getString("align");

        if (text == "" || text == "_") {
            // note(杨逸): "_" 单独的下划线符号在旧版本用于表示换行, 这里做一下兼容处理
            text = "　"
        }

        if (indent > 0) {
            val prefixChars = CharArray(indent)
            Arrays.fill(prefixChars, '　')
            val prefix = String(prefixChars)
            text = prefix + text
        }

        val ret = HPRTPrinterHelper.PrintData(createTextData(
            text,
            if (fontSize == "large") 32f else 24f,
            if (align == "center") Layout.Alignment.ALIGN_CENTER else Layout.Alignment.ALIGN_LEFT
        ))
        Log.d("HikvisionPDAPlugin", "printTextElement ok: $ret")
    }

    private fun hello(callbackContext: CallbackContext) {
        cordova.threadPool.run {
            try {
                callbackContext.success("hello, this is HikvisionPDAPlugin");
            } catch (error: Exception) {
                Log.e("HikvisionPDAPlugin", "hello error: ${error.message}")
                callbackContext.error(error.message);
            }
        }
    }

    /**
     * 创建 StaticLayout
     */
    private fun createTextLayout(text: CharSequence, textSize: Float, align: Layout.Alignment): StaticLayout {
        val width = 384;

        // note(杨逸):
        // 因为需要支持安卓5.0, 所以需要使用废弃的 StaticLayout 构造方法
        val paint = TextPaint(Paint.ANTI_ALIAS_FLAG)
        paint.color = Color.BLACK
        paint.textSize = textSize
        return StaticLayout(text, paint, width, align, 1.0f, 0.0f, false)
    }

    /**
     * 创建文本打印数据
     */
    private fun createTextData(text: CharSequence, textSize: Float, align: Layout.Alignment): ByteArray? {
        val layout = createTextLayout(text, textSize, align)
        val bitmap = Bitmap.createBitmap(layout.width, layout.height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        canvas.drawColor(-1)
        layout.draw(canvas)
        canvas.save()
        return HPRTPrinterHelper.PrintBitmap(bitmap, 0, 0)
    }

    /**
     * 创建图片打印数据
     */
    private fun createBase64ImageData(base64: String): ByteArray? {
        val data: ByteArray = Base64.decode(base64, Base64.DEFAULT)
        val bitmap = BitmapFactory.decodeByteArray(data, 0, data.size)
        val holder = Bitmap.createBitmap(384, bitmap.height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(holder)
        val left = (holder.width - bitmap.width) / 2.0f;
        canvas.drawColor(Color.WHITE)
        canvas.drawBitmap(bitmap, if (left < 0) 0f else left, 0.0f, null)
        canvas.save()
        return HPRTPrinterHelper.PrintBitmap(holder, 0, 0)
    }

    /**
     * 打开打印机 (端口)
     * -- 如果之前已经打开, 会先关闭
     */
    private fun openPrinterPort(): Int {
        return try {
            closePrinterPort()
            HPRTPrinterHelper.PortOpen("Serial,/dev/ttyUSB0,115200")
        } catch (error: Exception) {
            Log.e("HikvisionPDAPlugin", "openPrinterPort error: ${error.message}")
            error.printStackTrace()
            -1;
        }
    }

    /**
     * 关闭打印机 (端口)
     */
    private fun closePrinterPort() {
        try {
            if (printer != null && HPRTPrinterHelper.IsOpened()) {
                HPRTPrinterHelper.PortClose()
            }

            // disableExtHub
            // note(杨逸): 这段不知道是做什么
            val file = FileWriter("sys/class/ext_dev/function/ext_hub_enable")
            file.write("0")
            file.close()
            Log.e("HikvisionPDAPlugin", "closePrinterPort ok")
        } catch (error: Exception) {
            Log.e("HikvisionPDAPlugin", "closePrinterPort error: ${error.message}")
            error.printStackTrace()
        }
    }
}