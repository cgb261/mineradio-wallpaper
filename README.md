# MR 歌词壁纸 (Mineradio Wallpaper)

把 Mineradio 正在播放的歌曲，做成**桌面动态壁纸**：3D 粒子舞台 + 逐字高亮歌词 + 封面光环，切歌时自动换主题色。

## 原理

- `WallpaperSvc`：`WallpaperService`，独立线程以约 60fps 直接往壁纸 Surface 上画。
- `SceneRenderer`：伪 3D 渲染。粒子在球壳内分布，绕 Y 轴旋转后做透视投影，z 决定大小与透明度；歌词按 LRC 时间轴逐字高亮。
- `NotifListener`：`NotificationListenerService`，通过系统 MediaSession 读取歌名、歌手、封面、播放进度。
- `LyricFetcher`：按歌名 + 歌手去公开歌词接口取 LRC，解析成时间轴并按播放位置对齐。
- `ColorPicker`：从专辑封面里取主色，作为壁纸强调色，切歌时平滑过渡。

## 使用

1. 安装 APK。
2. 打开 App，点「① 打开通知使用权设置」，把「MR 歌词壁纸」的开关打开。
3. 回到 App，点「② 设置为本壁纸」，在预览页点「设置壁纸」。
4. 用 Mineradio 播放任意歌曲，回到桌面即可看到歌词与粒子舞台。

## 说明

- 只读取媒体元数据，不上传任何个人数据；歌词请求只发送歌名与歌手。
- 壁纸层由系统单独调度，息屏或桌面不可见时会自动暂停绘制。
- 本项目为个人用的非官方实现，与 Mineradio 作者无关，不包含其任何代码或加密资源。
