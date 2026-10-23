"""Chạy bằng Python có qrcode, Pillow và PyInstaller để tạo bản Windows."""
from pathlib import Path
import subprocess
import sys
from PIL import Image, ImageDraw

base = Path(__file__).resolve().parent
icon = Image.new("RGB", (256, 256), "#185adb")
draw = ImageDraw.Draw(icon)
draw.rounded_rectangle((20, 20, 236, 236), radius=26, fill="white")
for x, y in [(42, 42), (142, 42), (42, 142)]:
    draw.rectangle((x, y, x + 70, y + 70), fill="#142c54")
    draw.rectangle((x + 10, y + 10, x + 60, y + 60), fill="white")
    draw.rectangle((x + 22, y + 22, x + 48, y + 48), fill="#142c54")
for x, y in [(142, 142), (182, 142), (162, 162), (202, 162), (142, 182), (182, 202), (202, 202)]:
    draw.rectangle((x, y, x + 15, y + 15), fill="#142c54")
icon.save(base / "qr_icon.ico", sizes=[(16, 16), (32, 32), (48, 48), (64, 64), (128, 128), (256, 256)])
subprocess.run([
    sys.executable, "-m", "PyInstaller", "--noconfirm", "--clean", "--onefile",
    "--windowed", "--name", "TaoMaQR", "--icon", str(base / "qr_icon.ico"),
    "--add-data", str(base / "qr_icon.ico") + ";.",
    "--distpath", str(base / "dist"), "--workpath", str(base / "build"),
    "--specpath", str(base), str(base / "str2qr.py"),
], check=True)
