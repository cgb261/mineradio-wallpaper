package com.cgb261.mrwall;

import android.graphics.Bitmap;
import android.graphics.Color;

/** 从封面图里挑一个偏亮、有色的主色，作为壁纸的强调色。 */
final class ColorPicker {

    private ColorPicker() {
    }

    static int pick(Bitmap src) {
        if (src == null || src.isRecycled()) {
            return 0xFF6E5CE6;
        }
        int fallback = 0xFF6E5CE6;
        try {
            int w = Math.min(src.getWidth(), 48);
            int h = Math.min(src.getHeight(), 48);
            if (w <= 0 || h <= 0) {
                return fallback;
            }
            Bitmap small = Bitmap.createScaledBitmap(src, w, h, false);
            long r = 0, g = 0, b = 0;
            long count = 0;
            float[] hsv = new float[3];
            for (int y = 0; y < h; y += 2) {
                for (int x = 0; x < w; x += 2) {
                    int c = small.getPixel(x, y);
                    Color.colorToHSV(c, hsv);
                    if (hsv[2] < 0.18f || hsv[1] < 0.12f) {
                        continue;
                    }
                    r += Color.red(c);
                    g += Color.green(c);
                    b += Color.blue(c);
                    count++;
                }
            }
            if (count == 0) {
                return fallback;
            }
            int rr = (int) (r / count);
            int gg = (int) (g / count);
            int bb = (int) (b / count);
            int avg = Color.rgb(rr, gg, bb);
            Color.colorToHSV(avg, hsv);
            hsv[1] = Math.max(0.45f, Math.min(0.9f, hsv[1] * 1.15f));
            hsv[2] = Math.max(0.55f, Math.min(0.95f, hsv[2] * 1.1f));
            if (small != src) {
                small.recycle();
            }
            return Color.HSVToColor(hsv);
        } catch (Throwable t) {
            return fallback;
        }
    }
}
