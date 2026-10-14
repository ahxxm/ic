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
import androidx.compose.ui.test.onAllNodes
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
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
 * Viewport and font scale are pinned per case. Robolectric's sandbox screen is 320x470dp and
 * ForcedSize is clamped by it, so cases stay inside that or they would silently measure the
 * sandbox size. NATIVE graphics keeps text measurement real instead of stubbed. The platform jar
 * for targetSdk needs Java 21, as installed in CI.
 *
 * Two settings break these screens and are exercised separately: font scale enlarges text without
 * shrinking the window, display size shrinks the window without enlarging text.
 *
 * Every case asserts the same three properties: nothing escapes the viewport, single-line labels
 * stay on one line, and no two text nodes overlap.
 */
@RunWith(ParameterizedRobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class LargeFontLayoutTest(
    private val viewport: DpSize,
    private val fontScale: Float,
) {

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
                val text = node.config
                    .getOrNull(SemanticsProperties.Text)
                    .orEmpty()
                    .joinToString("") { it.text }
                TextBounds(
                    text = text,
                    left = with(density) { node.boundsInRoot.left.toDp() },
                    top = with(density) { node.boundsInRoot.top.toDp() },
                    right = with(density) { node.boundsInRoot.right.toDp() },
                    bottom = with(density) { node.boundsInRoot.bottom.toDp() },
                )
            }

    // The case is only real if the window is at least as big as the pinned viewport, since
    // ForcedSize is clamped by it. Without this a smaller sandbox would silently pass everything.
    private fun assertViewportIsHonored() {
        val root = rule.onRoot().getUnclippedBoundsInRoot()
        assertTrue(
            "sandbox window ${root.right - root.left} x ${root.bottom - root.top} is smaller " +
                "than the pinned ${viewport.width} x ${viewport.height}",
            root.right - root.left >= viewport.width && root.bottom - root.top >= viewport.height
        )
    }

    private fun assertNothingEscapesViewport() {
        textNodes().forEach { (text, bounds) ->
            assertTrue(
                "\"$text\" right=${bounds.right} bottom=${bounds.bottom} escapes " +
                    "${viewport.width} x ${viewport.height}",
                bounds.right <= viewport.width && bounds.bottom <= viewport.height
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
        assertNothingEscapesViewport()
        assertLabelsStayOnOneLine(
            listOf("ok q75", "good q85", "great q90", "Compress"),
            labelLineHeight
        )
        assertLabelsStayOnOneLine(listOf("Keep EXIF", "Include PNG"), bodyLineHeight)
        assertNoTextNodesOverlap()
    }

    private fun assertFolderBrowserHolds() {
        assertNothingEscapesViewport()
        assertLabelsStayOnOneLine(listOf("Refresh"), labelLineHeight)
        assertNoTextNodesOverlap()
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
        @ParameterizedRobolectricTestRunner.Parameters(name = "{0}")
        fun cases(): List<Array<Any>> = listOf(
            arrayOf("phone fontScale below 1", DpSize(320.dp, 470.dp), 0.85f),
            arrayOf("phone fontScale 1", DpSize(320.dp, 470.dp), 1f),
            arrayOf("phone fontScale 2", DpSize(320.dp, 470.dp), 2f),
            arrayOf("shrunk phone fontScale 1", DpSize(277.dp, 400.dp), 1f),
            arrayOf("shrunk phone fontScale 2", DpSize(277.dp, 400.dp), 2f),
        )
    }
}
