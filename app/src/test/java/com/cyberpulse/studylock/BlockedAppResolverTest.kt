package com.cyberpulse.studylock

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class BlockedAppResolverTest {
    @Test
    fun resolvesExactHtmlDefaultsToAndroidPackages() {
        val result = BlockedAppResolver.resolve(
            listOf(
                "instagram.com",
                "tiktok.com",
                "youtube.com",
                "twitter.com / x.com"
            )
        )

        assertTrue("com.instagram.android" in result)
        assertTrue("com.zhiliaoapp.musically" in result)
        assertTrue("com.google.android.youtube" in result)
        assertTrue("com.twitter.android" in result)
    }

    @Test
    fun acceptsAnExplicitAndroidPackageName() {
        val result = BlockedAppResolver.resolve(listOf("com.example.distraction"))
        assertEquals(setOf("com.example.distraction"), result)
    }
}
