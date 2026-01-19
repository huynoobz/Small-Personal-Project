# Building YTDownloader as Standalone .exe

This guide will help you convert the Python application to a standalone .exe file that can run on Windows machines without requiring Python or dependencies to be installed.

## Prerequisites

1. **Python 3.7+** installed on your development machine
2. **PyInstaller** - will be installed automatically

## Step 1: Install Dependencies

Open a terminal in this directory and run:

```bash
pip install -r requirements.txt
pip install pyinstaller
```

## Step 2: Build the Executable

You have two options:

### Option A: Using the build script (Recommended)
```bash
python build_exe.py
```

### Option B: Using PyInstaller directly
```bash
pyinstaller YTDownloader.spec
```

Or with command line:
```bash
pyinstaller --name=YTDownloader --onefile --console --clean YTDownloader.py
```

## Step 3: Find Your Executable

After building, the executable will be located in:
```
dist/YTDownloader.exe
```

## Important Notes

### FFmpeg Requirement
- **For video downloads**: Works without FFmpeg
- **For audio extraction**: Requires FFmpeg to be installed on the target machine

### FFmpeg Options:
1. **Bundle FFmpeg** (Recommended for full standalone):
   - Download FFmpeg from https://ffmpeg.org/download.html
   - Extract it and include the `ffmpeg.exe` in the same folder as your .exe
   - Or modify the code to specify FFmpeg path

2. **User installs FFmpeg**:
   - Users need to install FFmpeg and add it to PATH
   - Or place `ffmpeg.exe` in the same folder as `YTDownloader.exe`

### Distribution
- The `dist/YTDownloader.exe` file can be distributed to other Windows machines
- No Python installation required on target machines
- The .exe file will be large (50-100MB) as it bundles Python and all dependencies

## Troubleshooting

- If the build fails, make sure all dependencies are installed: `pip install -r requirements.txt pyinstaller`
- If the .exe doesn't run, try building with `--debug=all` to see error messages
- Antivirus software may flag PyInstaller executables - this is a false positive
