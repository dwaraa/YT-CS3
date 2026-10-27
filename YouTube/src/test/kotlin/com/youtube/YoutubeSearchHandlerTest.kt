package com.youtube

import com.youtube.search.YoutubeSearchHandler
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class YoutubeSearchHandlerTest {

    @Test
    fun testParseQueryPlain() {
        val parsed = YoutubeSearchHandler.parseQuery("veritasium")
        assertEquals("veritasium", parsed.cleanQuery)
        assertFalse(parsed.isShortsOnly)
        assertEquals(null, parsed.durationFilter)
    }

    @Test
    fun testParseQueryTypeFilter() {
        val parsed = YoutubeSearchHandler.parseQuery("mkbhd type:channel")
        assertEquals("mkbhd", parsed.cleanQuery)
        assertEquals(SearchContentFilter.CHANNELS_ONLY, parsed.contentFilter)

        val parsedShort = YoutubeSearchHandler.parseQuery("cooking type:shorts")
        assertEquals("cooking", parsedShort.cleanQuery)
        assertTrue(parsedShort.isShortsOnly)
    }

    @Test
    fun testParseQuerySortAndDurationFilters() {
        val parsed = YoutubeSearchHandler.parseQuery("android news sort:date duration:short")
        assertEquals("android news", parsed.cleanQuery)
        assertEquals(SearchSortOrder.UPLOAD_DATE, parsed.sortOrder)
        assertEquals("short", parsed.durationFilter)
    }
}
