package com.wonhy.cardperf.policy

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.ZoneId
import java.time.ZonedDateTime

class WidgetStatusTest {
    private val h = 3_600_000L
    private val kst = ZoneId.of("Asia/Seoul")
    private fun at(y: Int, m: Int, d: Int) =
        ZonedDateTime.of(y, m, d, 9, 0, 0, 0, kst).toInstant().toEpochMilli()

    @Test fun relativeTimes() {
        assertEquals("방금 갱신", WidgetStatus.statusLine(30_000, 0, LastResult.SUCCESS, null))
        assertEquals("15분 전 갱신", WidgetStatus.statusLine(15 * 60_000L, 0, LastResult.SUCCESS, null))
        assertEquals("3시간 전 갱신", WidgetStatus.statusLine(3 * h + 1, 0, LastResult.SUCCESS, null))
    }

    @Test fun staleAfter24h() {
        assertEquals("2일 전 갱신 · 오래됨", WidgetStatus.statusLine(49 * h, 0, LastResult.SUCCESS, null))
        assertTrue(WidgetStatus.isStale(25 * h, 0))
        assertFalse(WidgetStatus.isStale(23 * h, 0))
        assertTrue(WidgetStatus.isStale(1, null))
    }

    @Test fun failureAndCancel() {
        assertEquals("3시간 전 갱신 · 최근 갱신 실패", WidgetStatus.statusLine(3 * h, 0, LastResult.FAILED, "timeout"))
        assertEquals("3시간 전 갱신 · 갱신 중단됨", WidgetStatus.statusLine(3 * h, 0, LastResult.CANCELED, null))
        assertEquals("2일 전 갱신 · 최근 갱신 실패 · 오래됨", WidgetStatus.statusLine(49 * h, 0, LastResult.FAILED, null))
    }

    @Test fun neverRefreshed() {
        assertEquals("아직 갱신 안 됨", WidgetStatus.statusLine(h, null, null, null))
        assertEquals("아직 갱신 안 됨 · 최근 갱신 실패", WidgetStatus.statusLine(h, null, LastResult.FAILED, "launch"))
    }

    @Test fun previousPeriod() {
        assertFalse(WidgetStatus.isPreviousPeriod("10.1~10.31", at(2026, 10, 21), kst))
        assertTrue(WidgetStatus.isPreviousPeriod("10.1~10.31", at(2026, 11, 1), kst))
        assertTrue(WidgetStatus.isPreviousPeriod("12.1~12.31", at(2027, 1, 2), kst))
        assertFalse(WidgetStatus.isPreviousPeriod(null, at(2026, 11, 1), kst))
        assertFalse(WidgetStatus.isPreviousPeriod("이상한값", at(2026, 11, 1), kst))
    }
}
