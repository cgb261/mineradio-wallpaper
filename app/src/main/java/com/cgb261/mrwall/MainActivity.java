package com.cgb261.mrwall;

import android.app.Activity;
import android.app.WallpaperManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.os.Bundle;
import android.provider.Settings;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;

public class MainActivity extends Activity {

    private TextView status;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        int pad = (int) (24 * getResources().getDisplayMetrics().density);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(0xFF0A0A12);
        root.setPadding(pad, pad * 2, pad, pad);
        root.setGravity(Gravity.CENTER_HORIZONTAL);

        TextView title = new TextView(this);
        title.setText("MR 歌词壁纸");
        title.setTextColor(Color.WHITE);
        title.setTextSize(24);
        title.setGravity(Gravity.CENTER);
        root.addView(title, lp(0, pad / 2));

        TextView desc = new TextView(this);
        desc.setText("把 Mineradio 正在播放的歌词做成桌面背景。\n1) 先开通知使用权；2) 再设为动态壁纸。");
        desc.setTextColor(0xFF9A9AAA);
        desc.setTextSize(14);
        desc.setGravity(Gravity.CENTER);
        desc.setLineSpacing(0, 1.35f);
        root.addView(desc, lp(0, pad));

        Button notifyBtn = new Button(this);
        notifyBtn.setText("① 打开通知使用权设置");
        notifyBtn.setOnClickListener(v -> {
            try {
                startActivity(new Intent(
                        "android.settings.ACTION_NOTIFICATION_LISTENER_SETTINGS"));
            } catch (Throwable t) {
                startActivity(new Intent(Settings.ACTION_SETTINGS));
            }
        });
        root.addView(notifyBtn, lp(0, pad / 2));

        Button wallpaperBtn = new Button(this);
        wallpaperBtn.setText("② 设置为本壁纸");
        wallpaperBtn.setOnClickListener(v -> openWallpaperPicker());
        root.addView(wallpaperBtn, lp(0, pad / 2));

        status = new TextView(this);
        status.setTextColor(0xFF7FE2B0);
        status.setTextSize(13);
        status.setGravity(Gravity.CENTER);
        root.addView(status, lp(0, pad));

        setContentView(root);

        try {
            android.service.notification.NotificationListenerService.requestRebind(
                    new ComponentName(this, NotifListener.class));
        } catch (Throwable ignored) {
        }
    }

    private void openWallpaperPicker() {
        ComponentName cn = new ComponentName(this, WallpaperSvc.class);
        try {
            Intent i = new Intent("android.service.wallpaper.CHANGE_LIVE_WALLPAPER");
            i.putExtra("android.service.wallpaper.extra.LIVE_WALLPAPER_COMPONENT", cn);
            startActivity(i);
            return;
        } catch (Throwable ignored) {
        }
        try {
            Intent i = new Intent(WallpaperManager.ACTION_CHANGE_LIVE_WALLPAPER);
            i.putExtra(WallpaperManager.EXTRA_LIVE_WALLPAPER_COMPONENT, cn);
            startActivity(i);
            return;
        } catch (Throwable ignored) {
        }
        try {
            startActivity(new Intent(WallpaperManager.ACTION_LIVE_WALLPAPER_CHOOSER));
        } catch (Throwable t) {
            status.setText("系统没有开放动态壁纸选择器，请在 设置 → 壁纸 里手动选择「MR 歌词壁纸」");
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (status == null) {
            return;
        }
        boolean granted = isNotificationAccessGranted();
        MediaPoller.tick(this);
        StringBuilder sb = new StringBuilder();
        sb.append(granted ? "通知使用权：已开启" : "通知使用权：未开启（收不到播放信息）");
        if (granted) {
            String t = PlayerHolder.title;
            sb.append("\n当前：").append(TextUtils.isEmpty(t) ? "还没读到播放中的歌曲" : t);
            sb.append("\n").append(probeSessions());
        }
        status.setText(sb.toString());
    }

    /** 直接探测一次系统媒体会话，把结果写到界面上，方便定位读不到歌的原因。 */
    private String probeSessions() {
        try {
            android.media.session.MediaSessionManager msm =
                    (android.media.session.MediaSessionManager)
                            getSystemService(android.content.Context.MEDIA_SESSION_SERVICE);
            ComponentName cn = new ComponentName(this, NotifListener.class);
            java.util.List<android.media.session.MediaController> list = msm.getActiveSessions(cn);
            if (list == null || list.isEmpty()) {
                return "媒体会话：0 个";
            }
            android.media.session.MediaController c = list.get(0);
            String t = "";
            if (c.getMetadata() != null) {
                CharSequence cs = c.getMetadata().getText(
                        android.media.MediaMetadata.METADATA_KEY_TITLE);
                t = cs == null ? "" : cs.toString();
            }
            return "媒体会话：" + list.size() + " 个 · " + c.getPackageName()
                    + (t.isEmpty() ? "" : " · " + t);
        } catch (Throwable e) {
            return "媒体会话读取失败：" + e.getClass().getSimpleName();
        }
    }

    private boolean isNotificationAccessGranted() {
        try {
            String flat = Settings.Secure.getString(getContentResolver(),
                    "enabled_notification_listeners");
            if (flat == null) {
                return false;
            }
            return flat.contains(getPackageName());
        } catch (Throwable t) {
            return false;
        }
    }

    private LinearLayout.LayoutParams lp(int width, int bottomMargin) {
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(
                width == 0 ? ViewGroup.LayoutParams.MATCH_PARENT : width,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        p.bottomMargin = bottomMargin;
        return p;
    }
}
