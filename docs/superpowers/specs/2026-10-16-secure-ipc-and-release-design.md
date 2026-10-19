# 安全 IPC 与发布流水线修复设计

## 背景与范围

本次只修复安全审计确认的三个中风险问题：

1. 导出的设置广播允许任意本机应用修改模块与宿主设置；
2. Android 7–13 上导出的统计广播允许伪造统计并制造无限持久键；
3. 版本标签在默认分支来源校验前即可接触签名密钥并发布 APK。

修复必须保留 Android 7.0（API 24）及以上、LSPosed 独立模块、LSPatch/预嵌模块、现有设置页、片段跳过、广告过滤和本地统计能力。低风险问题不在本次范围内。

## 安全目标

- 跨 UID 的设置和统计操作必须使用系统提供的 Binder 调用身份鉴权；调用者提供的包名、路径和 Bundle 字段均不能充当身份凭据。
- 只有四个受支持的 Bilibili 包 UID 能读取模块设置、写回嵌入设置和合并统计。
- 模块设置、SponsorBlock userId 和用户名不能被普通本机应用修改或读取。
- 外部输入不能创建无限数量的统计持久键。
- 不引入前台服务、常驻连接、轮询、AlarmManager 或 WorkManager。
- 未经默认分支来源验证的 tag commit 不能接触签名材料、执行仓库控制的构建代码或写入 Release。

## 总体方案

### 1. 认证 Binder Service

模块 APK 新增一个 `exported=true` 的 bound Service 和版本化 AIDL 接口，取代：

- `SettingsSyncReceiver`；
- `SkipStatsReceiver`；
- 宿主进程中的动态 `ACTION_UPDATE_SETTINGS` Receiver；
- 四组设置/统计广播 action。

AIDL 只暴露三个业务操作：

- 获取模块设置；
- 嵌入设置写回模块；
- 合并宿主累计统计快照。

协议使用字段固定的 Parcelable，不接收任意 Bundle。Parcelable 包含协议版本；字符串、枚举和数值在服务端再次校验长度与范围。

### 2. 调用者鉴权

每个 AIDL 方法入口都重新读取 `Binder.getCallingUid()`，不能只在 `onBind()` 检查。服务端使用 `PackageManager.getPackagesForUid(uid)` 获取完整包集合，并执行以下拒绝优先规则：

- UID 必须大于普通应用起始 UID；拒绝 root、system、isolated 或无法解析的 UID；
- 包集合必须非空；
- 包集合中的每个包都必须属于四个目标包白名单；
- 设置/统计载荷中的 package/process 只能使用服务端从 UID 推导出的目标包；
- 统计 process 必须等于包名或以 `包名:` 开头；
- 不信任调用者传入的 source package、APK 路径或其他身份声明。

无法使用签名级 permission：官方 Bilibili、LSPatch 重签宿主和模块 APK 的签名并不一致。Binder UID 校验是 API 24+ 下共同可用的强制边界。

### 3. LSPosed 模式

宿主内模块代码通过显式 `ComponentName` 按需绑定模块 Service，不预先调用 `resolveService()`，减少 Android 11 包可见性影响。

设置拉取发生在：

- Bilibili 主进程初始化；
- Bilibili 从后台返回前台；
- 用户点击 Bilibili 设置中的模块入口。

前台拉取采用最短间隔和 in-flight 去重。单次操作完成立即解绑；不保留长期 callback 或 Binder 连接。

独立模块设置页只写模块私有 SharedPreferences。Bilibili 返回前台后主动拉取，因此不再由模块向宿主发送广播。

### 4. LSPatch/预嵌模式

未安装独立模块 APK 时，显式 bind 失败属于正常分支：

- 宿主继续使用自身私有 mirror preferences；
- 嵌入设置页直接调用 `SettingsRepository.updateFromEmbeddedSettings()`；
- 不触发网络、轮询或重试风暴；
- bind 失败按退避时间缓存，避免每次 Activity resume 重试。

如果设备同时安装独立模块 APK，则可以使用认证 Service 同步，但“独立设置页还是嵌入设置页”的选择继续沿用现有多信号判断，不能仅以 bind 成功作为模式依据。

### 5. 统计合并与能耗

每次跳过仍只在宿主私有 SharedPreferences 中更新累计快照。跨进程同步调整为：

- 第一次未同步变化安排一次进程内延迟 flush，窗口为 60 秒；
- 窗口内后续跳过只更新累计值，不重复 bind；
- 主进程初始化、返回前台和打开模块设置入口时尝试 flush；
- 同步成功后保存已确认的 generation/count/savedMs；
- 同步失败保留累计快照，下次触发重试；
- 进程在延迟任务执行前退出不会丢数据，因为下次发送的是累计快照。

模块端统计存储改为每个 `package/process` 固定槽位。generation 变化时替换该槽位的基线，不再把 generation 哈希成无限新键。单次增量、累计值、字符串长度和进程数量都设置明确上限。

该设计没有前台服务、定时唤醒或常驻进程。正常观看没有额外轮询；只有启动/回前台设置同步，以及发生跳过后的至多每分钟一次短 Binder 事务。

### 6. 协议版本与失败行为

- AIDL 协议包含整型版本；服务端拒绝不支持的版本。
- LSPosed 两端版本不匹配时保留宿主当前有效设置，不清空 mirror，也不静默改用未认证广播。
- LSPatch bind 不可用时使用宿主本地 mirror。
- IPC 异常只记录一次节流诊断并安全回退，不中断视频播放。
- 删除旧广播组件后，不兼容旧模块/旧嵌入代码的跨版本同步；用户需要同时更新模块或重新打包。这一变化在 README/CHANGELOG 中明确说明。

## 发布流水线修复

仅把祖先校验移动到现有 tag workflow 前部仍不完整：GitHub 的 `push` 事件会使用关联 tag/ref 中的 workflow 版本，恶意 tag commit 可以同时删除仓库内校验。发布改为两阶段：

1. `release-request.yml` 由 `v*.*.*` tag push 触发，不持有 secrets，权限为只读，只负责产生一个可观察的候选 workflow run；
2. `release.yml` 使用 `workflow_run` 监听第一阶段。GitHub 只从默认分支加载 `workflow_run` workflow，因此该阶段的校验逻辑不受候选 tag commit 控制；
3. 特权阶段先 checkout 默认分支版本，读取 `workflow_run.head_sha`，fetch 默认分支和 tags；
4. 要求候选 SHA 是默认分支祖先；要求该 SHA 上恰好有一个符合严格语义版本格式的 tag；
5. 只有验证通过后才 checkout 候选 SHA、读取 changelog、安装构建环境、恢复签名材料、运行 Gradle 和写入 Release；
6. 无法解析候选 SHA、tag 数量不为一、tag 格式非法或 ancestry 检查失败时立即终止。

签名 secrets 建议迁移到 `release-signing` GitHub Environment，并配置 required reviewer、禁止自批准和 tag/branch deployment policy。环境保护属于仓库外配置，README/CONTRIBUTING 必须给出明确设置步骤；即使环境尚未配置，可信默认分支 `workflow_run` 中的 ancestry 校验仍是仓库内强制边界。

后续 changelog 步骤保留并发变化检查，但不再承担首次来源认证职责。本次不同时处理 Action SHA 固定或 Gradle 依赖完整性，它们属于已记录的低风险后续事项。

## 代码结构

预计新增：

- AIDL 接口及设置/统计 Parcelable；
- `SecureModuleBridgeService`；
- 宿主侧短连接客户端；
- 调用 UID 鉴权器；
- 统计节流/固定槽位逻辑及对应测试。

预计修改：

- Android Manifest；
- `SettingsRepository`、`SettingsActivity`、嵌入设置页；
- `SkipController`、本地统计协议；
- `XposedInit` 生命周期转发；
- release workflow；
- README、CHANGELOG 和相关测试。

预计删除：

- `SettingsSyncReceiver`；
- `SkipStatsReceiver`；
- 旧设置/统计广播常量和调用路径。

## 测试与验证

### 单元测试

- UID 无包、root/system/isolated、混合白名单 shared UID、非白名单包全部拒绝；
- 四个目标包 UID 接受；
- 不信任载荷中的 package/process；
- 设置 Parcelable 对字段类型、枚举、长度和版本执行限制；
- 每个 package/process 只保留固定统计槽位；
- generation 切换不会增加持久键；
- 统计节流窗口内只安排一次 flush；
- 失败后累计值保留并可重试；
- LSPatch 无 Service 时保留本地 mirror；
- workflow 来源检查顺序测试。

### 构建检查

```shell
./gradlew testDebugUnitTest assembleDebug lintDebug
```

检查最终 Manifest 不再导出旧 Receiver，并只包含新的认证 Service。

### 设备回归

- Android 7、8、12、13、14/15 至少覆盖代表性设备或模拟器；
- 非白名单测试 APK 能 bind，但所有 AIDL 事务均被拒绝，或在 `onBind` 阶段直接拒绝；
- 四个目标包至少各验证一次合法调用；
- LSPosed：独立设置页保存，返回 Bilibili 后生效；
- LSPatch：无独立模块时嵌入设置正常，bind 失败不会循环；
- 连续多次跳过在一分钟窗口内只发生一次跨进程统计同步；
- 首页、关注、我的、视频播放、广告过滤和片段跳过无回归；
- 伪造旧设置/统计广播不再命中任何组件。

### 发布流水线验证

- tag push 第一阶段不持有写权限或签名 secrets；
- 特权 `workflow_run` 使用默认分支中的 workflow 版本；
- 指向默认分支祖先且只有一个合法版本 tag 的候选可继续构建；
- 指向非默认分支提交、无 tag、多 tag 或非法 tag 的候选在 checkout 候选代码和任何 secret-bearing 步骤前失败；
- changelog 并发保护仍然生效。

## 已接受的产品变化

- 独立模块设置页修改后，在用户返回 Bilibili 前台时生效，不保证后台宿主立即更新；
- 模块聚合统计最多延迟约 60 秒；
- 升级安全 IPC 后，旧嵌入模块代码与新独立模块 APK 的跨版本同步不再保证兼容，需要同时更新或重新打包。
