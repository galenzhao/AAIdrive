# 高德地图SDK本地文件配置

## 目录结构
```
app/libs/amap/
├── README.md
├── AMap3DMap_10.1.500_AMapNavi_10.1.500_AMapSearch_9.7.4_AMapLocation_6.5.0_20250814.jar  # 高德地图SDK合集
├── arm64-v8a/                                   # ARM64架构的native库
│   ├── libAMapSDK_NAVI_v10_1_500.so
│   ├── libneonui_shared.so
│   └── libneonuijni_public.so
└── armeabi-v7a/                                 # ARMv7架构的native库
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
将下载的jar/aar文件直接放置在此目录下即可。

## build.gradle配置
项目已配置为自动引用此目录下的所有jar和aar文件：
```gradle
amapImplementation fileTree(dir: 'libs/amap', include: ['*.jar', '*.aar'])
```

## 注意事项
1. 确保SDK版本配套，避免冲突
2. 如果使用aar文件，需要确保包含所有依赖
3. 可能需要添加ProGuard规则来避免混淆
4. 记得在AndroidManifest.xml中配置API Key和权限
