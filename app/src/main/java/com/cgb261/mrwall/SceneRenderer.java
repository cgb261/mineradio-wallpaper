package com.cgb261.mrwall;

import android.graphics.Bitmap;
import android.graphics.Camera;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.RadialGradient;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.Typeface;

import java.util.List;
import java.util.Random;

/**
 * 伪 3D 舞台：
 *  - 粒子球壳绕 Y 轴旋转 + 透视投影
 *  - 歌词用 Camera 做真正的 3D 倾斜，当前句逐字高亮
 *  - 桌面左右滑动（onOffsetsChanged）会带动场景旋转和歌词转向
 */
public class SceneRenderer {

    private static final int PARTICLES = 260;

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
    private final Paint sidePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint smallPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF tmpRect = new RectF();

    private final Camera camera = new Camera();
    private final Matrix matrix = new Matrix();

    private int w = 1;
    private int h = 1;
    private float t = 0f;
    private float rot = 0f;
    private float energy = 0f;
    private int accentCur = 0xFF6E5CE6;
    private int accentTarget = 0xFF6E5CE6;
    private String lastTitle = "";

    private float scrollRaw = 0.5f;
    private float scroll = 0.5f;

    private int lineIndex = -1;
    private long lineChangedAt = 0L;

    public SceneRenderer() {
        Random rnd = new Random(20261023L);
        for (int i = 0; i < PARTICLES; i++) {
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
        sidePaint.setTypeface(tf);
        sidePaint.setTextAlign(Paint.Align.CENTER);
        smallPaint.setTypeface(Typeface.create("sans-serif", Typeface.NORMAL));
        smallPaint.setTextAlign(Paint.Align.CENTER);
    }

    public void onResume() {
        t = 0f;
    }

    public void onPause() {
    }

    /** 桌面滑动时由 WallpaperService 回调，0..1。 */
    public void setScroll(float xOffset, float yOffset) {
        scrollRaw = Math.max(0f, Math.min(1f, xOffset));
    }

    public void draw(Canvas canvas, float dt) {
        w = Math.max(1, canvas.getWidth());
        h = Math.max(1, canvas.getHeight());
        t += dt;

        scroll += (scrollRaw - scroll) * Math.min(1f, dt * 4.5f);
        float parallax = scroll - 0.5f;

        accentTarget = PlayerHolder.accent;
        if (accentCur != accentTarget) {
            accentCur = blend(accentCur, accentTarget, Math.min(1f, dt * 1.6f));
        }

        String title = PlayerHolder.title;
        if (!title.equals(lastTitle)) {
            lastTitle = title;
            energy = Math.max(energy, 0.9f);
        }
        energy = Math.max(0f, energy - dt * 0.55f);
        float pulse = 0.5f + 0.5f * (float) Math.sin(t * 2.1f);
        float live = PlayerHolder.playing ? 1f : 0.3f;
        float amp = 0.35f + 0.65f * live;

        drawBackground(canvas, parallax, pulse * 0.35f * amp + energy * 0.25f);

        rot += dt * (0.12f + 0.35f * live + energy * 0.2f);
        float spin = rot + parallax * 2.4f;
        float cos = (float) Math.cos(spin);
        float sin = (float) Math.sin(spin);
        float cx = w * 0.5f + parallax * w * 0.06f;
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

        float ringY = cy - h * 0.19f + Math.abs(parallax) * h * 0.02f;
        drawCoverRing(canvas, cx, ringY, pulse * live);
        drawLyrics(canvas, parallax);
    }

    private void drawBackground(Canvas canvas, float parallax, float glow) {
        int dark = blend(0xFF05050A, accentCur, 0.10f + 0.14f * glow);
        bgPaint.setShader(new LinearGradient(0, 0, 0, h, 0xFF05050A, dark, Shader.TileMode.CLAMP));
        canvas.drawRect(0, 0, w, h, bgPaint);

        int halo = blend(0xFF05050A, accentCur, 0.55f);
        halo = (halo & 0x00FFFFFF) | 0x55000000;
        float hx = w * (0.5f + parallax * 0.12f);
        RadialGradient rg = new RadialGradient(hx, h * 0.42f, Math.max(w, h) * 0.62f,
                halo, 0x00000000, Shader.TileMode.CLAMP);
        bgPaint.setShader(rg);
        canvas.drawRect(0, 0, w, h, bgPaint);
        bgPaint.setShader(null);
    }

    private void drawCoverRing(Canvas canvas, float cx, float cy, float pulse) {
        float radius = Math.min(w, h) * 0.105f;
        Bitmap cover = PlayerHolder.cover;
        float r = radius * (1f + pulse * 0.05f);

        ringPaint.setStyle(Paint.Style.STROKE);
        ringPaint.setStrokeWidth(Math.max(2f, r * 0.05f));
        ringPaint.setColor((accentCur & 0x00FFFFFF) | 0x44000000);
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

    private void drawLyrics(Canvas canvas, float parallax) {
        List<PlayerHolder.Line> lines = PlayerHolder.lines;
        String title = PlayerHolder.title;
        float big = Math.min(w, h) * 0.062f;
        float baseY = h * 0.70f;

        if (title.isEmpty()) {
            smallPaint.setColor(0x88FFFFFF);
            smallPaint.setTextSize(big * 0.5f);
            canvas.drawText("等待 Mineradio 播放…", w * 0.5f, baseY, smallPaint);
            return;
        }

        long pos = PlayerHolder.positionNow();
        int index = -1;
        if (lines != null && !lines.isEmpty()) {
            for (int i = 0; i < lines.size(); i++) {
                if (lines.get(i).timeMs <= pos) {
                    index = i;
                } else {
                    break;
                }
            }
        }
        if (index != lineIndex) {
            lineIndex = index;
            lineChangedAt = System.currentTimeMillis();
        }
        float enter = Math.min(1f, (System.currentTimeMillis() - lineChangedAt) / 340f);
        float ease = 1f - (float) Math.pow(1f - enter, 3f);

        float rotY = parallax * 30f;
        float rotX = 7f + Math.abs(parallax) * 4f;

        if (index < 0) {
            draw3dText(canvas, title, w * 0.5f, baseY - (1f - ease) * big * 0.5f,
                    big * 0.62f, 220, rotX, rotY, 1f);
            if (!PlayerHolder.artist.isEmpty()) {
                draw3dText(canvas, PlayerHolder.artist, w * 0.5f, baseY + big * 0.95f,
                        big * 0.34f, 140, rotX, rotY, 1f);
            }
            drawMeta(canvas, title);
            return;
        }

        PlayerHolder.Line cur = lines.get(index);
        long start = cur.timeMs;
        long end = index + 1 < lines.size() ? lines.get(index + 1).timeMs : start + 4200L;
        if (end <= start) {
            end = start + 3000L;
        }
        float progress = Math.max(0f, Math.min(1f, (pos - start) / (float) (end - start)));

        // 远处：上两句、下一句，越远越小越淡
        for (int d = -2; d <= 2; d++) {
            if (d == 0) {
                continue;
            }
            int i = index + d;
            if (i < 0 || i >= lines.size()) {
                continue;
            }
            float dist = Math.abs(d);
            float scale = 1f - dist * 0.16f;
            float y = baseY + d * big * (d > 0 ? 1.25f : 1.05f);
            int alpha = (int) (150 * (1f - dist * 0.32f));
            draw3dText(canvas, lines.get(i).text, w * 0.5f, y,
                    big * scale, alpha, rotX, rotY * (1f - dist * 0.25f), 1f);
        }

        // 当前句：从下方滑入 + 逐字点亮
        float cyCur = baseY + (1f - ease) * big * 1.1f;
        draw3dText(canvas, cur.text, w * 0.5f, cyCur, big, 255, rotX, rotY, progress);

        drawMeta(canvas, title);
    }

    /**
     * 用 Camera 做 3D 倾斜后绘制一行字；highlight 0..1 表示从左到右点亮。
     */
    private void draw3dText(Canvas canvas, String text, float cx, float baseline,
                            float size, int alpha, float rotX, float rotY, float highlight) {
        if (text == null || text.isEmpty()) {
            return;
        }
        Paint paint = alpha >= 200 ? curPaint : sidePaint;
        paint.setTextSize(size);
        paint.setColor((alpha << 24) | 0x00FFFFFF);

        matrix.reset();
        camera.save();
        camera.rotateX(rotX);
        camera.rotateY(rotY);
        camera.getMatrix(matrix);
        camera.restore();
        matrix.preTranslate(-cx, -baseline);
        matrix.postTranslate(cx, baseline);

        canvas.save();
        canvas.concat(matrix);
        canvas.drawText(text, cx, baseline, paint);

        if (highlight < 1f && highlight > 0f) {
            float tw = paint.measureText(text);
            float left = cx - tw * 0.5f;
            canvas.save();
            canvas.clipRect(left, baseline - size * 1.5f, left + tw * highlight, baseline + size * 0.6f);
            paint.setColor(0xFFFFFFFF);
            canvas.drawText(text, cx, baseline, paint);
            canvas.restore();
        } else if (highlight >= 1f) {
            paint.setColor(0xEEFFFFFF);
            canvas.drawText(text, cx, baseline, paint);
        }
        canvas.restore();
    }

    private void drawMeta(Canvas canvas, String title) {
        float size = Math.min(w, h) * 0.026f;
        smallPaint.setTextSize(size);
        smallPaint.setColor(0x88FFFFFF);
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
