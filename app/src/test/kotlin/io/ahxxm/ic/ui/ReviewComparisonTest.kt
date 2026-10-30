package io.ahxxm.ic.ui

import android.net.Uri
import androidx.activity.ComponentActivity
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.hasScrollToIndexAction
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
    fun `tapping a preview opens an aligned adjustable comparison`() {
        setReview(previews(3))

        rule.onNodeWithText("photo-00.jpg").performClick()
        rule.waitForIdle()

        comparisonSlider().assertExists()
        rule.onNodeWithText("Apply to 3 Images").assertDoesNotExist()
        assertSameBounds(
            rule.onNodeWithContentDescription("Original"),
            rule.onNodeWithContentDescription("Compressed")
        )
        rule.onNodeWithText("Review Savings").assertExists()
    }

    @Test
    fun `toolbar back from comparison restores the same list and selection`() {
        val previews = previews(3)
        setReview(previews)

        rule.onAllNodes(isToggleable())[1].performClick()
        rule.onNodeWithText("2 of 3 selected", substring = true).assertExists()

        val tempFiles = previews.map { requireNotNull(it.tempFile) }
        rule.onNodeWithText("photo-00.jpg").performClick()
        comparisonSlider().assertExists()

        rule.onNodeWithContentDescription("Back").performClick()
        rule.waitForIdle()

        comparisonSlider().assertDoesNotExist()
        rule.onNodeWithText("2 of 3 selected", substring = true).assertExists()
        assertEquals(0, backCount)
        tempFiles.forEach { assertTrue("${it.name} was deleted", it.exists()) }
    }

    @Test
    fun `system back closes comparison before leaving review`() {
        val previews = previews(3)
        setReview(previews)

        rule.onNodeWithText("photo-01.jpg").performClick()
        rule.waitForIdle()
        comparisonSlider().assertExists()

        pressSystemBack()

        comparisonSlider().assertDoesNotExist()
        rule.onNodeWithText("3 of 3 selected", substring = true).assertExists()
        assertEquals(0, backCount)
        previews.forEach { preview ->
            val tempFile = requireNotNull(preview.tempFile)
            assertTrue("${tempFile.name} was deleted", tempFile.exists())
        }
    }

    @Test
    fun `system back on the result list leaves review and discards the batch`() {
        val previews = previews(3)
        setReview(previews)

        pressSystemBack()

        rule.onNodeWithText("Review Savings").assertDoesNotExist()
        assertEquals(1, backCount)
        previews.forEach { preview ->
            val tempFile = requireNotNull(preview.tempFile)
            assertFalse("${tempFile.name} survived discard", tempFile.exists())
        }
    }

    @Test
    fun `apply hands selected previews to the next stage without deleting them`() {
        val previews = previews(3)
        var applied: List<ImageCompressionPreview>? = null
        setReview(previews, onConfirm = { applied = it })

        rule.onAllNodes(isToggleable())[1].performClick()
        rule.onNodeWithText("Apply to 2 Images").performClick()
        rule.waitForIdle()

        assertEquals(listOf("photo-00.jpg", "photo-02.jpg"), applied?.map { it.image.name })
        rule.onNodeWithText("Review Savings").assertDoesNotExist()
        previews.forEach { assertTrue(requireNotNull(it.tempFile).exists()) }
    }

    @Test
    fun `split slider adjusts from 0 to 1`() {
        setReview(previews(3))
        rule.onNodeWithText("photo-02.jpg").performClick()
        rule.waitForIdle()

        val slider = comparisonSlider()
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
        setReview(previews(14))

        rule.onNode(hasScrollToIndexAction()).performScrollToIndex(10)
        rule.onNodeWithText("photo-10.jpg").performClick()
        comparisonSlider().assertExists()

        rule.onNodeWithContentDescription("Back").performClick()
        rule.waitForIdle()

        rule.onNodeWithText("photo-10.jpg").assertExists()
    }

    private fun comparisonSlider() =
        rule.onNode(SemanticsMatcher.keyIsDefined(SemanticsActions.SetProgress))

    private fun setReview(
        previews: List<ImageCompressionPreview>,
        onConfirm: (List<ImageCompressionPreview>) -> Unit = {}
    ) {
        rule.setContent {
            ImageCompressorTheme {
                var visible by remember { mutableStateOf(true) }
                if (visible) {
                    SavingsReviewScreen(
                        previews = previews,
                        onConfirm = { selected ->
                            onConfirm(selected)
                            visible = false
                        },
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

    private fun previews(count: Int): List<ImageCompressionPreview> =
        List(count) { index -> preview(index) }

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
