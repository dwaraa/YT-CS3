package com.youtube

import com.youtube.loader.YoutubeLoaders
import org.junit.Assert.assertEquals
import org.junit.Test

class YoutubeUrlUtilsTest {

    @Test
    fun testSanitizeUrl_addsHttpsProtocol() {
        assertEquals("https://www.youtube.com/watch?v=dQw4w9WgXcQ", YoutubeLoaders.sanitizeUrl("//www.youtube.com/watch?v=dQw4w9WgXcQ"))
        assertEquals("https://www.youtube.com/@mkbhd", YoutubeLoaders.sanitizeUrl("www.youtube.com/@mkbhd"))
        assertEquals("https://www.youtube.com/channel/UC123", YoutubeLoaders.sanitizeUrl("https://www.youtube.com/channel/UC123"))
    }

    @Test
    fun testSanitizeToWatchUrl_convertsShortsAndYoutuBe() {
        val expected = "https://www.youtube.com/watch?v=dQw4w9WgXcQ"

        assertEquals(expected, YoutubeLoaders.sanitizeToWatchUrl("https://youtu.be/dQw4w9WgXcQ"))
        assertEquals(expected, YoutubeLoaders.sanitizeToWatchUrl("https://youtu.be/dQw4w9WgXcQ?t=42"))
        assertEquals(expected, YoutubeLoaders.sanitizeToWatchUrl("https://www.youtube.com/shorts/dQw4w9WgXcQ"))
        assertEquals(expected, YoutubeLoaders.sanitizeToWatchUrl("https://www.youtube.com/embed/dQw4w9WgXcQ"))
        assertEquals(expected, YoutubeLoaders.sanitizeToWatchUrl("https://www.youtube.com/watch?v=dQw4w9WgXcQ"))
    }

    @Test
    fun testGetUrlType_correctlyCategorizes() {
        assertEquals(YoutubeLoaders.UrlType.Video, YoutubeLoaders.getUrlType("https://www.youtube.com/watch?v=abc12345"))
        assertEquals(YoutubeLoaders.UrlType.Video, YoutubeLoaders.getUrlType("https://youtu.be/abc12345"))
        assertEquals(YoutubeLoaders.UrlType.Video, YoutubeLoaders.getUrlType("https://www.youtube.com/shorts/abc12345"))

        assertEquals(YoutubeLoaders.UrlType.Channel, YoutubeLoaders.getUrlType("https://www.youtube.com/@mkbhd"))
        assertEquals(YoutubeLoaders.UrlType.Channel, YoutubeLoaders.getUrlType("https://www.youtube.com/channel/UCBJycsmduvYEL83R_U4JriQ"))
        assertEquals(YoutubeLoaders.UrlType.Channel, YoutubeLoaders.getUrlType("https://www.youtube.com/c/mkbhd"))

        assertEquals(YoutubeLoaders.UrlType.Playlist, YoutubeLoaders.getUrlType("https://www.youtube.com/playlist?list=PL123456789"))
    }
}
