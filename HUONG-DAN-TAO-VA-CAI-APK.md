# Hướng dẫn tạo và cài ứng dụng Xanh24 Kiosk (.apk)

Ứng dụng này thay thế Fully Kiosk Browser: không có hình mờ "PLUS Features Activated / Please Get a License", không mất phí bản quyền.

---

## Phần 1 – Chọn bản cài

| Trường hợp | Nên dùng | Tệp APK |
|---|---|---|
| Kỹ thuật viên đi lắp nhiều bệnh viện, muốn một tệp duy nhất | Bản **dùng chung** | `Xanh24-Kiosk-universal-2.0.0-release.apk` |
| Bàn giao cho một bệnh viện, không muốn ai đổi được bệnh viện | Bản **riêng** của bệnh viện đó | `…-pstw-…`, `…-pshn-…`, `…-sph-…`, `…-vmtc-…`, `…-pdg-…` |

- **Bản dùng chung:** lần đầu mở, người lắp đặt chọn bệnh viện. Từ đó kiosk chỉ hiện đúng bệnh viện này. Muốn đổi bệnh viện phải nhập PIN quản trị, và máy hỏi xác nhận trước khi đổi.
- **Bản riêng:** bệnh viện được cố định sẵn trong ứng dụng, không đổi được, kể cả khi biết PIN.

Hai bản cài song song trên cùng máy được (khác mã ứng dụng), nhưng mỗi máy kiosk **chỉ nên cài một bản**.

---

## Phần 2 – Tạo tệp APK

Máy chủ tạo APK cần có mạng để tải công cụ Android. Chọn một trong ba cách.

### Cách A – GitHub Actions (không cần cài gì trên máy tính, khuyến nghị)

1. Tạo tài khoản GitHub và một kho **Private**, ví dụ `xanh24-kiosk-android`.
2. Giải nén thư mục `xanh24-kiosk-android`. Trên trang kho, bấm **Add file › Upload files** rồi kéo **toàn bộ nội dung** thư mục vào.
   - Phải có thư mục ẩn `.github/workflows/build-apk.yml`. Trên Windows, bật hiện tệp ẩn trước khi kéo thả.
3. Mở tab **Actions** → chọn **Build Xanh24 Kiosk APK** → **Run workflow**. Chờ khoảng 5–8 phút.
4. Bấm vào lần chạy vừa xong → mục **Artifacts** → tải `Xanh24-Kiosk-APK.zip`. Trong đó có **6 tệp APK**.

Để ký APK bằng khóa chính thức (bắt buộc khi vận hành thật, xem Phần 3): vào **Settings › Secrets and variables › Actions › New repository secret** và thêm 4 mục:

| Tên | Giá trị |
|---|---|
| `X24_KEYSTORE_BASE64` | Nội dung tệp khóa `.jks` đã mã hóa base64. Windows PowerShell: `[Convert]::ToBase64String([IO.File]::ReadAllBytes("xanh24.jks")) \| Set-Clipboard` |
| `X24_KEYSTORE_PASSWORD` | Mật khẩu kho khóa |
| `X24_KEY_ALIAS` | Tên khóa, ví dụ `xanh24` |
| `X24_KEY_PASSWORD` | Mật khẩu khóa |

### Cách B – Android Studio

1. Cài **Android Studio** (bản mới nhất). Mở **File › Open** → chọn thư mục `xanh24-kiosk-android` → chờ đồng bộ Gradle.
   - Nếu được hỏi tạo Gradle Wrapper hoặc cài SDK 34, bấm đồng ý.
2. Mở **View › Tool Windows › Build Variants**. Chọn bản cần tạo, ví dụ `pdgRelease` hoặc `universalRelease`.
3. **Build › Generate Signed App Bundle / APK… › APK**.
   - Lần đầu: bấm **Create new…** để tạo kho khóa (Phần 3).
   - Chọn bản `release` → **Create**.
4. APK nằm ở `app/build/outputs/apk/<bản>/release/`.

### Cách C – Dòng lệnh (máy đã có JDK 17 và Android SDK)

```bash
cd xanh24-kiosk-android
cp keystore.properties.example keystore.properties   # điền đường dẫn & mật khẩu khóa
gradle assembleRelease            # tạo cả 6 bản
gradle assemblePdgRelease         # hoặc chỉ một bản
```

### Cập nhật nội dung kiosk đóng gói trong APK (khi phần mềm kiosk có phiên bản mới)

Từ thư mục dự án Xanh24 Smart Hospital:

```bash
for t in pstw pshn sph vmtc pdg; do python3 build_var.py $t prod /tmp/kb/$t.html; done
python3 xanh24-kiosk-android/tools/build_assets.py /tmp/kb
```

Sau đó tăng `versionCode` trong `app/build.gradle` (ví dụ 20 → 21) rồi tạo lại APK.

Nếu kiosk chạy **trực tuyến**, nội dung tự cập nhật từ máy chủ. Chỉ cần tạo lại APK khi muốn đổi bản dự phòng ngoại tuyến.

---

## Phần 3 – Khóa ký ứng dụng (quan trọng)

- Android chỉ cho **cập nhật đè** một ứng dụng khi APK mới được ký **cùng một khóa** với bản đang cài.
- **Mất khóa** thì phải gỡ ứng dụng trên từng máy rồi cài lại, và mọi cài đặt trên máy sẽ mất.
- **Tạo khóa một lần duy nhất.** Dùng Android Studio (Phần 2, Cách B), hoặc lệnh sau (có sẵn khi cài JDK):
  ```bash
  keytool -genkeypair -v -keystore xanh24.jks -alias xanh24 -keyalg RSA -keysize 2048 -validity 10000
  ```
- **Cất giữ** tệp `xanh24.jks` và mật khẩu ở 2 nơi an toàn, ví dụ két và kho mật khẩu của công ty.
- **Không** tải tệp khóa lên kho mã. Tệp `.gitignore` đã chặn sẵn.
- APK tạo **không có khóa** chính thức được ký bằng khóa gỡ lỗi. Loại APK này chỉ dùng để chạy thử, không dùng để triển khai.

---

## Phần 4 – Cài lên kiosk

### 4.1 Chuẩn bị máy (một lần)

1. Nếu đang dùng Fully Kiosk: thoát Kiosk Mode (nhập mật khẩu thoát) → **gỡ Fully Kiosk Browser**, hoặc bỏ Fully khỏi vai trò ứng dụng màn hình chính.
2. Cập nhật **Android System WebView** và **Google Chrome** lên bản mới nhất (qua Play Store hoặc tệp của nhà sản xuất).
3. Bật ngày giờ tự động. Tắt chế độ ngủ hoặc đặt thời gian tắt màn hình dài nhất.
4. Cài giọng đọc tiếng Việt: **Cài đặt › Hệ thống › Ngôn ngữ › Chuyển văn bản thành giọng nói › Google › Tải Tiếng Việt**.

### 4.2 Cài APK

**Cách 1 – Bằng USB**
1. Chép tệp APK vào USB.
2. Trên kiosk, mở ứng dụng Tệp → chạm vào tệp APK.
3. Nếu máy hỏi, cho phép **"Cài ứng dụng không rõ nguồn gốc"** cho ứng dụng Tệp → **Cài đặt**.

**Cách 2 – Bằng máy tính (adb)**
1. Trên kiosk: **Cài đặt › Giới thiệu › chạm 7 lần vào "Số bản dựng"** → vào **Tùy chọn nhà phát triển** → bật **Gỡ lỗi USB**.
2. Trên máy tính đã có Android Platform Tools:
   ```bash
   adb install -r Xanh24-Kiosk-pdg-2.0.0-release.apk
   ```

### 4.3 Cài đặt lần đầu (màn hình "Xanh24 Kiosk – Cài đặt màn hình")

| Mục | Điền |
|---|---|
| 1. Bệnh viện | Chọn bệnh viện (bản riêng đã cố định sẵn) |
| 2. Mã màn hình | Ví dụ `KIOSK-PDG-S1-01`; phải chứa mã bệnh viện: PSTW / PSHN / SPH / VMTC / PDG |
| 3. Chế độ chạy | **Trực tuyến** (khuyến nghị) + địa chỉ `https://xanh24.vn`; hoặc **Ngoại tuyến** |
| 4. PIN quản trị | 4–8 chữ số, không dùng 0000, 1234, 2468… |
| 5. Hướng màn hình | Tự động / Ngang / Dọc; giờ tải lại hằng ngày (mặc định 4 giờ sáng) |
| 6. Nâng cao | Khóa ký QR (giống máy chủ), địa chỉ nhận SOS, số trực của bệnh viện |

Bấm **Nghe thử giọng đọc tiếng Việt** để kiểm tra, rồi bấm **Lưu & chạy kiosk**.

Ở chế độ trực tuyến, kiosk mở các đường dẫn:

| Bệnh viện | Địa chỉ |
|---|---|
| Phụ sản Trung ương | `https://xanh24.vn/phu-san-tw/?kiosk=<mã>` |
| Phụ sản Hà Nội | `https://xanh24.vn/phu-san-hn/?kiosk=<mã>` |
| Xanh Pôn | `https://xanh24.vn/sph/?kiosk=<mã>` |
| Vinmec Times City | `https://xanh24.vn/vinmec/?kiosk=<mã>` |
| Phương Đông | `https://xanh24.vn/phuong-dong/?kiosk=<mã>` |

Hai đường dẫn Phụ sản đã có trong gói triển khai mới.

### 4.4 Khóa kiosk – chọn một trong hai mức

**Mức A – Device Owner (khóa hoàn toàn, khuyến nghị cho kiosk công cộng)**

Kết quả: không thoát ra Android được, không kéo thanh thông báo, nút Home và Back vô hiệu, bật máy là vào kiosk.

Điều kiện:
- máy mới, hoặc vừa **khôi phục cài đặt gốc**;
- **chưa đăng nhập tài khoản Google** hay tài khoản nào khác.

Các bước:
1. Bật Gỡ lỗi USB như mục 4.2, rồi cài APK bằng `adb install`.
2. Chạy lệnh tương ứng với bản đã cài:
   ```bash
   # bản dùng chung
   adb shell dpm set-device-owner vn.xanh24.kiosk.all/vn.xanh24.kiosk.KioskAdminReceiver
   # bản riêng: thay .all bằng .pstw / .pshn / .sph / .vmtc / .pdg, ví dụ
   adb shell dpm set-device-owner vn.xanh24.kiosk.pdg/vn.xanh24.kiosk.KioskAdminReceiver
   ```
   Máy báo `Success` là xong.
3. Mở ứng dụng → làm bước Cài đặt lần đầu (4.3).
4. **Tắt Gỡ lỗi USB** sau khi xong.

Nếu máy báo lỗi *"already several accounts on the device"*: vào **Cài đặt › Tài khoản**, xóa hết tài khoản, rồi chạy lại lệnh.

**Mức B – Không cần máy tính**

1. **Cài đặt › Ứng dụng › Ứng dụng mặc định › Ứng dụng màn hình chính** → chọn **Xanh24 Kiosk**. Nút Home luôn quay về kiosk, và bật máy là vào kiosk.
2. Tùy chọn: trong Cài đặt kiosk, bật **Ghim màn hình**. Android sẽ hỏi xác nhận một lần.

Mức này người rành Android vẫn có thể thoát ra, nên chỉ phù hợp nơi có nhân viên trông coi.

---

## Phần 5 – Vận hành

**Menu quản trị:** chạm **5 lần trong 3 giây vào góc trên bên trái** màn hình → nhập PIN. Menu gồm:
- Cài đặt kiosk
- Tải lại nội dung
- Xóa bộ nhớ đệm
- Mở Cài đặt Android
- Thoát chế độ kiosk
- Gỡ quyền Device Owner (chỉ hiện với máy đã đặt quyền này)

Nhập sai PIN 5 lần, menu khóa 5 phút.

**Trang Quản trị trong phần mềm kiosk** (chạm logo 5 lần, dùng cùng PIN):
- Map Studio › ★ Vị trí kiosk: đặt vị trí "Bạn đang ở đây" cho máy này.
- DOOH/Signage.
- SOS.

**Mất mạng:** kiosk tự chuyển sang bản đóng gói. Mỗi 10 phút, máy kiểm tra lại và tự quay về bản trực tuyến khi có mạng.

**Cập nhật ứng dụng:** cài APK mới (cùng khóa, `versionCode` lớn hơn) đè lên bản cũ bằng USB hoặc `adb install -r`. Mọi cài đặt được giữ nguyên, và ứng dụng tự mở lại sau khi cập nhật.

**Thu hồi máy:** vào menu quản trị → **Gỡ quyền Device Owner** → gỡ ứng dụng. Hoặc khôi phục cài đặt gốc.

---

## Phần 6 – Nghiệm thu từng máy

- [ ] Không còn hình mờ của Fully Kiosk
- [ ] Mở đúng bệnh viện, đúng mã màn hình (menu quản trị hiện thông tin)
- [ ] Bấm Home, Back, kéo thanh thông báo đều không thoát ra được (Mức A)
- [ ] Tắt/bật nguồn: kiosk tự mở lại
- [ ] Rút mạng: kiosk vẫn chạy bản đóng gói; cắm lại mạng: trong 10 phút tự về bản trực tuyến
- [ ] Giọng đọc tiếng Việt, video có tiếng
- [ ] Quét QR lộ trình và phiếu khám bằng iPhone và Android
- [ ] Đã đổi PIN, ghi mã màn hình vào biên bản bàn giao

## Xử lý sự cố

| Hiện tượng | Cách xử lý |
|---|---|
| Màn hình trắng hoặc lỗi hiển thị | Cập nhật Android System WebView; menu quản trị → Xóa bộ nhớ đệm & tải lại |
| Không có giọng đọc | Cài gói giọng Tiếng Việt (mục 4.1); kiểm tra bằng nút Nghe thử |
| `dpm set-device-owner` báo lỗi | Máy còn tài khoản hoặc đã có ứng dụng Device Owner khác (ví dụ Fully): gỡ, hoặc khôi phục cài đặt gốc |
| Không cài đè được APK mới | APK mới ký khác khóa: dùng đúng `xanh24.jks`, hoặc gỡ bản cũ rồi cài lại |
| Quên PIN | Máy Mức B: gỡ và cài lại ứng dụng. Máy Mức A: khôi phục cài đặt gốc |
