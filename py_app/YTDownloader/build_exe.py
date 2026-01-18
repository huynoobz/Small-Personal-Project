"""
Build script to create standalone .exe file using PyInstaller
Run this script to build the executable: python build_exe.py
"""

import PyInstaller.__main__
import os
import sys

# Get the directory where this script is located
script_dir = os.path.dirname(os.path.abspath(__file__))
main_script = os.path.join(script_dir, "YTDownloader.py")

# PyInstaller arguments
args = [
    main_script,
    "--name=YTDownloader",
    "--onefile",  # Create a single executable file
    "--console",  # Keep console window (since it uses input())
    "--clean",    # Clean PyInstaller cache before building
    "--noconfirm", # Overwrite output directory without asking
]

# Add hidden imports if needed
args.extend([
    "--hidden-import=yt_dlp",
    "--hidden-import=yt_dlp.extractor",
    "--hidden-import=yt_dlp.downloader",
])

print("Building executable...")
print(f"Script: {main_script}")
print(f"Arguments: {' '.join(args)}")
print("\n" + "="*50 + "\n")

try:
    PyInstaller.__main__.run(args)
    print("\n" + "="*50)
    print("Build completed successfully!")
    print(f"Executable location: {os.path.join(script_dir, 'dist', 'YTDownloader.exe')}")
    print("\nNote: FFmpeg is required for audio extraction.")
    print("Users will need to have FFmpeg installed or you can bundle it separately.")
except Exception as e:
    print(f"\nError during build: {e}")
    sys.exit(1)
