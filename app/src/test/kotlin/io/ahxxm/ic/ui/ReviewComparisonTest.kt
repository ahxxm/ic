package io.ahxxm.ic.ui

import android.net.Uri
import androidx.activity.ComponentActivity
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasScrollToIndexAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isToggleable
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToIndex
import androidx.compose.ui.test.performSemanticsAction
import coil3.ColorImage
import coil3.ImageLoader
import coil3.SingletonImageLoader
import coil3.annotation.DelicateCoilApi
import coil3.test.FakeImageLoaderEngine
import io.ahxxm.ic.domain.ImageCompressionPreview
import io.ahxxm.ic.domain.ImageItem
import io.ahxxm.ic.ui.theme.ImageCompressorTheme
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

/**
 * Expected Review UI contract:
 * - `SavingsReviewScreen(session, onConfirm, onBack)` renders the result list and an internal
 *   comparison view from the same `ReviewSession`; no comparison screen or new session is entered.
 * - The comparison is one draggable split slider with contentDescription "Comparison slider"
 *   whose progress is in 0f..1f and which accepts the SetProgress semantics action.
 * - Both images are overlaid with contentDescription "Original" and "Compressed" and equal bounds.
 * - Toolbar back and system back close comparison first; back on the result list leaves Review.
 */
@OptIn(DelicateCoilApi::class)
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w360dp-h640dp")
class ReviewComparisonTest {

    @get:Rule
    val rule = createAndroidComposeRule<ComponentActivity>()

    private lateinit var cacheDir: File
    private var backCount = 0

    @Before
    fun setUp() {
        val context = RuntimeEnvironment.getApplication()
        cacheDir = File(
            context.cacheDir,
            "review-comparison-${System.nanoTime()}"
        ).apply { mkdirs() }
        backCount = 0

        // Coil's real loader can abort Robolectric native graphics (coil-kt/coil#3049).
        val engine = FakeImageLoaderEngine.Builder()
            .default(ColorImage())
            .build()
        SingletonImageLoader.setUnsafe(
            ImageLoader.Builder(context)
                .components { add(engine) }
                .build()
        )
    }

    @After
    fun tearDown() {
        SingletonImageLoader.reset()
        cacheDir.deleteRecursively()
    }

    @Test
    fun `tapping a preview shows one aligned split comparison inside review`() {
        val session = session(3)
        setReview(session)

        rule.onNodeWithText("photo-00.jpg").performClick()
        rule.waitForIdle()

        rule.onNodeWithContentDescription("Comparison slider").assertExists()
        rule.onNodeWithContentDescription("Original").assertExists()
        rule.onNodeWithContentDescription("Compressed").assertExists()
        assertSameBounds(
            rule.onNodeWithContentDescription("Original"),
            rule.onNodeWithContentDescription("Compressed")
        )

        // Still the Review result screen, not a separate comparison destination.
        rule.onNodeWithText("Review Savings").assertExists()
        rule.onNode(hasText("Original (", substring = true) and hasClickAction())
            .assertDoesNotExist()
        rule.onNode(hasText("Compressed (", substring = true) and hasClickAction())
            .assertDoesNotExist()
    }

    @Test
    fun `toolbar back from comparison restores the same list and selection`() {
        val session = session(3)
        setReview(session)

        rule.onAllNodes(isToggleable())[1].performClick()
        rule.onNodeWithText("2 of 3 selected", substring = true).assertExists()

        val tempFiles = session.previews.map { it.tempFile!! }
        rule.onNodeWithText("photo-00.jpg").performClick()
        rule.onNodeWithContentDescription("Comparison slider").assertExists()

        rule.onNodeWithContentDescription("Back").performClick()
        rule.waitForIdle()

        rule.onNodeWithContentDescription("Comparison slider").assertDoesNotExist()
        rule.onNodeWithText("2 of 3 selected", substring = true).assertExists()
        assertEquals(0, backCount)
        tempFiles.forEach { assertTrue("${it.name} was deleted", it.exists()) }
    }

    @Test
    fun `system back closes comparison before leaving review`() {
        val session = session(3)
        setReview(session)

        rule.onNodeWithText("photo-01.jpg").performClick()
        rule.waitForIdle()
        rule.onNodeWithContentDescription("Comparison slider").assertExists()

        pressSystemBack()

        rule.onNodeWithContentDescription("Comparison slider").assertDoesNotExist()
        rule.onNodeWithText("3 of 3 selected", substring = true).assertExists()
        assertEquals(0, backCount)
        session.previews.forEach { preview ->
            val tempFile = requireNotNull(preview.tempFile)
            assertTrue("${tempFile.name} was deleted", tempFile.exists())
        }
    }

    @Test
    fun `system back on the result list leaves review and discards the batch`() {
        val session = session(3)
        setReview(session)

        pressSystemBack()

        rule.onNodeWithText("Review Savings").assertDoesNotExist()
        assertEquals(1, backCount)
        session.previews.forEach { preview ->
            val tempFile = requireNotNull(preview.tempFile)
            assertFalse("${tempFile.name} survived discard", tempFile.exists())
        }
    }

    @Test
    fun `split slider exposes adjustable 0 to 1 progress semantics`() {
        val session = session(3)
        setReview(session)
        rule.onNodeWithText("photo-02.jpg").performClick()
        rule.waitForIdle()

        val slider = rule.onNodeWithContentDescription("Comparison slider")
        val before = slider.fetchSemanticsNode().config[SemanticsProperties.ProgressBarRangeInfo]
        assertEquals(0f, before.range.start)
        assertEquals(1f, before.range.endInclusive)

        slider.performSemanticsAction(SemanticsActions.SetProgress) { setProgress ->
            setProgress(0.25f)
        }
        rule.waitForIdle()

        val after = slider.fetchSemanticsNode().config[SemanticsProperties.ProgressBarRangeInfo]
        assertEquals(0.25f, after.current, 0.01f)
    }

    @Test
    fun `result list scroll position survives comparison`() {
        val session = session(14)
        setReview(session)

        rule.onNode(hasScrollToIndexAction()).performScrollToIndex(10)
        rule.onNodeWithText("photo-10.jpg").performClick()
        rule.onNodeWithContentDescription("Comparison slider").assertExists()

        rule.onNodeWithContentDescription("Back").performClick()
        rule.waitForIdle()

        rule.onNodeWithText("photo-10.jpg").assertExists()
    }

    private fun setReview(session: ReviewSession) {
        rule.setContent {
            ImageCompressorTheme {
                var visible by remember { mutableStateOf(true) }
                if (visible) {
                    SavingsReviewScreen(
                        session = session,
                        onConfirm = {},
                        onBack = {
                            visible = false
                            backCount++
                        }
                    )
                }
            }
        }
    }

    private fun pressSystemBack() {
        rule.runOnUiThread { rule.activity.onBackPressedDispatcher.onBackPressed() }
        rule.waitForIdle()
    }

    private fun assertSameBounds(original: SemanticsNodeInteraction, compressed: SemanticsNodeInteraction) {
        val originalBounds = original.getUnclippedBoundsInRoot()
        val compressedBounds = compressed.getUnclippedBoundsInRoot()
        assertEquals(originalBounds.left.value, compressedBounds.left.value, 1f)
        assertEquals(originalBounds.top.value, compressedBounds.top.value, 1f)
        assertEquals(originalBounds.right.value, compressedBounds.right.value, 1f)
        assertEquals(originalBounds.bottom.value, compressedBounds.bottom.value, 1f)
    }

    private fun session(count: Int): ReviewSession =
        ReviewSession(List(count) { index -> preview(index) })

    private fun preview(index: Int): ImageCompressionPreview {
        val original = File(cacheDir, "original.jpg")
        if (!original.exists()) writeImagePlaceholder(original)
        val compressed = File(cacheDir, "compressed-$index.jpg")
        if (!compressed.exists()) writeImagePlaceholder(compressed)

        return ImageCompressionPreview(
            image = ImageItem(
                uri = Uri.fromFile(original),
                name = "photo-%02d.jpg".format(index),
                sizeBytes = 100_000L,
                mimeType = "image/jpeg"
            ),
            originalSize = 100_000L,
            compressedSize = 70_000L,
            tempFile = compressed
        )
    }

    private fun writeImagePlaceholder(file: File) {
        file.writeBytes(byteArrayOf(0))
    }
}
