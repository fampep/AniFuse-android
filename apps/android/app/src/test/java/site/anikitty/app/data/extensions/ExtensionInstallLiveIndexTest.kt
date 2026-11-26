// Copyright 2026 fampep
// Licensed under the Apache License, Version 2.0. See the LICENSE file.

package site.anikitty.app.data.extensions

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The extensions repository pin used to be fetched live. That repo answers
 * 404, which failed every APK build. The contract is checked from a fixture.
 */
class ExtensionInstallLiveIndexTest {
    @Test
    fun indexParsesVersionProtocolAndMkissa() {
        val index = parseStoreIndex(INDEX, sourceUrl = "fixture://store/index.json")
        val ids = index.extensions.map { it.id }
        assertEquals(
            listOf("anikoto", "gotaku", "reanime", "mkissa", "site.anikitty.example.hello"),
            ids,
        )
        val mkissa = index.extensions.first { it.id == "mkissa" }
        assertEquals("1.0.0", mkissa.version)
        assertEquals("1.1", mkissa.protocol)
        assertEquals("1.1", index.extensions.first { it.id == "anikoto" }.protocol)
        assertTrue(index.extensions.all { it.version != null && it.protocol == "1.1" })
    }

    @Test
    fun bundledProvidersStayOnDeviceAndHelloIsInstallable() {
        val index = parseStoreIndex(INDEX, sourceUrl = "fixture://store/index.json")
        val missing = ArrayList<String>()
        for (listing in index.extensions) {
            val plan = prepareStoreInstall(listing)
            val url = manifestUrls(listing.github, listing.manifestPath).first()
            assertTrue(url.startsWith("https://"))
            if (listing.id in BUNDLED_EXTENSION_IDS) {
                assertTrue(plan is StoreInstall.OnDevice)
                val failure = ExtensionFetchException(404, url)
                val message = explainInstallFailure(failure)
                assertTrue(message, message.contains("ships with AniFuse"))
                assertTrue(message, message.contains("anikitty-extension.json"))
                assertFalse(message.contains("HTTP 404"))
                missing += listing.id
            } else {
                assertTrue(plan is StoreInstall.Fetch)
                val manifest = ExtensionManifestParser.parse(HELLO, listing.github, url)
                assertEquals(listing.id, manifest.id)
                assertEquals(listing.version, manifest.version)
                assertFalse(isNewerVersion(manifest.version, listing.version!!))
            }
        }
        assertEquals(BUNDLED_EXTENSION_IDS, missing.toSet())
    }

    @Test
    fun blankFailureStillSaysWhatHappened() {
        val message = explainInstallFailure(IllegalStateException())
        assertEquals("Could not install this extension.", message)
    }

    private companion object {
        val INDEX = """
            {
              "apiVersion": 1,
              "protocol": "1.1",
              "updatedAt": "2026-01-01",
              "extensions": [
                {
                  "id": "anikoto",
                  "name": "Anikoto",
                  "github": "https://github.com/fampep/anikitty-extensions/tree/main/extensions/providers/anikoto",
                  "version": "1.0.0",
                  "protocol": "1.1"
                },
                {
                  "id": "gotaku",
                  "name": "Gotaku",
                  "github": "https://github.com/fampep/anikitty-extensions/tree/main/extensions/providers/gotaku",
                  "version": "1.0.0",
                  "protocol": "1.1"
                },
                {
                  "id": "reanime",
                  "name": "Reanime",
                  "github": "https://github.com/fampep/anikitty-extensions/tree/main/extensions/providers/reanime",
                  "version": "1.0.0",
                  "protocol": "1.1"
                },
                {
                  "id": "mkissa",
                  "name": "Mkissa",
                  "github": "https://github.com/fampep/anikitty-extensions/tree/main/extensions/providers/mkissa",
                  "version": "1.0.0",
                  "protocol": "1.1"
                },
                {
                  "id": "site.anikitty.example.hello",
                  "name": "Hello HTTP",
                  "github": "https://github.com/fampep/anikitty/tree/main/extensions/examples/hello-http",
                  "manifestPath": "extensions/examples/hello-http/anikitty-extension.json",
                  "version": "1.1.1",
                  "protocol": "1.1"
                }
              ]
            }
        """.trimIndent()

        val HELLO = """
            {
              "apiVersion": 1,
              "id": "site.anikitty.example.hello",
              "name": "Hello HTTP",
              "version": "1.1.1",
              "description": "Minimal BYO resolve example",
              "author": "fampep",
              "baseUrl": "https://example.invalid",
              "resolvePath": "/v1/resolve",
              "iconUrl": "https://example.invalid/icon.png",
              "siteUrl": "https://example.com"
            }
        """.trimIndent()
    }
}
