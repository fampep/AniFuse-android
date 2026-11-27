// Copyright 2026 fampep
// Licensed under the Apache License, Version 2.0. See the LICENSE file.

package site.anikitty.app.ui.app

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import site.anikitty.app.data.anifuse.AnifuseApi
import site.anikitty.app.data.anifuse.AnifuseApiStore
import site.anikitty.app.data.anifuse.AnifuseHealth
import site.anikitty.app.data.anifuse.AnifuseProvider
import site.anikitty.app.data.friendlyFailure
import site.anikitty.app.ui.components.StageGlassDisc
import site.anikitty.app.ui.components.StageProgressBar
import site.anikitty.app.ui.components.stageGlassPlate
import site.anikitty.app.ui.theme.KittyAccent
import site.anikitty.app.ui.theme.KittyFaint
import site.anikitty.app.ui.theme.KittyMuted
import site.anikitty.app.ui.theme.LocalKittyPalette
import site.anikitty.app.ui.theme.LookState
import site.anikitty.app.ui.theme.ShimmerBox
import site.anikitty.app.ui.theme.kittyPageBrush

/**
 * AniFuse resolver health board.
 *
 * The hosted API asks every bundled site in parallel and remembers which ones
 * are healthy. This page surfaces that live data — status, success rate,
 * attempts, cooldown — plus the endpoint the app is talking to.
 */
@Composable
fun AnifuseProvidersPage(
    api: AnifuseApi,
    store: AnifuseApiStore,
    onBack: () -> Unit,
) {
    BackHandler(onBack = onBack)
    val palette = LocalKittyPalette.current
    val scope = rememberCoroutineScope()
    var loading by remember { mutableStateOf(true) }
    var refreshing by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var health by remember { mutableStateOf<AnifuseHealth?>(null) }
    var providers by remember { mutableStateOf<List<AnifuseProvider>>(emptyList()) }
    var refreshedAt by remember { mutableStateOf<Long?>(null) }
    val endpoint by store.baseUrl.collectAsState()

    fun reload(force: Boolean = false) {
        if (force) refreshing = true else loading = true
        error = null
        scope.launch {
            runCatching {
                val h = api.health()
                val p = api.providers(forceRefresh = force)
                health to p
            }.onSuccess { (h, p) ->
                health = h
                providers = p
                refreshedAt = System.currentTimeMillis()
            }.onFailure { e ->
                error = friendlyFailure(e.message)
            }
            loading = false
            refreshing = false
        }
    }

    LaunchedEffect(endpoint) {
        api.invalidate()
        reload()
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(kittyPageBrush(LookState.look != "amoled"))
            .windowInsetsPadding(WindowInsets.statusBars),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 32.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                StageGlassDisc(onClick = onBack, size = 40.dp) {
                    Icon(
                        Icons.AutoMirrored.Rounded.ArrowBack,
                        contentDescription = "Back",
                        tint = palette.text,
                        modifier = Modifier.size(20.dp),
                    )
                }
                Column(modifier = Modifier.weight(1f).padding(horizontal = 12.dp)) {
                    Text("AniFuse API", color = palette.text, fontWeight = FontWeight.Bold, fontSize = 22.sp)
                    Text(
                        endpoint,
                        color = palette.muted,
                        fontSize = 12.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                if (refreshing) {
                    CircularProgressIndicator(
                        color = KittyAccent,
                        strokeWidth = 2.dp,
                        modifier = Modifier.size(22.dp).padding(end = 6.dp),
                    )
                } else {
                    StageGlassDisc(onClick = { reload(force = true) }, size = 40.dp) {
                        Icon(
                            Icons.Rounded.Refresh,
                            contentDescription = "Refresh providers",
                            tint = palette.text,
                            modifier = Modifier.size(20.dp),
                        )
                    }
                }
            }
        }
        item {
            Text(
                "The resolver API fronts every anime site in parallel and ranks the streams it finds. " +
                    "Provider health decides which sites join each race.",
                color = palette.muted,
                fontSize = 13.sp,
                lineHeight = 19.sp,
            )
        }
        when {
            loading -> {
                item { ShimmerBox(modifier = Modifier.fillMaxWidth().height(84.dp)) }
                items(4) { ShimmerBox(modifier = Modifier.fillMaxWidth().height(96.dp)) }
            }
            error != null && providers.isEmpty() -> item {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(error ?: "Couldn't reach the API", color = palette.text, fontSize = 14.sp)
                    OutlinedButton(onClick = { reload(force = true) }) {
                        Text("Try again", color = palette.text)
                    }
                }
            }
            else -> {
                item { HealthCard(health = health, providerCount = providers.size, demo = store.isCustom) }
                if (refreshedAt != null) {
                    item {
                        Text(
                            "Live data · refreshed ${java.text.DateFormat.getTimeInstance(java.text.DateFormat.SHORT).format(java.util.Date(refreshedAt!!))}",
                            color = KittyFaint,
                            fontSize = 11.sp,
                        )
                    }
                }
                item {
                    Text(
                        "Providers · ${providers.size}",
                        color = palette.text,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 15.sp,
                        modifier = Modifier.padding(top = 6.dp),
                    )
                }
                items(providers, key = { it.id }, contentType = { "provider" }) { provider ->
                    ProviderRow(provider)
                }
            }
        }
    }
}

@Composable
private fun HealthCard(health: AnifuseHealth?, providerCount: Int, demo: Boolean) {
    val palette = LocalKittyPalette.current
    val ok = health?.ok == true
    val statusLabel = when {
        health == null -> "…"
        ok -> "Online"
        else -> "Unhealthy"
    }
    val statusColor = when {
        health == null -> palette.muted
        ok -> KittyAccent
        else -> Color(0xFFE53935)
    }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .stageGlassPlate(shape = RoundedCornerShape(18.dp), elevated = true)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(10.dp)
                    .clip(CircleShape)
                    .background(statusColor),
            )
            Spacer(modifier = Modifier.size(8.dp))
            Text(
                statusLabel,
                color = palette.text,
                fontWeight = FontWeight.SemiBold,
                fontSize = 15.sp,
                modifier = Modifier.weight(1f),
            )
            if (health?.demo == true) {
                Text(
                    "Demo deployment",
                    color = palette.muted,
                    fontSize = 11.sp,
                    modifier = Modifier
                        .stageGlassPlate(shape = RoundedCornerShape(999.dp))
                        .padding(horizontal = 8.dp, vertical = 3.dp),
                )
            }
        }
        Row {
            Text(
                "${providerCount} providers · up ${uptimeLabel(health?.uptimeSeconds ?: 0L)}",
                color = palette.muted,
                fontSize = 12.sp,
            )
        }
        if (demo) {
            Text(
                "Custom endpoint — streams resolve from this server, not the hosted demo.",
                color = KittyFaint,
                fontSize = 11.sp,
            )
        }
    }
}

@Composable
private fun ProviderRow(provider: AnifuseProvider) {
    val palette = LocalKittyPalette.current
    val statusColor = when {
        provider.down -> Color(0xFFE53935)
        provider.degraded -> palette.muted
        else -> KittyAccent
    }
    val statusLabel = provider.status.uppercase().ifBlank { "OK" }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .stageGlassPlate(shape = RoundedCornerShape(16.dp))
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(8.dp)
                    .clip(CircleShape)
                    .background(statusColor),
            )
            Spacer(modifier = Modifier.size(8.dp))
            Text(
                provider.name.ifBlank { provider.id },
                color = palette.text,
                fontWeight = FontWeight.Medium,
                fontSize = 14.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            Text(
                statusLabel,
                color = statusColor,
                fontSize = 11.sp,
                fontWeight = FontWeight.SemiBold,
            )
        }
        val rate = provider.ratePercent
        StageProgressBar(
            fraction = (rate / 100f).coerceIn(0f, 1f),
            modifier = Modifier.fillMaxWidth(),
            barHeight = 4.dp,
        )
        Row {
            Text(
                "${rate}% success · ${provider.attempts} attempts",
                color = palette.muted,
                fontSize = 11.sp,
                modifier = Modifier.weight(1f),
            )
            if (provider.cooldownSeconds > 0) {
                Text(
                    "cooldown ${provider.cooldownSeconds}s",
                    color = palette.muted,
                    fontSize = 11.sp,
                )
            }
        }
    }
}

private fun uptimeLabel(seconds: Long): String {
    val days = seconds / 86_400L
    val hours = (seconds % 86_400L) / 3_600L
    val minutes = (seconds % 3_600L) / 60L
    return when {
        days > 0 -> "${days}d ${hours}h"
        hours > 0 -> "${hours}h ${minutes}m"
        else -> "${minutes}m"
    }
}
