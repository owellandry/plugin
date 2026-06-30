package com.evidex.detection

import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ExemptionServiceTest {

    private val uuid = UUID.randomUUID()

    @Test
    fun `exento dentro de la ventana, no exento despues`() {
        val svc = ExemptionService()
        svc.grant(uuid, ExemptReason.TELEPORT, durationMs = 1000, now = 0)

        assertTrue(svc.isExempt(uuid, now = 500))
        assertEquals(ExemptReason.TELEPORT, svc.activeReason(uuid, now = 500))

        assertFalse(svc.isExempt(uuid, now = 1000))
        assertNull(svc.activeReason(uuid, now = 1500))
    }

    @Test
    fun `grant conserva el vencimiento mas lejano`() {
        val svc = ExemptionService()
        svc.grant(uuid, ExemptReason.RESPAWN, durationMs = 2000, now = 0)   // hasta 2000
        svc.grant(uuid, ExemptReason.TELEPORT, durationMs = 100, now = 0)   // hasta 100 (menor)

        // sigue exento por el grant más largo, y conserva su razón
        assertTrue(svc.isExempt(uuid, now = 1500))
        assertEquals(ExemptReason.RESPAWN, svc.activeReason(uuid, now = 1500))
    }

    @Test
    fun `duracion no positiva no exime`() {
        val svc = ExemptionService()
        svc.grant(uuid, ExemptReason.JOIN, durationMs = 0, now = 0)
        assertFalse(svc.isExempt(uuid, now = 0))
    }

    @Test
    fun `clear quita la exencion`() {
        val svc = ExemptionService()
        svc.grant(uuid, ExemptReason.JOIN, durationMs = 5000, now = 0)
        svc.clear(uuid)
        assertFalse(svc.isExempt(uuid, now = 100))
    }
}
