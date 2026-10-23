package com.cgb261.mrwall;

import android.content.ComponentName;
import android.content.Context;
import android.graphics.Bitmap;
import android.media.MediaMetadata;
import android.media.session.MediaController;
import android.media.session.MediaSessionManager;
import android.media.session.PlaybackState;
import android.service.notification.NotificationListenerService;
import android.text.TextUtils;

import java.util.List;

/**
 * 借通知使用权读取系统里的 MediaSession，拿到歌名、歌手、封面、进度。
 */
public class NotifListener extends NotificationListenerService
        implements MediaSessionManager.OnActiveSessionsChangedListener {

    private MediaSessionManager manager;
    private ComponentName component;
    private MediaController current;
    private MediaController.Callback callback;

    @Override
    public void onListenerConnected() {
        super.onListenerConnected();
        bind();
    }

    @Override
    public void onListenerDisconnected() {
        unbind();
        super.onListenerDisconnected();
    }

    private void bind() {
        if (manager == null) {
            manager = (MediaSessionManager) getSystemService(Context.MEDIA_SESSION_SERVICE);
        }
        if (component == null) {
            component = new ComponentName(this, NotifListener.class);
        }
        if (manager == null) {
            return;
        }
        try {
            manager.addOnActiveSessionsChangedListener(this, component);
            attach(manager.getActiveSessions(component));
        } catch (Throwable ignored) {
            // 没有授权时会抛 SecurityException
        }
    }

    private void unbind() {
        if (manager != null) {
            try {
                manager.removeOnActiveSessionsChangedListener(this);
            } catch (Throwable ignored) {
            }
        }
        detach();
    }

    @Override
    public void onActiveSessionsChanged(List<MediaController> controllers) {
        attach(controllers);
    }

    private void attach(List<MediaController> controllers) {
        MediaController picked = null;
        if (controllers != null) {
            for (MediaController c : controllers) {
                PlaybackState st = c.getPlaybackState();
                if (st != null && st.getState() == PlaybackState.STATE_PLAYING) {
                    picked = c;
                    break;
                }
            }
            if (picked == null && !controllers.isEmpty()) {
                picked = controllers.get(0);
            }
        }
        if (picked == null) {
            return;
        }
        if (current != null && TextUtils.equals(current.getPackageName(), picked.getPackageName())
                && TextUtils.equals(String.valueOf(current.getSessionToken()), String.valueOf(picked.getSessionToken()))) {
            apply(picked);
            return;
        }
        detach();
        current = picked;
        callback = new MediaController.Callback() {
            @Override
            public void onMetadataChanged(MediaMetadata metadata) {
                apply(current);
            }

            @Override
            public void onPlaybackStateChanged(PlaybackState state) {
                if (current == null) {
                    return;
                }
                if (state != null) {
                    PlayerHolder.setPosition(state.getPosition(),
                            state.getState() == PlaybackState.STATE_PLAYING);
                    if (state.getState() == PlaybackState.STATE_STOPPED) {
                        PlayerHolder.playing = false;
                    }
                }
                apply(current);
            }

            @Override
            public void onSessionDestroyed() {
                if (current != null) {
                    PlayerHolder.playing = false;
                    PlayerHolder.hasSession = false;
                }
            }
        };
        try {
            current.registerCallback(callback);
        } catch (Throwable ignored) {
        }
        apply(current);
    }

    private void detach() {
        if (current != null && callback != null) {
            try {
                current.unregisterCallback(callback);
            } catch (Throwable ignored) {
            }
        }
        current = null;
        callback = null;
    }

    private void apply(MediaController controller) {
        if (controller == null) {
            return;
        }
        MediaMetadata md = controller.getMetadata();
        PlaybackState st = controller.getPlaybackState();

        String title = "";
        String artist = "";
        long duration = 0L;
        Bitmap art = null;
        if (md != null) {
            title = text(md.getText(MediaMetadata.METADATA_KEY_TITLE));
            artist = text(md.getText(MediaMetadata.METADATA_KEY_ARTIST));
            if (artist.isEmpty()) {
                artist = text(md.getText(MediaMetadata.METADATA_KEY_ALBUM_ARTIST));
            }
            duration = md.getLong(MediaMetadata.METADATA_KEY_DURATION);
            art = md.getBitmap(MediaMetadata.METADATA_KEY_ALBUM_ART);
            if (art == null) {
                art = md.getBitmap(MediaMetadata.METADATA_KEY_ART);
            }
        }
        if (st != null) {
            PlayerHolder.setPosition(st.getPosition(), st.getState() == PlaybackState.STATE_PLAYING);
        }
        PlayerHolder.durationMs = duration;

        boolean changed = !TextUtils.equals(title, PlayerHolder.title)
                || !TextUtils.equals(artist, PlayerHolder.artist);
        PlayerHolder.title = title;
        PlayerHolder.artist = artist;
        PlayerHolder.hasSession = !TextUtils.isEmpty(title);

        if (art != null && art != PlayerHolder.cover) {
            PlayerHolder.cover = art;
            PlayerHolder.accent = ColorPicker.pick(art);
        }
        if (changed && !title.isEmpty()) {
            LyricFetcher.request(title, artist);
        }
    }

    private static String text(CharSequence cs) {
        return cs == null ? "" : cs.toString().trim();
    }
}
