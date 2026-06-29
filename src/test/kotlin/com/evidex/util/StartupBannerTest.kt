package com.evidex.util

import kotlin.test.Test
import kotlin.test.assertEquals

class StartupBannerTest {

    @Test
    fun `uses server host when bind address is wildcard`() {
        assertEquals("http://192.168.1.50:9090", StartupBanner.buildDashboardUrl("0.0.0.0", 9090, "192.168.1.50"))
    }

    @Test
    fun `uses configured host when bind address is specific`() {
        assertEquals("http://192.168.1.50:9090", StartupBanner.buildDashboardUrl("192.168.1.50", 9090, "192.168.1.50"))
    }
}
