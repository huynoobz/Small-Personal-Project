"""
UIModule - Module xử lý UI interactions (menus, selections)
"""

import sys
import re


def convert_youtube_url(url):
    """Convert youtu.be short URLs to full www.youtube.com/watch?v= format."""
    # Pattern to match youtu.be URLs (with or without protocol)
    pattern = r'(?:https?://)?(?:www\.)?youtu\.be/([a-zA-Z0-9_-]+)'
    match = re.search(pattern, url)
    
    if match:
        video_id = match.group(1)
        # Remove any query parameters from the original URL if present
        # and construct the full YouTube URL
        return f"www.youtube.com/watch?v={video_id}"
    
    return url  # Return original URL if not a youtu.be link


def get_main_menu_choice():
    """Hiển thị menu chính và lấy lựa chọn của user"""
    print("\n" + "="*60)
    print("VIDEO DOWNLOADER")
    print("="*60)
    print("\nChọn chức năng:")
    print("1. Tải video/audio từ URL trực tiếp")
    print("2. Quét video từ Facebook post công khai")
    print("0. Thoát")
    
    choice = input("\nLựa chọn của bạn (0/1/2): ").strip()
    return choice


def get_download_type_choice():
    """Lấy lựa chọn loại tải (video hoặc audio)"""
    print("\nChọn loại tải:")
    print("1. Video")
    print("2. Audio only")
    
    choice = input("Lựa chọn của bạn (1/2): ").strip()
    return choice


def get_video_quality_choice():
    """Hiển thị menu chất lượng video và lấy lựa chọn"""
    print("\nChọn chất lượng video:")
    video_qualities = [
        ("best", "Best available quality"),
        (4320, "4320p (8K)"),
        (2160, "2160p (4K)"),
        (1440, "1440p (2K)"),
        (1080, "1080p (Full HD)"),
        (720, "720p (HD)"),
        (480, "480p"),
        (360, "360p"),
        (240, "240p"),
        (144, "144p"),
    ]
    
    for i, (res, desc) in enumerate(video_qualities, 1):
        print(f"  {i}. {desc}")
    
    try:
        quality_choice = int(input("Lựa chọn của bạn (1-10): ").strip())
        if 1 <= quality_choice <= len(video_qualities):
            return video_qualities[quality_choice - 1][0]
        else:
            print("Lựa chọn không hợp lệ. Vui lòng chọn số từ 1 đến 10.")
            return None
    except ValueError:
        print("Đầu vào không hợp lệ. Vui lòng nhập số.")
        return None


def get_audio_bitrate_choice():
    """Hiển thị menu bitrate audio và lấy lựa chọn"""
    print("\nChọn bitrate audio:")
    audio_qualities = [
        ("best", "Best available quality"),
        (320, "320 kbps (Highest quality)"),
        (256, "256 kbps"),
        (192, "192 kbps"),
        (160, "160 kbps"),
        (128, "128 kbps (Standard)"),
        (96, "96 kbps"),
        (64, "64 kbps"),
    ]
    
    for i, (br, desc) in enumerate(audio_qualities, 1):
        print(f"  {i}. {desc}")
    
    try:
        quality_choice = int(input("Lựa chọn của bạn (1-8): ").strip())
        if 1 <= quality_choice <= len(audio_qualities):
            return audio_qualities[quality_choice - 1][0]
        else:
            print("Lựa chọn không hợp lệ. Vui lòng chọn số từ 1 đến 8.")
            return None
    except ValueError:
        print("Đầu vào không hợp lệ. Vui lòng nhập số.")
        return None


def get_url_input(prompt="Nhập URL video (YT, FB,...): "):
    """Lấy URL từ user"""
    url = input(prompt).strip()
    # Convert youtu.be short URLs to full YouTube URLs
    url = convert_youtube_url(url)
    return url


def display_video_list(video_urls):
    """Hiển thị danh sách video và lấy lựa chọn của user"""
    if not video_urls:
        print("\nKhông tìm thấy video nào trong post này.")
        return None
    
    print("\n" + "="*60)
    print(f"Tìm thấy {len(video_urls)} video:")
    print("="*60)
    
    for i, url in enumerate(video_urls, 1):
        # Truncate URL if too long for display
        display_url = url if len(url) <= 70 else url[:67] + "..."
        print(f"{i}. {display_url}")
    
    print(f"\n0. Hủy")
    
    try:
        choice = int(input(f"\nChọn video để tải (1-{len(video_urls)}, 0 để hủy): ").strip())
        if choice == 0:
            return None
        elif 1 <= choice <= len(video_urls):
            return video_urls[choice - 1]
        else:
            print(f"Lựa chọn không hợp lệ. Vui lòng chọn số từ 1 đến {len(video_urls)}.")
            return None
    except ValueError:
        print("Đầu vào không hợp lệ. Vui lòng nhập số.")
        return None
