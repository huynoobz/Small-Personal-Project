"""
VidDownloader - Main file của dự án
Ứng dụng tải video/audio từ YouTube, Facebook và các nền tảng khác
"""

import sys
from GUIWindow import main

if __name__ == "__main__":
    try:
        main()
    except KeyboardInterrupt:
        print("\n\nĐã dừng chương trình.")
        sys.exit(0)
    except Exception as e:
        print(f"\nLỗi không mong muốn: {e}")
        import traceback
        traceback.print_exc()
        sys.exit(1)
