from selenium import webdriver
from selenium.webdriver.common.by import By
from selenium.webdriver.support.ui import WebDriverWait
from selenium.webdriver.support import expected_conditions as EC
import base64
import os
from selenium.webdriver.chrome.options import Options

opts = Options()
opts.add_argument("--disable-gpu")
opts.add_argument("--disable-software-rasterizer")
opts.add_argument("--disable-logging")
opts.add_argument("--log-level=3")

URL=input("Enter ynjn URL: ")
OUTPUT_DIR = input("Enter folder name: ")
os.makedirs(OUTPUT_DIR, exist_ok=True)

driver = webdriver.Chrome(options=opts)
driver.get(URL)

WebDriverWait(driver, 60).until(
    lambda d: d.execute_script(
        "return document.getElementsByTagName('canvas').length > 0"
    )
)

canvases = driver.find_elements(By.TAG_NAME, "canvas")
print(f"Found {len(canvases)} canvas elements")
max = len(canvases)-1

input("MAKE ALL IMAGES LOAD, then Enter to download them...")

for i, canvas in enumerate(canvases):
    data_url = driver.execute_script(
        "return arguments[0].toDataURL('image/png');",
        canvas
    )

    header, encoded = data_url.split(",", 1)
    path = os.path.join(OUTPUT_DIR, f"{max-i}.png")
    with open(path, "wb") as f:
        f.write(base64.b64decode(encoded))
    print("Successfully download", path)
