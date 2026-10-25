package com.avinash.minimal;

import android.content.Context;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.location.Location;
import android.location.LocationManager;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.TimeZone;

/** Location, the current Panchang, and the text lines shown on both home and lock screens. */
final class Info {
    // Varanasi (Kashi): used until the phone has a location fix.
    static final double DEFAULT_LAT = 25.3176, DEFAULT_LON = 82.9739;

    private Info() {}

    static SharedPreferences prefs(Context c) { return c.getSharedPreferences("minimal", Context.MODE_PRIVATE); }

    static double[] location(Context c) {
        SharedPreferences sp = prefs(c);
        if (c.checkSelfPermission(android.Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
            LocationManager lm = c.getSystemService(LocationManager.class);
            Location best = null;
            for (String prov : lm.getProviders(true)) {
                try {
                    Location l = lm.getLastKnownLocation(prov);
                    if (l != null && (best == null || l.getTime() > best.getTime())) best = l;
                } catch (SecurityException ignored) { }
            }
            if (best != null) {
                sp.edit().putFloat("lat", (float) best.getLatitude()).putFloat("lon", (float) best.getLongitude()).apply();
            }
        }
        return new double[]{sp.getFloat("lat", (float) DEFAULT_LAT), sp.getFloat("lon", (float) DEFAULT_LON)};
    }

    /** Asks for a fresh fix (network/fused, no GPS warm-up) and stores it; done() runs on the main thread. */
    static void requestFresh(Context c, Runnable done) {
        if (c.checkSelfPermission(android.Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) return;
        LocationManager lm = c.getSystemService(LocationManager.class);
        String prov = lm.isProviderEnabled(LocationManager.FUSED_PROVIDER) ? LocationManager.FUSED_PROVIDER
                : lm.isProviderEnabled(LocationManager.NETWORK_PROVIDER) ? LocationManager.NETWORK_PROVIDER : null;
        if (prov == null) return;
        try {
            lm.getCurrentLocation(prov, null, c.getMainExecutor(), l -> {
                if (l == null) return;
                prefs(c).edit().putFloat("lat", (float) l.getLatitude()).putFloat("lon", (float) l.getLongitude()).apply();
                done.run();
            });
        } catch (SecurityException | IllegalArgumentException ignored) { }
    }

    static Panchang now(Context c) {
        double[] ll = location(c);
        return new Panchang(System.currentTimeMillis(), ll[0], ll[1], TimeZone.getDefault());
    }

    static String time(long ms) {
        return ms == 0 ? "—" : new SimpleDateFormat("HH:mm", Locale.US).format(new Date(ms));
    }

    /** End time, prefixed with "कल" when it falls on a later day than the panchang instant. */
    static String until(Panchang p, long ms) {
        java.util.Calendar a = java.util.Calendar.getInstance(), b = java.util.Calendar.getInstance();
        a.setTimeInMillis(p.when);
        b.setTimeInMillis(ms);
        boolean later = b.get(java.util.Calendar.DAY_OF_YEAR) != a.get(java.util.Calendar.DAY_OF_YEAR);
        return (later ? "कल " : "") + time(ms) + " तक";
    }

    /** "आश्विन शुक्ल चतुर्दशी" */
    static String title(Panchang p) {
        return p.monthName() + " " + (p.tithi == 14 || p.tithi == 29 ? "" : p.paksha() + " ") + p.tithiName()
                + " · " + Panchang.VAAR[p.vaar];
    }

    static String[] panchangLines(Panchang p) {
        return new String[]{
                "तिथि " + until(p, p.tithiEnd) + " · नक्षत्र " + Panchang.NAKSHATRA[p.nakshatra] + " " + until(p, p.nakshatraEnd),
                "योग " + Panchang.YOGA[p.yoga] + " · करण " + p.karana + " · राहु काल " + time(p.rahuStart) + "–" + time(p.rahuEnd),
        };
    }

    static String[] sunLines(Panchang p) {
        return new String[]{
                "सूर्य",
                String.format(Locale.US, "ऊँचाई %+.1f°", p.sunAlt),
                String.format(Locale.US, "दिशा %.0f° %s", p.sunAz, Panchang.direction(p.sunAz)),
                "↑ " + time(p.sunrise) + "  ↓ " + time(p.sunset),
        };
    }

    static String[] moonLines(Panchang p) {
        return new String[]{
                "चंद्र",
                String.format(Locale.US, "ऊँचाई %+.1f°", p.moonAlt),
                String.format(Locale.US, "दिशा %.0f° %s", p.moonAz, Panchang.direction(p.moonAz)),
                "↑ " + time(p.moonrise) + "  ↓ " + time(p.moonset),
                String.format(Locale.US, "%s %.0f%% · कोण %.0f°", p.phaseName(), p.illumination * 100, p.elongation),
        };
    }
}
