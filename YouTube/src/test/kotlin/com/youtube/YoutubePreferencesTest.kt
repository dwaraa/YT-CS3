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
        assertEquals(8, HomeRowType.entries.size)
        assertTrue(HomeRowType.COMBINED_SUBSCRIPTIONS.defaultEnabled)
        assertTrue(HomeRowType.INDIVIDUAL_CHANNELS.defaultEnabled)
        assertTrue(HomeRowType.TRENDING.defaultEnabled)

        val fromKey = HomeRowType.fromPrefKey("show_trending")
        assertNotNull(fromKey)
        assertEquals(HomeRowType.TRENDING, fromKey)
    }
}
