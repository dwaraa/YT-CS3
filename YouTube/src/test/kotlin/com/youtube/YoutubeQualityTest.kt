package com.youtube

import com.youtube.extractor.YoutubeLinkResolver
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class YoutubeQualityTest {

    @Test
    fun testExtractPixelHeight_parsesStandardStrings() {
        assertEquals(4320, YoutubeLinkResolver.extractPixelHeight("4320p (8K)"))
        assertEquals(2160, YoutubeLinkResolver.extractPixelHeight("2160p (4K)"))
        assertEquals(2160, YoutubeLinkResolver.extractPixelHeight("4K"))
        assertEquals(1440, YoutubeLinkResolver.extractPixelHeight("1440p (2K)"))
        assertEquals(1440, YoutubeLinkResolver.extractPixelHeight("2K"))
        assertEquals(1080, YoutubeLinkResolver.extractPixelHeight("1080p"))
        assertEquals(1080, YoutubeLinkResolver.extractPixelHeight("1080p60"))
        assertEquals(720, YoutubeLinkResolver.extractPixelHeight("720p"))
        assertEquals(720, YoutubeLinkResolver.extractPixelHeight("720p60"))
        assertEquals(480, YoutubeLinkResolver.extractPixelHeight("480p"))
        assertEquals(360, YoutubeLinkResolver.extractPixelHeight("360p"))
        assertEquals(240, YoutubeLinkResolver.extractPixelHeight("240p"))
        assertEquals(144, YoutubeLinkResolver.extractPixelHeight("144p"))
    }

    @Test
    fun testExtractPixelHeight_usesHeightIntIfProvided() {
        assertEquals(1080, YoutubeLinkResolver.extractPixelHeight("custom", 1080))
        assertEquals(2160, YoutubeLinkResolver.extractPixelHeight(null, 2160))
    }

    @Test
    fun testParsePreferredTargetHeight_matchesComplexLabels() {
        assertNull(YoutubeLinkResolver.parsePreferredTargetHeight("Auto"))
        assertNull(YoutubeLinkResolver.parsePreferredTargetHeight("Auto (Best)"))
        assertNull(YoutubeLinkResolver.parsePreferredTargetHeight(null))
        assertNull(YoutubeLinkResolver.parsePreferredTargetHeight(""))

        assertEquals(2160, YoutubeLinkResolver.parsePreferredTargetHeight("2160p (4K)"))
        assertEquals(1440, YoutubeLinkResolver.parsePreferredTargetHeight("1440p (2K)"))
        assertEquals(1080, YoutubeLinkResolver.parsePreferredTargetHeight("1080p (Full HD)"))
        assertEquals(720, YoutubeLinkResolver.parsePreferredTargetHeight("720p (HD)"))
        assertEquals(480, YoutubeLinkResolver.parsePreferredTargetHeight("480p"))
        assertEquals(360, YoutubeLinkResolver.parsePreferredTargetHeight("360p"))
    }

    @Test
    fun testHeightToQuality_mapsToNumericHeights() {
        assertEquals(2160, YoutubeLinkResolver.heightToQuality(2160))
        assertEquals(1440, YoutubeLinkResolver.heightToQuality(1440))
        assertEquals(1080, YoutubeLinkResolver.heightToQuality(1080))
        assertEquals(720, YoutubeLinkResolver.heightToQuality(720))
        assertEquals(480, YoutubeLinkResolver.heightToQuality(480))
        assertEquals(360, YoutubeLinkResolver.heightToQuality(360))
    }

    @Test
    fun testFormatResolutionLabel() {
        assertEquals("4320p (8K)", YoutubeLinkResolver.formatResolutionLabel(4320))
        assertEquals("2160p (4K)", YoutubeLinkResolver.formatResolutionLabel(2160))
        assertEquals("1440p (2K)", YoutubeLinkResolver.formatResolutionLabel(1440))
        assertEquals("1080p", YoutubeLinkResolver.formatResolutionLabel(1080))
        assertEquals("720p", YoutubeLinkResolver.formatResolutionLabel(720))
        assertEquals("360p", YoutubeLinkResolver.formatResolutionLabel(360))
    }
}
