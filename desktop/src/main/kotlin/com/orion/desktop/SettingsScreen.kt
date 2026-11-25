package com.orion.desktop

import androidx.compose.animation.*
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.filled.Label
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.toComposeImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.orion.store.data.AppItem
import com.orion.store.data.Platform
import com.orion.store.data.json
import com.orion.store.ui.*
import kotlinx.coroutines.launch
import java.io.File
import javax.imageio.ImageIO

private val BlueC = Color(0xFF3B82F6)
private val PurpleC = Color(0xFFA855F7)
private val SkyC = Color(0xFF38BDF8)
private val OrangeC = Color(0xFFF97316)
private val GreenC = Color(0xFF22C55E)
private val PinkC = Color(0xFFEC4899)
private val TealC = Color(0xFF14B8A6)
private val YellowC = Color(0xFFEAB308)
private val IndigoC = Color(0xFF6366F1)

private val LocalSettingsAccent = compositionLocalOf { BlueC }

private data class SettingsCat(
    val id: String,
    val icon: ImageVector,
    val color: Color,
    val title: String,
    val desc: String,
    val shape: Shape
)

private val SETTINGS_CATS = listOf(
    SettingsCat("queue", Icons.Default.Downloading, BlueC, "Downloads", "Queue, installs and pending updates", OrionShapes.Cookie9Sided),
    SettingsCat("visuals", Icons.Default.Palette, PurpleC, "Look & feel", "Theme, font, motion and density", OrionShapes.Flower),
    SettingsCat("network", Icons.Default.Wifi, SkyC, "Network", "Wi-Fi rules and auto-update", OrionShapes.Clover4Leaf),
    SettingsCat("storage", Icons.Default.CleaningServices, OrangeC, "Storage", "Janitor and saved installers", OrionShapes.Sunny),
    SettingsCat("installer", Icons.Default.Shield, GreenC, "Orion Xtra", "Guardian, Sentinel and installer", OrionShapes.Gem),
    SettingsCat("identity", Icons.Default.Backup, PinkC, "Backup", "Save or restore your identity", OrionShapes.Cookie6Sided),
    SettingsCat("interface", Icons.Default.ViewQuilt, TealC, "Interface", "Store layout and visible tabs", OrionShapes.Pentagon),
    SettingsCat("developer", Icons.Default.Terminal, YellowC, "Developer", "Debug switches and tools", OrionShapes.SoftBurst),
)

private val CAT_TIPS = mapOf(
    "queue" to "Downloaded installers and portable executables show up here for one-click launching.",
    "visuals" to "Every color swatch updates all surfaces using Material 3 2025 tonal color tokens.",
    "network" to "Orion fetches from GitHub Raw first, then falls back cleanly to jsDelivr and GitHack mirrors.",
    "storage" to "Installer files downloaded to temp storage can be swept in one click anytime.",
    "installer" to "Desktop Runner verifies extensions and launches .msi and .exe packages natively on Windows.",
    "identity" to "Everything stays locally inside ~/.orion on your PC — zero accounts or telemetry.",
    "interface" to "Pick Modern for Orion's editorial storefront or Classic for a compact catalog list.",
    "developer" to "All live catalog JSON files live inside your local .orion directory.",
    "about" to "An unofficial Compose Multiplatform port. All credit for Orion Store, the catalog and the mirror infrastructure goes to RookieEnough."
)

private data class QuickDef(
    val id: String,
    val icon: ImageVector,
    val label: String,
    val get: (DesktopPrefs) -> Boolean,
    val flip: (DesktopPrefs) -> DesktopPrefs
)

private val QUICK_DEFS = listOf(
    QuickDef("autoCleanup", Icons.Default.CleaningServices, "Auto-cleanup", { it.autoCleanup }) { it.copy(autoCleanup = !it.autoCleanup) },
    QuickDef("compact", Icons.Default.Compress, "Compact", { it.compactMode }) { it.copy(compactMode = !it.compactMode) },
    QuickDef("stableOnly", Icons.Default.Verified, "Stable only", { it.stableOnly }) { it.copy(stableOnly = !it.stableOnly) },
    QuickDef("oled", Icons.Default.Contrast, "OLED black", { it.themeMode == "oled" }) { it.copy(themeMode = if (it.themeMode == "oled") "dark" else "oled") },
    QuickDef("glass", Icons.Default.BlurOn, "Glass dock", { it.glassDock }) { it.copy(glassDock = !it.glassDock) },
    QuickDef("smooth", Icons.Default.Speed, "Smooth motion", { it.smoothMotion }) { it.copy(smoothMotion = !it.smoothMotion) },
)

@Composable
fun OrionSettingsScreen(
    prefs: DesktopPrefs,
    palette: Palette,
    allApps: List<AppItem>,
    downloads: List<DownloadState>,
    announcement: String?,
    onUpdatePrefs: (DesktopPrefs) -> Unit,
    onReloadCatalog: () -> Unit,
    onSelectApp: (AppItem) -> Unit,
    onLaunchFile: (File) -> Unit,
    onBack: (() -> Unit)? = null
) {
    var currentPage by remember { mutableStateOf("none") }
    var editingQuick by remember { mutableStateOf(false) }

    AnimatedContent(
        targetState = currentPage,
        transitionSpec = { fadeIn() togetherWith fadeOut() },
        label = "settings-nav"
    ) { pageId ->
        val cat = SETTINGS_CATS.firstOrNull { it.id == pageId }
        if (cat == null) {
            SettingsHubView(
                prefs = prefs,
                palette = palette,
                allApps = allApps,
                downloads = downloads,
                announcement = announcement,
                onUpdatePrefs = onUpdatePrefs,
                onOpenCategory = { currentPage = it },
                onEditQuickTiles = { editingQuick = true },
                onBack = onBack
            )
        } else {
            SettingsCategoryPage(
                cat = cat,
                prefs = prefs,
                palette = palette,
                allApps = allApps,
                downloads = downloads,
                onUpdatePrefs = onUpdatePrefs,
                onReloadCatalog = onReloadCatalog,
                onSelectApp = onSelectApp,
                onLaunchFile = onLaunchFile,
                onBack = { currentPage = "none" }
            )
        }
    }

    if (editingQuick) {
        QuickTilesEditorDialog(
            prefs = prefs,
            onUpdatePrefs = onUpdatePrefs,
            onDismiss = { editingQuick = false }
        )
    }
}

@Composable
private fun SettingsHubView(
    prefs: DesktopPrefs,
    palette: Palette,
    allApps: List<AppItem>,
    downloads: List<DownloadState>,
    announcement: String?,
    onUpdatePrefs: (DesktopPrefs) -> Unit,
    onOpenCategory: (String) -> Unit,
    onEditQuickTiles: () -> Unit,
    onBack: (() -> Unit)? = null
) {
    val cs = MaterialTheme.colorScheme
    var showAnnouncement by remember { mutableStateOf(false) }
    val pendingFiles = remember(downloads) { DesktopCatalogRepository.pendingInstallerFiles() }
    val activeCount = downloads.count { !it.isComplete && it.error == null }
    val readyCount = downloads.count { it.isComplete }

    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
        Column(
            modifier = Modifier
                .fillMaxHeight()
                .widthIn(max = 920.dp)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(bottom = 120.dp)
        ) {
            // Header ("Settings" + subtitle + back navigation)
            Row(
                modifier = Modifier.fillMaxWidth().padding(start = if (onBack != null) 12.dp else 24.dp, end = 16.dp, top = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                if (onBack != null) {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back to store", tint = cs.onSurface)
                    }
                }
                Column(Modifier.weight(1f)) {
                    Text("Settings", style = MaterialTheme.typography.displaySmall, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(2.dp))
                    Text(
                        "Orion Store v2.0.0 · ${allApps.size} apps in catalog",
                        style = MaterialTheme.typography.bodyMedium,
                        color = cs.onSurfaceVariant
                    )
                }
                if (!announcement.isNullOrBlank()) {
                    IconButton(onClick = { showAnnouncement = true }) {
                        Icon(Icons.Default.Campaign, "Announcement", tint = cs.onSurfaceVariant)
                    }
                }
            }

            // Quick Toggles Header + 3-column bento row
            HubSectionHeader("Quick toggles") {
                IconButton(onClick = onEditQuickTiles) {
                    Icon(Icons.Default.Edit, "Edit quick toggles", tint = cs.primary)
                }
            }

            val pickedQuick = prefs.quickTiles.mapNotNull { id -> QUICK_DEFS.firstOrNull { it.id == id } }
            if (pickedQuick.isEmpty()) {
                Text(
                    "No quick toggles pinned. Tap the pencil to add some.",
                    Modifier.padding(horizontal = 24.dp),
                    style = MaterialTheme.typography.bodyMedium,
                    color = cs.onSurfaceVariant
                )
            } else {
                pickedQuick.chunked(3).forEach { row ->
                    Row(
                        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        row.forEach { q ->
                            QuickTileButton(
                                icon = q.icon,
                                label = q.label,
                                on = q.get(prefs),
                                modifier = Modifier.weight(1f),
                                onClick = { onUpdatePrefs(q.flip(prefs)) }
                            )
                        }
                        repeat(3 - row.size) { Spacer(Modifier.weight(1f)) }
                    }
                }
            }

            // "Everything else": Authentic Android Bento Grid layout matching Screenshot 2
            HubSectionHeader("Everything else", hint = "Long-press a card to move it")

            val catMap = remember(SETTINGS_CATS) { SETTINGS_CATS.associateBy { it.id } }
            fun getCat(id: String) = catMap[id] ?: SETTINGS_CATS.first()

            Column(
                modifier = Modifier.padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // 1. Slot 0: Downloads (Deep Blue - Full Width)
                val dlCat = getCat("queue")
                BentoCategoryCard(
                    cat = dlCat,
                    animate = prefs.smoothMotion,
                    compact = false,
                    badge = (activeCount + readyCount).takeIf { it > 0 }?.toString(),
                    modifier = Modifier.fillMaxWidth().height(156.dp),
                    onClick = { onOpenCategory(dlCat.id) }
                ) {
                    Spacer(Modifier.height(4.dp))
                    Text(
                        if (activeCount + readyCount == 0) "All caught up · 0 active · 0 ready"
                        else "$activeCount active · $readyCount ready · 0 updates",
                        style = MaterialTheme.typography.labelMedium,
                        color = LocalContentColor.current.copy(alpha = 0.8f)
                    )
                }

                // 2. Middle Row: Left = Look & Feel (Purple - Tall), Right = Network (Cyan) + Storage (Brown)
                Row(
                    modifier = Modifier.fillMaxWidth().height(268.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    // Left Column: Look & feel (Tall)
                    val visualCat = getCat("visuals")
                    BentoCategoryCard(
                        cat = visualCat,
                        animate = prefs.smoothMotion,
                        compact = false,
                        modifier = Modifier.weight(1f).fillMaxHeight(),
                        onClick = { onOpenCategory(visualCat.id) }
                    ) {
                        Spacer(Modifier.height(10.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy((-8).dp)) {
                            listOf(Color.White, Color(0xFFC084FC), Color(0xFF6B21A8)).forEach {
                                Box(Modifier.size(26.dp).clip(CircleShape).background(Color.White.copy(alpha = 0.15f)).padding(2.dp).clip(CircleShape).background(it))
                            }
                        }
                        Spacer(Modifier.height(6.dp))
                        Text(
                            "Google Sans · ${prefs.themeMode.replaceFirstChar { it.uppercase() }}",
                            style = MaterialTheme.typography.labelMedium,
                            color = LocalContentColor.current.copy(alpha = 0.85f)
                        )
                    }

                    // Right Column: Network on top, Storage on bottom
                    Column(
                        modifier = Modifier.weight(1f).fillMaxHeight(),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        val netCat = getCat("network")
                        BentoCategoryCard(
                            cat = netCat,
                            animate = prefs.smoothMotion,
                            compact = true,
                            modifier = Modifier.fillMaxWidth().weight(1f),
                            onClick = { onOpenCategory(netCat.id) }
                        )

                        val storageCat = getCat("storage")
                        BentoCategoryCard(
                            cat = storageCat,
                            animate = prefs.smoothMotion,
                            compact = true,
                            badge = pendingFiles.size.takeIf { it > 0 }?.toString(),
                            modifier = Modifier.fillMaxWidth().weight(1f),
                            onClick = { onOpenCategory(storageCat.id) }
                        )
                    }
                }

                // 3. Slot 4: Orion Xtra (Emerald Green - Full Width)
                val xtraCat = getCat("installer")
                BentoCategoryCard(
                    cat = xtraCat,
                    animate = prefs.smoothMotion,
                    compact = false,
                    modifier = Modifier.fillMaxWidth().height(136.dp),
                    onClick = { onOpenCategory(xtraCat.id) }
                )

                // 4. Bottom Split Row: Backup (Berry) + Interface (Teal)
                Row(
                    modifier = Modifier.fillMaxWidth().height(132.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    val backupCat = getCat("identity")
                    BentoCategoryCard(
                        cat = backupCat,
                        animate = prefs.smoothMotion,
                        compact = true,
                        modifier = Modifier.weight(1f).fillMaxHeight(),
                        onClick = { onOpenCategory(backupCat.id) }
                    )

                    val ifaceCat = getCat("interface")
                    BentoCategoryCard(
                        cat = ifaceCat,
                        animate = prefs.smoothMotion,
                        compact = true,
                        modifier = Modifier.weight(1f).fillMaxHeight(),
                        onClick = { onOpenCategory(ifaceCat.id) }
                    )
                }

                // 5. Developer Strip (Yellow - Full Width)
                val devCat = getCat("developer")
                BentoCategoryCard(
                    cat = devCat,
                    animate = prefs.smoothMotion,
                    compact = true,
                    modifier = Modifier.fillMaxWidth().height(96.dp),
                    onClick = { onOpenCategory(devCat.id) }
                )
            }
        }
    }

    if (showAnnouncement && !announcement.isNullOrBlank()) {
        AlertDialog(
            onDismissRequest = { showAnnouncement = false },
            icon = { Icon(Icons.Default.Campaign, null) },
            title = { Text("From the Orion team") },
            text = { Text(announcement) },
            confirmButton = { TextButton({ showAnnouncement = false }) { Text("Got it") } }
        )
    }
}

@Composable
private fun HubSectionHeader(title: String, hint: String? = null, action: @Composable () -> Unit = {}) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 24.dp, end = 12.dp, top = 16.dp, bottom = 4.dp)
            .heightIn(min = 44.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
            hint?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
        }
        action()
    }
}

@Composable
private fun QuickTileButton(
    icon: ImageVector,
    label: String,
    on: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    val cs = MaterialTheme.colorScheme
    val r by animateDpAsState(if (on) 36.dp else 20.dp, spring(stiffness = Spring.StiffnessMediumLow))
    val bg by animateColorAsState(if (on) cs.primary else cs.surfaceContainerHigh)
    val fg by animateColorAsState(if (on) cs.onPrimary else cs.onSurface)
    val bounce = remember { Animatable(1f) }
    LaunchedEffect(on) {
        bounce.snapTo(0.7f)
        bounce.animateTo(1f, spring(dampingRatio = 0.55f, stiffness = Spring.StiffnessMedium))
    }

    Row(
        modifier = modifier
            .height(76.dp)
            .expressiveSpringClick(RoundedCornerShape(r), onClick = onClick)
            .background(bg)
            .padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            icon,
            null,
            Modifier.size(24.dp).graphicsLayer { scaleX = bounce.value; scaleY = bounce.value },
            tint = fg
        )
        Spacer(Modifier.width(12.dp))
        Column {
            Text(label, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold, color = fg, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(if (on) "On" else "Off", style = MaterialTheme.typography.labelMedium, color = fg.copy(alpha = 0.75f), maxLines = 1)
        }
    }
}

@Composable
private fun BentoCategoryCard(
    cat: SettingsCat,
    animate: Boolean,
    modifier: Modifier = Modifier,
    compact: Boolean = false,
    badge: String? = null,
    onClick: () -> Unit,
    extra: @Composable ColumnScope.() -> Unit = {}
) {
    val (bg, fg) = tonalPair(cat.color)
    val cardShape = RoundedCornerShape(28.dp)
    Box(
        modifier = modifier
            .expressiveSpringClick(cardShape, onClick = onClick)
            .background(bg)
            .border(1.dp, Color.White.copy(alpha = 0.08f), cardShape)
            .clipToBounds()
    ) {
        SpinnerShape(
            shape = cat.shape,
            color = fg.copy(alpha = 0.11f),
            size = if (compact || cat.id == "developer") 120.dp else 168.dp,
            modifier = Modifier.align(Alignment.BottomEnd).offset(36.dp, 36.dp),
            animate = animate
        )
        CompositionLocalProvider(LocalContentColor provides fg) {
            Column(Modifier.fillMaxSize().padding(18.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(44.dp).background(fg.copy(alpha = 0.14f), cat.shape), contentAlignment = Alignment.Center) {
                        Icon(cat.icon, null, Modifier.size(24.dp))
                    }
                    Spacer(Modifier.weight(1f))
                    badge?.let {
                        Badge(containerColor = fg, contentColor = bg) {
                            Text(it, Modifier.padding(horizontal = 4.dp))
                        }
                    }
                }
                Spacer(Modifier.weight(1f))
                Text(cat.title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, maxLines = 1)
                if (!compact) {
                    Text(cat.desc, style = MaterialTheme.typography.bodySmall, color = fg.copy(alpha = 0.8f), maxLines = 2)
                }
                extra()
            }
        }
    }
}

// -------------------------------------------------------------
// CATEGORY DETAIL PAGE (Gradient Top Hero + Segmented Sections)
// -------------------------------------------------------------
@Composable
private fun SettingsCategoryPage(
    cat: SettingsCat,
    prefs: DesktopPrefs,
    palette: Palette,
    allApps: List<AppItem>,
    downloads: List<DownloadState>,
    onUpdatePrefs: (DesktopPrefs) -> Unit,
    onReloadCatalog: () -> Unit,
    onSelectApp: (AppItem) -> Unit,
    onLaunchFile: (File) -> Unit,
    onBack: () -> Unit
) {
    val cs = MaterialTheme.colorScheme
    val (bg, fg) = tonalPair(cat.color)
    var pendingFiles by remember { mutableStateOf(DesktopCatalogRepository.pendingInstallerFiles()) }
    var backupMsg by remember { mutableStateOf<String?>(null) }

    CompositionLocalProvider(LocalSettingsAccent provides cat.color) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(cs.surface)
                .verticalScroll(rememberScrollState())
                .padding(bottom = 120.dp)
        ) {
            // Gradient Top Hero (exact Settings.kt Page line 909)
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clipToBounds()
                    .background(Brush.verticalGradient(listOf(bg, cs.surface)))
            ) {
                SpinnerShape(
                    shape = cat.shape,
                    color = fg.copy(alpha = 0.08f),
                    size = 260.dp,
                    modifier = Modifier.align(Alignment.TopEnd).offset(70.dp, (-30).dp),
                    periodMs = 40_000,
                    animate = prefs.smoothMotion
                )

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .wrapContentWidth()
                        .widthIn(max = 880.dp)
                        .fillMaxWidth()
                        .padding(bottom = 16.dp)
                ) {
                    Row(Modifier.padding(start = 16.dp, top = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                        FilledTonalIconButton(onClick = onBack, shape = CircleShape) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back to Settings")
                        }
                    }
                    Box(Modifier.padding(start = 24.dp, top = 14.dp).size(72.dp), contentAlignment = Alignment.Center) {
                        SpinnerShape(cat.shape, fg.copy(alpha = 0.16f), 72.dp, periodMs = 18_000, animate = prefs.smoothMotion)
                        Icon(cat.icon, null, Modifier.size(34.dp), tint = fg)
                    }
                    Text(cat.title, Modifier.padding(start = 24.dp, top = 16.dp), style = MaterialTheme.typography.displaySmall, fontWeight = FontWeight.Bold)
                    Text(cat.desc, Modifier.padding(start = 24.dp, end = 24.dp, top = 4.dp), style = MaterialTheme.typography.bodyLarge, color = cs.onSurfaceVariant)
                }
            }

            // Category Content Column
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .wrapContentWidth()
                    .widthIn(max = 880.dp)
                    .fillMaxWidth()
            ) {
                when (cat.id) {
                    "visuals" -> {
                        ThemePreviewPicker(prefs, palette, onUpdatePrefs)
                        PaletteSplitSwatchPicker(prefs, palette, onUpdatePrefs)
                        SettingsSection("Color & motion") {
                            SettingsToggleRow(
                                icon = Icons.Default.BlurOn,
                                title = "Translucent glass dock",
                                desc = "Floating pill uses soft translucent surface layering",
                                checked = prefs.glassDock,
                                onToggle = { onUpdatePrefs(prefs.copy(glassDock = !prefs.glassDock)) }
                            )
                            SettingsToggleRow(
                                icon = Icons.Default.Speed,
                                title = "Smooth motion",
                                desc = "Slow spinning expressive shapes and spring interactions",
                                checked = prefs.smoothMotion,
                                onToggle = { onUpdatePrefs(prefs.copy(smoothMotion = !prefs.smoothMotion)) }
                            )
                            SettingsToggleRow(
                                icon = Icons.Default.Compress,
                                title = "Compact density",
                                desc = "Tighter swimlane icons and single-line titles",
                                checked = prefs.compactMode,
                                onToggle = { onUpdatePrefs(prefs.copy(compactMode = !prefs.compactMode)) }
                            )
                        }
                    }
                    "interface" -> {
                        LayoutPicturePicker(prefs, onUpdatePrefs)
                        DockStyleSection(prefs, onUpdatePrefs)
                        WideNavSection(prefs, onUpdatePrefs)
                        DockScalePreviewSection(prefs, onUpdatePrefs)
                    }
                    "network" -> {
                        SettingsSection("Release channels & mirrors") {
                            SettingsToggleRow(
                                icon = Icons.Default.Verified,
                                title = "Stable releases only",
                                desc = "Skip Beta, Alpha and Nightly builds when a Stable release exists",
                                checked = prefs.stableOnly,
                                onToggle = { onUpdatePrefs(prefs.copy(stableOnly = !prefs.stableOnly)) }
                            )
                            SettingsActionRow(
                                icon = Icons.Default.Sync,
                                title = "Refresh upstream catalog now",
                                desc = "Re-fetch config.json, apps.json, mirror.json and stars.json",
                                onClick = onReloadCatalog
                            ) {
                                SettingsChevron("Sync")
                            }
                            SettingsInfoCard(
                                title = "Multi-CDN Mirror Resilience",
                                text = "Orion tries GitHub Raw first, then automatically falls back to jsDelivr CDN and raw.githack if your ISP throttles or blocks raw.githubusercontent.com."
                            )
                        }
                    }
                    "storage" -> {
                        val totalBytes = pendingFiles.sumOf { it.length() }
                        val hasActiveDownload = downloads.any { !it.isComplete && it.error == null }
                        SettingsStatHero(
                            value = "${pendingFiles.size}",
                            caption = if (pendingFiles.size == 1) "Installer cached (${formatBytes(totalBytes)})" else "Installers cached (${formatBytes(totalBytes)})"
                        ) {
                            if (pendingFiles.isNotEmpty()) {
                                Button(
                                    onClick = {
                                        DesktopCatalogRepository.clearPendingInstallers()
                                        pendingFiles = DesktopCatalogRepository.pendingInstallerFiles()
                                    },
                                    enabled = !hasActiveDownload,
                                    shape = PillShape
                                ) {
                                    Icon(Icons.Default.CleaningServices, null, Modifier.size(18.dp))
                                    Spacer(Modifier.width(8.dp))
                                    Text(if (hasActiveDownload) "Download in progress" else "Clean now")
                                }
                            }
                        }
                        SettingsSection("Janitor") {
                            SettingsToggleRow(
                                icon = Icons.Default.CleaningServices,
                                title = "Auto-cleanup installers",
                                desc = "Purge temp installer binaries on exit",
                                checked = prefs.autoCleanup,
                                onToggle = { onUpdatePrefs(prefs.copy(autoCleanup = !prefs.autoCleanup)) }
                            )
                            SettingsActionRow(
                                icon = Icons.Default.FolderOpen,
                                title = "Open downloads folder",
                                desc = DesktopCatalogRepository.downloadsDir.absolutePath,
                                onClick = { DesktopInstaller.openBrowser(DesktopCatalogRepository.downloadsDir.toURI().toString()) }
                            ) { SettingsChevron() }
                        }
                    }
                    "installer" -> {
                        Row(Modifier.padding(horizontal = 16.dp, vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            SettingsFeatureTile(
                                icon = Icons.Default.RocketLaunch,
                                title = "Release Resolver",
                                desc = "Live .exe / .msi / .zip resolution via GitHub, GitLab & Codeberg",
                                color = GreenC,
                                shape = OrionShapes.Cookie9Sided,
                                animate = prefs.smoothMotion,
                                modifier = Modifier.weight(1f),
                                onClick = onReloadCatalog
                            )
                            SettingsFeatureTile(
                                icon = Icons.Default.Security,
                                title = "VirusTotal Audit",
                                desc = "Verify release URLs and hashes before launching",
                                color = BlueC,
                                shape = OrionShapes.Sunny,
                                animate = prefs.smoothMotion,
                                modifier = Modifier.weight(1f),
                                onClick = { DesktopInstaller.openBrowser("https://www.virustotal.com/gui/home/url") }
                            )
                        }
                        SettingsSection("Windows installer") {
                            SettingsToggleRow(
                                icon = Icons.Default.Bolt,
                                title = "Silent installs",
                                desc = "Use /S, /VERYSILENT or /quiet when the packaging is recognised",
                                checked = prefs.silentInstall,
                                onToggle = { onUpdatePrefs(prefs.copy(silentInstall = !prefs.silentInstall)) }
                            )
                            SettingsActionRow(
                                icon = Icons.Default.FolderOpen,
                                title = "Staging directory",
                                desc = DesktopCatalogRepository.downloadsDir.absolutePath,
                                onClick = { DesktopInstaller.openBrowser(DesktopCatalogRepository.downloadsDir.toURI().toString()) }
                            ) { SettingsChevron() }
                            SettingsActionRow(
                                icon = Icons.Default.Inventory2,
                                title = "Portable apps",
                                desc = File(DesktopCatalogRepository.orionDir, "tools").absolutePath,
                                onClick = { DesktopInstaller.openBrowser(File(DesktopCatalogRepository.orionDir, "tools").toURI().toString()) }
                            ) { SettingsChevron() }
                            SettingsInfoCard("Windows executables (.exe) run natively, MSI packages go through msiexec, and .zip/.7z builds are extracted to ~/.orion/tools with a desktop shortcut.")
                        }
                    }
                    "identity" -> {
                        val backupFile = File(DesktopCatalogRepository.orionDir, "orion_desktop_backup.json")
                        SettingsSection("Your data stays here") {
                            SettingsInfoCard(
                                title = "Orion is serverless",
                                text = "Save your theme, layout, quick tiles, and favorites locally as JSON. Nothing is ever sent to any Orion server.",
                                icon = Icons.Default.CloudOff
                            )
                        }
                        Row(Modifier.padding(horizontal = 16.dp, vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            Button(
                                onClick = {
                                    backupFile.writeText(json.encodeToString(DesktopPrefs.serializer(), prefs))
                                    backupMsg = "Saved backup to ${backupFile.absolutePath}"
                                },
                                shape = PillShape,
                                modifier = Modifier.weight(1f).height(56.dp)
                            ) {
                                Icon(Icons.Default.FileUpload, null)
                                Spacer(Modifier.width(8.dp))
                                Text("Back up", fontWeight = FontWeight.Bold)
                            }
                            FilledTonalButton(
                                onClick = {
                                    if (backupFile.exists()) {
                                        runCatching { json.decodeFromString(DesktopPrefs.serializer(), backupFile.readText()) }
                                            .onSuccess {
                                                onUpdatePrefs(it)
                                                backupMsg = "Restored preferences & ${it.favorites.size} favorites!"
                                            }
                                            .onFailure { backupMsg = "Failed to parse backup file." }
                                    } else {
                                        backupMsg = "No backup found at ${backupFile.name}"
                                    }
                                },
                                shape = PillShape,
                                modifier = Modifier.weight(1f).height(56.dp)
                            ) {
                                Icon(Icons.Default.FileDownload, null)
                                Spacer(Modifier.width(8.dp))
                                Text("Restore", fontWeight = FontWeight.Bold)
                            }
                        }
                        backupMsg?.let { msg ->
                            Text(msg, Modifier.padding(horizontal = 24.dp, vertical = 6.dp), style = MaterialTheme.typography.bodySmall, color = cs.primary)
                        }
                    }
                    "developer" -> {
                        val pcCount = allApps.count { it.platform == Platform.PC }
                        val androidCount = allApps.count { it.platform == Platform.ANDROID }
                        val starredCount = allApps.count { (it.stars ?: 0) > 0 }
                        SettingsSection("Catalog diagnostics") {
                            SettingsActionRow(
                                icon = Icons.Default.Dataset,
                                title = "Loaded catalog summary",
                                desc = "$pcCount PC tools · $androidCount Android apps · $starredCount starred repos"
                            )
                            SettingsActionRow(
                                icon = Icons.Default.FolderOpen,
                                title = "Open .orion data directory",
                                desc = DesktopCatalogRepository.orionDir.absolutePath,
                                onClick = { DesktopInstaller.openBrowser(DesktopCatalogRepository.orionDir.toURI().toString()) }
                            ) { SettingsChevron() }
                            SettingsActionRow(
                                icon = Icons.Default.DeleteSweep,
                                title = "Clear disk cache & re-sync",
                                desc = "Delete cached apps.json, mirror.json, config.json and reload live",
                                onClick = {
                                    DesktopCatalogRepository.clearCatalogCache()
                                    onReloadCatalog()
                                }
                            ) { SettingsChevron("Reset") }
                        }
                    }
                    "about" -> {
                        val completedNow = downloads.count { it.isComplete }
                        val stats = remember(completedNow) { DesktopCatalogRepository.installStats() }
                        val favorites = allApps.count { it.id in prefs.favorites }
                        val pcApps = allApps.count { it.platform == Platform.PC }

                        PilotProfileCard(
                            installs = stats.installs,
                            favorites = favorites,
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                        )

                        Row(
                            modifier = Modifier
                                .padding(horizontal = 16.dp, vertical = 8.dp)
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(28.dp))
                                .background(cs.surfaceContainer)
                                .padding(20.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(18.dp)
                        ) {
                            OrionLogo(76.dp)
                            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                                Text("Orion Store Desktop", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                                Text(
                                    "v2.0.0 · Compose Multiplatform · Windows",
                                    style = MaterialTheme.typography.labelLarge,
                                    color = cs.primary
                                )
                                Text(
                                    "A native desktop port of RookieEnough's Orion Store. Real download progress, native installers and portable archive extraction.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = cs.onSurfaceVariant
                                )
                            }
                        }

                        SettingsSection("At a glance") {
                            Row(
                                modifier = Modifier.fillMaxWidth().background(cs.surfaceContainer).padding(horizontal = 12.dp, vertical = 18.dp),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                AboutStat("${allApps.size}", "apps", Modifier.weight(1f))
                                AboutStat("$pcApps", "PC tools", Modifier.weight(1f))
                                AboutStat("$favorites", "favorites", Modifier.weight(1f))
                                AboutStat("${stats.installs}", "installs", Modifier.weight(1f))
                            }
                        }

                        SettingsSection("Local milestones") {
                            MilestoneRow("First install", stats.installs, 1)
                            MilestoneRow("Portable pro", stats.portable, 3)
                            MilestoneRow("App collector", favorites, 10)
                            MilestoneRow("Serial installer", stats.installs, 25)
                        }

                        SettingsSection("Credits & links") {
                            SettingsActionRow(
                                icon = Icons.Default.Code,
                                title = "Orion Store by RookieEnough",
                                desc = "Upstream Android app, catalog and mirror infrastructure",
                                onClick = { DesktopInstaller.openBrowser("https://github.com/RookieEnough/Orion-Store") }
                            ) { SettingsChevron("GitHub") }
                            SettingsActionRow(
                                icon = Icons.Default.Language,
                                title = "orionstore.pages.dev",
                                desc = "Official website and live catalog",
                                onClick = { DesktopInstaller.openBrowser("https://orionstore.pages.dev") }
                            ) { SettingsChevron("Open") }
                            SettingsActionRow(
                                icon = Icons.Default.Person,
                                title = "RookieZ · lead developer",
                                desc = "Catalog, mirror bots and relay, built and run solo",
                                onClick = { DesktopInstaller.openBrowser("https://github.com/RookieEnough") }
                            ) { SettingsChevron("Profile") }
                            SettingsActionRow(
                                icon = Icons.Default.FolderOpen,
                                title = "Local data",
                                desc = DesktopCatalogRepository.orionDir.absolutePath,
                                onClick = { DesktopInstaller.openBrowser(DesktopCatalogRepository.orionDir.toURI().toString()) }
                            ) { SettingsChevron() }
                        }

                        SettingsSection("Port notes") {
                            SettingsInfoCard(
                                "Downloads stream with byte-level progress. .zip and .7z builds extract to ~/.orion/tools with a desktop shortcut; .exe, .msi and .msix go through the native Windows installers. Silent flags are opt-in and only applied when the packaging is recognised.",
                                title = "What this port adds"
                            )
                            SettingsInfoCard(
                                "The Android XP economy, ad boosts and leaderboard relay are not faked here. Desktop milestones use your real local installs and favorites.",
                                title = "What it does not do",
                                icon = Icons.Default.Info
                            )
                        }
                    }
                    "queue" -> {
                        if (downloads.isEmpty()) {
                            Column(
                                modifier = Modifier.fillMaxWidth().padding(vertical = 40.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                Box(Modifier.size(88.dp).background(cs.surfaceContainerHigh, OrionShapes.Cookie9Sided), contentAlignment = Alignment.Center) {
                                    Icon(Icons.Default.DownloadDone, null, Modifier.size(38.dp), tint = cs.onSurfaceVariant)
                                }
                                Text("Queue is empty", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                                Text("Downloads and launched installers show up here.", style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant)
                            }
                        } else {
                            SettingsSection("Download tasks") {
                                downloads.forEach { task ->
                                    ListItem(
                                        headlineContent = { Text(task.app.name, fontWeight = FontWeight.SemiBold) },
                                        supportingContent = {
                                            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                                Text(
                                                    task.status,
                                                    color = if (task.error != null) cs.error else cs.primary,
                                                    maxLines = 2,
                                                    overflow = TextOverflow.Ellipsis
                                                )
                                                if (!task.isComplete && task.error == null) {
                                                    val fraction = task.progress.takeIf { it in 0f..1f }
                                                    if (fraction != null) {
                                                        LinearProgressIndicator(
                                                            progress = { fraction },
                                                            modifier = Modifier.fillMaxWidth()
                                                        )
                                                    } else {
                                                        LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                                                    }
                                                }
                                            }
                                        },
                                        leadingContent = { DesktopAppIcon(task.app.icon, task.app.name, 44.dp) },
                                        trailingContent = {
                                            if (task.isComplete) {
                                                val portable = task.installDir != null && task.localFile?.extension.equals("exe", true)
                                                FilledTonalButton(
                                                    onClick = {
                                                        if (portable) task.localFile?.let(onLaunchFile)
                                                        else DesktopInstaller.revealInExplorer(
                                                            task.installDir ?: task.localFile ?: DesktopCatalogRepository.downloadsDir
                                                        )
                                                    },
                                                    shape = PillShape
                                                ) { Text(if (portable) "Run" else "Folder") }
                                            }
                                        },
                                        modifier = Modifier.clip(RoundedCornerShape(4.dp)).clickable { onSelectApp(task.app) },
                                        colors = ListItemDefaults.colors(containerColor = cs.surfaceContainer)
                                    )
                                }
                            }
                        }
                    }
                }

                // Category Footer (exact Settings.kt PageFooter)
                val (_, tipFg) = tonalPair(cat.color)
                Column(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 40.dp, vertical = 32.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Box(Modifier.size(112.dp), contentAlignment = Alignment.Center) {
                        SpinnerShape(cat.shape, tipFg.copy(alpha = 0.12f), 112.dp, periodMs = 30_000, animate = prefs.smoothMotion)
                        Icon(cat.icon, null, Modifier.size(40.dp), tint = tipFg.copy(alpha = 0.7f))
                    }
                    Text(
                        CAT_TIPS[cat.id] ?: cat.desc,
                        modifier = Modifier.padding(top = 16.dp),
                        style = MaterialTheme.typography.bodyMedium,
                        color = cs.onSurfaceVariant,
                        textAlign = TextAlign.Center
                    )
                }
            }
        }
    }
}

// -------------------------------------------------------------
// SETTINGS VISUAL PICKERS (1:1 Settings.kt Ports)
// -------------------------------------------------------------
@Composable
private fun ThemePreviewPicker(prefs: DesktopPrefs, palette: Palette, onUpdatePrefs: (DesktopPrefs) -> Unit) {
    SettingsSection("Theme") {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(4.dp))
                .background(MaterialTheme.colorScheme.surfaceContainer)
                .padding(14.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            listOf("light" to "Light", "dusk" to "Dusk", "dark" to "Dark", "oled" to "OLED").forEach { (mode, label) ->
                val scheme = palette.scheme(mode)
                val on = prefs.themeMode == mode
                val r by animateDpAsState(if (on) 24.dp else 14.dp)
                val ring by animateDpAsState(if (on) 3.dp else 0.dp)

                Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(144.dp)
                            .expressiveSpringClick(RoundedCornerShape(r)) {
                                onUpdatePrefs(prefs.copy(themeMode = mode))
                            }
                            .border(ring, MaterialTheme.colorScheme.primary, RoundedCornerShape(r))
                            .background(scheme.surface)
                            .padding(10.dp)
                    ) {
                        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Box(Modifier.fillMaxWidth().height(12.dp).clip(PillShape).background(scheme.surfaceContainerHigh))
                            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                Box(Modifier.weight(1f).height(40.dp).clip(RoundedCornerShape(8.dp)).background(scheme.primaryContainer))
                                Box(Modifier.weight(1f).height(40.dp).clip(RoundedCornerShape(8.dp)).background(scheme.tertiaryContainer))
                            }
                            Box(Modifier.fillMaxWidth(0.7f).height(7.dp).clip(PillShape).background(scheme.onSurface.copy(alpha = 0.5f)))
                            Box(Modifier.fillMaxWidth(0.45f).height(7.dp).clip(PillShape).background(scheme.onSurface.copy(alpha = 0.3f)))
                        }
                        Box(Modifier.align(Alignment.BottomCenter).fillMaxWidth().height(16.dp).clip(PillShape).background(scheme.primary))
                        if (on) {
                            Box(
                                Modifier.align(Alignment.TopEnd).size(20.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primary),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(Icons.Default.Check, null, Modifier.size(13.dp), tint = MaterialTheme.colorScheme.onPrimary)
                            }
                        }
                    }
                    Text(
                        label,
                        Modifier.padding(top = 8.dp),
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = if (on) FontWeight.Bold else FontWeight.Medium,
                        color = if (on) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

@Composable
private fun PaletteSplitSwatchPicker(prefs: DesktopPrefs, currentPalette: Palette, onUpdatePrefs: (DesktopPrefs) -> Unit) {
    val cs = MaterialTheme.colorScheme
    SettingsSection("Palette") {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(4.dp))
                .background(cs.surfaceContainer)
                .horizontalScroll(rememberScrollState())
                .padding(14.dp),
            horizontalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            PALETTES.forEach { p ->
                val sc = p.scheme(prefs.themeMode)
                val on = currentPalette.id == p.id
                Column(Modifier.width(66.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    val swatchShape = if (on) OrionShapes.Cookie9Sided else CircleShape
                    val ring by animateDpAsState(if (on) 2.dp else 0.dp)
                    Box(
                        modifier = Modifier
                            .size(60.dp)
                            .hoverScale(hoverTarget = 1.05f)
                            .clip(swatchShape)
                            .clickable { onUpdatePrefs(prefs.copy(paletteId = p.id)) }
                            .border(ring, cs.primary, swatchShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(Modifier.fillMaxSize()) {
                            Box(Modifier.fillMaxWidth().weight(1f).background(sc.primary))
                            Row(Modifier.fillMaxWidth().weight(1f)) {
                                Box(Modifier.weight(1f).fillMaxHeight().background(sc.secondaryContainer))
                                Box(Modifier.weight(1f).fillMaxHeight().background(sc.tertiary))
                            }
                        }
                        if (on) {
                            Box(
                                Modifier.size(24.dp).clip(CircleShape).background(sc.surface),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(Icons.Default.Check, null, Modifier.size(16.dp), tint = sc.primary)
                            }
                        }
                    }
                    Text(
                        p.label,
                        Modifier.padding(top = 8.dp),
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = if (on) FontWeight.Bold else FontWeight.Medium,
                        color = if (on) cs.primary else cs.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
}

@Composable
private fun LayoutPicturePicker(prefs: DesktopPrefs, onUpdatePrefs: (DesktopPrefs) -> Unit) {
    val cs = MaterialTheme.colorScheme
    SettingsSection("Store layout") {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(4.dp))
                .background(cs.surfaceContainer)
                .padding(14.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            listOf("modern" to "Modern", "classic" to "Classic").forEach { (id, label) ->
                val on = prefs.storeLayout == id
                val r by animateDpAsState(if (on) 28.dp else 16.dp)
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .expressiveSpringClick(RoundedCornerShape(r)) {
                            onUpdatePrefs(prefs.copy(storeLayout = id))
                        }
                        .background(if (on) cs.secondaryContainer else cs.surfaceContainerHigh)
                        .padding(14.dp)
                ) {
                    Column(Modifier.fillMaxWidth().height(116.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        if (id == "modern") {
                            Box(Modifier.fillMaxWidth().height(40.dp).clip(RoundedCornerShape(10.dp)).background(cs.primaryContainer))
                            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                repeat(4) { Box(Modifier.weight(1f).height(30.dp).clip(RoundedCornerShape(6.dp)).background(cs.tertiaryContainer)) }
                            }
                            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                repeat(4) { Box(Modifier.weight(1f).height(30.dp).clip(RoundedCornerShape(6.dp)).background(cs.outlineVariant)) }
                            }
                        } else {
                            repeat(4) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Box(Modifier.size(20.dp).clip(RoundedCornerShape(6.dp)).background(cs.tertiaryContainer))
                                    Spacer(Modifier.width(8.dp))
                                    Box(Modifier.fillMaxWidth(0.8f).height(8.dp).clip(PillShape).background(cs.outlineVariant))
                                }
                            }
                        }
                    }
                    Row(Modifier.padding(top = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                        RadioButton(on, null)
                        Spacer(Modifier.width(6.dp))
                        Text(label, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                    }
                }
            }
        }
        SettingsToggleRow(
            icon = Icons.Default.Description,
            title = "App descriptions in list rows",
            desc = "Preview app descriptions in Classic layout and search results",
            checked = prefs.showAppAbout,
            onToggle = { onUpdatePrefs(prefs.copy(showAppAbout = !prefs.showAppAbout)) }
        )
    }
}

@Composable
private fun DockStyleSection(prefs: DesktopPrefs, onUpdatePrefs: (DesktopPrefs) -> Unit) {
    val cs = MaterialTheme.colorScheme
    SettingsSection("Dock style") {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(4.dp))
                .background(cs.surfaceContainer)
                .padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            DOCK_STYLES.chunked(2).forEach { row ->
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    row.forEach { (id, label) ->
                        val on = prefs.dockStyle == id
                        Row(
                            modifier = Modifier
                                .weight(1f)
                                .clip(PillShape)
                                .background(if (on) cs.secondaryContainer else cs.surfaceContainerHighest)
                                .expressiveSpringClick(PillShape) { onUpdatePrefs(prefs.copy(dockStyle = id)) }
                                .padding(horizontal = 14.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                when (id) {
                                    "pill" -> Icons.Default.BlurOn
                                    "labels" -> Icons.AutoMirrored.Filled.Label
                                    "shapes" -> Icons.Default.Category
                                    "bar" -> Icons.Default.HorizontalRule
                                    else -> Icons.Default.AutoAwesome
                                },
                                null,
                                Modifier.size(18.dp),
                                tint = if (on) cs.onSecondaryContainer else cs.onSurfaceVariant
                            )
                            Spacer(Modifier.width(8.dp))
                            Text(
                                label,
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = if (on) FontWeight.SemiBold else FontWeight.Normal,
                                color = if (on) cs.onSecondaryContainer else cs.onSurface
                            )
                        }
                    }
                    repeat(2 - row.size) { Spacer(Modifier.weight(1f)) }
                }
            }
        }
    }
}

@Composable
private fun WideNavSection(prefs: DesktopPrefs, onUpdatePrefs: (DesktopPrefs) -> Unit) {
    val cs = MaterialTheme.colorScheme
    SettingsSection("Navigation on wide windows") {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(4.dp))
                .background(cs.surfaceContainer)
                .padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            listOf(
                Triple("rail", "Side rail", "A vertical rail down the left edge, like Orion on tablets"),
                Triple("bottom", "Bottom dock", "Keep the floating dock along the bottom edge"),
                Triple("top", "Top dock", "Float the dock at the top of the window")
            ).forEach { (id, label, desc) ->
                val on = prefs.wideNav == id
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(18.dp))
                        .background(if (on) cs.secondaryContainer else cs.surfaceContainerHighest)
                        .expressiveSpringClick(RoundedCornerShape(18.dp)) { onUpdatePrefs(prefs.copy(wideNav = id)) }
                        .padding(horizontal = 14.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    RadioButton(on, null)
                    Spacer(Modifier.width(10.dp))
                    Column(Modifier.weight(1f)) {
                        Text(
                            label,
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = if (on) FontWeight.SemiBold else FontWeight.Normal,
                            color = if (on) cs.onSecondaryContainer else cs.onSurface
                        )
                        Text(
                            desc,
                            style = MaterialTheme.typography.bodySmall,
                            color = (if (on) cs.onSecondaryContainer else cs.onSurfaceVariant).copy(alpha = 0.85f)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun DockScalePreviewSection(prefs: DesktopPrefs, onUpdatePrefs: (DesktopPrefs) -> Unit) {
    val cs = MaterialTheme.colorScheme
    SettingsSection("Dock size") {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(4.dp))
                .background(cs.surfaceContainer)
                .padding(18.dp)
        ) {
            Box(Modifier.fillMaxWidth().height(84.dp), contentAlignment = Alignment.Center) {
                Row(
                    modifier = Modifier
                        .graphicsLayer { scaleX = prefs.dockScale; scaleY = prefs.dockScale }
                        .clip(PillShape)
                        .background(cs.surfaceContainerHighest)
                        .padding(6.dp),
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    listOf(
                        Icons.Default.DesktopWindows to "PC",
                        Icons.Default.Android to null,
                        Icons.Default.Explore to null,
                        Icons.Default.GridView to null,
                        Icons.Default.Palette to null
                    ).forEach { (ic, lbl) ->
                        val on = lbl != null
                        Row(
                            Modifier.height(40.dp).clip(PillShape)
                                .background(if (on) cs.secondaryContainer else Color.Transparent)
                                .padding(horizontal = 12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(ic, null, Modifier.size(18.dp), tint = if (on) cs.onSecondaryContainer else cs.onSurfaceVariant)
                            if (lbl != null) Text(lbl, Modifier.padding(start = 6.dp), style = MaterialTheme.typography.labelLarge, color = cs.onSecondaryContainer)
                        }
                    }
                }
            }
            Slider(
                value = prefs.dockScale,
                onValueChange = { onUpdatePrefs(prefs.copy(dockScale = it)) },
                valueRange = 0.85f..1.15f
            )
            Text(
                "Scale: ${(prefs.dockScale * 100).toInt()}%",
                style = MaterialTheme.typography.labelMedium,
                color = cs.onSurfaceVariant
            )
        }
    }
}

// -------------------------------------------------------------
// SETTINGS ROW BUILDING BLOCKS
// -------------------------------------------------------------
@Composable
private fun SettingsSection(title: String, content: @Composable ColumnScope.() -> Unit) {
    Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
        Text(
            title,
            Modifier.padding(start = 16.dp, top = 8.dp, bottom = 10.dp),
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold,
            color = tonalPair(LocalSettingsAccent.current).second
        )
        Column(Modifier.clip(RoundedCornerShape(28.dp)), verticalArrangement = Arrangement.spacedBy(2.dp), content = content)
    }
}

@Composable
private fun SettingsActionRow(
    icon: ImageVector,
    title: String,
    desc: String? = null,
    onClick: (() -> Unit)? = null,
    trailing: (@Composable () -> Unit)? = null
) {
    ListItem(
        headlineContent = { Text(title, style = MaterialTheme.typography.titleMedium) },
        supportingContent = desc?.let { { Text(it) } },
        leadingContent = { TonalIconCircle(icon, LocalSettingsAccent.current) },
        trailingContent = trailing,
        modifier = Modifier.clip(RoundedCornerShape(4.dp)).then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier),
        colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.surfaceContainer)
    )
}

@Composable
private fun SettingsToggleRow(
    icon: ImageVector,
    title: String,
    desc: String,
    checked: Boolean,
    onToggle: () -> Unit
) {
    SettingsActionRow(icon, title, desc, onClick = onToggle) {
        Switch(
            checked = checked,
            onCheckedChange = { onToggle() },
            thumbContent = {
                Icon(if (checked) Icons.Default.Check else Icons.Default.Close, null, Modifier.size(SwitchDefaults.IconSize))
            }
        )
    }
}

@Composable
private fun SettingsChevron(meta: String? = null) = Row(verticalAlignment = Alignment.CenterVertically) {
    meta?.let {
        Text(it, style = MaterialTheme.typography.labelLarge, color = tonalPair(LocalSettingsAccent.current).second, fontWeight = FontWeight.Bold)
        Spacer(Modifier.width(4.dp))
    }
    Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
}

@Composable
private fun SettingsInfoCard(text: String, title: String? = null, icon: ImageVector = Icons.Default.Info) {
    val (bg, fg) = tonalPair(LocalSettingsAccent.current)
    Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(4.dp)).background(bg).padding(16.dp)) {
        Icon(icon, null, Modifier.size(20.dp), tint = fg)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            title?.let { Text(it, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, color = fg) }
            Text(text, style = MaterialTheme.typography.bodySmall, color = fg.copy(alpha = 0.85f))
        }
    }
}

@Composable
private fun SettingsStatHero(value: String, caption: String, action: @Composable () -> Unit = {}) {
    val (bg, fg) = tonalPair(LocalSettingsAccent.current)
    Row(
        modifier = Modifier
            .padding(horizontal = 16.dp, vertical = 8.dp)
            .fillMaxWidth()
            .clip(RoundedCornerShape(28.dp))
            .background(bg)
            .padding(20.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            Text(value, style = MaterialTheme.typography.displayMedium, fontWeight = FontWeight.Bold, color = fg)
            Text(caption, style = MaterialTheme.typography.bodyMedium, color = fg.copy(alpha = 0.85f))
        }
        action()
    }
}

@Composable
private fun SettingsFeatureTile(
    icon: ImageVector,
    title: String,
    desc: String,
    color: Color,
    shape: Shape,
    animate: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    val (bg, fg) = tonalPair(color)
    Column(
        modifier = modifier
            .height(164.dp)
            .expressiveSpringClick(RoundedCornerShape(28.dp), onClick = onClick)
            .background(bg)
            .padding(18.dp)
    ) {
        Box(Modifier.size(52.dp), contentAlignment = Alignment.Center) {
            SpinnerShape(shape, fg.copy(alpha = 0.16f), 52.dp, periodMs = 16_000, animate = animate)
            Icon(icon, null, tint = fg)
        }
        Spacer(Modifier.weight(1f))
        Text(title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = fg)
        Text(desc, style = MaterialTheme.typography.bodySmall, color = fg.copy(alpha = 0.8f), maxLines = 2)
    }
}

@Composable
private fun AboutStat(value: String, label: String, modifier: Modifier = Modifier) {
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            value,
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            color = LocalSettingsAccent.current
        )
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun MilestoneRow(title: String, current: Int, target: Int) {
    val cs = MaterialTheme.colorScheme
    val accent = LocalSettingsAccent.current
    val done = current >= target
    val fraction = (current.toFloat() / target.coerceAtLeast(1)).coerceIn(0f, 1f)

    Column(
        Modifier
            .fillMaxWidth()
            .background(cs.surfaceContainer)
            .padding(horizontal = 20.dp, vertical = 12.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                if (done) Icons.Default.CheckCircle else Icons.Default.RadioButtonUnchecked,
                null,
                Modifier.size(18.dp),
                tint = if (done) accent else cs.onSurfaceVariant
            )
            Spacer(Modifier.width(10.dp))
            Text(title, Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
            Text(
                if (done) "Earned" else "$current / $target",
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold,
                color = if (done) accent else cs.onSurfaceVariant
            )
        }
        Spacer(Modifier.height(8.dp))
        LinearProgressIndicator(
            progress = { fraction },
            modifier = Modifier.fillMaxWidth(),
            color = accent,
            trackColor = cs.surfaceContainerHighest
        )
    }
}

@Composable
private fun PilotProfileCard(
    installs: Int,
    favorites: Int,
    modifier: Modifier = Modifier
) {
    // Local "pilot" progression derived from real desktop milestones (no fake XP economy).
    val xp = installs * 40 + favorites * 10
    val level = (xp / 120) + 1
    val intoLevel = xp % 120
    val fraction = intoLevel.toFloat() / 120f
    val silver = listOf(Color(0xFFF4F6F8), Color(0xFFC3C9D0), Color(0xFF8F979F))
    val ink = Color(0xFF1C2127)

    Box(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(28.dp))
            .background(Brush.linearGradient(silver))
            .border(1.dp, Color.White.copy(alpha = 0.35f), RoundedCornerShape(28.dp))
            .clipToBounds()
    ) {
        // Faint Cookie12Sided foil watermark, matching the Android pilot card.
        Box(
            Modifier
                .align(Alignment.TopEnd)
                .offset(56.dp, (-46).dp)
                .size(170.dp)
                .background(ink.copy(alpha = 0.06f), OrionShapes.Cookie12Sided)
        )
        Column(Modifier.fillMaxWidth().padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Verified, null, Modifier.size(22.dp), tint = ink)
                Spacer(Modifier.width(6.dp))
                Text(
                    "ORION PILOT",
                    style = MaterialTheme.typography.labelLarge.copy(
                        letterSpacing = 2.sp,
                        fontWeight = FontWeight.Black
                    ),
                    color = ink
                )
                Spacer(Modifier.weight(1f))
                Surface(shape = PillShape, color = ink) {
                    Text(
                        "LV $level",
                        Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold,
                        color = silver[0]
                    )
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                Box(
                    Modifier.size(58.dp).clip(CircleShape).background(Color.White.copy(alpha = 0.55f)),
                    contentAlignment = Alignment.Center
                ) {
                    OrionLogo(46.dp)
                }
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(
                        "Rookie Pilot",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = ink,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        "$xp XP · $installs installs · $favorites favorites",
                        style = MaterialTheme.typography.labelLarge,
                        color = ink.copy(alpha = 0.78f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
            // XP bar to next level
            LinearProgressIndicator(
                progress = { fraction },
                modifier = Modifier.fillMaxWidth().height(8.dp).clip(PillShape),
                color = ink,
                trackColor = ink.copy(alpha = 0.18f)
            )
            // Developer socials strip: GitHub · X · Discord · Web
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                SocialPill(Icons.Default.Code, "GitHub", ink) { DesktopInstaller.openBrowser("https://github.com/RookieEnough") }
                SocialPill(Icons.Default.Public, "X", ink) { DesktopInstaller.openBrowser("https://x.com") }
                SocialPill(Icons.Default.Forum, "Discord", ink) { DesktopInstaller.openBrowser("https://discord.com") }
                SocialPill(Icons.Default.Language, "Web", ink) { DesktopInstaller.openBrowser("https://orionstore.pages.dev") }
            }
        }
    }
}

@Composable
private fun SocialPill(icon: ImageVector, label: String, ink: Color, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .clip(PillShape)
            .background(ink.copy(alpha = 0.10f))
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, label, Modifier.size(16.dp), tint = ink)
        Spacer(Modifier.width(6.dp))
        Text(label, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold, color = ink)
    }
}

@Composable
private fun OrionLogo(size: androidx.compose.ui.unit.Dp) {
    val bitmap = remember {
        runCatching {
            Thread.currentThread().contextClassLoader.getResourceAsStream("orion_icon.png")
                ?.use { ImageIO.read(it) }
                ?.toComposeImageBitmap()
        }.getOrNull()
    }
    Box(
        modifier = Modifier
            .size(size)
            .clip(RoundedCornerShape(size * 0.28f))
            .background(MaterialTheme.colorScheme.surfaceContainerHigh),
        contentAlignment = Alignment.Center
    ) {
        if (bitmap != null) {
            Image(
                bitmap = bitmap,
                contentDescription = "Orion Store",
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop
            )
        } else {
            Icon(Icons.Default.Storefront, null, Modifier.size(size * 0.5f), tint = LocalSettingsAccent.current)
        }
    }
}

@Composable
private fun QuickTilesEditorDialog(
    prefs: DesktopPrefs,
    onUpdatePrefs: (DesktopPrefs) -> Unit,
    onDismiss: () -> Unit
) {
    val cs = MaterialTheme.colorScheme
    Dialog(onDismissRequest = onDismiss) {
        Surface(shape = RoundedCornerShape(28.dp), color = cs.surfaceContainerLow, modifier = Modifier.width(460.dp)) {
            Column(Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Quick toggles", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                Text("Tap a tile to show or hide it from Settings.", style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant)
                Spacer(Modifier.height(4.dp))
                QUICK_DEFS.forEachIndexed { idx, q ->
                    val on = q.id in prefs.quickTiles
                    Surface(
                        onClick = {
                            val next = if (on) prefs.quickTiles - q.id else prefs.quickTiles + q.id
                            onUpdatePrefs(prefs.copy(quickTiles = next))
                        },
                        shape = segmentedShape(idx, QUICK_DEFS.size),
                        color = if (on) cs.secondaryContainer else cs.surfaceContainer,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            Icon(q.icon, null, tint = if (on) cs.onSecondaryContainer else cs.onSurfaceVariant)
                            Text(q.label, Modifier.weight(1f), fontWeight = FontWeight.SemiBold)
                            Icon(if (on) Icons.Default.CheckCircle else Icons.Default.Add, null, tint = if (on) cs.primary else cs.onSurfaceVariant)
                        }
                    }
                }
                Spacer(Modifier.height(4.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    Button(onClick = onDismiss, shape = PillShape) { Text("Done") }
                }
            }
        }
    }
}
