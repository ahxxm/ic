package io.ahxxm.ic.ui

import android.net.Uri
import io.ahxxm.ic.domain.ImageCompressionPreview
import io.ahxxm.ic.domain.ImageItem
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import java.io.File

/**
 * Expected Review-owned batch API:
 * `ReviewSession(previews)` keeps selections, the open comparison index, and the compressed temp
 * files for the whole Review visit. `discard()` is the only operation that deletes those files;
 * `confirm()` returns the selected previews and leaves their files for Progress to consume.
 */
@RunWith(RobolectricTestRunner::class)
class ReviewSessionTest {

    private lateinit var cacheDir: File

    @Before
    fun setUp() {
        cacheDir = File(
            RuntimeEnvironment.getApplication().cacheDir,
            "review-session-${System.nanoTime()}"
        ).apply { mkdirs() }
    }

    @After
    fun tearDown() {
        cacheDir.deleteRecursively()
    }

    @Test
    fun `opening and closing comparison preserves the batch and selections`() {
        val previews = previews(3)
        val session = ReviewSession(previews)

        session.setSelected(1, false)
        session.openComparison(2)

        assertEquals(2, session.comparisonIndex)
        assertTrue(session.isComparing)
        assertEquals(listOf(true, false, true), session.selections)
        assertEquals(previews, session.previews)

        session.closeComparison()

        assertNull(session.comparisonIndex)
        assertFalse(session.isComparing)
        assertEquals(listOf(true, false, true), session.selections)
        assertEquals(previews, session.previews)
    }

    @Test
    fun `comparison does not delete compressed temporary files`() {
        val previews = previews(3)
        val session = ReviewSession(previews)

        session.openComparison(1)

        previews.forEach { assertTrue(it.tempFile!!.exists()) }
    }

    @Test
    fun `discard deletes every compressed temporary file`() {
        val previews = previews(3)
        val session = ReviewSession(previews)

        session.discard()

        previews.forEach { assertFalse(it.tempFile!!.exists()) }
    }

    @Test
    fun `confirm returns selected previews and keeps their temporary files`() {
        val previews = previews(3)
        val session = ReviewSession(previews)

        session.setSelected(0, false)
        val confirmed = session.confirm()

        assertEquals(listOf("photo-01.jpg", "photo-02.jpg"), confirmed.map { it.image.name })
        previews.forEach { assertTrue(it.tempFile!!.exists()) }

        // Progress consumes these files next; leaving Review must not delete them first.
        session.discard()
        previews.forEach { assertTrue(it.tempFile!!.exists()) }
    }

    private fun previews(count: Int): List<ImageCompressionPreview> =
        List(count) { index ->
            val original = File(cacheDir, "original-$index.jpg").apply { writeBytes(byteArrayOf(1, 2)) }
            val compressed = File(cacheDir, "compressed-$index.jpg").apply { writeBytes(byteArrayOf(3, 4)) }
            ImageCompressionPreview(
                image = ImageItem(
                    uri = Uri.fromFile(original),
                    name = "photo-0$index.jpg",
                    sizeBytes = 100_000L,
                    mimeType = "image/jpeg"
                ),
                originalSize = 100_000L,
                compressedSize = 70_000L,
                tempFile = compressed
            )
        }
}
