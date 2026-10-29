# 禁用行为埋点上报设计

## 目标

增加独立开关，阻止 B 站 9.15.0 的通用行为画像埋点，同时保留播放历史、视频/直播心跳、支付、账号、上传等业务接口。

## 范围

- 拦截 `com.bilibili.lib.neuron.api.Neurons` 的点击、曝光、页面、播放器和通用行为事件入口。
- 拦截 `com.bilibili.lib.infoeyes.InfoEyesManager` 的 `feedEvent`、`report`、`report2`。
- 拦截 `com.bilibili.lib.neuron.core.b$b.submit()` 新式事件提交入口。
- 不拦截 Neuron 的崩溃、网络质量、启动、图片性能专用入口。
- 共享 `trackT/trackTInCurrentProcess` 和 Builder `submit` 同样显式放行 `infra.crash`、`infra.xcrash`、`infra.net`、`infra.setup`、`infra.webimage`，防止诊断包装函数在下游被误拦截。
- Builder 事件标识读取自 9.15.0 已确认的字段 `c`；空标识直接放行，反射异常交给 Hook 记录一次并放行。
- 不拦截 `/x/report/heartbeat/mobile`、历史同步、直播心跳等独立业务 HTTP 接口。

## 行为

- 新开关名称为“禁用行为统计上报”，默认关闭。
- 只有模块总开关和该开关同时开启时才阻止事件。
- Hook 安装失败时失败开放，不影响 B 站启动。
- 按 Neurons、InfoEyes、Builder 分别记录安装结果；每个来源每进程只记录首次拦截和首次读取异常，不记录事件参数或高频逐条日志。
- 设置初始化和保存日志包含开关状态，方便确认镜像配置是否生效。

## 风险边界

该功能当前作用于宿主主进程，可减少已适配 Java/Kotlin 入口的通用行为统计；不承诺覆盖 Native/KMP 引擎、WebView JavaScript、其他进程、动态加载模块和业务接口自带的统计字段。
