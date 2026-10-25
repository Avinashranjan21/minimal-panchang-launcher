package com.avinash.minimal;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.DashPathEffect;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.util.AttributeSet;
import android.view.View;

/**
 * Horizon dial seen from above: north up, east right, centre = zenith, rim = horizon.
 * Today's sun and moon tracks are drawn faintly, their current positions as dots.
 * The N-S line doubles as the vertical for Earth's axis, drawn through the centre at its true tilt.
 */
final class SkyDial extends View {
    private Panchang p;

    SkyDial(Context c) { super(c); }
    SkyDial(Context c, AttributeSet a) { super(c, a); }

    void set(Panchang p) { this.p = p; invalidate(); }

    @Override protected void onMeasure(int w, int h) {
        int s = MeasureSpec.getSize(w);
        setMeasuredDimension(s, s);
    }

    @Override protected void onDraw(Canvas c) {
        if (p != null) draw(c, getWidth() / 2f, getHeight() / 2f, getWidth() / 2f, getResources().getDisplayMetrics().density, p, Theme.of(p));
    }

    /** Draws the dial centred at cx,cy; size is the outer radius including compass labels. */
    static void draw(Canvas c, float cx, float cy, float size, float dp, Panchang p, Theme th) {
        float R = size - 14 * dp;
        Paint line = new Paint(Paint.ANTI_ALIAS_FLAG);
        line.setStyle(Paint.Style.STROKE);
        line.setColor(th.line);
        line.setStrokeWidth(1 * dp);
        c.drawCircle(cx, cy, R, line);
        line.setPathEffect(new DashPathEffect(new float[]{2 * dp, 4 * dp}, 0));
        c.drawCircle(cx, cy, R * 2 / 3, line);
        c.drawCircle(cx, cy, R / 3, line);
        c.drawLine(cx - R, cy, cx + R, cy, line);
        c.drawLine(cx, cy - R, cx, cy + R, line);

        Paint lab = new Paint(Paint.ANTI_ALIAS_FLAG);
        lab.setColor(th.dim);
        lab.setTextSize(10 * dp);
        lab.setTextAlign(Paint.Align.CENTER);
        float off = lab.getTextSize() / 3;
        c.drawText("N", cx, cy - R - 4 * dp, lab);
        c.drawText("S", cx, cy + R + 4 * dp + lab.getTextSize() * 0.8f, lab);
        c.drawText("E", cx + R + 7 * dp, cy + off, lab);
        c.drawText("W", cx - R - 7 * dp, cy + off, lab);

        // Earth's axis against the vertical, with the tilt angle.
        double eps = p.obliquity * Astro.DEG;
        float ax = (float) (R * Math.sin(eps)), ay = (float) (R * Math.cos(eps));
        Paint axis = new Paint(Paint.ANTI_ALIAS_FLAG);
        axis.setColor(th.strong);
        axis.setAlpha(150);
        axis.setStrokeWidth(1.2f * dp);
        c.drawLine(cx - ax, cy + ay, cx + ax, cy - ay, axis);
        float ar = R * 0.42f;
        Paint arc = new Paint(Paint.ANTI_ALIAS_FLAG);
        arc.setStyle(Paint.Style.STROKE);
        arc.setStrokeWidth(1.2f * dp);
        arc.setColor(th.sun);
        c.drawArc(new RectF(cx - ar, cy - ar, cx + ar, cy + ar), -90, (float) p.obliquity, false, arc);
        Paint tilt = new Paint(Paint.ANTI_ALIAS_FLAG);
        tilt.setColor(th.sun);
        tilt.setTextSize(9 * dp);
        tilt.setTextAlign(Paint.Align.LEFT);
        c.drawText(String.format(java.util.Locale.US, "%.2f°", p.obliquity), cx + ar * 0.55f, cy - ar - 2 * dp, tilt);

        drawPath(c, cx, cy, R, p.sunPath, th.sun, dp, false);
        drawPath(c, cx, cy, R, p.moonPath, th.moon, dp, true);

        float[] m = pos(cx, cy, R, p.moonAlt, p.moonAz);
        drawMoon(c, m[0], m[1], 6 * dp, p, th, p.moonAlt >= 0 ? 255 : 90);
        float[] s = pos(cx, cy, R, p.sunAlt, p.sunAz);
        Paint sun = new Paint(Paint.ANTI_ALIAS_FLAG);
        sun.setColor(th.sun);
        if (p.sunAlt < 0) {
            sun.setStyle(Paint.Style.STROKE);
            sun.setStrokeWidth(1.5f * dp);
            sun.setAlpha(140);
        }
        c.drawCircle(s[0], s[1], 7 * dp, sun);
    }

    /** Above the horizon: radius by altitude. Below: pinned to the rim at its azimuth. */
    static float[] pos(float cx, float cy, float R, double alt, double az) {
        double r = alt >= 0 ? R * (90 - alt) / 90 : R;
        return new float[]{(float) (cx + r * Math.sin(az * Astro.DEG)), (float) (cy - r * Math.cos(az * Astro.DEG))};
    }

    private static void drawPath(Canvas c, float cx, float cy, float R, double[][] pts, int color, float dp, boolean dashed) {
        Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        paint.setStyle(Paint.Style.STROKE);
        paint.setColor(color);
        paint.setAlpha(90);
        paint.setStrokeWidth(1.5f * dp);
        if (dashed) paint.setPathEffect(new DashPathEffect(new float[]{3 * dp, 3 * dp}, 0));
        Path path = new Path();
        boolean pen = false;
        for (double[] h : pts) {
            if (h[0] < 0) { pen = false; continue; }
            float[] q = pos(cx, cy, R, h[0], h[1]);
            if (pen) path.lineTo(q[0], q[1]); else path.moveTo(q[0], q[1]);
            pen = true;
        }
        c.drawPath(path, paint);
    }

    /** Moon disc with its lit fraction: right limb lit while waxing (as seen from India). */
    static void drawMoon(Canvas c, float x, float y, float r, Panchang p, Theme th, int alpha) {
        Paint dark = new Paint(Paint.ANTI_ALIAS_FLAG);
        dark.setColor(th.moonDark);
        dark.setAlpha(alpha);
        c.drawCircle(x, y, r, dark);
        Paint lit = new Paint(Paint.ANTI_ALIAS_FLAG);
        lit.setColor(th.moon);
        lit.setAlpha(alpha);
        double k = p.illumination;
        float ex = (float) (r * Math.abs(1 - 2 * k));
        Path path = new Path();
        RectF disc = new RectF(x - r, y - r, x + r, y + r);
        RectF ell = new RectF(x - ex, y - r, x + ex, y + r);
        boolean right = p.waxing;
        // Lit limb half-circle, then back along the terminator ellipse.
        path.arcTo(disc, -90, right ? 180 : -180);
        path.arcTo(ell, 90, (k > 0.5) == right ? 180 : -180);
        path.close();
        c.drawPath(path, lit);
    }
}
