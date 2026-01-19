"""
vidDownloaderModule - Module xử lý việc tải video và audio
"""

import yt_dlp
import sys
import os
import base64
import secrets
from yt_dlp.postprocessor.ffmpeg import FFmpegPostProcessor


def _is_youtube_url(url: str) -> bool:
    if not url:
        return False
    u = url.lower()
    return "youtube.com" in u or "youtu.be" in u


def _looks_like_youtube_bot_check_error(err: Exception) -> bool:
    s = str(err).lower()
    return (
        "confirm you’re not a bot" in s
        or "confirm you're not a bot" in s
        or "not a bot" in s
        or "sign in to confirm" in s
    )


def _generate_random_visitor_data() -> str:
    """
    Generate a random visitor_data-like string.

    Note: This is NOT guaranteed to bypass YouTube bot-check. It's a best-effort
    fallback used only on retry.
    """
    # Common visitorData strings often start with "Cgt". We'll mimic that prefix.
    token = base64.urlsafe_b64encode(secrets.token_bytes(24)).decode("ascii").rstrip("=")
    return "Cgt" + token


def _add_antibot_extractor_args(ydl_opts: dict) -> None:
    """
    Apply the equivalent of:
      --extractor-args "youtubetab:skip=webpage"
      --extractor-args "youtube:player_skip=webpage,configs;visitor_data=..."
    """
    extractor_args = dict(ydl_opts.get("extractor_args") or {})
    extractor_args["youtubetab"] = {"skip": ["webpage"]}
    youtube_args = {
        "player_skip": ["webpage", "configs"],
        # visitor_data will be generated randomly on retry
        "visitor_data": [_generate_random_visitor_data()],
    }
    extractor_args["youtube"] = youtube_args
    ydl_opts["extractor_args"] = extractor_args


def get_ffmpeg_path():
    """Try to find FFmpeg in the same directory as the executable or in PATH."""
    if getattr(sys, 'frozen', False):
        # Running as compiled executable
        base_path = os.path.dirname(sys.executable)
    else:
        # Running as script
        base_path = os.path.dirname(os.path.abspath(__file__))
    
    # Check for ffmpeg.exe in ffmpeg/bin/ directory (primary location)
    ffmpeg_bin_path = os.path.join(base_path, 'ffmpeg', 'bin', 'ffmpeg.exe')
    if os.path.exists(ffmpeg_bin_path):
        return ffmpeg_bin_path
    
    # Check for ffmpeg.exe in the same directory
    ffmpeg_path = os.path.join(base_path, 'ffmpeg.exe')
    if os.path.exists(ffmpeg_path):
        return ffmpeg_path
    
    # Also check in dist folder (if running from source but ffmpeg is in dist)
    dist_path = os.path.join(base_path, 'dist', 'ffmpeg.exe')
    if os.path.exists(dist_path):
        return dist_path
    
    # Check if ffmpeg is in PATH (Windows)
    import shutil
    ffmpeg_in_path = shutil.which('ffmpeg.exe') or shutil.which('ffmpeg')
    if ffmpeg_in_path:
        return ffmpeg_in_path
    
    return None


class FFmpegAudioToAACPP(FFmpegPostProcessor):
    """Postprocessor to convert audio codec to AAC for Windows compatibility."""
    def __init__(self, downloader=None):
        super().__init__(downloader)
    
    def run(self, information):
        # Get the file path
        filepath = information.get('filepath')
        if not filepath or not os.path.exists(filepath):
            return [], information
        
        # Check if audio codec is already AAC
        acodec = information.get('acodec', '').lower()
        # Check for AAC codecs: mp4a, aac, mp4a.40.2, etc.
        # Only skip if we're absolutely sure it's AAC
        if acodec and ('mp4a.40.2' in acodec or acodec == 'mp4a.40.2' or 
                       (acodec.startswith('mp4a') and '40.2' in acodec)):
            # Confirmed AAC, skip conversion
            self.to_screen('Audio is already in AAC format.')
            return [], information
        
        # For any other codec (Opus, Vorbis, etc.) or unknown codec, convert to AAC
        # This ensures Windows compatibility, especially for "best" quality downloads
        self.to_screen(f'Converting audio from {acodec or "unknown"} to AAC for Windows compatibility...')
        
        # Store original filename
        original_filename = os.path.basename(filepath)
        
        # Convert audio to AAC to temporary file
        temp_filepath = filepath + '.tmp'
        try:
            self.run_ffmpeg(
                filepath,
                temp_filepath,
                ['-c:v', 'copy', '-c:a', 'aac', '-b:a', '192k', '-y']
            )
            
            # Only replace if final filename is the same as original
            final_filename = os.path.basename(temp_filepath).replace('.tmp', '')
            if final_filename == original_filename:
                # Safe to replace since filenames match
                os.replace(temp_filepath, filepath)
                self.to_screen('Audio converted to AAC successfully.')
            else:
                # Filenames differ - keep original and rename converted file
                final_filepath = os.path.join(os.path.dirname(filepath), final_filename)
                os.rename(temp_filepath, final_filepath)
                self.to_screen(f'Kept original file, converted file saved as: {final_filename}')
            
            information['acodec'] = 'mp4a.40.2'
            return [], information
        except Exception as e:
            if os.path.exists(temp_filepath):
                try:
                    os.remove(temp_filepath)
                except:
                    pass
            self.to_screen(f'Warning: Could not convert audio to AAC: {e}')
            # Don't raise - continue with original file
            return [], information


def download_video(url, resolution, add_quality_to_filename=True):
    """
    Tải video từ URL với độ phân giải được chỉ định
    
    Args:
        url: URL của video (YouTube, Facebook, etc.)
        resolution: "best" hoặc số (144, 240, 360, 480, 720, 1080, 1440, 2160, etc.)
        add_quality_to_filename: Nếu True, thêm quality vào filename (default: True)
    
    Returns:
        None (raises exception nếu có lỗi)
    """
    # resolution: can be "best" or a number (144, 240, 360, 480, 720, 1080, 1440, 2160, etc.)
    # Prefer formats with AAC audio codec for Windows compatibility
    
    if resolution == "best":
        # Download best available quality
        # Prefer AAC audio, but will convert to AAC via postprocessor if needed
        # More flexible format selector with fallbacks for sites like Facebook
        format_string = "bestvideo+bestaudio[acodec^=mp4a]/bestvideo+bestaudio/bestvideo+bestaudio/best"
        outtmpl_suffix = "best"
    else:
        # Download specific resolution
        # More flexible format selector with multiple fallbacks
        format_string = f"bestvideo[height<={resolution}]+bestaudio[acodec^=mp4a]/bestvideo[height<={resolution}]+bestaudio/bestvideo[height<={resolution}]+bestaudio/best[height<={resolution}]/best"
        outtmpl_suffix = str(resolution)
    
    # Tạo filename template
    if add_quality_to_filename:
        outtmpl = f"%(title)s_video_{outtmpl_suffix}.%(ext)s"
    else:
        outtmpl = "%(title)s_video.%(ext)s"
    
    ydl_opts = {
        "format": format_string,
        "merge_output_format": "mp4",
        "outtmpl": outtmpl,
        "quiet": False,
    }
    
    # FFmpeg is required to merge video and audio streams
    ffmpeg_path = get_ffmpeg_path()
    if ffmpeg_path:
        # Set ffmpeg_location to the directory containing ffmpeg.exe
        ffmpeg_dir = os.path.dirname(ffmpeg_path)
        ydl_opts["ffmpeg_location"] = ffmpeg_dir
        print(f"Using FFmpeg from: {ffmpeg_dir}")
    else:
        print("Warning: FFmpeg not found.")
        print("Video and audio merging may fail. Please ensure FFmpeg is in ffmpeg/bin/ or in PATH.")

    try:
        with yt_dlp.YoutubeDL(ydl_opts) as ydl:
            # Register custom postprocessor to convert audio to AAC
            # Run after video processing but before finalization
            ydl.add_post_processor(FFmpegAudioToAACPP(ydl), when='post_process')
            ydl.download([url])
        if add_quality_to_filename:
            print(f"\nDownload complete! File saved as: %(title)s_video_{outtmpl_suffix}.mp4")
        else:
            print(f"\nDownload complete! File saved as: %(title)s_video.mp4")
    except Exception as e:
        error_str = str(e).lower()
        if _is_youtube_url(url) and _looks_like_youtube_bot_check_error(e):
            # Retry once with anti-bot extractor args (optional visitor_data)
            if "extractor_args" not in ydl_opts:
                print("\nYouTube bot-check detected. Retrying with anti-bot extractor args...")
                _add_antibot_extractor_args(ydl_opts)
                try:
                    with yt_dlp.YoutubeDL(ydl_opts) as ydl:
                        ydl.add_post_processor(FFmpegAudioToAACPP(ydl), when='post_process')
                        ydl.download([url])
                    if add_quality_to_filename:
                        print(f"\nDownload complete! File saved as: %(title)s_video_{outtmpl_suffix}.mp4")
                    else:
                        print(f"\nDownload complete! File saved as: %(title)s_video.mp4")
                    return
                except Exception as e2:
                    print(f"\nError during download: {e2}")
                    raise
        if "requested format is not available" in error_str or "format is not available" in error_str:
            # Try with a simpler format selector as fallback
            print("\nFormat not available with preferred settings. Trying simpler format selector...")
            if resolution == "best":
                fallback_format = "best"
            else:
                fallback_format = f"best[height<={resolution}]/best"
            
            ydl_opts["format"] = fallback_format
            try:
                with yt_dlp.YoutubeDL(ydl_opts) as ydl:
                    ydl.add_post_processor(FFmpegAudioToAACPP(ydl), when='post_process')
                    ydl.download([url])
                if add_quality_to_filename:
                    print(f"\nDownload complete! File saved as: %(title)s_video_{outtmpl_suffix}.mp4")
                else:
                    print(f"\nDownload complete! File saved as: %(title)s_video.mp4")
            except Exception as e2:
                print(f"\nError during download: {e2}")
                raise
        elif "ffmpeg" in error_str or "merge" in error_str or "codec" in error_str:
            print(f"\nError: FFmpeg is required to merge video and audio streams.")
            print(f"Please ensure ffmpeg.exe is in ffmpeg/bin/ directory or in PATH.")
            print(f"Original error: {e}")
            raise
        else:
            print(f"\nError during download: {e}")
            raise


def download_audio(url, bitrate, add_bitrate_to_filename=True):
    """
    Tải audio từ URL với bitrate được chỉ định
    
    Args:
        url: URL của video (YouTube, Facebook, etc.)
        bitrate: "best" hoặc số (64, 96, 128, 160, 192, 256, 320)
        add_bitrate_to_filename: Nếu True, thêm bitrate vào filename (default: True)
    
    Returns:
        None (raises exception nếu có lỗi)
    """
    # bitrate: can be "best" or a number (64, 96, 128, 160, 192, 256, 320, or custom)
    
    if bitrate == "best":
        # Download best available audio quality
        if add_bitrate_to_filename:
            outtmpl = "%(title)s_audio_best.%(ext)s"
        else:
            outtmpl = "%(title)s_audio.%(ext)s"
        ydl_opts = {
            "format": "bestaudio/best",
            "outtmpl": outtmpl,
            "postprocessors": [
                {
                    "key": "FFmpegExtractAudio",
                    "preferredcodec": "mp3",
                    "preferredquality": "0",  # 0 = best quality
                }
            ],
            "quiet": False,
        }
        outtmpl_suffix = "best"
    else:
        # Convert to specific bitrate
        if add_bitrate_to_filename:
            outtmpl = f"%(title)s_audio_{bitrate}.%(ext)s"
        else:
            outtmpl = "%(title)s_audio.%(ext)s"
        ydl_opts = {
            "format": "bestaudio/best",
            "outtmpl": outtmpl,
            "postprocessors": [
                {
                    "key": "FFmpegExtractAudio",
                    "preferredcodec": "mp3",
                    "preferredquality": str(bitrate),
                }
            ],
            "quiet": False,
        }
        outtmpl_suffix = str(bitrate)
    
    # Try to use FFmpeg from the same directory if available
    ffmpeg_path = get_ffmpeg_path()
    if ffmpeg_path:
        # Set ffmpeg_location to the directory containing ffmpeg.exe
        ffmpeg_dir = os.path.dirname(ffmpeg_path)
        ydl_opts["ffmpeg_location"] = ffmpeg_dir
        print(f"Using FFmpeg from: {ffmpeg_dir}")
    else:
        print("Warning: FFmpeg not found.")
        print("Audio conversion to MP3 may fail. Please ensure FFmpeg is in ffmpeg/bin/ or in PATH.")

    try:
        with yt_dlp.YoutubeDL(ydl_opts) as ydl:
            ydl.download([url])
        if add_bitrate_to_filename:
            print(f"\nDownload complete! File saved as: %(title)s_audio_{outtmpl_suffix}.mp3")
        else:
            print(f"\nDownload complete! File saved as: %(title)s_audio.mp3")
    except Exception as e:
        if _is_youtube_url(url) and _looks_like_youtube_bot_check_error(e):
            if "extractor_args" not in ydl_opts:
                print("\nYouTube bot-check detected. Retrying with anti-bot extractor args...")
                _add_antibot_extractor_args(ydl_opts)
                try:
                    with yt_dlp.YoutubeDL(ydl_opts) as ydl:
                        ydl.download([url])
                    if add_bitrate_to_filename:
                        print(f"\nDownload complete! File saved as: %(title)s_audio_{outtmpl_suffix}.mp3")
                    else:
                        print(f"\nDownload complete! File saved as: %(title)s_audio.mp3")
                    return
                except Exception as e2:
                    print(f"\nError during download: {e2}")
                    raise
        if "ffmpeg" in str(e).lower() or "codec" in str(e).lower():
            print(f"\nError: FFmpeg is required for audio conversion to MP3.")
            print(f"Please ensure ffmpeg.exe is in ffmpeg/bin/ directory or in PATH.")
            print(f"Original error: {e}")
        else:
            print(f"\nError during download: {e}")
        raise
