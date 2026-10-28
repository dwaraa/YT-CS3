package com.youtube

import com.youtube.extractor.YoutubeEpisodeMapper
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class YoutubeFeedLogicTest {

    @Test
    fun testParseRelativeTimeToEpochMs() {
        val now = 1700000000000L // arbitrary epoch reference
        val oneHourAgo = YoutubeEpisodeMapper.parseRelativeTimeToEpochMs("1 hour ago", now)
        assertEquals(now - (3600 * 1000L), oneHourAgo)

        val twoDaysAgo = YoutubeEpisodeMapper.parseRelativeTimeToEpochMs("2 days ago", now)
        assertEquals(now - (2 * 86400 * 1000L), twoDaysAgo)

        val threeWeeksAgo = YoutubeEpisodeMapper.parseRelativeTimeToEpochMs("3 weeks ago", now)
        assertEquals(now - (3 * 7 * 86400 * 1000L), threeWeeksAgo)
    }

    @Test
    fun testFormatSecondsToTimestamp() {
        assertEquals("00:45", YoutubeEpisodeMapper.formatSecondsToTimestamp(45))
        assertEquals("03:20", YoutubeEpisodeMapper.formatSecondsToTimestamp(200))
        assertEquals("1:00:00", YoutubeEpisodeMapper.formatSecondsToTimestamp(3600))
        assertEquals("1:15:30", YoutubeEpisodeMapper.formatSecondsToTimestamp(4530))
    }

    @Test
    fun testFormatCount() {
        assertEquals("1.5B", YoutubeEpisodeMapper.formatCount(1_500_000_000L))
        assertEquals("2.4M", YoutubeEpisodeMapper.formatCount(2_400_000L))
        assertEquals("850.0K", YoutubeEpisodeMapper.formatCount(850_000L))
        assertEquals("500", YoutubeEpisodeMapper.formatCount(500L))
        assertEquals("", YoutubeEpisodeMapper.formatCount(0L))
    }

    @Test
    fun testIsShortUrl() {
        assertTrue(YoutubeEpisodeMapper.isShortUrl("https://www.youtube.com/shorts/abc123xyz"))
        assertFalse(YoutubeEpisodeMapper.isShortUrl("https://www.youtube.com/watch?v=abc123xyz"))
    }

    @Test
    fun testDefaultRowOrderIntegrity() {
        val order = YoutubePreferences.defaultRowOrder
        assertEquals(12, order.size)
        assertEquals(HomeRowType.COMBINED_SUBSCRIPTIONS, order[0])
        assertEquals(HomeRowType.SUBSCRIBED_CHANNELS, order[1])
        assertTrue(order.contains(HomeRowType.SHORTS))
        assertTrue(order.contains(HomeRowType.WATCH_LATER))
        assertTrue(order.contains(HomeRowType.LOCAL_PLAYLISTS))
        assertTrue(order.contains(HomeRowType.TRENDING))
        assertTrue(order.contains(HomeRowType.LIVE_STREAMS))
    }
}
