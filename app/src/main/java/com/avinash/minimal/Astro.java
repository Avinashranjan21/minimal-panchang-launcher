package com.avinash.minimal;

/**
 * Low-precision sun and moon positions (Meeus, Astronomical Algorithms ch. 25 and 47, truncated series).
 * Sun is good to ~0.01 deg, moon to ~0.01 deg in longitude: tithi/nakshatra end times land within a minute or two.
 */
final class Astro {
    private Astro() {}

    static final double DEG = Math.PI / 180.0;
    private static final double DELTA_T_DAYS = 69.0 / 86400.0;

    static double jd(long epochMillis) { return epochMillis / 86400000.0 + 2440587.5; }
    static long millis(double jd) { return Math.round((jd - 2440587.5) * 86400000.0); }

    static double norm(double d) { d %= 360.0; return d < 0 ? d + 360.0 : d; }
    private static double sin(double d) { return Math.sin(d * DEG); }
    private static double cos(double d) { return Math.cos(d * DEG); }

    private static double t(double jdUt) { return (jdUt + DELTA_T_DAYS - 2451545.0) / 36525.0; }

    /** Apparent tropical ecliptic longitude of the sun, degrees. */
    static double sunLongitude(double jdUt) {
        double T = t(jdUt);
        double L0 = 280.46646 + 36000.76983 * T + 0.0003032 * T * T;
        double M = 357.52911 + 35999.05029 * T - 0.0001537 * T * T;
        double C = (1.914602 - 0.004817 * T - 0.000014 * T * T) * sin(M)
                + (0.019993 - 0.000101 * T) * sin(2 * M) + 0.000289 * sin(3 * M);
        double omega = 125.04 - 1934.136 * T;
        return norm(L0 + C - 0.00569 - 0.00478 * sin(omega));
    }

    // D, M, M', F, coefficient (1e-6 deg). Powers of E are applied for |M| = 1 or 2.
    private static final int[][] MOON_LON = {
            {0, 0, 1, 0, 6288774}, {2, 0, -1, 0, 1274027}, {2, 0, 0, 0, 658314}, {0, 0, 2, 0, 213618},
            {0, 1, 0, 0, -185116}, {0, 0, 0, 2, -114332}, {2, 0, -2, 0, 58793}, {2, -1, -1, 0, 57066},
            {2, 0, 1, 0, 53322}, {2, -1, 0, 0, 45758}, {0, 1, -1, 0, -40923}, {1, 0, 0, 0, -34720},
            {0, 1, 1, 0, -30383}, {2, 0, 0, -2, 15327}, {0, 0, 1, 2, -12528}, {0, 0, 1, -2, 10980},
            {4, 0, -1, 0, 10675}, {0, 0, 3, 0, 10034}, {4, 0, -2, 0, 8548}, {2, 1, -1, 0, -7888},
            {2, 1, 0, 0, -6766}, {1, 0, -1, 0, -5163}, {1, 1, 0, 0, 4987}, {2, -1, 1, 0, 4036},
            {2, 0, 2, 0, 3994}, {4, 0, 0, 0, 3861}, {2, 0, -3, 0, 3665}, {0, 1, -2, 0, -2689},
            {2, 0, -1, 2, -2602}, {2, -1, -2, 0, 2390}, {1, 0, 1, 0, -2348}, {2, -2, 0, 0, 2236},
            {0, 1, 2, 0, -2120}, {0, 2, 0, 0, -2069}, {2, -2, -1, 0, 2048}, {2, 0, 1, -2, -1773},
            {2, 0, 0, 2, -1595}, {4, -1, -1, 0, 1215}, {0, 0, 2, 2, -1110}, {3, 0, -1, 0, -892},
    };
    private static final int[][] MOON_LAT = {
            {0, 0, 0, 1, 5128122}, {0, 0, 1, 1, 280602}, {0, 0, 1, -1, 277693}, {2, 0, 0, -1, 173237},
            {2, 0, -1, 1, 55413}, {2, 0, -1, -1, 46271}, {2, 0, 0, 1, 32573}, {0, 0, 2, 1, 17198},
            {2, 0, 1, -1, 9266}, {0, 0, 2, -1, 8822}, {2, -1, 0, -1, 8216}, {2, 0, -2, -1, 4324},
            {2, 0, 1, 1, 4200}, {2, 1, 0, -1, -3359}, {2, -1, -1, 1, 2463}, {2, -1, 0, 1, 2211},
    };

    /** {apparent tropical longitude, latitude} of the moon, degrees. */
    static double[] moonEcliptic(double jdUt) {
        double T = t(jdUt);
        double Lp = 218.3164477 + 481267.88123421 * T - 0.0015786 * T * T;
        double D = 297.8501921 + 445267.1114034 * T - 0.0018819 * T * T;
        double M = 357.5291092 + 35999.0502909 * T - 0.0001536 * T * T;
        double Mp = 134.9633964 + 477198.8675055 * T + 0.0087414 * T * T;
        double F = 93.2720950 + 483202.0175233 * T - 0.0036539 * T * T;
        double E = 1 - 0.002516 * T - 0.0000074 * T * T;
        double A1 = 119.75 + 131.849 * T, A2 = 53.09 + 479264.290 * T, A3 = 313.45 + 481266.484 * T;

        double sl = 0, sb = 0;
        for (int[] r : MOON_LON) {
            double e = Math.abs(r[1]) == 1 ? E : Math.abs(r[1]) == 2 ? E * E : 1;
            sl += r[4] * e * sin(r[0] * D + r[1] * M + r[2] * Mp + r[3] * F);
        }
        for (int[] r : MOON_LAT) {
            double e = Math.abs(r[1]) == 1 ? E : Math.abs(r[1]) == 2 ? E * E : 1;
            sb += r[4] * e * sin(r[0] * D + r[1] * M + r[2] * Mp + r[3] * F);
        }
        sl += 3958 * sin(A1) + 1962 * sin(Lp - F) + 318 * sin(A2);
        sb += -2235 * sin(Lp) + 382 * sin(A3) + 175 * sin(A1 - F) + 175 * sin(A1 + F)
                + 127 * sin(Lp - Mp) - 115 * sin(Lp + Mp);
        double omega = 125.04452 - 1934.136261 * T;
        return new double[]{norm(Lp + sl / 1e6 - 0.00478 * sin(omega)), sb / 1e6};
    }

    static double moonLongitude(double jdUt) { return moonEcliptic(jdUt)[0]; }

    /** Lahiri (Chitrapaksha) ayanamsa, degrees. */
    static double ayanamsa(double jdUt) {
        return 23.85906 + 1.39722 * t(jdUt) + 0.00018 * t(jdUt) * t(jdUt);
    }

    static double sidereal(double tropical, double jdUt) { return norm(tropical - ayanamsa(jdUt)); }

    /** Moon minus sun longitude, 0..360. */
    static double elongation(double jdUt) { return norm(moonLongitude(jdUt) - sunLongitude(jdUt)); }

    /** True obliquity of the ecliptic (Earth's axial tilt), degrees. */
    static double obliquity(double jdUt) {
        double T = t(jdUt);
        return 23.439291 - 0.0130042 * T + 0.00256 * cos(125.04 - 1934.136 * T);
    }

    /** {right ascension, declination} in degrees for an ecliptic position. */
    static double[] equatorial(double jdUt, double lambda, double beta) {
        double eps = obliquity(jdUt);
        double ra = norm(Math.atan2(sin(lambda) * cos(eps) - Math.tan(beta * DEG) * sin(eps), cos(lambda)) / DEG);
        double dec = Math.asin(sin(beta) * cos(eps) + cos(beta) * sin(eps) * sin(lambda)) / DEG;
        return new double[]{ra, dec};
    }

    /** Local sidereal time, degrees. */
    static double lst(double jdUt, double lon) {
        double d = jdUt - 2451545.0, Tu = d / 36525.0;
        return norm(280.46061837 + 360.98564736629 * d + 0.000387933 * Tu * Tu + lon);
    }

    /** {altitude, azimuth from north} in degrees for an ecliptic position seen from lat/lon. */
    static double[] horizontal(double jdUt, double lambda, double beta, double lat, double lon) {
        double[] eq = equatorial(jdUt, lambda, beta);
        double dec = eq[1];
        double H = norm(lst(jdUt, lon) - eq[0]);
        double alt = Math.asin(sin(lat) * sin(dec) + cos(lat) * cos(dec) * cos(H)) / DEG;
        double az = norm(Math.atan2(sin(H), cos(H) * sin(lat) - Math.tan(dec * DEG) * cos(lat)) / DEG + 180.0);
        return new double[]{alt, az};
    }

    static double[] sunHorizontal(double jdUt, double lat, double lon) {
        return horizontal(jdUt, sunLongitude(jdUt), 0, lat, lon);
    }

    /** Geocentric moon altitude/azimuth (rise/set thresholds account for parallax). */
    static double[] moonHorizontalGeo(double jdUt, double lat, double lon) {
        double[] ec = moonEcliptic(jdUt);
        return horizontal(jdUt, ec[0], ec[1], lat, lon);
    }

    /** Topocentric moon altitude/azimuth: what you actually see. */
    static double[] moonHorizontal(double jdUt, double lat, double lon) {
        double[] h = moonHorizontalGeo(jdUt, lat, lon);
        h[0] -= 0.9507 * cos(h[0]);
        return h;
    }

    static final double SUN_H0 = -0.833, MOON_H0 = 0.125;

    interface AltFn { double alt(double jd); }

    /**
     * Rise and set times (julian days, NaN if none) between jdStart and jdStart + 1, found by a 10 minute scan
     * and bisection to ~20 s.
     */
    static double[] riseSet(double jdStart, AltFn fn, double h0) {
        double rise = Double.NaN, set = Double.NaN;
        double step = 10.0 / 1440.0;
        double prevT = jdStart, prevA = fn.alt(prevT) - h0;
        for (double tt = jdStart + step; tt <= jdStart + 1 + 1e-9; tt += step) {
            double a = fn.alt(tt) - h0;
            if ((prevA < 0) != (a < 0)) {
                double lo = prevT, hi = tt, alo = prevA;
                for (int i = 0; i < 12; i++) {
                    double mid = (lo + hi) / 2, am = fn.alt(mid) - h0;
                    if ((am < 0) == (alo < 0)) { lo = mid; alo = am; } else hi = mid;
                }
                double root = (lo + hi) / 2;
                if (prevA < 0) { if (Double.isNaN(rise)) rise = root; } else if (Double.isNaN(set)) set = root;
            }
            prevT = tt; prevA = a;
        }
        return new double[]{rise, set};
    }
}
