package com.wonhy.cardperf.parse

import com.wonhy.cardperf.model.PerfSnapshot
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PerfParserTest {
    // 2026-10-21 실기에서 읽은 노드 텍스트 그대로
    private val texts = listOf(
        "실적 관리", "실적 기간 : 10.1~10.31", "설정",
        "샐러드 신용카드 412,300원 30만원 실적 달성 완료 축하",
        "그린 체크카드 258,500원 30만원 실적 달성 41,500원 남음",
        "여행 체크카드 0원 30만원 실적 달성 300,000원 남음",
        "미연결은행", "실적 달성 ??원 남음", "카드 연결",
    )

    @Test fun parsesRealScreen() {
        val s = PerfParser.parse(texts, now = 1L)
        assertEquals("10.1~10.31", s.period)
        assertEquals(listOf("샐러드 신용카드", "그린 체크카드", "여행 체크카드"), s.cards.map { it.name })
        assertEquals(1L, s.readAt)
    }

    @Test fun doneCardOverGoalIsFull() {
        val c = PerfParser.parse(texts, 1L).cards[0]
        assertTrue(c.done)
        assertEquals(412_300L, c.used)
        assertEquals("30만원", c.goalText)
        assertEquals(300_000L, c.goal)
        assertEquals(1f, c.progress, 0f)
    }

    @Test fun remainingCards() {
        val cards = PerfParser.parse(texts, 1L).cards
        assertFalse(cards[1].done)
        assertEquals(41_500L, cards[1].remaining)
        assertEquals(258_500f / 300_000f, cards[1].progress, 0.0001f)
        assertEquals(0f, cards[2].progress, 0f)
        assertEquals(300_000L, cards[2].remaining)
    }

    @Test fun detectsPerfScreen() {
        assertTrue(PerfParser.isPerfScreen(texts))
        assertFalse(PerfParser.isPerfScreen(listOf("카드값", "카드 혜택")))
    }

    @Test fun dedupesRepeatedCard() {
        assertEquals(1, PerfParser.parse(listOf(texts[3], texts[3]), 1L).cards.size)
    }

    @Test fun unparseableGoalFallsBackToRemaining() {
        val c = PerfParser.parse(listOf("A카드 100원 목표미정원 실적 달성 300원 남음"), 1L).cards.single()
        assertNull(c.goal)
        assertEquals(0.25f, c.progress, 0.0001f)
    }

    @Test fun jsonRoundTrip() {
        val s = PerfParser.parse(texts, 123L)
        assertEquals(s, PerfSnapshot.fromJson(s.toJson()))
        assertNull(PerfSnapshot.fromJson("not json"))
    }
}
