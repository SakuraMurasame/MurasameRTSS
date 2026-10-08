# PerfOverlay 0.2.1 验证记录

日期：2026-10-07。设备：24122RKC7G / SM8750，Android 16 / HyperOS，ADB + Shizuku，无 Root。

## 修复

横向模式按屏幕安全区域测量全部指标，窗口采用内容实际宽度，黑底仍紧贴文字。窗口配置变化时重新计算，每次读数更新也重新测量。保留用户设置的字号、透明度、指标、排序与纵向模式。

旧版根窗口使用 WRAP_CONTENT。Android 的 ViewRootImpl 会先尝试较窄的首选对话框宽度，遇到能够自行换行的内容时可能直接接受该宽度；现改用按完整可用区域测量后的明确宽度。参考 [Android ViewRootImpl.measureHierarchy 源码](https://android.googlesource.com/platform/frameworks/base/+/refs/heads/master/core/java/android/view/ViewRootImpl.java)。另外注册浮窗上下文配置回调，避免仅依赖 Service 配置事件。

## 实机结果

使用独立 UID、无权限的临时 SurfaceView 测试应用，通过 Activity 方向参数切换横竖屏，未修改手机自动旋转设置。字号 9 sp、黑底不透明度 25%、整窗 alpha 0.79，覆盖升级后均保留。

| 场景 | 可用宽度 | 浮窗尺寸 | 结果 |
| --- | --- | --- | --- |
| 旧版 0.2.0 横屏 2400×1080 | 旧版未记录 | 986×65 | 复现两行，左右空间充足 |
| 0.2.1 横屏 | 2279 px | 1189×33 | 一行，所有已选项目平铺 |
| 切到竖屏 1080×2400 | 1080 px | 986×65 | 两行，内容确实超过可用宽度 |
| 再切回横屏 | 2279 px | 1189×33 | 恢复一行，无需重启监控 |

- 横屏浮窗位于 x=666、y=0，点击 (1000,20) 在黑底范围内，底层测试应用 taps 从 0 增至 1，跨应用触摸穿透通过。
- 测试期间应用 FPS 显示 60.0，说明布局更新后采集正常运行。
- 新版进程 AndroidRuntime 错误日志查询无输出。
- 测试应用和手机临时截图已移除，恢复到 PerfOverlay 设置页，监控继续运行。
- 实机横屏截图：`docs/screenshots/0.2.1-landscape.png`。

本次针对布局缺陷验证；未重测全部硬件传感器、充放电或所有机型。前一版相关结果见 `TEST_REPORT_0.2.0.md`。

## 构建与交付

- `build.ps1` 成功：Java / AIDL 编译、DEX、APK 签名与 zipalign 校验通过。
- `test.ps1` 通过 5374 次断言，包含单位、异常值、透明度和布局不变量。
- ADB 覆盖安装成功，设备报告 versionCode=3 / versionName=0.2.1。
- APK：`dist/PerfOverlay-0.2.1.apk`。
- SHA-256：`d6add1b01042944947470ad08c0b1cd7b5ed6d0f3140a8419779022b64edbf48`。
- 源码 ZIP 排除签名私钥、构建缓存和旧安装包。
