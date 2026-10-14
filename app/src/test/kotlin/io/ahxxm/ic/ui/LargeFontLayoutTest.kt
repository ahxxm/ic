package io.ahxxm.ic.ui

import android.Manifest
import android.os.Build
import androidx.activity.ComponentActivity
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.DeviceConfigurationOverride
import androidx.compose.ui.test.FontScale
import androidx.compose.ui.test.ForcedSize
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import io.ahxxm.ic.ui.theme.ImageCompressorTheme
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.ParameterizedRobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.GraphicsMode

/**
 * The Robolectric sandbox is 320x470dp and clamps ForcedSize, so every case has to fit inside it or
 * it would silently measure the sandbox instead. NATIVE graphics keeps text measurement real. The
 * platform jar for targetSdk needs Java 21.
 */
@RunWith(ParameterizedRobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class LargeFontLayoutTest(
    widthDp: Int,
    heightDp: Int,
    private val fontScale: Float,
) {

    // DpSize is an inline value class, erased to a long in the constructor, so JUnit cannot pass it.
    private val viewport = DpSize(widthDp.dp, heightDp.dp)

    @get:Rule
    val rule = createAndroidComposeRule<ComponentActivity>()

    private var density: Density = Density(1f)
    private var labelLineHeight: Dp = 0.dp
    private var bodyLineHeight: Dp = 0.dp

    @Before
    fun grantMediaPermission() {
        val permission = if (Build.VERSION.SDK_INT >= 33) {
            Manifest.permission.READ_MEDIA_IMAGES
        } else {
            Manifest.permission.READ_EXTERNAL_STORAGE
        }
        shadowOf(RuntimeEnvironment.getApplication()).grantPermissions(permission)
    }

    private fun compose(content: @Composable () -> Unit) {
        rule.setContent {
            DeviceConfigurationOverride(DeviceConfigurationOverride.ForcedSize(viewport)) {
                DeviceConfigurationOverride(DeviceConfigurationOverride.FontScale(fontScale)) {
                    density = LocalDensity.current
                    labelLineHeight =
                        with(density) { MaterialTheme.typography.labelLarge.lineHeight.toDp() }
                    bodyLineHeight =
                        with(density) { MaterialTheme.typography.bodyMedium.lineHeight.toDp() }
                    ImageCompressorTheme {
                        content()
                    }
                }
            }
        }
    }

    private data class TextBounds(
        val text: String,
        val left: Dp,
        val top: Dp,
        val right: Dp,
        val bottom: Dp,
    )

    private fun textNodes(): List<TextBounds> =
        rule.onAllNodes(SemanticsMatcher.keyIsDefined(SemanticsProperties.Text))
            .fetchSemanticsNodes()
            .map { node ->
                val text = node.config[SemanticsProperties.Text].joinToString("") { it.text }
                TextBounds(
                    text = text,
                    left = with(density) { node.boundsInRoot.left.toDp() },
                    top = with(density) { node.boundsInRoot.top.toDp() },
                    right = with(density) { node.boundsInRoot.right.toDp() },
                    bottom = with(density) { node.boundsInRoot.bottom.toDp() },
                )
            }

    private fun assertViewportIsHonored() {
        val root = rule.onRoot().getUnclippedBoundsInRoot()
        assertTrue(
            "sandbox window ${root.right - root.left} x ${root.bottom - root.top} is smaller " +
                "than the pinned ${viewport.width} x ${viewport.height}",
            root.right - root.left >= viewport.width && root.bottom - root.top >= viewport.height
        )
    }

    private fun assertTextStaysInsideHorizontally() {
        textNodes().forEach { bounds ->
            assertTrue(
                "\"${bounds.text}\" left=${bounds.left} right=${bounds.right} escapes the " +
                    "${viewport.width} wide viewport",
                bounds.left >= 0.dp && bounds.right <= viewport.width
            )
        }
    }

    // Below the fold is legitimate once a screen scrolls, so scroll before asserting.
    private fun assertReachable(label: String) {
        val node = rule.onNodeWithText(label)
        val before = node.getUnclippedBoundsInRoot()
        if (before.right <= viewport.width && before.bottom <= viewport.height) return

        runCatching { node.performScrollTo() }
        val after = node.getUnclippedBoundsInRoot()
        assertTrue(
            "\"$label\" bottom=${after.bottom} right=${after.right} is outside " +
                "${viewport.width} x ${viewport.height}",
            after.right <= viewport.width && after.bottom <= viewport.height
        )
    }

    private fun assertChipsFollowLabel(label: String, chips: List<String>) {
        val labelBox = rule.onNodeWithText(label, useUnmergedTree = true)
            .getUnclippedBoundsInRoot()
        val boxes = chips.map { rule.onNodeWithText(it).getUnclippedBoundsInRoot() }
        val first = boxes.first()

        assertTrue(
            "first chip left=${first.left} is not under the label at ${labelBox.left}",
            first.left - labelBox.left <= 8.dp
        )
        assertTrue(
            "first chip top=${first.top} shares the \"$label\" row, heading bottom=" +
                "${labelBox.bottom}",
            first.top - labelBox.bottom >= -8.dp
        )
        // Chips on one line run left to right, so it is the start of each line that must line up.
        boxes.groupBy { it.top }.forEach { (top, line) ->
            val lineStart = line.minOf { it.left }
            val delta = lineStart - first.left
            assertTrue(
                "chips on line top=$top start at $lineStart instead of ${first.left}",
                delta <= 8.dp && delta >= -8.dp
            )
        }
    }

    private fun assertLabelsStayOnOneLine(labels: List<String>, lineHeight: Dp) {
        labels.forEach { label ->
            val text = rule.onNodeWithText(label, useUnmergedTree = true)
                .getUnclippedBoundsInRoot()
            val height = text.bottom - text.top
            assertTrue(
                "\"$label\" height=$height wraps at font scale $fontScale, expected one line " +
                    "of $lineHeight",
                height <= lineHeight * 1.5f
            )
        }
    }

    private fun assertNoTextNodesOverlap() {
        val nodes = textNodes()
        for (i in nodes.indices) {
            for (j in i + 1 until nodes.size) {
                val a = nodes[i]
                val b = nodes[j]
                val disjoint = a.right <= b.left || b.right <= a.left ||
                    a.bottom <= b.top || b.bottom <= a.top
                assertTrue("\"${a.text}\" overlaps \"${b.text}\"", disjoint)
            }
        }
    }

    private fun assertOptionsScreenHolds() {
        assertTextStaysInsideHorizontally()
        assertChipsFollowLabel("Quality", listOf("ok q75", "good q85", "great q90"))
        assertLabelsStayOnOneLine(
            listOf("ok q75", "good q85", "great q90", "Compress"),
            labelLineHeight
        )
        assertLabelsStayOnOneLine(listOf("Keep EXIF", "Include PNG"), bodyLineHeight)
        assertNoTextNodesOverlap()
        assertReachable("Compress")
    }

    private fun assertFolderBrowserHolds() {
        assertTextStaysInsideHorizontally()
        assertLabelsStayOnOneLine(listOf("Refresh"), labelLineHeight)
        assertNoTextNodesOverlap()
        assertReachable("Refresh")
    }

    @Test
    fun optionsScreen() {
        compose {
            OptionsScreen(bucketId = 1L, onConfirm = {}, onBack = {})
        }
        assertViewportIsHonored()
        assertOptionsScreenHolds()
    }

    @Test
    fun folderBrowser() {
        compose {
            FolderBrowserScreen(onFolderSelected = { _, _ -> })
        }
        assertViewportIsHonored()
        assertFolderBrowserHolds()
    }

    companion object {
        @JvmStatic
        @ParameterizedRobolectricTestRunner.Parameters(name = "{0}x{1} fontScale {2}")
        fun cases(): List<Array<Any>> = listOf(
            arrayOf<Any>(320, 470, 0.85f),
            arrayOf<Any>(320, 470, 1f),
            arrayOf<Any>(320, 470, 2f),
            arrayOf<Any>(277, 400, 1f),
            arrayOf<Any>(277, 400, 2f),
        )
    }
}
