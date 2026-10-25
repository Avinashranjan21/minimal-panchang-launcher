package com.avinash.minimal;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.graphics.Color;
import android.graphics.Typeface;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowInsets;
import android.view.WindowInsetsController;
import android.view.inputmethod.InputMethodManager;
import android.widget.ArrayAdapter;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.TextClock;
import android.widget.TextView;
import android.widget.Toast;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/** Home screen: clock, Panchang, sun/moon dial, a short text list of work apps, and a searchable full list. */
public class MainActivity extends Activity {
    // Starting list; anything not installed is skipped. Long-press in the full list to add or remove.
    private static final String DEFAULT_FAVS = "com.google.android.dialer,com.google.android.apps.messaging,"
            + "com.android.chrome,com.google.android.gm,com.google.android.apps.tachyon,com.google.android.apps.docs,"
            + "com.oplus.camera";

    static final class App {
        final String label, pkg; final ComponentName cn;
        App(String label, ComponentName cn) { this.label = label; this.cn = cn; this.pkg = cn.getPackageName(); }
        @Override public String toString() { return label; }
    }

    private final Handler handler = new Handler(Looper.getMainLooper());
    private TextView dateView, titleView, panchangView, sunView, moonView, allButton;
    private SkyDial dial;
    private FrameLayout root;
    private TextClock clock;
    private Theme theme;
    private ListView favList, allList;
    private LinearLayout allPanel;
    private EditText search;
    private final List<App> apps = new ArrayList<>();
    private final List<App> favs = new ArrayList<>();
    private ArrayAdapter<App> favAdapter, allAdapter;

    private final Runnable tick = new Runnable() {
        @Override public void run() {
            refreshInfo();
            handler.postDelayed(this, 60_000 - System.currentTimeMillis() % 60_000 + 500);
        }
    };

    private final BroadcastReceiver screenOff = new BroadcastReceiver() {
        @Override public void onReceive(Context c, Intent i) { LockWallpaper.refreshAsync(c, false); }
    };

    @Override protected void onCreate(Bundle b) {
        super.onCreate(b);
        getWindow().setStatusBarColor(Color.TRANSPARENT);
        getWindow().setNavigationBarColor(Color.BLACK);
        setContentView(buildUi());
        applyDebugTheme(getIntent());
        if (checkSelfPermission(android.Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{android.Manifest.permission.ACCESS_COARSE_LOCATION}, 1);
        }
        registerReceiver(screenOff, new IntentFilter(Intent.ACTION_SCREEN_OFF));
        LockWallpaper.schedule(this);
        LockWallpaper.refreshAsync(this, true);
    }

    @Override protected void onDestroy() {
        unregisterReceiver(screenOff);
        super.onDestroy();
    }

    @Override protected void onResume() {
        super.onResume();
        loadApps();
        Info.requestFresh(this, this::refreshInfo);
        handler.removeCallbacks(tick);
        handler.post(tick);
    }

    @Override protected void onPause() {
        handler.removeCallbacks(tick);
        super.onPause();
    }

    @Override public void onRequestPermissionsResult(int code, String[] perms, int[] res) {
        Info.requestFresh(this, this::refreshInfo);
        refreshInfo();
        LockWallpaper.refreshAsync(this, true);
    }

    /** Pressing Home while already home closes the app list. */
    @Override protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        showAll(false);
        if (applyDebugTheme(intent)) {
            refreshInfo();
            LockWallpaper.refreshAsync(this, true);
        }
    }

    @Override public void onBackPressed() { showAll(false); }

    /** Debug builds: `am start -n com.avinash.minimal/.MainActivity --es theme day|night|auto` for screenshots. */
    private boolean applyDebugTheme(Intent intent) {
        if ((getApplicationInfo().flags & ApplicationInfo.FLAG_DEBUGGABLE) == 0) return false;
        String t = intent.getStringExtra("theme");
        if (t == null) return false;
        Theme.override = t.equals("day") ? Boolean.TRUE : t.equals("night") ? Boolean.FALSE : null;
        return true;
    }

    private int dp(float v) { return (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, v, getResources().getDisplayMetrics()); }

    private TextView text(float size, int color, Typeface tf) {
        TextView t = new TextView(this);
        t.setTextSize(TypedValue.COMPLEX_UNIT_SP, size);
        t.setTextColor(color);
        t.setTypeface(tf);
        return t;
    }

    private View buildUi() {
        root = new FrameLayout(this);
        root.setBackgroundColor(Color.BLACK);

        LinearLayout home = new LinearLayout(this);
        home.setOrientation(LinearLayout.VERTICAL);
        home.setPadding(dp(28), dp(24), dp(28), dp(12));
        root.addView(home, new FrameLayout.LayoutParams(-1, -1));

        clock = new TextClock(this);
        clock.setFormat12Hour("h:mm");
        clock.setFormat24Hour("HH:mm");
        clock.setTextSize(TypedValue.COMPLEX_UNIT_SP, 64);
        clock.setTextColor(Color.WHITE);
        clock.setTypeface(Typeface.create("sans-serif-thin", Typeface.NORMAL));
        clock.setOnClickListener(v -> launchPackage("com.coloros.alarmclock"));
        home.addView(clock);

        dateView = text(15, 0x99FFFFFF, Typeface.DEFAULT);
        home.addView(dateView);

        titleView = text(19, 0xEEFFFFFF, Typeface.create("sans-serif-light", Typeface.NORMAL));
        titleView.setPadding(0, dp(22), 0, dp(4));
        home.addView(titleView);
        panchangView = text(13.5f, 0x99FFFFFF, Typeface.DEFAULT);
        panchangView.setLineSpacing(dp(3), 1f);
        // Long-press forces a lock-screen redraw (e.g. after moving cities).
        panchangView.setOnLongClickListener(v -> {
            LockWallpaper.refreshAsync(this, true);
            Toast.makeText(this, "लॉक स्क्रीन अपडेट", Toast.LENGTH_SHORT).show();
            return true;
        });
        home.addView(panchangView);

        LinearLayout sky = new LinearLayout(this);
        sky.setOrientation(LinearLayout.HORIZONTAL);
        sky.setGravity(Gravity.CENTER_VERTICAL);
        sky.setPadding(0, dp(16), 0, dp(8));
        dial = new SkyDial(this);
        sky.addView(dial, new LinearLayout.LayoutParams(dp(136), dp(136)));
        LinearLayout cols = new LinearLayout(this);
        cols.setOrientation(LinearLayout.VERTICAL);
        cols.setPadding(dp(18), 0, 0, 0);
        sunView = text(12.5f, 0xFFF2B33D, Typeface.DEFAULT);
        moonView = text(12.5f, 0xFFC9D4E3, Typeface.DEFAULT);
        moonView.setPadding(0, dp(8), 0, 0);
        cols.addView(sunView);
        cols.addView(moonView);
        sky.addView(cols, new LinearLayout.LayoutParams(0, -2, 1));
        home.addView(sky);

        favAdapter = new ArrayAdapter<App>(this, 0, favs) {
            @Override public View getView(int pos, View v, ViewGroup parent) {
                TextView t = v instanceof TextView ? (TextView) v : text(20, 0xDDFFFFFF, Typeface.create("sans-serif-light", Typeface.NORMAL));
                t.setPadding(0, dp(7), 0, dp(7));
                t.setText(getItem(pos).label);
                if (theme != null) t.setTextColor(theme.strong);
                return t;
            }
        };
        favList = new ListView(this);
        favList.setDivider(null);
        favList.setVerticalScrollBarEnabled(false);
        favList.setAdapter(favAdapter);
        favList.setOnItemClickListener((a, v, pos, id) -> launch(favs.get(pos)));
        favList.setOnItemLongClickListener((a, v, pos, id) -> { favMenu(pos); return true; });
        home.addView(favList, new LinearLayout.LayoutParams(-1, 0, 1));

        allButton = text(13, 0x66FFFFFF, Typeface.DEFAULT);
        allButton.setText("सभी ऐप्स");
        allButton.setGravity(Gravity.CENTER);
        allButton.setPadding(0, dp(10), 0, dp(10));
        allButton.setOnClickListener(v -> showAll(true));
        home.addView(allButton, new LinearLayout.LayoutParams(-1, -2));

        allPanel = new LinearLayout(this);
        allPanel.setOrientation(LinearLayout.VERTICAL);
        allPanel.setBackgroundColor(Color.BLACK);
        allPanel.setPadding(dp(28), dp(24), dp(28), dp(12));
        allPanel.setVisibility(View.GONE);
        search = new EditText(this);
        search.setHint("खोजें");
        search.setHintTextColor(0x55FFFFFF);
        search.setTextColor(Color.WHITE);
        search.setSingleLine(true);
        search.setBackgroundColor(0x14FFFFFF);
        search.setPadding(dp(14), dp(10), dp(14), dp(10));
        search.addTextChangedListener(new TextWatcher() {
            public void beforeTextChanged(CharSequence s, int a, int b, int c) {}
            public void onTextChanged(CharSequence s, int a, int b, int c) {}
            public void afterTextChanged(Editable s) { filterAll(s.toString()); }
        });
        search.setOnEditorActionListener((v, action, ev) -> {
            if (allAdapter.getCount() > 0) launch(allAdapter.getItem(0));
            return true;
        });
        allPanel.addView(search);
        allAdapter = new ArrayAdapter<App>(this, 0, new ArrayList<>()) {
            @Override public View getView(int pos, View v, ViewGroup parent) {
                TextView t = v instanceof TextView ? (TextView) v : text(18, 0xCCFFFFFF, Typeface.create("sans-serif-light", Typeface.NORMAL));
                t.setPadding(0, dp(9), 0, dp(9));
                App app = getItem(pos);
                t.setText(isFav(app.pkg) ? app.label + "  ·" : app.label);
                if (theme != null) t.setTextColor(theme.strong);
                return t;
            }
        };
        allList = new ListView(this);
        allList.setDivider(null);
        allList.setAdapter(allAdapter);
        allList.setOnItemClickListener((a, v, pos, id) -> launch(allAdapter.getItem(pos)));
        allList.setOnItemLongClickListener((a, v, pos, id) -> { allMenu(allAdapter.getItem(pos)); return true; });
        allPanel.addView(allList, new LinearLayout.LayoutParams(-1, 0, 1));
        root.addView(allPanel, new FrameLayout.LayoutParams(-1, -1));

        root.setOnApplyWindowInsetsListener((v, insets) -> {
            int top = insets.getInsets(WindowInsets.Type.systemBars()).top;
            int bottom = insets.getInsets(WindowInsets.Type.systemBars()).bottom;
            home.setPadding(dp(28), top + dp(16), dp(28), bottom + dp(4));
            allPanel.setPadding(dp(28), top + dp(16), dp(28), bottom + dp(4));
            return insets;
        });
        return root;
    }

    private void refreshInfo() {
        Panchang p = Info.now(this);
        dateView.setText(new SimpleDateFormat("EEEE, d MMMM", Locale.getDefault()).format(new Date()));
        titleView.setText(Info.title(p));
        panchangView.setText(String.join("\n", Info.panchangLines(p)));
        sunView.setText(String.join("\n", Info.sunLines(p)));
        moonView.setText(String.join("\n", Info.moonLines(p)));
        dial.set(p);
        applyTheme(Theme.of(p));
    }

    /** Swaps colours at sunrise and sunset; the lock wallpaper follows on its next render. */
    private void applyTheme(Theme t) {
        if (t == theme) return;
        boolean changed = theme != null;
        theme = t;
        root.setBackgroundColor(t.bg);
        getWindow().getDecorView().setBackgroundColor(t.bg);
        allPanel.setBackgroundColor(t.bg);
        getWindow().setNavigationBarColor(t.bg);
        int light = WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS | WindowInsetsController.APPEARANCE_LIGHT_NAVIGATION_BARS;
        getWindow().getInsetsController().setSystemBarsAppearance(t.day ? light : 0, light);
        clock.setTextColor(t.text);
        titleView.setTextColor(t.strong);
        dateView.setTextColor(t.dim);
        panchangView.setTextColor(t.dim);
        sunView.setTextColor(t.sun);
        moonView.setTextColor(t.moon);
        allButton.setTextColor(t.faint);
        search.setTextColor(t.text);
        search.setHintTextColor(t.faint);
        search.setBackgroundColor(t.search);
        favAdapter.notifyDataSetChanged();
        allAdapter.notifyDataSetChanged();
        if (changed) LockWallpaper.refreshAsync(this, true);
    }

    private void loadApps() {
        PackageManager pm = getPackageManager();
        Intent i = new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER);
        apps.clear();
        for (ResolveInfo ri : pm.queryIntentActivities(i, 0)) {
            if (ri.activityInfo.packageName.equals(getPackageName())) continue;
            apps.add(new App(ri.loadLabel(pm).toString(), new ComponentName(ri.activityInfo.packageName, ri.activityInfo.name)));
        }
        apps.sort((a, b) -> a.label.compareToIgnoreCase(b.label));
        favs.clear();
        for (String pkg : favPkgs()) {
            for (App a : apps) if (a.pkg.equals(pkg)) { favs.add(a); break; }
        }
        favAdapter.notifyDataSetChanged();
        filterAll(search.getText().toString());
    }

    private void filterAll(String q) {
        allAdapter.clear();
        String lq = q.trim().toLowerCase(Locale.ROOT);
        for (App a : apps) if (lq.isEmpty() || a.label.toLowerCase(Locale.ROOT).contains(lq)) allAdapter.add(a);
    }

    private List<String> favPkgs() {
        String s = Info.prefs(this).getString("favs", DEFAULT_FAVS);
        return s.isEmpty() ? new ArrayList<>() : new ArrayList<>(Arrays.asList(s.split(",")));
    }

    private void saveFavs(List<String> l) { Info.prefs(this).edit().putString("favs", String.join(",", l)).apply(); }

    private boolean isFav(String pkg) { return favPkgs().contains(pkg); }

    private void favMenu(int pos) {
        App app = favs.get(pos);
        new AlertDialog.Builder(this, android.R.style.Theme_DeviceDefault_Dialog_Alert)
                .setTitle(app.label)
                .setItems(new String[]{"ऊपर ले जाएँ", "हटाएँ", "ऐप जानकारी"}, (d, which) -> {
                    List<String> l = favPkgs();
                    int i = l.indexOf(app.pkg);
                    if (which == 0 && i > 0) { l.remove(i); l.add(i - 1, app.pkg); saveFavs(l); }
                    else if (which == 1) { l.remove(app.pkg); saveFavs(l); }
                    else if (which == 2) appInfo(app.pkg);
                    loadApps();
                }).show();
    }

    private void allMenu(App app) {
        boolean fav = isFav(app.pkg);
        new AlertDialog.Builder(this, android.R.style.Theme_DeviceDefault_Dialog_Alert)
                .setTitle(app.label)
                .setItems(new String[]{fav ? "होम से हटाएँ" : "होम पर जोड़ें", "ऐप जानकारी"}, (d, which) -> {
                    if (which == 0) {
                        List<String> l = favPkgs();
                        if (fav) l.remove(app.pkg); else l.add(app.pkg);
                        saveFavs(l);
                        loadApps();
                    } else appInfo(app.pkg);
                }).show();
    }

    private void appInfo(String pkg) {
        startActivity(new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:" + pkg)));
    }

    private void showAll(boolean show) {
        if (allPanel == null) return;
        allPanel.setVisibility(show ? View.VISIBLE : View.GONE);
        InputMethodManager imm = getSystemService(InputMethodManager.class);
        if (!show) {
            search.setText("");
            imm.hideSoftInputFromWindow(search.getWindowToken(), 0);
        } else {
            allList.setSelection(0);
        }
    }

    private void launch(App app) {
        try {
            startActivity(new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
                    .setComponent(app.cn).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED));
        } catch (Exception e) {
            Toast.makeText(this, "नहीं खुला", Toast.LENGTH_SHORT).show();
        }
        handler.postDelayed(() -> showAll(false), 400);
    }

    private void launchPackage(String pkg) {
        Intent i = getPackageManager().getLaunchIntentForPackage(pkg);
        if (i != null) startActivity(i);
    }
}
