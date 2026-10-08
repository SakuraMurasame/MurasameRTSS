# PerfOverlay 0.1.0 验证记录

日期：2026-10-07（Asia/Shanghai）

## 构建与产物

- Windows 本地构建成功：Java 编译、AIDL 生成、AAPT2 资源编译/链接、D8 DEX 编译、APK 对齐和签名。
- 工具：Microsoft OpenJDK 21.0.8，Android Platform 35，Build Tools 35.0.0。
- 包名：`io.perfoverlay.app`；版本：`0.1.0` / `1`。
- 最低 Android API 29，目标 API 35。
- APK 文件：`dist/PerfOverlay-0.1.0.apk`，78,513 字节。
- SHA-256：`4745aa4ac719cd27d3d65a99438d4de56d65dee6f2e431cf3492ee12c1991245`。
- `apksigner verify --verbose`：通过，APK Signature Scheme v3，一名签名者。
- `zipalign -c -p 4`：通过。
- `aapt2 dump badging`：启动 Activity、最低/目标 SDK、悬浮窗权限、通知权限、specialUse 前台服务声明与 Shizuku 权限符合预期。
- 未声明网络、无障碍、录屏或存储访问权限。
- APK 包含 Shizuku 与 Apache-2.0 依赖许可证。

## 自动化逻辑测试

`test.ps1` 通过，合计 **5,329 次断言**，其中含 500 组随机布局用例；这不是 5,329 个独立测试或实机测试。

覆盖：

- CPU kHz → GHz、温度毫摄氏度 → 摄氏度、电池微安/毫伏 → 瓦。
- 不支持、无权限、NaN、无穷大、异常温度与电流哨兵值不会作为有效指标。
- 不猜测不明确的厂商温度单位。
- Android 12+ 使用设备触摸遮挡上限并预留余量。
- 背景 alpha 与窗口 alpha 合成后符合滑块请求值。
- 横屏单行、窄屏换行、纵向排列与黑底自适应宽度。
- 指标不重叠、不超出可用宽度、不在换行时静默丢弃。

## 代码检查与修正

- 浮窗始终包含 `FLAG_NOT_TOUCHABLE | FLAG_NOT_FOCUSABLE`，只使用一个监控窗口。
- Android 11+ 使用与悬浮窗类型匹配的可视窗口 Context。
- 超时清除旧值，避免无限显示已过期读数。
- Shizuku 采集在工作线程执行；用户服务只有固定节点的只读采样方法。
- Shizuku 断线后回退普通采集；连接等待超时后允许重连。
- 显式停止释放浮窗、采样线程、广播接收器和 Shizuku 用户服务。
- 陈旧通知动作不会在停止之后重新启动监控。
- Gradle 使用 namespace 配置；手动构建在临时 manifest 中注入 package，不修改源 manifest。

## 尚未验证

`adb devices -l` 未发现已连接设备，环境中没有可用 Android 模拟器。

以下项目尚未进行运行时验证：APK 在目标手机上的安装与启动、设置页实际渲染、横竖屏切换、真实游戏上的触摸穿透、Shizuku 授权与服务生命周期、目标手机节点访问、长时间稳定性和监控开销。应用内同 UID 测试页也不能代替跨应用穿透测试。

标准 Gradle 工程与 wrapper 已提供，但本次实际执行的是 SDK 工具构建脚本，没有执行 Gradle 构建或 Android Lint。

CPU/GPU 独立功耗和游戏 FPS 本版未实现采集后端，界面明确显示 `--`；它们不计为已通过测试的功能。
