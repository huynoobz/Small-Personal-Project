import qrcode
from datetime import datetime

qrcode.make(input("Enter string: ")).save("QR "+datetime.now().strftime("%Y-%m-%d %H%M%S")+".png")
print("Saved \"QR "+datetime.now().strftime("%Y-%m-%d %H%M%S")+".png\"")