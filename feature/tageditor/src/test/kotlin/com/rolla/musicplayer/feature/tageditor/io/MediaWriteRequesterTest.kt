package com.rolla.musicplayer.feature.tageditor.io

import android.Manifest
import android.app.Activity
import android.app.PendingIntent
import android.app.RecoverableSecurityException
import android.app.RemoteAction
import android.content.ContentResolver
import android.content.IntentSender
import android.net.Uri
import android.provider.MediaStore
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.IntentSenderRequest
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.slot
import io.mockk.unmockkAll
import io.mockk.verify
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Direct, per-SDK-level coverage for [MediaWriteRequester.ensureWritable] dispatch and
 * [MediaWriteRequester.recoverAndRetry] -- run under [RobolectricTestRunner] so
 * `Build.VERSION.SDK_INT` (which [supportsPreflightWriteRequest]/[requiresLegacyWritePermission]
 * default to) is the real, per-[Config] emulated value rather than the `0` a plain JVM unit test
 * would see. This closes the Phase 7 note that [MediaWriteRequester]'s SDK branching needed
 * Robolectric/mockkStatic for direct tests; see [MediaWriteRequesterPredicatesTest] for the two
 * predicates own boundary table, which needs neither.
 *
 * [android.provider.MediaStore.createWriteRequest] is static-mocked via mockkStatic rather than
 * exercised for real: Robolectric android-all jar has no meaningful shadow for this API-30+
 * method, and the only thing under test here is which branch [MediaWriteRequester] dispatches
 * to and how it wires the result, not whether the platform API itself works.
 *
 * [launcher]/[permissionLauncher] are mocked with MockK ([ActivityResultLauncher] is abstract),
 * matching this module established pattern of mocking concrete/abstract collaborators directly
 * rather than introducing fakes for them (see TagSaveFinalizerTest).
 */
@RunWith(RobolectricTestRunner::class)
class MediaWriteRequesterTest {

    private val resolver: ContentResolver = mockk()
    private val launcher: ActivityResultLauncher<IntentSenderRequest> = mockk(relaxed = true)
    private val permissionLauncher: ActivityResultLauncher<String> = mockk(relaxed = true)

    @After
    fun tearDown() {
        unmockkAll()
    }

    /** Stubs MediaStore.createWriteRequest to return a mocked PendingIntent wrapping sender. */
    private fun stubCreateWriteRequest(uris: List<Uri>, sender: IntentSender) {
        mockkStatic(MediaStore::class)
        val pendingIntent: PendingIntent = mockk()
        every { pendingIntent.intentSender } returns sender
        every { MediaStore.createWriteRequest(resolver, uris) } returns pendingIntent
    }

    // -- API 30+ (R): createWriteRequest pre-flight --------------------------

    @Config(sdk = [30])
    @Test
    fun ensureWritable_api30_launchesCreateWriteRequestIntentSender_withNeitherCallbackFiredYet() {
        val requester = MediaWriteRequester(resolver, launcher, permissionLauncher) { true }
        val uris = listOf(mockk<Uri>())
        val sender: IntentSender = mockk()
        stubCreateWriteRequest(uris, sender)
        var grantedCount = 0
        var deniedCount = 0

        requester.ensureWritable(uris, onGranted = { grantedCount++ }, onDenied = { deniedCount++ })

        val requestSlot = slot<IntentSenderRequest>()
        verify(exactly = 1) { launcher.launch(capture(requestSlot)) }
        assertEquals(sender, requestSlot.captured.intentSender)
        assertEquals(0, grantedCount)
        assertEquals(0, deniedCount)
    }

    @Config(sdk = [30])
    @Test
    fun onActivityResult_api30_resultOk_firesOnGrantedOnlyOnce() {
        val requester = MediaWriteRequester(resolver, launcher, permissionLauncher) { true }
        val uris = listOf(mockk<Uri>())
        stubCreateWriteRequest(uris, mockk())
        var grantedCount = 0
        var deniedCount = 0
        requester.ensureWritable(uris, onGranted = { grantedCount++ }, onDenied = { deniedCount++ })

        requester.onActivityResult(Activity.RESULT_OK)

        assertEquals(1, grantedCount)
        assertEquals(0, deniedCount)
    }

    @Config(sdk = [30])
    @Test
    fun onActivityResult_api30_resultCanceled_firesOnDeniedOnlyOnce() {
        val requester = MediaWriteRequester(resolver, launcher, permissionLauncher) { true }
        val uris = listOf(mockk<Uri>())
        stubCreateWriteRequest(uris, mockk())
        var grantedCount = 0
        var deniedCount = 0
        requester.ensureWritable(uris, onGranted = { grantedCount++ }, onDenied = { deniedCount++ })

        requester.onActivityResult(Activity.RESULT_CANCELED)

        assertEquals(0, grantedCount)
        assertEquals(1, deniedCount)
    }

    @Config(sdk = [30])
    @Test
    fun onActivityResult_api30_secondCallAfterFirstIsConsumed_firesNothing() {
        val requester = MediaWriteRequester(resolver, launcher, permissionLauncher) { true }
        val uris = listOf(mockk<Uri>())
        stubCreateWriteRequest(uris, mockk())
        var grantedCount = 0
        var deniedCount = 0
        requester.ensureWritable(uris, onGranted = { grantedCount++ }, onDenied = { deniedCount++ })
        requester.onActivityResult(Activity.RESULT_OK)

        requester.onActivityResult(Activity.RESULT_OK)
        requester.onActivityResult(Activity.RESULT_CANCELED)

        assertEquals(1, grantedCount)
        assertEquals(0, deniedCount)
    }

    @Config(sdk = [30])
    @Test
    fun ensureWritable_api30_neverConsultsHasLegacyWritePermission() {
        val hasLegacyWritePermission: () -> Boolean = mockk()
        val requester = MediaWriteRequester(resolver, launcher, permissionLauncher, hasLegacyWritePermission)
        val uris = listOf(mockk<Uri>())
        stubCreateWriteRequest(uris, mockk())

        requester.ensureWritable(uris, onGranted = {}, onDenied = {})

        verify(exactly = 0) { hasLegacyWritePermission() }
    }

    // -- API 29 (Q): no pre-flight API, RecoverableSecurityException recovery --

    @Config(sdk = [29])
    @Test
    fun ensureWritable_api29_firesOnGrantedImmediately_withoutLaunchingEitherLauncher() {
        val requester = MediaWriteRequester(resolver, launcher, permissionLauncher) { true }
        var grantedCount = 0
        var deniedCount = 0

        requester.ensureWritable(listOf(mockk()), onGranted = { grantedCount++ }, onDenied = { deniedCount++ })

        assertEquals(1, grantedCount)
        assertEquals(0, deniedCount)
        verify(exactly = 0) { launcher.launch(any()) }
        verify(exactly = 0) { permissionLauncher.launch(any()) }
    }

    /** Stubs exception.userAction.actionIntent.intentSender down the full mocked chain. */
    private fun stubRecoverableSecurityException(sender: IntentSender): RecoverableSecurityException {
        val pendingIntent: PendingIntent = mockk()
        every { pendingIntent.intentSender } returns sender
        val remoteAction: RemoteAction = mockk()
        every { remoteAction.actionIntent } returns pendingIntent
        val exception: RecoverableSecurityException = mockk()
        every { exception.userAction } returns remoteAction
        return exception
    }

    @Config(sdk = [29])
    @Test
    fun recoverAndRetry_api29_launchesExceptionIntentSender_resultOk_firesOnGranted() {
        val requester = MediaWriteRequester(resolver, launcher, permissionLauncher) { true }
        val sender: IntentSender = mockk()
        val exception = stubRecoverableSecurityException(sender)
        var grantedCount = 0
        var deniedCount = 0

        requester.recoverAndRetry(exception, onGranted = { grantedCount++ }, onDenied = { deniedCount++ })

        val requestSlot = slot<IntentSenderRequest>()
        verify(exactly = 1) { launcher.launch(capture(requestSlot)) }
        assertEquals(sender, requestSlot.captured.intentSender)
        assertEquals(0, grantedCount)

        requester.onActivityResult(Activity.RESULT_OK)

        assertEquals(1, grantedCount)
        assertEquals(0, deniedCount)
    }

    @Config(sdk = [29])
    @Test
    fun recoverAndRetry_api29_launchesExceptionIntentSender_resultCanceled_firesOnDenied() {
        val requester = MediaWriteRequester(resolver, launcher, permissionLauncher) { true }
        val exception = stubRecoverableSecurityException(mockk())
        var grantedCount = 0
        var deniedCount = 0
        requester.recoverAndRetry(exception, onGranted = { grantedCount++ }, onDenied = { deniedCount++ })

        requester.onActivityResult(Activity.RESULT_CANCELED)

        assertEquals(0, grantedCount)
        assertEquals(1, deniedCount)
    }

    // -- API <=28: legacy WRITE_EXTERNAL_STORAGE runtime permission ----------

    @Config(sdk = [28])
    @Test
    fun ensureWritable_api28_permissionAlreadyHeld_firesOnGrantedImmediately_withoutLaunchingPermissionRequest() {
        val requester = MediaWriteRequester(resolver, launcher, permissionLauncher) { true }
        var grantedCount = 0
        var deniedCount = 0

        requester.ensureWritable(listOf(mockk()), onGranted = { grantedCount++ }, onDenied = { deniedCount++ })

        assertEquals(1, grantedCount)
        assertEquals(0, deniedCount)
        verify(exactly = 0) { permissionLauncher.launch(any()) }
        verify(exactly = 0) { launcher.launch(any()) }
    }

    @Config(sdk = [28])
    @Test
    fun ensureWritable_api28_permissionNotHeld_launchesPermissionRequest_withNeitherCallbackFiredYet() {
        val requester = MediaWriteRequester(resolver, launcher, permissionLauncher) { false }
        var grantedCount = 0
        var deniedCount = 0

        requester.ensureWritable(listOf(mockk()), onGranted = { grantedCount++ }, onDenied = { deniedCount++ })

        verify(exactly = 1) { permissionLauncher.launch(Manifest.permission.WRITE_EXTERNAL_STORAGE) }
        assertEquals(0, grantedCount)
        assertEquals(0, deniedCount)
    }

    @Config(sdk = [28])
    @Test
    fun onPermissionResult_api28_granted_firesOnGrantedOnlyOnce() {
        val requester = MediaWriteRequester(resolver, launcher, permissionLauncher) { false }
        var grantedCount = 0
        var deniedCount = 0
        requester.ensureWritable(listOf(mockk()), onGranted = { grantedCount++ }, onDenied = { deniedCount++ })

        requester.onPermissionResult(granted = true)

        assertEquals(1, grantedCount)
        assertEquals(0, deniedCount)
    }

    @Config(sdk = [28])
    @Test
    fun onPermissionResult_api28_denied_firesOnDeniedOnlyOnce() {
        val requester = MediaWriteRequester(resolver, launcher, permissionLauncher) { false }
        var grantedCount = 0
        var deniedCount = 0
        requester.ensureWritable(listOf(mockk()), onGranted = { grantedCount++ }, onDenied = { deniedCount++ })

        requester.onPermissionResult(granted = false)

        assertEquals(0, grantedCount)
        assertEquals(1, deniedCount)
    }

    @Config(sdk = [28])
    @Test
    fun onPermissionResult_api28_secondCallAfterFirstIsConsumed_firesNothing() {
        val requester = MediaWriteRequester(resolver, launcher, permissionLauncher) { false }
        var grantedCount = 0
        var deniedCount = 0
        requester.ensureWritable(listOf(mockk()), onGranted = { grantedCount++ }, onDenied = { deniedCount++ })
        requester.onPermissionResult(granted = true)

        requester.onPermissionResult(granted = true)
        requester.onPermissionResult(granted = false)

        assertEquals(1, grantedCount)
        assertEquals(0, deniedCount)
    }
}
