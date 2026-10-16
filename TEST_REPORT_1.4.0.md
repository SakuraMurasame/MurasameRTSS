# MurasameRTSS 1.4.0 检查报告

日期：2026-10-16

## 更新功能

- 固定来源：SakuraMurasame/MurasameRTSS 的 GitHub Releases。
- 请求地址：https://api.github.com/repos/SakuraMurasame/MurasameRTSS/releases/latest
- 自动检查默认开启，进入主界面时执行。成功检查后至少间隔 24 小时，失败后至少间隔 1 小时；没有后台轮询服务。手动检查可立即执行，进行中的请求合并，GitHub 明确限流时遵守等待时间。
- 关于 → 软件更新：自动开关、检查状态、立即检查、新版详情、发布页。同一版本自动提示一次，仍可手动查看。
- 按 major/minor/patch 数值比较，仅接受带可选 v/V 前缀的三段正式版本。远端较旧、相同版本均不提示降级；草稿和预发布不作为正式版。
- 弹窗显示安装版本、远端版本和原始发行说明。APK 需已上传、大小大于零，名称为 MurasameRTSS-版本号.apk，下载 URL 必须精确属于该仓库、对应版本标签。
- 用户点击后才通过浏览器下载，安装由系统确认。无可用 APK 时提供发布页；不静默下载或安装。
- 缓存成功获取的公开版本信息，网络失败保留已知版本详情，但手动检查会显示本次错误，不用缓存冒充新检查成功。
- 联网在工作线程执行，连接及读取超时各 8 秒，响应限 256 KiB，正文读取带额外时限，说明最多展示 12000 字符。拒绝接口重定向；无 GitHub 账号、令牌或遥测上传。
- 开关、检查时间、缓存和提醒状态独立保存在 updates 偏好中，不改监控记录。新增普通 INTERNET 权限。
- 五语言文案及 1.4.0 历史更新已生成。

## 已验证

- 本地 10000 项断言通过：基础 5374、历史 338、本地化 3832、应用耗电 408、更新规则 48。
- 新增更新规则测试覆盖数值排序、同版本、本地较新、无效/预发布标签、APK 来源和路径限制、24 小时/1 小时间隔、时钟回拨、限流等待及异常头值。
- 378 条文案 × 5 种语言一致性检查通过。
- AAPT2 / AIDL / Java / DEX 编译、v3 签名、zipalign、git diff --check 均通过。
- 从本机发出与应用相同 Accept / API-Version / User-Agent 的匿名请求，公开接口正常返回正式版 v1.2.1，附带匹配的 APK。由于当前本地版为 1.4.0，正确结果应为无需更新。
- 构建产物包名 io.perfoverlay.app，versionName 1.4.0，versionCode 13，minSdk 29 / targetSdk 35；含 INTERNET 权限。
- 签名证书 SHA-256 与旧版一致：ac53c468b193ada4955a850acf60ec033dea86ffbe6cde7db3715d61dcbb5c22。

## 发布维护

后续需要创建正式 GitHub Release 并设为 Latest，标签如 v1.4.1，上传 MurasameRTSS-1.4.1.apk。仅推送源码或标签不触发本应用更新发现。包名、签名应保留，versionCode 必须递增；否则系统安装器可能拒绝覆盖安装。

接口约定参考：[GitHub Releases REST API](https://docs.github.com/en/rest/releases/releases#get-the-latest-release)。

## 产物

- dist/MurasameRTSS-1.4.0.apk（2221264 字节）
- SHA-256：7501f1e7bba18d177887847ce4d068450a2dd328219ed6342379c69860af4c67
- dist/SHA256SUMS-1.4.0.txt

## 实机边界

本次 ADB 未发现设备，没有安装到手机。Android 内部 JSON/网络处理、弹窗、语言切换时的生命周期、手机断网/限流以及浏览器下载尚未实机验证；本机匿名请求和纯 Java 策略测试不等同于手机端全流程测试。本次没有创建 GitHub Release。
