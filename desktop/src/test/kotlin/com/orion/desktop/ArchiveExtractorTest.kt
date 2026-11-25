package com.orion.desktop

import kotlinx.coroutines.runBlocking
import org.junit.Test
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ArchiveExtractorTest {
    private fun tempDir(prefix: String) =
        File(System.getProperty("java.io.tmpdir"), "orion-test-$prefix-${System.nanoTime()}").apply { mkdirs() }

    @Test
    fun `zip slip is rejected and nothing escapes the destination`() = runBlocking {
        val root = tempDir("slip")
        val zip = File(root, "evil.zip")
        ZipOutputStream(zip.outputStream()).use { out ->
            out.putNextEntry(ZipEntry("../escaped.txt"))
            out.write("nope".toByteArray())
            out.closeEntry()
        }
        val dest = File(root, "dest").apply { mkdirs() }

        val error = assertFailsWith<Exception> { ArchiveExtractor.extractZip(zip, dest) }
        assertTrue(error.message.orEmpty().contains("unsafe", ignoreCase = true))
        assertFalse(File(root, "escaped.txt").exists())
    }

    @Test
    fun `extracts a nested zip and picks the real executable`() = runBlocking {
        val root = tempDir("extract")
        val zip = File(root, "app.zip")
        ZipOutputStream(zip.outputStream()).use { out ->
            fun entry(name: String, text: String = "x") {
                out.putNextEntry(ZipEntry(name))
                out.write(text.toByteArray())
                out.closeEntry()
            }
            entry("MyApp.exe")
            entry("tools/uninstall.exe")
            entry("docs/readme.txt")
        }
        val dest = File(root, "dest")

        ArchiveExtractor.extractZip(zip, dest)

        assertTrue(File(dest, "MyApp.exe").isFile)
        assertTrue(File(dest, "docs/readme.txt").isFile)
        assertEquals("MyApp.exe", ArchiveExtractor.findEntryPoint(dest)?.name)
    }

    @Test
    fun `tools directory slug is stable and safe`() {
        val dir = ArchiveExtractor.toolsDir("../../Some App: Edition")
        assertEquals("some-app-edition", dir.name)
        assertTrue(dir.canonicalPath.startsWith(DesktopCatalogRepository.orionDir.canonicalPath))
    }
}
