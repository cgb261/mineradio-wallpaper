package com.cgb261.mrwall;

import android.os.Handler;
import android.os.Looper;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 按歌名 / 歌手去公开歌词接口取 LRC，解析成时间轴。
 */
public final class LyricFetcher {

    private static final Pattern TIME_TAG =
            Pattern.compile("\\[(\\d{1,3}):(\\d{1,2})(?:[.:](\\d{1,3}))?]");

    private static final Handler MAIN = new Handler(Looper.getMainLooper());

    private LyricFetcher() {
    }

    public static void request(String title, String artist) {
        final String t = title == null ? "" : title.trim();
        final String a = artist == null ? "" : artist.trim();
        if (t.isEmpty()) {
            return;
        }
        final String key = t + "|" + a;
        if (key.equals(PlayerHolder.lyricKey)) {
            return;
        }
        PlayerHolder.lyricKey = key;
        PlayerHolder.lyricLoading = true;
        PlayerHolder.lines = new ArrayList<>();

        Thread worker = new Thread(() -> {
            List<PlayerHolder.Line> parsed = new ArrayList<>();
            try {
                parsed = fetch(t, a);
            } catch (Throwable ignored) {
                // 网络失败也把 loading 关掉，后续切歌再重试
            }
            final List<PlayerHolder.Line> result = parsed;
            MAIN.post(() -> {
                if (key.equals(PlayerHolder.lyricKey)) {
                    PlayerHolder.lines = result;
                    PlayerHolder.lyricLoading = false;
                }
            });
        }, "mrwall-lyrics");
        worker.setDaemon(true);
        worker.start();
    }

    private static List<PlayerHolder.Line> fetch(String title, String artist) throws Exception {
        String q = title;
        if (!artist.isEmpty()) {
            q = title + " " + artist;
        }
        String searchUrl = "https://music.163.com/api/search/get/web?s="
                + URLEncoder.encode(q, "UTF-8") + "&type=1&limit=5";
        JSONObject root = new JSONObject(http(searchUrl));
        JSONObject result = root.optJSONObject("result");
        if (result == null) {
            return new ArrayList<>();
        }
        JSONArray songs = result.optJSONArray("songs");
        if (songs == null || songs.length() == 0) {
            return new ArrayList<>();
        }

        long id = 0L;
        for (int i = 0; i < songs.length(); i++) {
            JSONObject s = songs.optJSONObject(i);
            if (s == null) {
                continue;
            }
            long sid = s.optLong("id", 0L);
            if (sid == 0L) {
                continue;
            }
            String name = s.optString("name", "");
            if (name.equalsIgnoreCase(title)) {
                id = sid;
                break;
            }
            if (id == 0L) {
                id = sid;
            }
        }
        if (id == 0L) {
            return new ArrayList<>();
        }

        String lyricUrl = "https://music.163.com/api/song/lyric?id=" + id + "&lv=1&kv=1&tv=-1";
        JSONObject lyricRoot = new JSONObject(http(lyricUrl));
        JSONObject lrc = lyricRoot.optJSONObject("lrc");
        if (lrc == null) {
            return new ArrayList<>();
        }
        return parse(lrc.optString("lyric", ""));
    }

    private static String http(String urlStr) throws Exception {
        HttpURLConnection conn = null;
        try {
            conn = (HttpURLConnection) new URL(urlStr).openConnection();
            conn.setConnectTimeout(8000);
            conn.setReadTimeout(10000);
            conn.setRequestMethod("GET");
            conn.setRequestProperty("User-Agent",
                    "Mozilla/5.0 (Linux; Android 14; Pixel) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0 Mobile Safari/537.36");
            conn.setRequestProperty("Referer", "https://music.163.com/");
            conn.setRequestProperty("Cookie", "os=android; appver=8.0.0; osver=14;");
            conn.setRequestProperty("Accept", "application/json,text/plain,*/*");
            int code = conn.getResponseCode();
            InputStream in = code >= 200 && code < 300 ? conn.getInputStream() : conn.getErrorStream();
            if (in == null) {
                return "";
            }
            ByteArrayOutputStream bos = new ByteArrayOutputStream();
            byte[] buf = new byte[8192];
            int n;
            while ((n = in.read(buf)) > 0) {
                bos.write(buf, 0, n);
                if (bos.size() > 1024 * 1024) {
                    break;
                }
            }
            in.close();
            return bos.toString("UTF-8");
        } finally {
            if (conn != null) {
                conn.disconnect();
            }
        }
    }

    static List<PlayerHolder.Line> parse(String lrc) {
        List<PlayerHolder.Line> out = new ArrayList<>();
        if (lrc == null || lrc.isEmpty()) {
            return out;
        }
        String[] rows = lrc.split("\\r?\\n");
        for (String row : rows) {
            Matcher m = TIME_TAG.matcher(row);
            List<Long> stamps = new ArrayList<>();
            int lastEnd = 0;
            while (m.find()) {
                try {
                    int mm = Integer.parseInt(m.group(1));
                    int ss = Integer.parseInt(m.group(2));
                    String frac = m.group(3);
                    int ms = 0;
                    if (frac != null && !frac.isEmpty()) {
                        if (frac.length() == 1) {
                            ms = Integer.parseInt(frac) * 100;
                        } else if (frac.length() == 2) {
                            ms = Integer.parseInt(frac) * 10;
                        } else {
                            ms = Integer.parseInt(frac.substring(0, 3));
                        }
                    }
                    stamps.add(mm * 60000L + ss * 1000L + ms);
                    lastEnd = m.end();
                } catch (NumberFormatException ignored) {
                    // 忽略畸形时间标签
                }
            }
            if (stamps.isEmpty()) {
                continue;
            }
            String text = row.substring(lastEnd).trim();
            if (text.isEmpty()) {
                continue;
            }
            for (long stamp : stamps) {
                out.add(new PlayerHolder.Line(stamp, text));
            }
        }
        Collections.sort(out, (a, b) -> Long.compare(a.timeMs, b.timeMs));
        return out;
    }
}
