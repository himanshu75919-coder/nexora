package com.nexora.app.ui.dashboard

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.nexora.app.data.api.AppCard
import com.nexora.app.ui.theme.NexoraColors

@Composable
fun DashboardScreen(
    onOpenCard: (AppCard) -> Unit,
    vm: DashboardViewModel = hiltViewModel()
) {
    val ui by vm.ui.collectAsState()
    var showSearch by remember { mutableStateOf(false) }

    Column(Modifier.fillMaxSize().background(NexoraColors.Background).verticalScroll(rememberScrollState())) {
        // Welcome header (admin-controlled strings)
        Column(Modifier.padding(20.dp, 26.dp, 20.dp, 6.dp)) {
            Text(ui.data.settings["welcome_title"] ?: "Welcome to NEXORA", fontSize = 22.sp, fontWeight = FontWeight.ExtraBold)
            Text(ui.data.settings["welcome_sub"] ?: "", fontSize = 11.sp, color = NexoraColors.TextSub, letterSpacing = 1.4.sp)
        }

        // Universal search
        OutlinedTextField(
            value = ui.query, onValueChange = vm::search,
            placeholder = { Text("Search apps…", color = NexoraColors.TextSub) },
            singleLine = true, shape = RoundedCornerShape(14.dp),
            modifier = Modifier.padding(20.dp, 8.dp).fillMaxWidth(),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = NexoraColors.Violet,
                unfocusedBorderColor = NexoraColors.GlassBorder,
                focusedContainerColor = Color(0x14140A23),
                unfocusedContainerColor = Color(0x14140A23)
            )
        )

        if (ui.loading) {
            // Skeleton loading (no long splash by design)
            repeat(4) { Box(Modifier.padding(20.dp, 6.dp).fillMaxWidth().height(78.dp)
                .background(NexoraColors.SurfaceGlass, RoundedCornerShape(16.dp))) }
        } else if (ui.offline && ui.data.cards.isEmpty()) {
            EmptyState("You are offline and no saved configuration exists yet.")
        } else {
            if (ui.query.isBlank()) {
                Section("FEATURED") { RowBlock(vm.featured(), vm, onOpenCard) }
                if (ui.settings["sections_trending"] == "1") Section("TRENDING") { RowBlock(vm.byLabel("TRENDING"), vm, onOpenCard) }
                if (ui.settings["sections_recent"] == "1" && vm.recents().isNotEmpty()) Section("RECENTLY OPENED") { RowBlock(vm.recents(), vm, onOpenCard) }
                if (ui.settings["sections_popular"] == "1") Section("ALL APPS") { GridBlock(vm.cards(), vm, onOpenCard) }
            } else {
                val res = vm.cards()
                if (res.isEmpty()) EmptyState("No apps match \"${ui.query}\".")
                else GridBlock(res, vm, onOpenCard)
            }
            // Announcements (admin pushes)
            ui.data.announcements.firstOrNull()?.let { a ->
                Card(Modifier.padding(20.dp, 12.dp).fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = NexoraColors.SurfaceGlass)) {
                    Column(Modifier.padding(14.dp)) {
                        Text(a.title, fontWeight = FontWeight.Bold, fontSize = 13.sp, color = NexoraColors.Neon)
                        Text(a.body, fontSize = 12.sp, color = NexoraColors.TextSub)
                    }
                }
            }
        }
        Spacer(Modifier.height(28.dp))
    }
}

@Composable
private fun Section(title: String, content: @Composable () -> Unit) {
    Text(title, Modifier.padding(20.dp, 18.dp, 0.dp, 6.dp), fontSize = 11.sp,
        color = NexoraColors.Neon, letterSpacing = 3.sp, fontWeight = FontWeight.Bold)
    content()
}

@Composable
private fun RowBlock(cards: List<AppCard>, vm: DashboardViewModel, open: (AppCard) -> Unit) {
    if (cards.isEmpty()) return
    Column { cards.take(4).forEach { CardTile(it, vm, open) } }
}

@Composable
private fun GridBlock(cards: List<AppCard>, vm: DashboardViewModel, open: (AppCard) -> Unit) {
    LazyVerticalGrid(columns = GridCells.Fixed(2),
        modifier = Modifier.height(((cards.size / 2 + 1) * 120).dp).padding(horizontal = 14.dp),
        userScrollEnabled = false) {
        items(cards) { CardTile(it, vm, open) }
    }
}

@Composable
private fun CardTile(c: AppCard, vm: DashboardViewModel, open: (AppCard) -> Unit) {
    Surface(
        onClick = { vm.markRecent(c); open(c) },
        shape = RoundedCornerShape(16.dp),
        color = NexoraColors.SurfaceGlass,
        border = androidx.compose.foundation.BorderStroke(1.dp, NexoraColors.GlassBorder),
        modifier = Modifier.padding(6.dp).fillMaxWidth()
    ) {
        Column(Modifier.padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(c.name, fontWeight = FontWeight.Bold, fontSize = 14.sp, modifier = Modifier.weight(1f), maxLines = 1)
                if (vm.ui.value.favorites.contains(c.name)) Text("★", color = NexoraColors.Warn)
            }
            if (c.description.isNotBlank()) Text(c.description, fontSize = 11.sp, color = NexoraColors.TextSub, maxLines = 2)
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                if (c.label.isNotBlank()) LabelPill(c.label)
                Text("Servers: ${c.servers.size}", fontSize = 10.sp, color = NexoraColors.TextSub)
            }
        }
    }
}

@Composable
fun LabelPill(label: String) {
    val color = when (label) {
        "HOT" -> NexoraColors.Err
        "NEW" -> NexoraColors.Ok
        "TRENDING" -> NexoraColors.Warn
        else -> NexoraColors.Neon
    }
    Box(Modifier.background(color.copy(alpha = .16f), RoundedCornerShape(99.dp))
        .border(1.dp, color.copy(alpha = .5f), RoundedCornerShape(99.dp)).padding(horizontal = 8.dp, vertical = 2.dp)) {
        Text(label, fontSize = 9.sp, color = color, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
    }
}

@Composable
private fun EmptyState(msg: String) {
    Column(Modifier.padding(40.dp).fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
        Text("⚡", fontSize = 34.sp)
        Text(msg, fontSize = 13.sp, color = NexoraColors.TextSub, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
        Spacer(Modifier.height(10.dp))
    }
}
