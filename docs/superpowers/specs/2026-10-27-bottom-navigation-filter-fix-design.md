# 底部导航过滤兼容修复设计

## 意图

修复手机登录账号下“隐藏底部会员购入口”和“隐藏底部发布按钮”开关已开启，但对应按钮仍显示的问题，同时保持蓝叠已验证路径和三等分布局不回归。

## 根因

9.15.0 的底部导航模型 `tv.danmaku.bili.ui.main2.resource.x` 中，混淆字段的实际含义为：

- `a`：`tabId`
- `d`：规范化后的 `uri`
- `g`：`reportId`
- `k`：是否为 `type == 3` 的发布入口

现有 Hook 将 `a` 当作业务 ID、将 `g` 当作 `tabId`，因此结构化后备判断无效，蓝叠仅因 URI 恰好命中而成功。手机账号可能收到 `bilibili://mall/homepage` 或其他实验数据，从而绕过过滤。

## 已选方案

只修正数据 Hook，不增加 View 层扫描兜底。

新的结构化身份包含 `tabId`、`uri` 和 `publishType`：

- 会员购在以下任一条件成立时匹配：
  - `tabId == "会员购Bottom"`
  - `uri == "bilibili://mall/home"`
  - `uri == "bilibili://mall/homepage"`
- 发布在以下任一条件成立时匹配：
  - `tabId == "publish"`
  - `publishType == true`
  - `uri` 以 `bilibili://uper/center_plus` 开头

Resolved 包装对象仍从字段 `c` 取得底层 `x`，三个静态资源 Provider 继续使用同一身份读取函数。字段缺失或反射失败时按 `null/false` 处理，失败开放。

## 非目标

- 不增加界面层隐藏或布局重排逻辑。
- 不过滤顶部“游戏中心”、直播分区或“我的”页面服务入口。
- 不使用按钮文案进行匹配。
- 不修改两个独立开关的默认值和设置同步方式。

## 验证

- 单元测试覆盖会员购两种 URI、正确 `tabId`，并确认普通商城 URI 不会误判。
- 单元测试覆盖发布 `tabId`、`publishType`、URI 前缀，以及普通上传入口不误判。
- 继续覆盖两个开关四种组合和输入列表不变。
- 对 9.15.0 反编译字段映射增加纯读取策略测试，防止再次把 `reportId` 当成 `tabId`。
- 运行全量测试、Debug 构建和 LSPatch 产物验证。
- 在蓝叠确认只剩“首页、关注、我的”；手机由用户再次安装验证。
