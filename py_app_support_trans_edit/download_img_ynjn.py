from selenium import webdriver
from selenium.webdriver.common.by import By
from selenium.webdriver.support.ui import WebDriverWait
from selenium.webdriver.chrome.options import Options
import hashlib
import time
import os

opts = Options()
opts.add_argument("--disable-gpu")
opts.add_argument("--disable-software-rasterizer")
opts.add_argument("--disable-logging")
opts.add_argument("--log-level=3")

url = input("Enter YNJN URL: ").strip()
output_dir = input("Enter folder name: ").strip()

os.makedirs(output_dir, exist_ok=True)

driver = webdriver.Chrome(options=opts)
driver.get(url)

WebDriverWait(driver, 60).until(
    lambda d: d.execute_script(
        "return document.querySelectorAll('canvas').length>0"
    )
)

input("Move to first page, then press Enter...")

saved = set()
index = 1

last_height = 0
same_height = 0

while True:

    canvases = driver.find_elements(By.TAG_NAME, "canvas")

    for canvas in canvases:

        try:
            img = canvas.screenshot_as_png
        except Exception:
            continue

        md5 = hashlib.md5(img).hexdigest()

        if md5 in saved:
            continue

        saved.add(md5)

        filename = os.path.join(output_dir, f"{index:03}.png")

        with open(filename, "wb") as f:
            f.write(img)

        print(f"Saved {filename}")

        index += 1

    driver.execute_script("window.scrollBy(0, window.innerHeight*0.8);")
    time.sleep(0.6)

    height = driver.execute_script("return window.pageYOffset")

    if height == last_height:
        same_height += 1
    else:
        same_height = 0

    last_height = height

    if same_height >= 5:
        break

driver.quit()

print(f"Downloaded {index-1} images.")
