package com.cgb261.mrwall;

import android.content.ComponentName;
import android.content.Context;
import android.graphics.Bitmap;
import android.media.MediaMetadata;
import android.media.session.MediaController;
import android.media.session.MediaSessionManager;
import android.media.session.PlaybackState;
import android.text.TextUtils;

import java.util.List;

/**
 * 直接轮询系统媒体会话。不依赖 NotificationListenerService 是否被系统连上
 * ——ColorOS 上那个服务经常不回调，但通知使用权能让我们直接读 controller。
 */
public final class MediaPoller {

    private static long lastTick = 0L;
    private static MediaSessionManager manager;
    private static long lastPosition = -1L;

    private MediaPoller() {
    }

    public static void tick(Context ctx) {
        long now = System.currentTimeMillis();
        if (now - lastTick < 700L) {
            return;
        }
        lastTick = now;
        try {
            if (manager == null) {
                manager = (MediaSessionManager) ctx.getSystemService(Context.MEDIA_SESSION_SERVICE);
            }
            if (manager == null) {
                return;
            }
            ComponentName cn = new ComponentName(ctx, NotifListener.class);
            List<MediaController> list = manager.getActiveSessions(cn);
            if (list == null || list.isEmpty()) {
                return;
            }
            MediaController picked = null;
            for (MediaController c : list) {
                PlaybackState st = c.getPlaybackState();
                if (st != null && st.getState() == PlaybackState.STATE_PLAYING) {
                    picked = c;
                    break;
                }
            }
            if (picked == null) {
                picked = list.get(0);
            }
            apply(picked);
        } catch (Throwable ignored) {
            // 没授权时会抛 SecurityException，交给界面提示
        }
    }

    private static void apply(MediaController c) {
        PlaybackState st = c.getPlaybackState();
        if (st != null) {
            long pos = st.getPosition();
            boolean playing = st.getState() == PlaybackState.STATE_PLAYING;
            // 播放器通常只在事件发生时刷新位置，跳变明显时才重置基准，避免时间轴卡住
            if (!playing || lastPosition < 0L || Math.abs(pos - lastPosition) > 1200L) {
                PlayerHolder.setPosition(pos, playing);
                lastPosition = pos;
            } else {
                PlayerHolder.playing = playing;
            }
        }

        MediaMetadata md = c.getMetadata();
        if (md == null) {
            return;
        }
        String title = text(md.getText(MediaMetadata.METADATA_KEY_TITLE));
        String artist = text(md.getText(MediaMetadata.METADATA_KEY_ARTIST));
        if (artist.isEmpty()) {
            artist = text(md.getText(MediaMetadata.METADATA_KEY_ALBUM_ARTIST));
        }
        long duration = md.getLong(MediaMetadata.METADATA_KEY_DURATION);
        if (duration > 0L) {
            PlayerHolder.durationMs = duration;
        }

        boolean changed = !TextUtils.equals(title, PlayerHolder.title)
                || !TextUtils.equals(artist, PlayerHolder.artist);
        PlayerHolder.title = title;
        PlayerHolder.artist = artist;
        PlayerHolder.hasSession = !title.isEmpty();

        if (PlayerHolder.cover == null) {
            Bitmap art = md.getBitmap(MediaMetadata.METADATA_KEY_ALBUM_ART);
            if (art == null) {
                art = md.getBitmap(MediaMetadata.METADATA_KEY_ART);
            }
            if (art != null && !art.isRecycled()) {
                PlayerHolder.cover = art;
                PlayerHolder.accent = ColorPicker.pick(art);
            }
        }

        if (changed && !title.isEmpty()) {
            LyricFetcher.request(title, artist);
        }
    }

    private static String text(CharSequence cs) {
        return cs == null ? "" : cs.toString().trim();
    }
}
