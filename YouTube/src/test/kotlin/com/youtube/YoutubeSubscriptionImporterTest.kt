package com.youtube

import com.youtube.subscription.YoutubeSubscriptionImporter
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class YoutubeSubscriptionImporterTest {

    @Test
    fun testParseNewPipeJson() {
        val json = """
        {
          "app_version": "0.27.0",
          "app_version_int": 1000,
          "subscriptions": [
            {
              "service_id": 0,
              "url": "https://www.youtube.com/@mkbhd",
              "name": "Marques Brownlee"
            },
            {
              "service_id": 0,
              "url": "https://www.youtube.com/channel/UCxxxx",
              "name": "Custom Channel"
            }
          ]
        }
        """.trimIndent()

        val result = YoutubeSubscriptionImporter.detectAndImport(json)
        assertEquals("NewPipe JSON", result.format)
        assertEquals(2, result.totalFound)
        assertEquals("Marques Brownlee", result.importedChannels[0].name)
        assertEquals("https://www.youtube.com/@mkbhd", result.importedChannels[0].url)
        assertEquals("Custom Channel", result.importedChannels[1].name)
    }

    @Test
    fun testParseGoogleTakeoutCsv() {
        val csv = """
        Channel Id,Channel Url,Channel Title
        UCBJycsmduvYEL83R_U4JriQ,http://www.youtube.com/channel/UCBJycsmduvYEL83R_U4JriQ,Marques Brownlee
        "UCHnyfMqiRRG1u-2MsSQLbXA","http://www.youtube.com/channel/UCHnyfMqiRRG1u-2MsSQLbXA","Veritasium, Science"
        """.trimIndent()

        val result = YoutubeSubscriptionImporter.detectAndImport(csv)
        assertEquals("Google Takeout CSV", result.format)
        assertEquals(2, result.totalFound)
        assertEquals("Marques Brownlee", result.importedChannels[0].name)
        assertEquals("https://www.youtube.com/channel/UCBJycsmduvYEL83R_U4JriQ", result.importedChannels[0].url)
        assertEquals("Veritasium, Science", result.importedChannels[1].name)
    }

    @Test
    fun testParseOpmlXml() {
        val opml = """
        <?xml version="1.0" encoding="UTF-8"?>
        <opml version="1.1">
          <head><title>YouTube Subscriptions</title></head>
          <body>
            <outline text="YouTube Subscriptions" title="YouTube Subscriptions">
              <outline text="Kurzgesagt" title="Kurzgesagt – In a Nutshell" type="rss" xmlUrl="https://www.youtube.com/feeds/videos.xml?channel_id=UCsXVk37bltHxD1rDPwtNM8Q" htmlUrl="https://www.youtube.com/channel/UCsXVk37bltHxD1rDPwtNM8Q" />
              <outline text="Veritasium" type="rss" xmlUrl="https://www.youtube.com/feeds/videos.xml?channel_id=UCHnyfMqiRRG1u-2MsSQLbXA" />
            </outline>
          </body>
        </opml>
        """.trimIndent()

        val result = YoutubeSubscriptionImporter.detectAndImport(opml)
        assertEquals("OPML XML", result.format)
        assertEquals(2, result.totalFound)
        assertEquals("Kurzgesagt – In a Nutshell", result.importedChannels[0].name)
        assertEquals("https://www.youtube.com/channel/UCsXVk37bltHxD1rDPwtNM8Q", result.importedChannels[0].url)
        assertEquals("Veritasium", result.importedChannels[1].name)
        assertEquals("https://www.youtube.com/channel/UCHnyfMqiRRG1u-2MsSQLbXA", result.importedChannels[1].url)
    }

    @Test
    fun testExportAndReimportJson() {
        val channels = listOf(
            SubscribedChannel("@mkbhd", "Marques Brownlee", "https://www.youtube.com/@mkbhd", ""),
            SubscribedChannel("@veritasium", "Veritasium", "https://www.youtube.com/@veritasium", "")
        )

        val exportedJson = YoutubeSubscriptionImporter.exportToNewPipeJson(channels)
        assertTrue(exportedJson.contains("Marques Brownlee"))
        assertTrue(exportedJson.contains("Veritasium"))

        val reimported = YoutubeSubscriptionImporter.detectAndImport(exportedJson)
        assertEquals(2, reimported.totalFound)
        assertEquals("Marques Brownlee", reimported.importedChannels[0].name)
    }

    @Test
    fun testExportAndReimportCsv() {
        val channels = listOf(
            SubscribedChannel("@mkbhd", "Marques Brownlee, Tech", "https://www.youtube.com/@mkbhd", "")
        )

        val exportedCsv = YoutubeSubscriptionImporter.exportToTakeoutCsv(channels)
        assertTrue(exportedCsv.contains("\"Marques Brownlee, Tech\""))

        val reimported = YoutubeSubscriptionImporter.detectAndImport(exportedCsv)
        assertEquals(1, reimported.totalFound)
        assertEquals("Marques Brownlee, Tech", reimported.importedChannels[0].name)
    }
}
