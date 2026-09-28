package com.android.purebilibili.feature.priority

import android.Manifest
import android.content.Intent
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.android.purebilibili.core.ui.AppAlertDialog
import com.android.purebilibili.core.ui.components.AppButton
import com.android.purebilibili.core.ui.components.AppText
import com.android.purebilibili.core.ui.components.AppTextButton
import com.android.purebilibili.data.model.response.VideoItem
import com.android.purebilibili.data.repository.BlockedUpRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

@Composable
fun PriorityCreatorDialog(mid: Long, name: String, face: String, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val state by remember(context) { PriorityCreatorStore.observe(context) }
        .collectAsStateWithLifecycle(initialValue = PriorityCreatorState())
    val selected = state.creators.firstOrNull { it.mid == mid }
    var busy by remember { mutableStateOf(false) }
    var status by remember { mutableStateOf<String?>(null) }
    var canNotify by remember { mutableStateOf(PriorityCreatorNotifications.canPost(context)) }
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        canNotify = granted && PriorityCreatorNotifications.canPost(context)
        if (canNotify) scope.launch { PriorityCreatorNotifications.postPending(context) }
    }
    AppAlertDialog(
        onDismissRequest = onDismiss,
        title = { AppText("重点关注 · $name") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                AppText("每 20 个视频的第 4 格留给喜欢的 UP，使用特殊底色和 ✓ 标记，其余保留普通推荐。")
                AppText("播放进度达到 90% 的视频会在本机记住，以后不再出现在首页推荐。", style = MaterialTheme.typography.bodySmall)
                AppText("没有可用视频时该格使用普通推荐；不会更改 B 站的关注关系。", style = MaterialTheme.typography.bodySmall)
                if (selected != null) {
                    AppTextButton(onClick = {
                        scope.launch { PriorityCreatorStore.setNotify(context, mid, !selected.notifyNewVideos) }
                    }) { AppText(if (selected.notifyNewVideos) "✓ 新视频系统提醒：开启" else "新视频系统提醒：关闭") }
                }
                if (!canNotify) {
                    AppTextButton(onClick = {
                        if (Build.VERSION.SDK_INT >= 33) permission.launch(Manifest.permission.POST_NOTIFICATIONS)
                        else context.startActivity(Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                            .putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName))
                    }) { AppText("允许系统通知") }
                }
                AppTextButton(onClick = {
                    context.startActivity(Intent(Settings.ACTION_CHANNEL_NOTIFICATION_SETTINGS)
                        .putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
                        .putExtra(Settings.EXTRA_CHANNEL_ID, PriorityCreatorNotifications.CHANNEL_ID))
                }) { AppText("通知声音与横幅设置") }
                AppText("后台约每 15 分钟检查，系统省电可能延后。首次添加不会提醒历史投稿。", style = MaterialTheme.typography.bodySmall)
                status?.let { AppText(it) }
            }
        },
        confirmButton = {
            AppButton(enabled = !busy, onClick = {
                busy = true
                scope.launch {
                    try {
                        if (selected == null) {
                            PriorityCreatorStore.select(context, mid, name, face)
                            PriorityCreatorWorker.syncSchedule(context)
                            val loaded = PriorityCreatorRepository.refresh(context)
                            status = if (loaded) "已添加，刷新首页后生效" else "已添加；视频暂未加载成功，将自动重试"
                        } else {
                            PriorityCreatorStore.remove(context, mid)
                            PriorityCreatorWorker.syncSchedule(context)
                            status = "已取消重点关注"
                        }
                    } catch (e: CancellationException) {
                        throw e
                    } catch (_: Exception) {
                        status = "保存失败，请重试"
                    } finally {
                        busy = false
                    }
                }
            }) { AppText(if (busy) "处理中…" else if (selected == null) "加入重点关注" else "取消重点关注") }
        },
        dismissButton = { AppTextButton(onClick = onDismiss) { AppText("完成") } },
    )
}

/** Container owns persistence; navigation remains with the home screen. */
@Composable
fun PriorityCreatorHomeCard(onVideoClick: (VideoItem) -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val state by remember(context) { PriorityCreatorStore.observe(context) }
        .collectAsStateWithLifecycle(initialValue = PriorityCreatorState())
    val blocked by remember(context) { BlockedUpRepository(context).getAllBlockedUps() }
        .collectAsStateWithLifecycle(initialValue = emptyList())
    val blockedMids = remember(blocked) { blocked.map { it.mid }.toSet() }
    var manage by remember { mutableStateOf(false) }
    var expanded by remember { mutableStateOf(false) }
    var editing by remember { mutableStateOf<PriorityCreator?>(null) }
    var refreshStatus by remember { mutableStateOf<String?>(null) }
    var refreshing by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        PriorityCreatorWorker.syncSchedule(context)
        PriorityCreatorRepository.refresh(context)
        PriorityCreatorNotifications.postPending(context)
    }
    if (state.creators.isEmpty()) return
    val unread = remember(state.unseenVideos, blockedMids) {
        state.unseenVideos.filter { it.owner.mid !in blockedMids }
    }
    Column(Modifier.fillMaxWidth().padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            AppText("★ 重点关注${if (unread.isNotEmpty()) " · ${unread.size} 个新视频" else ""}",
                modifier = Modifier.weight(1f).padding(vertical = 12.dp), style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.primary)
            AppTextButton(onClick = { manage = true }) { AppText("管理") }
        }
        AppText("${state.creators.size} 位 UP · 每 20 个视频的第 4 格", style = MaterialTheme.typography.bodySmall)
        if (unread.isEmpty()) AppText("暂无新投稿", style = MaterialTheme.typography.bodySmall)
        unread.take(if (expanded) 100 else 3).forEach { video ->
            AppText("${video.owner.name}：${video.title}",
                modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).clickable {
                    scope.launch { PriorityCreatorStore.markRead(context, video.bvid) }
                    onVideoClick(video)
                }.padding(vertical = 12.dp))
        }
        if (unread.size > 3) AppTextButton(onClick = { expanded = !expanded }) {
            AppText(if (expanded) "收起" else "查看全部 ${unread.size} 个更新")
        }
        AppTextButton(enabled = !refreshing, onClick = {
            refreshing = true
            scope.launch {
                try {
                    val loaded = PriorityCreatorRepository.refresh(context)
                    PriorityCreatorNotifications.postPending(context)
                    refreshStatus = if (loaded) "已检查更新（5 分钟内使用缓存）" else "暂未获取更新，请稍后重试"
                } finally { refreshing = false }
            }
        }) { AppText(if (refreshing) "检查中…" else "检查更新") }
        refreshStatus?.let { AppText(it, style = MaterialTheme.typography.bodySmall) }
    }
    if (manage) AppAlertDialog(
        onDismissRequest = { manage = false },
        title = { AppText("管理重点关注") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                AppText("前往任意 UP 主页，在右上角菜单中添加。点击下方 UP 可调整设置或取消。")
                state.creators.forEach { creator ->
                    AppTextButton(onClick = { editing = creator; manage = false }) {
                        AppText("${creator.name}${if (creator.mid in blockedMids) "（已屏蔽）" else ""}")
                    }
                }
            }
        },
        confirmButton = { AppTextButton(onClick = { manage = false }) { AppText("完成") } },
    )
    editing?.let { creator ->
        PriorityCreatorDialog(creator.mid, creator.name, creator.face, onDismiss = { editing = null })
    }
}
