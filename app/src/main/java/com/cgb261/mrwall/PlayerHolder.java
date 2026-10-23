package com.cgb261.mrwall;

import android.graphics.Bitmap;

import java.util.ArrayList;
import java.util.List;

/**
 * 进程内共享的播放状态。壁纸服务与通知监听服务同进程，直接读写即可。
 */
public final class PlayerHolder {

    public static final class Line {
        public final long timeMs;
        public final String text;

        public Line(long timeMs, String text) {
            this.timeMs = timeMs;
            this.text = text;
        }
    }

    public static volatile String title = "";
    public static volatile String artist = "";
    public static volatile long durationMs = 0L;
    public static volatile boolean playing = false;
    public static volatile int accent = 0xFF6E5CE6;
    public static volatile Bitmap cover = null;
    public static volatile List<Line> lines = new ArrayList<>();
    public static volatile String lyricKey = "";
    public static volatile boolean lyricLoading = false;
    public static volatile boolean hasSession = false;

    private static volatile long positionMs = 0L;
    private static volatile long positionSetAt = 0L;

    private PlayerHolder() {
    }

    public static synchronized void setPosition(long pos, boolean isPlaying) {
        positionMs = Math.max(0L, pos);
        playing = isPlaying;
        positionSetAt = System.currentTimeMillis();
    }

    public static long positionNow() {
        long p = positionMs;
        if (playing) {
            p += System.currentTimeMillis() - positionSetAt;
        }
        if (durationMs > 0L && p > durationMs) {
            p = durationMs;
        }
        return Math.max(0L, p);
    }

    public static void reset() {
        title = "";
        artist = "";
        durationMs = 0L;
        playing = false;
        cover = null;
        lines = new ArrayList<>();
        lyricKey = "";
        lyricLoading = false;
        hasSession = false;
        setPosition(0L, false);
    }
}
