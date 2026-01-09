#!/usr/bin/env python3
"""
pdf_table_to_excel.py

Windows-ready script: convert a PDF (each page is an image of a table) into an Excel file.
- Converts PDF pages to images (pdf2image / Poppler)
- Detects table cell boxes with OpenCV (horizontal/vertical line morphology)
- OCRs each cell with pytesseract (Vietnamese: 'vie' language pack recommended)
- Writes each PDF page to a separate Excel sheet (pandas + openpyxl)

Dependencies (install with pip):
    pip install pdf2image pillow opencv-python pytesseract pandas openpyxl numpy
System prerequisites (Windows):
  - Tesseract OCR: install from https://github.com/tesseract-ocr/tesseract/releases
    Typical path: C:\Program Files\Tesseract-OCR\tesseract.exe
    Install Vietnamese language data (tessdata) so lang='vie' works.
  - Poppler for Windows: used by pdf2image. Download and point --poppler-path to the bin folder.

Usage example (PowerShell / CMD):
  python pdf_table_to_excel.py input.pdf output.xlsx \
    --poppler-path "C:\\path\\to\\poppler-xx\\Library\\bin" \
    --tesseract "C:\\Program Files\\Tesseract-OCR\\tesseract.exe" \
    --dpi 300

Notes / caveats:
  - This script uses heuristics to detect table grids. It works well on clear printed tables with visible grid lines.
  - For complex or borderless tables, results may need manual cleanup.
  - If detection fails, try increasing DPI or adjusting morphological kernel sizes.
"""

import argparse
import os
import sys
from pdf2image import convert_from_path
import numpy as np
import cv2
from PIL import Image
import pytesseract
import pandas as pd


def extract_images_from_pdf(pdf_path, dpi=300, poppler_path=None):
    images = convert_from_path(pdf_path, dpi=dpi, poppler_path=poppler_path)
    return images


def detect_table_cells(pil_img):
    # Convert PIL image to OpenCV BGR
    img = cv2.cvtColor(np.array(pil_img), cv2.COLOR_RGB2BGR)
    gray = cv2.cvtColor(img, cv2.COLOR_BGR2GRAY)

    # Binarize
    blur = cv2.GaussianBlur(gray, (3, 3), 0)
    thresh = cv2.adaptiveThreshold(blur, 255, cv2.ADAPTIVE_THRESH_GAUSSIAN_C,
                                   cv2.THRESH_BINARY_INV, 15, 9)

    # Detect horizontal lines
    horizontal = thresh.copy()
    cols = horizontal.shape[1]
    horizontal_size = max(10, cols // 30)  # heuristic
    horizontal_kernel = cv2.getStructuringElement(cv2.MORPH_RECT, (horizontal_size, 1))
    horizontal = cv2.erode(horizontal, horizontal_kernel, iterations=1)
    horizontal = cv2.dilate(horizontal, horizontal_kernel, iterations=1)

    # Detect vertical lines
    vertical = thresh.copy()
    rows = vertical.shape[0]
    vertical_size = max(10, rows // 30)  # heuristic
    vertical_kernel = cv2.getStructuringElement(cv2.MORPH_RECT, (1, vertical_size))
    vertical = cv2.erode(vertical, vertical_kernel, iterations=1)
    vertical = cv2.dilate(vertical, vertical_kernel, iterations=1)

    # Combine lines to get grid
    grid = cv2.add(horizontal, vertical)

    # Find contours from grid
    contours, hierarchy = cv2.findContours(grid, cv2.RETR_TREE, cv2.CHAIN_APPROX_SIMPLE)

    rects = []
    min_area = (img.shape[0] * img.shape[1]) * 0.0005  # filter very small boxes
    for cnt in contours:
        x, y, w, h = cv2.boundingRect(cnt)
        area = w * h
        if area < min_area:
            continue
        # filter out very thin lines
        if w < 20 or h < 10:
            continue
        rects.append((x, y, w, h))

    if not rects:
        # fallback: try to find text boxes via MSER or treat whole image as one cell
        h, w = gray.shape
        return [(0, 0, w, h)]

    # Merge overlapping rects (bounding boxes may overlap)
    rects = merge_rects(rects)

    # Sort rects top-to-bottom
    rects = sorted(rects, key=lambda r: (r[1], r[0]))

    # Group into rows by y coordinate
    rows_grouped = group_rects_into_rows(rects)

    # Within each row, sort by x
    table_cells = []
    for row in rows_grouped:
        row_sorted = sorted(row, key=lambda r: r[0])
        table_cells.append(row_sorted)

    return table_cells


def merge_rects(rects, overlap_thresh=0.3):
    # Simple greedy merge: if two rects overlap significantly, merge into bounding union
    rects = [list(r) for r in rects]
    changed = True
    while changed:
        changed = False
        out = []
        used = [False] * len(rects)
        for i in range(len(rects)):
            if used[i]:
                continue
            xi, yi, wi, hi = rects[i]
            ri = (xi, yi, xi + wi, yi + hi)
            merged = ri
            used[i] = True
            for j in range(i + 1, len(rects)):
                if used[j]:
                    continue
                xj, yj, wj, hj = rects[j]
                rj = (xj, yj, xj + wj, yj + hj)
                if rects_overlap_fraction(merged, rj) > overlap_thresh:
                    # merge
                    merged = (min(merged[0], rj[0]), min(merged[1], rj[1]),
                              max(merged[2], rj[2]), max(merged[3], rj[3]))
                    used[j] = True
                    changed = True
            out.append((merged[0], merged[1], merged[2] - merged[0], merged[3] - merged[1]))
        rects = out
    return rects


def rects_overlap_fraction(a, b):
    # a, b are (x1,y1,x2,y2)
    xa1, ya1, xa2, ya2 = a
    xb1, yb1, xb2, yb2 = b
    xi1 = max(xa1, xb1)
    yi1 = max(ya1, yb1)
    xi2 = min(xa2, xb2)
    yi2 = min(ya2, yb2)
    if xi2 <= xi1 or yi2 <= yi1:
        return 0.0
    inter = (xi2 - xi1) * (yi2 - yi1)
    area_a = (xa2 - xa1) * (ya2 - ya1)
    return inter / float(area_a)


def group_rects_into_rows(rects, y_tol_ratio=0.5):
    # rects: list of (x,y,w,h) sorted by y
    rows = []
    for r in rects:
        x, y, w, h = r
        placed = False
        for row in rows:
            # compare with representative y of the row
            rx, ry, rw, rh = row[0]
            if abs(y - ry) <= int(max(h, rh) * y_tol_ratio) + 5:
                row.append(r)
                placed = True
                break
        if not placed:
            rows.append([r])
    return rows


def ocr_crop(pil_img, bbox, tesseract_cmd=None, lang='vie'):
    x, y, w, h = bbox
    crop = pil_img.crop((x, y, x + w, y + h))
    # Preprocess crop for OCR
    crop_gray = crop.convert('L')
    npimg = np.array(crop_gray)
    # optional resizing to help OCR
    h0, w0 = npimg.shape
    scale = 1.0
    if max(w0, h0) < 300:
        scale = 2.0
        npimg = cv2.resize(npimg, (int(w0 * scale), int(h0 * scale)), interpolation=cv2.INTER_LINEAR)
    # Threshold
    _, npimg = cv2.threshold(npimg, 0, 255, cv2.THRESH_BINARY + cv2.THRESH_OTSU)
    pil_for_ocr = Image.fromarray(npimg)

    if tesseract_cmd:
        pytesseract.pytesseract.tesseract_cmd = tesseract_cmd

    custom_config = r'--oem 3 --psm 6'
    try:
        text = pytesseract.image_to_string(pil_for_ocr, lang=lang, config=custom_config)
    except pytesseract.TesseractError:
        # fallback to default language
        text = pytesseract.image_to_string(pil_for_ocr, config=custom_config)
    # cleanup
    text = text.replace('\x0c', '').strip()
    return text


def page_to_table(pil_img, tesseract_cmd=None, lang='vie'):
    cells = detect_table_cells(pil_img)
    # If detect_table_cells returned a single list of rows (list of rows -> cells), then it's already grouped
    if cells and isinstance(cells[0], list):
        rows = []
        max_cols = 0
        for row in cells:
            row_texts = []
            for (x, y, w, h) in row:
                txt = ocr_crop(pil_img, (x, y, w, h), tesseract_cmd=tesseract_cmd, lang=lang)
                row_texts.append(txt)
            max_cols = max(max_cols, len(row_texts))
            rows.append(row_texts)
        # Normalize row lengths
        for r in rows:
            if len(r) < max_cols:
                r.extend([''] * (max_cols - len(r)))
        return rows
    else:
        # cells is a single bounding box fallback
        (x, y, w, h) = cells[0]
        text = ocr_crop(pil_img, (x, y, w, h), tesseract_cmd=tesseract_cmd, lang=lang)
        # try to split lines and delimiter detection
        lines = [l.strip() for l in text.splitlines() if l.strip()]
        rows = [line.split() for line in lines]
        return rows


def save_pages_to_excel(pages_tables, output_path):
    with pd.ExcelWriter(output_path, engine='openpyxl') as writer:
        for i, rows in enumerate(pages_tables):
            if not rows:
                df = pd.DataFrame()
            else:
                df = pd.DataFrame(rows)
            sheet_name = f'Page_{i+1}'
            # Excel sheet names limited to 31 chars
            df.to_excel(writer, sheet_name=sheet_name[:31], index=False, header=False)


def main():
    parser = argparse.ArgumentParser(description='Extract table(s) from a PDF (image pages) to an Excel file.')
    parser.add_argument('pdf', help='Input PDF path')
    parser.add_argument('output', help='Output Excel path (.xlsx)')
    parser.add_argument('--poppler-path', default=None, help='Path to Poppler bin folder (Windows), e.g. C:\\poppler-xx\\Library\\bin')
    parser.add_argument('--tesseract', default=None, help='Full path to tesseract.exe (Windows). If not provided, must be on PATH.')
    parser.add_argument('--dpi', type=int, default=300, help='DPI for PDF->image conversion')
    parser.add_argument('--lang', default='vie', help="Tesseract language (default 'vie'). Use 'eng' if 'vie' not installed.")
    args = parser.parse_args()

    pdf_path = args.pdf
    out_path = args.output
    poppler = args.poppler_path
    tess = args.tesseract
    dpi = args.dpi
    lang = args.lang

    if not os.path.isfile(pdf_path):
        print('Input PDF not found:', pdf_path)
        sys.exit(1)

    print('Converting PDF pages to images (dpi=%d)...' % dpi)
    images = extract_images_from_pdf(pdf_path, dpi=dpi, poppler_path=poppler)
    print('Pages:', len(images))

    pages_tables = []
    for i, pil_img in enumerate(images):
        print('\nProcessing page', i + 1)
        try:
            rows = page_to_table(pil_img, tesseract_cmd=tess, lang=lang)
            # trim empty rows
            rows = [r for r in rows if any(cell.strip() for cell in r)]
            print('Detected rows:', len(rows))
            pages_tables.append(rows)
        except Exception as e:
            print('Failed page', i + 1, '->', e)
            pages_tables.append([])

    print('\nSaving to Excel:', out_path)
    save_pages_to_excel(pages_tables, out_path)
    print('Done.')


if __name__ == '__main__':
    main()
