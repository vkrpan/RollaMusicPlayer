package com.rolla.musicplayer.core.permissions

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.lifecycle.Lifecycle
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

/** rememberMediaPermissionState (Phase 6b ruling 8) reads the grant at start and again on every resume. */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [34])
class MediaPermissionStateTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    // Returning from Settings with access granted must lift the gate without a restart. Trivial stub: the status fixed
    // at its first value → isGranted stays false after the resume. Mutation: remove the resume observer → RED on the
    // last assertion.
    @Test
    fun grantIsReadAtStartAndOnResume() {
        val application = shadowOf(composeRule.activity.application)
        application.denyPermissions(MediaPermission.name)
        lateinit var state: MediaPermissionState
        composeRule.setContent { state = rememberMediaPermissionState() }
        composeRule.runOnIdle {
            assertFalse("with the permission denied, access is not granted at start", state.isGranted)
            assertEquals(
                "with the permission denied, the first status asks for it",
                MediaPermissionStatus.NEEDS_REQUEST,
                state.status,
            )
        }

        application.grantPermissions(MediaPermission.name)
        composeRule.activityRule.scenario.moveToState(Lifecycle.State.STARTED) // ON_PAUSE (off to Settings)
        composeRule.activityRule.scenario.moveToState(Lifecycle.State.RESUMED) // ON_RESUME (back from Settings)
        composeRule.runOnIdle {
            assertTrue("a grant made while away is read on resume", state.isGranted)
        }
    }
}
