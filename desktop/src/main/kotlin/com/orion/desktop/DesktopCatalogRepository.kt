package com.orion.desktop

import androidx.compose.ui.graphics.Color
import com.orion.store.data.AppItem
import com.orion.store.data.CatalogProcessor
import com.orion.store.data.Defaults
import com.orion.store.data.StoreConfig
import com.orion.store.data.json
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import okhttp3.OkHttpClient
import okhttp3.Request
import java.awt.image.BufferedImage
import java.io.ByteArrayInputStream
import java.io.File
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
import javax.imageio.ImageIO

data class DesktopCatalogResult(
    val apps: List<AppItem>,
    val config: StoreConfig?,
    val sourceLabel: String
)

@Serializable
data class DesktopPrefs(
    val paletteId: String = "orion",
    val themeMode: String = "dark",
    val favorites: Set<String> = emptySet(),
    val compactMode: Boolean = false,
    val storeLayout: String = "modern",
    val glassDock: Boolean = true,
    val dockScale: Float = 1.0f,
    /** Dock look: pill, labels, shapes, bar or vibrant (the styles Orion ships on Android). */
    val dockStyle: String = "pill",
    /** Where navigation lives on a wide desktop window: rail, bottom or top. */
    val wideNav: String = "bottom",
    val smoothMotion: Boolean = true,
    val silentInstall: Boolean = false,
    val stableOnly: Boolean = false,
    val autoCleanup: Boolean = false,
    val showAppAbout: Boolean = true,
    val quickTiles: List<String> = listOf("autoCleanup", "compact", "stableOnly", "oled", "glass", "smooth")
)

@Serializable
data class InstallStats(
    val installs: Int = 0,
    val portable: Int = 0,
    val lastApp: String? = null
)

object DesktopCatalogRepository {
    private val client = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(35, TimeUnit.SECONDS)
        .callTimeout(45, TimeUnit.SECONDS)
        .build()

    val orionDir: File
        get() = File(System.getProperty("user.home"), ".orion").apply { mkdirs() }

    val downloadsDir: File
        get() = File(System.getProperty("java.io.tmpdir"), "OrionDownloads").apply { mkdirs() }

    private val appsCacheFile get() = File(orionDir, "apps.json")
    private val configCacheFile get() = File(orionDir, "config.json")
    private val starsCacheFile get() = File(orionDir, "stars.json")
    private val mirrorCacheFile get() = File(orionDir, "mirror.json")
    private val prefsFile get() = File(orionDir, "desktop_prefs.json")
    private val statsFile get() = File(orionDir, "stats.json")

    fun pendingInstallerFiles(): List<File> = downloadsDir.listFiles()?.toList().orEmpty()
    fun clearPendingInstallers(): Int {
        val files = pendingInstallerFiles()
        var n = 0
        files.forEach { if (it.delete()) n++ }
        return n
    }
    fun clearCatalogCache() {
        listOf(appsCacheFile, configCacheFile, starsCacheFile, mirrorCacheFile).forEach { it.delete() }
    }

    private val iconColorCache = ConcurrentHashMap<String, Color>()
    private val failedIconColors = ConcurrentHashMap.newKeySet<String>()

    fun loadPrefs(): DesktopPrefs = runCatching {
        if (prefsFile.exists()) json.decodeFromString(DesktopPrefs.serializer(), prefsFile.readText()) else DesktopPrefs()
    }.getOrDefault(DesktopPrefs())

    fun savePrefs(prefs: DesktopPrefs) {
        runCatching { prefsFile.writeText(json.encodeToString(DesktopPrefs.serializer(), prefs)) }
    }

    @Synchronized
    fun installStats(): InstallStats = runCatching {
        if (statsFile.exists()) json.decodeFromString(InstallStats.serializer(), statsFile.readText()) else InstallStats()
    }.getOrDefault(InstallStats())

    /** Durable local install counter; the in-memory download list is cleared on restart. */
    @Synchronized
    fun recordInstall(appName: String, portable: Boolean): InstallStats {
        val current = installStats()
        val next = current.copy(
            installs = current.installs + 1,
            portable = current.portable + if (portable) 1 else 0,
            lastApp = appName
        )
        runCatching { statsFile.writeText(json.encodeToString(InstallStats.serializer(), next)) }
        return next
    }

    private fun rawMirrors(url: String): List<String> {
        val m = Regex("^https://raw\\.githubusercontent\\.com/([^/]+)/([^/]+)/(?:refs/heads/)?([^/]+)/(.+)$").find(url.trim())
            ?: return listOf(url)
        val (o, r, b, p) = m.destructured
        return listOf(url, "https://cdn.jsdelivr.net/gh/$o/$r@$b/$p", "https://raw.githack.com/$o/$r/$b/$p")
    }

    private fun fetchFirstText(urls: List<String>, cache: File? = null): String? {
        for (u in urls.distinct()) {
            runCatching {
                val req = Request.Builder().url(u).build()
                client.newCall(req).execute().use { resp ->
                    if (resp.isSuccessful) {
                        val body = resp.body?.string().orEmpty()
                        if (body.isNotBlank()) {
                            cache?.writeText(body)
                            return body
                        }
                    }
                }
            }
        }
        return cache?.takeIf { it.exists() }?.readText()
    }

    private fun fetchFirstBytesToFile(urls: List<String>, target: File): File? {
        for (u in urls.distinct()) {
            runCatching {
                val req = Request.Builder().url(u).build()
                client.newCall(req).execute().use { resp ->
                    if (resp.isSuccessful) {
                        val stream = resp.body?.byteStream() ?: return@use
                        val tmp = File(target.parentFile, "${target.name}.tmp")
                        tmp.outputStream().use { out -> stream.copyTo(out) }
                        if (tmp.length() > 1024) {
                            tmp.renameTo(target)
                            return target
                        } else {
                            tmp.delete()
                        }
                    }
                }
            }
        }
        return target.takeIf { it.exists() && it.length() > 1024 }
    }

    /** Fast local load from disk cache if already present, for zero-flicker startup. */
    fun loadCached(): DesktopCatalogResult? = runCatching {
        if (!appsCacheFile.exists()) return null
        val appsRoot = json.parseToJsonElement(appsCacheFile.readText())
        val cfg = configCacheFile.takeIf { it.exists() }?.readText()?.let { runCatching { CatalogProcessor.parseConfig(it) }.getOrNull() }
        val stars = starsCacheFile.takeIf { it.exists() }?.readText()?.let {
            runCatching { json.decodeFromString(MapSerializer(String.serializer(), Int.serializer()), it).mapKeys { e -> e.key.lowercase() } }.getOrNull()
        }
        val mirror = mirrorCacheFile.takeIf { it.exists() && it.length() > 1024 }?.let { f ->
            runCatching { f.inputStream().use { CatalogProcessor.decodeMirror(it) } }.getOrNull()
        }.orEmpty()
        val rawArr = extractAppsArray(appsRoot)
        val processed = CatalogProcessor.process(rawArr, mirror)
        val withStars = CatalogProcessor.withStars(processed, stars, emptyList())
        DesktopCatalogResult(withStars, cfg, "Cached")
    }.getOrNull()

    suspend fun loadCatalog(): DesktopCatalogResult = withContext(Dispatchers.IO) {
        coroutineScope {
            val cfgDeferred = async {
                fetchFirstText(Defaults.CONFIG_URLS, configCacheFile)?.let {
                    runCatching { CatalogProcessor.parseConfig(it) }.getOrNull()
                }
            }
            val cfg = cfgDeferred.await()

            val appsUrls = buildList {
                cfg?.appsJsonUrl?.takeIf { it.isNotBlank() }?.let { addAll(rawMirrors(it)) }
                addAll(Defaults.APPS_URLS)
            }
            val mirrorUrls = rawMirrors(cfg?.mirrorJsonUrl?.takeIf { it.isNotBlank() } ?: Defaults.MIRROR_JSON)
            val starsUrls = rawMirrors(Defaults.STARS_JSON)

            val appsDeferred = async { fetchFirstText(appsUrls, appsCacheFile) }
            val starsDeferred = async {
                fetchFirstText(starsUrls, starsCacheFile)?.let { text ->
                    runCatching {
                        json.decodeFromString(MapSerializer(String.serializer(), Int.serializer()), text)
                            .mapKeys { it.key.lowercase() }
                    }.getOrNull()
                }
            }
            val mirrorDeferred = async {
                fetchFirstBytesToFile(mirrorUrls, mirrorCacheFile)?.let { file ->
                    runCatching { file.inputStream().use { CatalogProcessor.decodeMirror(it) } }.getOrNull()
                }.orEmpty()
            }

            val appsText = appsDeferred.await().orEmpty()
            val stars = starsDeferred.await()
            val mirror = mirrorDeferred.await()

            val rawArr = if (appsText.isNotBlank()) extractAppsArray(json.parseToJsonElement(appsText)) else JsonArray(emptyList())
            val processed = CatalogProcessor.process(rawArr, mirror)
            val withStars = CatalogProcessor.withStars(processed, stars, emptyList())
            DesktopCatalogResult(withStars, cfg, if (mirror.isNotEmpty()) "Live Mirror" else "Live")
        }
    }

    private fun extractAppsArray(root: JsonElement): JsonArray = when (root) {
        is JsonArray -> root
        is JsonObject -> root["apps"]?.jsonArray ?: JsonArray(emptyList())
        else -> JsonArray(emptyList())
    }

    suspend fun fetchGitHubRelease(repo: String): String? = withContext(Dispatchers.IO) {
        val cleanRepo = repo.replace(Regex("^https?://(www\\.)?github\\.com/"), "").trimEnd('/')
        val url = "https://api.github.com/repos/$cleanRepo/releases/latest"
        runCatching {
            val req = Request.Builder()
                .url(url)
                .header("Accept", "application/vnd.github.v3+json")
                .build()
            client.newCall(req).execute().use { resp ->
                if (resp.isSuccessful) resp.body?.string() else null
            }
        }.getOrNull()
    }

    suspend fun extractIconColor(iconUrl: String): Color? {
        if (iconUrl.isBlank() || iconUrl.endsWith(".svg", true)) return null
        iconColorCache[iconUrl]?.let { return it }
        if (iconUrl in failedIconColors) return null
        return withContext(Dispatchers.IO) {
            val computed = runCatching {
                val req = Request.Builder().url(iconUrl).build()
                val bytes = client.newCall(req).execute().use { r ->
                    if (!r.isSuccessful) return@runCatching null
                    r.body?.bytes()
                } ?: return@runCatching null
                val img = ImageIO.read(ByteArrayInputStream(bytes)) ?: return@runCatching null
                dominantColor(img)
            }.getOrNull()
            if (computed != null) iconColorCache[iconUrl] = computed else failedIconColors.add(iconUrl)
            computed
        }
    }

    /** Same 12-hue weighted dominant color bucket algorithm as Common.kt */
    private fun dominantColor(src: BufferedImage): Color? {
        val w = src.width.coerceAtLeast(1)
        val h = src.height.coerceAtLeast(1)
        val stepX = (w / 24).coerceAtLeast(1)
        val stepY = (h / 24).coerceAtLeast(1)
        val weights = FloatArray(12)
        val rgb = Array(12) { FloatArray(3) }
        val hsb = FloatArray(3)

        var x = 0
        while (x < w) {
            var y = 0
            while (y < h) {
                val argb = src.getRGB(x, y)
                val alpha = (argb ushr 24) and 0xFF
                if (alpha >= 160) {
                    val r = (argb ushr 16) and 0xFF
                    val g = (argb ushr 8) and 0xFF
                    val b = argb and 0xFF
                    java.awt.Color.RGBtoHSB(r, g, b, hsb)
                    val k = hsb[1] * hsb[2]
                    if (k >= 0.18f) {
                        val bucket = ((hsb[0] * 360f) / 30f).toInt().coerceIn(0, 11)
                        weights[bucket] += k
                        rgb[bucket][0] += r * k
                        rgb[bucket][1] += g * k
                        rgb[bucket][2] += b * k
                    }
                }
                y += stepY
            }
            x += stepX
        }
        val best = weights.indices.maxByOrNull { weights[it] } ?: return null
        if (weights[best] < 2.5f) return null
        return Color(
            (rgb[best][0] / weights[best] / 255f).coerceIn(0f, 1f),
            (rgb[best][1] / weights[best] / 255f).coerceIn(0f, 1f),
            (rgb[best][2] / weights[best] / 255f).coerceIn(0f, 1f)
        )
    }
}
