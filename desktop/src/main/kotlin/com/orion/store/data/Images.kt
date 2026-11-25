package com.orion.store.data

import java.net.URLEncoder

/**
 * Image URLs, ported 1:1 from the Android app (Logic.kt) so both platforms ask the network for
 * exactly the same thing.
 *
 * The order matters: a right-sized copy through the image proxy first, the original only if that
 * fails. Icons that point at a multi-megabyte original or a host that refuses the app's user
 * agent still load, which is the difference between a real icon and a letter placeholder.
 */
private val PLAY_SIZE = Regex("=[swh]\\d+")
private val PLAY_SIZE_TAIL = Regex("=[swh]\\d+[^/]*$")

fun enc(s: String): String = URLEncoder.encode(s, "UTF-8")
    .replace("+", "%20").replace("%21", "!").replace("%27", "'")
    .replace("%28", "(").replace("%29", ")").replace("%7E", "~").replace("*", "%2A")

fun optimizedImageUrl(url: String?, width: Int? = null, height: Int? = null): String {
    if (url.isNullOrEmpty()) return ""
    if (url.startsWith("data:") || url.startsWith("blob:") || url.startsWith("file:") || url.startsWith("/")) return url
    // Play images resize on Google's own CDN: ask for the display size directly instead of a
    // full-size fetch transcoded by the proxy.
    if ("googleusercontent.com" in url || "ggpht.com" in url) {
        val base = if (PLAY_SIZE.containsMatchIn(url)) url.replace(PLAY_SIZE_TAIL, "") else url
        return base + "=" + (listOfNotNull(width?.let { "w$it" }, height?.let { "h$it" })
            .ifEmpty { listOf("w2560", "h1440") }).joinToString("-") + "-rw"
    }
    val src = url
    val q = when { width != null && width <= 112 -> 60; width != null && width < 200 -> 68; else -> 95 }
    return buildString {
        append("https://wsrv.nl/?url=${enc(src)}&output=webp&q=$q&l=1&il=${if (width != null && width < 200) 0 else 1}&maxage=31d&n=-1")
        width?.let { append("&w=$it") }; height?.let { append("&h=$it") }
    }
}
