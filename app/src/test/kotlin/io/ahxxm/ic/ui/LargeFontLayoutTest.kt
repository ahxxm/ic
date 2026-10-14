package io.ahxxm.ic.ui

import android.Manifest
import android.os.Build
import androidx.activity.ComponentActivity
import androidx.compose.runtime.Composable
import androidx.compose.ui.test.DeviceConfigurationOverride
import androidx.compose.ui.test.FontScale
import androidx.compose.ui.test.ForcedSize
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.ahxxm.ic.ui.theme.ImageCompressorTheme
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.GraphicsMode

/**
 * Layout regression tests for system font scale 2.0 (Settings > Display size and text).
 *
 * Runs on the JVM under Robolectric, as part of testDebugUnitTest. The viewport is pinned to
 * 360x640dp so the assertions do not depend on the machine running them.
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class LargeFontLayoutTest {

    @get:Rule
    val rule = createAndroidComposeRule<ComponentActivity>()

    private val viewport = DpSize(360.dp, 640.dp)

    @Before
    fun grantMediaPermission() {
        val permission = if (Build.VERSION.SDK_INT >= 33) {
            Manifest.permission.READ_MEDIA_IMAGES
        } else {
            Manifest.permission.READ_EXTERNAL_STORAGE
        }
        shadowOf(RuntimeEnvironment.getApplication()).grantPermissions(permission)
    }

    private fun setContent(content: @Composable () -> Unit) {
        rule.setContent {
            DeviceConfigurationOverride(DeviceConfigurationOverride.ForcedSize(viewport)) {
                DeviceConfigurationOverride(DeviceConfigurationOverride.FontScale(2f)) {
                    ImageCompressorTheme {
                        content()
                    }
                }
            }
        }
    }

    @Test
    fun optionsScreen_compressButtonIsInsideViewport() {
        setContent {
            OptionsScreen(bucketId = 1L, onConfirm = {}, onBack = {})
        }

        val button = rule.onNodeWithText("Compress").getUnclippedBoundsInRoot()

        assertTrue(
            "Compress button bottom=${button.bottom} exceeds viewport height=${viewport.height}",
            button.bottom <= viewport.height
        )
    }

    @Test
    fun optionsScreen_chipsFitInOneLine() {
        setContent {
            OptionsScreen(bucketId = 1L, onConfirm = {}, onBack = {})
        }

        val oneLine = 64.dp
        listOf("ok q75", "good q85", "great q90").forEach { label ->
            val chip = rule.onNodeWithText(label).getUnclippedBoundsInRoot()
            assertTrue(
                "chip \"$label\" height=${chip.bottom - chip.top} right=${chip.right} " +
                    "does not fit a ${viewport.width} wide viewport",
                chip.bottom - chip.top <= oneLine && chip.right <= viewport.width
            )
        }
    }

    @Test
    fun folderBrowserHeader_titleDoesNotOverlapRefresh() {
        setContent {
            FolderBrowserScreen(onFolderSelected = { _, _ -> })
        }

        val title = rule.onNodeWithText("Select folder to compress").getUnclippedBoundsInRoot()
        val refresh = rule.onNodeWithText("Refresh").getUnclippedBoundsInRoot()

        assertTrue(
            "title right=${title.right} overlaps Refresh left=${refresh.left}",
            title.right <= refresh.left
        )
    }
}
