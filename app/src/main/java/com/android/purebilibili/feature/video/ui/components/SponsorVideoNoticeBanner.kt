package com.android.purebilibili.feature.video.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.android.purebilibili.core.ui.AppShapes
import com.android.purebilibili.core.ui.ContainerLevel
import com.android.purebilibili.core.ui.components.AppSurface
import com.android.purebilibili.core.ui.components.AppText
import com.android.purebilibili.feature.plugin.SponsorVideoNotice

/** Informational only: full-video labels never trigger a seek or block playback. */
@Composable
fun SponsorVideoNoticeBanner(notice: SponsorVideoNotice, modifier: Modifier = Modifier) {
    AppSurface(
        modifier = modifier.fillMaxWidth().semantics(mergeDescendants = true) {},
        shape = AppShapes.container(ContainerLevel.Card),
        color = MaterialTheme.colorScheme.secondaryContainer,
    ) {
        Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
            AppText(
                text = notice.title,
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSecondaryContainer,
            )
            AppText(
                text = "来自小电视空降助手社区，标注仅供参考",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSecondaryContainer,
            )
        }
    }
}
