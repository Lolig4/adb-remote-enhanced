package com.jcapps.adbserver

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import com.koushikdutta.async.http.server.AsyncHttpServerResponse
import org.xmlpull.v1.XmlPullParser
import java.io.File
import java.io.FileOutputStream
import java.util.Locale

/** Bitmap / drawable helpers of the icon endpoint (R8 merged them into C0335 and AbstractC0353). */
internal object IconUtils {

    /** PNG-encodes [bitmap] into [file]; the file on success, null otherwise. */
    // obf: C0335.m327
    fun saveBitmap(file: File, bitmap: Bitmap): File? {
        return try {
            // BUG(original): the FileOutputStream is never closed (fd leaks until GC).
            if (!bitmap.compress(Bitmap.CompressFormat.PNG, 100, FileOutputStream(file))) null else file
        } catch (e: Exception) {
            null
        }
    }

    /**
     * Drawable -> Bitmap at its intrinsic size (androidx-ktx `Drawable.toBitmap()` inlined).
     * Throws for drawables without an intrinsic size (createBitmap with <= 0).
     */
    // obf: C0335.m328
    fun drawableToBitmap(drawable: Drawable): Bitmap {
        val width = drawable.intrinsicWidth
        val height = drawable.intrinsicHeight
        if (drawable is BitmapDrawable) {
            // RECON-UNSURE: the dex compares against bitmapDrawable.intrinsicWidth/Height (always
            // equal to the values above), so the scaled branch is effectively dead.
            return if (width == drawable.intrinsicWidth && height == drawable.intrinsicHeight) {
                drawable.bitmap
            } else {
                Bitmap.createScaledBitmap(drawable.bitmap, width, height, true)
            }
        }
        // Fixed (rebuild): copy the bounds; getBounds() returns the live Rect, so the original's
        // "restore" re-applied the new bounds.
        val oldBounds = android.graphics.Rect(drawable.bounds)
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        drawable.setBounds(0, 0, width, height)
        drawable.draw(Canvas(bitmap))
        drawable.setBounds(oldBounds.left, oldBounds.top, oldBounds.right, oldBounds.bottom)
        return bitmap
    }

    /** A [width] x [height] bitmap filled with [color]. */
    // obf: C0335.m330
    fun solidBitmap(width: Int, height: Int, color: Int): Bitmap {
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val paint = Paint()
        paint.color = color
        canvas.drawRect(0.0f, 0.0f, width.toFloat(), height.toFloat(), paint)
        return bitmap
    }

    /**
     * Inflates a compiled (binary) drawable XML through the hidden `android.content.res.XmlBlock`.
     * [resources] must be the APK's own (see AppsHandler.apkResources), else references into the
     * app's resources (@color/..., @drawable/...) cannot resolve.
     */
    // obf: AbstractC0353.m358
    fun getDrawableFromXml(resources: android.content.res.Resources, binXml: ByteArray): Drawable? {
        return try {
            val xmlBlock = Class.forName("android.content.res.XmlBlock")
            val constructor = xmlBlock.getConstructor(ByteArray::class.java)
            val newParser = xmlBlock.getDeclaredMethod("newParser")
            constructor.isAccessible = true
            newParser.isAccessible = true
            val parser = newParser.invoke(constructor.newInstance(binXml)) as XmlPullParser
            Drawable.createFromXml(resources, parser)
        } catch (e: Exception) {
            null
        }
    }
}

/** Sends [imageFile] with a Content-Type derived from its extension (default image/png). */
// obf: AbstractC0353.m368
internal fun AsyncHttpServerResponse.sendImageFile(imageFile: File) {
    val mimeType = when (imageFile.name.substringAfterLast('.', "").lowercase(Locale.ROOT)) {
        "bmp" -> "image/bmp"
        "gif" -> "image/gif"
        "jpg" -> "image/jpeg"
        "svg" -> "image/svg+xml"
        "tif" -> "image/tiff"
        "jpeg" -> "image/jpeg"
        "tiff" -> "image/tiff"
        "webp" -> "image/webp"
        else -> "image/png"
    }
    headers.set("Content-Type", mimeType)
    sendFile(imageFile)
}
