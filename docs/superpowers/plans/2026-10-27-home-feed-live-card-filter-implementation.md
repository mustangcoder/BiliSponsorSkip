# 首页推荐直播卡片过滤 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 为哔哩哔哩首页“推荐”频道新增默认开启、可独立控制的直播卡片过滤功能。

**Architecture:** 复用现有 `HomeFeedAdHook` 的解析器与适配器提交 Hook，在同一列表过滤阶段分别计算广告与直播命中。直播依据 `BasicIndexItem` 的 `goTo`、`cardGoto` 和明确直播 URI 组合识别，字段缺失或读取失败时保留卡片。

**Tech Stack:** Kotlin、Android API 24+、Xposed API 82、JUnit 4、LSPatch 1.2-487、Bilibili 9.15.0。

**Spec:** `docs/superpowers/specs/2026-10-27-home-feed-live-card-filter-design.md`

## Global Constraints

- 仅作用于首页“推荐”频道现有 `HomeFeedAdHook` 覆盖的数据流。
- 独立开关键名为 `hide_home_feed_live_cards`，默认值为 `true`。
- `goTo/cardGoto` 只匹配 `live`、`live_rcmd`；URI 只匹配 `bilibili://live/`、`https://live.bilibili.com/` 前缀。
- 不匹配标题、角标或 UP 主名称中的直播文案。
- 模块总开关关闭或独立开关关闭时不得过滤直播卡片。
- 字段缺失、反射失败或宿主版本不兼容时失败开放。
- 不新增依赖，不改动直播分区、关注页、搜索结果和详情页推荐。
- 未获得用户明确授权前不得执行 `git commit` 或 `git push`。

## Review Focus

- 三个字段均为 `null` 或不存在时必须保留卡片，由 Task 1 的缺失字段测试覆盖。
- `vertical_live`、`live_room`、`live_event` 等相似值不得误判，由 Task 1 的精确匹配测试覆盖。
- 标题包含“直播”但 `goTo != "live"` 时必须保留，由 Task 1 的文案无关测试覆盖。
- 广告与直播开关四种组合必须互不耦合，由 Task 1 的组合过滤测试覆盖。
- 同一列表同时包含广告和直播时，日志计数与最终列表必须一致，由 Task 1 的分类结果测试覆盖。

---

### Task 1: 首页直播卡片信号与过滤策略

**Files:**
- Modify: `app/src/main/java/com/retrsoft/bilisponsorskip/HomeFeedAdFilter.kt`
- Modify: `app/src/main/java/com/retrsoft/bilisponsorskip/HomeFeedAdHook.kt`
- Modify: `app/src/test/java/com/retrsoft/bilisponsorskip/HomeFeedAdFilterTest.kt`
- Create: `app/src/test/java/com/retrsoft/bilisponsorskip/HomeFeedFilterPolicyTest.kt`

**Interfaces:**
- Consumes: `HomeFeedAdSignals`、现有首页解析器/适配器 Hook、反射读取辅助方法。
- Produces: `HomeFeedCardSignals(ad, goTo, cardGoto, uri)`、`isHomeFeedLiveCard(signals)`、`readHomeFeedCardSignals(item, reader)`、`filterHomeFeedResult(items, hideAds, hideLiveCards, signals)`。

- [ ] **Step 1: 写直播精确识别的失败测试**

在 `HomeFeedAdFilterTest` 中断言：`goTo/cardGoto` 的 `live`、`live_rcmd` 和两类明确直播 URI 为真；`null`、`av`、`vertical_live`、`live_room`、`live_event` 为假；标题不进入信号模型。

- [ ] **Step 2: 运行测试确认 RED**

Run: `./gradlew testDebugUnitTest --tests '*HomeFeedAdFilterTest'`

Expected: 因 `HomeFeedCardSignals` 或 `isHomeFeedLiveCard` 尚不存在而编译失败。

- [ ] **Step 3: 实现最小直播识别模型**

在 `HomeFeedAdFilter.kt` 增加上述接口和精确允许集合，不做包含匹配。

- [ ] **Step 4: 写广告/直播组合过滤与分类计数的失败测试**

在 `HomeFeedFilterPolicyTest` 覆盖四种开关组合，并断言混合列表的 `removedAds`、`removedLiveCards`、剩余顺序和输入列表不变；同一张卡同时满足广告和直播时只删除一次，并优先计入广告。

- [ ] **Step 5: 运行组合测试确认 RED**

Run: `./gradlew testDebugUnitTest --tests '*HomeFeedFilterPolicyTest'`

Expected: 因 `filterHomeFeedResult` 和 `HomeFeedFilterOutcome` 尚不存在而编译失败。

- [ ] **Step 6: 实现纯过滤策略并接入 Hook**

先为 `readHomeFeedCardSignals` 增加测试，覆盖公开方法和字段回退，再实现读取 `getGoto/goTo`、`getCardGoto/cardGoto`、`getUri/uri`。`filterHomeFeedResult` 返回新列表及分类计数；现有两个 Hook 入口均调用该策略，再以结果替换原列表内容。日志分别输出广告和直播移除数。

- [ ] **Step 7: 运行 Task 1 测试确认 GREEN**

Run: `./gradlew testDebugUnitTest --tests '*HomeFeedAdFilterTest' --tests '*HomeFeedFilterPolicyTest'`

Expected: PASS。

- [ ] **Step 8: 条件提交**

仅在用户明确授权提交后执行：

```bash
git add app/src/main/java/com/retrsoft/bilisponsorskip/HomeFeedAdFilter.kt \
  app/src/main/java/com/retrsoft/bilisponsorskip/HomeFeedAdHook.kt \
  app/src/test/java/com/retrsoft/bilisponsorskip/HomeFeedAdFilterTest.kt \
  app/src/test/java/com/retrsoft/bilisponsorskip/HomeFeedFilterPolicyTest.kt
git commit -m "feat: filter home feed live cards"
```

### Task 2: 独立设置与双向同步

**Files:**
- Modify: `app/src/main/java/com/retrsoft/bilisponsorskip/Settings.kt`
- Modify: `app/src/main/java/com/retrsoft/bilisponsorskip/SettingsActivity.kt`
- Modify: `app/src/main/java/com/retrsoft/bilisponsorskip/BiliSettingsEntryInjector.kt`
- Modify: `app/src/main/java/com/retrsoft/bilisponsorskip/SettingsSyncReceiver.kt`
- Create: `app/src/test/java/com/retrsoft/bilisponsorskip/HomeFeedLiveSettingsTest.kt`

**Interfaces:**
- Consumes: Task 1 的直播过滤开关布尔值。
- Produces: `SettingsSnapshot.hideHomeFeedLiveCards: Boolean`、`SettingsContract.KEY_HIDE_HOME_FEED_LIVE_CARDS`，并覆盖所有设置传输路径。

- [ ] **Step 1: 写设置契约失败测试**

断言默认值为 `true`；键值为 `hide_home_feed_live_cards`；键存在于 `BOOLEAN_SETTING_KEYS` 和 `booleanSettings()`；广告、直播两个开关可以取四种独立组合。

- [ ] **Step 2: 运行测试确认 RED**

Run: `./gradlew testDebugUnitTest --tests '*HomeFeedLiveSettingsTest'`

Expected: 因设置字段和键尚不存在而编译失败。

- [ ] **Step 3: 接入设置存储与同步**

将字段加入 `SettingsSnapshot`、`booleanSettings()`、镜像写入、快照读取、`SettingsSyncReceiver` 和 Bundle 传输，所有读取默认值均为 `true`。

- [ ] **Step 4: 接入两套设置 UI**

在独立设置页与 B 站内嵌设置页的广告过滤区域新增“隐藏首页推荐直播卡片”，说明文字为“过滤首页推荐频道中结构化标记为直播的卡片”，并在保存时写回新字段。

- [ ] **Step 5: 运行设置及首页过滤测试确认 GREEN**

Run: `./gradlew testDebugUnitTest --tests '*HomeFeedLiveSettingsTest' --tests '*HomeFeedAdFilterTest' --tests '*HomeFeedFilterPolicyTest'`

Expected: PASS。

- [ ] **Step 6: 条件提交**

仅在用户明确授权提交后执行，提交信息：`feat: add home feed live filter setting`。

### Task 3: 文档、构建与补丁包验证

**Files:**
- Modify: `README.md`
- Modify: `CHANGELOG.md`
- Verify: `app/src/main/java/com/retrsoft/bilisponsorskip/XposedInit.kt`
- Build: `app/build/outputs/apk/debug/app-debug.apk`
- Build: `output/9.15.0/bili-9.15.0-487-lspatched-live-filter.apk`

**Interfaces:**
- Consumes: Task 1 的过滤策略和 Task 2 的设置字段。
- Produces: 文档完备、通过测试的模块 APK 和不覆盖旧包的新 LSPatch APK。

- [ ] **Step 1: 补充 README 和 CHANGELOG**

说明该功能默认开启、使用独立开关、依据 `goTo/cardGoto` 的直播类型与明确直播 URI、仅影响首页推荐频道。

- [ ] **Step 2: 运行全量验证**

Run: `./gradlew testDebugUnitTest assembleDebug`

Expected: `BUILD SUCCESSFUL`，零失败测试。

- [ ] **Step 3: 构建版本化模块与 LSPatch APK**

使用下一个未占用的模块版本名/版本码；以 LSPatch 1.2-487、`--sigbypasslv 2`、嵌入模块且不启用 `injectDex` 修补原始 9.15.0 APK，输出到独立文件名，不覆盖现有包。

- [ ] **Step 4: 验证产物**

检查 ZIP 完整性、APK 签名、内嵌模块版本、`injectDex=false`、`sigBypassLevel=2` 和 SHA-256；运行 `git diff --check`。

- [ ] **Step 5: 运行时验收**

在蓝叠或真机打开首页推荐流，确认直播卡片消失、普通视频保留；关闭独立开关后直播卡片恢复；检查无 FATAL/ANR，并确认现有广告过滤仍按自己的开关工作。

- [ ] **Step 6: 条件提交与推送**

只有用户明确要求时，提交剩余文档并按用户指定分支/远端推送。
