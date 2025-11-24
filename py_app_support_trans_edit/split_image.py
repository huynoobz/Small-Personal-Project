from tkinter import Tk, filedialog
from PIL import Image
import os

# Hide the root window
root = Tk()
root.withdraw()

# Open file dialog
file_path = filedialog.askopenfilename(
    title="Select an image",
    filetypes=[("Image files", "*.jpg *.jpeg *.png *.bmp *.tiff *.webp")]
)

if not file_path:
    print("No file selected.")
    exit()

# Open the image
img = Image.open(file_path)

# Get original name and extension
folder, filename = os.path.split(file_path)
basename, ext = os.path.splitext(filename)

# Get size
width, height = img.size

# Calculate new height
new_height = height // 2

# Define boxes
box1 = (0, 0, width, new_height)
box2 = (0, new_height, width, height)

# Crop
img1 = img.crop(box1)
img2 = img.crop(box2)

# Create new filenames
output1 = os.path.join(folder, f"{basename}_1{ext}")
output2 = os.path.join(folder, f"{basename}_2{ext}")

# Save
img1.save(output1)
img2.save(output2)

print(f"Image split done!\nSaved:\n{output1}\n{output2}")
