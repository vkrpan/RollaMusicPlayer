package com.rolla.musicplayer.feature.tageditor

import android.app.Activity
import android.app.PendingIntent
import android.app.RecoverableSecurityException
import android.app.RemoteAction
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.drawable.Icon
import android.net.Uri
import android.view.View
import androidx.activity.ComponentActivity
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.rolla.musicplayer.core.designsystem.theme.RollaMusicPlayerTheme
import com.rolla.musicplayer.core.designsystem.theme.destructiveText
import com.rolla.musicplayer.core.testing.isColorNear
import com.rolla.musicplayer.core.testing.pixelsIn
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

private const val PICKED_URI = "content://media/picker/0/com.android.providers.media.photopicker/media/77"
private const val SONG_URI = "content://media/external/audio/media/1"

/**
 * [SelectionArtworkActions], the Tracks selection bar's ⋮ (spec goal 5, ruling P2): its menu, the Remove confirmation,
 * the photo picker hand-off, the working ring and the delivery of the message and result to the host. The ViewModel is
 * a relaxed MockK stub whose [SelectionArtworkUiState] the test drives, so no Hilt graph is needed; the host is this
 * composable itself, which holds the menu and dialog state the way production does.
 */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [34], qualifiers = "w360dp-h640dp")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class SelectionArtworkActionsTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    private val state = MutableStateFlow(SelectionArtworkUiState())
    private val viewModel: SelectionArtworkViewModel = mockk(relaxed = true) { every { uiState } returns state }

    private val messages = mutableListOf<String>()
    private val results = mutableListOf<SelectionArtworkResult>()
    private var destructive = Color.Unspecified
    private var onSurface = Color.Unspecified

    private fun setActions(selectedIds: List<Long> = listOf(1L, 2L)) {
        composeRule.setContent {
            RollaMusicPlayerTheme(darkTheme = true) {
                destructive = MaterialTheme.colorScheme.destructiveText
                onSurface = MaterialTheme.colorScheme.onSurface
                SelectionArtworkActions(
                    selectedIds = selectedIds,
                    onResult = { results += it },
                    messages = { messages += it },
                    viewModel = viewModel,
                )
            }
        }
    }

    // Spec goal 5 and §3's mockup: ⋮ opens a menu with Set artwork above Remove artwork, Remove in destructiveText.
    // Trivial stub: nothing drawn → "More options" is missing → RED. Mutations: drop destructive = true on Remove →
    // its label draws onSurface → RED; swap the two items → RED on the order.
    @Test
    fun selectionBar_menuOffersSetAndRemove() {
        setActions()
        assertEquals("the menu is closed before the tap", 0, textCount("Set artwork"))

        composeRule.onNodeWithContentDescription("More options").performClick()

        val set = composeRule.onNodeWithText("Set artwork", useUnmergedTree = true)
            .assertExists("the open menu offers Set artwork").getUnclippedBoundsInRoot()
        val remove = composeRule.onNodeWithText("Remove artwork", useUnmergedTree = true)
            .assertExists("the open menu offers Remove artwork").getUnclippedBoundsInRoot()
        assertTrue("Set artwork is listed above Remove artwork", set.bottom <= remove.top)
        val popup = drawLatestPopup()
        val removeInk = composeRule.pixelsIn(popup, remove)
        val setInk = composeRule.pixelsIn(popup, set)
        assertTrue("control: Set artwork is drawn in onSurface", setInk.any { isColorNear(onSurface, it) })
        assertTrue("Remove artwork is drawn in destructiveText", removeInk.any { isColorNear(destructive, it) })
        assertFalse("Set artwork is not destructive", setInk.any { isColorNear(destructive, it) })
    }

    // Spec goal 5: Remove asks "Remove artwork from N tracks?" first; Cancel writes nothing, Remove hands the ids to
    // the ViewModel. Trivial stub: no menu → RED. Mutations: title without tracksCountLabel ("2 track") → RED; the
    // dialog's Remove skips onRemoveArtwork → RED on the verify.
    @Test
    fun remove_confirmDialogNamesTheCount() {
        setActions(selectedIds = listOf(1L, 2L))

        openRemoveDialog()
        composeRule.onNodeWithText("Remove artwork from 2 tracks?").assertExists("the dialog names the count")
        composeRule.onNodeWithText("Cancel").performClick()
        assertEquals("Cancel closes the dialog", 0, textCount("Remove artwork from 2 tracks?"))
        verify(exactly = 0) { viewModel.onRemoveArtwork(any()) }

        openRemoveDialog()
        composeRule.onNodeWithText("Remove").performClick()
        assertEquals("Remove closes the dialog", 0, textCount("Remove artwork from 2 tracks?"))
        verify(exactly = 1) { viewModel.onRemoveArtwork(listOf(1L, 2L)) }
    }

    // Spec goal 5: Set artwork opens the system photo picker and hands the picked image and the selection to the
    // ViewModel. Trivial stub: no menu → RED. Mutation: the picker callback drops the uri → RED on the verify.
    @Test
    fun setArtwork_handsThePickToTheViewModel() {
        setActions(selectedIds = listOf(1L, 2L))
        composeRule.onNodeWithContentDescription("More options").performClick()
        composeRule.onNodeWithText("Set artwork").performClick()
        composeRule.waitForIdle()

        val activity = shadowOf(composeRule.activity)
        val started = activity.nextStartedActivityForResult
        assertNotNull("Set artwork launches the photo picker", started)
        composeRule.runOnUiThread {
            activity.receiveResult(started.intent, Activity.RESULT_OK, Intent().setData(Uri.parse(PICKED_URI)))
        }
        composeRule.waitForIdle()

        verify(exactly = 1) { viewModel.onSetArtwork(listOf(1L, 2L), PICKED_URI) }
    }

    // Plan Task 8: while working, a primary ring described "Saving artwork" replaces ⋮ (the host reads isWorking from
    // the same ViewModel, ruling P23). Trivial stub: nothing drawn → "More options" missing → RED on the control.
    // Mutation: keep ⋮ while working → RED.
    @Test
    fun working_showsProgressInsteadOfMenu() {
        setActions()
        composeRule.onNodeWithContentDescription("More options").assertExists("control: ⋮ while idle")

        composeRule.runOnIdle { state.value = SelectionArtworkUiState(isWorking = true) }

        composeRule.onNodeWithContentDescription("Saving artwork").assertExists("the ring replaces ⋮ while working")
        assertEquals("no ⋮ while working", 0, descriptionCount("More options"))
    }

    // The consent effect hands the ViewModel's one request to MediaWriteRequester. On API 29 ensureWritable grants
    // synchronously (no pre-flight prompt), so the grant reaches the ViewModel at once. Control: nothing is granted
    // before the request. Trivial stub: no effect → RED. Mutation: drop the consent LaunchedEffect → RED.
    @Config(sdk = [29])
    @Test
    fun consentRequest_reachesTheViewModelWhenGranted() {
        setActions()
        verify(exactly = 0) { viewModel.onConsentGranted() }

        composeRule.runOnIdle {
            state.value = SelectionArtworkUiState(isWorking = true, consentRequest = listOf(SONG_URI))
        }
        composeRule.waitForIdle()

        verify(exactly = 1) { viewModel.onConsentGranted() }
        verify(exactly = 0) { viewModel.onConsentDenied() }
    }

    // API 29: a recovery request launches the exception's own intent sender; a cancelled prompt reports a decline.
    // The exception wraps a real PendingIntent, so the activity's result registry launches it for real. Trivial stub:
    // no effect → RED. Mutation: route the recovery's onDenied to onRecoveryGranted → RED.
    @Config(sdk = [29])
    @Test
    fun recoveryRequest_cancelledReportsDenied() {
        setActions()

        answerRecoveryPrompt(Activity.RESULT_CANCELED)

        verify(exactly = 1) { viewModel.onConsentDenied() }
        verify(exactly = 0) { viewModel.onRecoveryGranted() }
    }

    // The twin: an allowed recovery prompt retries that song through onRecoveryGranted only, never the initial
    // consent's handlers. Trivial stub: no effect → RED. Mutation: wire the recovery's onGranted to onConsentGranted
    // → RED.
    @Config(sdk = [29])
    @Test
    fun recoveryRequest_allowedReportsRecoveryGranted() {
        setActions()

        answerRecoveryPrompt(Activity.RESULT_OK)

        verify(exactly = 1) { viewModel.onRecoveryGranted() }
        verify(exactly = 0) { viewModel.onConsentGranted() }
        verify(exactly = 0) { viewModel.onConsentDenied() }
    }

    /**
     * Puts an API 29 recovery request in the state, then answers the prompt it launches with [resultCode] through the
     * activity's result registry. The exception wraps a real PendingIntent, so the registry launches it for real.
     */
    private fun answerRecoveryPrompt(resultCode: Int) {
        val activity = composeRule.activity
        val pendingIntent = PendingIntent.getActivity(activity, 0, Intent(), PendingIntent.FLAG_IMMUTABLE)
        val icon = Icon.createWithBitmap(Bitmap.createBitmap(1, 1, Bitmap.Config.ARGB_8888))
        val action = RemoteAction(icon, "Allow", "Allow", pendingIntent)
        val exception = RecoverableSecurityException(SecurityException("not owner"), "Allow editing", action)

        composeRule.runOnIdle { state.value = SelectionArtworkUiState(isWorking = true, recoveryRequest = exception) }
        composeRule.waitForIdle()
        val request = shadowOf(activity).lastIntentSenderRequest
        assertNotNull("the recovery prompt is launched", request)
        composeRule.runOnUiThread {
            activity.activityResultRegistry.dispatchResult(request.requestCode, resultCode, null)
        }
        composeRule.waitForIdle()
    }

    // The message goes to the host's snackbar and the result to the host's selection, each once, then the ViewModel
    // forgets them. Trivial stub: nothing delivered → RED. Mutation: deliver the result without consumeResult → RED.
    @Test
    fun messageAndResult_reachTheHostOnce() {
        setActions()
        assertTrue("control: nothing delivered before the state carries it", messages.isEmpty() && results.isEmpty())
        val result = SelectionArtworkResult(setOf("2"))

        composeRule.runOnIdle {
            state.value = SelectionArtworkUiState(message = "Artwork set on 1 of 2 tracks", result = result)
        }
        composeRule.waitForIdle()

        assertEquals("the message reaches the host", listOf("Artwork set on 1 of 2 tracks"), messages)
        assertEquals("the result reaches the host", listOf(result), results)
        verify(exactly = 1) { viewModel.dismissMessage() }
        verify(exactly = 1) { viewModel.consumeResult() }
    }

    private fun openRemoveDialog() {
        composeRule.onNodeWithContentDescription("More options").performClick()
        composeRule.onNodeWithText("Remove artwork").performClick()
    }

    private fun textCount(text: String): Int = composeRule.onAllNodesWithText(text).fetchSemanticsNodes().size

    private fun descriptionCount(description: String): Int =
        composeRule.onAllNodesWithContentDescription(description).fetchSemanticsNodes().size

    /**
     * Draws the most recently added window (the dropdown menu's popup) into a software bitmap. A popup is its own
     * window, which drawRoot() cannot see; WindowManagerGlobal keeps every added root view in `mViews`, last added
     * last. Node bounds inside the popup index the result directly, as drawLatestDialog()'s do.
     */
    private fun drawLatestPopup(): Bitmap {
        composeRule.waitForIdle()
        lateinit var bitmap: Bitmap
        composeRule.runOnUiThread {
            val global = Class.forName("android.view.WindowManagerGlobal").getMethod("getInstance").invoke(null)
            val field = global.javaClass.getDeclaredField("mViews").apply { isAccessible = true }
            val view = (field.get(global) as List<*>).last() as View
            bitmap = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
            view.draw(Canvas(bitmap))
        }
        return bitmap
    }
}
