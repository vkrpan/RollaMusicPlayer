package com.rolla.musicplayer.core.media

import android.content.Context
import android.content.Intent
import androidx.media3.session.MediaButtonReceiver
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class MediaButtonManifestTest {

    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val mediaButton = Intent(Intent.ACTION_MEDIA_BUTTON).setPackage(context.packageName)

    // Spec §7.6. Mutation: delete the <receiver> → RED.
    @Test
    fun mediaButtonsReachMedia3sReceiver() {
        val receivers = context.packageManager.queryBroadcastReceivers(mediaButton, 0)
        assertTrue(receivers.any { it.activityInfo.name == MediaButtonReceiver::class.java.name })
    }

    // MediaButtonReceiver forwards to the service that handles MEDIA_BUTTON. Mutation: drop that action from the
    // service's intent filter → RED.
    @Test
    fun theServiceHandlesMediaButtons() {
        val services = context.packageManager.queryIntentServices(mediaButton, 0)
        assertTrue(services.any { it.serviceInfo.name == PlaybackService::class.java.name })
    }

    // Panel-resume spec §5.2. The System UI binds the legacy MediaBrowserService action, and Media3 looks the service
    // up by its MediaLibraryService action. Control: the existing MEDIA_BUTTON test proves the query sees this
    // service. Mutations: drop either action from the service's intent filter → RED.
    @Test
    fun theServiceIsALibraryAndALegacyBrowser() {
        val actions = listOf("androidx.media3.session.MediaLibraryService", "android.media.browse.MediaBrowserService")
        for (action in actions) {
            val services = context.packageManager.queryIntentServices(Intent(action).setPackage(context.packageName), 0)
            assertTrue(action, services.any { it.serviceInfo.name == PlaybackService::class.java.name })
        }
    }
}
