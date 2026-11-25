package com.orion.desktop

import com.sun.net.httpserver.HttpServer
import kotlinx.coroutines.runBlocking
import org.junit.Test
import java.io.File
import java.net.InetSocketAddress
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class DesktopInstallerTest {
    private fun server(handler: (com.sun.net.httpserver.HttpExchange) -> Unit): HttpServer =
        HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0).apply {
            createContext("/") { exchange -> handler(exchange) }
            start()
        }

    @Test
    fun `streams a file and reports real byte progress`() = runBlocking {
        val payload = ByteArray(512 * 1024) { (it % 251).toByte() }
        val http = server { exchange ->
            exchange.sendResponseHeaders(200, payload.size.toLong())
            exchange.responseBody.use { it.write(payload) }
        }
        try {
            val events = mutableListOf<DownloadProgress>()
            val name = "orion-progress-${System.nanoTime()}.bin"
            val result = DesktopInstaller.download("http://127.0.0.1:${http.address.port}/file.bin", name) { events += it }
            val file = result.getOrThrow()

            assertEquals(payload.size.toLong(), file.length())
            assertTrue(events.any { it.phase == InstallPhase.DOWNLOADING })
            val last = events.last { it.phase == InstallPhase.DOWNLOADING }
            assertEquals(payload.size.toLong(), last.totalBytes)
            assertEquals(1f, last.fraction ?: 0f, 0.001f)
            file.delete()
        } finally {
            http.stop(0)
        }
        Unit
    }

    @Test
    fun `rejects an html page disguised as an installer`() = runBlocking {
        val body = "<!doctype html><html><body>not a binary</body></html>".toByteArray()
        val http = server { exchange ->
            exchange.sendResponseHeaders(200, body.size.toLong())
            exchange.responseBody.use { it.write(body) }
        }
        try {
            val result = DesktopInstaller.download(
                "http://127.0.0.1:${http.address.port}/fake.exe",
                "orion-fake-${System.nanoTime()}.exe"
            ) {}
            assertTrue(result.isFailure)
            assertTrue(result.exceptionOrNull()?.message.orEmpty().contains("HTML", ignoreCase = true))
        } finally {
            http.stop(0)
        }
    }

    @Test
    fun `path traversal in a filename stays inside the download folder`() = runBlocking {
        val payload = "ok".toByteArray()
        val http = server { exchange ->
            exchange.sendResponseHeaders(200, payload.size.toLong())
            exchange.responseBody.use { it.write(payload) }
        }
        try {
            val unique = "orion-path-${System.nanoTime()}.bin"
            val result = DesktopInstaller.download(
                "http://127.0.0.1:${http.address.port}/payload.bin",
                "../../$unique"
            ) {}
            val file = result.getOrThrow()
            assertEquals(DesktopCatalogRepository.downloadsDir.canonicalPath, file.parentFile.canonicalPath)
            assertTrue(file.name.contains("orion-path-"))
            file.delete()
        } finally {
            http.stop(0)
        }
        Unit
    }
}
