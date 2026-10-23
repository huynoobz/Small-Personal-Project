"""Tạo mã QR ngoại tuyến với giao diện tiếng Việt."""
from datetime import datetime
from pathlib import Path
import sys
import tkinter as tk
from tkinter import ttk, filedialog, messagebox

import qrcode
from qrcode.exceptions import DataOverflowError
from PIL import Image, ImageTk


def create_qr(content):
    if not content.strip():
        raise ValueError("Hãy nhập văn bản hoặc đường dẫn cần tạo mã QR.")
    qr = qrcode.QRCode(error_correction=qrcode.constants.ERROR_CORRECT_M,
                       box_size=12, border=4)
    qr.add_data(content)
    try:
        qr.make(fit=True)
    except ValueError as error:
        # qrcode 8.2 may report version 41 for oversized input.
        raise DataOverflowError("Nội dung quá dài") from error
    return qr.make_image(fill_color="black", back_color="white").convert("RGB")


class QRApp:
    def __init__(self, root):
        self.root = root
        self.qr_image = None
        root.title("Tạo mã QR")
        root.geometry("640x800")
        root.minsize(560, 760)
        root.configure(bg="#f3f6fb")
        icon = Path(getattr(sys, "_MEIPASS", Path(__file__).parent)) / "qr_icon.ico"
        if icon.exists():
            root.iconbitmap(str(icon))
        style = ttk.Style(root)
        style.theme_use("clam")
        style.configure("TFrame", background="#f3f6fb")
        style.configure("TLabel", background="#f3f6fb", font=("Segoe UI", 11))
        style.configure("TButton", font=("Segoe UI", 11), padding=(12, 9))
        style.configure("Primary.TButton", background="#185adb", foreground="white")
        style.map("Primary.TButton", background=[("active", "#1245ab")])
        frame = ttk.Frame(root, padding=24)
        frame.pack(fill="both", expand=True)
        ttk.Label(frame, text="Tạo mã QR", font=("Segoe UI", 24, "bold")).pack(anchor="w")
        ttk.Label(frame, text="Biến văn bản hoặc đường dẫn thành mã QR.").pack(anchor="w", pady=(4, 20))
        ttk.Label(frame, text="1. Nhập nội dung", font=("Segoe UI", 12, "bold")).pack(anchor="w")
        self.content = tk.Text(frame, height=5, wrap="word", font=("Segoe UI", 12),
                               padx=12, pady=10, relief="solid", bd=1, undo=True)
        self.content.pack(fill="x", pady=8)
        self.content.bind("<<Modified>>", self.on_edit)
        actions = ttk.Frame(frame)
        actions.pack(fill="x")
        ttk.Button(actions, text="Dán nội dung", command=self.paste).pack(side="left")
        ttk.Button(actions, text="Xóa nội dung", command=self.clear).pack(side="left", padx=8)
        ttk.Button(frame, text="2. Tạo mã QR", style="Primary.TButton", command=self.generate).pack(fill="x", pady=(16, 12))
        self.preview = tk.Label(frame, text="Mã QR sẽ hiện ở đây", bg="white", fg="#667085", font=("Segoe UI", 11))
        self.preview.pack(fill="both", expand=True)
        self.save_button = ttk.Button(frame, text="3. Lưu ảnh QR…", command=self.save, state="disabled")
        self.save_button.pack(fill="x", pady=(12, 8))
        self.status = tk.StringVar(value="Nhập nội dung để bắt đầu. Không cần kết nối Internet.")
        ttk.Label(frame, textvariable=self.status, wraplength=510).pack(anchor="w")
        root.bind("<Control-Return>", lambda event: self.generate())
        self.content.focus_set()

    def on_edit(self, event=None):
        if self.content.edit_modified():
            self.qr_image = None
            self.save_button.configure(state="disabled")
            self.preview.configure(image="", text="Nhấn “Tạo mã QR” để xem nội dung mới")
            self.preview.image = None
            self.status.set("Sau khi nhập xong, nhấn “Tạo mã QR” (Ctrl + Enter).")
            self.content.edit_modified(False)

    def paste(self):
        try:
            value = self.root.clipboard_get()
        except tk.TclError:
            messagebox.showinfo("Chưa có nội dung", "Hãy sao chép văn bản hoặc đường dẫn trước khi nhấn Dán.")
            return
        self.content.delete("1.0", "end")
        self.content.insert("1.0", value)

    def clear(self):
        self.content.delete("1.0", "end")
        self.content.focus_set()

    def generate(self):
        self.on_edit()
        try:
            result = create_qr(self.content.get("1.0", "end-1c"))
        except ValueError as error:
            messagebox.showinfo("Cần nhập nội dung", str(error))
            self.content.focus_set()
            return
        except DataOverflowError:
            messagebox.showwarning("Nội dung quá dài", "Hãy rút ngắn văn bản rồi thử lại.")
            return
        self.qr_image = result
        photo = ImageTk.PhotoImage(result.resize((300, 300), Image.Resampling.NEAREST))
        self.preview.configure(image=photo, text="")
        self.preview.image = photo
        self.save_button.configure(state="normal")
        self.status.set("Đã tạo mã QR. Nhấn “Lưu ảnh QR…” để chọn nơi lưu.")

    def save(self):
        if self.qr_image is None:
            return
        filename = filedialog.asksaveasfilename(
            title="Chọn nơi lưu ảnh QR", defaultextension=".png",
            initialfile="Ma_QR_" + datetime.now().strftime("%Y-%m-%d_%H%M%S") + ".png",
            filetypes=[("Ảnh PNG", "*.png")])
        if not filename:
            return
        try:
            self.qr_image.save(filename, format="PNG")
        except OSError:
            messagebox.showerror("Không lưu được ảnh", "Hãy chọn thư mục khác hoặc kiểm tra quyền ghi.")
            return
        self.status.set("Đã lưu: " + filename)
        messagebox.showinfo("Lưu ảnh thành công", "Ảnh QR đã được lưu tại:\n" + filename)


if __name__ == "__main__":
    window = tk.Tk()
    QRApp(window)
    window.mainloop()
