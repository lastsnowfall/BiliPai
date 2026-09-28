package com.android.purebilibili.core.plugin

/**
 * 内置插件首次安装时的默认启用策略。
 *
 * 投屏和广告片段跳过开箱可用，其余第三方/可选插件仍默认关闭。
 * PluginStore 优先使用已保存的开关，因此用户手动关闭后不会在重启时被重新开启。
 */
internal fun resolvePluginDefaultEnabled(pluginId: String): Boolean {
    return pluginId in BUILTIN_DEFAULT_ENABLED_PLUGIN_IDS
}

private val BUILTIN_DEFAULT_ENABLED_PLUGIN_IDS = setOf(
    "dlna_cast",
    "google_cast",
    "sponsor_block",
)
