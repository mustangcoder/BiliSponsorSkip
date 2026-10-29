# Compose 底部导航过滤兼容实施计划

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 在保留旧底栏兼容性的同时，让两个现有底部入口开关也能过滤 B 站 9.15.0 的 Compose“发布”和“会员购”Tab。

**Architecture:** 新增独立的 Compose 底栏 Hook，在 `Qo1.e` 状态构造前读取首个 `List<Qo1.d>` 参数，将每项的 `Po1.q` 数据映射为现有 `BottomTabIdentity`，复用 `shouldHideBottomTab` 生成新列表并替换构造参数。旧 `BottomMallTabHook` 保持不变，两条链路分别兼容远程实验切换前后的底栏。

**Tech Stack:** Kotlin、Xposed/LSPatch、JUnit 4、Gradle Android 插件

**Spec:** `docs/superpowers/specs/2026-10-29-compose-bottom-navigation-filter-design.md`

## Global Constraints

- 不修改现有设置项、默认值和同步协议。
- 不按可见文案扫描 Compose 界面。
- 不原地修改 B 站传入列表，始终替换为新的过滤列表。
- 反射结构不匹配时失败开放并记录日志，不阻止 B 站启动。
- 保留旧底栏 Hook。
- 未获得用户额外授权前不执行 Git 提交或推送。

## Review Focus

- `Qo1.e` 存在但构造参数不是预期列表时必须失败开放，不抛异常。
- `Qo1.d.a` 或内部 `Po1.q` 缺失时必须保留该项。
- `Po1.q.h/c` 缺失且 `Qo1.d.o=false` 时必须保留该项。
- 传入不可变列表时必须通过新列表过滤，不能调用原列表的移除方法。
- 两个独立开关只有各自开启时才过滤对应入口。

---

### Task 1: Compose Tab 身份读取与过滤策略

**Files:**
- Modify: `app/src/main/java/com/retrsoft/bilisponsorskip/BottomMallTabFilter.kt`
- Create: `app/src/test/java/com/retrsoft/bilisponsorskip/ComposeBottomTabFilterTest.kt`

**Interfaces:**
- Consumes: `BottomTabIdentity`、`ReflectiveValueReader`、`filterBottomTabs(...)`
- Produces: `readComposeBottomTabIdentity(state: Any, reader: ReflectiveValueReader): BottomTabIdentity?`

- [ ] **Step 1: 写失败测试**

覆盖：从 `Qo1.d.a → Po1.q.h/c` 读取 `tabId/uri`、从 `Qo1.d.o` 读取发布标记、内部对象缺失时返回 `null`、过滤结果不修改原列表、两个开关独立生效。

- [ ] **Step 2: 运行目标测试确认 RED**

Run: `JAVA_HOME=/opt/homebrew/opt/openjdk@17/libexec/openjdk.jdk/Contents/Home ANDROID_HOME=/opt/homebrew/share/android-commandlinetools ./gradlew testDebugUnitTest --tests '*ComposeBottomTabFilterTest'`

Expected: FAIL，原因是 `readComposeBottomTabIdentity` 尚不存在。

- [ ] **Step 3: 实现最小身份读取函数**

在 `BottomMallTabFilter.kt` 中新增指定签名；字段读取失败返回 `null`，字段映射固定为 Tab 状态 `a/o` 与 Tab 数据 `h/c`。

- [ ] **Step 4: 运行目标测试确认 GREEN**

Run: 同 Step 2。

Expected: PASS。

### Task 2: Compose 状态构造 Hook

**Files:**
- Create: `app/src/main/java/com/retrsoft/bilisponsorskip/ComposeBottomTabHook.kt`
- Modify: `app/src/main/java/com/retrsoft/bilisponsorskip/XposedInit.kt`
- Create: `app/src/test/java/com/retrsoft/bilisponsorskip/ComposeBottomTabHookPolicyTest.kt`

**Interfaces:**
- Consumes: `readComposeBottomTabIdentity(...)`、`filterBottomTabs(...)`、`SettingsRepository.current`
- Produces: `ComposeBottomTabHook(settings: SettingsRepository, classLoader: ClassLoader).install()`

- [ ] **Step 1: 写失败测试**

为纯策略入口验证：总开关关闭时不处理；两个子开关均关闭时不处理；开启后返回新列表并过滤命中项；不可识别项保留。

- [ ] **Step 2: 运行目标测试确认 RED**

Run: `JAVA_HOME=/opt/homebrew/opt/openjdk@17/libexec/openjdk.jdk/Contents/Home ANDROID_HOME=/opt/homebrew/share/android-commandlinetools ./gradlew testDebugUnitTest --tests '*ComposeBottomTabHookPolicyTest'`

Expected: FAIL，原因是 Compose 策略入口尚不存在。

- [ ] **Step 3: 实现构造 Hook**

Hook `Qo1.e` 的全部构造函数；只处理第一个参数为 `List` 的调用，使用新 `ArrayList` 替换 `param.args[0]`。安装、过滤成功和异常分别写入 `BiliSponsorSkip` 日志。

- [ ] **Step 4: 接入初始化流程**

在旧 `BottomMallTabHook` 后独立安装，新 Hook 失败不能影响其他功能。

- [ ] **Step 5: 运行目标测试及全量测试确认 GREEN**

Run: `JAVA_HOME=/opt/homebrew/opt/openjdk@17/libexec/openjdk.jdk/Contents/Home ANDROID_HOME=/opt/homebrew/share/android-commandlinetools ./gradlew test assembleDebug`

Expected: `BUILD SUCCESSFUL`。

### Task 3: 打包与手机运行验证

**Files:**
- Output only: `output/9.15.0/bili-9.15.0-487-lspatched-compose-bottom-filter.apk`

**Interfaces:**
- Consumes: `app/build/outputs/apk/debug/app-debug.apk`、9.15.0 原包、`tools/lspatch-v1.2-487-release.jar`
- Produces: 可覆盖安装且包含新模块的 LSPatch APK

- [ ] **Step 1: 使用 9.15.0 原包重新打补丁**

不得对现有已打补丁 APK 二次打补丁；从其 `assets/lspatch/origin.apk` 提取原包，嵌入新的 Debug 模块。

- [ ] **Step 2: 验证产物**

核对宿主版本 `9.15.0/9150300`、内嵌模块、原包 SHA-256、LSPatch 配置及 APK 签名；确认签名证书与手机当前安装包一致。

- [ ] **Step 3: 覆盖安装到当前 USB 手机**

Run: `adb install -r <patched-apk>`

Expected: `Success`，不清除 B 站数据。

- [ ] **Step 4: 强制停止并重新启动验证**

检查日志出现 Compose Hook 安装和过滤记录；通过 UI hierarchy 验证底栏不含“发布按钮”和“会员购”，仍含“首页、关注、我的”。

- [ ] **Step 5: 记录结果**

报告 APK 绝对路径、SHA-256、测试结果和手机实测证据；不提交代码。
