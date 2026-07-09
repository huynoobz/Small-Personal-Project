import cv2
import numpy as np
import os

# Input image
INPUT_IMAGE = input("Enter image filename: ")

# Output folder
OUTPUT_DIR = "signatures"
os.makedirs(OUTPUT_DIR, exist_ok=True)

# Load image
img = cv2.imread(INPUT_IMAGE)

# Convert to grayscale
gray = cv2.cvtColor(img, cv2.COLOR_BGR2GRAY)

# Threshold (invert so handwriting becomes white)
_, thresh = cv2.threshold(
    gray,
    0,
    255,
    cv2.THRESH_BINARY_INV + cv2.THRESH_OTSU
)

# Remove horizontal table lines
horizontal_kernel = cv2.getStructuringElement(cv2.MORPH_RECT, (40, 1))
remove_lines = cv2.morphologyEx(
    thresh,
    cv2.MORPH_OPEN,
    horizontal_kernel,
    iterations=1
)

# Subtract detected lines
clean = cv2.subtract(thresh, remove_lines)

# Dilate slightly to connect strokes
kernel = np.ones((3, 3), np.uint8)
clean = cv2.dilate(clean, kernel, iterations=1)

# Find connected components / contours
contours, _ = cv2.findContours(
    clean,
    cv2.RETR_EXTERNAL,
    cv2.CHAIN_APPROX_SIMPLE
)

# Sort top-to-bottom
contours = sorted(contours, key=lambda c: cv2.boundingRect(c)[1])

count = 0

for c in contours:
    x, y, w, h = cv2.boundingRect(c)

    # Ignore tiny noise
    if w < 15 or h < 10:
        continue

    # Add padding
    pad = 10
    x1 = max(x - pad, 0)
    y1 = max(y - pad, 0)
    x2 = min(x + w + pad, img.shape[1])
    y2 = min(y + h + pad, img.shape[0])

    crop = img[y1:y2, x1:x2]

    # Save
    out_path = os.path.join(OUTPUT_DIR, f"sign_{count:02d}.png")
    cv2.imwrite(out_path, crop)

    count += 1

print(f"Saved {count} signature images to '{OUTPUT_DIR}'")
