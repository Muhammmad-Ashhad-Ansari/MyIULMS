package com.example.myiulms

import com.example.myiulms.data.CACHE_SCHEMA_VERSION
import com.example.myiulms.data.CacheEnvelope
import com.example.myiulms.data.decodeEnvelope
import com.example.myiulms.data.encodeEnvelope
import com.example.myiulms.data.ownerHashOf
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Test

class CacheEnvelopeTest {

    private val schedule = WeeklySchedule(
        title = "Semester Schedule",
        entries = listOf(WeeklyScheduleEntry("MON", "1:00 PM/2:20 PM", "AI Agents", "Dr. X", "E-806", "1", "CS-1"))
    )

    @Test
    fun `round trips a schedule envelope`() {
        val envelope = CacheEnvelope(CACHE_SCHEMA_VERSION, ownerHashOf("68669"), 42L, schedule)

        val bytes = encodeEnvelope(WeeklySchedule.serializer(), envelope)

        assertEquals(envelope, decodeEnvelope(WeeklySchedule.serializer(), bytes))
    }

    @Test
    fun `another schema version reads as a miss`() {
        val old = CacheEnvelope(CACHE_SCHEMA_VERSION + 1, ownerHashOf("68669"), 42L, schedule)

        assertNull(decodeEnvelope(WeeklySchedule.serializer(), encodeEnvelope(WeeklySchedule.serializer(), old)))
    }

    @Test
    fun `garbage reads as a miss`() {
        assertNull(decodeEnvelope(WeeklySchedule.serializer(), "not json".toByteArray()))
    }

    @Test
    fun `owner hash ignores case and spaces but separates accounts`() {
        assertEquals(ownerHashOf("ab123"), ownerHashOf(" AB123 "))
        assertNotEquals(ownerHashOf("68669"), ownerHashOf("68670"))
        assertEquals(64, ownerHashOf("68669").length)
    }
}
