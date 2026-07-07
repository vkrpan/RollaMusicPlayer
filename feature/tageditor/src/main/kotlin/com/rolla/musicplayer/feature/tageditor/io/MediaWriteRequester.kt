package com.rolla.musicplayer.feature.tageditor.io

import android.Manifest
import android.app.Activity
import android.app.RecoverableSecurityException
import android.content.ContentResolver
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.ChecksSdkIntAtLeast
import androidx.annotation.RequiresApi
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat

/**
 * Gates a tag-edit file write behind Android's scoped-storage write-consent flow, per SDK level.
 *
 * This class never writes a byte itself -- it only decides whether/when to invoke an `onGranted`
 * callback, which is the caller's cue that it is now safe to perform the actual write (e.g. via
 * `SongFileResolver.persistEditedCopy`). Structuring it this way is what makes "never write before
 * consent" enforceable: there is simply no write code in this file to call too early.
 *
 * Intended call sequence for a single edit or a batch of edits:
 * 1. `ensureWritable(uris, onGranted = { ... }, onDenied = { ... })` -- on API 30+ this shows ONE
 *    system consent sheet covering every uri in the batch; on API 29 there is no pre-flight
 *    consent API, so [onGranted] fires immediately and the real gate is the write attempt itself;
 *    on API <=28 the gate is the legacy WRITE_EXTERNAL_STORAGE permission, requested here if not
 *    already held (see step 4).
 * 2. From inside `onGranted`, the caller performs the write(s). Never write outside this callback.
 * 3. On API 29 specifically, that write can still throw [RecoverableSecurityException] (the
 *    pre-flight step above could not have prevented this, since no such pre-flight exists on Q).
 *    Catch it at the write site and call `recoverAndRetry(exception, onGranted = { retry the same
 *    write }, onDenied = { ... })`.
 * 4. On API <=28 the direct write requires the legacy (runtime, maxSdk=28)
 *    WRITE_EXTERNAL_STORAGE permission, declared in this module's own manifest. [ensureWritable]
 *    IS the consent gate for it: if the permission is not yet held, it launches the system
 *    permission dialog and only invokes [onGranted] on a grant -- so "never write before consent"
 *    holds on every SDK level, just with three different system prompts (permission dialog <=28,
 *    RecoverableSecurityException recovery on 29, createWriteRequest sheet on 30+).
 *
 * Instances are created via [rememberMediaWriteRequester] and are not meant to be constructed
 * directly outside of tests -- the constructor takes already-built [ActivityResultLauncher]s so
 * this class stays a plain, launcher-injected object rather than an Activity/Fragment-coupled one.
 */
class MediaWriteRequester internal constructor(
    private val resolver: ContentResolver,
    private val launcher: ActivityResultLauncher<IntentSenderRequest>,
    private val permissionLauncher: ActivityResultLauncher<String>,
    private val hasLegacyWritePermission: () -> Boolean,
) {

    private var pendingOnGranted: (() -> Unit)? = null
    private var pendingOnDenied: (() -> Unit)? = null

    /**
     * Requests write consent for every uri in [uris] as a single batch -- one system prompt on
     * API 30+ covers the whole list, never a per-file prompt -- then invokes [onGranted] once
     * consent is confirmed. The caller must perform the actual file write(s) from inside
     * [onGranted], never before calling this function and never outside that callback.
     *
     * - **API 30+ (R)**: launches `MediaStore.createWriteRequest(resolver, uris)`; the system
     *   result [Activity.RESULT_OK] invokes [onGranted], anything else (including
     *   `RESULT_CANCELED`, i.e. the user declined) invokes [onDenied] and nothing is written.
     * - **API 29 (Q)**: no pre-flight write-request API exists, so [onGranted] is invoked
     *   immediately and synchronously -- the subsequent write attempt itself is the real gate
     *   and can still throw [RecoverableSecurityException]; see [recoverAndRetry].
     * - **API <=28**: the direct write requires the legacy WRITE_EXTERNAL_STORAGE runtime
     *   permission. Already held -> [onGranted] immediately; otherwise the system permission
     *   dialog is launched and its result routes to [onGranted]/[onDenied] -- a write is never
     *   attempted without the permission (it would only throw a non-recoverable
     *   [SecurityException] at the write site).
     */
    fun ensureWritable(uris: List<Uri>, onGranted: () -> Unit, onDenied: () -> Unit) {
        when {
            supportsPreflightWriteRequest() -> requestPreflightConsent(uris, onGranted, onDenied)
            requiresLegacyWritePermission() && !hasLegacyWritePermission() ->
                requestLegacyWritePermission(onGranted, onDenied)
            else -> onGranted()
        }
    }

    private fun requestLegacyWritePermission(onGranted: () -> Unit, onDenied: () -> Unit) {
        pendingOnGranted = onGranted
        pendingOnDenied = onDenied
        permissionLauncher.launch(Manifest.permission.WRITE_EXTERNAL_STORAGE)
    }

    @RequiresApi(Build.VERSION_CODES.R)
    private fun requestPreflightConsent(uris: List<Uri>, onGranted: () -> Unit, onDenied: () -> Unit) {
        pendingOnGranted = onGranted
        pendingOnDenied = onDenied
        val writeRequest = MediaStore.createWriteRequest(resolver, uris)
        launcher.launch(IntentSenderRequest.Builder(writeRequest.intentSender).build())
    }

    /**
     * Recovers from a [RecoverableSecurityException] thrown by a write attempt on API 29 (Q):
     * launches the exception's own `userAction.actionIntent.intentSender`, and on
     * [Activity.RESULT_OK] invokes [onGranted] so the caller can retry the *exact same write*
     * that just failed; anything else invokes [onDenied] and the caller must not retry.
     *
     * Only meaningful on API 29: [RecoverableSecurityException] is itself an API 29+ type, and on
     * API 30+ [ensureWritable]'s pre-flight request means a write should never throw it in the
     * first place (call sites must still guard with `Build.VERSION.SDK_INT == Build.VERSION_CODES.Q`
     * before calling this -- enforced by the [RequiresApi] annotation via lint's NewApi check).
     */
    @RequiresApi(Build.VERSION_CODES.Q)
    fun recoverAndRetry(exception: RecoverableSecurityException, onGranted: () -> Unit, onDenied: () -> Unit) {
        pendingOnGranted = onGranted
        pendingOnDenied = onDenied
        val intentSender = exception.userAction.actionIntent.intentSender
        launcher.launch(IntentSenderRequest.Builder(intentSender).build())
    }

    /**
     * Callback target for the [ActivityResultLauncher] built in [rememberMediaWriteRequester].
     * Consumes and clears the pending callbacks set by [requestPreflightConsent] or
     * [recoverAndRetry] so a stale callback can never fire twice, then invokes exactly one of
     * them: [Activity.RESULT_OK] -> the granted callback, anything else (denial, back-press,
     * system dismissal) -> the denied callback. [onDenied] is guaranteed to fire on decline so
     * the screen can always surface a clear message; nothing is written in either branch here.
     */
    internal fun onActivityResult(resultCode: Int) {
        val onGranted = pendingOnGranted
        val onDenied = pendingOnDenied
        pendingOnGranted = null
        pendingOnDenied = null
        if (resultCode == Activity.RESULT_OK) {
            onGranted?.invoke()
        } else {
            onDenied?.invoke()
        }
    }

    /**
     * Callback target for the WRITE_EXTERNAL_STORAGE permission launcher used by the API <=28
     * branch of [ensureWritable]. Same consume-and-clear contract as [onActivityResult]: exactly
     * one of the pending callbacks fires, and a stale callback can never fire twice.
     */
    internal fun onPermissionResult(granted: Boolean) {
        val onGranted = pendingOnGranted
        val onDenied = pendingOnDenied
        pendingOnGranted = null
        pendingOnDenied = null
        if (granted) {
            onGranted?.invoke()
        } else {
            onDenied?.invoke()
        }
    }
}

/**
 * True on API levels (<=28) where writing another app's media file requires the legacy
 * WRITE_EXTERNAL_STORAGE runtime permission rather than a scoped-storage consent flow. Extracted
 * as a pure `Int -> Boolean` for the same JVM-testability reason as [supportsPreflightWriteRequest];
 * API 29 is deliberately outside both: it needs no permission up front, its gate is the
 * [RecoverableSecurityException] thrown by the write itself.
 */
internal fun requiresLegacyWritePermission(sdkInt: Int = Build.VERSION.SDK_INT): Boolean =
    sdkInt <= Build.VERSION_CODES.P

/**
 * Pure SDK-branch decision extracted out of [MediaWriteRequester.ensureWritable]: true once the
 * platform exposes the pre-flight `MediaStore.createWriteRequest` API (30+), false when
 * [MediaWriteRequester.ensureWritable] must instead grant immediately and rely on a direct write
 * (API <=29). Defined as a plain `Int -> Boolean` function (the [sdkInt] parameter defaults to the
 * real device value but can be passed explicitly), so the branch is unit-testable without any
 * Android framework/Robolectric dependency, should that ever be worth adding for this module.
 *
 * Annotated with [ChecksSdkIntAtLeast] so lint's `NewApi` check treats a `true` result here as
 * proof the call site is already guarded for API 30+, the same way it treats an inline
 * `Build.VERSION.SDK_INT >= Build.VERSION_CODES.R` check.
 */
@ChecksSdkIntAtLeast(api = Build.VERSION_CODES.R)
internal fun supportsPreflightWriteRequest(sdkInt: Int = Build.VERSION.SDK_INT): Boolean =
    sdkInt >= Build.VERSION_CODES.R

/**
 * Creates and remembers a [MediaWriteRequester] wired to a Compose
 * `rememberLauncherForActivityResult(ActivityResultContracts.StartIntentSenderForResult())`
 * launcher, so a screen/ViewModel can gate a tag-edit write behind scoped-storage consent without
 * touching `ComponentActivity`/`ContentResolver` plumbing itself. This is the only supported entry
 * point for obtaining a [MediaWriteRequester] outside of tests.
 *
 * Known limitation: the pending `onGranted`/`onDenied` lambdas passed to [MediaWriteRequester.ensureWritable]
 * or [MediaWriteRequester.recoverAndRetry] live only in the remembered instance's fields, not in a
 * `Bundle` -- if the hosting process dies while the system consent sheet is on screen (e.g. the OS
 * reclaims memory), those callbacks are lost on process recreation and the edit must be retried
 * from scratch rather than silently resuming. This is the standard, accepted tradeoff for
 * `rememberLauncherForActivityResult`-based flows project-wide (see `MediaPermissionGate` in
 * `:core:permissions`), not something specific to this component.
 */
@Composable
fun rememberMediaWriteRequester(): MediaWriteRequester {
    val context = LocalContext.current
    lateinit var requester: MediaWriteRequester
    val launcher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartIntentSenderForResult(),
    ) { result -> requester.onActivityResult(result.resultCode) }
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted -> requester.onPermissionResult(granted) }
    return remember(context, launcher, permissionLauncher) {
        MediaWriteRequester(
            resolver = context.contentResolver,
            launcher = launcher,
            permissionLauncher = permissionLauncher,
            hasLegacyWritePermission = {
                ContextCompat.checkSelfPermission(
                    context,
                    Manifest.permission.WRITE_EXTERNAL_STORAGE,
                ) == PackageManager.PERMISSION_GRANTED
            },
        )
    }.also { requester = it }
}
