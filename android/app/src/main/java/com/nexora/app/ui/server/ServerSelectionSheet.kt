package com.nexora.app.ui.server

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nexora.app.data.api.AppCard
import com.nexora.app.ui.theme.NexoraColors

/**
 * Server selection: user-facing names are STRICTLY numbered (Server 1..N).
 * Independent websites are NOT treated as mirrors - no auto-fallback across servers.
 * Auto mode opens the admin-configured default (or first by priority).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ServerSelectionSheet(card: AppCard, onOpen: (String) -> Unit, onDismiss: () -> Unit) {
    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = androidx.compose.ui.graphics.Color(0xFF120A1F)) {
        Column(Modifier.padding(20.dp, 0.dp, 20.dp, 34.dp)) {
            Text(card.name, fontSize = 18.sp, fontWeight = FontWeight.ExtraBold, color = NexoraColors.TextPrimary)
            Text("Choose a server — each server is a separate website.",
                 fontSize = 12.sp, color = NexoraColors.TextSub, modifier = Modifier.padding(top = 4.dp, bottom = 14.dp))
            val available = card.servers
            LazyColumn {
                items(available) { s ->
                    val idx = available.indexOf(s) + 1
                    Surface(
                        onClick = { onOpen(s.url) },
                        shape = RoundedCornerShape(14.dp),
                        color = NexoraColors.SurfaceGlass,
                        border = androidx.compose.foundation.BorderStroke(1.dp, NexoraColors.GlassBorder),
                        modifier = Modifier.fillMaxWidth().padding(vertical = 5.dp)
                    ) {
                        Row(Modifier.padding(14.dp), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text("Server $idx", fontWeight = FontWeight.Bold, fontSize = 14.sp, color = NexoraColors.TextPrimary)
                                Text(s.url, fontSize = 10.sp, color = NexoraColors.TextSub, maxLines = 1)
                            }
                            if (s.is_default) Text("DEFAULT", fontSize = 9.sp, color = NexoraColors.Neon, fontWeight = FontWeight.Bold)
                            if (s.maintenance) Text("MAINT.", fontSize = 9.sp, color = NexoraColors.Warn, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
    }
}
