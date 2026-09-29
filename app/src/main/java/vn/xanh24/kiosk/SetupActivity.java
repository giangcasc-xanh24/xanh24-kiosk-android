package vn.xanh24.kiosk;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.Typeface;
import android.os.Bundle;
import android.speech.tts.TextToSpeech;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.ScrollView;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import java.util.Locale;
import java.util.regex.Pattern;

/**
 * Màn hình cài đặt kiosk (lần đầu, hoặc mở lại từ menu quản trị sau khi nhập PIN).
 * Chọn bệnh viện (bản dùng chung), mã màn hình, chế độ chạy, máy chủ, PIN quản trị, hướng màn hình, giờ tải lại.
 */
public class SetupActivity extends Activity {
    private static final int NAVY = Color.parseColor("#0A2240"), TEAL = Color.parseColor("#0B8F87"), MUTED = Color.parseColor("#5B6B84");
    private static final Pattern KIOSK_CODE = Pattern.compile("^(KIOSK|DOOH)-[A-Z0-9]{2,6}-[A-Z0-9]{1,6}-\\d{2}$");

    private KioskConfig cfg;
    private Spinner hospital;
    private EditText kiosk, domain, pin, pin2, reload, qr, sos, duty;
    private RadioGroup mode, orient;
    private CheckBox pinScreen;
    private TextToSpeech tts;

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        cfg = new KioskConfig(this);
        ScrollView sv = new ScrollView(this);
        sv.setBackgroundColor(Color.WHITE);
        LinearLayout f = new LinearLayout(this);
        f.setOrientation(LinearLayout.VERTICAL);
        int pad = dp(28);
        f.setPadding(pad, pad, pad, pad);
        sv.addView(f);
        setContentView(sv);

        title(f, "Xanh24 Kiosk – Cài đặt màn hình", 26);
        note(f, "Phiên bản " + BuildConfig.VERSION_NAME + (KioskConfig.isLocked() ? " • Bản riêng: " + Hospitals.name(KioskConfig.lockedTenant()) : " • Bản dùng chung 5 bệnh viện"));

        /* 1. Bệnh viện */
        section(f, "1. Bệnh viện đặt màn hình");
        hospital = new Spinner(this);
        hospital.setAdapter(new ArrayAdapter<>(this, android.R.layout.simple_spinner_dropdown_item, Hospitals.NAMES));
        int idx = Hospitals.index(cfg.tenant());
        if (idx >= 0) hospital.setSelection(idx);
        if (KioskConfig.isLocked()) { hospital.setEnabled(false); note(f, "Ứng dụng này chỉ dành cho " + Hospitals.name(KioskConfig.lockedTenant()) + "."); }
        else note(f, "Kiosk chỉ hiển thị phần mềm của bệnh viện được chọn. Muốn đổi bệnh viện phải nhập PIN quản trị.");
        f.addView(hospital, lp());

        /* 2. Mã màn hình */
        section(f, "2. Mã màn hình");
        kiosk = input(f, cfg.kioskCode().length() > 0 ? cfg.kioskCode() : "", "vd: KIOSK-PDG-S1-01", InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_CHARACTERS);
        note(f, "Quy tắc: KIOSK-<MÃ BV>-<KHU>-<SỐ> cho kiosk cảm ứng, DOOH-… cho màn hình chỉ trình chiếu. Dán nhãn mã này lên thân máy.");
        if (cfg.kioskCode().length() == 0) hospital.setOnItemSelectedListener(new android.widget.AdapterView.OnItemSelectedListener() {
            @Override public void onItemSelected(android.widget.AdapterView<?> a, View v, int i, long id) { if (kiosk.getText().toString().startsWith("KIOSK-") || kiosk.length() == 0) kiosk.setText("KIOSK-" + Hospitals.PREFIX[i] + "-S1-01"); }
            @Override public void onNothingSelected(android.widget.AdapterView<?> a) { }
        });

        /* 3. Chế độ chạy */
        section(f, "3. Chế độ chạy");
        mode = new RadioGroup(this);
        RadioButton on = radio(mode, 1, "Trực tuyến (khuyến nghị) – tự cập nhật bản đồ, video, gửi cảnh báo SOS; mất mạng tự chuyển sang bản offline");
        RadioButton off = radio(mode, 2, "Ngoại tuyến – dùng nội dung đóng gói trong ứng dụng");
        (KioskConfig.MODE_OFFLINE.equals(cfg.mode()) ? off : on).setChecked(true);
        f.addView(mode, lp());
        domain = input(f, cfg.domain(), "https://xanh24.vn", InputType.TYPE_TEXT_VARIATION_URI);
        note(f, "Địa chỉ máy chủ Xanh24 Smart Hospital (bắt buộc https).");

        /* 4. PIN */
        section(f, "4. Mã PIN quản trị");
        pin = input(f, "", cfg.hasPin() ? "Để trống nếu giữ PIN cũ" : "4–8 chữ số", InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_VARIATION_PASSWORD);
        pin2 = input(f, "", "Nhập lại PIN", InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_VARIATION_PASSWORD);
        note(f, "Dùng để mở menu quản trị của ứng dụng (chạm 5 lần vào góc trên bên trái) và trang Quản trị trong phần mềm kiosk. Không dùng 0000, 1234, 2468…");

        /* 5. Màn hình */
        section(f, "5. Hướng màn hình & tải lại hằng ngày");
        orient = new RadioGroup(this);
        orient.setOrientation(LinearLayout.HORIZONTAL);
        RadioButton oa = radio(orient, 11, "Tự động"), ol = radio(orient, 12, "Ngang"), op = radio(orient, 13, "Dọc");
        ("landscape".equals(cfg.orientation()) ? ol : "portrait".equals(cfg.orientation()) ? op : oa).setChecked(true);
        f.addView(orient, lp());
        reload = input(f, String.valueOf(cfg.reloadHour()), "Giờ tải lại (0–23)", InputType.TYPE_CLASS_NUMBER);
        note(f, "Kiosk tải lại nội dung mỗi ngày vào giờ này (mặc định 4 giờ sáng) để nhận bản đồ, video mới.");
        pinScreen = new CheckBox(this);
        pinScreen.setText("Ghim màn hình (chỉ dùng khi chưa đặt ứng dụng làm Device Owner – xem hướng dẫn)");
        pinScreen.setChecked(cfg.pinScreen());
        f.addView(pinScreen, lp());

        /* 6. Nâng cao */
        section(f, "6. Nâng cao (không bắt buộc)");
        qr = input(f, cfg.qrSecret(), "Khóa ký mã QR (qrSecret, ≥ 32 ký tự, giống máy chủ)", InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
        sos = input(f, cfg.sosEndpoint(), "Địa chỉ nhận cảnh báo SOS (https://…)", InputType.TYPE_TEXT_VARIATION_URI);
        duty = input(f, cfg.dutyPhone(), "Số điện thoại trực của bệnh viện", InputType.TYPE_CLASS_PHONE);

        Button tbtn = button(f, "Nghe thử giọng đọc tiếng Việt", false);
        tbtn.setOnClickListener(v -> testVoice());
        Button save = button(f, "Lưu & chạy kiosk", true);
        save.setOnClickListener(v -> save());
        note(f, "© Công ty TNHH Công nghệ và Truyền thông Xanh24");
    }

    private void save() {
        String t = Hospitals.CODES[hospital.getSelectedItemPosition()];
        String k = kiosk.getText().toString().trim().toUpperCase(Locale.ROOT);
        String d = domain.getText().toString().trim().replaceAll("/+$", "");
        String p1 = pin.getText().toString().trim(), p2 = pin2.getText().toString().trim();
        if (!KIOSK_CODE.matcher(k).matches()) { err("Mã màn hình chưa đúng quy tắc, vd KIOSK-" + Hospitals.prefix(t) + "-S1-01"); return; }
        if (!k.contains("-" + Hospitals.prefix(t) + "-")) { err("Mã màn hình phải chứa mã bệnh viện " + Hospitals.prefix(t)); return; }
        if (!d.startsWith("https://") || d.length() < 12) { err("Địa chỉ máy chủ phải bắt đầu bằng https://"); return; }
        if (p1.length() > 0 || !cfg.hasPin()) {
            if (!p1.matches("\\d{4,8}")) { err("PIN gồm 4–8 chữ số"); return; }
            if (p1.matches("(\\d)\\1+") || "1234".equals(p1) || "12345678".equals(p1) || "2468".equals(p1) || "123456".equals(p1)) { err("PIN quá dễ đoán"); return; }
            if (!p1.equals(p2)) { err("Hai lần nhập PIN không khớp"); return; }
        }
        int h;
        try { h = Integer.parseInt(reload.getText().toString().trim()); } catch (Exception e) { h = -1; }
        if (h < 0 || h > 23) { err("Giờ tải lại từ 0 đến 23"); return; }
        String q = qr.getText().toString().trim(), s = sos.getText().toString().trim();
        if (q.length() > 0 && q.length() < 32) { err("Khóa ký QR cần ít nhất 32 ký tự"); return; }
        if (s.length() > 0 && !s.startsWith("https://")) { err("Địa chỉ SOS phải bắt đầu bằng https://"); return; }
        int oid = orient.getCheckedRadioButtonId();
        String o = oid == 12 ? "landscape" : oid == 13 ? "portrait" : "auto";
        String m = mode.getCheckedRadioButtonId() == 2 ? KioskConfig.MODE_OFFLINE : KioskConfig.MODE_ONLINE;
        if (!KioskConfig.isLocked() && cfg.setupDone() && !t.equals(cfg.tenant())) {
            new AlertDialog.Builder(this).setTitle("Đổi bệnh viện?")
                    .setMessage("Kiosk sẽ chuyển từ " + Hospitals.name(cfg.tenant()) + " sang " + Hospitals.name(t) + ". Dữ liệu vị trí “Bạn đang ở đây” của máy cần đặt lại trong Map Studio.")
                    .setPositiveButton("Đồng ý", (di, w) -> commit(t, k, m, d, o, 0 + hFinal(), q, s, p1))
                    .setNegativeButton("Hủy", null).show();
            return;
        }
        commit(t, k, m, d, o, hFinal(), q, s, p1);
    }

    private int hFinal() { try { return Integer.parseInt(reload.getText().toString().trim()); } catch (Exception e) { return 4; } }

    private void commit(String t, String k, String m, String d, String o, int h, String q, String s, String p1) {
        if (p1.length() > 0) cfg.setPin(p1);
        cfg.save(t, k, m, d, o, h, q, s, duty.getText().toString().trim(), pinScreen.isChecked());
        Toast.makeText(this, "Đã lưu cài đặt", Toast.LENGTH_SHORT).show();
        Intent i = new Intent(this, MainActivity.class);
        i.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_NEW_TASK);
        startActivity(i);
        finish();
    }

    private void testVoice() {
        if (tts != null) { tts.shutdown(); tts = null; }
        tts = new TextToSpeech(this, st -> {
            if (st != TextToSpeech.SUCCESS) { err("Máy chưa có bộ đọc văn bản. Cài Google Speech Services."); return; }
            int r = tts.setLanguage(new Locale("vi", "VN"));
            if (r == TextToSpeech.LANG_MISSING_DATA || r == TextToSpeech.LANG_NOT_SUPPORTED) { err("Chưa có giọng tiếng Việt: Cài đặt › Ngôn ngữ › Chuyển văn bản thành giọng nói › tải Tiếng Việt."); return; }
            tts.speak("Xin chào, Xanh24 Smart Hospital sẵn sàng hỗ trợ bạn.", TextToSpeech.QUEUE_FLUSH, null, "test");
        });
    }

    private void err(String m) { runOnUiThread(() -> new AlertDialog.Builder(this).setMessage(m).setPositiveButton("Đã hiểu", null).show()); }

    /* ---------------- dựng giao diện ---------------- */
    private int dp(int v) { return Math.round(v * getResources().getDisplayMetrics().density); }
    private LinearLayout.LayoutParams lp() { LinearLayout.LayoutParams l = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT); l.topMargin = dp(6); return l; }
    private void title(LinearLayout f, String s, int size) { TextView t = new TextView(this); t.setText(s); t.setTextSize(size); t.setTypeface(Typeface.DEFAULT_BOLD); t.setTextColor(NAVY); f.addView(t, lp()); }
    private void section(LinearLayout f, String s) { TextView t = new TextView(this); t.setText(s); t.setTextSize(19); t.setTypeface(Typeface.DEFAULT_BOLD); t.setTextColor(NAVY); LinearLayout.LayoutParams l = lp(); l.topMargin = dp(22); f.addView(t, l); }
    private void note(LinearLayout f, String s) { TextView t = new TextView(this); t.setText(s); t.setTextSize(14); t.setTextColor(MUTED); f.addView(t, lp()); }
    private EditText input(LinearLayout f, String v, String hint, int type) { EditText e = new EditText(this); e.setText(v); e.setHint(hint); e.setInputType(type); e.setTextSize(18); e.setSingleLine(true); f.addView(e, lp()); return e; }
    private RadioButton radio(RadioGroup g, int id, String s) { RadioButton r = new RadioButton(this); r.setId(id); r.setText(s); r.setTextSize(16); g.addView(r); return r; }
    private Button button(LinearLayout f, String s, boolean primary) {
        Button b = new Button(this); b.setText(s); b.setAllCaps(false); b.setTextSize(18);
        b.setTextColor(primary ? Color.WHITE : NAVY); b.setBackgroundColor(primary ? NAVY : Color.parseColor("#EEF3F8"));
        LinearLayout.LayoutParams l = lp(); l.topMargin = dp(16); l.height = dp(60); f.addView(b, l); b.setGravity(Gravity.CENTER); return b;
    }

    @Override
    protected void onDestroy() { if (tts != null) tts.shutdown(); super.onDestroy(); }

    @SuppressWarnings("deprecation")
    @Override
    public void onBackPressed() {
        if (cfg.setupDone()) super.onBackPressed(); /* lần đầu: không thoát khi chưa cài đặt xong */
    }
}
