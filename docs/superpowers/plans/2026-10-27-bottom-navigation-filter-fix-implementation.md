# 底部导航过滤兼容修复 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 修复手机登录账号使用实验导航数据时，“会员购”和“发布”底部按钮未被隐藏的问题。

**Architecture:** 保留现有数据层 Hook，修正 9.15.0 底部导航模型的混淆字段含义，并用 `tabId`、URI 和发布类型三类结构化信号交叉识别。不增加 View 层兜底。

**Tech Stack:** Kotlin、Android API 24+、Xposed API 82、JUnit 4、LSPatch 1.2-487、Bilibili 9.15.0。

**Spec:** `docs/superpowers/specs/2026-10-27-bottom-navigation-filter-fix-design.md`

## Global Constraints

- `a` 读取为 `tabId`，`d` 读取为 `uri`，`k` 读取为 `publishType`；不得再把 `g/reportId` 当成 `tabId`。
- 会员购匹配 `会员购Bottom`、`bilibili://mall/home` 或 `bilibili://mall/homepage`。
- 发布匹配 `publish`、`publishType == true` 或 `bilibili://uper/center_plus` URI 前缀。
- 字段缺失和反射失败时失败开放。
- 不使用可见文案，不增加 View 层扫描，不改设置默认值。
- 保留两个开关的独立组合语义。
- 未获得用户明确授权前不得提交或推送。

## Review Focus

- `bilibili://mall/homepage` 必须命中，普通商城深链不得误判，由 Task 1 覆盖。
- `tabId == publish` 即使 URI 变化也必须命中，由 Task 1 覆盖。
- `publishType == true` 只在发布开关开启时过滤，由 Task 1 覆盖。
- `reportId` 等于目标文字不得影响判断，由 Task 1 的字段读取测试覆盖。
- 字段反射异常不得删除普通按钮，由 Task 1 的失败开放测试覆盖。

---

### Task 1: 修正身份模型、匹配规则和 Hook 字段读取

**Files:**
- Modify: `app/src/main/java/com/retrsoft/bilisponsorskip/BottomMallTabFilter.kt`
- Modify: `app/src/main/java/com/retrsoft/bilisponsorskip/BottomMallTabHook.kt`
- Modify: `app/src/test/java/com/retrsoft/bilisponsorskip/BottomMallTabFilterTest.kt`
- Create: `app/src/test/java/com/retrsoft/bilisponsorskip/BottomMallTabSignalReaderTest.kt`

**Interfaces:**
- Produces: `BottomTabIdentity(tabId: String?, uri: String?, publishType: Boolean)`；更新后的 `isMallBottomTab`、`isPublishBottomTab` 和 Hook 反射读取。

- [ ] **Step 1: 写匹配规则失败测试**

覆盖会员购两个 URI 与 `会员购Bottom`；发布 `publish`、`publishType=true` 与 URI 前缀；断言普通商城、普通上传、相似 tabId 不命中，并重跑两个开关四种组合测试。

- [ ] **Step 2: 运行匹配测试确认 RED**

Run: `./gradlew testDebugUnitTest --tests '*BottomMallTabFilterTest'`

Expected: 因 `publishType` 和新匹配值尚不存在而编译失败或断言失败。

- [ ] **Step 3: 最小修改身份模型与匹配函数**

移除过时业务 ID 依赖；按 Global Constraints 实现精确结构化匹配。

- [ ] **Step 4: 写 Hook 字段读取失败测试**

复用项目现有 `ReflectiveValueReader`，断言 `a → tabId`、`d → uri`、`k → publishType`；`g/reportId` 不参与；读取异常得到空身份。

- [ ] **Step 5: 运行读取测试确认 RED**

Run: `./gradlew testDebugUnitTest --tests '*BottomMallTabSignalReaderTest'`

Expected: 因读取接口尚不存在而编译失败。

- [ ] **Step 6: 实现字段读取并接回两个 Provider Hook**

Resolved 包装对象继续从字段 `c` 取得底层 tab；静态 Provider 直接读取 tab。两条路径复用同一读取函数。

- [ ] **Step 7: 运行 Task 1 测试确认 GREEN**

Run: `./gradlew testDebugUnitTest --tests '*BottomMallTabFilterTest' --tests '*BottomMallTabSignalReaderTest'`

Expected: PASS。

### Task 2: 文档、全量验证和新版补丁包

**Files:**
- Modify: `README.md`
- Modify: `CHANGELOG.md`
- Build: `output/9.15.0/bili-9.15.0-487-lspatched-live-bottom-filter.apk`

**Interfaces:**
- Consumes: Task 1 的新身份模型和 Hook。
- Produces: 包含直播卡片过滤及底部导航兼容修复的新安装包。

- [ ] **Step 1: 更新文档**

说明底部导航按 `tabId`、URI 和发布类型过滤，并兼容 `/mall/homepage` 服务端实验值。

- [ ] **Step 2: 运行全量测试与构建**

Run: `./gradlew testDebugUnitTest assembleDebug`

Expected: `BUILD SUCCESSFUL`。

- [ ] **Step 3: 生成新版本 LSPatch APK**

使用下一个版本名/版本码、LSPatch 1.2-487、`sigBypassLevel=2`、`injectDex=false`，输出独立文件名，不覆盖现有包。

- [ ] **Step 4: 验证 APK**

检查 ZIP、签名、内嵌模块版本、配置、目标类字节码及 SHA-256。

- [ ] **Step 5: 蓝叠回归**

确认日志命中 `provider=resolved`，最终底部只剩“首页、关注、我的”，且首页直播过滤 Hook 仍成功安装。

- [ ] **Step 6: 手机验收**

由用户安装新包并确认手机底部按钮消失；若仍失败，再以手机日志作为下一轮诊断证据。
