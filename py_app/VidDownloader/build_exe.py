"""
Build script to create standalone .exe file using PyInstaller
Run this script to build the executable: python build_exe.py
"""

import PyInstaller.__main__
import os
import sys

# Get the directory where this script is located
script_dir = os.path.dirname(os.path.abspath(__file__))
main_script = os.path.join(script_dir, "VidDownloader.py")

# PyInstaller arguments
args = [
    main_script,
    "--name=VidDownloader",
    "--onefile",  # Create a single executable file
    "--noconsole",  # GUI app (no console window)
    "--clean",    # Clean PyInstaller cache before building
    "--noconfirm", # Overwrite output directory without asking
]

# Collect submodules that PyInstaller might miss
args.extend(
    [
        # yt-dlp (extractors, postprocessors, etc.)
        "--collect-submodules=yt_dlp",
        "--hidden-import=yt_dlp",
        "--hidden-import=yt_dlp.extractor",
        "--hidden-import=yt_dlp.downloader",
        "--hidden-import=yt_dlp.postprocessor",
        "--hidden-import=yt_dlp.postprocessor.ffmpeg",
        # Pillow (thumbnails)
        "--collect-submodules=PIL",
        "--hidden-import=PIL",
        "--hidden-import=PIL.Image",
        "--hidden-import=PIL.ImageTk",
        # Selenium (Facebook public post scanner)
        "--collect-submodules=selenium",
        "--hidden-import=selenium",
        "--hidden-import=selenium.webdriver",
    ]
)

print("Building executable...")
print(f"Script: {main_script}")
print(f"Arguments: {' '.join(args)}")
print("\n" + "="*50 + "\n")

try:
    PyInstaller.__main__.run(args)
    print("\n" + "="*50)
    print("Build completed successfully!")
    print(f"Executable location: {os.path.join(script_dir, 'dist', 'VidDownloader.exe')}")
    print("\nNote: FFmpeg is required for merging/conversion.")
    print("Users will need FFmpeg installed or bundled separately.")
except Exception as e:
    print(f"\nError during build: {e}")
    sys.exit(1)
