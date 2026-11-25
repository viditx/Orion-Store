package com.orion.desktop

import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.*
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.orion.store.data.AppItem
import com.orion.store.data.Platform
import com.orion.store.ui.OrionShapes
import com.orion.store.ui.PALETTES
import com.orion.store.ui.Palette
import com.orion.store.ui.PillShape
import com.orion.store.ui.Sym
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import java.io.File
import kotlin.math.cos
import kotlin.math.sin

@Composable
fun OrbitTabView(
    apps: List<AppItem>,
    query: String,
    onSelectApp: (AppItem) -> Unit
) {
    val cs = MaterialTheme.colorScheme
    val clipboard = LocalClipboardManager.current
    var copiedBanner by remember { mutableStateOf(false) }

    val repoApps = remember(apps, query) {
        val base = apps.filter { !it.repoUrl.isNullOrBlank() && it.repoUrl != "#" }
        if (query.isBlank()) base else {
            val q = query.lowercase()
            base.filter { q in it.name.lowercase() || q in it.author.lowercase() || q in (it.repoUrl ?: "").lowercase() }
        }
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize().wrapContentWidth().widthIn(max = 1200.dp),
        contentPadding = PaddingValues(start = 24.dp, end = 24.dp, top = 8.dp, bottom = 120.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        item {
            Surface(
                shape = RoundedCornerShape(28.dp),
                color = cs.surfaceContainerHigh,
                modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp)
            ) {
                Row(
                    modifier = Modifier.padding(24.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(20.dp)
                ) {
                    Box(
                        modifier = Modifier.size(64.dp).background(cs.primaryContainer, OrionShapes.Cookie9Sided),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Default.Explore, null, Modifier.size(32.dp), tint = cs.onPrimaryContainer)
                    }
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text("Orbit Upstream Tracker", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                        Text(
                            "Inspect ${repoApps.size} verified upstream repositories or export Obtainium-compatible tracking JSON.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = cs.onSurfaceVariant
                        )
                    }
                    Button(
                        onClick = {
                            val jsonExport = JsonObject(
                                mapOf(
                                    "apps" to JsonArray(
                                        repoApps.filter { it.platform == Platform.ANDROID }.take(100).map { app ->
                                            JsonObject(
                                                mapOf(
                                                    "id" to JsonPrimitive(app.packageName ?: app.id),
                                                    "url" to JsonPrimitive(app.repoUrl.orEmpty()),
                                                    "author" to JsonPrimitive(app.author),
                                                    "name" to JsonPrimitive(app.name)
                                                )
                                            )
                                        }
                                    )
                                )
                            ).toString()
                            clipboard.setText(AnnotatedString(jsonExport))
                            copiedBanner = true
                        },
                        shape = PillShape
                    ) {
                        Icon(Icons.Default.ContentCopy, null, Modifier.size(16.dp))
                        Spacer(Modifier.width(8.dp))
                        Text(if (copiedBanner) "Export Copied" else "Copy Obtainium JSON")
                    }
                }
            }
        }

        itemsIndexed(repoApps.take(80), key = { _, it -> "orb-${it.id}" }) { idx, app ->
            Surface(
                onClick = { onSelectApp(app) },
                shape = segmentedShape(idx, minOf(repoApps.size, 80)),
                color = cs.surfaceContainer,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    DesktopAppIcon(app.icon, app.name, 44.dp)
                    Column(Modifier.weight(1f)) {
                        Text(app.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(
                            app.repoUrl.orEmpty().removePrefix("https://"),
                            style = MaterialTheme.typography.bodySmall,
                            color = cs.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    Surface(shape = PillShape, color = cs.surfaceContainerHigh) {
                        Text(
                            "${app.platform} · ${app.latestVersion}",
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                            style = MaterialTheme.typography.labelSmall,
                            color = cs.primary
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun MyAppsTabView(
    downloads: List<DownloadState>,
    favoriteApps: List<AppItem>,
    onSelectApp: (AppItem) -> Unit,
    onLaunchFile: (File) -> Unit,
    onRevealFile: (File) -> Unit,
    onClearFinished: () -> Unit
) {
    val cs = MaterialTheme.colorScheme
    val ordered = remember(downloads) {
        downloads.sortedWith(
            compareBy(
                { if (it.isComplete || it.error != null) 1 else 0 },
                { it.app.name.lowercase() }
            )
        )
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize().wrapContentWidth().widthIn(max = 1200.dp),
        contentPadding = PaddingValues(start = 24.dp, end = 24.dp, top = 8.dp, bottom = 120.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        if (ordered.isNotEmpty()) {
            item {
                Row(
                    Modifier.fillMaxWidth().padding(vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("Downloads & Installers", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                    if (ordered.any { it.isComplete || it.error != null }) {
                        TextButton(onClick = onClearFinished) { Text("Clear finished") }
                    }
                }
            }
            itemsIndexed(ordered, key = { _, task -> "dl-${task.app.id}" }) { idx, task ->
                Surface(
                    shape = segmentedShape(idx, ordered.size),
                    color = cs.surfaceContainer,
                    onClick = { onSelectApp(task.app) },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        DesktopAppIcon(task.app.icon, task.app.name, 48.dp)
                        Column(Modifier.weight(1f)) {
                            Text(task.app.name, fontWeight = FontWeight.Bold)
                            Text(
                                task.status,
                                style = MaterialTheme.typography.bodySmall,
                                color = if (task.error != null) cs.error else cs.primary,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis
                            )
                            if (!task.isComplete && task.error == null) {
                                Spacer(Modifier.height(6.dp))
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
                        if (task.isComplete) {
                            val portable = task.installDir != null && task.localFile?.extension.equals("exe", true)
                            FilledTonalButton(
                                onClick = {
                                    if (portable) task.localFile?.let(onLaunchFile)
                                    else onRevealFile(task.installDir ?: task.localFile ?: DesktopCatalogRepository.downloadsDir)
                                },
                                shape = PillShape
                            ) {
                                Text(if (portable) "Run" else "Folder")
                            }
                            if (portable) {
                                IconButton(onClick = { onRevealFile(task.installDir) }) {
                                    Icon(Icons.Default.FolderOpen, "Show in folder", tint = cs.onSurfaceVariant)
                                }
                            }
                        }
                    }
                }
            }
        }

        item {
            Text("Saved Favorites (${favoriteApps.size})", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 12.dp, bottom = 4.dp))
        }

        if (favoriteApps.isEmpty()) {
            item {
                Column(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 48.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Box(Modifier.size(88.dp).background(cs.surfaceContainerHigh, OrionShapes.Cookie9Sided), contentAlignment = Alignment.Center) {
                        Icon(Icons.Default.FavoriteBorder, null, Modifier.size(36.dp), tint = cs.onSurfaceVariant)
                    }
                    Text("No favorites yet", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Text("Click the heart icon on any app to keep it pinned here.", style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant)
                }
            }
        } else {
            itemsIndexed(favoriteApps, key = { _, it -> "fav-${it.id}" }) { idx, app ->
                Surface(
                    shape = segmentedShape(idx, favoriteApps.size),
                    color = cs.surfaceContainer,
                    onClick = { onSelectApp(app) },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        DesktopAppIcon(app.icon, app.name, 48.dp)
                        Column(Modifier.weight(1f)) {
                            Text(app.name, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text("${app.platform} · ${app.category} · ${app.latestVersion}", style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant)
                        }
                        Icon(Icons.AutoMirrored.Filled.ArrowForward, null, tint = cs.onSurfaceVariant)
                    }
                }
            }
        }
    }
}

@Composable
fun DesktopDevCoin(
    flipped: Boolean,
    onTap: () -> Unit
) {
    val cs = MaterialTheme.colorScheme
    val rot by animateFloatAsState(
        targetValue = if (flipped) 180f else 0f,
        animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
        label = "dev-coin-flip"
    )
    val infiniteTransition = rememberInfiniteTransition(label = "halo")
    val spin by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(tween(24_000, easing = LinearEasing)),
        label = "halo-spin"
    )

    val orbitBadges = listOf(
        Triple("Legend", Sym.WorkspacePremium, Color(0xFFEAB308)),
        Triple("Guardian", Sym.Shield, Color(0xFFEF4444)),
        Triple("Supernova", Sym.AutoAwesome, Color(0xFFEC4899))
    )

    Box(Modifier.size(260.dp, 200.dp), contentAlignment = Alignment.Center) {
        Box(
            Modifier.size(176.dp)
                .graphicsLayer { rotationZ = spin }
                .background(cs.primary.copy(alpha = 0.25f), OrionShapes.SoftBurst)
        )

        orbitBadges.forEachIndexed { i, (_, ic, c) ->
            val bg = c.copy(alpha = 0.22f)
            val fg = c
            Box(
                Modifier.size(38.dp).graphicsLayer {
                    val a = Math.toRadians((spin * 2.0 + i * 120.0))
                    translationX = (cos(a) * 112.dp.toPx()).toFloat()
                    translationY = (sin(a) * 70.dp.toPx()).toFloat()
                    val z = sin(a).toFloat()
                    scaleX = 0.85f + 0.2f * z
                    scaleY = scaleX
                    alpha = 0.7f + 0.3f * z
                }.clip(CircleShape).background(bg),
                contentAlignment = Alignment.Center
            ) {
                Icon(ic, null, Modifier.size(20.dp), tint = fg)
            }
        }

        Box(
            Modifier.size(128.dp)
                .graphicsLayer {
                    rotationY = if (rot > 90f) rot - 180f else rot
                    cameraDistance = 12f * density
                    shadowElevation = 16.dp.toPx() * kotlin.math.abs(cos(Math.toRadians(rot.toDouble()))).toFloat()
                    shape = CircleShape
                }
                .clip(CircleShape)
                .clickable(onClick = onTap),
            contentAlignment = Alignment.Center
        ) {
            if (rot <= 90f) {
                Box(Modifier.fillMaxSize().background(cs.primaryContainer), contentAlignment = Alignment.Center) {
                    Text("R", fontSize = 60.sp, fontWeight = FontWeight.Black, color = cs.onPrimaryContainer)
                }
            } else {
                Column(
                    Modifier.fillMaxSize().background(cs.tertiaryContainer),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Icon(Sym.EmojiEvents, "Hall of Fame", Modifier.size(46.dp), tint = cs.onTertiaryContainer)
                    Spacer(Modifier.height(4.dp))
                    Text("ORION 2.0", fontSize = 11.sp, fontWeight = FontWeight.Black, letterSpacing = 1.sp, color = cs.onTertiaryContainer)
                }
            }
        }
    }
}

@Composable
fun DevAboutTabView(
    onOpenSettings: () -> Unit
) {
    val cs = MaterialTheme.colorScheme
    var coinFlipped by remember { mutableStateOf(false) }

    LazyColumn(
        modifier = Modifier.fillMaxSize().wrapContentWidth().widthIn(max = 920.dp),
        contentPadding = PaddingValues(start = 24.dp, end = 24.dp, top = 16.dp, bottom = 120.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Flippable Dev Coin Hero
        item {
            Column(
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                DesktopDevCoin(flipped = coinFlipped, onTap = { coinFlipped = !coinFlipped })
                Text("RookieZ", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Black)
                Text(
                    if (coinFlipped) "Tap again for Developer Profile" else "Tap the coin to flip it",
                    style = MaterialTheme.typography.labelMedium,
                    color = cs.primary
                )
                Text(
                    "Orion is the future, one commit at a time. No ads, no tracking, just code.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = cs.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(horizontal = 32.dp, vertical = 4.dp)
                )
            }
        }

        // Badges Showcase Shelf with + Add badge
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(
                    shape = PillShape,
                    color = cs.surfaceContainerHigh,
                    modifier = Modifier.clickable { }
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Icon(Sym.Add, null, Modifier.size(16.dp), tint = cs.primary)
                        Text("Add badge", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold)
                    }
                }
                Surface(shape = PillShape, color = Color(0xFF8B5CF6).copy(alpha = 0.18f)) {
                    Row(Modifier.padding(horizontal = 12.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Icon(Sym.Palette, null, Modifier.size(16.dp), tint = Color(0xFF8B5CF6))
                        Text("Icon Collector", style = MaterialTheme.typography.labelSmall, color = Color(0xFF8B5CF6))
                    }
                }
                Surface(shape = PillShape, color = Color(0xFF22C55E).copy(alpha = 0.18f)) {
                    Row(Modifier.padding(horizontal = 12.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Icon(Sym.ViewAgenda, null, Modifier.size(16.dp), tint = Color(0xFF22C55E))
                        Text("Dock Master", style = MaterialTheme.typography.labelSmall, color = Color(0xFF22C55E))
                    }
                }
            }
        }

        // Connect Section (Organic Scalloped Buttons matching Screenshot 4)
        item {
            Column(modifier = Modifier.fillMaxWidth().padding(top = 10.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("Connect", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Surface(
                    shape = RoundedCornerShape(24.dp),
                    color = cs.surfaceContainerHigh,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 18.dp),
                        horizontalArrangement = Arrangement.SpaceEvenly,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        listOf(
                            Triple(Sym.BrandGitHub, "GitHub", Color(0xFF94A3B8)) to "https://github.com/RookieEnough/Orion-Store",
                            Triple(Sym.BrandX, "X", Color(0xFF64748B)) to "https://x.com/_Rookie_Z",
                            Triple(Sym.BrandDiscord, "Discord", Color(0xFF5865F2)) to "https://discord.gg/CrM6y4ujnq",
                            Triple(Sym.Public, "Website", Color(0xFF6366F1)) to "https://orionstore.pages.dev"
                        ).forEachIndexed { i, (spec, url) ->
                            val shape = listOf(OrionShapes.Cookie9Sided, OrionShapes.Clover4Leaf, OrionShapes.Sunny, OrionShapes.Pentagon)[i]
                            val (ic, name, col) = spec
                            Column(
                                modifier = Modifier.clip(RoundedCornerShape(20.dp)).clickable { DesktopInstaller.openBrowser(url) }.padding(8.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Box(
                                    modifier = Modifier.size(64.dp).clip(shape).background(col.copy(alpha = 0.18f)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(ic, name, Modifier.size(28.dp), tint = col)
                                }
                                Text(name, Modifier.padding(top = 8.dp), style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold)
                            }
                        }
                    }
                }
            }
        }

        // Support Orion (Purple Fuel the Code + Brown Buy me a coffee)
        item {
            Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("Support Orion", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)

                // Fuel the Code banner
                Surface(
                    shape = RoundedCornerShape(24.dp),
                    color = Color(0xFF4C1D95),
                    modifier = Modifier.fillMaxWidth().clickable { DesktopInstaller.openBrowser("https://github.com/RookieEnough/Orion-Store") }
                ) {
                    Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                            Box(
                                modifier = Modifier.size(48.dp).clip(OrionShapes.Cookie9Sided).background(Color(0xFF6D28D9)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(Sym.RocketLaunch, "Fuel", Modifier.size(24.dp), tint = Color(0xFFE9D5FF))
                            }
                            Column(Modifier.weight(1f)) {
                                Text("Fuel the Code", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = Color.White)
                                Text("Start your journey", style = MaterialTheme.typography.bodySmall, color = Color(0xFFDDD6FE))
                            }
                            Icon(Icons.Default.PlayArrow, "Play", tint = Color.White, modifier = Modifier.size(24.dp))
                        }
                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            LinearProgressIndicator(
                                progress = { 0.15f },
                                modifier = Modifier.fillMaxWidth().height(4.dp).clip(PillShape),
                                color = Color(0xFFA78BFA),
                                trackColor = Color(0xFF3B0764)
                            )
                            Text("0 watched · 3 more to Backer", style = MaterialTheme.typography.labelSmall, color = Color(0xFFC4B5FD))
                        }
                    }
                }

                // Buy me a coffee
                Surface(
                    shape = RoundedCornerShape(20.dp),
                    color = Color(0xFF382312),
                    modifier = Modifier.fillMaxWidth().clickable { DesktopInstaller.openBrowser("https://ko-fi.com/rookie_z") }
                ) {
                    Row(
                        modifier = Modifier.padding(18.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        Box(
                            modifier = Modifier.size(42.dp).clip(CircleShape).background(Color(0xFF78350F)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(Sym.Coffee, "Coffee", Modifier.size(22.dp), tint = Color(0xFFFDE68A))
                        }
                        Column(Modifier.weight(1f)) {
                            Text("Buy me a coffee", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, color = Color.White)
                            Text("Ko-fi, UPI or PayPal", style = MaterialTheme.typography.bodySmall, color = Color(0xFFD4B896))
                        }
                        Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null, tint = Color(0xFFD4B896))
                    }
                }
            }
        }

        // Help Bento (FAQs + What's new)
        item {
            Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("Help", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Surface(
                        shape = RoundedCornerShape(20.dp),
                        color = cs.surfaceContainerHigh,
                        modifier = Modifier.weight(1f).clickable { DesktopInstaller.openBrowser("https://github.com/RookieEnough/Orion-Store/wiki") }
                    ) {
                        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Box(Modifier.size(36.dp).clip(CircleShape).background(cs.surfaceContainerHighest), contentAlignment = Alignment.Center) {
                                Icon(Icons.Default.HelpOutline, null, Modifier.size(20.dp), tint = cs.onSurfaceVariant)
                            }
                            Text("FAQs", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                            Text("Secrets & safety", style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant)
                        }
                    }
                    Surface(
                        shape = RoundedCornerShape(20.dp),
                        color = cs.surfaceContainerHigh,
                        modifier = Modifier.weight(1f).clickable { DesktopInstaller.openBrowser("https://github.com/RookieEnough/Orion-Store/releases") }
                    ) {
                        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Box(Modifier.size(36.dp).clip(CircleShape).background(cs.surfaceContainerHighest), contentAlignment = Alignment.Center) {
                                Icon(Icons.Default.CheckCircleOutline, null, Modifier.size(20.dp), tint = cs.onSurfaceVariant)
                            }
                            Text("What's new", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                            Text("Release notes", style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant)
                        }
                    }
                }
            }
        }

        // Built by attribution (Clean human credits, zero AI corporate fluff)
        item {
            Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("Built by", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Surface(
                        shape = RoundedCornerShape(22.dp),
                        color = cs.surfaceContainerHigh,
                        modifier = Modifier.weight(1f)
                    ) {
                        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                Box(Modifier.size(40.dp).clip(CircleShape).background(cs.primaryContainer), contentAlignment = Alignment.Center) {
                                    Text("R", fontWeight = FontWeight.Bold, color = cs.onPrimaryContainer)
                                }
                                Column {
                                    Text("Sarthak Sinha", fontWeight = FontWeight.Bold)
                                    Text("@RookieEnough", style = MaterialTheme.typography.labelSmall, color = cs.primary)
                                }
                            }
                            Text("Original Orion Android app & Orion-Data catalog.", style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant)
                        }
                    }
                    Surface(
                        shape = RoundedCornerShape(22.dp),
                        color = cs.surfaceContainerHigh,
                        modifier = Modifier.weight(1f)
                    ) {
                        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                Box(Modifier.size(40.dp).clip(CircleShape).background(cs.secondaryContainer), contentAlignment = Alignment.Center) {
                                    Text("V", fontWeight = FontWeight.Bold, color = cs.onSecondaryContainer)
                                }
                                Column {
                                    Text("Vidit", fontWeight = FontWeight.Bold)
                                    Text("@viditx", style = MaterialTheme.typography.labelSmall, color = cs.secondary)
                                }
                            }
                            Text("Windows desktop client & setup installer.", style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant)
                        }
                    }
                }
            }
        }

        // Footer Pills & Tagline
        item {
            Column(
                modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Surface(shape = PillShape, color = cs.surfaceContainerHighest) {
                        Text("v2.0.0", Modifier.padding(horizontal = 12.dp, vertical = 5.dp), style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                    }
                    Surface(shape = PillShape, color = cs.surfaceContainerHighest) {
                        Text("RE · Native", Modifier.padding(horizontal = 12.dp, vertical = 5.dp), style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                    }
                    Surface(shape = PillShape, color = Color(0xFF059669)) {
                        Text("Source: Remote", Modifier.padding(horizontal = 12.dp, vertical = 5.dp), style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, color = Color.White)
                    }
                }
                Text("Made with 💜 for Geeks", style = MaterialTheme.typography.labelMedium, color = cs.onSurfaceVariant)
            }
        }
    }
}

@Composable
fun PilotProfileDialog(
    allAppsCount: Int,
    downloadsCount: Int,
    favoritesCount: Int,
    onDismiss: () -> Unit
) {
    val cs = MaterialTheme.colorScheme
    var flipped by remember { mutableStateOf(false) }
    val flipRot by animateFloatAsState(
        targetValue = if (flipped) 180f else 0f,
        animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
        label = "pilot-flip"
    )
    val inkColors = listOf(Color(0xFF1B1B1F), Color(0xFF1D4ED8), Color(0xFFDC2626), Color(0xFF15803D), Color(0xFF7C3AED), Color(0xFFB45309))
    var selectedInk by remember { mutableStateOf(0) }

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(32.dp),
            color = cs.surfaceContainerHigh,
            shadowElevation = 24.dp,
            modifier = Modifier.widthIn(max = 480.dp).fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Pilot ID Holographic Card (Flippable with signature strip on back)
                Surface(
                    shape = RoundedCornerShape(26.dp),
                    color = Color(0xFF1C1D24),
                    border = BorderStroke(1.dp, Color(0xFF333544)),
                    modifier = Modifier.fillMaxWidth().height(180.dp)
                        .graphicsLayer {
                            rotationY = if (flipRot > 90f) flipRot - 180f else flipRot
                            cameraDistance = 12f * density
                        }
                        .clickable { flipped = !flipped }
                ) {
                    if (flipRot <= 90f) {
                        // Front Face: ORION PILOT
                        Column(
                            modifier = Modifier.fillMaxSize().padding(18.dp),
                            verticalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                OrionLogoMark()
                                Spacer(Modifier.width(8.dp))
                                Text("ORION PILOT", style = MaterialTheme.typography.labelLarge, letterSpacing = 2.sp, fontWeight = FontWeight.Black, color = Color.White)
                                Spacer(Modifier.weight(1f))
                                Surface(shape = PillShape, color = Color.Black) {
                                    Text("LV 5", Modifier.padding(horizontal = 10.dp, vertical = 3.dp), style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Black, color = Color.White)
                                }
                            }
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                                Box(
                                    modifier = Modifier.size(54.dp).clip(OrionShapes.Cookie9Sided).background(cs.primaryContainer),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text("U", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Black, color = cs.onPrimaryContainer)
                                }
                                Column {
                                    Text("Unnamed pilot", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = Color.White)
                                    Text("Cadet · 450 XP", style = MaterialTheme.typography.labelMedium, color = Color(0xFF94A3B8))
                                }
                            }
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text("0003 · 3929 · 03", style = MaterialTheme.typography.labelSmall, color = Color(0xFF64748B))
                                Text("Since Today", style = MaterialTheme.typography.labelSmall, color = Color(0xFF64748B))
                            }
                        }
                    } else {
                        // Back Face: PILOT SIGNATURE Strip
                        Column(
                            modifier = Modifier.fillMaxSize().padding(18.dp),
                            verticalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text("PILOT SIGNATURE", style = MaterialTheme.typography.labelLarge, letterSpacing = 2.sp, fontWeight = FontWeight.Black, color = Color.White)
                                Spacer(Modifier.weight(1f))
                                Text("Tap to flip", style = MaterialTheme.typography.labelSmall, color = Color(0xFF94A3B8))
                            }
                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = Color(0xFFFAFAF7),
                                modifier = Modifier.fillMaxWidth().height(60.dp)
                            ) {
                                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                    Text("Signed by Orion Pilot", style = MaterialTheme.typography.bodyMedium, color = inkColors[selectedInk], fontWeight = FontWeight.Medium)
                                }
                            }
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                                inkColors.forEachIndexed { i, c ->
                                    val isSelected = i == selectedInk
                                    Box(
                                        modifier = Modifier.size(24.dp)
                                            .clip(CircleShape)
                                            .background(c)
                                            .border(if (isSelected) 2.dp else 0.dp, Color.White, CircleShape)
                                            .clickable { selectedInk = i }
                                    )
                                }
                            }
                        }
                    }
                }

                Text(
                    "Tilt or drag the card · tap it to flip and sign",
                    style = MaterialTheme.typography.labelSmall,
                    color = cs.onSurfaceVariant
                )

                // 4 Bento Cards (Matching Android Screenshot 1 exact colors)
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    // Favorites: Pink
                    Surface(shape = RoundedCornerShape(16.dp), color = Color(0xFF4A1525), modifier = Modifier.weight(1f)) {
                        Column(Modifier.padding(10.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("$favoritesCount", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Black, color = Color(0xFFF472B6))
                            Text("Favorites", style = MaterialTheme.typography.labelSmall, color = Color(0xFFFBCFE8))
                        }
                    }
                    // Downloaded: Emerald
                    Surface(shape = RoundedCornerShape(16.dp), color = Color(0xFF063523), modifier = Modifier.weight(1f)) {
                        Column(Modifier.padding(10.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("$downloadsCount", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Black, color = Color(0xFF34D399))
                            Text("Downloaded", style = MaterialTheme.typography.labelSmall, color = Color(0xFFA7F3D0))
                        }
                    }
                    // XP earned: Amber
                    Surface(shape = RoundedCornerShape(16.dp), color = Color(0xFF382405), modifier = Modifier.weight(1f)) {
                        Column(Modifier.padding(10.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("450", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Black, color = Color(0xFFFBBF24))
                            Text("XP earned", style = MaterialTheme.typography.labelSmall, color = Color(0xFFFDE68A))
                        }
                    }
                    // Rank: Purple
                    Surface(shape = RoundedCornerShape(16.dp), color = Color(0xFF271246), modifier = Modifier.weight(1f)) {
                        Column(Modifier.padding(10.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("Cadet", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Black, color = Color(0xFFC084FC))
                            Text("Rank", style = MaterialTheme.typography.labelSmall, color = Color(0xFFE9D5FF))
                        }
                    }
                }

                // Badges Shelf (0 of 17 unlocked + locked soft-burst badges)
                Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("Badges unlocked", style = MaterialTheme.typography.labelSmall, color = cs.onSurfaceVariant)
                        Text("0 of 17 unlocked", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, color = cs.primary)
                    }
                    LinearProgressIndicator(
                        progress = { 0.05f },
                        modifier = Modifier.fillMaxWidth().height(6.dp).clip(PillShape)
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Surface(shape = PillShape, color = cs.surfaceContainerHighest, modifier = Modifier.weight(1f)) {
                            Row(Modifier.padding(horizontal = 8.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                Icon(Sym.Palette, null, Modifier.size(14.dp), tint = Color(0xFF8B5CF6))
                                Text("Icon Collector", style = MaterialTheme.typography.labelSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            }
                        }
                        Surface(shape = PillShape, color = cs.surfaceContainerHighest, modifier = Modifier.weight(1f)) {
                            Row(Modifier.padding(horizontal = 8.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                Icon(Sym.ViewAgenda, null, Modifier.size(14.dp), tint = Color(0xFF22C55E))
                                Text("Dock Master", style = MaterialTheme.typography.labelSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            }
                        }
                        Surface(shape = PillShape, color = cs.surfaceContainerHighest, modifier = Modifier.weight(1f)) {
                            Row(Modifier.padding(horizontal = 8.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                Icon(Sym.WorkspacePremium, null, Modifier.size(14.dp), tint = Color(0xFFEAB308))
                                Text("Legend", style = MaterialTheme.typography.labelSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            }
                        }
                    }
                }

                Button(
                    onClick = onDismiss,
                    shape = PillShape,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Close")
                }
            }
        }
    }
}
