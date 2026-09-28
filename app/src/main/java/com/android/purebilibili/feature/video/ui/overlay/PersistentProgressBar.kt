package com.android.purebilibili.feature.video.ui.overlay

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.remember
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import com.android.purebilibili.data.model.response.SponsorProgressMarker
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.graphics.RectangleShape

internal fun resolvePersistentProgressFraction(current: Long, duration: Long): Float {
    return resolveProgressFraction(
        positionMs = current,
        durationMs = duration
    )
}

@Composable
internal fun PersistentBottomProgressBar(
    current: Long,
    duration: Long,
    modifier: Modifier = Modifier,
    sponsorMarkers: List<SponsorProgressMarker> = emptyList(),
) {
    val progress = resolvePersistentProgressFraction(current = current, duration = duration)
    if (duration <= 0L) return
    val markers = remember(duration, sponsorMarkers) { resolveSponsorProgressBarMarkers(duration, sponsorMarkers) }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(2.dp)
            .background(
                color = Color.White.copy(alpha = 0.3f),
                shape = RectangleShape
            )
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth(progress)
                .height(2.dp)
                .background(MaterialTheme.colorScheme.primary)
        )
        Canvas(Modifier.fillMaxSize()) {
            markers.forEach { marker ->
                drawRect(marker.color,
                    topLeft = Offset(size.width * marker.startFraction, 0f),
                    size = Size(size.width * (marker.endFraction - marker.startFraction), size.height))
            }
        }
    }
}
