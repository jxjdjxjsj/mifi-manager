# 随身 WiFi 管家

一个用于管理随身 WiFi / 移动路由器的 Android 应用。直接访问设备局域网管理页面，读取网络状态、流量、已连接设备等信息，并提供重启、关机、测速与外观设置。

- 包名：`com.admi.mifi`
- 最低系统：Android 8.0（API 26）
- 目标系统：Android 16（API 36）
- 技术栈：Kotlin、Jetpack Compose、Material 3、MaterialKolor、Haze

## 功能

### 概览

- 网络类型、运营商、Band、信号强度（dBm / 信号格）
- WAN IP、网络连接状态、电量与充电状态
- WiFi 名称与密码（支持显示/隐藏、复制）
- ICCID、IMEI、设备型号、硬件版本、软件版本、运行时长
- 已连接设备数量
- 本次流量、当月流量、实时上传/下载速度与趋势图
- 一键刷新、重启、关机

### 设备

- WiFi 热点信息卡片
- 已连接 / 曾连接客户端列表
- 客户端名称、IP、MAC、在线状态、信号和上下行用量
- 跳转设备管理后台

### 流量

- 当月流量环形占比（下载 / 上传）
- 本次使用与生命周期累计流量
- 上下行流量构成进度条
- 最近 36 次采样的实时速度趋势
- 设备未提供流量字段时，自动使用应用本地观测统计，并在界面标注

### 设置

- 管理地址、管理密码与接口协议
- 连接测试、打开管理页面
- MD3 Expressive / MiuiX 两种设计风格即时切换
- 深色模式：跟随系统 / 浅色 / 深色
- MD3 Expressive：色调、活力、表现力、中性、单色、柔和六种色彩风格
- MiuiX：Monet 动态取色（Android 12+）、强调色、五套调色盘
- MiuiX 液态玻璃悬浮底栏（真实背景模糊，Android 12 以下降级为半透明）
- 自动刷新开关
- 网络测速：打开 [南京大学测速网](https://test.nju.edu.cn/)
- 演示模式：无硬件时预览完整界面
- 复制诊断信息、恢复默认外观

所有页面共用同一个悬浮底栏：首页、设备、流量、设置。

## 构建

需要 JDK 17 与 Android SDK 36。

```bash
cd MifiManager
./gradlew :app:assembleDebug
```

生成的 APK：

```text
app/build/outputs/apk/debug/app-debug.apk
```

体积更小的 release 包（R8 代码压缩 + 资源压缩，默认使用 debug 签名便于直接安装）：

```bash
./gradlew :app:assembleRelease
```

```text
app/build/outputs/apk/release/app-release.apk
```

如果使用本机自带环境，可指定：

```bash
export JAVA_HOME=/path/to/jdk17
export ANDROID_HOME=/path/to/android-sdk
echo "sdk.dir=$ANDROID_HOME" > local.properties
./gradlew :app:assembleDebug
```

## 支持的设备与协议

应用会在自动模式下依次尝试两类常见接口：

### 1. ASR / UAPXB

- 状态：`/xml_action.cgi?method=get&module=duster&file=status1`
- 统计：`...&file=statistics`
- 设备列表 / 管理：`...&file=device_management`
- WiFi：`...&file=uapxb_wlan_security_settings`
- 自定义配置：`...&file=customconfig`
- 重启：`...&file=reset`
- 关机：`...&file=power_off`
- 登录：`/login.cgi`，支持 Digest 认证；登录响应会同时带入查询串与 `Authorization` 头，以兼容不同固件实现。

### 2. ZTE Goform / Reqproc

- `/goform/goform_get_cmd_process`
- `/goform/goform_set_cmd_process`
- `/reqproc/proc_get`
- `/reqproc/proc_post`
- 登录时密码按设备惯例进行 Base64 编码，并兼容部分设备要求的明文密码。

### 3. 演示模式

不发起任何网络请求，使用本地生成的 5G 设备数据，便于在模拟器或没有随身 WiFi 时查看界面。

管理接口通常为 HTTP 明文，因此应用允许局域网 HTTP 访问。密码仅保存在本机 DataStore 中，用于登录设备管理页面，不会上传到任何服务器。

## 数据与统计说明

- 实时速度优先使用设备提供的 `tx_rate` / `rx_rate`（字节/秒），否则根据累计字节计数器的差值除以采样时间计算。
- 当月流量优先使用设备上报的字段（如 ASR 的 `total_used_month`）。
- 设备没有当月流量字段时，应用按自身观测到的累计字节在本机记账，换月自动归零；界面会显示“本地统计”标记。
- 设备没有本次流量字段时，同样由应用按本次连接期间的采样累计。
- 部分设备不返回 WiFi 密码、ICCID 或 IMEI，此时对应字段显示为“—”。
- 重启 / 关机后设备通常会立刻断开连接，因此请求异常会被视为指令已送达。

## 外观实现

- 使用 MaterialKolor 的 HCT / DynamicScheme 生成 MD3 配色，避免依赖系统动态色。
- MD3 Expressive 风格使用更大的圆角、加粗的标题层级与胶囊式底栏选中态。
- MiuiX 风格使用柔和渐变背景、半透明卡片、白色高光描边和玻璃质感。
- 液态玻璃底栏通过 Haze 的 `hazeSource` / `hazeEffect` 对真实背景做模糊，并叠加渐变光斑、描边和高光。
- 色彩偏好、风格、Monet、调色盘、液态玻璃、刷新设置全部保存在本机。

## 项目结构

```text
app/src/main/java/com/admi/mifi/
├── MainActivity.kt
├── MifiApp.kt
├── data/
│   ├── Models.kt              # 数据模型
│   ├── Settings.kt            # DataStore 设置
│   ├── DeviceRepository.kt    # 协议识别、速度/流量统计
│   └── api/
│       ├── AsrApi.kt          # ASR / UAPXB
│       ├── GoformApi.kt       # ZTE Goform / Reqproc
│       ├── DemoApi.kt         # 演示数据
│       ├── HttpSupport.kt
│       ├── XmlTree.kt
│       └── DeviceApi.kt
├── ui/
│   ├── MifiViewModel.kt
│   ├── MainTab.kt
│   ├── components/            # 悬浮底栏、卡片、图表等
│   ├── screens/               # 首页、设备、流量、设置
│   └── theme/                 # 配色、字体、形状
└── util/                      # 格式化与字段解析
```

## 已知限制

- 不同厂商的字段命名差异很大，自动识别无法覆盖所有固件；可在设置中手动指定 ASR 或 Goform。
- 管理密码以明文形式保存在应用私有 DataStore 中（Android 沙盒保护），未做额外加密。
- 设备列表与流量信息依赖管理接口，可能存在数秒延迟。
- 液态玻璃在 Android 12 以下没有真实模糊，会显示半透明降级效果。

## 致谢

- [Jetpack Compose](https://developer.android.com/jetpack/compose)
- [MaterialKolor](https://github.com/jordond/materialkolor)
- [Haze](https://github.com/chrisbanes/haze)
- [OkHttp](https://square.github.io/okhttp/)
- [KernelSU-Style-UI-Kit](https://github.com/chenaizhang/KernelSU-Style-UI-Kit)
- [compose-miuix-ui / Miuix](https://github.com/compose-miuix-ui/miuix)

## 开源协议

本项目以 **GNU General Public License v3.0** 开源，完整协议见 [`LICENSE`](LICENSE)。

项目直接移植/改编了 [KernelSU-Style-UI-Kit](https://github.com/chenaizhang/KernelSU-Style-UI-Kit) 的部分 GPL-3.0 界面代码，因此整体按 GPL-3.0 分发。第三方代码来源与许可证说明见 [`NOTICE.md`](NOTICE.md) 和 [`THIRD_PARTY_LICENSES/`](THIRD_PARTY_LICENSES/)。
