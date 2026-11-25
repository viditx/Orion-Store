package com.orion.desktop

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
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
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import coil3.compose.AsyncImage
import com.orion.store.data.AppItem
import com.orion.store.data.AppVariant
import com.orion.store.data.BundleItem
import com.orion.store.data.CatalogProcessor
import com.orion.store.data.Platform
import com.orion.store.ui.OrionShapes
import com.orion.store.ui.PillShape
import com.orion.store.ui.appSeedAccent
import com.orion.store.ui.compactCount
import com.orion.store.ui.tonalPair
import java.io.File

@Composable
fun DesktopDetailScreen(
    baseApp: AppItem,
    isFavorite: Boolean,
    downloadState: DownloadState?,
    onToggleFavorite: () -> Unit,
    onBack: () -> Unit,
    onDownloadAsset: (AppItem, String?, String?) -> Unit,
    onLaunchFile: (File) -> Unit,
    onRevealFile: (File) -> Unit
) {
    val cs = MaterialTheme.colorScheme
    val clipboard = LocalClipboardManager.current
    var liveVariants by remember(baseApp.id) { mutableStateOf(baseApp.variants) }
    var liveVersion by remember(baseApp.id) { mutableStateOf(baseApp.latestVersion) }
    var liveSize by remember(baseApp.id) { mutableStateOf(baseApp.size) }
    var resolvingRelease by remember(baseApp.id) { mutableStateOf(false) }
    var releaseNotes by remember(baseApp.id) { mutableStateOf<String?>(null) }
    var lightboxUrl by remember { mutableStateOf<String?>(null) }
    var variantSheetOpen by remember { mutableStateOf(false) }
    var copiedNotice by remember { mutableStateOf(false) }

    // Always resolve latest live release from GitHub / GitLab / Codeberg in real time
    LaunchedEffect(baseApp.id) {
        resolvingRelease = true
        val release = ReleaseResolver.resolve(baseApp)
        if (release != null) {
            if (release.tag.isNotBlank()) liveVersion = release.tag.removePrefix("v")
            if (!release.notes.isNullOrBlank()) releaseNotes = release.notes
            if (release.assets.isNotEmpty()) {
                liveVariants = release.assets
                release.assets.first().size?.let { liveSize = formatBytes(it) }
            }
        }
        resolvingRelease = false
    }

    val primaryUrl = liveVariants.firstOrNull()?.url
        ?: baseApp.downloadUrl.takeIf { it.isNotBlank() && it != "#" }
    val primaryFilename = liveVariants.firstOrNull()?.name?.takeIf { it.isNotBlank() }
        ?: primaryUrl?.let { fallbackFilename(baseApp, it) }
        ?: fallbackFilename(baseApp, "")
    val completed = downloadState?.isComplete == true
    val busy = downloadState != null && !downloadState.isComplete && downloadState.error == null
    val portable = completed && downloadState.installDir != null &&
        downloadState.localFile?.extension.equals("exe", true)

    // Dynamic icon-derived atmospheric wash gradient (exact 1:1 parity with Android Detail.kt:432-442)
    val extractedColor by produceState<Color?>(null, baseApp.icon) {
        value = DesktopCatalogRepository.extractIconColor(baseApp.icon)
    }
    val accentColor = extractedColor ?: appSeedAccent(baseApp.id)
    val washBase = tonalPair(accentColor).first
    val wash by animateColorAsState(washBase, animationSpec = tween(500), label = "detailWash")

    Box(modifier = Modifier.fillMaxSize().background(cs.surface)) {
        // Ambient Aura Gradient Wash flowing down from top edge
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(480.dp)
                .background(
                    Brush.verticalGradient(
                        listOf(
                            wash.copy(alpha = 0.58f),
                            wash.copy(alpha = 0.32f),
                            wash.copy(alpha = 0.12f),
                            Color.Transparent
                        )
                    )
                )
        )

        // Main unified scrolling single column (Centered with readable max width ~880dp)
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 24.dp),
            contentPadding = PaddingValues(top = 80.dp, bottom = 120.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(22.dp)
        ) {
            item {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .widthIn(max = 880.dp),
                    verticalArrangement = Arrangement.spacedBy(20.dp)
                ) {
                    // App Head
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            val (glow, _) = tonalPair(accentColor)
                            Box(
                                Modifier
                                    .size(98.dp)
                                    .clip(CircleShape)
                                    .background(glow.copy(alpha = 0.45f))
                            )
                            DesktopAppIcon(
                                url = baseApp.icon,
                                name = baseApp.name,
                                size = 96.dp,
                                shape = RoundedCornerShape(24.dp)
                            )
                        }
                        Spacer(Modifier.width(20.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                baseApp.name,
                                style = MaterialTheme.typography.headlineMedium,
                                fontWeight = FontWeight.Bold,
                                color = cs.onSurface,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis
                            )
                            Spacer(Modifier.height(4.dp))
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    baseApp.author,
                                    style = MaterialTheme.typography.titleMedium,
                                    color = cs.primary,
                                    fontWeight = FontWeight.SemiBold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.weight(1f, false)
                                )
                                Spacer(Modifier.width(6.dp))
                                Icon(Icons.Default.Verified, null, modifier = Modifier.size(18.dp), tint = cs.primary)
                            }

                            val counts = listOfNotNull(
                                baseApp.stars?.let { "${compactCount(it)} ${if (it == 1) "star" else "stars"}" },
                                baseApp.downloadCount?.takeIf { it > 0 }?.let { "${compactCount(it)} downloads" }
                            )
                            if (counts.isNotEmpty()) {
                                Row(Modifier.padding(top = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        if (baseApp.stars != null) Icons.Default.Star else Icons.Default.Download,
                                        null,
                                        Modifier.size(15.dp),
                                        tint = cs.tertiary
                                    )
                                    Spacer(Modifier.width(4.dp))
                                    Text(
                                        counts.joinToString(" · "),
                                        style = MaterialTheme.typography.labelMedium,
                                        color = cs.onSurfaceVariant,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }
                        }
                    }

                    // Stats Strip (Exact replica of Detail.kt:305-331 with high contrast)
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(IntrinsicSize.Min)
                            .padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        val displayVer = if (liveVersion.isBlank()) "Latest" else liveVersion.removePrefix("v").take(14).trimEnd('.', '-', '_')
                        StatCell(
                            label = "Version",
                            value = {
                                Text(
                                    displayVer,
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = cs.onSurface,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            },
                            modifier = Modifier.weight(1f)
                        )
                        VerticalDivider(
                            modifier = Modifier.padding(vertical = 6.dp),
                            color = cs.outlineVariant.copy(alpha = 0.35f)
                        )
                        StatCell(
                            label = "Size",
                            value = {
                                Text(
                                    liveSize.ifBlank { "Varies" },
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = cs.onSurface,
                                    maxLines = 1
                                )
                            },
                            modifier = Modifier.weight(1f)
                        )
                        VerticalDivider(
                            modifier = Modifier.padding(vertical = 6.dp),
                            color = cs.outlineVariant.copy(alpha = 0.35f)
                        )
                        StatCell(
                            label = baseApp.platform,
                            value = {
                                Icon(
                                    when (baseApp.platform) {
                                        Platform.ANDROID -> Icons.Default.Android
                                        Platform.TV -> Icons.Default.Tv
                                        else -> Icons.Default.DesktopWindows
                                    },
                                    null,
                                    tint = cs.onSurface,
                                    modifier = Modifier.size(24.dp)
                                )
                            },
                            modifier = Modifier.weight(1f)
                        )
                    }

                    // Tags row
                    if (baseApp.tags.isNotEmpty()) {
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            itemsIndexed(baseApp.tags) { _, tag ->
                                Surface(shape = PillShape, color = cs.surfaceContainerHigh.copy(alpha = 0.8f)) {
                                    Text(
                                        tag,
                                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 5.dp),
                                        style = MaterialTheme.typography.labelMedium,
                                        color = cs.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    }

                    // Active download status banner if active
                    if (downloadState != null && downloadState.error == null) {
                        val container = if (completed) cs.secondaryContainer else cs.primaryContainer
                        val onContainer = if (completed) cs.onSecondaryContainer else cs.onPrimaryContainer
                        Surface(
                            shape = RoundedCornerShape(20.dp),
                            color = container,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Icon(
                                        if (completed) Icons.Default.CheckCircle else Icons.Default.Downloading,
                                        null,
                                        Modifier.size(18.dp),
                                        tint = onContainer
                                    )
                                    Text(
                                        downloadState.status,
                                        style = MaterialTheme.typography.labelLarge,
                                        color = onContainer,
                                        fontWeight = FontWeight.SemiBold,
                                        maxLines = 3,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                                if (busy) {
                                    val fraction = downloadState.progress.takeIf { it in 0f..1f }
                                    if (fraction != null) {
                                        LinearProgressIndicator(
                                            progress = { fraction },
                                            modifier = Modifier.fillMaxWidth(),
                                            color = onContainer,
                                            trackColor = onContainer.copy(alpha = 0.2f)
                                        )
                                    } else {
                                        LinearProgressIndicator(
                                            modifier = Modifier.fillMaxWidth(),
                                            color = onContainer,
                                            trackColor = onContainer.copy(alpha = 0.2f)
                                        )
                                    }
                                }
                                if (completed) {
                                    val target = downloadState.installDir ?: downloadState.localFile
                                    if (target != null) {
                                        TextButton(onClick = { onRevealFile(target) }) {
                                            Text("Show in folder", color = onContainer)
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // Screenshots Gallery (Directly below App Head & Stats)
            if (baseApp.screenshots.isNotEmpty()) {
                item {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .widthIn(max = 880.dp)
                    ) {
                        LazyRow(
                            horizontalArrangement = Arrangement.spacedBy(14.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            itemsIndexed(baseApp.screenshots) { _, shotUrl ->
                                val isPortrait = baseApp.platform == Platform.ANDROID
                                Box(
                                    modifier = Modifier
                                        .height(if (isPortrait) 360.dp else 260.dp)
                                        .widthIn(min = 160.dp, max = 500.dp)
                                        .shadow(8.dp, RoundedCornerShape(22.dp))
                                        .clip(RoundedCornerShape(22.dp))
                                        .background(cs.surfaceContainer)
                                        .clickable { lightboxUrl = shotUrl }
                                ) {
                                    AsyncImage(
                                        model = shotUrl,
                                        contentDescription = "Screenshot",
                                        modifier = Modifier.fillMaxHeight().wrapContentWidth(),
                                        contentScale = ContentScale.FillHeight
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // About this app
            item {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .widthIn(max = 880.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    var expanded by remember { mutableStateOf(false) }
                    Text(
                        "About this app",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = cs.onSurface
                    )
                    Text(
                        baseApp.description.ifBlank { "No description provided." },
                        style = MaterialTheme.typography.bodyLarge,
                        color = cs.onSurfaceVariant,
                        maxLines = if (expanded) Int.MAX_VALUE else 5,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier
                            .animateContentSize()
                            .clickable { expanded = !expanded }
                    )
                    if (baseApp.description.length > 220) {
                        TextButton(onClick = { expanded = !expanded }) {
                            Text(if (expanded) "Show less" else "Read more", color = cs.primary, fontWeight = FontWeight.SemiBold)
                        }
                    }
                }
            }

            // Available Downloads (if variants exist)
            if (liveVariants.isNotEmpty()) {
                item {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .widthIn(max = 880.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text("Available Downloads", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = cs.onSurface)
                        Spacer(Modifier.height(4.dp))
                        liveVariants.forEachIndexed { idx, v ->
                            val shape = segmentedShape(idx, liveVariants.size)
                            Surface(
                                onClick = {
                                    onDownloadAsset(
                                        baseApp,
                                        v.url,
                                        v.name.ifBlank { "${baseApp.name}_${v.arch}" }
                                    )
                                },
                                shape = shape,
                                color = cs.surfaceContainer,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                                ) {
                                    Box(
                                        Modifier.size(38.dp).background(cs.primaryContainer, OrionShapes.Cookie6Sided),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(Icons.Default.FileDownload, null, Modifier.size(18.dp), tint = cs.onPrimaryContainer)
                                    }
                                    Column(Modifier.weight(1f)) {
                                        Text(
                                            v.name.ifBlank { v.arch },
                                            style = MaterialTheme.typography.titleSmall,
                                            fontWeight = FontWeight.SemiBold,
                                            color = cs.onSurface,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                        Text(
                                            listOfNotNull(
                                                v.arch,
                                                v.size?.let(::formatBytes)
                                            ).joinToString(" · "),
                                            style = MaterialTheme.typography.labelMedium,
                                            color = cs.onSurfaceVariant
                                        )
                                    }
                                    Icon(Icons.AutoMirrored.Filled.ArrowForward, null, Modifier.size(18.dp), tint = cs.onSurfaceVariant)
                                }
                            }
                        }
                    }
                }
            }

            // Release Notes if resolved
            if (!releaseNotes.isNullOrBlank()) {
                item {
                    Surface(
                        shape = RoundedCornerShape(22.dp),
                        color = cs.surfaceContainer,
                        modifier = Modifier.fillMaxWidth().widthIn(max = 880.dp)
                    ) {
                        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text("What's New · v${liveVersion.removePrefix("v")}", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = cs.onSurface)
                            Text(
                                releaseNotes!!.take(1200),
                                style = MaterialTheme.typography.bodyMedium,
                                color = cs.onSurfaceVariant
                            )
                        }
                    }
                }
            }

            // Source & safety (VirusTotal + App info + Links)
            item {
                Column(
                    modifier = Modifier.fillMaxWidth().widthIn(max = 880.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text(
                        "Source & safety",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = cs.onSurface
                    )
                    Spacer(Modifier.height(2.dp))

                    // VirusTotal Card
                    Surface(
                        onClick = {
                            val targetUrl = primaryUrl ?: baseApp.repoUrl ?: baseApp.officialSite
                            val vtUrl = if (!targetUrl.isNullOrBlank()) {
                                "https://www.virustotal.com/gui/search/" + java.net.URLEncoder.encode(targetUrl, "UTF-8")
                            } else {
                                "https://www.virustotal.com/gui/home/url"
                            }
                            DesktopInstaller.openBrowser(vtUrl)
                        },
                        shape = RoundedCornerShape(20.dp),
                        color = cs.surfaceContainer,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(14.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(42.dp)
                                    .clip(CircleShape)
                                    .background(cs.surfaceContainerHighest),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    Icons.Default.Security,
                                    contentDescription = null,
                                    tint = cs.onSurface,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                            Column(Modifier.weight(1f)) {
                                Text(
                                    "Scan with VirusTotal",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.SemiBold,
                                    color = cs.onSurface
                                )
                                Text(
                                    "Check the download against 70+ antiviruses",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = cs.onSurfaceVariant,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                            Icon(
                                Icons.AutoMirrored.Filled.ArrowForward,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp),
                                tint = cs.onSurfaceVariant
                            )
                        }
                    }

                    // App info Card
                    var appInfoOpen by remember { mutableStateOf(false) }
                    val turn by animateFloatAsState(if (appInfoOpen) 180f else 0f, label = "appInfoTurn")
                    Surface(
                        onClick = { appInfoOpen = !appInfoOpen },
                        shape = RoundedCornerShape(20.dp),
                        color = cs.surfaceContainer,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(Modifier.padding(horizontal = 16.dp, vertical = 14.dp)) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(14.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(42.dp)
                                        .clip(CircleShape)
                                        .background(cs.surfaceContainerHighest),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        Icons.Default.Info,
                                        contentDescription = null,
                                        tint = cs.onSurface,
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                                Column(Modifier.weight(1f)) {
                                    Text(
                                        "App info",
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.SemiBold,
                                        color = cs.onSurface
                                    )
                                    Text(
                                        if (appInfoOpen) "Click to hide" else "Developer, size, package and source",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = cs.onSurfaceVariant,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                                Icon(
                                    Icons.Default.ExpandMore,
                                    contentDescription = if (appInfoOpen) "Hide" else "Show",
                                    modifier = Modifier.size(20.dp).graphicsLayer { rotationZ = turn },
                                    tint = cs.onSurfaceVariant
                                )
                            }

                            AnimatedVisibility(visible = appInfoOpen) {
                                Column(
                                    modifier = Modifier.padding(top = 16.dp),
                                    verticalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    HorizontalDivider(color = cs.outlineVariant.copy(alpha = 0.25f))
                                    val infoRows = listOf(
                                        "Developer" to baseApp.author,
                                        "Version" to liveVersion.ifBlank { "Latest" },
                                        "Size" to liveSize.ifBlank { "Varies" },
                                        "Platform" to baseApp.platform,
                                        "Category" to baseApp.category,
                                        "Source" to (baseApp.repoUrl ?: baseApp.officialSite ?: "Catalog")
                                    )
                                    infoRows.forEach { (k, v) ->
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween
                                        ) {
                                            Text(k, style = MaterialTheme.typography.bodyMedium, color = cs.onSurfaceVariant)
                                            Text(v, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold, color = cs.onSurface)
                                        }
                                    }
                                }
                            }
                        }
                    }

                    // External Links
                    val extRows = buildList<Triple<String, String, () -> Unit>> {
                        baseApp.repoUrl?.takeIf { it.isNotBlank() && it != "#" }?.let { repo ->
                            val hostLabel = when {
                                "gitlab" in repo -> "GitLab Repository"
                                "codeberg" in repo -> "Codeberg Repository"
                                else -> "GitHub Repository"
                            }
                            add(Triple(hostLabel, repo.removePrefix("https://"), { DesktopInstaller.openBrowser(repo) }))
                        }
                        baseApp.officialSite?.takeIf { it.isNotBlank() && it != "#" }?.let { site ->
                            add(Triple("Official Website", site.removePrefix("https://"), { DesktopInstaller.openBrowser(site) }))
                        }
                    }
                    if (extRows.isNotEmpty()) {
                        extRows.forEachIndexed { idx, (title, sub, onClick) ->
                            Surface(
                                onClick = onClick,
                                shape = segmentedShape(idx, extRows.size),
                                color = cs.surfaceContainer,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(14.dp)
                                ) {
                                    Icon(
                                        if ("Website" in title) Icons.Default.Language else Icons.Default.Code,
                                        contentDescription = null,
                                        tint = cs.primary
                                    )
                                    Column(Modifier.weight(1f)) {
                                        Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold, color = cs.onSurface)
                                        Text(sub, style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    }
                                    Icon(Icons.AutoMirrored.Filled.OpenInNew, null, Modifier.size(18.dp), tint = cs.onSurfaceVariant)
                                }
                            }
                        }
                    }
                }
            }
        }

        // Pinned Top Navigation Bar
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp, vertical = 14.dp)
                .align(Alignment.TopCenter),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                FilledTonalIconButton(
                    onClick = onBack,
                    shape = CircleShape,
                    colors = IconButtonDefaults.filledTonalIconButtonColors(
                        containerColor = cs.surfaceContainerHigh.copy(alpha = 0.85f),
                        contentColor = cs.onSurface
                    )
                ) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                }
                Text(
                    baseApp.category,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = cs.onSurfaceVariant
                )
            }

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                if (copiedNotice) {
                    Surface(shape = PillShape, color = cs.secondaryContainer) {
                        Text(
                            "Link copied",
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                            style = MaterialTheme.typography.labelMedium,
                            color = cs.onSecondaryContainer,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
                FilledTonalIconButton(
                    onClick = onToggleFavorite,
                    shape = CircleShape,
                    colors = IconButtonDefaults.filledTonalIconButtonColors(
                        containerColor = cs.surfaceContainerHigh.copy(alpha = 0.85f),
                        contentColor = if (isFavorite) cs.primary else cs.onSurface
                    )
                ) {
                    Icon(
                        if (isFavorite) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                        contentDescription = "Favorite",
                        tint = if (isFavorite) cs.primary else cs.onSurfaceVariant
                    )
                }
                FilledTonalIconButton(
                    onClick = {
                        val encId = java.net.URLEncoder.encode(baseApp.id, "UTF-8")
                        clipboard.setText(AnnotatedString("https://rookieenough.github.io/Orion-Data/app/?id=$encId"))
                        copiedNotice = true
                    },
                    shape = CircleShape,
                    colors = IconButtonDefaults.filledTonalIconButtonColors(
                        containerColor = cs.surfaceContainerHigh.copy(alpha = 0.85f),
                        contentColor = cs.onSurface
                    )
                ) {
                    Icon(Icons.Default.Share, contentDescription = "Share link")
                }
                baseApp.repoUrl?.takeIf { it.isNotBlank() && it != "#" }?.let { repo ->
                    FilledTonalIconButton(
                        onClick = { DesktopInstaller.openBrowser(repo) },
                        shape = CircleShape,
                        colors = IconButtonDefaults.filledTonalIconButtonColors(
                            containerColor = cs.surfaceContainerHigh.copy(alpha = 0.85f),
                            contentColor = cs.onSurface
                        )
                    ) {
                        Icon(Icons.AutoMirrored.Filled.OpenInNew, contentDescription = "Open Repo")
                    }
                }
            }
        }

        // FLOATING ACTION PILL DOCK PINNED AT THE BOTTOM (Exact parity with Android Detail.kt:207-277)
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.BottomCenter)
                .padding(bottom = 20.dp),
            contentAlignment = Alignment.Center
        ) {
            Surface(
                modifier = Modifier
                    .widthIn(max = 520.dp)
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp),
                shape = RoundedCornerShape(36.dp),
                color = cs.surfaceContainerHigh.copy(alpha = 0.95f),
                border = BorderStroke(1.dp, Color.White.copy(alpha = 0.08f)),
                shadowElevation = 12.dp
            ) {
                Row(
                    modifier = Modifier.padding(10.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Button(
                        onClick = {
                            when {
                                completed -> {
                                    val file = downloadState.localFile
                                    val target = downloadState.installDir ?: file
                                    if (portable && file != null) onLaunchFile(file)
                                    else onRevealFile(target ?: DesktopCatalogRepository.downloadsDir)
                                }
                                busy -> Unit
                                liveVariants.size > 1 -> variantSheetOpen = true
                                primaryUrl != null -> onDownloadAsset(baseApp, primaryUrl, primaryFilename)
                                else -> onDownloadAsset(baseApp, null, null)
                            }
                        },
                        enabled = !resolvingRelease && !busy,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color.White,
                            contentColor = Color.Black,
                            disabledContainerColor = Color.White.copy(alpha = 0.38f),
                            disabledContentColor = Color.Black.copy(alpha = 0.38f)
                        ),
                        shape = RoundedCornerShape(28.dp),
                        modifier = Modifier.weight(1f).height(54.dp)
                    ) {
                        when {
                            resolvingRelease -> {
                                CircularProgressIndicator(Modifier.size(20.dp), color = Color.Black, strokeWidth = 2.dp)
                                Spacer(Modifier.width(10.dp))
                                Text("Checking release...", fontWeight = FontWeight.Bold, color = Color.Black)
                            }
                            completed -> {
                                Icon(if (portable) Icons.Default.PlayArrow else Icons.Default.FolderOpen, null, tint = Color.Black)
                                Spacer(Modifier.width(10.dp))
                                Text(
                                    if (portable) "Run" else "Show in folder",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = Color.Black
                                )
                            }
                            busy -> {
                                CircularProgressIndicator(Modifier.size(20.dp), color = Color.Black, strokeWidth = 2.dp)
                                Spacer(Modifier.width(10.dp))
                                Text(
                                    downloadState.progress.takeIf { it in 0f..1f }
                                        ?.let { "${(it * 100).toInt()}%" } ?: "Downloading…",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = Color.Black
                                )
                            }
                            else -> {
                                Icon(
                                    if (baseApp.platform == Platform.PC) Icons.Default.DesktopWindows else Icons.Default.Download,
                                    null,
                                    tint = Color.Black,
                                    modifier = Modifier.size(20.dp)
                                )
                                Spacer(Modifier.width(10.dp))
                                Text(
                                    when {
                                        primaryUrl != null && baseApp.platform == Platform.PC -> "Get on PC"
                                        primaryUrl != null -> "Download"
                                        else -> "View on GitHub"
                                    },
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = Color.Black
                                )
                            }
                        }
                    }

                    if (liveVariants.size > 1 && !completed && !busy) {
                        FilledTonalIconButton(
                            onClick = { variantSheetOpen = true },
                            shape = CircleShape,
                            colors = IconButtonDefaults.filledTonalIconButtonColors(
                                containerColor = Color.White.copy(alpha = 0.12f),
                                contentColor = Color.White
                            ),
                            modifier = Modifier.size(54.dp)
                        ) {
                            Icon(Icons.AutoMirrored.Filled.List, "Choose Variant (${liveVariants.size})")
                        }
                    }
                }
            }
        }
    }

    // Lightbox modal for screenshot zoom
    lightboxUrl?.let { url ->
        Dialog(onDismissRequest = { lightboxUrl = null }) {
            Surface(
                shape = RoundedCornerShape(24.dp),
                color = cs.surfaceContainerHigh,
                modifier = Modifier.widthIn(max = 980.dp).heightIn(max = 720.dp)
            ) {
                Box(Modifier.padding(12.dp)) {
                    AsyncImage(
                        model = url,
                        contentDescription = "Zoomed Screenshot",
                        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)),
                        contentScale = ContentScale.Fit
                    )
                    FilledTonalIconButton(
                        onClick = { lightboxUrl = null },
                        modifier = Modifier.align(Alignment.TopEnd).padding(8.dp),
                        shape = CircleShape
                    ) {
                        Icon(Icons.Default.Close, "Close")
                    }
                }
            }
        }
    }

    // Variant chooser dialog when multiple assets exist
    if (variantSheetOpen) {
        Dialog(onDismissRequest = { variantSheetOpen = false }) {
            Surface(
                shape = RoundedCornerShape(28.dp),
                color = cs.surfaceContainerLow,
                modifier = Modifier.width(500.dp)
            ) {
                Column(Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("Choose Build", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                    Text("Select which release asset to download for ${baseApp.name}", style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant)
                    Spacer(Modifier.height(4.dp))
                    liveVariants.forEachIndexed { idx, v ->
                        Surface(
                            onClick = {
                                variantSheetOpen = false
                                onDownloadAsset(baseApp, v.url, v.name.ifBlank { "${baseApp.name}_${v.arch}" })
                            },
                            shape = segmentedShape(idx, liveVariants.size),
                            color = cs.surfaceContainer,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                Modifier.padding(14.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Column(Modifier.weight(1f)) {
                                    Text(v.name.ifBlank { v.arch }, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    Text(listOfNotNull(v.arch, v.size?.let(::formatBytes)).joinToString(" · "), style = MaterialTheme.typography.labelSmall, color = cs.onSurfaceVariant)
                                }
                                Icon(Icons.Default.FileDownload, null, tint = cs.primary)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun BundlePreviewDialog(
    bundle: BundleItem,
    onDismiss: () -> Unit,
    onSelectApp: (AppItem) -> Unit
) {
    val cs = MaterialTheme.colorScheme
    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(28.dp),
            color = cs.surfaceContainerLow,
            modifier = Modifier.width(560.dp).heightIn(max = 680.dp)
        ) {
            Column(Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(bundle.title, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                        Text(bundle.description, style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant)
                    }
                    IconButton(onClick = onDismiss) { Icon(Icons.Default.Close, "Close") }
                }
                HorizontalDivider(color = cs.outlineVariant.copy(alpha = 0.4f))
                LazyColumn(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    itemsIndexed(bundle.apps) { idx, app ->
                        Surface(
                            onClick = {
                                onDismiss()
                                onSelectApp(app)
                            },
                            shape = segmentedShape(idx, bundle.apps.size),
                            color = cs.surfaceContainer,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                DesktopAppIcon(app.icon, app.name, 44.dp)
                                Column(Modifier.weight(1f)) {
                                    Text(app.name, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    Text("${app.category} · ${app.author}", style = MaterialTheme.typography.labelMedium, color = cs.onSurfaceVariant)
                                }
                                Icon(Icons.AutoMirrored.Filled.ArrowForward, null, tint = cs.primary)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun StatCell(label: String, value: @Composable () -> Unit, modifier: Modifier = Modifier) {
    Column(modifier.padding(horizontal = 4.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Box(Modifier.height(28.dp), contentAlignment = Alignment.Center) { value() }
        Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

fun segmentedShape(index: Int, count: Int): RoundedCornerShape = when {
    count <= 1 -> RoundedCornerShape(20.dp)
    index == 0 -> RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp, bottomStart = 6.dp, bottomEnd = 6.dp)
    index == count - 1 -> RoundedCornerShape(topStart = 6.dp, topEnd = 6.dp, bottomStart = 20.dp, bottomEnd = 20.dp)
    else -> RoundedCornerShape(6.dp)
}

fun formatBytes(b: Long): String = when {
    b < 1024 -> "$b B"
    b < 1024 * 1024 -> "${b / 1024} KB"
    b < 1024L * 1024 * 1024 -> String.format(java.util.Locale.US, "%.1f MB", b / (1024.0 * 1024.0))
    else -> String.format(java.util.Locale.US, "%.2f GB", b / (1024.0 * 1024.0 * 1024.0))
}

fun formatSpeed(bytesPerSecond: Long): String = when {
    bytesPerSecond <= 0 -> ""
    bytesPerSecond < 1024 -> "$bytesPerSecond B/s"
    bytesPerSecond < 1024 * 1024 -> "${bytesPerSecond / 1024} KB/s"
    else -> String.format(java.util.Locale.US, "%.1f MB/s", bytesPerSecond / (1024.0 * 1024.0))
}
