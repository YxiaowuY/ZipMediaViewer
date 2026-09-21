# 压缩包媒体查看器（ZipMediaViewer）

一款 Android 应用：**不解压**即可浏览手机内 ZIP / RAR 压缩包中的图片与视频。

- Kotlin + 原生 Android（View 体系）
- 图片：流式直读、双击/双指缩放、左右滑动切换、横竖屏自适应、全屏沉浸
- 视频：ExoPlayer 播放（从压缩包抽取到本地缓存后播放）
- 排序切换（名称/大小/时间）、筛选（全部/仅图片/仅视频）
- 历史记录（Room 数据库）、选择预览文件夹批量打开

---

## 一、功能清单

| 功能 | 说明 |
| --- | --- |
| 不解压查看 | 图片从压缩包内直接读取字节解码，无需整体解压到磁盘 |
| 支持格式 | ZIP、RAR（RAR 使用纯 Java 的 junrar，逐条解压） |
| 图片缩放 | 双击放大/缩小，双指捏合缩放，可平移 |
| 滑动切换 | 图片浏览页左右滑动在压缩包内图片间切换 |
| 横竖屏 | 全局开启传感器自动旋转，界面自适应 |
| 全屏自适应 | 图片/视频页进入沉浸全屏（隐藏状态栏与导航栏），旋转正确填充 |
| 排序 | 按名称 / 大小 / 修改时间 三种方式循环切换 |
| 筛选 | 全部媒体 / 仅图片 / 仅视频 循环切换 |
| 历史记录 | 记录打开过的压缩包，一键重开、单条删除、全部清空 |
| 选择预览文件夹 | 选择一个文件夹，自动扫描其中所有 zip/rar，选择后打开 |
| 视频快进快退 | 播放控制条提供快退/快进按钮（每次 10 秒） |
| 视频倍速 | 支持 0.5x~5x 多档倍速切换（含 1.25/1.5/2/3/4/5x），最高 5 倍 |
| 手动清理缓存 | 主页“清理缓存”仅清除临时视频，保留压缩包副本与历史记录 |

---

## 二、目录结构

```
ZipMediaViewer/
├── settings.gradle.kts
├── build.gradle.kts
├── gradle.properties
├── gradle/
│   ├── libs.versions.toml          # 依赖版本统一管理
│   └── wrapper/gradle-wrapper.properties
└── app/
    ├── build.gradle.kts
    └── src/main/
        ├── AndroidManifest.xml
        ├── java/com/example/zipmedia/
        │   ├── MainActivity.kt                  # 主界面（打开/文件夹/历史）
        │   ├── ArchiveBrowserActivity.kt        # 压缩包内媒体列表页
        │   ├── ImageViewerActivity.kt           # 图片查看页（缩放/滑动/沉浸）
        │   ├── VideoPlayerActivity.kt           # 视频播放页
        │   ├── data/                            # 数据层
        │   │   ├── ArchiveEntry.kt              # 条目模型（Parcelable）
        │   │   ├── ArchiveReader.kt             # 读取接口
        │   │   ├── ZipArchiveReader.kt          # ZIP 读取
        │   │   ├── RarArchiveReader.kt          # RAR 读取（junrar）
        │   │   ├── ArchiveLoader.kt             # 打开/复制缓存/分发
        │   │   ├── History*/.kt                 # Room 历史记录
        │   ├── ui/                              # 适配器
        │   │   ├── MediaAdapter.kt
        │   │   └── HistoryAdapter.kt
        │   └── util/                            # 工具
        │       ├── CacheUtils.kt / Prefs.kt / Immersive.kt
        │       ├── ThumbnailLoader.kt / StorageUtils.kt / Extras.kt
        └── res/                                 # 布局/主题/字符串/图标
```

---

## 三、如何运行 / 打包 APK

### 环境要求
- **Android Studio**（Hedgehog 或更新版本，自带正确版本的 JDK）
- 无需预先安装命令行工具

### 步骤
1. 用 **Android Studio** 打开本项目根目录 `ZipMediaViewer`：
   `File → Open... → 选择 ZipMediaViewer 文件夹 → OK`
2. 首次打开会自动 **Sync（同步）**。若提示下载 Gradle 8.9 / 依赖，点同意即可。
3. 若提示 **Gradle wrapper 缺失/无法识别**：重新点击菜单 `File → Sync Project with Gradle Files`，
   Android Studio 会基于 `gradle/wrapper/gradle-wrapper.properties` 自动生成 wrapper 并下载 Gradle 8.9。
4. 顶部选择一台设备（或模拟器）后点击绿色 **▶ Run**；或：
   `Build → Build APK(s) → Build APK(s)`，生成后可在 `app/build/outputs/apk/debug/` 找到 `app-debug.apk`，传到手机安装。
5. 若手机是 Android 10 以下且要访问内存卡，请在 App 提示时允许“存储”权限；
   Android 10+ 会自动采用系统文件选择器（无需存储权限）。

### 最小 / 目标系统
- minSdk 24（Android 7.0）
- targetSdk 34

---

## 四、使用说明

1. **打开压缩包**：主界面点“打开压缩包”，通过系统文件选择器选一个 zip/rar 文件。
2. **选择预览文件夹**：点“选择预览文件夹”，选一个文件夹，App 自动扫描其中所有压缩包供选择。
3. **浏览**：进入后以网格展示媒体；顶部可循环切换“排序”和“筛选”。
4. **看图**：点图片进入查看页：
   - 双击 / 双指缩放，拖动平移
   - 左右滑动切换上一张 / 下一张
   - 单击切换顶栏与系统栏显隐；旋转手机横竖屏自适应
5. **看视频**：点视频进入播放页（会先抽取到本地缓存，稍等片刻自动播放）：
   - 播放器自带快退 / 快进按钮（每次 10 秒，可拖动进度条）
   - 左上角“速度 xx”可循环切换倍速（0.5x～5x，最高 5 倍）
   - 支持全屏与控制条；单击切换系统栏显隐
6. **历史记录**：主界面列出最近打开过的压缩包，点击重开；用右侧删除图标删除；右上“清空”可全部清除。
7. **清理缓存**：主页“清理缓存”会显示当前临时视频占用的空间，确认后清除（**仅清除临时视频，压缩包与历史记录保留**）。

---

## 五、说明与限制
- RAR 由于格式限制，无法像 ZIP 那样随机按需读取单个条目，图片/视频均按“抽取该条目”的方式读取，大文件会稍慢。
- 图片查看为整图读取到内存再分区解码，单张过大（约 >80MB）时会跳过并提示，避免内存溢出。
- 为支持“历史记录重开”，打开的压缩包会复制一份到应用缓存目录；卸载 App 或系统清缓存后历史显示“缓存文件不存在”，重新选择即可。
- 视频播放完毕后会自动删除抽取的临时文件，避免占用存储。

---

## 六、主要依赖
| 库 | 用途 | 版本 |
| --- | --- | --- |
| AndroidX Core / AppCompat / Material / Lifecycle | 基础 UI 与生命周期 | 见 `libs.versions.toml` |
| Room | 历史记录数据库 | 2.6.1 |
| junrar | RAR 解析 | 7.5.5 |
| Media3 (ExoPlayer) | 视频播放 | 1.4.1 |
| subsampling-scale-image-view | 图片缩放 | 3.10.0 |
| Kotlinx Coroutines | 异步 | 1.8.1 |