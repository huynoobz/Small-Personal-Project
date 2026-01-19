import yt_dlp
import sys
import os
import re
from yt_dlp.postprocessor.ffmpeg import FFmpegPostProcessor

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

def download_video(url, resolution):
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
    
    ydl_opts = {
        "format": format_string,
        "merge_output_format": "mp4",
        "outtmpl": f"%(title)s_video_{outtmpl_suffix}.%(ext)s",
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
        print(f"\nDownload complete! File saved as: %(title)s_video_{outtmpl_suffix}.mp4")
    except Exception as e:
        error_str = str(e).lower()
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
                print(f"\nDownload complete! File saved as: %(title)s_video_{outtmpl_suffix}.mp4")
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


def download_audio(url, bitrate):
    # bitrate: can be "best" or a number (64, 96, 128, 160, 192, 256, 320, or custom)
    
    if bitrate == "best":
        # Download best available audio quality
        ydl_opts = {
            "format": "bestaudio/best",
            "outtmpl": "%(title)s_audio_best.%(ext)s",
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
        ydl_opts = {
            "format": "bestaudio/best",
            "outtmpl": f"%(title)s_audio_{bitrate}.%(ext)s",
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
        print(f"\nDownload complete! File saved as: %(title)s_audio_{outtmpl_suffix}.mp3")
    except Exception as e:
        if "ffmpeg" in str(e).lower() or "codec" in str(e).lower():
            print(f"\nError: FFmpeg is required for audio conversion to MP3.")
            print(f"Please ensure ffmpeg.exe is in ffmpeg/bin/ directory or in PATH.")
            print(f"Original error: {e}")
        else:
            print(f"\nError during download: {e}")
        raise


def main():
    url = input("Enter Video URL (YT, FB,...): ").strip()
    
    # Convert youtu.be short URLs to full YouTube URLs
    url = convert_youtube_url(url)

    print("\nChoose download type:")
    print("1. Video")
    print("2. Audio only")

    choice = input("Your choice (1/2): ").strip()

    if choice == "1":
        print("\nChoose video quality:")
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
            quality_choice = int(input("Your choice (1-10): ").strip())
            if 1 <= quality_choice <= len(video_qualities):
                selected_res = video_qualities[quality_choice - 1][0]
                download_video(url, selected_res)
            else:
                print("Invalid choice. Please select a number from 1 to 10.")
                sys.exit(1)
        except ValueError:
            print("Invalid input. Please enter a number.")
            sys.exit(1)

    elif choice == "2":
        print("\nChoose audio bitrate:")
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
            quality_choice = int(input("Your choice (1-8): ").strip())
            if 1 <= quality_choice <= len(audio_qualities):
                selected_br = audio_qualities[quality_choice - 1][0]
                download_audio(url, selected_br)
            else:
                print("Invalid choice. Please select a number from 1 to 8.")
                sys.exit(1)
        except ValueError:
            print("Invalid input. Please enter a number.")
            sys.exit(1)

    else:
        print("Invalid choice.")
        sys.exit(1)


if __name__ == "__main__":
    main()
