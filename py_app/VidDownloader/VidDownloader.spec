# -*- mode: python ; coding: utf-8 -*-
from PyInstaller.utils.hooks import collect_submodules

hiddenimports = ['yt_dlp', 'yt_dlp.extractor', 'yt_dlp.downloader', 'yt_dlp.postprocessor', 'yt_dlp.postprocessor.ffmpeg', 'PIL', 'PIL.Image', 'PIL.ImageTk', 'selenium', 'selenium.webdriver']
hiddenimports += collect_submodules('yt_dlp')
hiddenimports += collect_submodules('PIL')
hiddenimports += collect_submodules('selenium')


a = Analysis(
    ['C:\\Users\\ASUS\\Documents\\Small-Personal-Project\\py_app\\VidDownloader\\VidDownloader.py'],
    pathex=[],
    binaries=[],
    datas=[],
    hiddenimports=hiddenimports,
    hookspath=[],
    hooksconfig={},
    runtime_hooks=[],
    excludes=[],
    noarchive=False,
    optimize=0,
)
pyz = PYZ(a.pure)

exe = EXE(
    pyz,
    a.scripts,
    a.binaries,
    a.datas,
    [],
    name='VidDownloader',
    debug=False,
    bootloader_ignore_signals=False,
    strip=False,
    upx=True,
    upx_exclude=[],
    runtime_tmpdir=None,
    console=False,
    disable_windowed_traceback=False,
    argv_emulation=False,
    target_arch=None,
    codesign_identity=None,
    entitlements_file=None,
)
