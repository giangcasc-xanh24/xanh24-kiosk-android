# Xanh24 Kiosk 2.0 – ứng dụng Android cho kiosk & standee (5 bệnh viện)

Vỏ WebView toàn màn hình chạy **Xanh24 Smart Hospital**, thay cho Fully Kiosk Browser (không hình mờ, không phí bản quyền).

| Bản cài | Tên trên máy | Dùng cho |
|---|---|---|
| `universal` | Xanh24 Kiosk | Dùng chung – chọn bệnh viện khi cài đặt lần đầu (đổi lại cần PIN) |
| `pstw` | Xanh24 Kiosk PSTW | Chỉ Bệnh viện Phụ sản Trung ương |
| `pshn` | Xanh24 Kiosk PSHN | Chỉ Bệnh viện Phụ sản Hà Nội |
| `sph` | Xanh24 Kiosk Xanh Pôn | Chỉ Bệnh viện Đa khoa Xanh Pôn |
| `vmtc` | Xanh24 Kiosk Vinmec | Chỉ Vinmec Times City |
| `pdg` | Xanh24 Kiosk Phương Đông | Chỉ Bệnh viện Đa khoa Phương Đông |

Tính năng chính:
- **Khóa đúng bệnh viện.** Chỉ mở trang của bệnh viện đã chọn; mọi đường dẫn khác bị chặn, kể cả trang của bệnh viện khác.
- **Chạy trực tuyến hoặc ngoại tuyến.** Ở chế độ trực tuyến, mất mạng thì tự chuyển sang nội dung đóng gói; có mạng lại thì tự quay về.
- **Khóa thiết bị.** Dùng quyền Device Owner (khóa hoàn toàn) hoặc ghim màn hình.
- **Tự vận hành.** Tự mở khi bật máy, tự tải lại hằng ngày, tự dựng lại khi trình hiển thị lỗi.
- **Giọng đọc tiếng Việt** qua Text-to-Speech của Android.
- **Menu quản trị ẩn.** Chạm 5 lần vào góc trên bên trái, rồi nhập PIN.

Yêu cầu:
- Máy chạy Android 8.0 trở lên.
- Build với compileSdk 34, Java 17, Android Gradle Plugin 8.5, Gradle 8.9.

**Đọc `HUONG-DAN-TAO-VA-CAI-APK.md` để tạo và cài APK.**

© Công ty TNHH Công nghệ và Truyền thông Xanh24
