# 灵动岛 · Dynamic Island for Android

把 iPhone 的「灵动岛」搬到了 Android：一个常驻屏幕顶部的黑色胶囊，系统来通知时自动变宽、展开显示内容。

纯 Android 系统 API 实现，**零第三方依赖**（不引 AndroidX、不引 Material），所以包体很小、构建很稳。

## 功能

- 悬浮胶囊：`TYPE_APPLICATION_OVERLAY` 常驻屏幕顶部，退出 App 也不消失
- 通知联动：监听系统通知，自动展开显示「应用名 / 标题 / 正文」，4.5 秒后自动收起
- 展开动画：宽度高度由一个 `ValueAnimator` 驱动，带定制 `PathInterpolator`，撑开时有回弹感
- 手势：拖动可移动位置，单击展开 / 收起，往上甩一下关闭
- 配色：微信、QQ、抖音、小红书等常见应用用固定强调色，其他应用按包名散列出一个稳定色相
- 页内预览：App 首页就是实时预览，点胶囊即可看动画，不用先开权限

## 安装

到仓库的 **Actions** 页面，点开最新一次 `Build APK` 运行记录，在页面底部的 **Artifacts** 里下载 `DynamicIsland-apk`，解压得到 `DynamicIsland.apk`，传到手机安装即可。

或者打一个 tag（如 `v1.0`），APK 会同时挂到 **Releases** 里，手机上直接点链接就能下。

## 使用步骤

1. 打开 App，点「① 授予悬浮窗权限」，在系统设置里允许「显示在其他应用上层」
2. 点「② 开启通知使用权」，在列表里勾选「灵动岛」
3. 点「③ 开启灵动岛」，胶囊出现，然后就可以退回桌面
4. 随便来一条通知（比如让别人给你发条微信），胶囊会展开显示内容

## 工程结构

```
android/
├── app/src/main/
│   ├── java/com/dynamicisland/app/
│   │   ├── MainActivity.kt                 # 权限引导 + 实时预览
│   │   ├── IslandService.kt                # 前台服务，挂悬浮窗
│   │   ├── IslandView.kt                   # 自绘胶囊 + 动画 + 手势
│   │   ├── IslandNotificationListener.kt   # 通知监听
│   │   └── IslandContent.kt                # 数据模型与同进程总线
│   ├── res/                                # 布局、主题、图标
│   └── AndroidManifest.xml
├── build.gradle.kts                        # AGP 8.6.1 + Kotlin 2.0.21
└── settings.gradle.kts
```

## 构建

CI 里由 `.github/workflows/build.yml` 自动完成（JDK 17 + SDK 35 + Gradle 8.9）。本地构建：

```bash
cd android
gradle :app:assembleRelease
```

产物在 `android/app/build/outputs/apk/release/`。

> Release 包统一用 debug 密钥签名，只为方便侧载安装；要上架应用商店请换成自己的签名。
