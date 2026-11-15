package io.ahxxm.ic.domain

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.exifinterface.media.ExifInterface
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

class HalvingDecisionTest {

    @Test
    fun `source above 20MP is halved`() {
        assertTrue(ImageCompressor.shouldHalve(8000, 6000, true))
    }

    @Test
    fun `source below 20MP is skipped`() {
        assertFalse(ImageCompressor.shouldHalve(4000, 3000, true))
    }

    @Test
    fun `exactly 20MP is skipped`() {
        assertFalse(ImageCompressor.shouldHalve(5000, 4000, true))
    }

    @Test
    fun `option off never halves`() {
        assertFalse(ImageCompressor.shouldHalve(8000, 6000, false))
    }

    @Test
    fun `odd integer tokens floor, rationals double denominator, garbage passes through`() {
        assertEquals("1000 1500 50 50", ImageCompressor.halveNumericExifValue("2000 3001 101 100"))
        assertEquals("350/2", ImageCompressor.halveNumericExifValue("350/1"))
        assertEquals("not a number", ImageCompressor.halveNumericExifValue("not a number"))
    }
}

@RunWith(AndroidJUnit4::class)
@Config(sdk = [36])
class DecodeHalvingTest {

    @Test
    fun `inSampleSize 2 halves decoder output`() {
        // Even size: exact halving under any rounding convention
        val source = newJpeg(2000, 1500)
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        source.inputStream().use { BitmapFactory.decodeStream(it, null, bounds) }
        assertEquals(2000, bounds.outWidth)
        assertEquals(1500, bounds.outHeight)

        val even = requireNotNull(
            BitmapFactory.decodeStream(
                source.inputStream(),
                null,
                BitmapFactory.Options().apply { inSampleSize = 2 }
            )
        ) { "decode(2000x1500, inSampleSize = 2) returned null" }
        assertEquals(1000, even.width)
        assertEquals(750, even.height)
        even.recycle()

        // Odd size: on-device libjpeg rounds up, Robolectric's decoder rounds
        // down, so pin "within one pixel of half" instead of one convention
        val odd = requireNotNull(
            BitmapFactory.decodeStream(
                newJpeg(2001, 1501).inputStream(),
                null,
                BitmapFactory.Options().apply { inSampleSize = 2 }
            )
        ) { "decode(2001x1501, inSampleSize = 2) returned null" }
        assertTrue("odd width ${odd.width} not ~half of 2001", odd.width in 1000..1001)
        assertTrue("odd height ${odd.height} not ~half of 1501", odd.height in 750..751)
        odd.recycle()
    }

    private fun newJpeg(width: Int, height: Int): File {
        val file = File.createTempFile("halving", ".jpg")
        file.deleteOnExit()
        Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
            .compress(Bitmap.CompressFormat.JPEG, 85, file.outputStream())
        return file
    }
}

@RunWith(AndroidJUnit4::class)
@Config(sdk = [36])
class ExifRestatementTest {

    @Test
    fun `restate writes output dimensions and halves pixel-derived tags`() {
        val jpeg = newJpeg()
        val source = ExifInterface(jpeg)
        source.setAttribute(ExifInterface.TAG_X_RESOLUTION, "350/1")
        source.setAttribute(ExifInterface.TAG_FOCAL_PLANE_X_RESOLUTION, "800/1")
        source.setAttribute(ExifInterface.TAG_SUBJECT_AREA, "2000 3000 100 100")
        source.setAttribute(ExifInterface.TAG_SUBJECT_LOCATION, "2000 3000")
        source.saveAttributes()

        val dest = ExifInterface(jpeg)
        ImageCompressor.restateResolutionTags(dest, 1000, 750)
        dest.saveAttributes()

        val out = ExifInterface(jpeg)
        val expected = mapOf(
            ExifInterface.TAG_IMAGE_WIDTH to "1000",
            ExifInterface.TAG_IMAGE_LENGTH to "750",
            ExifInterface.TAG_PIXEL_X_DIMENSION to "1000",
            ExifInterface.TAG_PIXEL_Y_DIMENSION to "750",
            ExifInterface.TAG_X_RESOLUTION to "350/2",
            ExifInterface.TAG_FOCAL_PLANE_X_RESOLUTION to "800/2",
            ExifInterface.TAG_SUBJECT_AREA to "1000 1500 50 50",
            ExifInterface.TAG_SUBJECT_LOCATION to "1000 1500",
        )
        val mismatches = expected.entries
            .filter { out.getAttribute(it.key) != it.value }
            .joinToString("\n") { (tag, wrote) ->
                "${tag}: wrote $wrote, read ${out.getAttribute(tag)}"
            }
        assertTrue("tags changed across restate+save:\n$mismatches", mismatches.isEmpty())
    }

    // SOF dimensions match the restated ones: ExifInterface may normalize
    // primary dimension tags against the stream, so both paths must agree
    private fun newJpeg(): File {
        val file = File.createTempFile("exif", ".jpg")
        file.deleteOnExit()
        Bitmap.createBitmap(1000, 750, Bitmap.Config.ARGB_8888)
            .compress(Bitmap.CompressFormat.JPEG, 90, file.outputStream())
        return file
    }
}
