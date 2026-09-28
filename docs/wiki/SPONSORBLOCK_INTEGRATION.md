# BiliPai 商单提示与广告跳过

基于 BiliPai `fd0ac74`，复用已有的「空降助手」插件、播放器进度检查和跳过按钮。

## 使用

「空降助手」现默认开启，首次使用自动跳过广告/恰饭片段，并显示绿色进度段及社区商单提示。可在「设置 → 插件 → 空降助手」手动关闭。覆盖升级时，没有保存过开关的安装使用新默认值；已明确保存的开关和逐类别策略保留。自动模式显示跳过提示，手动模式使用原有的跳过按钮。

视频详情标题上方显示社区标记：

- 整条赞助视频：显示商单提示，整条视频不会因此被跳过。
- 品牌合作 / 抢先体验：显示单独的提示。
- 有定时广告片段：显示广告提示，并按照设置处理片段。
- 无标注或请求失败：不显示肯定性结论，不将视频标为「无广告」。

提示复用应用的主题组件，覆盖手机、平板及影院布局。数据来自小电视空降助手社区，提示文案注明来源与参考性质。

## 绿色进度与封面角标

- 广告 / 恰饭片段默认使用小电视空降助手同款绿色 `#00D400`；展开的进度条和隐藏控件后的底部细进度条均显示。已有自定义颜色仍保留。
- 封面右上角显示「整条商单」或「品牌合作」，覆盖首页卡片、横幅、相关推荐、UP 列表、历史、收藏和稍后再看通用卡片。
- 封面使用扩展同款 `GET /api/videoLabels/{prefix}`：BV 号 SHA-256 一次后的前 4 位十六进制，并在响应内精确匹配完整 BV 号。不会把普通定时广告片段误标成整条商单。
- 查询限并发 3、最多缓存 128 个前缀块，成功缓存 15 分钟，失败 30 秒后可重试。卡片回收和插件关闭后不保留错误视频的角标。
- 标签来源是社区，并非所有商单都有标注；没有标签不代表没有广告。
- 只读接口验证：示例 `BV1bY4y1v7Mb` 的前缀为 `7520`，服务端返回 HTTP 200 和 `sponsor` 整条视频标签。

## 播放行为

- 按 BV 号和当前 CID 请求数据，分 P 切换重新加载，旧请求结果不能覆盖新视频。
- 保留同一视频的多段广告；只合并起止位置、类别和动作完全一致的重复标注。
- `full` 标记与定时片段分开处理；`[0, 0]` 商单标记可以提示，不参与 seek 或进度色块。
- 支持重叠片段，起点包含、终点不包含；处理 seek 后短暂返回旧进度的情况，避免向后跳。
- 保留暂停意图，跳过不会把已暂停的视频重新播放。
- 关闭插件、换分 P 或用户拖动进度时恢复插件产生的静音；不改变用户原来的音量。
- 不新增标注、上传、投票或帐号功能。上游已有的社区贡献与播放记录上传开关默认仍为关闭。

## 接口依据

沿用上游 `GET /api/skipSegments?videoID=...&cid=...&category=...`，获取 sponsor 与 exclusive_access 标记；不使用提交接口完成上述功能。

- 项目：https://github.com/hanydd/BilibiliSponsorBlock
- 协议：https://github.com/hanydd/BilibiliSponsorBlock/wiki/API
- 整个视频标签：https://github.com/hanydd/BilibiliSponsorBlock/wiki/Full-Video-Labels

已用官方 README 的示例视频 `BV1bY4y1v7Mb` 做只读接口验证：返回多段广告、静音片段以及 `actionType=full, segment=[0,0]` 的整条商单标签。

## 验证状态

已增加封面标签哈希、类别优先级和未知类别测试，保留原有商单与 seek 边界测试。
本轮另有固定推荐格及接近看完策略测试。完整通过数量、最终 APK 构建结果见交付目录 `BiliPai-verification.txt`。
设备播放、深色模式、平板布局及后台通知仍需实际验收。

在具备本项目 Android SDK 和既有 Maven 依赖访问条件的环境中运行：

```sh
./gradlew :app:testDebugUnitTest \
  --tests 'com.android.purebilibili.feature.plugin.SponsorBlockPlaybackPolicyTest' \
  --tests 'com.android.purebilibili.feature.plugin.SponsorBlockPluginPolicyTest' \
  --tests 'com.android.purebilibili.feature.video.viewmodel.SponsorBlockSeekPolicyTest' \
  --tests 'com.android.purebilibili.feature.video.viewmodel.VideoPlaybackViewModelSponsorBlockPolicyTest' \
  --tests 'com.android.purebilibili.data.repository.SponsorBlockClientPolicyTest'
```

真机验收应覆盖：示例商单播放、自动/手动跳过、暂停后拖动到广告、连续切换两个视频、切换分 P、无标注视频、断网以及播放中关闭插件。

改动保存在本地源码及配套补丁中，未提交或推送到 GitHub。
