package ai.sotuvchi.app.features.catalog

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import androidx.exifinterface.media.ExifInterface
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import kotlin.math.max
import kotlin.math.roundToInt

/**
 * Prepares photos for upload. The server takes JPEG, PNG or WebP up to 5 MB and
 * checks the type from the bytes, so every photo is re-encoded as JPEG,
 * resized and compressed on the phone first.
 */
object ImageCompressor {
    const val MAX_BYTES = 4_800_000
    const val MAX_DIMENSION = 1600

    /** Target size for an image, keeping its proportions; unchanged when it already fits. */
    fun fit(width: Int, height: Int, maxDimension: Int = MAX_DIMENSION): Pair<Int, Int> {
        val longest = max(width, height)
        if (longest <= maxDimension) return width to height
        val scale = maxDimension.toDouble() / longest
        return max(1, (width * scale).roundToInt()) to max(1, (height * scale).roundToInt())
    }

    /** The JPEG quality steps tried until the result fits the limit. */
    fun qualitySteps(): List<Int> = generateSequence(82) { it - 12 }.takeWhile { it > 30 }.toList()

    /** Returns a JPEG of at most [MAX_BYTES], or null when the bytes are not an image or cannot be made small enough. */
    fun jpeg(raw: ByteArray): ByteArray? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(raw, 0, raw.size, bounds)
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null

        // Decode at a size close to the target, then do the exact resize.
        var sample = 1
        while (max(bounds.outWidth, bounds.outHeight) / (sample * 2) >= MAX_DIMENSION) sample *= 2
        val options = BitmapFactory.Options().apply { inSampleSize = sample }
        var bitmap = BitmapFactory.decodeByteArray(raw, 0, raw.size, options) ?: return null

        bitmap = rotated(bitmap, orientation(raw))
        val (width, height) = fit(bitmap.width, bitmap.height)
        if (width != bitmap.width || height != bitmap.height) {
            bitmap = Bitmap.createScaledBitmap(bitmap, width, height, true)
        }
        for (quality in qualitySteps()) {
            val out = ByteArrayOutputStream()
            bitmap.compress(Bitmap.CompressFormat.JPEG, quality, out)
            if (out.size() <= MAX_BYTES) return out.toByteArray()
        }
        return null
    }

    private fun orientation(raw: ByteArray): Int = try {
        ExifInterface(ByteArrayInputStream(raw)).getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)
    } catch (_: Exception) {
        ExifInterface.ORIENTATION_NORMAL
    }

    private fun rotated(bitmap: Bitmap, orientation: Int): Bitmap {
        val matrix = Matrix()
        when (orientation) {
            ExifInterface.ORIENTATION_ROTATE_90 -> matrix.postRotate(90f)
            ExifInterface.ORIENTATION_ROTATE_180 -> matrix.postRotate(180f)
            ExifInterface.ORIENTATION_ROTATE_270 -> matrix.postRotate(270f)
            ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> matrix.preScale(-1f, 1f)
            ExifInterface.ORIENTATION_FLIP_VERTICAL -> matrix.preScale(1f, -1f)
            else -> return bitmap
        }
        return Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
    }
}
