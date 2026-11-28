// Copyright 2026 fampep
// Licensed under the Apache License, Version 2.0. See the LICENSE file.

package site.anikitty.app.cast

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayOutputStream
import java.util.zip.GZIPOutputStream

class CastRelayTest {
    @Test
    fun corsAllowsPrivateAndLocalNetwork() {
        val lines = castRelayCorsLines()
        assertTrue(lines.any { it == "Access-Control-Allow-Private-Network: true" })
        assertTrue(lines.any { it == "Access-Control-Allow-Local-Network: true" })
        assertTrue(lines.any { it.startsWith("Access-Control-Allow-Origin:") })
    }

    @Test
    fun animexTxtVariantsStayPlaylists() {
        val variant = "https://s-91.megavid.se/i/abc/s/1080p/nut-1.txt"
        val master = "https://s-91.megavid.se/i/abc/s/deeznuts.txt"
        val segment = "https://s-91.megavid.se/i/abc/s/c/frame.jpg"
        assertTrue(castRelayPlaylistUrl(master))
        assertTrue(castRelayPlaylistUrl(variant))
        assertTrue(castRelayLooksLikePlaylist(variant, emptyMap()))
        assertTrue(!castRelayPlaylistUrl(segment))
        assertTrue(
            castRelayLooksLikePlaylist(
                segment,
                mapOf("content-type" to listOf("application/vnd.apple.mpegurl")),
            ),
        )
    }

    @Test
    fun xanimeIcoVariantsStayPlaylists() {
        val variant = "https://xanivsrc10.org/media/s1v/085/92580/a-index-f1-v1-a1.ico?slug=1"
        val segmentJs = "https://xanivsrc10.org/media/s1v/085/92580/seg-1-f1-v1-a1.js"
        val segmentGif = "https://xanivsrc10.org/media/s1v/085/92580/seg-2-f1-v1-a1.gif"
        assertTrue(castRelayPlaylistUrl(variant))
        assertTrue(castRelayLooksLikePlaylist(variant, emptyMap()))
        assertTrue(!castRelayPlaylistUrl(segmentJs))
        assertTrue(!castRelayPlaylistUrl(segmentGif))
    }

    @Test
    fun castContentKeepsTheLanguageQuery() {
        val dub = castRelayContent("http://192.168.1.8:4321/index.m3u8?pack=ts&lang=en")
        assertEquals("http://192.168.1.8:4321/index.m3u8?lang=en", dub.url)
        assertEquals("ts", dub.pack)
        val sub = castRelayContent("http://192.168.1.8:4321/index.m3u8?lang=ja&pack=fmp4")
        assertEquals("http://192.168.1.8:4321/index.m3u8?lang=ja", sub.url)
        assertEquals("fmp4", sub.pack)
        val plain = castRelayContent("http://192.168.1.8:4321/index.m3u8")
        assertEquals("http://192.168.1.8:4321/index.m3u8", plain.url)
        assertEquals(null, plain.pack)
        val locked = castRelayContent("http://192.168.1.8:4321/index.m3u8?pack=ts&lang=en&h=720")
        assertEquals("http://192.168.1.8:4321/index.m3u8?lang=en&h=720", locked.url)
        assertEquals("ts", locked.pack)
    }

    @Test
    fun castUrlChangesWhenTheAudioLanguageChanges() {
        assertEquals("index.m3u8", castRelayPlaybackName(hls = true, segmentPack = null, audioLanguage = null))
        assertEquals("index.m3u8?pack=ts", castRelayPlaybackName(hls = true, segmentPack = "ts", audioLanguage = null))
        assertEquals(
            "index.m3u8?pack=ts&lang=en",
            castRelayPlaybackName(hls = true, segmentPack = "ts", audioLanguage = "en"),
        )
        assertEquals("index.m3u8?lang=ja", castRelayPlaybackName(hls = true, segmentPack = null, audioLanguage = "ja"))
        assertEquals(
            "index.m3u8?lang=ja&h=720",
            castRelayPlaybackName(hls = true, segmentPack = null, audioLanguage = "ja", maxHeight = 720),
        )
        assertEquals(
            "index.m3u8?pack=fmp4&h=1080",
            castRelayPlaybackName(hls = true, segmentPack = "fmp4", audioLanguage = null, maxHeight = 1080),
        )
        assertEquals("video.mp4", castRelayPlaybackName(hls = false, segmentPack = "ts", audioLanguage = "en"))
    }

    @Test
    fun masterDropsVariantsAboveTheQualityCeiling() {
        val master = """
            #EXTM3U
            #EXT-X-MEDIA:TYPE=AUDIO,GROUP-ID="a",NAME="Japanese",LANGUAGE="jpn",DEFAULT=YES
            #EXT-X-STREAM-INF:BANDWIDTH=800000,RESOLUTION=1280x720,AUDIO="a"
            720.m3u8
            #EXT-X-STREAM-INF:BANDWIDTH=2000000,RESOLUTION=1920x1080,AUDIO="a"
            1080.m3u8
            #EXT-X-STREAM-INF:BANDWIDTH=400000,RESOLUTION=640x360,AUDIO="a"
            360.m3u8
        """.trimIndent()
        val capped = castRelayCapHeight(master, 720)
        assertTrue(capped.contains("720.m3u8"))
        assertTrue(capped.contains("360.m3u8"))
        assertTrue(!capped.contains("1080.m3u8"))
        assertTrue(!capped.contains("1920x1080"))
        assertTrue(capped.contains("TYPE=AUDIO"))
        assertEquals(master, castRelayCapHeight(master, null))
        assertEquals(master, castRelayCapHeight(master, 0))
        assertEquals(master, castRelayCapHeight(master, 2160))
    }

    @Test
    fun ceilingAboveEveryRungKeepsTheLowest() {
        val master = """
            #EXTM3U
            #EXT-X-STREAM-INF:BANDWIDTH=2000000,RESOLUTION=1920x1080
            1080.m3u8
            #EXT-X-STREAM-INF:BANDWIDTH=5000000,RESOLUTION=2560x1440
            1440.m3u8
        """.trimIndent()
        val capped = castRelayCapHeight(master, 480)
        assertTrue(capped.contains("1080.m3u8"))
        assertTrue(!capped.contains("1440.m3u8"))
        assertTrue(capped.contains("1920x1080"))
    }

    @Test
    fun sharedMasterFollowsTheSelectedAudio() {
        val master = """
            #EXTM3U
            #EXT-X-MEDIA:TYPE=SUBTITLES,GROUP-ID="subs",NAME="English",LANGUAGE="eng",DEFAULT=YES,URI="s.vtt"
            #EXT-X-MEDIA:TYPE=AUDIO,GROUP-ID="stereo",NAME="Spanish",LANGUAGE="spa",DEFAULT=NO,AUTOSELECT=YES
            #EXT-X-MEDIA:TYPE=AUDIO,GROUP-ID="stereo",NAME="English",LANGUAGE="eng",DEFAULT=NO,AUTOSELECT=YES
            #EXT-X-MEDIA:TYPE=AUDIO,GROUP-ID="stereo",NAME="Japanese",LANGUAGE="jpn",DEFAULT=YES,AUTOSELECT=YES
            #EXT-X-MEDIA:TYPE=AUDIO,GROUP-ID="commentary",NAME="Spanish",LANGUAGE="spa",DEFAULT=YES
            #EXT-X-STREAM-INF:BANDWIDTH=1,AUDIO="stereo"
            video.m3u8
        """.trimIndent()
        val dub = castRelayPreferAudio(master, "en")
        assertTrue(dub.contains("NAME=\"English\",LANGUAGE=\"eng\",DEFAULT=YES"))
        assertTrue(dub.contains("NAME=\"Japanese\",LANGUAGE=\"jpn\",DEFAULT=NO"))
        assertTrue(dub.contains("NAME=\"Spanish\",LANGUAGE=\"spa\",DEFAULT=NO,AUTOSELECT=YES"))
        assertTrue(dub.contains("GROUP-ID=\"commentary\",NAME=\"Spanish\",LANGUAGE=\"spa\",DEFAULT=YES"))
        assertTrue(dub.contains("TYPE=SUBTITLES,GROUP-ID=\"subs\",NAME=\"English\",LANGUAGE=\"eng\",DEFAULT=YES"))
        assertTrue(dub.endsWith("video.m3u8"))
        val sub = castRelayPreferAudio(master, "ja")
        assertTrue(sub.contains("LANGUAGE=\"jpn\",DEFAULT=YES"))
        assertTrue(sub.contains("LANGUAGE=\"eng\",DEFAULT=NO"))
        assertEquals(master, castRelayPreferAudio(master, "da"))
        assertEquals(master, castRelayPreferAudio(master, null))
    }

    @Test
    fun namedAudioRenditionIsEnoughWhenLanguageIsMissing() {
        val master = """
            #EXTM3U
            #EXT-X-MEDIA:TYPE=AUDIO,NAME="Japanese",DEFAULT=YES,URI="ja.m3u8"
            #EXT-X-MEDIA:TYPE=AUDIO,NAME="English",DEFAULT=NO,URI="en.m3u8"
        """.trimIndent()
        val dub = castRelayPreferAudio(master, "en")
        assertTrue(dub.contains("NAME=\"English\",DEFAULT=YES"))
        assertTrue(dub.contains("NAME=\"Japanese\",DEFAULT=NO"))
    }

    @Test
    fun unnamedSegmentsFollowTheBytesNotTheDisguise() {
        assertEquals("ts", castRelayPackFromBytes(byteArrayOf(0x47, 0x40, 0x00, 0x10)))
        assertEquals(
            "fmp4",
            castRelayPackFromBytes(
                byteArrayOf(0, 0, 0, 0x18, 'f'.code.toByte(), 't'.code.toByte(), 'y'.code.toByte(), 'p'.code.toByte()),
            ),
        )
        assertNull(castRelayPackFromBytes("#EXTM3U\n".toByteArray()))
        val disguised = "https://cdn.example/hls/seg-1"
        assertEquals("video/mp4", castRelaySegmentType(disguised, "application/octet-stream", "fmp4"))
        assertEquals("video/mp4", castRelaySegmentType(disguised, "video/mp2t", "fmp4"))
        assertEquals("video/mp2t", castRelaySegmentType(disguised, "image/jpeg", "ts"))
        assertEquals("video/mp2t", castRelaySegmentType(disguised, "", null))
        assertEquals("video/mp2t", castRelaySegmentType("https://cdn.example/s/1/a", "", "fmp4"))
    }

    @Test
    fun scrapedPlaylistLinksStayInThePath() {
        val upstream = "https://cdn.example/hls/720/index.m3u8?token=a&exp=1"
        val payload = castRelayEncodeTarget(upstream)
        assertEquals(upstream, castRelayDecodeTarget(payload))
        assertEquals(upstream, castRelayTarget("/m/$payload/index.m3u8"))
        assertNull(castRelayTarget("/u"))
        assertNull(castRelayDecodeTarget("%%%%"))
        val master = """
            #EXTM3U
            #EXT-X-STREAM-INF:BANDWIDTH=1000
            720/index.m3u8?token=a&exp=1
            #EXT-X-MEDIA:TYPE=AUDIO,GROUP-ID="a",NAME="Japanese",DEFAULT=YES,URI="audio.m3u8"
            #EXT-X-MEDIA:TYPE=SUBTITLES,GROUP-ID="s",NAME="English",URI="sub.vtt"
            #EXTINF:4,
            seg-1.jpg
            #EXT-X-KEY:METHOD=AES-128,URI="enc.key"
            #EXT-X-MAP:URI=init.mp4
        """.trimIndent()
        val out = castRelayRewritePlaylist(master, "https://cdn.example/hls/master.m3u8") { absolute, kind ->
            "http://192.168.1.5:9/m/${castRelayEncodeTarget(absolute)}/${castRelayProxyFile(absolute, kind, null)}"
        }
        assertTrue(!out.contains("?u="))
        assertTrue(out.endsWith("\n"))
        val urls = Regex("""/m/([^/]+)/([^/\s"]+)""").findAll(out).map { match ->
            match.groupValues[2] to castRelayTarget("/m/${match.groupValues[1]}/file")
        }.toList()
        assertEquals("index.m3u8", urls[0].first)
        assertEquals(upstream, urls[0].second)
        assertEquals("index.m3u8", urls[1].first)
        assertEquals("https://cdn.example/hls/audio.m3u8", urls[1].second)
        assertEquals("subs.vtt", urls[2].first)
        assertEquals("https://cdn.example/hls/sub.vtt", urls[2].second)
        assertEquals("seg.ts", urls[3].first)
        assertEquals("https://cdn.example/hls/seg-1.jpg", urls[3].second)
        assertEquals("key.bin", urls[4].first)
        assertEquals("https://cdn.example/hls/enc.key", urls[4].second)
        assertEquals("seg.mp4", urls[5].first)
        assertEquals("https://cdn.example/hls/init.mp4", urls[5].second)
    }

    @Test
    fun aSingleMuxedVariantIsThePlaylistToCast() {
        val one = """
            #EXTM3U
            #EXT-X-STREAM-INF:BANDWIDTH=1
            media.m3u8
        """.trimIndent()
        assertEquals("media.m3u8", castRelaySoleVariant(one))
        val two = one + "\n#EXT-X-STREAM-INF:BANDWIDTH=2\nlow.m3u8\n"
        assertNull(castRelaySoleVariant(two))
        val split = """
            #EXTM3U
            #EXT-X-MEDIA:TYPE=AUDIO,GROUP-ID="a",NAME="Japanese",URI="a.m3u8"
            #EXT-X-STREAM-INF:BANDWIDTH=1,AUDIO="a"
            media.m3u8
        """.trimIndent()
        assertNull(castRelaySoleVariant(split))
    }

    @Test
    fun disguisedSegmentsPeelToARealContainer() {
        assertTrue(castRelayRealMediaSuffix("https://cdn.example/a.ts?x=1"))
        assertTrue(!castRelayRealMediaSuffix("https://cdn.example/seg-1.jpg"))
        assertTrue(!castRelayRealMediaSuffix("https://cdn.example/playlist.php?t=1"))
        val ts = ByteArray(188 * 4)
        for (index in ts.indices step 188) ts[index] = 0x47
        val wrappedTs = byteArrayOf(0xFF.toByte(), 0xD8.toByte()) + ts
        val media = castRelayUnwrapSegment(wrappedTs)
        assertEquals(0x47, media[0].toInt() and 0xFF)
        assertEquals("video/mp2t", castRelayUnwrappedType(media, "https://cdn.example/seg-1.jpg", null))
        assertTrue(castRelayUnwrapSegment(ts).contentEquals(ts))
        val junk = byteArrayOf(0xFF.toByte(), 0xD8.toByte(), 0xFF.toByte(), 0xE0.toByte())
        val box = ByteArray(16)
        box[3] = 16
        box[4] = 'f'.code.toByte()
        box[5] = 't'.code.toByte()
        box[6] = 'y'.code.toByte()
        box[7] = 'p'.code.toByte()
        val fmp4 = castRelayUnwrapSegment(junk + box)
        assertEquals("fmp4", castRelayPackFromBytes(fmp4))
        assertEquals("video/mp4", castRelayUnwrappedType(fmp4, "https://cdn.example/seg-1.jpg", null))
    }

    @Test
    fun gzipPlaylistStillReadsAsHls() {
        val raw = "#EXTM3U\n#EXTINF:1,\nseg.ts\n".toByteArray()
        val gzip = ByteArrayOutputStream().use { out ->
            GZIPOutputStream(out).use { it.write(raw) }
            out.toByteArray()
        }
        val text = castRelayPlaylistText(gzip)
        assertTrue(text != null && text.startsWith("#EXTM3U"))
        assertNull(castRelayPlaylistText(byteArrayOf(0x47, 0x00)))
    }

    @Test
    fun segmentFormatIsOmittedUnlessThePlaylistProvesIt() {
        assertEquals("fmp4", certainHlsPack("#EXTM3U\n#EXT-X-MAP:URI=\"init.mp4\"\nseg.m4s\n"))
        assertEquals("ts", certainHlsPack("#EXTM3U\n#EXTINF:6,\nseg-1.ts\n"))
        assertNull(certainHlsPack("#EXTM3U\n#EXT-X-STREAM-INF:BANDWIDTH=1\nvideo.m3u8\n"))
    }
}
