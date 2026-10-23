package com.cgb261.mrwall;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.RadialGradient;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.Typeface;

import java.util.List;
import java.util.Random;

/**
 * 伪 3D 舞台：透视粒子云 + 封面光环 + 逐字高亮歌词。
 * 全部用 Canvas 绘制，能直接画进壁纸的 Surface。
 */
public class SceneRenderer {

    private static final int PARTICLES = 240;

    private final float[] px = new float[PARTICLES];
    private final float[] py = new float[PARTICLES];
    private final float[] pz = new float[PARTICLES];
    private final float[] pr = new float[PARTICLES];
    private final float[] ph = new float[PARTICLES];

    private final Paint bgPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint dotPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint coverPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint ringPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint curPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint nextPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint smallPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF tmpRect = new RectF();

    private int w = 1;
    private int h = 1;
    private float t = 0f;
    private float rot = 0f;
    private float energy = 0f;
    private int accentCur = 0xFF6E5CE6;
    private int accentTarget = 0xFF6E5CE6;
    private float accentMix = 1f;
    private String lastTitle = "";

    public SceneRenderer() {
        Random rnd = new Random(20261023L);
        for (int i = 0; i < PARTICLES; i++) {
            // 球壳内均匀撒点
            double u = rnd.nextDouble() * 2.0 - 1.0;
            double theta = rnd.nextDouble() * Math.PI * 2.0;
            double r = Math.cbrt(rnd.nextDouble());
            double s = Math.sqrt(Math.max(0.0, 1.0 - u * u));
            px[i] = (float) (r * s * Math.cos(theta));
            py[i] = (float) (r * u);
            pz[i] = (float) (r * s * Math.sin(theta));
            pr[i] = 0.35f + rnd.nextFloat() * 0.85f;
            ph[i] = rnd.nextFloat() * 6.283f;
        }
        Typeface tf = Typeface.create("sans-serif-medium", Typeface.NORMAL);
        curPaint.setTypeface(tf);
        curPaint.setTextAlign(Paint.Align.CENTER);
        nextPaint.setTypeface(tf);
        nextPaint.setTextAlign(Paint.Align.CENTER);
        smallPaint.setTypeface(Typeface.create("sans-serif", Typeface.NORMAL));
        smallPaint.setTextAlign(Paint.Align.CENTER);
    }

    public void onResume() {
        t = 0f;
    }

    public void onPause() {
        // 无状态需要清理
    }

    public void draw(Canvas canvas, float dt) {
        w = Math.max(1, canvas.getWidth());
        h = Math.max(1, canvas.getHeight());
        t += dt;

        accentTarget = PlayerHolder.accent;
        if (accentCur != accentTarget) {
            accentCur = blend(accentCur, accentTarget, Math.min(1f, dt * 1.6f));
        }

        String title = PlayerHolder.title;
        if (!title.equals(lastTitle)) {
            lastTitle = title;
            energy = Math.max(energy, 0.85f);
        }
        energy = Math.max(0f, energy - dt * 0.55f);
        float pulse = 0.5f + 0.5f * (float) Math.sin(t * 2.1f);
        float live = PlayerHolder.playing ? 1f : 0.25f;
        float amp = 0.35f + 0.65f * live;

        drawBackground(canvas, pulse * 0.35f * amp + energy * 0.25f);

        rot += dt * (0.12f + 0.35f * live + energy * 0.2f);
        float cos = (float) Math.cos(rot);
        float sin = (float) Math.sin(rot);
        float cx = w * 0.5f;
        float cy = h * 0.5f;

        dotPaint.setColor(accentCur);
        for (int i = 0; i < PARTICLES; i++) {
            float X = px[i] * cos - pz[i] * sin;
            float Z = px[i] * sin + pz[i] * cos;
            float persp = 1.9f / (1.9f + Z + 1.1f);
            if (persp <= 0.05f) {
                continue;
            }
            float sx = cx + X * persp * w * 0.62f;
            float sy = cy + py[i] * persp * h * 0.42f
                    + (float) Math.sin(t * 0.7f + ph[i]) * 14f * persp;
            float radius = pr[i] * persp * 2.6f;
            int alpha = (int) (255 * Math.min(1f, persp * 0.95f) * (0.25f + 0.75f * amp));
            dotPaint.setAlpha(Math.max(8, Math.min(230, alpha)));
            canvas.drawCircle(sx, sy, radius, dotPaint);
        }

        drawCoverRing(canvas, cx, cy - h * 0.16f, pulse * live);
        drawLyrics(canvas, cx);
    }

    private void drawBackground(Canvas canvas, float glow) {
        int dark = blend(0xFF05050A, accentCur, 0.10f + 0.14f * glow);
        bgPaint.setShader(new LinearGradient(0, 0, 0, h, 0xFF05050A, dark, Shader.TileMode.CLAMP));
        canvas.drawRect(0, 0, w, h, bgPaint);

        int halo = blend(0xFF05050A, accentCur, 0.55f);
        halo = (halo & 0x00FFFFFF) | 0x55000000;
        RadialGradient rg = new RadialGradient(w * 0.5f, h * 0.42f, Math.max(w, h) * 0.62f,
                halo, 0x00000000, Shader.TileMode.CLAMP);
        bgPaint.setShader(rg);
        canvas.drawRect(0, 0, w, h, bgPaint);
        bgPaint.setShader(null);
    }

    private void drawCoverRing(Canvas canvas, float cx, float cy, float pulse) {
        float radius = Math.min(w, h) * 0.105f;
        Bitmap cover = PlayerHolder.cover;
        float scale = 1f + pulse * 0.05f;
        float r = radius * scale;

        int glowColor = (accentCur & 0x00FFFFFF) | 0x44000000;
        ringPaint.setStyle(Paint.Style.STROKE);
        ringPaint.setStrokeWidth(Math.max(2f, r * 0.05f));
        ringPaint.setColor(glowColor);
        canvas.drawCircle(cx, cy, r * 1.18f, ringPaint);
        ringPaint.setColor((accentCur & 0x00FFFFFF) | 0xAA000000);
        canvas.drawCircle(cx, cy, r, ringPaint);

        if (cover != null && !cover.isRecycled()) {
            tmpRect.set(cx - r * 0.92f, cy - r * 0.92f, cx + r * 0.92f, cy + r * 0.92f);
            coverPaint.setAlpha(235);
            canvas.drawBitmap(cover, null, tmpRect, coverPaint);
        } else {
            coverPaint.setStyle(Paint.Style.FILL);
            coverPaint.setColor((accentCur & 0x00FFFFFF) | 0x33000000);
            canvas.drawCircle(cx, cy, r * 0.92f, coverPaint);
        }
    }

    private void drawLyrics(Canvas canvas, float cx) {
        List<PlayerHolder.Line> lines = PlayerHolder.lines;
        String title = PlayerHolder.title;

        float big = Math.min(w, h) * 0.058f;
        float mid = big * 0.62f;

        if (title.isEmpty()) {
            smallPaint.setColor(0x88FFFFFF);
            smallPaint.setTextSize(big * 0.5f);
            canvas.drawText("等待 Mineradio 播放…", cx, h * 0.62f, smallPaint);
            return;
        }

        int index = -1;
        long pos = PlayerHolder.positionNow();
        if (lines != null && !lines.isEmpty()) {
            for (int i = 0; i < lines.size(); i++) {
                if (lines.get(i).timeMs <= pos) {
                    index = i;
                } else {
                    break;
                }
            }
        }

        if (index < 0) {
            // 还没到第一句：显示歌名
            smallPaint.setColor(0xCCFFFFFF);
            smallPaint.setTextSize(big * 0.6f);
            canvas.drawText(title, cx, h * 0.62f, smallPaint);
            if (!PlayerHolder.artist.isEmpty()) {
                smallPaint.setColor(0x88FFFFFF);
                smallPaint.setTextSize(mid * 0.7f);
                canvas.drawText(PlayerHolder.artist, cx, h * 0.62f + big * 0.9f, smallPaint);
            }
            return;
        }

        PlayerHolder.Line cur = lines.get(index);
        long start = cur.timeMs;
        long end = index + 1 < lines.size() ? lines.get(index + 1).timeMs : start + 4200L;
        if (end <= start) {
            end = start + 3000L;
        }
        float progress = (pos - start) / (float) (end - start);
        progress = Math.max(0f, Math.min(1f, progress));

        // 上一句
        if (index > 0) {
            nextPaint.setColor(0x55FFFFFF);
            nextPaint.setTextSize(mid);
            canvas.drawText(lines.get(index - 1).text, cx, h * 0.66f - big * 1.45f, nextPaint);
        }

        // 当前句：先画暗底，再按进度裁出亮色
        curPaint.setTextSize(big);
        curPaint.setColor(0x59FFFFFF);
        float baseY = h * 0.68f;
        canvas.drawText(cur.text, cx, baseY, curPaint);

        float tw = curPaint.measureText(cur.text);
        float left = cx - tw * 0.5f;
        canvas.save();
        canvas.clipRect(left, baseY - big * 1.4f, left + tw * progress, baseY + big * 0.5f);
        curPaint.setColor(0xFFFFFFFF);
        canvas.drawText(cur.text, cx, baseY, curPaint);
        canvas.restore();

        // 下一句
        if (index + 1 < lines.size()) {
            nextPaint.setColor(0x66FFFFFF);
            nextPaint.setTextSize(mid);
            canvas.drawText(lines.get(index + 1).text, cx, baseY + big * 1.55f, nextPaint);
        }

        // 左下角：歌名 · 歌手
        smallPaint.setTextSize(mid * 0.55f);
        smallPaint.setColor(0x77FFFFFF);
        smallPaint.setTextAlign(Paint.Align.LEFT);
        String meta = PlayerHolder.artist.isEmpty() ? title : title + "  ·  " + PlayerHolder.artist;
        canvas.drawText(meta, w * 0.06f, h * 0.94f, smallPaint);
        smallPaint.setTextAlign(Paint.Align.CENTER);
    }

    private static int blend(int from, int to, float f) {
        f = Math.max(0f, Math.min(1f, f));
        int a = (int) (Color.alpha(from) + (Color.alpha(to) - Color.alpha(from)) * f);
        int r = (int) (Color.red(from) + (Color.red(to) - Color.red(from)) * f);
        int g = (int) (Color.green(from) + (Color.green(to) - Color.green(from)) * f);
        int b = (int) (Color.blue(from) + (Color.blue(to) - Color.blue(from)) * f);
        return Color.argb(a, r, g, b);
    }
}
