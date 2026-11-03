package com.jcapps.adbserver

import android.content.AttributionSource
import android.content.ClipData
import android.content.Context
import android.content.IContentProvider
import android.media.MediaScannerConnection
import android.net.Uri
import android.os.Binder
import android.os.Build
import android.os.Environment
import android.os.IBinder
import android.os.IInterface
import android.os.ParcelFileDescriptor
import com.google.gson.annotations.SerializedName
import com.koushikdutta.async.ByteBufferList
import com.koushikdutta.async.callback.CompletedCallback
import com.koushikdutta.async.callback.DataCallback
import com.koushikdutta.async.http.body.AsyncHttpRequestBody
import com.koushikdutta.async.http.body.MultipartFormDataBody
import com.koushikdutta.async.http.body.UrlEncodedFormBody
import com.koushikdutta.async.http.server.AsyncHttpServerRequest
import com.koushikdutta.async.http.server.AsyncHttpServerResponse
import java.io.Closeable
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.lang.reflect.Method

/**
 * GET /clipboard[?type=image], POST /clipboard/text (form field `text`),
 * POST /clipboard/image (multipart, file part `image`).
 *
 * In the dex this is one branch of the merged handler class C0171 (its constructor for EnumC0060).
 */
// obf: C0171 (EnumC0060 branch of mo143)
class ClipboardHandler(param: Param, private val action: Action) : BaseHandler(param) { // obf: f355

    // obf: EnumC0060 (GET = f136, SET_TEXT = f135, SET_IMAGE = f138)
    enum class Action {
        GET,       // GET  /clipboard
        SET_TEXT,  // POST /clipboard/text
        SET_IMAGE, // POST /clipboard/image
    }

    override fun handle(request: AsyncHttpServerRequest, response: AsyncHttpServerResponse) {
        when (action) {
            Action.GET -> getClipboard(request, response)
            Action.SET_TEXT -> setText(request, response)
            Action.SET_IMAGE -> setImage(request, response)
        }
    }

    private fun getClipboard(request: AsyncHttpServerRequest, response: AsyncHttpServerResponse) {
        val type = request.query.getString("type")
        try {
            val clipboardManager = ServiceManager.getClipboardManager()
            if (clipboardManager == null) {
                response.sendError("Failed to access clipboard manager.")
                return
            }
            val clipData = clipboardManager.getPrimaryClip()
            val item = if (clipData != null && clipData.itemCount != 0) clipData.getItemAt(0) else null
            if (item == null) {
                response.sendError("No data found in clipboard.")
                return
            }
            val uri = item.uri
            val contentResolver = FakeContext.contentResolver // obf: C0244.f508
            val mimeType = if (uri != null) checkNotNull(contentResolver).getType(uri) else null
            if (mimeType != null && mimeType.startsWith("image/")) {
                sendImage(response, type, uri, mimeType)
                return
            }
            val text = item.text
            if (type == "image") {
                response.sendError("No image found in clipboard.")
                return
            }
            response.sendSuccess(Clipboard(text?.toString() ?: "", false))
        } catch (e: Exception) {
            println(e.stackTraceToString()) // obf: AbstractC0353.m389
            response.sendError(e.stackTraceToString())
        }
    }

    /**
     * The clip is an image: without `type=image` only report `has_image`, else stream the image
     * bytes (not JSON) with its MIME type.
     */
    // obf: m204
    private fun sendImage(response: AsyncHttpServerResponse, type: String?, uri: Uri?, mimeType: String) {
        if (type != "image") {
            response.sendSuccess(Clipboard("", true))
            return
        }
        if (uri != null) {
            val pfd = runCatching {
                val token = Binder()
                val name = uri.host ?: uri.authority?.substringAfter('@') ?: ""
                val activityManager = ActivityManagerProxy.get()
                // `!!` like the original: a missing provider fails inside runCatching -> error answer.
                // Fixed (rebuild): the provider is released again after opening the file (the original
                // never did, so every clipboard image kept the source app's provider referenced). The
                // returned file descriptor stays valid on its own and is closed by sendFileDescriptor.
                ContentProviderWrapper(
                    activityManager, activityManager.getContentProviderExternal(name, token)!!, name, token,
                ).use { it.openFile(uri) }
            }.getOrNull()
            if (pfd != null) {
                sendFileDescriptor(response, pfd, mimeType)
                return
            }
        }
        response.sendError("Failed to get image from clipboard.")
    }

    // obf: inlined helper (param names "pfd", "mimeType")
    private fun sendFileDescriptor(response: AsyncHttpServerResponse, pfd: ParcelFileDescriptor, mimeType: String) {
        response.headers.set("Content-Type", mimeType)
        try {
            val input = FileInputStream(pfd.fileDescriptor)
            response.sendStream(input, input.channel.size())
        } catch (e: Exception) {
            // BUG(original): the stack trace goes out with the image Content-Type set just above.
            response.send(e.stackTraceToString()) // send(String): Content-Type header or text/html
        }
    }

    private fun setText(request: AsyncHttpServerRequest, response: AsyncHttpServerResponse) {
        val body = request.getBody<AsyncHttpRequestBody<*>>()
        if (body !is UrlEncodedFormBody) {
            response.sendError("Body must be Form URL encoded.")
            return
        }
        val text = body.get()?.getString("text")
        if (text == null) {
            response.sendError("Missing text param.")
            return
        }
        val clipboardManager = ServiceManager.getClipboardManager()
        if (clipboardManager == null) {
            response.sendError("Failed to access clipboard manager.")
        } else if (clipboardManager.setPrimaryClip(ClipData.newPlainText(null, text))) {
            response.sendSuccess("Set clipboard successfully.")
        } else {
            response.sendError("Failed to set clipboard.")
        }
    }

    /**
     * Saves the uploaded `image` part to /storage/emulated/0/Pictures/Clipboard/<filename>, lets the
     * media scanner index it and puts the resulting content:// URI on the clipboard.
     */
    private fun setImage(request: AsyncHttpServerRequest, response: AsyncHttpServerResponse) {
        val body = request.getBody<AsyncHttpRequestBody<*>>()
        if (body !is MultipartFormDataBody) {
            response.sendError("Body must be multipart form data.")
            return
        }
        try {
            val clipboardManager = ServiceManager.getClipboardManager()
            if (clipboardManager == null) {
                response.sendError("Failed to access clipboard manager.")
                return
            }
            var errMessage: String? = null                        // obf: c0322 (Ref.ObjectRef)
            var mimeType: String? = null                          // obf: c0323
            var imageFile: File? = null                           // obf: c0324
            var imageFileOutputStream: FileOutputStream? = null   // obf: c0325

            // obf: anonymous InterfaceC0270 (ဝတလဉပဏယ) = request end callback
            request.setEndCallback(CompletedCallback { exc ->
                MiscUtils.ignoreExceptions { imageFileOutputStream?.close() } // obf: C0110 mode 0
                imageFileOutputStream = null
                if (exc != null || errMessage != null) {
                    MiscUtils.ignoreExceptions { imageFile?.delete() } // obf: C0110 mode 1
                    imageFile = null
                    response.sendError(errMessage ?: exc?.toString() ?: "")
                    return@CompletedCallback
                }
                // BUG(original): with no error and no image part (e.g. an empty multipart body) no
                // response is ever sent and the request hangs.
                if (imageFile != null) {
                    val path = imageFile!!.absolutePath
                    // obf: C0135 (first constructor) = scan-completed callback
                    scanFile(FakeContext, path, mimeType!!) { uri ->
                        imageFile = null
                        if (uri != null) {
                            if (clipboardManager.setPrimaryClip(
                                    ClipData("URI", arrayOf<String?>(mimeType), ClipData.Item(uri)),
                                )
                            ) {
                                response.sendSuccess("Clipboard set successfully.")
                            } else {
                                response.sendError("Failed to set clipboard.")
                            }
                        } else {
                            response.sendError("Failed to set clipboard.")
                        }
                    }
                }
            })

            // obf: C0135 (second constructor) stored in C0169.f349; body inlined into C0043.mo57
            body.setMultipartCallback(MultipartFormDataBody.MultipartCallback { part ->
                if (part.name == "image" && part.isFile) {
                    mimeType = part.contentType
                    val partMimeType = mimeType
                    if (partMimeType == null || !partMimeType.startsWith("image/")) {
                        errMessage = "File must be image."
                    } else {
                        val pictures = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES)
                        if (pictures.exists() || pictures.mkdir()) {
                            val clipboardDir = File(pictures, "Clipboard")
                            if (clipboardDir.exists() || clipboardDir.mkdir()) {
                                imageFile = File(clipboardDir, part.filename)
                                // BUG(original): immediately overwritten with a hard-coded path, so the
                                // directory checked/created above is ignored if Pictures lives elsewhere.
                                imageFile = File("/storage/emulated/0/Pictures/Clipboard", part.filename)
                                val output = FileOutputStream(imageFile!!)
                                imageFileOutputStream = output
                                // obf: C0043 mode 2 (data callback) + C0345 (write error callback)
                                body.setDataCallback(DataCallback { _, bb: ByteBufferList ->
                                    try {
                                        while (bb.size() > 0) {
                                            val buffer = bb.remove()
                                            output.write(
                                                buffer.array(), buffer.arrayOffset() + buffer.position(),
                                                buffer.remaining(),
                                            )
                                            ByteBufferList.reclaim(buffer)
                                        }
                                    } catch (e: Exception) {
                                        // obf: C0345.mo53 -> C0287 mode 0
                                        MiscUtils.ignoreExceptions {
                                            imageFileOutputStream?.close()
                                            imageFile?.delete()
                                        }
                                        imageFileOutputStream = null
                                        imageFile = null
                                        errMessage = e.toString()
                                    } finally {
                                        bb.recycle()
                                    }
                                })
                            } else {
                                errMessage = "Failed to create directory: " + clipboardDir.absolutePath + '.'
                            }
                        } else {
                            errMessage = "Failed to create directory: " + pictures.absolutePath + '.'
                        }
                    }
                } else {
                    errMessage = "Form data must contain text or image."
                }
            })
        } catch (e: Exception) {
            println(e.stackTraceToString())
            response.sendError(e.stackTraceToString())
        }
    }

    /** `MediaScannerConnection.scanFile` for one file. */
    // obf: inlined helper (param names "context", "filePath", "mimeType", "callback")
    private fun scanFile(context: Context, filePath: String, mimeType: String, callback: (Uri?) -> Unit) {
        MediaScannerConnection.scanFile(context, arrayOf(filePath), arrayOf(mimeType)) { _, uri ->
            callback(uri)
        }
    }

    /**
     * Hidden `android.os.ServiceManager` access (scrcpy-style). Private copy per coordinator: CORE's
     * FakeContext.kt has its own private ActivityManager copy; the original shares one instance.
     */
    // obf: AbstractC0063
    private object ServiceManager {

        // obf: f142
        private val getServiceMethod: Method = try {
            Class.forName("android.os.ServiceManager").getDeclaredMethod("getService", String::class.java)
        } catch (e: Exception) {
            throw AssertionError(e)
        }

        private var clipboardManager: ClipboardManager? = null // obf: f141

        // obf: m90
        fun getClipboardManager(): ClipboardManager? {
            if (clipboardManager == null) {
                try {
                    val binder = getServiceMethod.invoke(null, "clipboard") as IBinder?
                    val manager = Class.forName("android.content.IClipboard" + "\$Stub")
                        .getMethod("asInterface", IBinder::class.java)
                        .invoke(null, binder) as IInterface?
                    clipboardManager = if (manager != null) ClipboardManager(manager) else null
                } catch (e: Exception) {
                    throw AssertionError(e)
                }
            }
            return clipboardManager
        }
    }

    /** Reflection wrapper around the hidden `IClipboard` (probes the per-version signatures). */
    // obf: C0129
    private class ClipboardManager(private val manager: IInterface) { // obf: f269

        private var getPrimaryClipMethod: Method? = null // obf: f268
        private var getMethodVersion = 0                 // obf: f270
        private var setPrimaryClipMethod: Method? = null // obf: f271
        private var setMethodVersion = 0                 // obf: f272

        // obf: m166
        private fun getGetPrimaryClipMethod(): Method {
            if (getPrimaryClipMethod == null) {
                val cls = manager.javaClass
                val int = Integer.TYPE
                val bool = java.lang.Boolean.TYPE
                val str = String::class.java
                if (Build.VERSION.SDK_INT < 29) {
                    getPrimaryClipMethod = cls.getMethod("getPrimaryClip", str)
                    return getPrimaryClipMethod!!
                }
                try {
                    getPrimaryClipMethod = cls.getMethod("getPrimaryClip", str, int)
                    getMethodVersion = 0
                } catch (e1: NoSuchMethodException) {
                    try {
                        getPrimaryClipMethod = cls.getMethod("getPrimaryClip", str, str, int)
                        getMethodVersion = 1
                    } catch (e2: NoSuchMethodException) {
                        try {
                            getPrimaryClipMethod = cls.getMethod("getPrimaryClip", str, str, int, int)
                            getMethodVersion = 2
                        } catch (e3: NoSuchMethodException) {
                            try {
                                getPrimaryClipMethod = cls.getMethod("getPrimaryClip", str, int, str)
                                getMethodVersion = 3
                            } catch (e4: NoSuchMethodException) {
                                try {
                                    getPrimaryClipMethod = cls.getMethod("getPrimaryClip", str, str, int, int, bool)
                                    getMethodVersion = 4
                                } catch (e5: NoSuchMethodException) {
                                    try {
                                        getPrimaryClipMethod =
                                            cls.getMethod("getPrimaryClip", str, str, str, str, int, int, bool)
                                        getMethodVersion = 5
                                    } catch (e6: NoSuchMethodException) {
                                        getPrimaryClipMethod =
                                            cls.getMethod("getPrimaryClip", str, str, int, int, str)
                                        getMethodVersion = 6
                                    }
                                }
                            }
                        }
                    }
                }
            }
            return getPrimaryClipMethod!!
        }

        // obf: m168
        private fun getSetPrimaryClipMethod(): Method {
            if (setPrimaryClipMethod == null) {
                val cls = manager.javaClass
                val int = Integer.TYPE
                val str = String::class.java
                val clip = ClipData::class.java
                if (Build.VERSION.SDK_INT < 29) {
                    setPrimaryClipMethod = cls.getMethod("setPrimaryClip", clip, str)
                    return setPrimaryClipMethod!!
                }
                try {
                    setPrimaryClipMethod = cls.getMethod("setPrimaryClip", clip, str, int)
                    setMethodVersion = 0
                } catch (e1: NoSuchMethodException) {
                    try {
                        setPrimaryClipMethod = cls.getMethod("setPrimaryClip", clip, str, str, int)
                        setMethodVersion = 1
                    } catch (e2: NoSuchMethodException) {
                        try {
                            setPrimaryClipMethod = cls.getMethod("setPrimaryClip", clip, str, str, int, int)
                            setMethodVersion = 2
                        } catch (e3: NoSuchMethodException) {
                            setPrimaryClipMethod =
                                cls.getMethod("setPrimaryClip", clip, str, str, int, int, java.lang.Boolean.TYPE)
                            setMethodVersion = 3
                        }
                    }
                }
            }
            return setPrimaryClipMethod!!
        }

        /** Current primary clip, or null on reflection failure. */
        // obf: inlined into C0171.mo143 (clipboard GET branch)
        fun getPrimaryClip(): ClipData? {
            return try {
                invokeGetPrimaryClip(getGetPrimaryClipMethod(), getMethodVersion)
            } catch (e: ReflectiveOperationException) {
                println("Could not invoke method. $e")
                null
            }
        }

        // obf: m167
        fun setPrimaryClip(clipData: ClipData): Boolean {
            return try {
                invokeSetPrimaryClip(getSetPrimaryClipMethod(), setMethodVersion, clipData)
                true
            } catch (e: ReflectiveOperationException) {
                println("Could not invoke method. $e")
                false
            }
        }

        // obf: m164
        private fun invokeGetPrimaryClip(method: Method, methodVersion: Int): ClipData? {
            if (Build.VERSION.SDK_INT < 29) {
                return method.invoke(manager, PACKAGE_NAME) as ClipData?
            }
            return when (methodVersion) {
                0 -> method.invoke(manager, PACKAGE_NAME, 0)
                1 -> method.invoke(manager, PACKAGE_NAME, null, 0)
                2 -> method.invoke(manager, PACKAGE_NAME, null, 0, 0)
                3 -> method.invoke(manager, PACKAGE_NAME, 0, null)
                4 -> method.invoke(manager, PACKAGE_NAME, null, 0, 0, true)
                5 -> method.invoke(manager, PACKAGE_NAME, null, null, null, 0, 0, true)
                else -> method.invoke(manager, PACKAGE_NAME, null, 0, 0, null)
            } as ClipData?
        }

        // obf: m165
        private fun invokeSetPrimaryClip(method: Method, methodVersion: Int, clipData: ClipData) {
            if (Build.VERSION.SDK_INT < 29) {
                method.invoke(manager, clipData, PACKAGE_NAME)
                return
            }
            when (methodVersion) {
                0 -> method.invoke(manager, clipData, PACKAGE_NAME, 0)
                1 -> method.invoke(manager, clipData, PACKAGE_NAME, null, 0)
                2 -> method.invoke(manager, clipData, PACKAGE_NAME, null, 0, 0)
                else -> method.invoke(manager, clipData, PACKAGE_NAME, null, 0, 0, true)
            }
        }

        private companion object {
            const val PACKAGE_NAME = "com.android.shell"
        }
    }


    /**
     * A provider acquired via getContentProviderExternal (scrcpy-style ContentProvider). Its static
     * initializer still lists scrcpy's settings constants ("system", "secure", "global", "GET_*",
     * "PUT_*", "_user", "value"), unused in this app.
     */
    // obf: C0172
    private class ContentProviderWrapper(
        private val activityManager: ActivityManagerProxy, // obf: f357
        private val provider: IContentProvider,       // obf: f356
        private val name: String,                     // obf: f359
        private val token: Binder,                    // obf: f358
    ) : Closeable {

        /** Releases the provider (`removeContentProviderExternal(name, token)`). */
        override fun close() {
            activityManager.removeContentProviderExternal(name, token)
        }

        // obf: m205
        fun openFile(uri: Uri): ParcelFileDescriptor? {
            val method: Method
            val args: Array<Any?>
            val sdk = Build.VERSION.SDK_INT
            if (sdk >= 31) {
                method = provider.javaClass.getMethod(
                    "openFile", AttributionSource::class.java, Uri::class.java, String::class.java,
                    Class.forName("android.os.ICancellationSignal"),
                )
                args = arrayOf(FakeContext.attributionSource, uri, "r", null) // obf: C0244.f508
            } else if (sdk == 30) {
                method = provider.javaClass.getMethod(
                    "openFile", String::class.java, String::class.java, Uri::class.java, String::class.java,
                    Class.forName("android.os.ICancellationSignal"), IBinder::class.java,
                )
                args = arrayOf("com.android.shell", null, uri, "r", null, Binder())
            } else {
                method = provider.javaClass.getMethod(
                    "openFile", String::class.java, Uri::class.java, String::class.java,
                    Class.forName("android.os.ICancellationSignal"), IBinder::class.java,
                )
                args = arrayOf("com.android.shell", uri, "r", null, Binder())
            }
            return method.invoke(provider, *args) as ParcelFileDescriptor?
        }
    }
}

/** Response of GET /clipboard. */
// obf: C0062 (toString "Clipboard(text=...")
data class Clipboard(
    @field:SerializedName("text") val text: String,          // obf: f140
    @field:SerializedName("has_image") val hasImage: Boolean, // obf: f139
)
