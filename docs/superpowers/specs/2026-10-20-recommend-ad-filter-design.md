# 推荐广告过滤设计

## 背景与目标

Bilibili 9.15.0 中有两条尚未覆盖的广告推荐链路：

1. 全屏连续刷视频（Story）加载下一批视频时，列表中可能包含带推广或广告标记的视频；
2. 普通视频详情页下方的相关推荐中可能包含商业推广卡片。

目标是在数据或组件生成阶段排除明确带广告结构化标记的条目，使广告不会短暂显示，也不会留下空白。普通视频即使标题或简介包含“广告”“推广”等文字，只要没有广告数据标记，就必须保留。

## 范围

本次新增两个彼此独立、默认开启的设置：

- `跳过全屏刷视频广告`；
- `隐藏详情页相关推荐广告`。

不改变首页信息流广告开关，不处理视频内部的口播/植入片段，不使用 OCR、标题关键词或界面坐标判断。

## 已确认的数据链路

### 全屏连续刷视频

9.15.0 的 Story 接口返回 `StoryFeedResponse`，其中 `Data.items` 是 `List<StoryDetail>`。`StoryDetail` 自带以下明确广告信号：

- `adInfo` / `getAdInfo()`；
- `isAd()`；
- `isNaturalAd()`；
- 广告专用 `goto`，包括广告视频、广告图片和广告直播类型。

`StoryVideoFragment$getFeedListData$dataListener$1.b(List<StoryDetail>, Config)` 在列表进入 Story 播放器前统一消费这些条目。过滤应发生在该列表进入播放器适配器之前，而不是播放后模拟上滑。

### 视频详情页相关推荐

详情页相关推荐使用 `RelateCard`，商业推广卡片具有明确枚举 `RelateCardType.CM`。`DetailRelateService` 将内部 `B0` 数据转换为 `RunningUIComponent`；其 CM 分支会创建广告专用组件、曝光统计和点击逻辑。

首屏和加载更多最终都经过同一组件转换路径，因此在组件创建前拒绝 `CM` 类型可以避免空白，并保留 AV、番剧、直播、课程等普通推荐。

## 方案

### 1. 统一广告信号模型

新增纯 Kotlin 过滤模型，复用首页已有的结构化信号思想，但不强行复用不同宿主模型的反射代码：

- `StoryAdSignals`：`isAd`、`isNaturalAd`、`hasAdInfo`、`goto`；
- `DetailRelateSignals`：`cardType`，其中仅 `CM` 视为商业广告；
- 分别提供纯函数判定与列表过滤，便于在没有 Android/Xposed 环境的单元测试中验证。

判定采取“明确标记才删除”的保守策略。无法读取字段、字段为空或枚举未知时保留条目并记录一次诊断，不因异常默认删除。

### 2. Story 数据 Hook

新增 `StoryAdFilterHook`：

- 优先 Hook `StoryFeedResponse.getItems()` 的返回值；该类仅服务 Story 数据，作用范围比通用列表 Hook 更小；
- 对返回列表创建新列表，不修改不可变或宿主共享集合；
- 通过公开方法 `isAd()`、`isNaturalAd()`、`getAdInfo()`、`getGoto()` 读取信号，公开方法不可用时再按已验证字段回退；
- 过滤完成后保留原有相对顺序；
- 列表为空时返回空列表，由 B 站现有“没有更多”分支处理；
- 开关关闭或模块总开关关闭时原样返回。

如果运行时验证发现 `getItems()` 在同一响应上被重复调用，Hook 必须保持幂等，不重复写宿主对象，也不触发额外网络请求。

### 3. 详情推荐组件 Hook

新增 `DetailRelateAdFilterHook`：

- 定位稳定类 `com.bilibili.ship.theseus.united.page.intro.module.relate.DetailRelateService`；
- 不依赖混淆方法名 `d`，而是按方法签名选择：单个 `B0` 参数、返回 `RunningUIComponent`；
- 在方法执行前读取 `B0` 的 `RelateCardType`；类型为 `CM` 时直接返回 `null`，沿用宿主已有的“组件为空则不加入列表”逻辑；
- 非 `CM` 类型、类型读取失败或开关关闭时继续执行原方法。

该边界覆盖首屏和加载更多，因为两者转换成 `B0` 后都通过该组件创建函数。不会修改 `RelateCard` protobuf，也不会破坏分页游标或推荐列表内部计数。

### 4. 设置与生命周期

`SettingsSnapshot` 新增：

- `skipStoryFeedAds: Boolean = true`；
- `hideDetailRelateAds: Boolean = true`。

两个字段加入独立模块设置页、Bilibili 内嵌设置页、镜像设置和同步 Bundle。Hook 安装一次，运行时每次过滤读取当前设置，因此修改开关后下一批 Story 数据或下一次推荐组件创建即可生效。

### 5. 日志与失败行为

仅在真正删除广告时记录汇总：来源、删除数量和剩余数量；不记录广告文案、跳转 URL 或广告追踪字段。

Hook 点不存在时记录一次明确错误，但不得阻止播放器和其他模块功能初始化。字段读取异常按“保留条目”处理，避免版本变化导致所有推荐消失。

## 测试

### 单元测试

- 每个 Story 明确广告信号均能命中；
- 普通条目以及仅文案包含“广告/推广”的条目保留；
- 混合列表、连续多个广告、全广告列表保持正确顺序和空列表语义；
- `RelateCardType.CM` 被拒绝，AV、番剧、直播、课程和未知类型保留；
- 两个设置默认开启并进入布尔设置同步集合；
- 模块总开关关闭时两个过滤均不生效。

### 构建与静态验证

```shell
./gradlew testDebugUnitTest assembleDebug
git diff --check
```

检查最终内嵌模块版本、LSPatch `injectDex=false`、签名和 APK 压缩完整性。

### 蓝叠运行时回归

- 全屏连续刷视频多次加载下一批数据，明确广告条目不进入播放器；
- 普通视频顺序连续，不发生重复、索引错位、空白页或自动返回；
- Story 过滤开关关闭后广告条目可按原逻辑出现；
- 视频详情页首屏相关推荐与加载更多均不显示 `CM` 卡片；
- 详情推荐开关关闭后 CM 卡片恢复；
- 普通相关推荐不留空白，点击和滚动正常；
- 首页信息流、底栏、游戏中心、开屏广告和 SponsorBlock 功能无回归；
- 日志无 `FATAL EXCEPTION`、ANR 或列表越界。

## 兼容性与限制

- 本设计以粉版 Bilibili 9.15.0 的实际 APK 结构为基线；客户端升级可能改变类或方法签名。
- Story 使用稳定业务模型和公开判定方法，预计比 UI 文案匹配更耐版本变化。
- 详情推荐依赖 `DetailRelateService`、`B0` 和 `RelateCardType.CM`；安装时会输出 Hook 安装结果，便于升级后诊断。
- 本功能只过滤客户端明确标记的商业广告，不判断未标记的软广、UP 主推广或视频内容中的植入。
