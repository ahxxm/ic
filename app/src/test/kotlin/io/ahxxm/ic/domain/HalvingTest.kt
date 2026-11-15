package io.ahxxm.ic.domain

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.exifinterface.media.ExifInterface
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
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
    fun `inSampleSize 2 halves decoder output, odd dimensions round up`() {
        val source = newJpeg(2001, 1501)

        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        source.inputStream().use { BitmapFactory.decodeStream(it, null, bounds) }
        assertEquals(2001, bounds.outWidth)
        assertEquals(1501, bounds.outHeight)

        val decoded = BitmapFactory.decodeStream(
            source.inputStream(),
            null,
            BitmapFactory.Options().apply { inSampleSize = 2 }
        )
        assertNotNull(decoded)
        assertEquals(1001, decoded.width)
        assertEquals(751, decoded.height)
        decoded.recycle()
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
        assertEquals("1000", out.getAttribute(ExifInterface.TAG_IMAGE_WIDTH))
        assertEquals("750", out.getAttribute(ExifInterface.TAG_IMAGE_LENGTH))
        assertEquals("1000", out.getAttribute(ExifInterface.TAG_PIXEL_X_DIMENSION))
        assertEquals("750", out.getAttribute(ExifInterface.TAG_PIXEL_Y_DIMENSION))
        assertEquals("350/2", out.getAttribute(ExifInterface.TAG_X_RESOLUTION))
        assertEquals("800/2", out.getAttribute(ExifInterface.TAG_FOCAL_PLANE_X_RESOLUTION))
        assertEquals("1000 1500 50 50", out.getAttribute(ExifInterface.TAG_SUBJECT_AREA))
        assertEquals("1000 1500", out.getAttribute(ExifInterface.TAG_SUBJECT_LOCATION))
    }

    private fun newJpeg(): File {
        val file = File.createTempFile("exif", ".jpg")
        file.deleteOnExit()
        Bitmap.createBitmap(8, 8, Bitmap.Config.ARGB_8888)
            .compress(Bitmap.CompressFormat.JPEG, 90, file.outputStream())
        return file
    }
}
