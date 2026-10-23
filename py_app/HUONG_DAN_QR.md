# Tạo mã QR

Mở `dist/TaoMaQR.exe` bằng cách nhấp đúp. Máy Windows không cần cài Python.

1. Nhập văn bản hoặc đường dẫn, hoặc sao chép rồi nhấn **Dán nội dung**.
2. Nhấn **Tạo mã QR** (có thể dùng Ctrl + Enter).
3. Nhấn **Lưu ảnh QR…**, chọn thư mục và nhấn **Save/Lưu**.

Ứng dụng hoạt động ngoại tuyến. Ảnh được lưu ở định dạng PNG. Hãy dùng điện thoại quét thử ảnh trước khi in hoặc chia sẻ. Nếu sửa nội dung, cần tạo lại mã QR trước khi lưu.

## Chạy và đóng gói từ mã nguồn

Dùng Python trên Windows có hỗ trợ Tkinter:

```powershell
python -m pip install -r py_app/requirements-qr.txt
python py_app/str2qr.py
python py_app/build_qr.py
```

Lệnh cuối tạo biểu tượng `py_app/qr_icon.ico` và file `py_app/dist/TaoMaQR.exe`.
