# 高德地图SDK本地文件配置

## 架构说明
项目现在完全使用高德地图导航SDK (`com.amap.api.navi`) 实现地图显示和导航功能：
- **AmapNaviController**: 导航控制器，管理导航逻辑
- **AmapNaviProjection**: 导航视图投影，使用AMapNaviView显示地图
- **AmapController**: 主控制器，协调各个组件

## 目录结构
```
app/
├── libs/amap/
│   ├── README.md
│   └── AMap3DMap_10.1.500_AMapNavi_10.1.500_AMapSearch_9.7.4_AMapLocation_6.5.0_20250814.jar  # 高德地图SDK合集
└── src/amap/jniLibs/                            # Native库，只打包进 amap 版本
    ├── arm64-v8a/                               # ARM64架构的native库
    │   ├── libAMapSDK_NAVI_v10_1_500.so
    │   ├── libneonui_shared.so
    │   └── libneonuijni_public.so
    └── armeabi-v7a/                             # ARMv7架构的native库
        ├── libAMapSDK_NAVI_v10_1_500.so
        ├── libneonui_shared.so
        └── libneonuijni_public.so
```

## 如何获取SDK文件

### 1. 从高德开放平台下载
- 访问：https://lbs.amap.com/
- 注册开发者账号
- 创建应用获取API Key
- 下载对应版本的SDK

### 2. 需要的SDK文件
根据官方配套版本信息：
- **定位SDK**: V6.5.0 (com.amap.api.location)
- **搜索SDK**: V9.7.4 (com.amap.api.search)  
- **3D地图SDK**: V10.1.500 (com.amap.api.map3d)
- **导航SDK**: V10.1.500 (com.amap.api.navi)

### 3. 文件放置
将下载的jar/aar文件直接放置在此目录下即可，.so 文件放到 `src/amap/jniLibs/<abi>/`。

## build.gradle配置
`filterAmapSdkJars` 任务会把此目录下的 jar/aar 复制一份，并去掉 SDK 自带的
`com/autonavi/base/ae/gmap/glyph/ReflectUtil.class`（改用 `src/amap/java` 里的静音版本），再作为
`amapImplementation` 依赖引用。更换 SDK 版本后，需要确认 ReflectUtil 的方法签名没有变化。

## Native库配置
Gradle 会自动读取 flavor 自己的 `src/amap/jniLibs`，不需要额外配置。
不要放到 `src/main/jniLibs`，否则 gmap / mapbox / nomap 的 APK 也会带上这些 .so（arm64 约 65MB）。

## 功能特性
- ✅ **完整导航功能**: 使用AMapNaviView提供专业的导航界面
- ⚠️ **语音播报**: SDK 内置 TTS 已关闭，它会让进程崩溃
- ✅ **路线规划**: 支持多种路线策略，可在车机上选择路线
- ✅ **实时导航**: 使用车机 GPS（通过 setExtraGPSData 输入）导航，没有车机 GPS 时使用模拟导航
- ✅ **地图显示**: 3D地图显示，支持多种地图样式
- ✅ **本地文件**: 使用本地SDK文件，无需网络下载

## 坐标系
- 应用内部的车辆位置统一是 WGS-84；如果车机输出的是 GCJ-02，打开设置里的"车机 GPS 输出的是 GCJ-02 坐标"，会先转换回 WGS-84。
- 交给高德的车辆位置会统一转换成 GCJ-02。
- 目的地来自高德搜索或从高德搜索保存的收藏，本身就是 GCJ-02，不再转换。

## 注意事项
1. 确保SDK版本配套，避免冲突
2. 如果使用aar文件，需要确保包含所有依赖
3. 可能需要添加ProGuard规则来避免混淆
4. 记得在AndroidManifest.xml中配置API Key和权限
5. **重要**: Native库文件(.so)必须放在`src/amap/jniLibs`目录下，而不是`libs`目录
6. 确保包含所有架构的native库文件（arm64-v8a, armeabi-v7a等）
7. 如果遇到"UnsatisfiedLinkError"错误，检查native库文件是否正确放置
8. **架构简化**: 已删除旧的RouteSearch实现，现在完全使用AMapNaviView
