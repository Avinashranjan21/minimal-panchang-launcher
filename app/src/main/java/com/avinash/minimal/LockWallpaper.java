package com.avinash.minimal;

import android.app.WallpaperManager;
import android.app.job.JobInfo;
import android.app.job.JobParameters;
import android.app.job.JobScheduler;
import android.app.job.JobService;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Point;
import android.graphics.Typeface;
import android.hardware.display.DisplayManager;
import android.util.Log;
import android.view.Display;

/**
 * The lock screen stays the system one (PIN, clock, unlock). Its wallpaper is a black image with the Panchang
 * and sun/moon dial drawn below the clock, re-rendered every 15 minutes and whenever the screen turns off.
 */
public final class LockWallpaper extends JobService {
    private static final String TAG = "MinimalLock";
    private static final int JOB_ID = 7001;
    private static final long MIN_GAP_MS = 3 * 60 * 1000;
    private static long lastSet;

    static void schedule(Context c) {
        JobScheduler js = c.getSystemService(JobScheduler.class);
        js.schedule(new JobInfo.Builder(JOB_ID, new ComponentName(c, LockWallpaper.class))
                .setPeriodic(15 * 60 * 1000)
                .setPersisted(true)
                .build());
    }

    /** Re-render off the main thread unless it was done very recently. */
    static void refreshAsync(Context c, boolean force) {
        Context app = c.getApplicationContext();
        if (!force && System.currentTimeMillis() - lastSet < MIN_GAP_MS) return;
        new Thread(() -> apply(app)).start();
    }

    static synchronized void apply(Context c) {
        try {
            Panchang p = Info.now(c);
            Point size = new Point();
            c.getSystemService(DisplayManager.class).getDisplay(Display.DEFAULT_DISPLAY).getRealSize(size);
            Bitmap bmp = render(c, p, size.x, size.y);
            WallpaperManager wm = WallpaperManager.getInstance(c);
            wm.setBitmap(bmp, null, true, WallpaperManager.FLAG_LOCK);
            if (!Info.prefs(c).getBoolean("homeBlack", false)) {
                Bitmap black = Bitmap.createBitmap(size.x, size.y, Bitmap.Config.RGB_565);
                black.eraseColor(Color.BLACK);
                wm.setBitmap(black, null, true, WallpaperManager.FLAG_SYSTEM);
                Info.prefs(c).edit().putBoolean("homeBlack", true).apply();
            }
            lastSet = System.currentTimeMillis();
        } catch (Exception e) {
            Log.w(TAG, "lock wallpaper failed", e);
        }
    }

    static Bitmap render(Context c, Panchang p, int w, int h) {
        float dp = c.getResources().getDisplayMetrics().density;
        Bitmap bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
        Canvas cv = new Canvas(bmp);
        Theme th = Theme.of(p);
        cv.drawColor(th.bg);

        Paint title = text(th.strong, 20 * dp, Typeface.create("sans-serif-light", Typeface.NORMAL));
        Paint body = text(th.dim, 13.5f * dp, Typeface.create("sans-serif", Typeface.NORMAL));
        float cx = w / 2f;
        // ColorOS draws its clock in roughly the top quarter; start under it.
        float y = h * 0.38f;
        cv.drawText(Info.title(p), cx, y, title);
        y += 30 * dp;
        for (String line : Info.panchangLines(p)) {
            cv.drawText(line, cx, y, body);
            y += 21 * dp;
        }

        float r = Math.min(w * 0.19f, 82 * dp);
        y += 14 * dp + r;
        SkyDial.draw(cv, cx, y, r, dp, p, th);

        y += r + 26 * dp;
        Paint sun = text(th.sun, 13 * dp, Typeface.DEFAULT), moon = text(th.moon, 13 * dp, Typeface.DEFAULT);
        sun.setTextAlign(Paint.Align.CENTER);
        String[] s = Info.sunLines(p), m = Info.moonLines(p);
        float colL = w * 0.28f, colR = w * 0.72f;
        for (int i = 0; i < Math.max(s.length, m.length); i++) {
            if (i < s.length) { sun.setAlpha(i == 0 ? 255 : 170); cv.drawText(s[i], colL, y, sun); }
            if (i < m.length) {
                moon.setAlpha(i == 0 ? 255 : 170);
                String t = m[i];
                if (i == m.length - 1 && i >= s.length) cv.drawText(t, cx, y, moon); else cv.drawText(t, colR, y, moon);
            }
            y += 19 * dp;
        }
        Paint tiny = text(th.faint, 10 * dp, Typeface.DEFAULT);
        cv.drawText("अद्यतन " + Info.time(p.when), cx, y + 6 * dp, tiny);
        return bmp;
    }

    private static Paint text(int color, float size, Typeface tf) {
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        p.setColor(color);
        p.setTextSize(size);
        p.setTypeface(tf);
        p.setTextAlign(Paint.Align.CENTER);
        return p;
    }

    @Override public boolean onStartJob(JobParameters params) {
        new Thread(() -> { apply(getApplicationContext()); jobFinished(params, false); }).start();
        return true;
    }

    @Override public boolean onStopJob(JobParameters params) { return true; }

    /** Re-arms the periodic job after reboot or an app update. */
    public static final class Boot extends BroadcastReceiver {
        @Override public void onReceive(Context c, Intent i) {
            schedule(c);
            refreshAsync(c, true);
        }
    }
}
