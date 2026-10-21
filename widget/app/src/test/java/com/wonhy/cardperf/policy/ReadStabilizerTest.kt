package com.wonhy.cardperf.policy

import com.wonhy.cardperf.model.CardPerf
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ReadStabilizerTest {
    private val a = CardPerf("A", 1, "30만원", 300_000, false, 299_999)
    private val b = CardPerf("B", 2, "30만원", 300_000, false, 299_998)

    @Test fun acceptsAfterTwoIdenticalReads() {
        val s = ReadStabilizer(required = 2)
        assertFalse(s.offer(listOf(a)))
        assertFalse(s.offer(listOf(a, b)))
        assertTrue(s.offer(listOf(a, b)))
    }

    @Test fun defaultNeedsFourIdenticalReads() {
        // 4 reads at 500ms polling = the list held steady for at least 1.5s
        val s = ReadStabilizer()
        assertFalse(s.offer(listOf(a)))
        assertFalse(s.offer(listOf(a)))
        assertFalse(s.offer(listOf(a)))
        assertTrue(s.offer(listOf(a)))
    }

    @Test fun neverAcceptsEmpty() {
        val s = ReadStabilizer()
        assertFalse(s.offer(emptyList()))
        assertFalse(s.offer(emptyList()))
    }

    @Test fun changingReadsResetCount() {
        val s = ReadStabilizer()
        assertFalse(s.offer(listOf(a)))
        assertFalse(s.offer(listOf(a, b)))
        assertFalse(s.offer(listOf(a)))
    }
}
