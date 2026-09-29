package vn.xanh24.kiosk;

import android.content.Context;
import android.content.SharedPreferences;
import android.net.Uri;

import org.json.JSONObject;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;

/**
 * Cấu hình của từng máy kiosk (lưu trong bộ nhớ riêng của ứng dụng, không sao lưu ra ngoài).
 * - Bản "dùng chung" (flavor all): người lắp đặt chọn bệnh viện khi cài đặt lần đầu; đổi lại cần PIN quản trị.
 * - Bản riêng từng bệnh viện (flavor pstw/pshn/sph/vmtc/pdg): bệnh viện cố định trong ứng dụng, không đổi được.
 */
public final class KioskConfig {
    public static final String MODE_ONLINE = "online";
    public static final String MODE_OFFLINE = "offline";
    public static final String ASSET_HOST = "appassets.androidplatform.net";

    private final SharedPreferences p;

    public KioskConfig(Context c) { p = c.getSharedPreferences("x24kiosk", Context.MODE_PRIVATE); }

    /** Bệnh viện cố định khi build (rỗng = bản dùng chung). */
    public static String lockedTenant() { return BuildConfig.LOCKED_TENANT; }
    public static boolean isLocked() { return BuildConfig.LOCKED_TENANT.length() > 0; }

    public boolean setupDone() { return p.getBoolean("setupDone", false) && tenant().length() > 0 && hasPin(); }
    public String tenant() { return isLocked() ? lockedTenant() : p.getString("tenant", ""); }
    public String kioskCode() { return p.getString("kiosk", ""); }
    public String mode() { return p.getString("mode", MODE_ONLINE); }
    public String domain() { return p.getString("domain", "https://xanh24.vn"); }
    public String orientation() { return p.getString("orientation", "auto"); }
    public int reloadHour() { return p.getInt("reloadHour", 4); }
    public String qrSecret() { return p.getString("qrSecret", ""); }
    public String sosEndpoint() { return p.getString("sosEndpoint", ""); }
    public String dutyPhone() { return p.getString("dutyPhone", ""); }
    public boolean pinScreen() { return p.getBoolean("pinScreen", false); }

    public void save(String tenant, String kiosk, String mode, String domain, String orientation, int reloadHour,
                     String qrSecret, String sosEndpoint, String dutyPhone, boolean pinScreen) {
        SharedPreferences.Editor e = p.edit();
        if (!isLocked()) e.putString("tenant", tenant);
        e.putString("kiosk", kiosk).putString("mode", mode).putString("domain", domain).putString("orientation", orientation)
                .putInt("reloadHour", reloadHour).putString("qrSecret", qrSecret).putString("sosEndpoint", sosEndpoint)
                .putString("dutyPhone", dutyPhone).putBoolean("pinScreen", pinScreen).putBoolean("setupDone", true).apply();
    }

    /* ---------------- PIN quản trị: lưu dạng băm SHA-256 + muối, khóa tạm khi nhập sai ---------------- */
    public boolean hasPin() { return p.getString("pinHash", "").length() > 0; }

    public void setPin(String pin) {
        byte[] salt = new byte[16];
        new SecureRandom().nextBytes(salt);
        String s = hex(salt);
        byte[] ws = new byte[8];
        new SecureRandom().nextBytes(ws);
        String w = hex(ws);
        /* PIN trang Quản trị trong phần mềm kiosk: cùng định dạng băm với web (sha256("x24pin|salt|pin")) – không lưu PIN gốc */
        p.edit().putString("pinSalt", s).putString("pinHash", sha256(s + ":" + pin))
                .putString("webPinSalt", w).putString("webPinHash", sha256("x24pin|" + w + "|" + pin)).apply();
    }

    public long lockedUntil() { return p.getLong("pinLockUntil", 0); }

    public boolean verifyPin(String pin) {
        if (System.currentTimeMillis() < lockedUntil()) return false;
        String h = sha256(p.getString("pinSalt", "") + ":" + (pin == null ? "" : pin));
        boolean ok = MessageDigest.isEqual(h.getBytes(StandardCharsets.UTF_8), p.getString("pinHash", "").getBytes(StandardCharsets.UTF_8));
        if (ok) { p.edit().putInt("pinFails", 0).apply(); return true; }
        int f = p.getInt("pinFails", 0) + 1;
        SharedPreferences.Editor e = p.edit().putInt("pinFails", f);
        if (f >= 5) e.putInt("pinFails", 0).putLong("pinLockUntil", System.currentTimeMillis() + 5 * 60_000L);
        e.apply();
        return false;
    }

    /* ---------------- Địa chỉ khởi động ---------------- */
    public String offlineUrl() {
        return "https://" + ASSET_HOST + "/assets/tenants/" + tenant() + "/index.html" + query();
    }

    public String onlineUrl() {
        String d = domain().replaceAll("/+$", "");
        return d + Hospitals.path(tenant()) + query();
    }

    public String startUrl() { return MODE_OFFLINE.equals(mode()) ? offlineUrl() : onlineUrl(); }

    private String query() { return kioskCode().length() > 0 ? "?kiosk=" + Uri.encode(kioskCode()) : ""; }

    /** Tệp x24-config.js sinh động cho bản chạy offline (khóa bệnh viện, PIN, địa chỉ QR, SOS). */
    public String configJs() {
        try {
            String d = domain().replaceAll("/+$", "");
            JSONObject o = new JSONObject();
            o.put("lockTenant", tenant());
            o.put("adminPinSalt", p.getString("webPinSalt", ""));
            o.put("adminPinHash", p.getString("webPinHash", ""));
            o.put("qrSecret", qrSecret());
            o.put("portal", d + "/phieu-kham/");
            o.put("publicBase", d + Hospitals.path(tenant()));
            o.put("kioskTimeout", 60);
            o.put("paymentGateway", false);
            JSONObject sos = new JSONObject();
            sos.put("enabled", true);
            sos.put("endpoint", sosEndpoint());
            sos.put("dutyPhone", dutyPhone());
            o.put("sos", sos);
            return "/* Sinh bởi ứng dụng Xanh24 Kiosk " + BuildConfig.VERSION_NAME + " */\nwindow.X24_CONFIG = " + o.toString(2) + ";\n";
        } catch (Exception e) {
            return "window.X24_CONFIG = { lockTenant: \"" + tenant() + "\" };";
        }
    }

    public String infoJson() {
        try {
            JSONObject o = new JSONObject();
            o.put("app", "xanh24-kiosk");
            o.put("version", BuildConfig.VERSION_NAME);
            o.put("flavor", BuildConfig.FLAVOR);
            o.put("tenant", tenant());
            o.put("kiosk", kioskCode());
            o.put("mode", mode());
            o.put("locked", isLocked());
            return o.toString();
        } catch (Exception e) { return "{}"; }
    }

    static String sha256(String s) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            return hex(md.digest(s.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) { return ""; }
    }

    static String hex(byte[] b) {
        StringBuilder sb = new StringBuilder();
        for (byte x : b) sb.append(String.format("%02x", x));
        return sb.toString();
    }
}
