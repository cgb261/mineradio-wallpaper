package com.cgb261.mrwall;

import android.graphics.Canvas;
import android.os.Handler;
import android.os.HandlerThread;
import android.service.wallpaper.WallpaperService;
import android.view.SurfaceHolder;

/** 壁纸服务：一个独立线程按 ~60fps 往 Surface 上画。 */
public class WallpaperSvc extends WallpaperService {

    @Override
    public Engine onCreateEngine() {
        return new WpEngine();
    }

    private final class WpEngine extends Engine {

        private final SceneRenderer renderer = new SceneRenderer();
        private HandlerThread thread;
        private Handler handler;
        private boolean visible = false;
        private long lastFrame = 0L;

        private final Runnable frame = new Runnable() {
            @Override
            public void run() {
                if (!visible) {
                    return;
                }
                MediaPoller.tick(WallpaperSvc.this);
                long now = System.currentTimeMillis();
                float dt = lastFrame == 0L ? 0.016f : (now - lastFrame) / 1000f;
                lastFrame = now;
                if (dt > 0.1f) {
                    dt = 0.1f;
                }

                SurfaceHolder holder = getSurfaceHolder();
                Canvas canvas = null;
                try {
                    canvas = holder.lockCanvas();
                    if (canvas != null) {
                        renderer.draw(canvas, dt);
                    } else {
                        android.util.Log.w("MRWALL", "lockCanvas returned null, visible=" + visible
                                + " surfaceValid=" + holder.getSurface().isValid());
                    }
                } catch (Throwable t) {
                    android.util.Log.w("MRWALL", "draw failed", t);
                } finally {
                    if (canvas != null) {
                        try {
                            holder.unlockCanvasAndPost(canvas);
                        } catch (Throwable ignored) {
                        }
                    }
                }
                schedule();
            }
        };

        private void schedule() {
            if (!visible || handler == null) {
                return;
            }
            handler.postDelayed(frame, 16L);
        }

        @Override
        public void onCreate(SurfaceHolder holder) {
            super.onCreate(holder);
            thread = new HandlerThread("mrwall-render");
            thread.start();
            handler = new Handler(thread.getLooper());
        }

        @Override
        public void onVisibilityChanged(boolean isVisible) {
            visible = isVisible;
            android.util.Log.i("MRWALL", "onVisibilityChanged visible=" + isVisible);
            if (handler == null) {
                return;
            }
            handler.removeCallbacks(frame);
            if (isVisible) {
                lastFrame = 0L;
                renderer.onResume();
                handler.post(frame);
            } else {
                renderer.onPause();
            }
        }

        @Override
        public void onOffsetsChanged(float xOffset, float yOffset, float xOffsetStep,
                                     float yOffsetStep, int xPixelOffset, int yPixelOffset) {
            renderer.setScroll(xOffset, yOffset);
        }

        @Override
        public void onSurfaceChanged(SurfaceHolder holder, int format, int width, int height) {
            super.onSurfaceChanged(holder, format, width, height);
            android.util.Log.i("MRWALL", "onSurfaceChanged " + width + "x" + height);
        }

        @Override
        public void onDestroy() {
            super.onDestroy();
            visible = false;
            if (handler != null) {
                handler.removeCallbacksAndMessages(null);
            }
            if (thread != null) {
                thread.quitSafely();
            }
        }
    }
}
