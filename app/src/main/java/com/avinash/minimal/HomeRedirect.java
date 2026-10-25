package com.avinash.minimal;

import android.accessibilityservice.AccessibilityService;
import android.content.Intent;
import android.os.SystemClock;
import android.view.KeyEvent;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityNodeInfo;

/**
 * ColorOS force-picks its own launcher for the Home button whatever the default home is set to
 * (OplusDefaultAppPolicyManager "force" list). This service watches for that launcher's home screen
 * and opens Minimal in its place. Recents is a separate activity and is left alone.
 * Where the Home key reaches accessibility key filtering it is handled directly, so the stock
 * launcher never shows; the window check stays as the fallback.
 */
public final class HomeRedirect extends AccessibilityService {
    private static final String STOCK_PKG = "com.android.launcher";
    private static final String STOCK_HOME = "com.android.launcher.Launcher";
    private long lastRedirect;

    @Override public void onAccessibilityEvent(AccessibilityEvent e) {
        if (e.getEventType() != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) return;
        if (!STOCK_PKG.contentEquals(e.getPackageName() == null ? "" : e.getPackageName())) return;
        if (!STOCK_HOME.contentEquals(e.getClassName() == null ? "" : e.getClassName())) return;
        if (showingOverview()) return;
        long now = SystemClock.uptimeMillis();
        if (now - lastRedirect < 400) return;
        goHome();
    }

    @Override protected boolean onKeyEvent(KeyEvent k) {
        if (k.getKeyCode() != KeyEvent.KEYCODE_HOME) return false;
        if (k.getAction() == KeyEvent.ACTION_UP && !k.isCanceled()) goHome();
        return true;
    }

    private void goHome() {
        lastRedirect = SystemClock.uptimeMillis();
        startActivity(new Intent(this, MainActivity.class)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_NO_ANIMATION));
    }

    /** The stock launcher can also host its own overview; never steal that. */
    private boolean showingOverview() {
        AccessibilityNodeInfo root = getRootInActiveWindow();
        if (root == null) return false;
        return !root.findAccessibilityNodeInfosByViewId(STOCK_PKG + ":id/overview_panel").isEmpty();
    }

    @Override public void onInterrupt() { }
}
