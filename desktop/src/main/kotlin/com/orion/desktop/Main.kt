package com.orion.desktop

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.VerticalScrollbar
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollbarAdapter
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.*
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PageSize
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.compose.ui.graphics.painter.ColorPainter
import androidx.compose.ui.graphics.toComposeImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import coil3.compose.AsyncImage
import com.orion.store.data.*
import com.orion.store.ui.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import javax.imageio.ImageIO
import kotlin.math.abs

fun main() {
    System.setProperty("skiko.vsync.enabled", "true")
    System.setProperty("skiko.hardwareAcceleration", "true")
    application {
    val windowImg = remember {
        runCatching {
            Thread.currentThread().contextClassLoader.getResourceAsStream("orion_icon.png")?.use {
                ImageIO.read(it)
            }
        }.getOrNull()
    }
    val windowPainter = remember(windowImg) {
        windowImg?.let { BitmapPainter(it.toComposeImageBitmap()) }
    }
    var prefs by remember { mutableStateOf(DesktopCatalogRepository.loadPrefs()) }

    fun updatePrefs(next: DesktopPrefs) {
        prefs = next
        DesktopCatalogRepository.savePrefs(next)
    }

    Window(
        onCloseRequest = {
            if (prefs.autoCleanup) DesktopCatalogRepository.clearPendingInstallers()
            exitApplication()
        },
        title = "Orion Store Windows",
        icon = windowPainter,
        state = rememberWindowState(width = 1280.dp, height = 860.dp)
    ) {
        LaunchedEffect(windowImg) {
            if (windowImg != null) {
                window.iconImage = windowImg
            }
        }

        val palette = remember(prefs.paletteId) { PALETTES.firstOrNull { it.id == prefs.paletteId } ?: PALETTES.first() }
        val scheme = remember(palette, prefs.themeMode) { palette.scheme(prefs.themeMode) }
        // Google Sans, shipped with the app.
        val orionFonts = rememberOrionFontFamily()
        val orionType = remember(orionFonts) { Typography().withFontFamily(orionFonts) }

        MaterialTheme(colorScheme = scheme, typography = orionType) {
            CompositionLocalProvider(LocalSmoothMotion provides prefs.smoothMotion) {
                Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.surface) {
                    DesktopStartupShell(smoothMotion = prefs.smoothMotion) {
                        OrionDesktopRoot(
                            prefs = prefs,
                            palette = palette,
                            onUpdatePrefs = ::updatePrefs
                        )
                    }
                }
            }
        }
    }
}
}

@Composable
private fun DesktopStartupShell(
    smoothMotion: Boolean,
    content: @Composable () -> Unit
) {
    val cs = MaterialTheme.colorScheme
    val intro = remember { Animatable(0f) }
    val splashAlpha = remember { Animatable(1f) }
    var splashVisible by remember { mutableStateOf(true) }
    var contentReady by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        // Start on the first rendered frame, so the reveal runs at its intended pace instead of
        // being swallowed by the work of the very first composition.
        withFrameNanos { }
        if (smoothMotion) {
            intro.animateTo(1f, tween(durationMillis = 900, easing = FastOutSlowInEasing))
            // Android keeps the splash up until the app has drawn its first frame; clearing it
            // earlier would flash an empty window.
            val readyBy = System.currentTimeMillis() + 8000
            while (!contentReady && System.currentTimeMillis() < readyBy) {
                withFrameNanos { }
            }
            // Let the store settle for a moment so the handover lands on real content.
            repeat(12) { withFrameNanos { } }
            splashAlpha.animateTo(0f, tween(durationMillis = 320, easing = FastOutSlowInEasing))
        } else {
            intro.snapTo(1f)
            splashAlpha.snapTo(0f)
        }
        splashVisible = false
    }

    Box(Modifier.fillMaxSize()) {
        // The splash sits underneath and stays opaque until the store has drawn, while the store
        // fades in over it. Nothing can flash an empty window, however slow the catalog is.
        if (splashVisible) {
            Box(
                Modifier
                    .fillMaxSize()
                    .background(cs.background)
                    // A soft brand bloom behind the mark: Android's splash is a flat colour, but on
                    // a wide desktop canvas that reads as an empty void rather than a splash.
                    .background(
                        Brush.radialGradient(
                            0.0f to cs.primary.copy(alpha = 0.16f),
                            0.45f to cs.primary.copy(alpha = 0.05f),
                            1.0f to Color.Transparent,
                            center = Offset.Unspecified,
                            radius = Float.POSITIVE_INFINITY
                        )
                    ),
                contentAlignment = Alignment.Center
            ) {
                // The mark drifts away as the splash clears.
                Box(
                    Modifier.graphicsLayer {
                        alpha = splashAlpha.value
                        val drift = 1f + (1f - splashAlpha.value) * 0.22f
                        scaleX = drift
                        scaleY = drift
                    }
                ) {
                    OrionAnimatedSplashMark(intro.value, cs.primary, cs.onPrimary)
                }
            }
        }
        Box(
            Modifier.fillMaxSize()
                // The store fades in over the opaque splash, so the handover can never expose an
                // empty window.
                .graphicsLayer { alpha = 1f - splashAlpha.value }
                .onGloballyPositioned { contentReady = true }
        ) {
            content()
        }
    }
}


enum class DesktopTab(val label: String, val icon: ImageVector, val scopeName: String) {
    PC("PC", Icons.Default.DesktopWindows, "PC"),
    APPS("Apps", Icons.Default.Android, "apps"),
    TV("TV", Icons.Default.Tv, "TV"),
    ORBIT("Orbit", Icons.Default.Explore, "your Orbit"),
    MY_APPS("My Apps", Icons.Default.GridView, "my apps"),
    DEV("Dev", Icons.Default.Code, "About Orion & Devs")
}

enum class DesktopSort(val label: String) {
    NEWEST("Recently added"),
    MOST_STARRED("Most starred"),
    NAME_ASC("Name (A–Z)"),
    NAME_DESC("Name (Z–A)"),
    SIZE_ASC("Smallest first"),
    SIZE_DESC("Largest first")
}

data class DownloadState(
    val app: AppItem,
    val status: String,
    val progress: Float = -1f,
    val bytesDownloaded: Long = 0,
    val totalBytes: Long = -1,
    val bytesPerSecond: Long = 0,
    val localFile: File? = null,
    val installDir: File? = null,
    val shortcut: File? = null,
    val isComplete: Boolean = false,
    val error: String? = null
)

fun fallbackFilename(app: AppItem, url: String): String {
    val fromUrl = url.substringBefore('?').substringAfterLast('/').trim()
    if (fromUrl.isNotBlank() && '.' in fromUrl && fromUrl.length < 120) return fromUrl
    val extension = if (app.platform == Platform.PC) "exe" else "apk"
    return "${app.name.replace(" ", "_")}_${app.latestVersion}.$extension"
}

private val fullSpan: LazyGridItemSpanScope.() -> GridItemSpan = { GridItemSpan(maxLineSpan) }
private val BUNDLE_FAN = listOf(-6f, 5f, 3f, -4f)
private val SPACES_REGEX = Regex("\\s+")

@Composable
fun OrionDesktopRoot(
    prefs: DesktopPrefs,
    palette: Palette,
    onUpdatePrefs: (DesktopPrefs) -> Unit
) {
    val scope = rememberCoroutineScope()
    val cs = MaterialTheme.colorScheme

    var catalog by remember {
        mutableStateOf(DesktopCatalogResult(emptyList(), null, "Loading"))
    }
    var refreshing by remember { mutableStateOf(true) }
    // The desktop store opens on its own catalog; Apps and TV stay one tap away.
    var currentTab by remember { mutableStateOf(DesktopTab.PC) }
    var searchQuery by remember { mutableStateOf("") }
    var selectedCategory by remember { mutableStateOf("All") }
    var sortOption by remember { mutableStateOf(DesktopSort.NEWEST) }
    var filterFavoritesOnly by remember { mutableStateOf(false) }
    var expandedView by remember { mutableStateOf<String?>(null) }

    var selectedApp by remember { mutableStateOf<AppItem?>(null) }
    var selectedBundle by remember { mutableStateOf<BundleItem?>(null) }
    var downloadTasks by remember { mutableStateOf<Map<String, DownloadState>>(emptyMap()) }
    var snackMessage by remember { mutableStateOf<String?>(null) }
    var inSettingsView by remember { mutableStateOf(false) }
    var showPilotProfile by remember { mutableStateOf(false) }

    fun toggleFavorite(id: String) {
        val next = if (id in prefs.favorites) prefs.favorites - id else prefs.favorites + id
        onUpdatePrefs(prefs.copy(favorites = next))
    }

    fun reloadCatalog() {
        scope.launch {
            refreshing = true
            val fresh = DesktopCatalogRepository.loadCatalog()
            if (fresh.apps.isNotEmpty()) catalog = fresh
            refreshing = false
        }
    }

    LaunchedEffect(Unit) {
        // The cached mirror is tens of megabytes. Parsing it on the main thread would stall the
        // first frames, and a stalled frame clock cuts the splash reveal short.
        val cached = withContext(Dispatchers.IO) { DesktopCatalogRepository.loadCached() }
        if (cached != null && cached.apps.isNotEmpty()) catalog = cached
        reloadCatalog()
    }

    fun startDownload(app: AppItem, customUrl: String? = null, customFile: String? = null) {
        val active = downloadTasks[app.id]?.let { !it.isComplete && it.error == null } == true
        if (active) return

        downloadTasks = downloadTasks + (app.id to DownloadState(app, "Checking GitHub for latest release…"))
        scope.launch {
            var targetUrl = customUrl
            var targetFile = customFile

            // Always resolve latest release from GitHub / GitLab / Codeberg on download
            val isDirectBinary = !targetUrl.isNullOrBlank() &&
                targetUrl != "#" &&
                targetUrl != app.downloadUrl &&
                ReleaseResolver.isDirectBinaryUrl(targetUrl)

            if (!isDirectBinary) {
                val live = ReleaseResolver.resolve(app)
                if (live != null && live.assets.isNotEmpty()) {
                    val best = live.assets.firstOrNull { it.url.isNotBlank() && it.url != "#" }
                    if (best != null) {
                        targetUrl = best.url
                        targetFile = best.name.ifBlank { null }
                    }
                }
            }

            val url = (targetUrl ?: app.downloadUrl).takeIf { it.isNotBlank() && it != "#" }
            if (url == null || (!ReleaseResolver.isDirectBinaryUrl(url) && url.startsWith("http"))) {
                withContext(Dispatchers.Main.immediate) {
                    val fallback = url ?: app.repoUrl ?: app.officialSite
                    if (!fallback.isNullOrBlank()) {
                        DesktopInstaller.openBrowser(fallback)
                        downloadTasks = downloadTasks - app.id
                        snackMessage = "No direct binary on GitHub. Opened official page for ${app.name}."
                    } else {
                        downloadTasks = downloadTasks + (app.id to DownloadState(app, "No release available", error = "No downloadable release found"))
                        snackMessage = "No downloadable release found on GitHub for ${app.name}"
                    }
                }
                return@launch
            }

            val filename = targetFile?.takeIf { it.isNotBlank() } ?: fallbackFilename(app, url)
            withContext(Dispatchers.Main.immediate) {
                downloadTasks = downloadTasks + (app.id to DownloadState(app, "Connecting…"))
            }

            val res = DesktopInstaller.downloadAndInstall(url, filename, app.name, prefs.silentInstall) { p ->
                val status = when (p.phase) {
                    InstallPhase.CONNECTING -> "Connecting…"
                    InstallPhase.DOWNLOADING -> buildString {
                        append("Downloading")
                        p.fraction?.let { append(" · ${(it * 100).toInt()}%") }
                        if (p.totalBytes > 0) append(" · ${formatBytes(p.bytesDownloaded)} / ${formatBytes(p.totalBytes)}")
                        if (p.bytesPerSecond > 0) append(" · ${formatSpeed(p.bytesPerSecond)}")
                    }
                    InstallPhase.EXTRACTING -> "Extracting archive…"
                    InstallPhase.LAUNCHING -> p.label.ifBlank { "Opening installer…" }
                    InstallPhase.DONE -> "Finishing…"
                }
                withContext(Dispatchers.Main.immediate) {
                    downloadTasks = downloadTasks + (app.id to DownloadState(
                        app = app,
                        status = status,
                        progress = p.fraction ?: -1f,
                        bytesDownloaded = p.bytesDownloaded,
                        totalBytes = p.totalBytes,
                        bytesPerSecond = p.bytesPerSecond
                    ))
                }
            }
            res.getOrNull()?.let { result ->
                withContext(Dispatchers.IO) {
                    DesktopCatalogRepository.recordInstall(app.name, result.installDir != null)
                }
            }
            withContext(Dispatchers.Main.immediate) {
                val result = res.getOrNull()
                downloadTasks = if (res.isSuccess && result != null) {
                    downloadTasks + (app.id to DownloadState(
                        app = app,
                        status = result.message,
                        progress = 1f,
                        bytesDownloaded = result.file.length(),
                        totalBytes = result.file.length(),
                        localFile = result.file,
                        installDir = result.installDir,
                        shortcut = result.shortcut,
                        isComplete = true
                    ))
                } else {
                    val rawMsg = res.exceptionOrNull()?.message ?: "Download failed"
                    if (rawMsg.contains("HTML page instead of an installer", ignoreCase = true)) {
                        DesktopInstaller.openBrowser(url)
                        downloadTasks = downloadTasks - app.id
                        snackMessage = "Opening download page in browser for ${app.name}..."
                        downloadTasks
                    } else {
                        downloadTasks + (app.id to DownloadState(app, "Failed: $rawMsg", error = rawMsg))
                    }
                }
                snackMessage = result?.message ?: res.exceptionOrNull()?.message
            }
        }
    }

    fun clearFinishedDownloads() {
        downloadTasks = downloadTasks.filterValues { !it.isComplete && it.error == null }
    }

    val allApps = catalog.apps
    val pcApps = remember(allApps) { allApps.filter { it.platform == Platform.PC } }
    val androidApps = remember(allApps) { allApps.filter { it.platform == Platform.ANDROID } }
    val tvApps = remember(allApps) { allApps.filter { it.platform == Platform.TV } }

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        // Android keeps the dock at the bottom on phones and moves it to a side rail on wide windows.
        val wide = maxWidth >= 840.dp
        val navPlacement = if (!wide) "bottom" else prefs.wideNav
        val rail = navPlacement == "rail"
        var navShown by remember { mutableStateOf(true) }
        var scrollDeltaAccumulator by remember { mutableFloatStateOf(0f) }
        val hideOnScroll = remember {
            object : NestedScrollConnection {
                override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                    scrollDeltaAccumulator += available.y
                    if (scrollDeltaAccumulator < -40f) {
                        navShown = false
                        scrollDeltaAccumulator = 0f
                    } else if (scrollDeltaAccumulator > 40f) {
                        navShown = true
                        scrollDeltaAccumulator = 0f
                    }
                    return Offset.Zero
                }
            }
        }
        val activeDownloads = downloadTasks.values.count { !it.isComplete && it.error == null }

        fun selectTab(tab: DesktopTab) {
            inSettingsView = false
            if (tab != currentTab) {
                currentTab = tab
                selectedCategory = "All"
                expandedView = null
            }
        }

        ExpressiveBackdrop()
        Row(modifier = Modifier.fillMaxSize()) {
            if (rail && !inSettingsView) {
                OrionSideRail(
                    currentTab = currentTab,
                    downloadsBadge = activeDownloads,
                    onSelectTab = ::selectTab,
                    modifier = Modifier.fillMaxHeight()
                )
            }
            Box(modifier = Modifier.weight(1f).fillMaxHeight()) {
                Column(modifier = Modifier.fillMaxSize()) {
                    // 1. Official Orion SearchPill Header (hidden inside Settings Hub/Subpages, matching Settings.kt!)
                    if (!inSettingsView) {
                        OrionHeader(
                            currentTab = currentTab,
                            searchQuery = searchQuery,
                            onSearchChange = { searchQuery = it; expandedView = null },
                            themeMode = prefs.themeMode,
                            onToggleTheme = {
                                val next = when (prefs.themeMode) {
                                    "dark" -> "dusk"
                                    "dusk" -> "oled"
                                    "oled" -> "light"
                                    else -> "dark"
                                }
                                onUpdatePrefs(prefs.copy(themeMode = next))
                            },
                            refreshing = refreshing,
                            onRefresh = ::reloadCatalog,
                            activeDownloadCount = activeDownloads,
                            onOpenDownloads = { currentTab = DesktopTab.MY_APPS; inSettingsView = false },
                            onOpenSettings = { inSettingsView = true },
                            onOpenPilotProfile = { showPilotProfile = true }
                        )
                    }

                    if (inSettingsView) {
                        Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
                            OrionSettingsScreen(
                                prefs = prefs,
                                palette = palette,
                                allApps = allApps,
                                downloads = downloadTasks.values.toList(),
                                announcement = catalog.config?.announcement,
                                onUpdatePrefs = onUpdatePrefs,
                                onReloadCatalog = ::reloadCatalog,
                                onSelectApp = { selectedApp = it },
                                onLaunchFile = DesktopInstaller::openFile,
                                onBack = { inSettingsView = false }
                            )
                        }
                    } else {
                        // 2. Main Tab Area (ultra-smooth spring slide + fade between tabs)
                        Box(modifier = Modifier.weight(1f).fillMaxWidth().nestedScroll(hideOnScroll)) {
                                AnimatedContent(
                                    targetState = currentTab,
                                    transitionSpec = {
                                        val dir = if (targetState.ordinal >= initialState.ordinal) 1 else -1
                                        (slideInHorizontally(spring(stiffness = Spring.StiffnessMediumLow, dampingRatio = Spring.DampingRatioLowBouncy)) { 40 * dir } +
                                         fadeIn(tween(180, easing = FastOutSlowInEasing))) togetherWith
                                        (slideOutHorizontally(spring(stiffness = Spring.StiffnessMediumLow)) { -24 * dir } +
                                         fadeOut(tween(110, easing = FastOutLinearInEasing)))
                                    },
                                    label = "tab-switch"
                                ) { tab ->
                                when (tab) {
                                    DesktopTab.PC, DesktopTab.APPS, DesktopTab.TV -> {
                                        val (platform, platformApps) = when (tab) {
                                            DesktopTab.PC -> Platform.PC to pcApps
                                            DesktopTab.TV -> Platform.TV to tvApps
                                            else -> Platform.ANDROID to androidApps
                                        }
                                        StorefrontScreen(
                                            allApps = allApps,
                                            platformApps = platformApps,
                                            platform = platform,
                                            modules = catalog.config?.storefrontModules.orEmpty(),
                                            loading = refreshing && allApps.isEmpty(),
                                            searchQuery = searchQuery,
                                            selectedCategory = selectedCategory,
                                            sortOption = sortOption,
                                            filterFavoritesOnly = filterFavoritesOnly,
                                            expandedView = expandedView,
                                            prefs = prefs,
                                            downloadTasks = downloadTasks,
                                            onSelectCategory = { selectedCategory = it; expandedView = null },
                                            onSelectSort = { sortOption = it },
                                            onToggleFavoritesOnly = { filterFavoritesOnly = !filterFavoritesOnly },
                                            onExpandView = { expandedView = it },
                                            onSelectApp = { selectedApp = it },
                                            onSelectBundle = { selectedBundle = it },
                                            onDownloadApp = { startDownload(it) },
                                            onToggleFavorite = ::toggleFavorite
                                        )
                                    }
                                    DesktopTab.ORBIT -> OrbitTabView(
                                        apps = allApps,
                                        query = searchQuery,
                                        onSelectApp = { selectedApp = it }
                                    )
                                    DesktopTab.MY_APPS -> MyAppsTabView(
                                        downloads = downloadTasks.values.toList(),
                                        favoriteApps = allApps.filter { it.id in prefs.favorites },
                                        onSelectApp = { selectedApp = it },
                                        onLaunchFile = DesktopInstaller::openFile,
                                        onRevealFile = DesktopInstaller::revealInExplorer,
                                        onClearFinished = ::clearFinishedDownloads
                                    )
                                    DesktopTab.DEV -> DevAboutTabView(
                                        onOpenSettings = { inSettingsView = true }
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        // 3. The floating dock, in whichever style and position was picked
        if (!rail && !inSettingsView) {
            OrionDock(
                currentTab = currentTab,
                style = prefs.dockStyle,
                downloadsBadge = activeDownloads,
                glassEffect = prefs.glassDock,
                dockScale = prefs.dockScale,
                visible = navShown,
                onSelectTab = ::selectTab,
                modifier = Modifier
                    .align(if (navPlacement == "top") Alignment.TopCenter else Alignment.BottomCenter)
                    .padding(top = if (navPlacement == "top") 12.dp else 0.dp, bottom = if (navPlacement == "top") 0.dp else 18.dp)
            )
        }

        // 4. Full-Canvas Two-Pane Detail Overlay (Detail.kt)
        AnimatedVisibility(
            visible = selectedApp != null,
            enter = fadeIn() + scaleIn(initialScale = 0.95f),
            exit = fadeOut() + scaleOut(targetScale = 0.96f)
        ) {
            selectedApp?.let { app ->
                DesktopDetailScreen(
                    baseApp = app,
                    isFavorite = app.id in prefs.favorites,
                    downloadState = downloadTasks[app.id],
                    onToggleFavorite = { toggleFavorite(app.id) },
                    onBack = { selectedApp = null },
                    onDownloadAsset = { targetApp, url, file -> startDownload(targetApp, url, file) },
                    onLaunchFile = DesktopInstaller::openFile,
                    onRevealFile = DesktopInstaller::revealInExplorer
                )
            }
        }

        // 5. Bundle Preview Modal
        selectedBundle?.let { b ->
            BundlePreviewDialog(
                bundle = b,
                onDismiss = { selectedBundle = null },
                onSelectApp = { app -> selectedApp = app }
            )
        }

        // 6. Holographic Pilot Profile Dialog (from top-right Avatar)
        if (showPilotProfile) {
            PilotProfileDialog(
                allAppsCount = allApps.size,
                downloadsCount = downloadTasks.values.count { it.isComplete },
                favoritesCount = prefs.favorites.size,
                onDismiss = { showPilotProfile = false }
            )
        }

        // 6. Snack message, drawn as Orion's expressive card rather than a plain bar
        snackMessage?.let { msg ->
            LaunchedEffect(msg) { delay(3000); snackMessage = null }
            OrionSnackCard(
                message = msg,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(horizontal = 16.dp)
                    .padding(bottom = 88.dp)
            )
        }
    }
}

// -------------------------------------------------------------
// HEADER + SEARCHPILL (Root.kt 1:1)
// -------------------------------------------------------------
@Composable
fun OrionHeader(
    currentTab: DesktopTab,
    searchQuery: String,
    onSearchChange: (String) -> Unit,
    themeMode: String,
    onToggleTheme: () -> Unit,
    refreshing: Boolean,
    onRefresh: () -> Unit,
    activeDownloadCount: Int,
    onOpenDownloads: () -> Unit,
    onOpenSettings: () -> Unit,
    onOpenPilotProfile: () -> Unit
) {
    val cs = MaterialTheme.colorScheme
    Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .widthIn(max = 1200.dp)
                .height(72.dp)
                .padding(start = 20.dp, end = 16.dp, top = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Left: Authentic SearchPill (capped at 680.dp like Root.kt line 396)
            Box(Modifier.weight(1f)) {
                if (currentTab == DesktopTab.DEV) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        OrionLogoMark()
                        Spacer(Modifier.width(12.dp))
                        Text("About & Developers", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                    }
                } else {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .widthIn(max = 680.dp)
                            .height(52.dp)
                            .clip(PillShape)
                            .background(cs.surfaceContainerHigh)
                            .padding(start = 6.dp, end = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        OrionLogoMark()
                        BasicTextField(
                            value = searchQuery,
                            onValueChange = onSearchChange,
                            modifier = Modifier.weight(1f).padding(horizontal = 12.dp),
                            singleLine = true,
                            textStyle = MaterialTheme.typography.bodyLarge.copy(color = cs.onSurface),
                            cursorBrush = SolidColor(cs.primary),
                            decorationBox = { field ->
                                Box(contentAlignment = Alignment.CenterStart) {
                                    if (searchQuery.isEmpty()) {
                                        Text(
                                            "Search ${currentTab.scopeName}",
                                            style = MaterialTheme.typography.bodyLarge,
                                            color = cs.onSurfaceVariant,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                    field()
                                }
                            }
                        )
                        if (searchQuery.isNotEmpty()) {
                            IconButton(onClick = { onSearchChange("") }) {
                                Icon(Icons.Default.Close, "Clear search", tint = cs.onSurfaceVariant)
                            }
                        } else {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                IconButton(onClick = { DesktopInstaller.openBrowser("https://www.virustotal.com/gui/home/url") }) {
                                    Icon(
                                        Sym.Shield,
                                        contentDescription = "Security Shield · VirusTotal",
                                        tint = cs.onSurfaceVariant,
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                                IconButton(onClick = onToggleTheme) {
                                    Icon(
                                        when (themeMode) {
                                            "light" -> Icons.Default.LightMode
                                            "dusk" -> Sym.Brightness6
                                            else -> Icons.Default.DarkMode
                                        },
                                        contentDescription = "Theme: $themeMode",
                                        tint = cs.onSurfaceVariant,
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // Right utility icons (Refresh, Downloads badge, Settings, Pilot avatar)
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                IconButton(onClick = onRefresh, enabled = !refreshing) {
                    if (refreshing) {
                        CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                    } else {
                        Icon(Icons.Default.Refresh, "Reload catalog", tint = cs.onSurfaceVariant)
                    }
                }

                if (activeDownloadCount > 0) {
                    IconButton(onClick = onOpenDownloads) {
                        BadgedBox(badge = { Badge(containerColor = cs.primary) { Text("$activeDownloadCount") } }) {
                            Icon(Icons.Default.Downloading, "Downloads", tint = cs.primary)
                        }
                    }
                }

                IconButton(onClick = onOpenSettings) {
                    Icon(Icons.Default.Settings, "Settings", tint = cs.onSurfaceVariant)
                }

                // Pilot Avatar Circle (opens Holographic Pilot Profile modal)
                IconButton(onClick = onOpenPilotProfile) {
                    Box(
                        modifier = Modifier.size(34.dp).clip(CircleShape).background(cs.secondaryContainer),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            "U",
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.Black,
                            color = cs.onSecondaryContainer
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun OrionLogoMark() {
    val cs = MaterialTheme.colorScheme
    val fg = cs.onPrimary
    Box(
        modifier = Modifier
            .size(40.dp)
            .clip(OrionShapes.Cookie9Sided)
            .background(cs.primary),
        contentAlignment = Alignment.Center
    ) {
        // Official Orion 3-shape glyph (ic_splash_logo.xml: rounded triangle + circle + rounded square)
        Canvas(modifier = Modifier.size(28.dp)) {
            val sx = size.width / 100f
            val sy = size.height / 100f

            val tri = Path().apply {
                moveTo(39f * sx, 52f * sy)
                lineTo(61f * sx, 52f * sy)
                lineTo(63.8f * sx, 48.5f * sy)
                lineTo(50f * sx, 28f * sy)
                lineTo(36.2f * sx, 48.5f * sy)
                close()
            }
            drawPath(tri, color = fg)
            drawPath(tri, color = fg, style = Stroke(width = 4.5f * sx, join = StrokeJoin.Round))

            drawCircle(
                color = fg,
                radius = 12.2f * sx,
                center = Offset(36.8f * sx, 66.9f * sy)
            )

            drawRoundRect(
                color = fg,
                topLeft = Offset(51f * sx, 54.7f * sy),
                size = Size(24.3f * sx, 24.4f * sy),
                cornerRadius = CornerRadius(3.9f * sx, 3.9f * sy)
            )
        }
    }
}

// -------------------------------------------------------------
// STOREFRONT SCREEN (StoreScreen.kt 1:1 Layout Engine)
// -------------------------------------------------------------
@Composable
fun StorefrontScreen(
    allApps: List<AppItem>,
    platformApps: List<AppItem>,
    platform: String,
    modules: List<ModuleConfig>,
    loading: Boolean,
    searchQuery: String,
    selectedCategory: String,
    sortOption: DesktopSort,
    filterFavoritesOnly: Boolean,
    expandedView: String?,
    prefs: DesktopPrefs,
    downloadTasks: Map<String, DownloadState>,
    onSelectCategory: (String) -> Unit,
    onSelectSort: (DesktopSort) -> Unit,
    onToggleFavoritesOnly: () -> Unit,
    onExpandView: (String?) -> Unit,
    onSelectApp: (AppItem) -> Unit,
    onSelectBundle: (BundleItem) -> Unit,
    onDownloadApp: (AppItem) -> Unit,
    onToggleFavorite: (String) -> Unit
) {
    val favorites = prefs.favorites
    val compactMode = prefs.compactMode
    val cs = MaterialTheme.colorScheme
    val gridState = rememberLazyGridState()

    val categories = remember(platformApps) { availableFilterCategories(platformApps) }

    val filteredList = remember(platformApps, searchQuery, selectedCategory, sortOption, filterFavoritesOnly, favorites) {
        var base = CatalogProcessor.search(searchQuery, selectedCategory, SortOption.NEWEST, platformApps)
        if (filterFavoritesOnly) base = base.filter { it.id in favorites }
        when (sortOption) {
            DesktopSort.NEWEST -> base
            DesktopSort.MOST_STARRED -> base.sortedByDescending { it.stars ?: -1 }
            DesktopSort.NAME_ASC -> base.sortedWith { a, b -> collator.compare(a.name, b.name) }
            DesktopSort.NAME_DESC -> base.sortedWith { a, b -> collator.compare(b.name, a.name) }
            DesktopSort.SIZE_ASC -> base.sortedBy { CatalogProcessor.parseSize(it.size) }
            DesktopSort.SIZE_DESC -> base.sortedByDescending { CatalogProcessor.parseSize(it.size) }
        }
    }

    val collections = remember(allApps, platform, modules) {
        CatalogProcessor.storefront(allApps, platform, modules)
    }

    val showHomeCollections = prefs.storeLayout == "modern" &&
        searchQuery.isBlank() &&
        selectedCategory == "All" &&
        sortOption == DesktopSort.NEWEST &&
        !filterFavoritesOnly &&
        expandedView == null &&
        collections.isNotEmpty()

    LaunchedEffect(selectedCategory, sortOption, filterFavoritesOnly, expandedView) {
        gridState.scrollToItem(0)
    }

    Column(modifier = Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally) {
        // Pinned Category & Sort Tabs Header (Always accessible, zero jump on tab switch)
        Box(modifier = Modifier.fillMaxWidth().widthIn(max = 1200.dp)) {
            FiltersHeader(
                categories = categories,
                selectedCategory = selectedCategory,
                sortOption = sortOption,
                filterFavoritesOnly = filterFavoritesOnly,
                searchQuery = searchQuery,
                count = filteredList.size,
                onSelectCategory = onSelectCategory,
                onSelectSort = onSelectSort,
                onToggleFavoritesOnly = onToggleFavoritesOnly
            )
        }

        // Animated Content: Tactile Emil Kowalski spring glide & fade on category / filter switch
        AnimatedContent(
            targetState = selectedCategory to (searchQuery.isNotEmpty() || filterFavoritesOnly || sortOption != DesktopSort.NEWEST),
            transitionSpec = {
                (fadeIn(animationSpec = tween(180, easing = FastOutSlowInEasing)) +
                 slideInVertically(animationSpec = spring(stiffness = Spring.StiffnessMediumLow, dampingRatio = Spring.DampingRatioLowBouncy)) { 24 })
                    .togetherWith(
                        fadeOut(animationSpec = tween(110, easing = FastOutLinearInEasing))
                    )
            },
            modifier = Modifier.weight(1f).fillMaxWidth(),
            contentAlignment = Alignment.TopCenter,
            label = "category-content-switch"
        ) { _ ->
            BoxWithConstraints(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
                LazyVerticalGrid(
                    columns = GridCells.Adaptive(minSize = 280.dp),
                    state = gridState,
                    modifier = Modifier.fillMaxSize().widthIn(max = 1200.dp),
                    contentPadding = PaddingValues(start = 10.dp, end = 10.dp, bottom = 110.dp),
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    when {
                        loading -> item(key = "skeleton", span = fullSpan) { StoreSkeletonView() }
                        filteredList.isEmpty() -> item(key = "empty", span = fullSpan) {
                            EmptyStoreState(
                                icon = if (filterFavoritesOnly) Icons.Default.HeartBroken else Icons.Default.SearchOff,
                                title = if (filterFavoritesOnly) "No favorites found" else "No $platform apps found",
                                body = if (filterFavoritesOnly) "Tap the heart inside any app page to pin it here." else null
                            )
                        }
                        showHomeCollections -> {
                            collections.forEach { c ->
                                when (c.type) {
                                    CollectionType.HERO -> if (c.apps.isNotEmpty()) {
                                        item(key = c.id, span = fullSpan) { HeroCarouselSection(c.apps, onSelectApp) }
                                    }
                                    CollectionType.SWIMLANE -> if (c.apps.isNotEmpty()) {
                                        if (c.id == "recommended_daily") {
                                            item(key = c.id, span = fullSpan) {
                                                RecommendedMultiBrowseSection(c, prefs.smoothMotion, onSelectApp)
                                            }
                                        } else {
                                            item(key = c.id, span = fullSpan) {
                                                SwimlaneSection(
                                                    c = c,
                                                    compact = compactMode,
                                                    onSeeMore = when {
                                                        c.id == "new_updated" -> ({ onExpandView("new_updated") })
                                                        c.filter != null -> ({ onSelectCategory(c.filter) })
                                                        else -> null
                                                    },
                                                    onSelectApp = onSelectApp
                                                )
                                            }
                                        }
                                    }
                                    CollectionType.CATEGORY_CARDS -> if (c.categoryCards.isNotEmpty()) {
                                        item(key = c.id, span = fullSpan) {
                                            CategoryBlobsSection(c, onSelectCategory)
                                        }
                                    }
                                    CollectionType.BUNDLE -> if (c.apps.isNotEmpty()) {
                                        item(key = c.id, span = fullSpan) {
                                            SpotlightNumberedSection(c, onSelectApp)
                                        }
                                    }
                                    CollectionType.RECOMMENDATION_BUNDLES -> if (c.bundles.isNotEmpty()) {
                                        item(key = c.id, span = fullSpan) {
                                            BundlesFolderSection(c, onSelectBundle)
                                        }
                                    }
                                    CollectionType.RANKED -> if (c.apps.isNotEmpty()) {
                                        item(key = c.id, span = fullSpan) {
                                            RankedChartsSection(c, onSelectApp)
                                        }
                                    }
                                    else -> Unit
                                }
                            }
                        }
                        expandedView == "new_updated" -> {
                            item(key = "exp-hdr", span = fullSpan) {
                                SectionHeading("New & Updated", modifier = Modifier.padding(top = 8.dp))
                            }
                            items(platformApps.reversed().take(48), key = { "exp-${it.id}" }) { app ->
                                AppGridTile(app, compactMode, Modifier.padding(2.dp)) { onSelectApp(app) }
                            }
                        }
                        else -> {
                            items(filteredList, key = { "res-${it.id}" }) { app ->
                                AppRowCard(
                                    app = app,
                                    isFavorite = app.id in favorites,
                                    showAbout = prefs.showAppAbout,
                                    downloadState = downloadTasks[app.id],
                                    onClick = { onSelectApp(app) },
                                    onQuickDownload = { onDownloadApp(app) },
                                    onToggleFavorite = { onToggleFavorite(app.id) },
                                    modifier = Modifier.fillMaxWidth()
                                )
                            }
                        }
                    }
                }

                VerticalScrollbar(
                    adapter = rememberScrollbarAdapter(gridState),
                    modifier = Modifier
                        .align(Alignment.CenterEnd)
                        .fillMaxHeight()
                        .padding(end = 4.dp, top = 8.dp, bottom = 80.dp)
                )
            }
        }
    }
}

@Composable
private fun FiltersHeader(
    categories: List<String>,
    selectedCategory: String,
    sortOption: DesktopSort,
    filterFavoritesOnly: Boolean,
    searchQuery: String,
    count: Int,
    onSelectCategory: (String) -> Unit,
    onSelectSort: (DesktopSort) -> Unit,
    onToggleFavoritesOnly: () -> Unit
) {
    val cs = MaterialTheme.colorScheme
    var sortMenuOpen by remember { mutableStateOf(false) }
    val filtered = selectedCategory != "All" || searchQuery.isNotEmpty()

    Column(modifier = Modifier.fillMaxWidth().padding(top = 4.dp, bottom = 6.dp)) {
        LazyRow(
            contentPadding = PaddingValues(horizontal = 20.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            item("sort") {
                val sortActive = sortMenuOpen || sortOption != DesktopSort.NEWEST
                val sortCorner by animateDpAsState(
                    targetValue = if (sortActive) 14.dp else 22.dp,
                    animationSpec = spring(stiffness = Spring.StiffnessMediumLow, dampingRatio = Spring.DampingRatioLowBouncy)
                )
                val sortBg by animateColorAsState(
                    targetValue = if (sortActive) cs.secondaryContainer else cs.surfaceContainerHigh,
                    animationSpec = spring(stiffness = Spring.StiffnessMediumLow)
                )
                val sortFg by animateColorAsState(
                    targetValue = if (sortActive) cs.onSecondaryContainer else cs.onSurfaceVariant,
                    animationSpec = spring(stiffness = Spring.StiffnessMediumLow)
                )

                Box {
                    Surface(
                        shape = RoundedCornerShape(sortCorner),
                        color = sortBg,
                        contentColor = sortFg,
                        modifier = Modifier
                            .height(38.dp)
                            .clickable { sortMenuOpen = true }
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Icon(Icons.Default.SwapVert, "Sort Apps", Modifier.size(18.dp), tint = sortFg)
                            Text(sortOption.label, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold)
                        }
                    }
                    DropdownMenu(sortMenuOpen, { sortMenuOpen = false }, shape = RoundedCornerShape(20.dp)) {
                        Text(
                            "Sort by",
                            Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                            style = MaterialTheme.typography.labelMedium,
                            color = cs.onSurfaceVariant
                        )
                        DesktopSort.entries.forEach { opt ->
                            val on = sortOption == opt
                            DropdownMenuItem(
                                text = { Text(opt.label, fontWeight = if (on) FontWeight.Bold else null) },
                                onClick = { onSelectSort(opt); sortMenuOpen = false },
                                leadingIcon = { RadioButton(on, null) }
                            )
                        }
                    }
                }
            }

            item("fav") {
                val favCorner by animateDpAsState(
                    targetValue = if (filterFavoritesOnly) 14.dp else 22.dp,
                    animationSpec = spring(stiffness = Spring.StiffnessMediumLow, dampingRatio = Spring.DampingRatioLowBouncy)
                )
                val favBg by animateColorAsState(
                    targetValue = if (filterFavoritesOnly) cs.primary else cs.surfaceContainerHigh,
                    animationSpec = spring(stiffness = Spring.StiffnessMediumLow)
                )
                val favFg by animateColorAsState(
                    targetValue = if (filterFavoritesOnly) cs.onPrimary else cs.onSurfaceVariant,
                    animationSpec = spring(stiffness = Spring.StiffnessMediumLow)
                )
                val favScale by animateFloatAsState(
                    targetValue = if (filterFavoritesOnly) 1.05f else 1f,
                    animationSpec = spring(stiffness = Spring.StiffnessMediumLow, dampingRatio = Spring.DampingRatioLowBouncy)
                )
                Surface(
                    shape = RoundedCornerShape(favCorner),
                    color = favBg,
                    contentColor = favFg,
                    modifier = Modifier
                        .size(38.dp)
                        .graphicsLayer {
                            scaleX = favScale
                            scaleY = favScale
                        }
                        .clickable { onToggleFavoritesOnly() }
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            if (filterFavoritesOnly) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                            "Favorites",
                            Modifier.size(18.dp),
                            tint = favFg
                        )
                    }
                }
            }

            item("div") {
                VerticalDivider(Modifier.height(26.dp).padding(horizontal = 4.dp), color = cs.outlineVariant.copy(alpha = 0.5f))
            }

            items(categories, key = { it }) { cat ->
                val isHome = cat == "All"
                val on = selectedCategory == cat
                val cornerRadius by animateDpAsState(
                    targetValue = if (on) 14.dp else 22.dp,
                    animationSpec = spring(stiffness = Spring.StiffnessMediumLow, dampingRatio = Spring.DampingRatioLowBouncy)
                )
                val containerColor by animateColorAsState(
                    targetValue = if (on) cs.primary else cs.surfaceContainerHigh,
                    animationSpec = spring(stiffness = Spring.StiffnessMediumLow)
                )
                val contentColor by animateColorAsState(
                    targetValue = if (on) cs.onPrimary else cs.onSurface,
                    animationSpec = spring(stiffness = Spring.StiffnessMediumLow)
                )
                val scale by animateFloatAsState(
                    targetValue = if (on) 1.04f else 1.0f,
                    animationSpec = spring(stiffness = Spring.StiffnessMediumLow, dampingRatio = Spring.DampingRatioLowBouncy)
                )

                Surface(
                    shape = RoundedCornerShape(cornerRadius),
                    color = containerColor,
                    contentColor = contentColor,
                    border = BorderStroke(1.dp, if (on) cs.primary.copy(alpha = 0.4f) else cs.outlineVariant.copy(alpha = 0.18f)),
                    modifier = Modifier
                        .height(38.dp)
                        .graphicsLayer {
                            scaleX = scale
                            scaleY = scale
                        }
                        .clickable { onSelectCategory(cat) }
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        if (isHome) {
                            Icon(Icons.Default.Home, null, Modifier.size(16.dp), tint = contentColor)
                        }
                        Text(
                            text = if (isHome) "Home" else cat,
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = if (on) FontWeight.Bold else FontWeight.Medium,
                            color = contentColor,
                            maxLines = 1
                        )
                    }
                }
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth().padding(start = 24.dp, end = 16.dp, top = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(Modifier.weight(1f)) {
                if (filtered) {
                    AnimatedContent(
                        targetState = if (selectedCategory == "All") "Search results" else selectedCategory,
                        transitionSpec = {
                            (fadeIn(tween(160)) + slideInVertically(spring(stiffness = Spring.StiffnessMediumLow)) { 12 })
                                .togetherWith(fadeOut(tween(100)))
                        },
                        label = "cat-title-anim"
                    ) { title ->
                        Text(
                            title,
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
                AnimatedContent(
                    targetState = "$count apps · " + (if (searchQuery.isNotEmpty()) "\"$searchQuery\"" else sortOption.label),
                    transitionSpec = {
                        fadeIn(tween(140)).togetherWith(fadeOut(tween(90)))
                    },
                    label = "count-anim"
                ) { label ->
                    Text(
                        label,
                        style = MaterialTheme.typography.labelLarge,
                        color = cs.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            TextButton(
                onClick = { DesktopInstaller.openBrowser("https://github.com/RookieEnough/Orion-Data/issues/new") },
                shape = PillShape
            ) {
                Icon(Icons.Default.AddCircleOutline, null, Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text("Submit app")
            }
        }
    }
}

// -------------------------------------------------------------
// 1. HERO CAROUSEL SECTION (StoreScreen.kt Hero & HeroCard 1:1)
// -------------------------------------------------------------
@Composable
fun HeroCarouselSection(apps: List<AppItem>, onSelectApp: (AppItem) -> Unit) {
    val cs = MaterialTheme.colorScheme
    val scope = rememberCoroutineScope()
    val pager = rememberPagerState { apps.size }

    BoxWithConstraints(Modifier.fillMaxWidth().padding(top = 6.dp)) {
        val gap = 12.dp
        val room = maxWidth - 40.dp
        val cardsVisible = ((room + gap) / (440.dp + gap)).toInt().coerceIn(1, apps.size.coerceAtLeast(1))
        val pageWidth = (room - gap * (cardsVisible - 1)) / cardsVisible

        LaunchedEffect(pager, apps.size, cardsVisible) {
            if (apps.size > cardsVisible) {
                while (true) {
                    delay(5500)
                    if (!pager.isScrollInProgress) {
                        val next = if (pager.currentPage >= apps.size - cardsVisible) 0 else pager.currentPage + 1
                        pager.animateScrollToPage(next)
                    }
                }
            }
        }

        Column {
            Box(Modifier.fillMaxWidth()) {
                HorizontalPager(
                    state = pager,
                    modifier = Modifier.fillMaxWidth().height(220.dp),
                    contentPadding = PaddingValues(horizontal = 20.dp),
                    pageSpacing = gap,
                    pageSize = if (cardsVisible == 1) PageSize.Fill else PageSize.Fixed(pageWidth),
                    key = { apps[it].id }
                ) { i ->
                    val app = apps[i]
                    HeroAppCard(app = app, onClick = { onSelectApp(app) })
                }

                // Subtle Desktop Pager Controls on Left/Right Hover Edges
                if (apps.size > cardsVisible && pager.currentPage > 0) {
                    FilledTonalIconButton(
                        onClick = { scope.launch { pager.animateScrollToPage(pager.currentPage - 1) } },
                        modifier = Modifier.align(Alignment.CenterStart).padding(start = 6.dp).size(36.dp),
                        shape = CircleShape
                    ) { Icon(Icons.Default.ChevronLeft, "Prev") }
                }
                if (apps.size > cardsVisible && pager.currentPage < apps.size - cardsVisible) {
                    FilledTonalIconButton(
                        onClick = { scope.launch { pager.animateScrollToPage(pager.currentPage + 1) } },
                        modifier = Modifier.align(Alignment.CenterEnd).padding(end = 6.dp).size(36.dp),
                        shape = CircleShape
                    ) { Icon(Icons.Default.ChevronRight, "Next") }
                }
            }

            // Dot Indicator Row matching StoreScreen.kt lines 422-430
            if (apps.size > cardsVisible) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterHorizontally)
                ) {
                    repeat(apps.size) { j ->
                        val on = j == pager.currentPage
                        val w by animateDpAsState(if (on) 22.dp else 6.dp)
                        Box(Modifier.height(6.dp).width(w).clip(PillShape).background(if (on) cs.primary else cs.outlineVariant))
                    }
                }
            }
        }
    }
}

@Composable
private fun HeroAppCard(app: AppItem, onClick: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    val extracted by produceState<Color?>(null, app.icon) {
        value = DesktopCatalogRepository.extractIconColor(app.icon)
    }
    val (bg0, fg0) = tonalPair(extracted ?: appSeedAccent(app.id))
    val bg by animateColorAsState(bg0)
    val fg by animateColorAsState(fg0)
    val shape = RoundedCornerShape(28.dp)

    Box(
        modifier = Modifier
            .fillMaxSize()
            .expressiveSpringClick(shape, onClick = onClick)
            .background(Brush.verticalGradient(listOf(bg, androidx.compose.ui.graphics.lerp(bg, fg, 0.10f))))
    ) {
        // Cookie12Sided faint watermark in bottom right (exact StoreScreen.kt line 442)
        Box(
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .offset(x = 46.dp, y = 62.dp)
                .size(196.dp)
                .background(fg.copy(alpha = 0.08f), OrionShapes.Cookie12Sided)
        )

        // Fanned Screenshots OR Large Tilted Icon Fallback (exact StoreScreen.kt lines 444-458)
        val wideShot = app.platform != Platform.ANDROID
        val shots = app.screenshots.take(if (wideShot) 1 else 2)
        Box(
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(end = if (wideShot) 14.dp else 20.dp)
                .offset(y = if (wideShot) 20.dp else 36.dp)
        ) {
            if (shots.isEmpty()) {
                DesktopAppIcon(
                    url = app.icon,
                    name = app.name,
                    size = 96.dp,
                    shape = RoundedCornerShape(30.dp),
                    modifier = Modifier
                        .offset(x = (-14).dp, y = (-48).dp)
                        .graphicsLayer { rotationZ = -10f; shadowElevation = 12.dp.toPx() }
                )
            } else {
                shots.forEachIndexed { k, u ->
                    val back = k == 0 && shots.size > 1
                    Box(
                        modifier = Modifier
                            .size(width = if (wideShot) 186.dp else 94.dp, height = if (wideShot) 120.dp else 176.dp)
                            .offset(x = if (back) (-52).dp else 0.dp, y = if (back) 12.dp else 0.dp)
                            .graphicsLayer {
                                rotationZ = if (back) -7f else 4f
                                shadowElevation = 10.dp.toPx()
                                this.shape = RoundedCornerShape(16.dp)
                                clip = true
                            }
                            .background(cs.surfaceContainer)
                    ) {
                        AsyncImage(
                            model = u,
                            contentDescription = null,
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.Crop
                        )
                    }
                }
            }
        }

        // Left Text + Small Icon Column (exact StoreScreen.kt lines 459-464)
        Column(
            modifier = Modifier
                .fillMaxHeight()
                .fillMaxWidth(0.56f)
                .padding(20.dp)
        ) {
            DesktopAppIcon(app.icon, app.name, 48.dp)
            Spacer(Modifier.weight(1f))
            Text(
                app.name,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = fg,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                listOf(app.category, app.author).filter { it.isNotEmpty() }.joinToString(" · "),
                style = MaterialTheme.typography.labelLarge,
                color = fg.copy(alpha = 0.78f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(Modifier.height(10.dp))
            // Primary CTA pill with high-contrast label
            Row(
                modifier = Modifier
                    .clip(PillShape)
                    .background(fg.copy(alpha = 0.16f))
                    .clickable(onClick = onClick)
                    .padding(horizontal = 14.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    "Explore",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold,
                    color = fg
                )
                Spacer(Modifier.width(6.dp))
                Icon(Icons.AutoMirrored.Filled.ArrowForward, null, Modifier.size(16.dp), tint = fg)
            }
        }
    }
}

// -------------------------------------------------------------
// 2. RECOMMENDED FOR YOU (Signature Icon-Tinted Cards + Animated Expressive Blobs!)
// -------------------------------------------------------------
@Composable
fun RecommendedMultiBrowseSection(
    c: StoreCollection,
    smoothMotion: Boolean = true,
    onSelectApp: (AppItem) -> Unit
) {
    val shapes = listOf(
        OrionShapes.Cookie9Sided,
        OrionShapes.Sunny,
        OrionShapes.Flower,
        OrionShapes.SoftBurst,
        OrionShapes.Clover4Leaf,
        OrionShapes.Gem
    )

    Column(Modifier.padding(top = 20.dp)) {
        SectionHeading(c.title, subtitle = c.subtitle)
        LazyRow(
            contentPadding = PaddingValues(horizontal = 20.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            itemsIndexed(c.apps, key = { _, it -> "rec-${it.id}" }) { i, app ->
                RecommendedTonalCard(
                    app = app,
                    index = i,
                    blobShape = shapes[i % shapes.size],
                    smoothMotion = smoothMotion,
                    onClick = { onSelectApp(app) }
                )
            }
        }
    }
}

@Composable
private fun RecommendedTonalCard(
    app: AppItem,
    index: Int = 0,
    blobShape: Shape = OrionShapes.Cookie9Sided,
    smoothMotion: Boolean = true,
    onClick: () -> Unit
) {
    val extracted by produceState<Color?>(null, app.icon) {
        value = DesktopCatalogRepository.extractIconColor(app.icon)
    }
    val (bg0, fg0) = tonalPair(extracted ?: appSeedAccent(app.id))
    val bg by animateColorAsState(bg0)
    val fg by animateColorAsState(fg0)

    // Ambient slow rotation: alternating direction per card for a dynamic alive carousel
    val isEven = index % 2 == 0
    val targetDeg = if (isEven) 360f else -360f
    val spin by rememberInfiniteTransition(label = "rec-spin-$index").animateFloat(
        initialValue = 0f,
        targetValue = if (smoothMotion) targetDeg else 0f,
        animationSpec = infiniteRepeatable(
            animation = tween(
                durationMillis = 20_000 + (index % 4) * 3_000,
                easing = LinearEasing
            )
        ),
        label = "rec-deg-$index"
    )

    // Breathing pulse scale
    val pulse by rememberInfiniteTransition(label = "rec-pulse-$index").animateFloat(
        initialValue = 0.94f,
        targetValue = 1.05f,
        animationSpec = infiniteRepeatable(
            animation = tween(
                durationMillis = 3_000 + (index % 3) * 600,
                easing = FastOutSlowInEasing
            ),
            repeatMode = RepeatMode.Reverse
        ),
        label = "rec-scale-$index"
    )

    Box(
        modifier = Modifier
            .size(width = 184.dp, height = 224.dp)
            .expressiveSpringClick(RoundedCornerShape(28.dp), onClick = onClick)
            .background(Brush.verticalGradient(listOf(bg, androidx.compose.ui.graphics.lerp(bg, fg, 0.10f))))
    ) {
        // Rotating & pulsing expressive scalloped blob centered behind icon!
        Box(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(top = 20.dp)
                .size(134.dp)
                .graphicsLayer {
                    rotationZ = spin
                    scaleX = if (smoothMotion) pulse else 1f
                    scaleY = if (smoothMotion) pulse else 1f
                }
                .background(fg.copy(alpha = 0.16f), blobShape)
        )
        DesktopAppIcon(
            url = app.icon,
            name = app.name,
            size = 84.dp,
            shape = RoundedCornerShape(24.dp),
            modifier = Modifier.align(Alignment.TopCenter).padding(top = 46.dp)
        )
        Column(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Text(
                app.name,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = fg,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                app.category,
                style = MaterialTheme.typography.labelMedium,
                color = fg.copy(alpha = 0.80f),
                maxLines = 1
            )
        }
    }
}

// -------------------------------------------------------------
// 3. SWIMLANE (New & Updated + Top in Category)
// -------------------------------------------------------------
@Composable
fun SwimlaneSection(
    c: StoreCollection,
    compact: Boolean,
    onSeeMore: (() -> Unit)?,
    onSelectApp: (AppItem) -> Unit
) {
    Column(Modifier.padding(top = 20.dp)) {
        SectionHeading(c.title, subtitle = c.subtitle, onMore = onSeeMore)
        LazyRow(
            contentPadding = PaddingValues(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            items(c.apps, key = { "${c.id}-${it.id}" }) { app ->
                AppGridTile(
                    app = app,
                    compact = compact,
                    modifier = Modifier.width(if (compact) 84.dp else 100.dp),
                    onClick = { onSelectApp(app) }
                )
            }
        }
    }
}

@Composable
fun AppGridTile(app: AppItem, compact: Boolean, modifier: Modifier = Modifier, onClick: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    val featured = app.tags.any { it == "Featured" || it == "Editor's Choice" }
    Column(
        modifier = modifier
            .expressiveSpringClick(RoundedCornerShape(20.dp), onClick = onClick)
            .padding(if (compact) 6.dp else 8.dp)
    ) {
        DesktopAppIcon(
            url = app.icon,
            name = app.name,
            size = if (compact) 72.dp else 84.dp,
            shape = RoundedCornerShape(24.dp),
            modifier = Modifier.align(Alignment.CenterHorizontally)
        )
        Spacer(Modifier.height(if (compact) 6.dp else 8.dp))
        val lines = if (compact) 1 else 2
        Text(
            app.name,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Medium,
            minLines = lines,
            maxLines = lines,
            overflow = TextOverflow.Ellipsis
        )
        Text(
            if (featured) "Featured" else app.category.ifEmpty { app.size },
            style = MaterialTheme.typography.labelMedium,
            maxLines = 1,
            color = if (featured) cs.primary else cs.onSurfaceVariant
        )
    }
}

// -------------------------------------------------------------
// 4. BROWSE BY CATEGORY BLOBS (StoreScreen.kt Categories 1:1)
// -------------------------------------------------------------
@Composable
fun CategoryBlobsSection(c: StoreCollection, onSelectCategory: (String) -> Unit) {
    val cs = MaterialTheme.colorScheme
    Column(Modifier.padding(top = 20.dp)) {
        if (c.title.isNotEmpty()) SectionHeading(c.title, subtitle = c.subtitle)
        LazyRow(
            contentPadding = PaddingValues(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            itemsIndexed(c.categoryCards, key = { _, it -> it.id }) { i, card ->
                val (bg, fg) = tonalPair(parseHexColor(card.accent, cs.primary))
                val shape = OrionShapes.BLOBS[i % OrionShapes.BLOBS.size]
                Column(
                    modifier = Modifier
                        .width(120.dp)
                        .expressiveSpringClick(RoundedCornerShape(24.dp)) { onSelectCategory(card.label) }
                        .padding(vertical = 8.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Box(
                        modifier = Modifier.size(84.dp).background(bg, shape),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(faIcon(card.icon), null, Modifier.size(34.dp), tint = fg)
                    }
                    Text(
                        card.label,
                        modifier = Modifier.padding(top = 8.dp, start = 4.dp, end = 4.dp),
                        style = MaterialTheme.typography.titleSmall,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        textAlign = TextAlign.Center
                    )
                    Text("${card.count} apps", style = MaterialTheme.typography.labelMedium, color = cs.onSurfaceVariant)
                }
            }
        }
    }
}

// -------------------------------------------------------------
// 5. CURATED SPOTLIGHT NUMBERED PICKS (StoreScreen.kt Spotlight)
// -------------------------------------------------------------
@Composable
fun SpotlightNumberedSection(c: StoreCollection, onSelectApp: (AppItem) -> Unit) {
    val cs = MaterialTheme.colorScheme
    val picks = c.apps.take(6)
    Column(Modifier.padding(top = 20.dp)) {
        SectionHeading(c.title, subtitle = c.subtitle)
        Column(Modifier.padding(horizontal = 12.dp)) {
            picks.chunked(3).forEachIndexed { r, row ->
                Row(Modifier.fillMaxWidth()) {
                    row.forEachIndexed { k, app ->
                        val i = r * 3 + k
                        Row(
                            modifier = Modifier
                                .weight(1f)
                                .expressiveSpringClick(RoundedCornerShape(20.dp)) { onSelectApp(app) }
                                .padding(horizontal = 12.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                "${i + 1}",
                                modifier = Modifier.width(30.dp),
                                style = MaterialTheme.typography.headlineSmall,
                                fontWeight = FontWeight.Bold,
                                color = cs.primary
                            )
                            DesktopAppIcon(app.icon, app.name, 56.dp, RoundedCornerShape(24.dp))
                            Spacer(Modifier.width(14.dp))
                            Column(Modifier.weight(1f)) {
                                Text(app.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                Text(
                                    listOf(app.category, app.size).filter { it.isNotEmpty() }.joinToString(" · "),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = cs.onSurfaceVariant,
                                    maxLines = 1
                                )
                            }
                        }
                    }
                    repeat(3 - row.size) { Spacer(Modifier.weight(1f)) }
                }
            }
        }
    }
}

// -------------------------------------------------------------
// 6. RECOMMENDED APP BUNDLES FOLDERS (StoreScreen.kt Bundles 1:1)
// -------------------------------------------------------------
@Composable
fun BundlesFolderSection(c: StoreCollection, onSelectBundle: (BundleItem) -> Unit) {
    val cs = MaterialTheme.colorScheme
    val folderShape = RoundedCornerShape(36.dp)
    Column(Modifier.padding(top = 20.dp)) {
        SectionHeading(c.title, subtitle = c.subtitle)
        LazyRow(
            contentPadding = PaddingValues(horizontal = 20.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            itemsIndexed(c.bundles, key = { _, b -> b.id }) { i, b ->
                val bg = b.color?.let { tonalPair(parseHexColor(it, cs.primary)).first }
                    ?: listOf(cs.primaryContainer, cs.tertiaryContainer, cs.secondaryContainer)[i % 3]
                Column(
                    modifier = Modifier
                        .width(220.dp)
                        .expressiveSpringClick(RoundedCornerShape(28.dp)) { onSelectBundle(b) }
                        .padding(bottom = 8.dp)
                ) {
                    Box(
                        modifier = Modifier.size(212.dp).clip(folderShape).background(bg),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            b.apps.take(4).chunked(2).forEachIndexed { r, row ->
                                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                    row.forEachIndexed { k, a ->
                                        DesktopAppIcon(
                                            url = a.icon,
                                            name = a.name,
                                            size = 72.dp,
                                            shape = RoundedCornerShape(72.dp * .24f),
                                            modifier = Modifier.graphicsLayer { rotationZ = BUNDLE_FAN[(r * 2 + k) % BUNDLE_FAN.size] }
                                        )
                                    }
                                }
                            }
                        }
                        b.badge?.let {
                            Badge(
                                modifier = Modifier.align(Alignment.TopEnd).padding(14.dp),
                                containerColor = cs.primary,
                                contentColor = cs.onPrimary
                            ) {
                                Text(it, Modifier.padding(horizontal = 6.dp, vertical = 2.dp))
                            }
                        }
                    }
                    Text(
                        b.title,
                        modifier = Modifier.padding(start = 8.dp, end = 8.dp, top = 10.dp),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        "${b.apps.size} apps · ${b.description}",
                        modifier = Modifier.padding(horizontal = 8.dp),
                        style = MaterialTheme.typography.labelMedium,
                        color = cs.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
}

// -------------------------------------------------------------
// 7. RANKED CHARTS (Most Popular & Hidden Gems)
// -------------------------------------------------------------
@Composable
fun RankedChartsSection(c: StoreCollection, onSelectApp: (AppItem) -> Unit) {
    val cs = MaterialTheme.colorScheme
    Column(Modifier.padding(top = 20.dp)) {
        SectionHeading(c.title, subtitle = c.subtitle)
        val groups = c.apps.chunked(3)
        LazyRow(
            contentPadding = PaddingValues(horizontal = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            itemsIndexed(groups) { col, group ->
                Column(Modifier.width(320.dp)) {
                    group.forEachIndexed { k, app ->
                        val rank = col * 3 + k
                        val top = rank < 3
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .expressiveSpringClick(RoundedCornerShape(18.dp)) { onSelectApp(app) }
                                .padding(horizontal = 10.dp, vertical = 7.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(34.dp)
                                    .background(
                                        if (top) cs.primaryContainer else cs.surfaceContainerHigh,
                                        OrionShapes.RANK_SHAPES[rank % OrionShapes.RANK_SHAPES.size]
                                    ),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    "${rank + 1}",
                                    style = MaterialTheme.typography.labelLarge,
                                    fontWeight = FontWeight.Bold,
                                    color = if (top) cs.onPrimaryContainer else cs.onSurfaceVariant
                                )
                            }
                            Spacer(Modifier.width(12.dp))
                            DesktopAppIcon(app.icon, app.name, 52.dp, RoundedCornerShape(52.dp * .24f))
                            Column(Modifier.weight(1f).padding(start = 12.dp)) {
                                Text(app.name, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Default.Star, null, Modifier.size(14.dp), tint = cs.tertiary)
                                    Spacer(Modifier.width(4.dp))
                                    Text(compactCount(app.stars ?: 0), style = MaterialTheme.typography.labelMedium, color = cs.onSurfaceVariant, maxLines = 1)
                                    if (app.category.isNotEmpty()) {
                                        Text(" · ${app.category}", style = MaterialTheme.typography.labelMedium, color = cs.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

// -------------------------------------------------------------
// APP ROW CARD + STATEBLOB (Filtered / Search Results)
// -------------------------------------------------------------
@Composable
fun AppRowCard(
    app: AppItem,
    isFavorite: Boolean,
    showAbout: Boolean,
    downloadState: DownloadState?,
    onClick: () -> Unit,
    onQuickDownload: () -> Unit,
    onToggleFavorite: () -> Unit,
    modifier: Modifier = Modifier
) {
    val cs = MaterialTheme.colorScheme
    val about = remember(app.description) { app.description.replace(SPACES_REGEX, " ").trim() }

    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(22.dp),
        color = cs.surfaceContainer,
        modifier = modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            DesktopAppIcon(app.icon, app.name, 52.dp, RoundedCornerShape(52.dp * .24f))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Surface(shape = PillShape, color = cs.primary.copy(alpha = 0.12f)) {
                        Row(
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Icon(categoryIcon(app.category), null, Modifier.size(12.dp), tint = cs.primary)
                            Text(app.category, style = MaterialTheme.typography.labelSmall, color = cs.primary, maxLines = 1)
                        }
                    }
                    app.stars?.takeIf { it > 0 }?.let {
                        Text("★ ${compactCount(it)}", style = MaterialTheme.typography.labelSmall, color = cs.onSurfaceVariant)
                    }
                }
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        app.name,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, false)
                    )
                    if (isFavorite) Icon(Icons.Default.Favorite, null, Modifier.size(14.dp), tint = cs.primary)
                }
                Text(
                    downloadState?.status ?: listOf(app.author, app.latestVersion, app.size).filter { it.isNotBlank() && it != "Varies" }.joinToString(" · "),
                    style = MaterialTheme.typography.bodySmall,
                    color = if (downloadState != null) cs.primary else cs.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                if (showAbout && about.isNotEmpty()) {
                    Text(
                        about,
                        style = MaterialTheme.typography.bodySmall,
                        color = cs.onSurfaceVariant.copy(alpha = 0.78f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            // Expressive StateBlob action button (Cookie6Sided / Gem / Clover)
            val blobShape = when {
                downloadState?.isComplete == true -> OrionShapes.Clover4Leaf
                app.downloadUrl.isNotBlank() && app.downloadUrl != "#" -> OrionShapes.Cookie6Sided
                else -> OrionShapes.Gem
            }
            val blobBg = if (downloadState?.isComplete == true) cs.secondaryContainer else cs.surfaceContainerHighest
            val blobFg = if (downloadState?.isComplete == true) cs.onSecondaryContainer else cs.onSurfaceVariant

            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(blobShape)
                    .background(blobBg)
                    .clickable(onClick = onQuickDownload),
                contentAlignment = Alignment.Center
            ) {
                if (downloadState != null && !downloadState.isComplete && downloadState.error == null) {
                    CircularProgressIndicator(Modifier.size(22.dp), strokeWidth = 2.dp, color = cs.primary)
                } else {
                    Icon(
                        if (downloadState?.isComplete == true) Icons.Default.Check else Icons.Default.Download,
                        contentDescription = "Download",
                        modifier = Modifier.size(20.dp),
                        tint = blobFg
                    )
                }
            }
        }
    }
}

// -------------------------------------------------------------
// NAVIGATION: the dock styles and the wide-window side rail
// (Root.kt FloatingNav + Home's NavigationRail)
// -------------------------------------------------------------

/** Every dock look Orion ships on Android, in the order the desktop offers them. */
val DOCK_STYLES = listOf(
    "pill" to "Pill",
    "labels" to "Labeled",
    "shapes" to "Shape shift",
    "bar" to "Classic",
    "vibrant" to "Expressive"
)

/** Each tab owns an expressive shape, the way Orion morphs its dock indicators. */
private val DOCK_SHAPES = listOf(
    OrionShapes.Cookie9Sided,
    OrionShapes.Clover4Leaf,
    OrionShapes.Cookie6Sided,
    OrionShapes.Flower,
    OrionShapes.Pentagon,
    OrionShapes.Gem
)

@Composable
fun OrionDock(
    currentTab: DesktopTab,
    style: String,
    downloadsBadge: Int,
    glassEffect: Boolean,
    dockScale: Float,
    visible: Boolean,
    onSelectTab: (DesktopTab) -> Unit,
    modifier: Modifier = Modifier
) {
    val cs = MaterialTheme.colorScheme
    val scale by animateFloatAsState(
        targetValue = dockScale,
        animationSpec = spring(dampingRatio = 0.7f, stiffness = Spring.StiffnessMediumLow)
    )
    // The dock slides out of the way while the content scrolls down, exactly like Orion's.
    val drop by animateFloatAsState(
        targetValue = if (visible) 0f else 1f,
        animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
        label = "dock-drop"
    )
    val slide = Modifier.graphicsLayer { translationY = drop * (size.height + 64.dp.toPx()) }

    when (style) {
        // "Classic": a bar across the bottom, but never stretched edge to edge on a wide window
        "bar" -> Surface(
            modifier = modifier
                .then(slide)
                .graphicsLayer { scaleX = scale; scaleY = scale }
                .widthIn(max = 560.dp),
            shape = RoundedCornerShape(28.dp),
            color = cs.surfaceContainer,
            shadowElevation = 8.dp
        ) {
            Row(Modifier.padding(horizontal = 8.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                DesktopTab.entries.forEach { tab ->
                    DockTabItem(tab, currentTab == tab, downloadsBadge, onSelectTab, showLabel = true, iconOnly = false)
                }
            }
        }
        // "Expressive": a floating toolbar whose picked tab sits on a rounded square
        "vibrant" -> Surface(
            modifier = modifier
                .then(slide)
                .graphicsLayer { scaleX = scale; scaleY = scale },
            shape = RoundedCornerShape(28.dp),
            color = cs.surfaceContainerHigh.copy(alpha = if (glassEffect) 0.9f else 1f),
            shadowElevation = 10.dp,
            border = BorderStroke(1.dp, cs.outlineVariant.copy(alpha = if (glassEffect) 0.3f else 0.15f))
        ) {
            Row(Modifier.padding(6.dp), horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
                DesktopTab.entries.forEach { tab ->
                    val on = currentTab == tab
                    val fg by animateColorAsState(
                        targetValue = if (on) cs.onSecondaryContainer else cs.onSurfaceVariant,
                        animationSpec = spring(stiffness = Spring.StiffnessMediumLow)
                    )
                    Box(
                        modifier = Modifier
                            .size(46.dp)
                            .clip(RoundedCornerShape(if (on) 16.dp else 24.dp))
                            .background(if (on) cs.secondaryContainer else Color.Transparent)
                            .selectable(selected = on, role = Role.Tab) { onSelectTab(tab) },
                        contentAlignment = Alignment.Center
                    ) {
                        CompositionLocalProvider(LocalContentColor provides fg) {
                            DockIcon(tab, downloadsBadge)
                        }
                    }
                }
            }
        }
        else -> Surface(
            modifier = modifier
                .then(slide)
                .graphicsLayer { scaleX = scale; scaleY = scale },
            shape = if (style == "labels") RoundedCornerShape(28.dp) else PillShape,
            color = cs.surfaceContainerHigh.copy(alpha = if (glassEffect) 0.82f else 1f),
            shadowElevation = 12.dp,
            border = BorderStroke(1.dp, cs.outlineVariant.copy(alpha = if (glassEffect) 0.35f else 0.2f))
        ) {
            when (style) {
                // "Labeled": the icon over its name, every tab
                "labels" -> Row(
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    DesktopTab.entries.forEach { tab ->
                        DockTabItem(tab, currentTab == tab, downloadsBadge, onSelectTab, showLabel = true, iconOnly = false)
                    }
                }
                // "Shape shift": the picked tab's indicator grows into its own expressive shape
                "shapes" -> Row(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    DesktopTab.entries.forEachIndexed { index, tab ->
                        val on = currentTab == tab
                        val shape = DOCK_SHAPES[index % DOCK_SHAPES.size]
                        val grow by animateFloatAsState(
                            targetValue = if (on) 1f else 0.5f,
                            animationSpec = spring(dampingRatio = 0.6f, stiffness = Spring.StiffnessMediumLow)
                        )
                        val fg by animateColorAsState(
                            targetValue = if (on) cs.onSecondaryContainer else cs.onSurfaceVariant,
                            animationSpec = spring(stiffness = Spring.StiffnessMediumLow)
                        )
                        Box(
                            modifier = Modifier
                                .size(40.dp)
                                .clip(shape)
                                .background(if (on) cs.secondaryContainer else Color.Transparent)
                                .selectable(selected = on, role = Role.Tab) { onSelectTab(tab) },
                            contentAlignment = Alignment.Center
                        ) {
                            CompositionLocalProvider(LocalContentColor provides fg) {
                                Box(Modifier.graphicsLayer { scaleX = grow; scaleY = grow }) {
                                    DockIcon(tab, downloadsBadge)
                                }
                            }
                        }
                    }
                }
                // "Pill": the icon, with the picked tab's name sliding out beside it
                else -> Row(
                    modifier = Modifier.padding(6.dp),
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    DesktopTab.entries.forEach { tab ->
                        DockTabItem(tab, currentTab == tab, downloadsBadge, onSelectTab, showLabel = false, iconOnly = false)
                    }
                }
            }
        }
    }
}

/** One dock entry: the icon, its badge, and the label that slides out when it is picked. */
@Composable
private fun DockTabItem(
    tab: DesktopTab,
    on: Boolean,
    downloadsBadge: Int,
    onSelectTab: (DesktopTab) -> Unit,
    showLabel: Boolean,
    iconOnly: Boolean
) {
    val cs = MaterialTheme.colorScheme
    val bg by animateColorAsState(
        targetValue = if (on) cs.secondaryContainer else Color.Transparent,
        animationSpec = spring(stiffness = Spring.StiffnessMediumLow)
    )
    val fg by animateColorAsState(
        targetValue = if (on) cs.onSecondaryContainer else cs.onSurfaceVariant,
        animationSpec = spring(stiffness = Spring.StiffnessMediumLow)
    )

    Row(
        modifier = Modifier
            .height(50.dp)
            .clip(PillShape)
            .background(bg)
            .selectable(selected = on, role = Role.Tab) { onSelectTab(tab) }
            .padding(horizontal = if (showLabel) 12.dp else 16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        CompositionLocalProvider(LocalContentColor provides fg) {
            DockIcon(tab, downloadsBadge)
        }
        if (showLabel) {
            Text(
                tab.label,
                modifier = Modifier.padding(start = 8.dp),
                style = MaterialTheme.typography.labelLarge,
                fontWeight = if (on) FontWeight.SemiBold else FontWeight.Normal,
                color = fg,
                maxLines = 1
            )
        } else {
            AnimatedVisibility(
                visible = on,
                enter = expandHorizontally(spring(stiffness = Spring.StiffnessMediumLow)) + fadeIn(),
                exit = shrinkHorizontally(spring(stiffness = Spring.StiffnessMediumLow)) + fadeOut()
            ) {
                Text(
                    tab.label,
                    modifier = Modifier.padding(start = 8.dp),
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold,
                    color = fg,
                    maxLines = 1
                )
            }
        }
    }
}

@Composable
private fun DockIcon(tab: DesktopTab, downloadsBadge: Int) {
    if (tab == DesktopTab.MY_APPS && downloadsBadge > 0) {
        BadgedBox(badge = { Badge { Text("$downloadsBadge") } }) {
            Icon(tab.icon, tab.label, Modifier.size(20.dp))
        }
    } else {
        Icon(tab.icon, tab.label, Modifier.size(20.dp))
    }
}

/** The side rail Orion puts on tablets, foldables and any window wider than 840dp. */
@Composable
fun OrionSideRail(
    currentTab: DesktopTab,
    downloadsBadge: Int,
    onSelectTab: (DesktopTab) -> Unit,
    modifier: Modifier = Modifier
) {
    val cs = MaterialTheme.colorScheme
    NavigationRail(
        modifier = modifier,
        containerColor = cs.surfaceContainer,
        header = { Spacer(Modifier.height(12.dp)) }
    ) {
        Spacer(Modifier.weight(1f))
        DesktopTab.entries.forEach { tab ->
            val on = currentTab == tab
            NavigationRailItem(
                selected = on,
                onClick = { onSelectTab(tab) },
                icon = {
                    val fg by animateColorAsState(
                        targetValue = if (on) cs.onSecondaryContainer else cs.onSurfaceVariant,
                        animationSpec = spring(stiffness = Spring.StiffnessMediumLow)
                    )
                    CompositionLocalProvider(LocalContentColor provides fg) {
                        DockIcon(tab, downloadsBadge)
                    }
                },
                label = { Text(tab.label, maxLines = 1) },
                alwaysShowLabel = false
            )
        }
        Spacer(Modifier.weight(1f))
    }
}

// -------------------------------------------------------------
// SHARED UI HELPERS
// -------------------------------------------------------------

/** Orion's snackbar: a tonal cookie card with the message, not a plain white bar. */
@Composable
private fun OrionSnackCard(message: String, modifier: Modifier = Modifier) {
    val cs = MaterialTheme.colorScheme
    Surface(
        modifier = modifier.widthIn(max = 560.dp),
        shape = RoundedCornerShape(28.dp),
        color = cs.primaryContainer,
        contentColor = cs.onPrimaryContainer,
        shadowElevation = 6.dp
    ) {
        Row(
            modifier = Modifier.padding(start = 12.dp, end = 16.dp, top = 10.dp, bottom = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                Modifier.size(40.dp).clip(OrionShapes.Cookie9Sided).background(cs.primary),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Default.Info, null, Modifier.size(20.dp), tint = cs.onPrimary)
            }
            Text(
                message,
                Modifier.weight(1f).padding(horizontal = 12.dp),
                style = MaterialTheme.typography.bodyMedium,
                color = cs.onPrimaryContainer,
                maxLines = 4
            )
        }
    }
}
@Composable
fun SectionHeading(
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    onMore: (() -> Unit)? = null
) {
    Row(
        modifier = modifier.fillMaxWidth().padding(start = 24.dp, end = 16.dp, top = 8.dp, bottom = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            subtitle?.let {
                Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
        }
        onMore?.let {
            FilledTonalIconButton(onClick = it, shape = CircleShape) {
                Icon(Icons.AutoMirrored.Filled.ArrowForward, "See all $title")
            }
        }
    }
}

/**
 * App icon, drawn exactly like the Android app's RemoteIcon: the image itself with no backing
 * box, a right-sized copy through the image proxy first and the original only if that fails, a
 * skeleton while it loads, and a hued monogram as the last resort.
 */
@Composable
fun DesktopAppIcon(
    url: String,
    name: String,
    size: Dp,
    shape: androidx.compose.ui.graphics.Shape = RoundedCornerShape(size * .24f),
    modifier: Modifier = Modifier
) {
    val cs = MaterialTheme.colorScheme
    // Stage 0: the proxied copy. Stage 1: the original URL. Stage 2: the monogram.
    var stage by remember(url) { mutableIntStateOf(if (url.isBlank()) 2 else 0) }
    val proxied = remember(url) { optimizedImageUrl(url, 256, 256) }
    val skeleton = remember(cs.surfaceContainerHighest) { ColorPainter(cs.surfaceContainerHighest) }

    if (stage < 2) {
        AsyncImage(
            model = if (stage == 0) proxied else url,
            contentDescription = name,
            modifier = modifier.size(size).clip(shape),
            placeholder = skeleton,
            contentScale = ContentScale.Crop,
            onError = { stage = (stage + 1).coerceAtMost(2) }
        )
    } else {
        val h = remember(name) { hue(name) }
        val light = cs.background.luminance() > 0.5f
        Box(
            modifier = modifier.size(size).clip(shape)
                .background(hsl(h, .5f, if (light) .87f else .28f)),
            contentAlignment = Alignment.Center
        ) {
            Text(
                name.take(1).uppercase(),
                fontSize = (size.value * .42f).sp,
                fontWeight = FontWeight.Black,
                color = hsl(h, .6f, if (light) .36f else .8f)
            )
        }
    }
}

@Composable
private fun StoreSkeletonView() = Column(Modifier.fillMaxWidth().padding(20.dp), verticalArrangement = Arrangement.spacedBy(20.dp)) {
    // A named loading state, so the moment between the splash and the catalog reads as a step
    // rather than an empty window.
    Row(
        Modifier.fillMaxWidth().clip(PillShape).background(MaterialTheme.colorScheme.surfaceContainer)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            Modifier.size(26.dp).clip(OrionShapes.Cookie9Sided).background(MaterialTheme.colorScheme.primaryContainer),
            contentAlignment = Alignment.Center
        ) {
            Icon(Icons.Default.CloudDownload, null, Modifier.size(15.dp), tint = MaterialTheme.colorScheme.onPrimaryContainer)
        }
        Spacer(Modifier.width(12.dp))
        Text(
            "Loading the Orion catalog…",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        repeat(2) {
            Box(Modifier.weight(1f).height(192.dp).clip(RoundedCornerShape(28.dp)).background(MaterialTheme.colorScheme.surfaceContainerHigh))
        }
    }
    repeat(2) {
        Box(Modifier.size(200.dp, 24.dp).clip(PillShape).background(MaterialTheme.colorScheme.surfaceContainerHigh))
        Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            repeat(6) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Box(Modifier.size(92.dp).clip(RoundedCornerShape(22.dp)).background(MaterialTheme.colorScheme.surfaceContainerHigh))
                    Box(Modifier.size(72.dp, 12.dp).clip(PillShape).background(MaterialTheme.colorScheme.surfaceContainerHigh))
                }
            }
        }
    }
}

@Composable
private fun EmptyStoreState(icon: ImageVector, title: String, body: String?) = Column(
    modifier = Modifier.fillMaxWidth().padding(vertical = 64.dp, horizontal = 32.dp),
    horizontalAlignment = Alignment.CenterHorizontally
) {
    Box(
        modifier = Modifier.size(96.dp).background(MaterialTheme.colorScheme.surfaceContainerHigh, OrionShapes.Cookie9Sided),
        contentAlignment = Alignment.Center
    ) {
        Icon(icon, null, Modifier.size(40.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
    }
    Spacer(Modifier.height(16.dp))
    Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
    body?.let {
        Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 6.dp))
    }
}
