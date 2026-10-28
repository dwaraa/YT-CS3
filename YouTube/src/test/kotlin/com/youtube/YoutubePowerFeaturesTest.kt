package com.youtube

import com.youtube.power.DiagnosticsHelper
import com.youtube.power.LocalPlaylistHelper
import com.youtube.power.PlaylistItem
import com.youtube.power.ReturnYouTubeDislikeHelper
import com.youtube.power.SponsorBlockHelper
import com.youtube.power.SponsorSegment
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class YoutubePowerFeaturesTest {

    @Test
    fun testSponsorBlock_formatSegmentsForDescription() {
        val segments = listOf(
            SponsorSegment(
                category = "sponsor",
                actionType = "skip",
                segment = listOf(15.0, 45.0),
                UUID = "uuid1"
            ),
            SponsorSegment(
                category = "intro",
                actionType = "skip",
                segment = listOf(0.0, 10.0),
                UUID = "uuid2"
            )
        )

        val formatted = SponsorBlockHelper.formatSegmentsForDescription(segments)
        assertNotNull(formatted)
        assertTrue(formatted!!.contains("⚡ SponsorBlock Segments (2)"))
        assertTrue(formatted.contains("Sponsor"))
        assertTrue(formatted.contains("00:15 - 00:45"))
        assertTrue(formatted.contains("Intro / Animation"))
        assertTrue(formatted.contains("00:00 - 00:10"))
    }

    @Test
    fun testSponsorBlock_generateWebVtt() {
        val segments = listOf(
            SponsorSegment(
                category = "sponsor",
                actionType = "skip",
                segment = listOf(65.0, 95.5),
                UUID = "seg1"
            )
        )

        val vtt = SponsorBlockHelper.generateWebVtt(segments)
        assertTrue(vtt.startsWith("WEBVTT"))
        assertTrue(vtt.contains("00:01:05.000 --> 00:01:35.500"))
        assertTrue(vtt.contains("[SponsorBlock] Skip Sponsor"))
    }

    @Test
    fun testReturnYouTubeDislike_formatting() {
        val ryd = ReturnYouTubeDislikeHelper.DislikeData(
            id = "test_video_123",
            likes = 10000,
            dislikes = 500,
            rating = 4.75,
            viewCount = 150000
        )

        assertEquals(95.2, ryd.likePercentage, 0.1)
        val stats = ryd.formattedStats()
        assertTrue(stats.contains("👍"))
        assertTrue(stats.contains("10.0K"))
        assertTrue(stats.contains("👎"))
        assertTrue(stats.contains("500"))
        assertTrue(stats.contains("95.2%"))
    }

    @Test
    fun testReturnYouTubeDislike_jsonParsing() {
        val sampleJson = """
            {
                "id": "dQw4w9WgXcQ",
                "dateCreated": "2021-12-01T00:00:00Z",
                "likes": 16000000,
                "dislikes": 350000,
                "rating": 4.89,
                "viewCount": 1500000000,
                "deleted": false
            }
        """.trimIndent()

        val parsed = ReturnYouTubeDislikeHelper.mapper.readValue(
            sampleJson,
            ReturnYouTubeDislikeHelper.DislikeData::class.java
        )

        assertEquals("dQw4w9WgXcQ", parsed.id)
        assertEquals(16000000L, parsed.likes)
        assertEquals(350000L, parsed.dislikes)
        assertEquals(4.89, parsed.rating, 0.01)
        assertTrue(parsed.likePercentage > 97.0)
    }

    @Test
    fun testLocalPlaylist_serialization() {
        val item1 = PlaylistItem("vid1", "Test Video 1", "https://youtube.com/watch?v=vid1", "https://thumb/1.jpg")
        val item2 = PlaylistItem("vid2", "Test Video 2", "https://youtube.com/watch?v=vid2", "https://thumb/2.jpg")
        val playlist = LocalPlaylistHelper.LocalPlaylist(
            id = "pl_123",
            name = "My Favorites",
            items = listOf(item1, item2)
        )

        val json = LocalPlaylistHelper.mapper.writeValueAsString(playlist)
        assertTrue(json.contains("My Favorites"))
        assertTrue(json.contains("vid1"))
        assertTrue(json.contains("vid2"))

        val deserialized = LocalPlaylistHelper.mapper.readValue(
            json,
            LocalPlaylistHelper.LocalPlaylist::class.java
        )
        assertEquals("pl_123", deserialized.id)
        assertEquals(2, deserialized.items.size)
        assertEquals("Test Video 1", deserialized.items[0].title)
    }

    @Test
    fun testDiagnostics_generateMarkdownReport() {
        val result = DiagnosticsHelper.DiagnosticsResult(
            newpipeVersion = "v0.26.5",
            newpipeVersionOk = true,
            consentCookieHealthy = true,
            extractionOk = true,
            extractionDetails = "Sample queries successful",
            pingYoutubeMs = 85,
            pingSponsorBlockMs = 120,
            pingRydMs = 95,
            audioTrackHelperFunctional = true,
            activeSubscriptionsCount = 5,
            activeProfile = "Default",
            summary = "All diagnostics passed cleanly."
        )

        val report = DiagnosticsHelper.generateMarkdownReport(result)
        assertTrue(report.contains("# YouTube CloudStream Extension Diagnostics Report"))
        assertTrue(report.contains("v0.26.5"))
        assertTrue(report.contains("85ms"))
        assertTrue(report.contains("Default"))
        assertTrue(report.contains("All diagnostics passed cleanly."))
    }

    @Test
    fun testProfileNamespacing() {
        val origProfile = YoutubePreferences.activeProfile
        try {
            YoutubePreferences.activeProfile = "Default"
            assertEquals("subscribed_channels_json", YoutubePreferences.profileKey("subscribed_channels_json"))

            YoutubePreferences.activeProfile = "Kids"
            assertEquals("prof_kids_subscribed_channels_json", YoutubePreferences.profileKey("subscribed_channels_json"))

            YoutubePreferences.activeProfile = "Music"
            assertEquals("prof_music_home_row_order_json", YoutubePreferences.profileKey("home_row_order_json"))

            YoutubePreferences.activeProfile = "Work Profile"
            assertEquals("prof_work_profile_trending_region_code", YoutubePreferences.profileKey("trending_region_code"))
        } finally {
            YoutubePreferences.activeProfile = origProfile
        }
    }
}
