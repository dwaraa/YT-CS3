package com.youtube

import com.youtube.extractor.YoutubeLinkResolver
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class YoutubeLinkResolverTest {

    @Test
    fun testParsePreferredTargetHeight() {
        assertNull(YoutubeLinkResolver.parsePreferredTargetHeight(null))
        assertNull(YoutubeLinkResolver.parsePreferredTargetHeight("Auto"))
        assertNull(YoutubeLinkResolver.parsePreferredTargetHeight("Auto (Best)"))

        assertEquals(2160, YoutubeLinkResolver.parsePreferredTargetHeight("2160p (4K)"))
        assertEquals(1440, YoutubeLinkResolver.parsePreferredTargetHeight("1440p (2K)"))
        assertEquals(1080, YoutubeLinkResolver.parsePreferredTargetHeight("1080p (Full HD)"))
        assertEquals(720, YoutubeLinkResolver.parsePreferredTargetHeight("720p (HD)"))
        assertEquals(480, YoutubeLinkResolver.parsePreferredTargetHeight("480p"))
        assertEquals(360, YoutubeLinkResolver.parsePreferredTargetHeight("360p"))
    }

    @Test
    fun testHeightToQualityMapping() {
        assertEquals(2160, YoutubeLinkResolver.heightToQuality(4320))
        assertEquals(2160, YoutubeLinkResolver.heightToQuality(2160))
        assertEquals(1440, YoutubeLinkResolver.heightToQuality(1440))
        assertEquals(1080, YoutubeLinkResolver.heightToQuality(1080))
        assertEquals(720, YoutubeLinkResolver.heightToQuality(720))
        assertEquals(480, YoutubeLinkResolver.heightToQuality(480))
        assertEquals(360, YoutubeLinkResolver.heightToQuality(360))
        assertEquals(240, YoutubeLinkResolver.heightToQuality(240))
        assertEquals(144, YoutubeLinkResolver.heightToQuality(144))
    }

    @Test
    fun testResolutionLabelFormatting() {
        assertEquals("4320p (8K)", YoutubeLinkResolver.formatResolutionLabel(4320))
        assertEquals("2160p (4K)", YoutubeLinkResolver.formatResolutionLabel(2160))
        assertEquals("1440p (2K)", YoutubeLinkResolver.formatResolutionLabel(1440))
        assertEquals("1080p", YoutubeLinkResolver.formatResolutionLabel(1080))
        assertEquals("720p", YoutubeLinkResolver.formatResolutionLabel(720))
        assertEquals("480p", YoutubeLinkResolver.formatResolutionLabel(480))
        assertEquals("360p", YoutubeLinkResolver.formatResolutionLabel(360))
        assertEquals("240p", YoutubeLinkResolver.formatResolutionLabel(240))
        assertEquals("144p", YoutubeLinkResolver.formatResolutionLabel(144))
    }

    @Test
    fun testExtractPixelHeight_variousFormats() {
        assertEquals(1080, YoutubeLinkResolver.extractPixelHeight("1080p60"))
        assertEquals(720, YoutubeLinkResolver.extractPixelHeight("720p50"))
        assertEquals(1440, YoutubeLinkResolver.extractPixelHeight("1440p"))
        assertEquals(2160, YoutubeLinkResolver.extractPixelHeight("2160p (4K)"))
        assertEquals(0, YoutubeLinkResolver.extractPixelHeight(""))
        assertEquals(0, YoutubeLinkResolver.extractPixelHeight(null))
    }
}
