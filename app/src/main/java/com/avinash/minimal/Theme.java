package com.avinash.minimal;

/** Day look between sunrise and sunset, night look otherwise, for both home and lock screens. */
final class Theme {
    final boolean day;
    final int bg, text, strong, dim, faint, line, sun, moon, moonDark, search;

    private Theme(boolean day) {
        this.day = day;
        if (day) {
            bg = 0xFFF3EFE6; text = 0xFF1E1C19; strong = 0xEE1E1C19; dim = 0x991E1C19; faint = 0x551E1C19;
            line = 0x331E1C19; sun = 0xFFD08300; moon = 0xFF4E6283; moonDark = 0xFFD6D2C8; search = 0x141E1C19;
        } else {
            bg = 0xFF000000; text = 0xFFFFFFFF; strong = 0xEEFFFFFF; dim = 0x99FFFFFF; faint = 0x55FFFFFF;
            line = 0x33FFFFFF; sun = 0xFFF2B33D; moon = 0xFFC9D4E3; moonDark = 0xFF2A2F38; search = 0x14FFFFFF;
        }
    }

    private static final Theme DAY = new Theme(true), NIGHT = new Theme(false);

    /** Debug builds only: forces day (true) or night (false) for screenshots; null follows the sun. */
    static Boolean override;

    static Theme of(Panchang p) {
        if (override != null) return override ? DAY : NIGHT;
        boolean day = p.sunrise > 0 && p.sunset > 0 ? p.when >= p.sunrise && p.when < p.sunset : p.sunAlt > -0.833;
        return day ? DAY : NIGHT;
    }
}
