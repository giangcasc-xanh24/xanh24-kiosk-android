package vn.xanh24.kiosk;

/** Danh sách 5 bệnh viện liên kết: mã phần mềm, tên, tiền tố mã màn hình, đường dẫn bản chạy trực tuyến. */
public final class Hospitals {
    public static final String[] CODES = { "pstw", "pshn", "sph", "vmtc", "pdg" };
    public static final String[] NAMES = {
            "Bệnh viện Phụ sản Trung ương",
            "Bệnh viện Phụ sản Hà Nội",
            "Bệnh viện Đa khoa Xanh Pôn",
            "Bệnh viện ĐKQT Vinmec Times City",
            "Bệnh viện Đa khoa Phương Đông" };
    public static final String[] PREFIX = { "PSTW", "PSHN", "SPH", "VMTC", "PDG" };
    /** Đường dẫn trên máy chủ (vd https://xanh24.vn/phuong-dong/) – mỗi đường dẫn chỉ chứa dữ liệu một bệnh viện. */
    public static final String[] PATH = { "/phu-san-tw/", "/phu-san-hn/", "/sph/", "/vinmec/", "/phuong-dong/" };

    private Hospitals() { }

    public static int index(String code) {
        for (int i = 0; i < CODES.length; i++) if (CODES[i].equals(code)) return i;
        return -1;
    }

    public static String name(String code) { int i = index(code); return i < 0 ? code : NAMES[i]; }
    public static String prefix(String code) { int i = index(code); return i < 0 ? "X24" : PREFIX[i]; }
    public static String path(String code) { int i = index(code); return i < 0 ? "/kiosk/" : PATH[i]; }
}
