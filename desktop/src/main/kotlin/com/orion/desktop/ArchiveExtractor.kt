package com.orion.desktop

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException
import java.util.Locale
import java.util.concurrent.TimeUnit
import java.util.zip.ZipFile

/**
 * Portable-archive handling for the desktop store.
 *
 * ZIPs are extracted with the JDK. 7z has no JDK reader, so we shell out to a locally
 * installed 7-Zip and fall back to Windows' bundled bsdtar (which can read 7z).
 * Every entry is canonicalised before writing so a malicious archive cannot escape
 * its install directory.
 */
object ArchiveExtractor {
    private const val MAX_ENTRIES = 20_000
    private const val MAX_UNCOMPRESSED = 8L * 1024 * 1024 * 1024
    private val ILLEGAL_WINDOWS_CHARS = Regex("[\\\\/:*?\"<>|]")
    private val SHORTCUT_BAD_WORDS = listOf(
        "unins", "uninstall", "setup", "install", "update", "updater",
        "crash", "helper", "vc_redist", "dotnet"
    )

    fun toolsDir(appName: String): File {
        val slug = appName.lowercase(Locale.US)
            .replace(Regex("[^a-z0-9]+"), "-")
            .trim('-')
            .ifBlank { "app" }
            .take(60)
        return File(DesktopCatalogRepository.orionDir, "tools/$slug").apply { mkdirs() }
    }

    suspend fun extractZip(
        zip: File,
        dest: File,
        onProgress: suspend (Float) -> Unit = {}
    ): Unit = withContext(Dispatchers.IO) {
        dest.mkdirs()
        val destRoot = dest.canonicalFile
        var written = 0L
        var done = 0
        ZipFile(zip).use { zf ->
            val total = zf.size().coerceAtLeast(1)
            val entries = zf.entries()
            while (entries.hasMoreElements()) {
                currentCoroutineContext().ensureActive()
                val entry = entries.nextElement()
                if (entry.isDirectory) continue
                val name = entry.name
                if (name.startsWith("__MACOSX/") || name.endsWith("/.DS_Store") || name == ".DS_Store") continue
                if (name.startsWith("/") || name.startsWith("\\") || name.contains(':')) {
                    throw IOException("Blocked unsafe path in archive: $name")
                }
                val out = File(destRoot, name).canonicalFile
                if (out.path != destRoot.path && !out.path.startsWith(destRoot.path + File.separator)) {
                    throw IOException("Blocked unsafe path in archive: $name")
                }
                out.parentFile?.mkdirs()
                zf.getInputStream(entry).use { input ->
                    out.outputStream().use { output ->
                        val buffer = ByteArray(64 * 1024)
                        while (true) {
                            val read = input.read(buffer)
                            if (read < 0) break
                            written += read
                            if (written > MAX_UNCOMPRESSED) throw IOException("Archive exceeds the 8 GB extraction limit")
                            output.write(buffer, 0, read)
                        }
                    }
                }
                done++
                if (done > MAX_ENTRIES) throw IOException("Archive contains more than $MAX_ENTRIES files")
                if (done % 8 == 0 || done == total) onProgress((done.toFloat() / total).coerceIn(0f, 1f))
            }
        }
        onProgress(1f)
    }

    suspend fun extract7z(
        archive: File,
        dest: File,
        onProgress: suspend (Float) -> Unit = {}
    ): Unit = withContext(Dispatchers.IO) {
        dest.mkdirs()
        onProgress(0.05f)
        val sevenZip = find7Zip()
        val result = if (sevenZip != null) {
            runProcess(listOf(sevenZip, "x", archive.absolutePath, "-o${dest.absolutePath}", "-y", "-bsp1"), 10)
        } else {
            // Windows 10/11 ship bsdtar as tar.exe; libarchive can read 7z archives.
            runProcess(listOf("tar.exe", "-xf", archive.absolutePath, "-C", dest.absolutePath), 10)
        }
        if (result.exitCode != 0) {
            val hint = if (sevenZip == null) " Install 7-Zip and retry, or download the .zip build." else ""
            throw IOException("Could not extract .7z${result.output.takeLast(240).let { if (it.isBlank()) "" else ": $it" }}.$hint")
        }
        onProgress(1f)
    }

    /** Best-effort entry point for a portable app: the shallowest .exe that is not an uninstaller/updater. */
    fun findEntryPoint(dir: File): File? {
        val all = dir.walkTopDown()
            .maxDepth(3)
            .filter { it.isFile && it.extension.equals("exe", true) }
            .toList()
        if (all.isEmpty()) return null
        val preferred = all.filterNot { file ->
            val lower = file.name.lowercase(Locale.US)
            SHORTCUT_BAD_WORDS.any { it in lower }
        }
        return (preferred.ifEmpty { all }).minWithOrNull(
            compareBy(
                { it.relativeTo(dir).invariantSeparatorsPath.count { c -> c == '/' } },
                { it.name.length }
            )
        )
    }

    /** Creates a .lnk on the real desktop (honours OneDrive redirection) via WScript.Shell. */
    fun createDesktopShortcut(target: File, appName: String): File? {
        if (!target.exists()) return null
        val shortcutName = appName.replace(ILLEGAL_WINDOWS_CHARS, "").trim().take(70).ifBlank { "Orion App" }
        val script = """
            ${'$'}desktop = [Environment]::GetFolderPath('Desktop')
            if (-not ${'$'}desktop) { exit 1 }
            ${'$'}lnk = Join-Path ${'$'}desktop '${psEscape(shortcutName)}.lnk'
            ${'$'}shell = New-Object -ComObject WScript.Shell
            ${'$'}shortcut = ${'$'}shell.CreateShortcut(${'$'}lnk)
            ${'$'}shortcut.TargetPath = '${psEscape(target.absolutePath)}'
            ${'$'}shortcut.WorkingDirectory = '${psEscape(target.parentFile?.absolutePath.orEmpty())}'
            ${'$'}shortcut.IconLocation = '${psEscape(target.absolutePath)},0'
            ${'$'}shortcut.Description = '${psEscape(appName)}'
            ${'$'}shortcut.Save()
            Write-Output ${'$'}lnk
        """.trimIndent()
        val result = runProcess(
            listOf("powershell.exe", "-NoProfile", "-NonInteractive", "-ExecutionPolicy", "Bypass", "-Command", script),
            timeoutMinutes = 1
        )
        if (result.exitCode != 0) return null
        val path = result.output.lineSequence().map { it.trim() }.lastOrNull { it.endsWith(".lnk", true) } ?: return null
        return File(path).takeIf { it.exists() }
    }

    private fun psEscape(value: String) = value.replace("'", "''")

    private fun find7Zip(): String? {
        listOf(
            "C:\\Program Files\\7-Zip\\7z.exe",
            "C:\\Program Files (x86)\\7-Zip\\7z.exe"
        ).firstOrNull { File(it).isFile }?.let { return it }
        return runCatching {
            val process = ProcessBuilder("where.exe", "7z.exe").redirectErrorStream(true).start()
            val line = process.inputStream.bufferedReader().readLine()?.trim()
            process.waitFor(5, TimeUnit.SECONDS)
            line?.takeIf { it.isNotBlank() && File(it).isFile }
        }.getOrNull()
    }

    private data class ProcessResult(val exitCode: Int, val output: String)

    private fun runProcess(command: List<String>, timeoutMinutes: Long): ProcessResult {
        val process = ProcessBuilder(command).redirectErrorStream(true).start()
        val output = process.inputStream.bufferedReader().readText()
        if (!process.waitFor(timeoutMinutes, TimeUnit.MINUTES)) {
            process.destroyForcibly()
            throw IOException("${command.first()} timed out")
        }
        return ProcessResult(process.exitValue(), output)
    }
}
