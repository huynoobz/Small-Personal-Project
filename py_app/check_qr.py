"""Kiểm tra tạo mã, lưu ảnh và trạng thái giao diện: python py_app/check_qr.py."""
from pathlib import Path
from tempfile import TemporaryDirectory
import tkinter as tk
from tkinter import filedialog, messagebox
from qrcode.exceptions import DataOverflowError
from str2qr import QRApp, create_qr

assert create_qr("Xin chào Việt Nam").width > 0
for value, error in [("   ", ValueError), ("x" * 10000, DataOverflowError)]:
    try:
        create_qr(value)
    except error:
        pass
    else:
        raise AssertionError("Invalid content accepted")
root = tk.Tk()
root.withdraw()
try:
    app = QRApp(root)
    app.content.insert("1.0", "Xin chào Việt Nam")
    root.update()
    app.generate()
    root.update()
    assert app.qr_image is not None
    assert str(app.save_button["state"]) == "normal"
    with TemporaryDirectory() as folder:
        target = Path(folder) / "Ảnh QR.png"
        filedialog.asksaveasfilename = lambda **kwargs: str(target)
        messagebox.showinfo = lambda *args: None
        app.save()
        assert target.exists()
    app.content.insert("end", " mới")
    root.update()
    assert app.qr_image is None
    assert str(app.save_button["state"]) == "disabled"
finally:
    root.destroy()
print("PASS: Unicode, empty/overflow, GUI generation, PNG save, edit invalidation")
