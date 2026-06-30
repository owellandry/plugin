package com.evidex.detection.lag

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class LagCompensatorTest {

    @Test
    fun `reachBuffer es 0 si esta deshabilitado`() {
        assertEquals(0.0, LagCompensator.reachBuffer(enabled = false, pingMs = 200, reachPerMs = 0.003))
    }

    @Test
    fun `reachBuffer escala con el ping`() {
        // 200 ms * 0.003 bloques/ms-equivalente / 1000 = 0.0006
        assertEquals(0.2 * 0.003, LagCompensator.reachBuffer(enabled = true, pingMs = 200, reachPerMs = 0.003), 1e-9)
        // ping negativo se trata como 0
        assertEquals(0.0, LagCompensator.reachBuffer(enabled = true, pingMs = -50, reachPerMs = 0.003))
    }

    @Test
    fun `shouldSoften false si esta deshabilitado`() {
        assertFalse(LagCompensator.shouldSoften(enabled = false, tps = 1.0, minTps = 18.0, pingMs = 9999, maxPingMs = 400))
    }

    @Test
    fun `shouldSoften true con TPS bajos`() {
        assertTrue(LagCompensator.shouldSoften(enabled = true, tps = 12.0, minTps = 18.0, pingMs = 50, maxPingMs = 400))
    }

    @Test
    fun `shouldSoften true con ping alto`() {
        assertTrue(LagCompensator.shouldSoften(enabled = true, tps = 20.0, minTps = 18.0, pingMs = 500, maxPingMs = 400))
    }

    @Test
    fun `shouldSoften false en condiciones normales`() {
        assertFalse(LagCompensator.shouldSoften(enabled = true, tps = 20.0, minTps = 18.0, pingMs = 60, maxPingMs = 400))
    }
}
