package com.orion.desktop

import com.orion.store.data.AppItem
import com.orion.store.data.Platform
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ReleaseResolverTest {
    @Test
    fun `github release keeps windows assets and drops checksums and sig files`() {
        val body = """
            {
              "tag_name": "v2.20.0",
              "body": "Desktop release",
              "assets": [
                {"name": "Optimaxer-v2.20.0.zip", "browser_download_url": "https://example.com/Optimaxer-v2.20.0.zip", "size": 5060000},
                {"name": "optimaxer-linux.py", "browser_download_url": "https://example.com/optimaxer-linux.py", "size": 130000},
                {"name": "release-notes.md", "browser_download_url": "https://example.com/release-notes.md", "size": 900},
                {"name": "SHA256SUMS.txt", "browser_download_url": "https://example.com/SHA256SUMS.txt", "size": 300},
                {"name": "OmniSearch_0.1.16_x64-setup.exe.sig", "browser_download_url": "https://example.com/setup.exe.sig", "size": 0},
                {"name": "OmniSearch_0.1.16_x64-setup.exe", "browser_download_url": "https://example.com/OmniSearch_0.1.16_x64-setup.exe", "size": 5340000}
              ]
            }
        """.trimIndent()

        val release = assertNotNull(ReleaseResolver.parseGitHub(body, Platform.PC))
        assertEquals("v2.20.0", release.tag)
        assertEquals("Desktop release", release.notes)
        assertEquals("GitHub", release.host)
        assertEquals(2, release.assets.size)
        assertEquals("OmniSearch_0.1.16_x64-setup.exe", release.assets[0].name)
        assertEquals("x64", release.assets[0].arch)
        assertEquals(5340000L, release.assets[0].size)
        assertEquals("Optimaxer-v2.20.0.zip", release.assets[1].name)
    }

    @Test
    fun `gitlab release links are normalised and direct asset urls win`() {
        val body = """
            [
              {
                "tag_name": "v3.1.0",
                "description": "Fixes",
                "assets": {
                  "links": [
                    {"name": "Nebula-3.1.0-x64.exe", "url": "https://gitlab.com/api/v4/projects/1/releases/v3.1.0/assets/links/1/download", "direct_asset_url": "https://gitlab.com/acme/nebula/-/releases/v3.1.0/downloads/Nebula-3.1.0-x64.exe", "size": 4200000},
                    {"name": "Nebula-checksums.txt", "url": "https://gitlab.com/acme/nebula/-/releases/v3.1.0/downloads/checksums.txt"},
                    {"name": "Nebula-3.1.0-x64.AppImage", "url": "https://gitlab.com/acme/nebula/-/releases/v3.1.0/downloads/Nebula.AppImage"}
                  ]
                }
              }
            ]
        """.trimIndent()

        val release = assertNotNull(ReleaseResolver.parseGitLab(body, Platform.PC))
        assertEquals("v3.1.0", release.tag)
        assertEquals("GitLab", release.host)
        assertEquals(1, release.assets.size)
        assertEquals(
            "https://gitlab.com/acme/nebula/-/releases/v3.1.0/downloads/Nebula-3.1.0-x64.exe",
            release.assets[0].url
        )
        assertEquals("x64", release.assets[0].arch)
    }

    @Test
    fun `codeberg release prefers msi over zip and skips hash files`() {
        val body = """
            {
              "tag_name": "v1.4.2",
              "body": "Release body",
              "assets": [
                {"name": "Kite-1.4.2-win64.zip", "browser_download_url": "https://codeberg.org/acme/kite/releases/download/v1.4.2/Kite-win64.zip", "size": 8000000},
                {"name": "Kite-1.4.2-win64.zip.sha256", "browser_download_url": "https://codeberg.org/acme/kite/releases/download/v1.4.2/Kite-win64.zip.sha256", "size": 64},
                {"name": "Kite-1.4.2-arm64.msi", "browser_download_url": "https://codeberg.org/acme/kite/releases/download/v1.4.2/Kite-arm64.msi", "size": 9000000}
              ]
            }
        """.trimIndent()

        val release = assertNotNull(ReleaseResolver.parseCodeberg(body, Platform.PC))
        assertEquals("Codeberg", release.host)
        assertEquals(2, release.assets.size)
        assertEquals("Kite-1.4.2-arm64.msi", release.assets[0].name)
        assertEquals("ARM64", release.assets[0].arch)
    }

    @Test
    fun `android apps only get apk assets and pc apps never do`() {
        val body = """
            {
              "tag_name": "v2.1.7",
              "assets": [
                {"name": "app-release.apk", "browser_download_url": "https://example.com/app-release.apk", "size": 133000000}
              ]
            }
        """.trimIndent()

        val pc = assertNotNull(ReleaseResolver.parseGitHub(body, Platform.PC))
        assertTrue(pc.assets.isEmpty())

        val android = assertNotNull(ReleaseResolver.parseGitHub(body, Platform.ANDROID))
        assertEquals(1, android.assets.size)
        assertEquals("app-release.apk", android.assets[0].name)
    }

    @Test
    fun `repo refs cover github gitlab codeberg and full urls without duplicates`() {
        val app = AppItem(
            name = "Test App",
            platform = Platform.PC,
            githubRepo = "owner/repo",
            gitlabRepo = "https://gitlab.example.com/group/sub/project.git",
            codebergRepo = "user/code",
            repoUrl = "https://github.com/owner/repo"
        )

        val refs = ReleaseResolver.repoRefs(app)
        assertEquals(3, refs.size)
        assertTrue(refs.any { it.host == "github.com" && it.path == "owner/repo" && it.kind == ReleaseResolver.RepoKind.GITHUB })
        assertTrue(refs.any { it.host == "gitlab.example.com" && it.path == "group/sub/project" && it.kind == ReleaseResolver.RepoKind.GITLAB })
        assertTrue(refs.any { it.host == "codeberg.org" && it.path == "user/code" && it.kind == ReleaseResolver.RepoKind.CODEBERG })
    }

    @Test
    fun `community release is narrowed to the catalog keyword`() {
        val body = """
            [
              {
                "tag_name": "latest",
                "name": "Morphe community builds",
                "assets": {
                  "links": [
                    {"name": "youtube-universal-morphe-patches-v20.0.0.apk", "direct_asset_url": "https://gitlab.com/x/youtube.apk"},
                    {"name": "music-universal-morphe-patches-v8.0.0.apk", "direct_asset_url": "https://gitlab.com/x/music.apk"},
                    {"name": "tiktok-universal-morphe-patches-v40.0.0.apk", "direct_asset_url": "https://gitlab.com/x/tiktok.apk"}
                  ]
                }
              }
            ]
        """.trimIndent()

        val release = assertNotNull(ReleaseResolver.parseGitLab(body, Platform.ANDROID, "youtube-universal"))
        assertEquals(1, release.assets.size)
        assertEquals("youtube-universal-morphe-patches-v20.0.0.apk", release.assets[0].name)
    }

    @Test
    fun `keyword that matches neither assets nor release name hides unrelated files`() {
        val body = """
            {
              "tag_name": "v1.0.0",
              "name": "Something else",
              "assets": [
                {"name": "other-app.apk", "browser_download_url": "https://example.com/other-app.apk"}
              ]
            }
        """.trimIndent()

        val release = assertNotNull(ReleaseResolver.parseGitHub(body, Platform.ANDROID, "youtube-universal"))
        assertTrue(release.assets.isEmpty())
    }

    @Test
    fun `keyword in release name keeps every platform asset`() {
        val body = """
            {
              "tag_name": "v1.0.0",
              "name": "Nebula desktop build",
              "assets": [
                {"name": "Nebula-x64.exe", "browser_download_url": "https://example.com/Nebula-x64.exe"},
                {"name": "Nebula-arm64.exe", "browser_download_url": "https://example.com/Nebula-arm64.exe"}
              ]
            }
        """.trimIndent()

        val release = assertNotNull(ReleaseResolver.parseGitHub(body, Platform.PC, "desktop"))
        assertEquals(2, release.assets.size)
    }

    @Test
    fun `invalid json does not throw`() {
        assertNull(ReleaseResolver.parseGitHub("not json", Platform.PC))
        assertNull(ReleaseResolver.parseGitLab("{}", Platform.PC))
        assertNull(ReleaseResolver.parseCodeberg("[]", Platform.PC))
    }
}
