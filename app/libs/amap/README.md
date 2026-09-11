# 高德地图 SDK（Maven）

amap flavor：车机投影导航（`AMapNaviView` + Presentation）。不做手机导航组件、不做货车。

依赖：`navi-3dmap-location-search:11.2.100_...`，仅 arm64-v8a。

连接车机时会 `AmapSdkBootstrap.warmUpNavi`，并在 inflate `AMapNaviView` 前再次 warm-up，减轻 native logger `UnsatisfiedLinkError`。warm-up 可能在监听器挂上前就完成 init，投影侧会把已有 `AMapNavi` 实例视为 ready，避免 `Holding route` / 2s 假重试。车机投影强制 `setSensorEnable(false)`（朝向靠 CDS extra GPS，勿改 true）。Inputtips 常无 `poiID` 时会用周边关键词 `PoiSearch` 补全。

## 配置项来源

以 **Maven SDK jar 的 public API**（`AMapNavi` / `AMapNaviView` / `AMapNaviViewOptions`）为准梳理；你贴的开放平台文档作交叉核对。  
文档未写、但 SDK 有 setter 的项（如三维路口、拥堵高亮、锁图延迟、红绿灯 HUD 等）也已做成 App 配置。

刻意不暴露：自定义 Bitmap/Rect、网约车、货车、手机设置面板等与车机无关的 API。

## 实时生效？

车机地图打开后，`AppSettings` 变更会经 `AppSettingsObserver` → `AmapNaviController.applySettings` → `AmapNaviProjection.applySettings`：

| 类型 | 何时生效 |
|------|----------|
| HUD / 图层 / 视角 / 昼夜 / 语音开关等 | **实时**（地图已打开时） |
| 车牌、限行、`AMapCarInfo` | **实时写入引擎**；限行路线要 **下次算路** 才改轨迹 |
| 策略 checkbox / 显式策略 ID / 多路线 | **下次算路** |
| 选路开关、模拟时速 | **下次开始导航** |
| 渲染倍率 `AMAP_RENDER_SCALE` | **重新连接车机**（改 VirtualDisplay） |

## App 设置页

手机 **地图 → 高德导航** 分组。Key：`AndroidAutoIdrive_AmapApiKey`。
