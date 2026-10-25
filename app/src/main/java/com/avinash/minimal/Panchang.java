package com.avinash.minimal;

import java.util.Calendar;
import java.util.TimeZone;

/** Panchang for one instant and place: tithi, nakshatra, yoga, karana with end times, plus sun and moon. */
final class Panchang {
    static final String[] TITHI = {"प्रतिपदा", "द्वितीया", "तृतीया", "चतुर्थी", "पंचमी", "षष्ठी", "सप्तमी",
            "अष्टमी", "नवमी", "दशमी", "एकादशी", "द्वादशी", "त्रयोदशी", "चतुर्दशी", "पूर्णिमा"};
    static final String[] NAKSHATRA = {"अश्विनी", "भरणी", "कृत्तिका", "रोहिणी", "मृगशिरा", "आर्द्रा",
            "पुनर्वसु", "पुष्य", "आश्लेषा", "मघा", "पूर्वा फाल्गुनी", "उत्तरा फाल्गुनी", "हस्त", "चित्रा",
            "स्वाती", "विशाखा", "अनुराधा", "ज्येष्ठा", "मूल", "पूर्वाषाढ़ा", "उत्तराषाढ़ा", "श्रवण", "धनिष्ठा",
            "शतभिषा", "पूर्वा भाद्रपद", "उत्तरा भाद्रपद", "रेवती"};
    static final String[] YOGA = {"विष्कम्भ", "प्रीति", "आयुष्मान", "सौभाग्य", "शोभन", "अतिगण्ड", "सुकर्मा",
            "धृति", "शूल", "गण्ड", "वृद्धि", "ध्रुव", "व्याघात", "हर्षण", "वज्र", "सिद्धि", "व्यतीपात",
            "वरीयान", "परिघ", "शिव", "सिद्ध", "साध्य", "शुभ", "शुक्ल", "ब्रह्म", "इन्द्र", "वैधृति"};
    static final String[] KARANA_MOVABLE = {"बव", "बालव", "कौलव", "तैतिल", "गर", "वणिज", "विष्टि"};
    static final String[] RASHI = {"मेष", "वृषभ", "मिथुन", "कर्क", "सिंह", "कन्या", "तुला", "वृश्चिक",
            "धनु", "मकर", "कुम्भ", "मीन"};
    static final String[] MONTH = {"चैत्र", "वैशाख", "ज्येष्ठ", "आषाढ़", "श्रावण", "भाद्रपद", "आश्विन",
            "कार्तिक", "मार्गशीर्ष", "पौष", "माघ", "फाल्गुन"};
    static final String[] VAAR = {"रविवार", "सोमवार", "मंगलवार", "बुधवार", "गुरुवार", "शुक्रवार", "शनिवार"};
    /** Rahu kaal: which eighth of the daytime, by weekday starting Sunday. */
    private static final int[] RAHU_PART = {8, 2, 7, 5, 6, 4, 3};

    final long when;
    final double lat, lon;

    int tithi;            // 0..29 (0..14 shukla, 15..29 krishna)
    long tithiEnd;
    int nakshatra;        // 0..26
    int nakshatraPada;    // 1..4
    long nakshatraEnd;
    int yoga;             // 0..26
    long yogaEnd;
    String karana;
    long karanaEnd;
    int month;            // amanta, 0 = Chaitra
    boolean adhika;
    int vaar;             // 0 = Sunday
    int moonRashi, sunRashi;
    int vikramSamvat, shakaSamvat;

    long sunrise, sunset, moonrise, moonset; // epoch ms, 0 if none today
    long rahuStart, rahuEnd;
    double sunAlt, sunAz, moonAlt, moonAz;
    double sunSidLon, moonSidLon;
    double obliquity;     // Earth's axial tilt, degrees
    double illumination;  // 0..1
    double elongation;    // moon - sun, degrees
    boolean waxing;

    /** Sun and moon tracks for today (local midnight to midnight), every 15 min: {alt, az} pairs. */
    double[][] sunPath, moonPath;

    Panchang(long when, double lat, double lon, TimeZone tz) {
        this.when = when;
        this.lat = lat;
        this.lon = lon;
        double jd = Astro.jd(when);

        elongation = Astro.elongation(jd);
        waxing = elongation < 180;
        illumination = (1 - Math.cos(elongation * Astro.DEG)) / 2;
        tithi = (int) (elongation / 12);
        tithiEnd = nextChange(jd, j -> (int) (Astro.elongation(j) / 12));
        int k = (int) (elongation / 6);
        karana = karanaName(k);
        karanaEnd = nextChange(jd, j -> (int) (Astro.elongation(j) / 6));

        double sunTrop = Astro.sunLongitude(jd);
        double[] moonEc = Astro.moonEcliptic(jd);
        sunSidLon = Astro.sidereal(sunTrop, jd);
        moonSidLon = Astro.sidereal(moonEc[0], jd);
        double span = 360.0 / 27;
        nakshatra = (int) (moonSidLon / span);
        nakshatraPada = (int) ((moonSidLon % span) / (span / 4)) + 1;
        nakshatraEnd = nextChange(jd, j -> (int) (Astro.sidereal(Astro.moonLongitude(j), j) / span));
        yoga = (int) (Astro.norm(moonSidLon + sunSidLon) / span);
        yogaEnd = nextChange(jd, j -> (int) (Astro.norm(Astro.sidereal(Astro.moonLongitude(j), j)
                + Astro.sidereal(Astro.sunLongitude(j), j)) / span));
        moonRashi = (int) (moonSidLon / 30);
        sunRashi = (int) (sunSidLon / 30);

        double prevNew = newMoon(jd, false), nextNew = newMoon(jd, true);
        int signPrev = (int) (Astro.sidereal(Astro.sunLongitude(prevNew), prevNew) / 30);
        int signNext = (int) (Astro.sidereal(Astro.sunLongitude(nextNew), nextNew) / 30);
        month = (signPrev + 1) % 12;
        adhika = signPrev == signNext;

        obliquity = Astro.obliquity(jd);

        double[] sh = Astro.sunHorizontal(jd, lat, lon);
        sunAlt = sh[0]; sunAz = sh[1];
        double[] mh = Astro.moonHorizontal(jd, lat, lon);
        moonAlt = mh[0]; moonAz = mh[1];

        Calendar c = Calendar.getInstance(tz);
        c.setTimeInMillis(when);
        vaar = c.get(Calendar.DAY_OF_WEEK) - 1;
        int gy = c.get(Calendar.YEAR), gm = c.get(Calendar.MONTH) + 1;
        vikramSamvat = gy + (gm <= 4 && month >= 9 ? 56 : 57);
        shakaSamvat = vikramSamvat - 135;

        c.set(Calendar.HOUR_OF_DAY, 0); c.set(Calendar.MINUTE, 0); c.set(Calendar.SECOND, 0); c.set(Calendar.MILLISECOND, 0);
        double day0 = Astro.jd(c.getTimeInMillis());
        double[] srs = Astro.riseSet(day0, j -> Astro.sunHorizontal(j, lat, lon)[0], Astro.SUN_H0);
        double[] mrs = Astro.riseSet(day0, j -> Astro.moonHorizontalGeo(j, lat, lon)[0], Astro.MOON_H0);
        sunrise = toMs(srs[0]); sunset = toMs(srs[1]);
        moonrise = toMs(mrs[0]); moonset = toMs(mrs[1]);
        if (sunrise > 0 && sunset > sunrise) {
            long part = (sunset - sunrise) / 8;
            rahuStart = sunrise + part * (RAHU_PART[vaar] - 1);
            rahuEnd = rahuStart + part;
        }

        int n = 97;
        sunPath = new double[n][];
        moonPath = new double[n][];
        for (int i = 0; i < n; i++) {
            double j = day0 + i / 96.0;
            sunPath[i] = Astro.sunHorizontal(j, lat, lon);
            moonPath[i] = Astro.moonHorizontal(j, lat, lon);
        }
    }

    private static long toMs(double jd) { return Double.isNaN(jd) ? 0 : Astro.millis(jd); }

    String paksha() { return tithi < 15 ? "शुक्ल" : "कृष्ण"; }

    String tithiName() {
        if (tithi == 29) return "अमावस्या";
        return TITHI[tithi % 15];
    }

    String monthName() { return (adhika ? "अधिक " : "") + MONTH[month]; }

    static String karanaName(int k) {
        if (k == 0) return "किंस्तुघ्न";
        if (k == 57) return "शकुनि";
        if (k == 58) return "चतुष्पद";
        if (k == 59) return "नाग";
        return KARANA_MOVABLE[(k - 1) % 7];
    }

    /** 16-point compass direction for an azimuth. */
    static String direction(double az) {
        String[] d = {"N", "NNE", "NE", "ENE", "E", "ESE", "SE", "SSE", "S", "SSW", "SW", "WSW", "W", "WNW", "NW", "NNW"};
        return d[(int) Math.round(Astro.norm(az) / 22.5) % 16];
    }

    String phaseName() {
        double e = elongation;
        if (e < 10 || e > 350) return "अमावस्या";
        if (Math.abs(e - 180) < 10) return "पूर्ण चंद्र";
        if (Math.abs(e - 90) < 10 || Math.abs(e - 270) < 10) return "अर्ध चंद्र";
        return e < 180 ? "बढ़ता चंद्र" : "घटता चंद्र";
    }

    interface IndexFn { int at(double jd); }

    /** First instant after jd where fn changes value (hourly scan, then bisection to ~15 s). */
    private static long nextChange(double jd, IndexFn fn) {
        int start = fn.at(jd);
        double lo = jd, hi = jd;
        for (int i = 1; i <= 36; i++) {
            hi = jd + i / 24.0;
            if (fn.at(hi) != start) break;
            lo = hi;
        }
        for (int i = 0; i < 12; i++) {
            double mid = (lo + hi) / 2;
            if (fn.at(mid) == start) lo = mid; else hi = mid;
        }
        return Astro.millis(hi);
    }

    /** Previous (or next) new moon around jd via Newton steps on the elongation. */
    private static double newMoon(double jd, boolean next) {
        double e = Astro.elongation(jd);
        double t = next ? jd + (360 - e) / 12.19 : jd - e / 12.19;
        for (int i = 0; i < 6; i++) {
            double f = Astro.elongation(t);
            if (f > 180) f -= 360;
            t -= f / 12.19;
        }
        return t;
    }
}
