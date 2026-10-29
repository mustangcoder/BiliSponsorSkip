# 禁用行为埋点上报实施计划

**Goal:** 增加默认关闭的独立开关，并拦截 Neuron、InfoEyes 和新式 Neuron Builder 行为事件。

**Spec:** `docs/superpowers/specs/2026-10-29-disable-analytics-reporting-design.md`

## Task 1：设置协议

- 先写测试覆盖默认值、布尔协议和总开关联动。
- 在 `SettingsSnapshot`、设置镜像、同步 Receiver、独立设置页和内嵌设置页增加开关。

## Task 2：行为上报 Hook

- 先写测试验证开关、共享入口参数读取，以及 Builder 实际字段读取后的拦截结果。
- 两层放行已确认的五类诊断事件；回归测试应在移除诊断放行规则时失败。
- 新增 `AnalyticsBlockingHook`，只对返回 `void` 的目标方法在调用前 `setResult(null)`。
- 单来源安装失败独立记录；首次拦截和读取异常限频记录，不输出事件参数。
- 从 `XposedInit` 独立安装，失败开放。

## Task 3：文档与验证

- 更新 README 和 CHANGELOG。
- 运行全量测试和 Debug 构建。
- 使用模块版本 `1.3.4-adbanner.17/13417` 构建，LSPatch 1.2-487 配置维持 `-l 2`。
- 打包到 `output/9.15.0/bili-9.15.0-487-lspatched-analytics-filter.apk`，校验内嵌模块、原包、配置与签名。
- USB 手机连接时验证开关关闭、开启及重启后状态；没有设备时明确标记真机验证待完成，不以静态结果替代。

## 2026-10-29 执行与验证记录

- 开发分支：`feat/disable-analytics-reporting`，基线为已推送主分支的 Compose 修复 `9e24021`。
- 已完成设置持久化、同步 Receiver、独立及内嵌设置页接入。
- 审查发现独立设置页遗漏广播字段，已改为通过 `SettingsContract.readBooleanSettingsForSync` 从统一布尔设置表读取完整负载，避免再次漏项。
- 已完成三套来源的独立 Hook 安装、五类诊断事件的共享入口/Builder 放行、首次拦截与错误日志。
- 临时移除诊断放行规则时，3 项回归测试失败；恢复规则后通过。独立同步负载覆盖了保存为 true、历史默认 false 及完整键集合。
- 完整验证命令：`JAVA_HOME=/opt/homebrew/opt/openjdk@17/libexec/openjdk.jdk/Contents/Home ANDROID_HOME=/opt/homebrew/share/android-commandlinetools ./gradlew test assembleDebug -PreleaseVersionName=1.3.4-adbanner.17 -PreleaseVersionCode=13417`。
- Debug、Release 各 143 项测试，failures=0、errors=0；Debug APK 构建通过。仍有既存的 Bundle.get 弃用警告。
- 产物：`output/9.15.0/bili-9.15.0-487-lspatched-analytics-filter.apk`。
- 产物 SHA-256：`56e10e69634bd6bb46a08d52f7b888d430370b803176f02b4a47dd30ac54d5da`。
- 内嵌模块版本：`1.3.4-adbanner.17/13417`；原包和模块字节比较一致，ZIP、v2 签名及 LSPatch 配置校验通过。
- 签名证书 SHA-256 与上一版一致：`c081890cf2a1adf13e56d7b50a4f3d8edb35b7c46d6ccc732dd997b7e433be1d`，具备覆盖安装的签名条件。
- 初次打包时没有 ADB 设备；随后用户重新连接手机，已完成下述手机测试。

## 2026-10-29 手机验证（19:25—19:30）

- 设备：OPPO Find X7 Ultra（PHY120），B 站 `9.15.0/9150300`。
- 手机原包证书与 `.17` 产物一致；`adb install -r` 返回 `Success`，没有清除应用数据。
- 已安装 Hook：Neurons 30 个、InfoEyes 5 个、Builder 1 个，共 36 个方法。
- 初始关闭阶段：PID `15945`，初始化日志 `analyticsBlocking=false`，首页、关注、我的及设置页面操作中未观察到阻断日志。
- 开启并保存后：同一进程日志记录 `analyticsBlocking=true`，19:27:19 实际阻断 `Neurons.trackT`，19:27:21 实际阻断 Builder `submit`。
- 开启后的冷启动：PID `19965`，初始化仍为 `analyticsBlocking=true`；19:27:47 实际阻断 Neurons `trackT` 和 InfoEyes `report2`，19:29:06 实际阻断 Builder `submit`。
- 基本流程：视频详情进入成功，媒体会话为 `PLAYING(3)`、speed=1.0 且无播放错误，截图确认有正常视频画面；返回和首页、关注、我的切换正常。
- 关闭后的冷启动：PID `23600`，初始化为 `analyticsBlocking=false`，再次切换页面后，该进程阶段的阻断日志数为 0。
- 最后重新开启：保存后强制停止并重启，PID `27300`，初始化为 `analyticsBlocking=true`，再次观察到 Neurons 和 InfoEyes 阻断。手机最终停留首页，开关保持开启。
- 本次四个进程的 crash buffer 均没有记录；底栏仍只显示“首页、关注、我的”。
- 已适配三套入口均有 `analytics event blocked` 日志，写入点位于 Hook 设置跳过原方法之后，可据此确认实际命中。
- 证据保存在本机 `/tmp/bili-analytics-phone.Sy12Yw/`，包括按阶段分隔的日志和 UI hierarchy，不加入仓库。

### 结论范围

已确认本机该版本主进程中的三个已适配入口能实际阻断事件，设置开启/关闭及重启持久化符合预期，所测页面和播放流程正常。关闭阶段无阻断日志不等于证明事件已实际发出；本轮没有抓包，也未触发诊断上传、支付、上传和直播等完整业务回归，不能据此宣称所有网络埋点均被阻断或所有业务接口已验证。
