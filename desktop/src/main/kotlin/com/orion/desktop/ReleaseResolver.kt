package com.orion.desktop

import com.orion.store.data.AppItem
import com.orion.store.data.AppVariant
import com.orion.store.data.CatalogProcessor
import com.orion.store.data.Platform
import com.orion.store.data.json
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.longOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import java.net.URI
import java.net.URLEncoder
import java.util.Locale
import java.util.concurrent.TimeUnit

data class ResolvedRelease(
    val tag: String = "",
    val notes: String? = null,
    val assets: List<AppVariant> = emptyList(),
    val host: String = ""
)

/**
 * Live release lookup for the detail screen. GitHub is the common case, but the
 * catalog also carries GitLab (including self-hosted) and Codeberg projects, and
 * their APIs speak three different shapes. This normalises all three to [AppVariant].
 */
object ReleaseResolver {
    private val client = OkHttpClient.Builder()
        .connectTimeout(12, TimeUnit.SECONDS)
        .readTimeout(25, TimeUnit.SECONDS)
        .callTimeout(35, TimeUnit.SECONDS)
        .build()

    private val PC_EXTENSIONS = listOf(".exe", ".msi", ".msix", ".msixbundle", ".appx", ".appxbundle", ".zip", ".7z", ".ps1")
    private val SKIP_ASSETS = listOf(
        "sha256", "sha512", "checksum", ".sig", ".asc", ".blockmap", "source code", ".yml", ".yaml"
    )
    private val ARCH_ORDER = mapOf(
        "Universal" to 1, "ARM64" to 2, "ARMv7" to 3, "x64" to 4, "x86" to 5,
        "TV" to 6, "ARM64 (TV)" to 7, "ARMv7 (TV)" to 8
    )
    private val WINDOWS_TOKENS = setOf(
        "win", "windows", "win32", "win64", "x64", "x86", "amd64", "setup", "installer", "desktop"
    )

    suspend fun resolve(app: AppItem): ResolvedRelease? = withContext(Dispatchers.IO) {
        var fallback: ResolvedRelease? = null
        for (ref in repoRefs(app)) {
            val release = runCatching {
                when (ref.kind) {
                    RepoKind.GITHUB -> resolveGitHub(ref, app.platform, app.releaseKeyword)
                    RepoKind.GITLAB -> resolveGitLab(ref, app.platform, app.releaseKeyword)
                    RepoKind.CODEBERG -> resolveCodeberg(ref, app.platform, app.releaseKeyword)
                    RepoKind.UNKNOWN -> null
                }
            }.getOrNull() ?: continue
            if (release.assets.isNotEmpty()) return@withContext release
            // A release without a matching asset still gives the UI a version and notes,
            // but keep looking in case another repo reference has downloadable files.
            if (fallback == null) fallback = release
        }
        fallback
    }

    internal enum class RepoKind { GITHUB, GITLAB, CODEBERG, UNKNOWN }

    internal data class RepoRef(val host: String, val path: String, val kind: RepoKind) {
        val baseUrl: String get() = "https://$host"
    }

    internal fun repoRefs(app: AppItem): List<RepoRef> {
        val out = mutableListOf<RepoRef>()

        fun add(raw: String?, fallback: RepoKind) {
            val value = raw?.trim()?.takeIf { it.isNotBlank() && it != "#" } ?: return
            if (value.startsWith("http://") || value.startsWith("https://")) {
                val uri = runCatching { URI(value) }.getOrNull() ?: return
                val host = uri.host?.lowercase(Locale.US)?.removePrefix("www.") ?: return
                val segments = uri.path.orEmpty().trim('/').split('/').filter { it.isNotBlank() }
                if (segments.size >= 2) {
                    val kind = kindFor(host, app).takeIf { it != RepoKind.UNKNOWN } ?: fallback
                    if (kind != RepoKind.UNKNOWN) {
                        val repoPath = if (kind == RepoKind.GITHUB || kind == RepoKind.CODEBERG) {
                            "${segments[0]}/${segments[1].removeSuffix(".git")}"
                        } else {
                            val cleanSegments = segments.takeWhile { it != "-" && it != "releases" && it != "tags" }
                            cleanSegments.joinToString("/").removeSuffix(".git")
                        }
                        if (repoPath.count { it == '/' } >= 1) {
                            out += RepoRef(host, repoPath, kind)
                        }
                    }
                }
            } else {
                val host = when (fallback) {
                    RepoKind.GITLAB -> app.gitlabDomain?.trim()?.removePrefix("https://")?.removePrefix("http://")
                        ?.substringBefore('/')?.ifBlank { null } ?: "gitlab.com"
                    RepoKind.GITHUB -> "github.com"
                    RepoKind.CODEBERG -> "codeberg.org"
                    RepoKind.UNKNOWN -> return
                }
                val segments = value.trim('/').split('/').filter { it.isNotBlank() }
                if (segments.size >= 2) {
                    val repoPath = if (fallback == RepoKind.GITHUB || fallback == RepoKind.CODEBERG) {
                        "${segments[0]}/${segments[1].removeSuffix(".git")}"
                    } else {
                        val cleanSegments = segments.takeWhile { it != "-" && it != "releases" && it != "tags" }
                        cleanSegments.joinToString("/").removeSuffix(".git")
                    }
                    if (repoPath.count { it == '/' } >= 1) {
                        out += RepoRef(host, repoPath, fallback)
                    }
                }
            }
        }

        add(app.githubRepo, RepoKind.GITHUB)
        add(app.gitlabRepo, RepoKind.GITLAB)
        add(app.codebergRepo, RepoKind.CODEBERG)
        add(app.repoUrl, RepoKind.UNKNOWN)
        add(app.downloadUrl, RepoKind.UNKNOWN)
        add(app.officialSite, RepoKind.UNKNOWN)
        return out.distinctBy { it.host + "/" + it.path.lowercase(Locale.US) }
    }

    private fun kindFor(host: String, app: AppItem): RepoKind {
        val h = host.lowercase(Locale.US)
        val selfHostedGitLab = app.gitlabDomain?.trim()?.removePrefix("https://")?.removePrefix("http://")
            ?.substringBefore('/')?.lowercase(Locale.US)
        return when {
            h == "github.com" || h.endsWith(".github.com") -> RepoKind.GITHUB
            h == "codeberg.org" || h.endsWith(".codeberg.org") -> RepoKind.CODEBERG
            "gitlab" in h || (selfHostedGitLab != null && h == selfHostedGitLab) -> RepoKind.GITLAB
            else -> RepoKind.UNKNOWN
        }
    }

    private fun resolveGitHub(ref: RepoRef, platform: String, keyword: String?): ResolvedRelease? {
        val path = ref.path.split('/').take(2).joinToString("/")
        val body = fetch("https://api.github.com/repos/$path/releases/latest", accept = "application/vnd.github.v3+json") ?: return null
        return parseGitHub(body, platform, keyword)
    }

    private fun resolveGitLab(ref: RepoRef, platform: String, keyword: String?): ResolvedRelease? {
        val project = URLEncoder.encode(ref.path, "UTF-8")
        val body = fetch("${ref.baseUrl}/api/v4/projects/$project/releases?per_page=1") ?: return null
        return parseGitLab(body, platform, keyword)
    }

    private fun resolveCodeberg(ref: RepoRef, platform: String, keyword: String?): ResolvedRelease? {
        val body = fetch("${ref.baseUrl}/api/v1/repos/${ref.path}/releases/latest") ?: return null
        return parseCodeberg(body, platform, keyword)
    }

    internal fun parseGitHub(body: String, platform: String, keyword: String? = null): ResolvedRelease? {
        val obj = runCatching { json.parseToJsonElement(body).jsonObject }.getOrNull() ?: return null
        val assets = (obj["assets"] as? JsonArray).orEmpty().mapNotNull { element ->
            val asset = element as? JsonObject ?: return@mapNotNull null
            val name = asset.string("name").orEmpty()
            val url = asset.string("browser_download_url").orEmpty()
            variantOf(name, url, asset.long("size"), platform)
        }
        val tag = obj.string("tag_name").orEmpty()
        return ResolvedRelease(
            tag = tag,
            notes = obj.string("body"),
            assets = sorted(filterByKeyword(assets, keyword, obj.string("name"), tag, platform)),
            host = "GitHub"
        )
    }

    internal fun parseGitLab(body: String, platform: String, keyword: String? = null): ResolvedRelease? {
        val arr = runCatching { json.parseToJsonElement(body).jsonArray }.getOrNull() ?: return null
        val release = arr.firstOrNull() as? JsonObject ?: return null
        val links = ((release["assets"] as? JsonObject)?.get("links") as? JsonArray).orEmpty()
        val assets = links.mapNotNull { element ->
            val link = element as? JsonObject ?: return@mapNotNull null
            val name = link.string("name").orEmpty()
            val url = link.string("direct_asset_url") ?: link.string("url") ?: return@mapNotNull null
            variantOf(name, url, link.long("size"), platform)
        }
        val tag = release.string("tag_name").orEmpty()
        return ResolvedRelease(
            tag = tag,
            notes = release.string("description"),
            assets = sorted(filterByKeyword(assets, keyword, release.string("name"), tag, platform)),
            host = "GitLab"
        )
    }

    internal fun parseCodeberg(body: String, platform: String, keyword: String? = null): ResolvedRelease? {
        val obj = runCatching { json.parseToJsonElement(body).jsonObject }.getOrNull() ?: return null
        val assets = (obj["assets"] as? JsonArray).orEmpty().mapNotNull { element ->
            val asset = element as? JsonObject ?: return@mapNotNull null
            val name = asset.string("name").orEmpty()
            val url = asset.string("browser_download_url").orEmpty()
            variantOf(name, url, asset.long("size"), platform)
        }
        val tag = obj.string("tag_name").orEmpty()
        return ResolvedRelease(
            tag = tag,
            notes = obj.string("body"),
            assets = sorted(filterByKeyword(assets, keyword, obj.string("name"), tag, platform)),
            host = "Codeberg"
        )
    }

    /**
     * Community repos often publish dozens of unrelated APKs in one "latest" release
     * (Grininja/community-builds is the extreme case). [AppItem.releaseKeyword] is the
     * catalog's curated selector; mirror its behaviour instead of dumping every asset.
     */
    private fun filterByKeyword(
        assets: List<AppVariant>,
        keyword: String?,
        releaseName: String?,
        releaseTag: String?,
        platform: String = Platform.PC
    ): List<AppVariant> {
        // On PC, ignore legacy Android catalog keyword "apk"
        val kw = keyword?.trim()?.lowercase(Locale.US)?.takeIf {
            it.isNotBlank() && !(platform == Platform.PC && (it == "apk" || it == ".apk"))
        } ?: return assets
        val matching = assets.filter {
            kw in it.name.lowercase(Locale.US) || kw in it.url.lowercase(Locale.US)
        }
        if (matching.isNotEmpty()) return matching
        val meta = listOfNotNull(releaseName, releaseTag).joinToString(" ").lowercase(Locale.US)
        return if (kw in meta) assets else emptyList()
    }

    fun isDirectBinaryUrl(url: String?): Boolean {
        if (url.isNullOrBlank() || url == "#") return false
        val clean = url.substringBefore('?').substringBefore('#').lowercase(Locale.US)
        return PC_EXTENSIONS.any { clean.endsWith(it) } || clean.contains("/releases/download/")
    }

    private fun variantOf(name: String, url: String, size: Long?, platform: String): AppVariant? {
        if (url.isBlank()) return null
        val effectiveName = name.ifBlank { url.substringBefore('?').substringAfterLast('/') }
        val lower = effectiveName.lowercase(Locale.US)
        if (SKIP_ASSETS.any { it in lower }) return null
        val keep = if (platform == Platform.PC) {
            PC_EXTENSIONS.any { lower.endsWith(it) }
        } else {
            lower.endsWith(".apk")
        }
        if (!keep) return null
        return AppVariant(CatalogProcessor.determineArch(effectiveName), url, size?.takeIf { it > 0 }, effectiveName)
    }

    private fun sorted(assets: List<AppVariant>) = assets
        .distinctBy { it.url }
        .sortedWith(
            compareBy<AppVariant>(
                { extensionRank(it.name) },
                { windowsScore(it.name) },
                { ARCH_ORDER[it.arch] ?: 9 }
            ).thenByDescending { it.name.lowercase(Locale.US) }
        )

    private fun extensionRank(name: String): Int = when {
        name.endsWith(".msi", true) -> 0
        name.endsWith(".exe", true) -> 1
        name.endsWith(".msix", true) || name.endsWith(".msixbundle", true) -> 2
        name.endsWith(".zip", true) -> 3
        name.endsWith(".7z", true) -> 4
        else -> 5
    }

    /** Prefer a real Windows build over a source/universal zip when both are offered. */
    private fun windowsScore(name: String): Int {
        val tokens = name.lowercase(Locale.US).split(Regex("[^a-z0-9]+"))
        return if (tokens.any { it in WINDOWS_TOKENS }) 0 else 1
    }

    private fun fetch(url: String, accept: String? = null): String? = runCatching {
        val builder = Request.Builder()
            .url(url)
            .header("User-Agent", "OrionStoreDesktop/2.0 (Windows)")
        if (accept != null) builder.header("Accept", accept)
        client.newCall(builder.build()).execute().use { response ->
            if (response.isSuccessful) response.body?.string() else null
        }
    }.getOrNull()

    private fun JsonObject.string(key: String): String? =
        (this[key] as? JsonPrimitive)?.contentOrNull?.takeIf { it.isNotBlank() }

    private fun JsonObject.long(key: String): Long? =
        (this[key] as? JsonPrimitive)?.longOrNull
}
