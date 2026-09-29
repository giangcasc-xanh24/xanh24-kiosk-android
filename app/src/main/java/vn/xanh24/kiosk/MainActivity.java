package vn.xanh24.kiosk;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.ActivityManager;
import android.app.AlertDialog;
import android.app.admin.DevicePolicyManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.ActivityInfo;
import android.graphics.Color;
import android.net.ConnectivityManager;
import android.net.NetworkCapabilities;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.speech.tts.TextToSpeech;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.webkit.JavascriptInterface;
import android.webkit.RenderProcessGoneDetail;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceError;
import android.webkit.WebResourceRequest;
import android.webkit.WebResourceResponse;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.Toast;

import androidx.webkit.WebViewAssetLoader;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.Calendar;
import java.util.Locale;

/**
 * Xanh24 Kiosk 2.0 – vỏ WebView toàn màn hình, dùng chung cho 5 bệnh viện.
 * - Chỉ mở phần mềm của bệnh viện đã cài đặt (chặn mọi đường dẫn khác, kể cả trang của bệnh viện khác)
 * - Trực tuyến hoặc ngoại tuyến; mất mạng tự chuyển sang bản đóng gói, có mạng lại tự quay về
 * - Khóa thiết bị: Device Owner (lock task) hoặc ghim màn hình; tự mở khi bật máy; tải lại hằng ngày
 * - Cầu nối giọng đọc tiếng Việt (window.AndroidTTS) và quản trị (window.AndroidKiosk)
 * - Menu quản trị ẩn: chạm 5 lần trong 3 giây vào góc trên bên trái → nhập PIN
 */
public class MainActivity extends Activity {
    private KioskConfig cfg;
    private WebView web;
    private TextToSpeech tts;
    private volatile boolean ttsReady = false;
    private boolean fallback = false;
    private final Handler h = new Handler(Looper.getMainLooper());
    private int taps = 0;
    private long firstTap = 0;

    @SuppressLint({"SetJavaScriptEnabled", "AddJavascriptInterface"})
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        cfg = new KioskConfig(this);
        if (!cfg.setupDone()) { startActivity(new Intent(this, SetupActivity.class)); finish(); return; }

        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        applyOrientation();
        enterLockTask();

        FrameLayout root = new FrameLayout(this);
        web = new WebView(this);
        web.setBackgroundColor(Color.WHITE);
        root.addView(web, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        View corner = new View(this);                 // vùng chạm ẩn mở menu quản trị
        corner.setOnClickListener(v -> cornerTap());
        int s = Math.round(72 * getResources().getDisplayMetrics().density);
        root.addView(corner, new FrameLayout.LayoutParams(s, s, Gravity.TOP | Gravity.START));
        setContentView(root);

        WebSettings ws = web.getSettings();
        ws.setJavaScriptEnabled(true);
        ws.setDomStorageEnabled(true);
        ws.setMediaPlaybackRequiresUserGesture(false);
        ws.setTextZoom(100);
        ws.setSupportZoom(false);
        ws.setBuiltInZoomControls(false);
        ws.setDisplayZoomControls(false);
        ws.setAllowFileAccess(false);
        ws.setAllowContentAccess(false);
        ws.setMixedContentMode(WebSettings.MIXED_CONTENT_NEVER_ALLOW);
        ws.setCacheMode(WebSettings.LOAD_DEFAULT);
        ws.setUserAgentString(ws.getUserAgentString() + " Xanh24Kiosk/" + BuildConfig.VERSION_NAME);

        final WebViewAssetLoader loader = new WebViewAssetLoader.Builder()
                .addPathHandler("/assets/", new WebViewAssetLoader.AssetsPathHandler(this))
                .build();
        final String cfgPath = "/assets/tenants/" + cfg.tenant() + "/x24-config.js";

        web.setWebViewClient(new WebViewClient() {
            @Override
            public WebResourceResponse shouldInterceptRequest(WebView view, WebResourceRequest req) {
                Uri u = req.getUrl();
                if (KioskConfig.ASSET_HOST.equals(u.getHost())) {
                    if (cfgPath.equals(u.getPath())) {   // cấu hình sinh động theo phần Cài đặt
                        return new WebResourceResponse("application/javascript", "utf-8", new ByteArrayInputStream(cfg.configJs().getBytes(StandardCharsets.UTF_8)));
                    }
                    String p = u.getPath() == null ? "" : u.getPath();
                    if (p.startsWith("/assets/tenants/") && !p.startsWith("/assets/tenants/" + cfg.tenant() + "/")) {
                        return new WebResourceResponse("text/plain", "utf-8", 403, "Forbidden", null, new ByteArrayInputStream(new byte[0]));
                    }
                    return loader.shouldInterceptRequest(u);
                }
                return null;
            }

            @Override
            public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest req) { return !allowed(req.getUrl()); }

            @Override
            public void onReceivedError(WebView view, WebResourceRequest req, WebResourceError error) {
                if (req.isForMainFrame() && !fallback && KioskConfig.MODE_ONLINE.equals(cfg.mode())) goOffline();
            }

            @Override
            public void onReceivedHttpError(WebView view, WebResourceRequest req, WebResourceResponse resp) {
                if (req.isForMainFrame() && resp.getStatusCode() >= 500 && !fallback && KioskConfig.MODE_ONLINE.equals(cfg.mode())) goOffline();
            }

            @Override
            public boolean onRenderProcessGone(WebView view, RenderProcessGoneDetail detail) {
                recreate();          // trình hiển thị bị lỗi → dựng lại màn hình, kiosk không bị treo
                return true;
            }
        });
        web.setWebChromeClient(new WebChromeClient());
        web.addJavascriptInterface(new TtsBridge(), "AndroidTTS");
        web.addJavascriptInterface(new KioskBridge(), "AndroidKiosk");

        tts = new TextToSpeech(this, st -> {
            if (st == TextToSpeech.SUCCESS) {
                int r = tts.setLanguage(new Locale("vi", "VN"));
                ttsReady = r != TextToSpeech.LANG_MISSING_DATA && r != TextToSpeech.LANG_NOT_SUPPORTED;
                if (!ttsReady) ttsReady = tts.setLanguage(Locale.getDefault()) >= 0;
            }
        });

        if (KioskConfig.MODE_ONLINE.equals(cfg.mode()) && !online()) goOffline();
        else web.loadUrl(cfg.startUrl());
        scheduleDailyReload();
        h.postDelayed(retryOnline, 10 * 60_000L);
        hideSystemUi();
    }

    /** Chỉ cho phép trang của đúng bệnh viện: bản đóng gói, hoặc đường dẫn của bệnh viện trên máy chủ Xanh24. */
    private boolean allowed(Uri u) {
        String host = u.getHost(), path = u.getPath() == null ? "/" : u.getPath();
        if (host == null || !"https".equals(u.getScheme())) return false;
        if (KioskConfig.ASSET_HOST.equals(host)) return path.startsWith("/assets/tenants/" + cfg.tenant() + "/");
        Uri d = Uri.parse(cfg.domain());
        return host.equalsIgnoreCase(d.getHost()) && (path.startsWith(Hospitals.path(cfg.tenant())) || path.startsWith("/phieu-kham/") || path.startsWith("/maps/") || path.startsWith("/signage/"));
    }

    private void goOffline() {
        fallback = true;
        h.post(() -> { web.stopLoading(); web.loadUrl(cfg.offlineUrl()); });
    }

    private final Runnable retryOnline = new Runnable() {
        @Override public void run() {
            if (fallback && KioskConfig.MODE_ONLINE.equals(cfg.mode()) && online()) { fallback = false; web.loadUrl(cfg.onlineUrl()); }
            h.postDelayed(this, 10 * 60_000L);
        }
    };

    private boolean online() {
        try {
            ConnectivityManager cm = (ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE);
            if (Build.VERSION.SDK_INT >= 23) {
                NetworkCapabilities nc = cm.getNetworkCapabilities(cm.getActiveNetwork());
                return nc != null && nc.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED);
            }
            //noinspection deprecation
            return cm.getActiveNetworkInfo() != null && cm.getActiveNetworkInfo().isConnected();
        } catch (Exception e) { return true; }
    }

    private void scheduleDailyReload() {
        Calendar c = Calendar.getInstance();
        c.set(Calendar.HOUR_OF_DAY, cfg.reloadHour()); c.set(Calendar.MINUTE, 0); c.set(Calendar.SECOND, 0);
        if (c.getTimeInMillis() <= System.currentTimeMillis()) c.add(Calendar.DAY_OF_MONTH, 1);
        h.postDelayed(() -> { fallback = false; web.clearCache(false); web.loadUrl(cfg.startUrl()); scheduleDailyReload(); }, c.getTimeInMillis() - System.currentTimeMillis());
    }

    private void applyOrientation() {
        String o = cfg.orientation();
        setRequestedOrientation("landscape".equals(o) ? ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
                : "portrait".equals(o) ? ActivityInfo.SCREEN_ORIENTATION_SENSOR_PORTRAIT : ActivityInfo.SCREEN_ORIENTATION_FULL_USER);
    }

    /* ---------------- Khóa thiết bị ---------------- */
    private void enterLockTask() {
        try {
            DevicePolicyManager dpm = (DevicePolicyManager) getSystemService(Context.DEVICE_POLICY_SERVICE);
            ComponentName admin = new ComponentName(this, KioskAdminReceiver.class);
            if (dpm != null && dpm.isDeviceOwnerApp(getPackageName())) {
                dpm.setLockTaskPackages(admin, new String[]{ getPackageName() });
                IntentFilter home = new IntentFilter(Intent.ACTION_MAIN);
                home.addCategory(Intent.CATEGORY_HOME); home.addCategory(Intent.CATEGORY_DEFAULT);
                dpm.addPersistentPreferredActivity(admin, home, new ComponentName(this, MainActivity.class));
                if (Build.VERSION.SDK_INT >= 23) { dpm.setStatusBarDisabled(admin, true); dpm.setKeyguardDisabled(admin, true); }
                startLockTask();
            } else if (cfg.pinScreen()) {
                startLockTask();   // ghim màn hình: Android hỏi xác nhận một lần
            }
        } catch (Exception ignored) { }
    }

    /** Quay lại kiosk sau khi mở Cài đặt Android → khóa lại (chỉ khi là Device Owner, không làm phiền bằng hộp thoại ghim). */
    private void relock() {
        try {
            ActivityManager am = (ActivityManager) getSystemService(Context.ACTIVITY_SERVICE);
            DevicePolicyManager dpm = (DevicePolicyManager) getSystemService(Context.DEVICE_POLICY_SERVICE);
            if (am != null && dpm != null && dpm.isDeviceOwnerApp(getPackageName()) && am.getLockTaskModeState() == ActivityManager.LOCK_TASK_MODE_NONE) startLockTask();
        } catch (Exception ignored) { }
    }

    private void exitKiosk() {
        try {
            DevicePolicyManager dpm = (DevicePolicyManager) getSystemService(Context.DEVICE_POLICY_SERVICE);
            ComponentName admin = new ComponentName(this, KioskAdminReceiver.class);
            if (dpm != null && dpm.isDeviceOwnerApp(getPackageName()) && Build.VERSION.SDK_INT >= 23) dpm.setStatusBarDisabled(admin, false);
            ActivityManager am = (ActivityManager) getSystemService(Context.ACTIVITY_SERVICE);
            if (am != null && am.getLockTaskModeState() != ActivityManager.LOCK_TASK_MODE_NONE) stopLockTask();
        } catch (Exception ignored) { }
        finishAndRemoveTask();
    }

    /* ---------------- Menu quản trị ẩn ---------------- */
    private void cornerTap() {
        long now = System.currentTimeMillis();
        if (now - firstTap > 3000) { firstTap = now; taps = 0; }
        if (++taps >= 5) { taps = 0; askPin(); }
    }

    private void askPin() {
        if (System.currentTimeMillis() < cfg.lockedUntil()) { Toast.makeText(this, "Nhập sai nhiều lần – thử lại sau 5 phút", Toast.LENGTH_LONG).show(); return; }
        final EditText e = new EditText(this);
        e.setInputType(InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_VARIATION_PASSWORD);
        e.setHint("PIN quản trị");
        AlertDialog d = new AlertDialog.Builder(this).setTitle("Quản trị Xanh24 Kiosk").setView(e)
                .setPositiveButton("Mở", (di, w) -> { if (cfg.verifyPin(e.getText().toString().trim())) adminMenu(); else Toast.makeText(this, "PIN không đúng", Toast.LENGTH_SHORT).show(); })
                .setNegativeButton("Hủy", null).create();
        d.show();
        h.postDelayed(() -> { if (d.isShowing()) d.dismiss(); }, 30_000);
    }

    private void adminMenu() {
        String info = Hospitals.name(cfg.tenant()) + "\nMã màn hình: " + cfg.kioskCode() + "\nChế độ: " + (KioskConfig.MODE_OFFLINE.equals(cfg.mode()) ? "ngoại tuyến" : "trực tuyến") + (fallback ? " (đang dùng bản dự phòng – mất mạng)" : "") + "\nPhiên bản: " + BuildConfig.VERSION_NAME + " (" + BuildConfig.FLAVOR + ")";
        final boolean owner = isOwner();
        String[] items = owner ? new String[]{ "Cài đặt kiosk", "Tải lại nội dung", "Xóa bộ nhớ đệm & tải lại", "Mở Cài đặt Android", "Thoát chế độ kiosk", "Gỡ quyền Device Owner (trước khi thu hồi máy)" }
                : new String[]{ "Cài đặt kiosk", "Tải lại nội dung", "Xóa bộ nhớ đệm & tải lại", "Mở Cài đặt Android", "Thoát chế độ kiosk" };
        new AlertDialog.Builder(this).setTitle(info).setItems(items, (di, w) -> {
            switch (w) {
                case 0: startActivity(new Intent(this, SetupActivity.class)); break;
                case 1: fallback = false; web.loadUrl(cfg.startUrl()); break;
                case 2: fallback = false; web.clearCache(true); web.loadUrl(cfg.startUrl()); break;
                case 3: try { exitLockOnly(); startActivity(new Intent(Settings.ACTION_SETTINGS)); } catch (Exception ignored) { } break;
                case 4: exitKiosk(); break;
                default: new AlertDialog.Builder(this).setTitle("Gỡ quyền Device Owner?").setMessage("Máy sẽ không còn bị khóa ở chế độ kiosk. Chỉ làm khi thu hồi hoặc chuyển mục đích sử dụng máy.")
                        .setPositiveButton("Gỡ", (d2, w2) -> clearOwner()).setNegativeButton("Hủy", null).show();
            }
        }).setNegativeButton("Đóng", null).show();
    }

    private boolean isOwner() {
        try { DevicePolicyManager dpm = (DevicePolicyManager) getSystemService(Context.DEVICE_POLICY_SERVICE); return dpm != null && dpm.isDeviceOwnerApp(getPackageName()); } catch (Exception e) { return false; }
    }

    @SuppressWarnings("deprecation")
    private void clearOwner() {
        try {
            DevicePolicyManager dpm = (DevicePolicyManager) getSystemService(Context.DEVICE_POLICY_SERVICE);
            ComponentName admin = new ComponentName(this, KioskAdminReceiver.class);
            if (Build.VERSION.SDK_INT >= 23) { dpm.setStatusBarDisabled(admin, false); dpm.setKeyguardDisabled(admin, false); }
            dpm.clearPackagePersistentPreferredActivities(admin, getPackageName());
            exitLockOnly();
            dpm.clearDeviceOwnerApp(getPackageName());
            Toast.makeText(this, "Đã gỡ quyền Device Owner", Toast.LENGTH_LONG).show();
        } catch (Exception e) { Toast.makeText(this, "Không gỡ được: " + e.getMessage(), Toast.LENGTH_LONG).show(); }
    }

    private void exitLockOnly() {
        try { ActivityManager am = (ActivityManager) getSystemService(Context.ACTIVITY_SERVICE); if (am != null && am.getLockTaskModeState() != ActivityManager.LOCK_TASK_MODE_NONE) stopLockTask(); } catch (Exception ignored) { }
    }

    /* ---------------- Cầu nối JavaScript ---------------- */
    /** window.AndroidTTS.speak(text, rate) / stop() / ready() */
    private class TtsBridge {
        @JavascriptInterface public void speak(String text, float rate) {
            if (tts == null || !ttsReady || text == null) return;
            String t = text.length() > 3000 ? text.substring(0, 3000) : text;
            tts.setSpeechRate(Math.max(0.5f, Math.min(2f, rate)));
            tts.speak(t, TextToSpeech.QUEUE_FLUSH, null, "x24");
        }
        @JavascriptInterface public void stop() { if (tts != null) tts.stop(); }
        @JavascriptInterface public boolean ready() { return ttsReady; }
    }

    /** window.AndroidKiosk.version() / info() / exitApp() / openSettings() – exitApp/openSettings chỉ gọi sau khi đã nhập PIN trong trang Quản trị */
    private class KioskBridge {
        @JavascriptInterface public String version() { return BuildConfig.VERSION_NAME; }
        @JavascriptInterface public String info() { return cfg.infoJson(); }
        @JavascriptInterface public void exitApp() { runOnUiThread(MainActivity.this::askPin); }
        @JavascriptInterface public void openSettings() { runOnUiThread(MainActivity.this::askPin); }
    }

    @SuppressWarnings("deprecation")
    private void hideSystemUi() {
        View d = getWindow().getDecorView();
        d.setSystemUiVisibility(View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY | View.SYSTEM_UI_FLAG_FULLSCREEN | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                | View.SYSTEM_UI_FLAG_LAYOUT_STABLE | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN);
    }

    @Override public void onWindowFocusChanged(boolean hasFocus) { super.onWindowFocusChanged(hasFocus); if (hasFocus) hideSystemUi(); }

    @SuppressWarnings("deprecation")
    @Override public void onBackPressed() { if (web != null && web.canGoBack()) web.goBack(); }

    @Override protected void onResume() {
        super.onResume();
        if (web != null) { web.onResume(); applyOrientation(); relock(); }
        hideSystemUi();
    }

    @Override protected void onNewIntent(Intent intent) { super.onNewIntent(intent); if (cfg != null && cfg.setupDone() && web != null) { fallback = false; web.loadUrl(cfg.startUrl()); } }

    @Override protected void onPause() { if (web != null) web.onPause(); super.onPause(); }

    @Override protected void onDestroy() {
        h.removeCallbacksAndMessages(null);
        if (tts != null) { tts.stop(); tts.shutdown(); }
        if (web != null) web.destroy();
        super.onDestroy();
    }
}
