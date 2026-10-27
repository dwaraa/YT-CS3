package com.youtube

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class YoutubePreferencesTest {

    private val mapper = jacksonObjectMapper()

    @Test
    fun testSubscribedChannelSerialization() {
        val channel = SubscribedChannel(
            id = "@veritasium",
            name = "Veritasium",
            url = "https://www.youtube.com/@veritasium",
            avatarUrl = "https://yt3.googleusercontent.com/avatar.jpg"
        )

        val json = mapper.writeValueAsString(listOf(channel))
        val deserialized: List<SubscribedChannel> = mapper.readValue(json)

        assertEquals(1, deserialized.size)
        assertEquals("@veritasium", deserialized[0].id)
        assertEquals("Veritasium", deserialized[0].name)
        assertEquals("https://www.youtube.com/@veritasium", deserialized[0].url)
        assertEquals("https://yt3.googleusercontent.com/avatar.jpg", deserialized[0].avatarUrl)
    }

    @Test
    fun testHomeRowTypeEnumDefaults() {
        assertEquals(10, HomeRowType.entries.size)
        assertTrue(HomeRowType.SUBSCRIBED_CHANNELS.defaultEnabled)
        assertTrue(HomeRowType.COMBINED_SUBSCRIPTIONS.defaultEnabled)
        assertTrue(HomeRowType.SHORTS.defaultEnabled)
        assertTrue(HomeRowType.INDIVIDUAL_CHANNELS.defaultEnabled)
        assertTrue(HomeRowType.TRENDING.defaultEnabled)

        val fromKey = HomeRowType.fromPrefKey("show_trending")
        assertNotNull(fromKey)
        assertEquals(HomeRowType.TRENDING, fromKey)

        val fromShortsKey = HomeRowType.fromPrefKey("show_shorts")
        assertNotNull(fromShortsKey)
        assertEquals(HomeRowType.SHORTS, fromShortsKey)
    }

    @Test
    fun testStage4Enums() {
        assertEquals(ShortsMode.SHOW_INLINE, ShortsMode.fromPrefValue("inline"))
        assertEquals(ShortsMode.SEPARATE_ROW, ShortsMode.fromPrefValue("separate_row"))
        assertEquals(ShortsMode.HIDE, ShortsMode.fromPrefValue("hide"))
        assertEquals(ShortsMode.SHOW_INLINE, ShortsMode.fromPrefValue(null))

        assertEquals(SearchContentFilter.ALL, SearchContentFilter.fromPrefValue("all"))
        assertEquals(SearchContentFilter.VIDEOS_ONLY, SearchContentFilter.fromPrefValue("videos"))
        assertEquals(SearchContentFilter.CHANNELS_ONLY, SearchContentFilter.fromPrefValue("channels"))
        assertEquals(SearchContentFilter.PLAYLISTS_ONLY, SearchContentFilter.fromPrefValue("playlists"))

        assertEquals(SearchSortOrder.RELEVANCE, SearchSortOrder.fromPrefValue("relevance"))
        assertEquals(SearchSortOrder.UPLOAD_DATE, SearchSortOrder.fromPrefValue("date"))
        assertEquals(SearchSortOrder.VIEW_COUNT, SearchSortOrder.fromPrefValue("views"))
        assertEquals(SearchSortOrder.RATING, SearchSortOrder.fromPrefValue("rating"))
    }

    @Test
    fun testDefaultRowOrder_containsSubscribedChannels() {
        val defaultRows = YoutubePreferences.defaultRowOrder
        assertTrue(defaultRows.contains(HomeRowType.SUBSCRIBED_CHANNELS))
        assertTrue(defaultRows.contains(HomeRowType.COMBINED_SUBSCRIPTIONS))
        assertEquals(HomeRowType.COMBINED_SUBSCRIPTIONS, defaultRows[0])
        assertEquals(HomeRowType.SUBSCRIBED_CHANNELS, defaultRows[1])
    }
}
