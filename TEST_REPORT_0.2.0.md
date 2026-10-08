# PerfOverlay 0.2.0 验证记录

日期：2026-10-07（Asia/Shanghai）

## 构建

- 版本 0.2.0 / versionCode 2，同一开发证书，支持覆盖旧版本。
- SDK 工具链编译成功，APK v3 签名验证与 zipalign 检查通过。
- APK：`dist/PerfOverlay-0.2.0.apk`。
- SHA-256：`82cec072de00878122399166f0318ddcae7ffc69861f4f16efad5e4c3eeb2def`。
- 核心逻辑测试通过，共 5374 次断言（包括随机布局断言，不是独立实机测试数量）。

新增测试覆盖：1/2/30/60/90/120/144/240 FPS 计算、帧间隔、时间戳去重、pending/无效时间戳、旧帧不冻结 FPS、Android 16 图层列表格式、前台应用识别、自身浮窗排除、整机功耗外接电源限制、反向电流绝对值、非温度 thermal 节点排除。

## 实机环境

- 型号 24122RKC7G，设备代号 miro，SoC SM8750。
- Android 16 / API 36，HyperOS OS3.0.302.0.WOMMIXM。
- ADB 与 Shizuku 均为 shell 权限（UID 2000），未使用 Root。
- 覆盖安装成功，保留用户字号 9 sp、黑底不透明度 25% 等配置。

## 实测结果

| 项目 | 证据 / 结果 |
| --- | --- |
| Shizuku 连接 | 升级后服务报告“Shizuku 采集” |
| CPU 拓扑 | policy6 包含核心 6–7，最高 4.32 GHz；policy0 包含核心 0–5，最高 3.5328 GHz |
| GPU 读取 | `gpuclk`、`devfreq/cur_freq` 返回 Permission denied；普通应用和 shell 都受到限制 |
| 30 FPS | 独立 UID SurfaceView 测试画面，浮窗服务报告 FPS=30.0，FRAME_TIME=33.3 ms |
| 60 FPS | 同一测试画面切换至 60，报告 FPS=60.0，FRAME_TIME=16.7 ms |
| 非游戏低帧率 | shell 探针观察普通应用时能报告约 2 FPS，不误报为屏幕刷新率 |
| 跨 UID 覆盖点击 | 浮窗范围 x=47,y=121,width=986,height=65；在 (400,150) 注入点击，底层测试应用 taps 从 0 增至 1 |
| 外接电源功耗保护 | 实机处于 AC powered=true、status=2；整机功耗显示 `--` 并说明原因，没有把净电池功率作为整机值 |
| 电流符号 | 观察到充电状态下 CURRENT_NOW 为负数；实现未接电源放电方向校准，校准前仅显示电池功率绝对值 |

FPS 通过新后端从 SurfaceFlinger 读取真实图层时间戳，不通过屏幕 Hz、浮窗刷新率或测试目标设置直接生成结果。

## 限制与未测项目

- 本轮没有指定的真实游戏画面，使用独立测试应用验证了呈现 FPS；不同游戏、多 Surface、分屏和插帧兼容性仍需继续验证。
- 未为测试修改或模拟电池状态。本轮连接充电状态，未实测断开电源后的整机功耗数值与方向校准；换算与门控已通过单元测试。
- 点击穿透验证使用 ADB 注入点击，尚未覆盖手指多点操作和全部游戏。
- GPU 频率读取被系统拒绝，未解锁该权限；CPU/GPU 独立功耗仍未适配。
- 未进行长时间耗电、负载或稳定性评测。
- 临时测试 APK 与 /data/local/tmp 下的探针文件已清理；未修改系统安全策略。
