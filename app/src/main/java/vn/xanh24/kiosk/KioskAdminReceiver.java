package vn.xanh24.kiosk;

import android.app.admin.DeviceAdminReceiver;

/**
 * Quyền quản trị thiết bị. Khi đặt ứng dụng làm Device Owner (adb shell dpm set-device-owner
 * vn.xanh24.kiosk.all/vn.xanh24.kiosk.KioskAdminReceiver – thay ".all" bằng ".pdg", ".vmtc"… với bản riêng), kiosk khóa hoàn toàn: không thoát ra Android,
 * không kéo thanh trạng thái, tự là màn hình chính. Xem HUONG-DAN-TAO-VA-CAI-APK.md.
 */
public class KioskAdminReceiver extends DeviceAdminReceiver { }
