package com.youtube

import com.youtube.extractor.YoutubeEpisodeMapper
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.concurrent.TimeUnit

class YoutubeDateParserTest {

    @Test
    fun testParseRelativeTimeToEpochMs() {
        val now = 1700000000000L // arbitrary fixed timestamp

        val fiveMinutesAgo = YoutubeEpisodeMapper.parseRelativeTimeToEpochMs("5 minutes ago", now)
        assertEquals(now - TimeUnit.MINUTES.toMillis(5), fiveMinutesAgo)

        val twoHoursAgo = YoutubeEpisodeMapper.parseRelativeTimeToEpochMs("2 hours ago", now)
        assertEquals(now - TimeUnit.HOURS.toMillis(2), twoHoursAgo)

        val threeDaysAgo = YoutubeEpisodeMapper.parseRelativeTimeToEpochMs("3 days ago", now)
        assertEquals(now - TimeUnit.DAYS.toMillis(3), threeDaysAgo)

        val streamedYesterday = YoutubeEpisodeMapper.parseRelativeTimeToEpochMs("Streamed 1 day ago", now)
        assertEquals(now - TimeUnit.DAYS.toMillis(1), streamedYesterday)

        val twoWeeksAgo = YoutubeEpisodeMapper.parseRelativeTimeToEpochMs("2 weeks ago", now)
        assertEquals(now - TimeUnit.DAYS.toMillis(14), twoWeeksAgo)

        val oneMonthAgo = YoutubeEpisodeMapper.parseRelativeTimeToEpochMs("1 month ago", now)
        assertEquals(now - TimeUnit.DAYS.toMillis(30), oneMonthAgo)

        val oneYearAgo = YoutubeEpisodeMapper.parseRelativeTimeToEpochMs("1 year ago", now)
        assertEquals(now - TimeUnit.DAYS.toMillis(365), oneYearAgo)
    }

    @Test
    fun testRelativeTimeSortingOrder() {
        val now = 1700000000000L
        val t1 = YoutubeEpisodeMapper.parseRelativeTimeToEpochMs("10 minutes ago", now)
        val t2 = YoutubeEpisodeMapper.parseRelativeTimeToEpochMs("2 hours ago", now)
        val t3 = YoutubeEpisodeMapper.parseRelativeTimeToEpochMs("3 days ago", now)
        val t4 = YoutubeEpisodeMapper.parseRelativeTimeToEpochMs("2 weeks ago", now)

        assertTrue(t1 > t2)
        assertTrue(t2 > t3)
        assertTrue(t3 > t4)
    }
}
