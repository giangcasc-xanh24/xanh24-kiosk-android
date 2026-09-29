#!/usr/bin/env python3
"""tools/build_assets.py – đóng gói nội dung kiosk của 5 bệnh viện vào ứng dụng (chạy offline).
Dùng: python3 tools/build_assets.py <thư mục chứa pstw.html pshn.html sph.html vmtc.html pdg.html>
Các tệp nguồn là bản build 'prod' từng bệnh viện của Xanh24 Smart Hospital (build_var.py <mã> prod <tệp>).
Kết quả: app/src/main/assets/tenants/<mã>/index.html (cấu hình x24-config.js do ứng dụng tự sinh theo phần Cài đặt)."""
import os, sys
SRC = sys.argv[1] if len(sys.argv) > 1 else "."
OUT = os.path.join(os.path.dirname(os.path.abspath(__file__)), "..", "app", "src", "main", "assets", "tenants")
for t in ["pstw", "pshn", "sph", "vmtc", "pdg"]:
    s = open(os.path.join(SRC, t + ".html"), encoding="utf-8").read()
    for a, b in [('<link rel="manifest" href="/manifest.webmanifest">', ''),
                 ('window.X24_SW = true; window.X24_DEFAULT_MODE = "__X24MODE__";', 'window.X24_SW = false; window.X24_DEFAULT_MODE = "kiosk"; window.X24_APK = true;'),
                 ('<script src="/x24-config.js"></script>', '<script src="x24-config.js"></script>')]:
        assert a in s, (t, a[:40]); s = s.replace(a, b, 1)
    d = os.path.join(OUT, t); os.makedirs(d, exist_ok=True)
    open(os.path.join(d, "index.html"), "w", encoding="utf-8").write(s)
    print(t, "→", os.path.relpath(os.path.join(d, "index.html")), len(s) // 1024, "KB")
