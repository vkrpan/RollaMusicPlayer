package com.rolla.musicplayer.feature.tageditor.io

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Boundary coverage for the pure `Int -> Boolean` SDK-branch predicates extracted out of
 * [MediaWriteRequester.ensureWritable] -- deliberately plain JUnit, no Robolectric: both
 * functions accept an explicit `sdkInt` override precisely so this boundary table needs no
 * Android framework/Robolectric dependency at all. The dispatch behavior these predicates drive
 * (which callback fires, which launcher is used) is covered separately, at the real emulated SDK
 * levels, in [MediaWriteRequesterTest].
 */
class MediaWriteRequesterPredicatesTest {

    // ── supportsPreflightWriteRequest (true from API 30/R) ──────────────────

    @Test
    fun supportsPreflightWriteRequest_api28_isFalse() {
        assertFalse(supportsPreflightWriteRequest(sdkInt = 28))
    }

    @Test
    fun supportsPreflightWriteRequest_api29_isFalse() {
        assertFalse(supportsPreflightWriteRequest(sdkInt = 29))
    }

    @Test
    fun supportsPreflightWriteRequest_api30_isTrue() {
        assertTrue(supportsPreflightWriteRequest(sdkInt = 30))
    }

    @Test
    fun supportsPreflightWriteRequest_api33_isTrue() {
        assertTrue(supportsPreflightWriteRequest(sdkInt = 33))
    }

    // ── requiresLegacyWritePermission (true through API 28/P) ───────────────

    @Test
    fun requiresLegacyWritePermission_api28_isTrue() {
        assertTrue(requiresLegacyWritePermission(sdkInt = 28))
    }

    @Test
    fun requiresLegacyWritePermission_api29_isFalse() {
        assertFalse(requiresLegacyWritePermission(sdkInt = 29))
    }

    @Test
    fun requiresLegacyWritePermission_api30_isFalse() {
        assertFalse(requiresLegacyWritePermission(sdkInt = 30))
    }
}
