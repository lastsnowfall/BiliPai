package com.android.purebilibili.core.plugin

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class BuiltinPluginDefaultPolicyTest {

    @Test
    fun castPluginsAreEnabledByDefault() {
        assertTrue(resolvePluginDefaultEnabled("dlna_cast"))
        assertTrue(resolvePluginDefaultEnabled("google_cast"))
    }

    @Test
    fun sponsorBlockIsEnabledByDefault() {
        assertTrue(resolvePluginDefaultEnabled("sponsor_block"))
    }

    @Test
    fun optionalPluginsStayDisabledByDefault() {
        assertFalse(resolvePluginDefaultEnabled("ad_filter"))
        assertFalse(resolvePluginDefaultEnabled("unknown_plugin"))
    }
}
