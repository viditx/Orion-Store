package com.orion.desktop

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.awt.Desktop
import java.io.File
import java.io.IOException
import java.net.URI
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.util.Locale
import java.util.concurrent.TimeUnit

enum class InstallPhase { CONNECTING, DOWNLOADING, EXTRACTING, LAUNCHING, DONE }

data class DownloadProgress(
    val phase: InstallPhase,
    val bytesDownloaded: Long = 0,
    val totalBytes: Long = -1,
    val bytesPerSecond: Long = 0,
    val label: String = ""
) {
    /** Null when the server did not send a Content-Length. */
    val fraction: Float? get() = if (totalBytes > 0) (bytesDownloaded.toFloat() / totalBytes).coerceIn(0f, 1f) else null
}

data class InstallResult(
    val file: File,
    val installDir: File? = null,
    val shortcut: File? = null,
    val launched: Boolean = false,
    val message: String
)

object DesktopInstaller {
    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .followRedirects(true)
        .build()

    private val UNSAFE_NAME = Regex("[<>:\"/\\\\|?*\\u0000-\\u001F]")

    fun openBrowser(url: String) {
        runCatching {
            if (Desktop.isDesktopSupported() && Desktop.getDesktop().isSupported(Desktop.Action.BROWSE)) {
                Desktop.getDesktop().browse(URI(url))
            } else {
                ProcessBuilder("rundll32.exe", "url.dll,FileProtocolHandler", url).start()
            }
        }.onFailure { it.printStackTrace() }
    }

    fun openFile(file: File) {
        runCatching {
            if (Desktop.isDesktopSupported() && Desktop.getDesktop().isSupported(Desktop.Action.OPEN)) {
                Desktop.getDesktop().open(file)
            } else {
                ProcessBuilder("rundll32", "url.dll,FileProtocolHandler", file.absolutePath).start()
            }
        }.onFailure { it.printStackTrace() }
    }

    fun revealInExplorer(file: File) {
        runCatching {
            if (file.isDirectory) ProcessBuilder("explorer.exe", file.absolutePath).start()
            else ProcessBuilder("explorer.exe", "/select,${file.absolutePath}").start()
        }.onFailure { openFile(file) }
    }

    /**
     * Streams [url] into the Orion temp folder and reports real byte progress.
     * Kept separate from installation so it can be tested (and reused) without
     * launching anything on the host machine.
     */
    suspend fun download(
        url: String,
        filename: String,
        onProgress: suspend (DownloadProgress) -> Unit
    ): Result<File> = withContext(Dispatchers.IO) {
        runCatching {
            val safeName = safeFilename(filename, url)
            onProgress(DownloadProgress(InstallPhase.CONNECTING, label = safeName))

            val request = Request.Builder()
                .url(url)
                .header("User-Agent", "OrionStoreDesktop/2.0 (Windows)")
                .build()

            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) throw IOException("HTTP ${response.code} ${response.message}")
                val body = response.body ?: throw IOException("Empty response body")
                val total = body.contentLength()
                val dir = DesktopCatalogRepository.downloadsDir
                val target = File(dir, safeName)
                val part = File(dir, "$safeName.part")
                part.delete()

                var downloaded = 0L
                var windowBytes = 0L
                var windowStart = System.nanoTime()
                var speed = 0L
                var lastEmit = 0L

                body.byteStream().use { input ->
                    part.outputStream().use { output ->
                        val buffer = ByteArray(64 * 1024)
                        while (true) {
                            currentCoroutineContext().ensureActive()
                            val read = input.read(buffer)
                            if (read < 0) break
                            output.write(buffer, 0, read)
                            downloaded += read
                            val now = System.nanoTime()
                            if (now - windowStart >= 250_000_000L) {
                                val instant = (downloaded - windowBytes) * 1_000_000_000L / (now - windowStart).coerceAtLeast(1)
                                speed = if (speed == 0L) instant else (speed * 7 + instant * 3) / 10
                                windowBytes = downloaded
                                windowStart = now
                            }
                            if (now - lastEmit >= 100_000_000L || (total > 0 && downloaded >= total)) {
                                lastEmit = now
                                onProgress(DownloadProgress(InstallPhase.DOWNLOADING, downloaded, total, speed))
                            }
                        }
                    }
                }

                if (downloaded <= 0L) {
                    part.delete()
                    throw IOException("The download was empty")
                }
                if (total > 0 && downloaded < total) {
                    part.delete()
                    throw IOException("Incomplete download (${formatBytes(downloaded)} of ${formatBytes(total)})")
                }
                verifyDownload(part, safeName)
                Files.move(part.toPath(), target.toPath(), StandardCopyOption.REPLACE_EXISTING)
                onProgress(DownloadProgress(InstallPhase.DOWNLOADING, downloaded, if (total > 0) total else downloaded, speed))
                target
            }
        }
    }

    /**
     * Downloads [url], then hands the artifact to the right Windows install path.
     * Archives are extracted to `~/.orion/tools/<app>` and get a desktop shortcut
     * instead of being "launched".
     */
    suspend fun downloadAndInstall(
        url: String,
        filename: String,
        appName: String,
        silent: Boolean = false,
        onProgress: suspend (DownloadProgress) -> Unit
    ): Result<InstallResult> {
        val downloaded = download(url, filename, onProgress).getOrElse { return Result.failure(it) }
        return withContext(Dispatchers.IO) {
            runCatching { installDownloaded(downloaded, appName, silent, onProgress) }
        }
    }

    private suspend fun installDownloaded(
        file: File,
        appName: String,
        silent: Boolean,
        onProgress: suspend (DownloadProgress) -> Unit
    ): InstallResult {
        val extension = file.extension.lowercase(Locale.US)
        return when (extension) {
            "zip", "7z" -> {
                onProgress(DownloadProgress(InstallPhase.EXTRACTING, label = "Extracting ${file.name}"))
                val dest = ArchiveExtractor.toolsDir(appName)
                if (extension == "zip") {
                    ArchiveExtractor.extractZip(file, dest) { fraction ->
                        onProgress(DownloadProgress(InstallPhase.EXTRACTING, bytesDownloaded = (fraction * 1000).toLong(), totalBytes = 1000, label = "Extracting"))
                    }
                } else {
                    ArchiveExtractor.extract7z(file, dest) { fraction ->
                        onProgress(DownloadProgress(InstallPhase.EXTRACTING, bytesDownloaded = (fraction * 1000).toLong(), totalBytes = 1000, label = "Extracting"))
                    }
                }
                val entry = ArchiveExtractor.findEntryPoint(dest)
                val shortcut = entry?.let { ArchiveExtractor.createDesktopShortcut(it, appName) }
                val message = buildString {
                    append("Extracted to ${dest.absolutePath}")
                    if (shortcut != null) append(" · desktop shortcut created")
                    else if (entry == null) append(" · no .exe found, open the folder to inspect it")
                }
                InstallResult(entry ?: dest, dest, shortcut, launched = false, message = message)
            }

            "msi" -> {
                onProgress(DownloadProgress(InstallPhase.LAUNCHING, label = "Opening installer"))
                val command = mutableListOf("msiexec", "/i", file.absolutePath)
                if (silent) command += listOf("/quiet", "/norestart")
                ProcessBuilder(command).start()
                InstallResult(file, launched = true, message = if (silent) "MSI installed silently" else "MSI installer opened")
            }

            "msix", "msixbundle", "appx", "appxbundle" -> {
                onProgress(DownloadProgress(InstallPhase.LAUNCHING, label = "Installing package"))
                val result = runPowerShell("Add-AppxPackage -Path '${psEscape(file.absolutePath)}'")
                if (result.first != 0) throw IOException("Package install failed${result.second.takeIf { it.isNotBlank() }?.let { ": $it" } ?: ""}")
                InstallResult(file, launched = true, message = "Package installed")
            }

            "exe" -> {
                onProgress(DownloadProgress(InstallPhase.LAUNCHING, label = "Opening installer"))
                val flavor = installerFlavor(file)
                val args = if (silent) silentArgs(flavor) else emptyList()
                val command = mutableListOf(file.absolutePath).apply { addAll(args) }
                ProcessBuilder(command).directory(file.parentFile).start()
                val note = when {
                    !silent -> "Installer opened"
                    flavor == "unknown" -> "Installer opened (unknown packaging, so silent flags were skipped)"
                    else -> "Installer started silently"
                }
                InstallResult(file, launched = true, message = note)
            }

            "ps1" -> {
                onProgress(DownloadProgress(InstallPhase.LAUNCHING, label = "Running PowerShell script"))
                ProcessBuilder(
                    "powershell.exe", "-NoProfile", "-ExecutionPolicy", "Bypass", "-File", file.absolutePath
                ).directory(file.parentFile).start()
                InstallResult(file, launched = true, message = "PowerShell installer opened")
            }

            else -> {
                revealInExplorer(file)
                InstallResult(file, launched = false, message = "Downloaded ${file.name}")
            }
        }
    }

    private fun installerFlavor(file: File): String = runCatching {
        val head = file.inputStream().use { it.readNBytes(2 * 1024 * 1024) }
        val text = String(head, Charsets.ISO_8859_1)
        when {
            text.contains("Inno Setup", ignoreCase = true) -> "inno"
            text.contains("Nullsoft", ignoreCase = true) || text.contains("NSIS", ignoreCase = true) -> "nsis"
            else -> "unknown"
        }
    }.getOrDefault("unknown")

    private fun silentArgs(flavor: String): List<String> = when (flavor) {
        "inno" -> listOf("/VERYSILENT", "/SUPPRESSMSGBOXES", "/NORESTART")
        "nsis" -> listOf("/S")
        else -> emptyList()
    }

    private fun verifyDownload(file: File, name: String) {
        if (file.length() == 0L) throw IOException("The downloaded file is empty")
        val extension = name.substringAfterLast('.', "").lowercase(Locale.US)
        if (extension !in setOf("exe", "msi", "msix", "msixbundle", "appx", "appxbundle")) return
        val head = file.inputStream().use { it.readNBytes(512) }
        val text = String(head, Charsets.ISO_8859_1).trimStart().lowercase(Locale.US)
        if (text.startsWith("<!doctype html") || text.startsWith("<html")) {
            file.delete()
            throw IOException("The server returned an HTML page instead of an installer")
        }
    }

    private fun safeFilename(raw: String, url: String): String {
        var name = raw.substringAfterLast('/').substringAfterLast('\\').trim()
        if (name.isBlank() || name == "." || name == "..") {
            name = url.substringBefore('?').substringAfterLast('/').ifBlank { "download.bin" }
        }
        name = name.replace(UNSAFE_NAME, "_")
        if (!name.contains('.')) {
            val ext = url.substringBefore('?').substringAfterLast('.', "")
            if (ext.length in 1..5) name = "$name.$ext"
        }
        if (name.length > 160) {
            val ext = name.substringAfterLast('.', "")
            name = name.take(150) + if (ext.length in 1..5) ".$ext" else ""
        }
        return name.ifBlank { "download.bin" }
    }

    private fun psEscape(value: String) = value.replace("'", "''")

    private fun runPowerShell(script: String): Pair<Int, String> {
        val process = ProcessBuilder(
            "powershell.exe", "-NoProfile", "-NonInteractive", "-ExecutionPolicy", "Bypass", "-Command", script
        ).redirectErrorStream(true).start()
        val output = process.inputStream.bufferedReader().readText().trim()
        if (!process.waitFor(5, TimeUnit.MINUTES)) {
            process.destroyForcibly()
            return 1 to "PowerShell timed out"
        }
        return process.exitValue() to output.takeLast(600)
    }
}
