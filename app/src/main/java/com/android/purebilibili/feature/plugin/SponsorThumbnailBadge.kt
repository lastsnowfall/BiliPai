package com.android.purebilibili.feature.plugin

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.zIndex
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.android.purebilibili.core.plugin.PluginManager
import com.android.purebilibili.core.ui.components.AppText
import com.android.purebilibili.data.repository.SponsorThumbnailRepository

@Composable
fun SponsorThumbnailBadge(bvid: String, modifier: Modifier = Modifier) {
    val plugins by PluginManager.pluginsFlow.collectAsStateWithLifecycle()
    val plugin = plugins.firstOrNull { it.enabled && it.plugin is SponsorBlockPlugin }?.plugin as? SponsorBlockPlugin
        ?: return
    val baseUrl = plugin.getCommunityServerBaseUrl()
    var label by remember(bvid, baseUrl) { mutableStateOf<String?>(null) }
    LaunchedEffect(bvid, baseUrl) { label = SponsorThumbnailRepository.label(bvid, baseUrl) }
    label?.let { text ->
        AppText(
            text = text,
            color = Color(0xFF073B0A),
            fontSize = 10.sp,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            modifier = modifier
                .zIndex(1f)
                .semantics { contentDescription = "社区标记：$text" }
                .background(Color(0xFF00D400), RoundedCornerShape(4.dp))
                .padding(horizontal = 5.dp, vertical = 3.dp)
        )
    }
}
