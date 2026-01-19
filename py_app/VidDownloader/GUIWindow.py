"""
GUIWindow - Module GUI cho ứng dụng Video Downloader
Sử dụng tkinter để tạo giao diện cửa sổ Windows
"""

import tkinter as tk
from tkinter import ttk, scrolledtext, messagebox
import threading
import sys
import queue
import urllib.request
from io import BytesIO

import yt_dlp
from PIL import Image, ImageTk
from vidDownloaderModule import download_video, download_audio
from getPublicPostMedias import get_video_urls
from UIModule import convert_youtube_url


class _QueueWriter:
    """File-like object to redirect stdout/stderr into a queue (thread-safe)."""

    def __init__(self, q: "queue.Queue[str]", prefix: str = ""):
        self._q = q
        self._prefix = prefix
        self._buf = ""

    def write(self, s: str) -> int:
        if not s:
            return 0
        # yt-dlp uses carriage returns for progress; convert to newlines for UI.
        s = s.replace("\r", "\n")
        self._buf += s

        while "\n" in self._buf:
            line, self._buf = self._buf.split("\n", 1)
            line = line.rstrip()
            if line:
                self._q.put(f"{self._prefix}{line}")
        return len(s)

    def flush(self) -> None:
        if self._buf.strip():
            self._q.put(f"{self._prefix}{self._buf.strip()}")
        self._buf = ""


class VideoDownloaderGUI:
    """Class chính cho GUI ứng dụng Video Downloader"""
    
    def __init__(self, root):
        self.root = root
        self.root.title("Video Downloader")
        self.root.geometry("800x600")
        self.root.resizable(True, True)
        
        # Style
        self.setup_styles()
        
        # Tạo giao diện chính
        self.create_main_window()
        
        # Biến để lưu trữ
        self.is_downloading = False
        self.saved_listbox_selection = None  # Lưu selection của listbox

        # Log queue (stdout/stderr + app logs)
        self._log_queue: "queue.Queue[str]" = queue.Queue()
        self._pending_logs: list[str] = []
        self._start_log_pump()
    
    def setup_styles(self):
        """Thiết lập style cho các widget"""
        style = ttk.Style()
        style.theme_use('clam')
        
        # Configure button style
        style.configure('Title.TLabel', font=('Arial', 16, 'bold'))
        style.configure('Heading.TLabel', font=('Arial', 12, 'bold'))
    
    def create_scrollable_frame(self, parent):
        """Tạo một frame có thể scroll được"""
        # Tạo canvas và scrollbar
        canvas = tk.Canvas(parent, highlightthickness=0)
        scrollbar = ttk.Scrollbar(parent, orient="vertical", command=canvas.yview)
        scrollable_frame = ttk.Frame(canvas)
        
        def configure_scroll_region(event=None):
            canvas.update_idletasks()
            canvas.configure(scrollregion=canvas.bbox("all"))
        
        scrollable_frame.bind("<Configure>", configure_scroll_region)
        
        canvas_frame = canvas.create_window((0, 0), window=scrollable_frame, anchor="nw")
        
        def configure_canvas_width(event):
            canvas_width = event.width
            canvas.itemconfig(canvas_frame, width=canvas_width)
        
        canvas.bind('<Configure>', configure_canvas_width)
        canvas.configure(yscrollcommand=scrollbar.set)
        
        # Pack canvas và scrollbar
        canvas.pack(side="left", fill="both", expand=True)
        scrollbar.pack(side="right", fill="y")
        
        # Bind mousewheel to canvas
        def _on_mousewheel(event):
            canvas.yview_scroll(int(-1*(event.delta/120)), "units")
        
        def _bind_to_mousewheel(event):
            canvas.bind_all("<MouseWheel>", _on_mousewheel)
        
        def _unbind_from_mousewheel(event):
            canvas.unbind_all("<MouseWheel>")
        
        canvas.bind('<Enter>', _bind_to_mousewheel)
        canvas.bind('<Leave>', _unbind_from_mousewheel)
        
        return scrollable_frame, canvas
    
    def create_main_window(self):
        """Tạo cửa sổ chính"""
        # Clear existing widgets
        for widget in self.root.winfo_children():
            widget.destroy()
        
        # Title
        title_frame = ttk.Frame(self.root, padding="20")
        title_frame.pack(fill=tk.X)
        
        title_label = ttk.Label(
            title_frame,
            text="VIDEO DOWNLOADER",
            style='Title.TLabel'
        )
        title_label.pack()
        
        subtitle_label = ttk.Label(
            title_frame,
            text="Tải video/audio từ YouTube, Facebook và các nền tảng khác"
        )
        subtitle_label.pack(pady=(5, 0))
        
        # Buttons frame
        buttons_frame = ttk.Frame(self.root, padding="20")
        buttons_frame.pack(fill=tk.BOTH, expand=True)
        
        # Button 1: Direct Download
        btn_direct = ttk.Button(
            buttons_frame,
            text="1. Tải video/audio từ URL trực tiếp",
            command=self.show_direct_download_window,
            width=40
        )
        btn_direct.pack(pady=15, padx=20, fill=tk.X)
        
        # Button 2: Facebook Post Scan
        btn_facebook = ttk.Button(
            buttons_frame,
            text="2. Quét video từ Facebook post công khai",
            command=self.show_facebook_scan_window,
            width=40
        )
        btn_facebook.pack(pady=15, padx=20, fill=tk.X)
        
        # Exit button
        btn_exit = ttk.Button(
            buttons_frame,
            text="Thoát",
            command=self.root.quit,
            width=40
        )
        btn_exit.pack(pady=15, padx=20, fill=tk.X)
    
    def show_direct_download_window(self):
        """Hiển thị cửa sổ tải trực tiếp"""
        # Clear existing widgets
        for widget in self.root.winfo_children():
            widget.destroy()
        
        # Title
        title_frame = ttk.Frame(self.root, padding="10")
        title_frame.pack(fill=tk.X)
        
        back_btn = ttk.Button(title_frame, text="← Quay lại", command=self.create_main_window)
        back_btn.pack(side=tk.LEFT, padx=5)
        
        title_label = ttk.Label(
            title_frame,
            text="Tải video/audio từ URL trực tiếp",
            style='Heading.TLabel'
        )
        title_label.pack(side=tk.LEFT, padx=10)
        
        # Container for scrollable content
        scroll_container = ttk.Frame(self.root)
        scroll_container.pack(fill=tk.BOTH, expand=True)
        
        # Main content frame (scrollable)
        content_frame, self.direct_canvas = self.create_scrollable_frame(scroll_container)
        content_frame.configure(padding="20")
        
        # URL input
        url_frame = ttk.Frame(content_frame)
        url_frame.pack(fill=tk.X, pady=10)
        
        ttk.Label(url_frame, text="URL video:").pack(anchor=tk.W)
        self.url_entry = ttk.Entry(url_frame, width=70)
        self.url_entry.pack(fill=tk.X, pady=5)
        
        # Bind event để kiểm tra URL và hiển thị/ẩn quality options
        self.url_entry.bind('<KeyRelease>', self.check_url_and_update_options)
        self.url_entry.bind('<FocusOut>', self.check_url_and_update_options)
        
        # Download type
        type_frame = ttk.LabelFrame(content_frame, text="Loại tải", padding="10")
        type_frame.pack(fill=tk.X, pady=10)
        
        self.download_type = tk.StringVar(value="video")
        ttk.Radiobutton(
            type_frame,
            text="Video",
            variable=self.download_type,
            value="video"
        ).pack(side=tk.LEFT, padx=20)
        
        ttk.Radiobutton(
            type_frame,
            text="Audio only",
            variable=self.download_type,
            value="audio"
        ).pack(side=tk.LEFT, padx=20)
        
        # Quality selection
        self.quality_frame = ttk.LabelFrame(content_frame, text="Chất lượng", padding="10")
        self.quality_frame.pack(fill=tk.X, pady=10)
        quality_frame = self.quality_frame  # Alias for easier reference
        
        # Video qualities
        self.video_quality_var = tk.StringVar(value="best")
        video_qualities = [
            ("best", "Best available quality"),
            ("4320", "4320p (8K)"),
            ("2160", "2160p (4K)"),
            ("1440", "1440p (2K)"),
            ("1080", "1080p (Full HD)"),
            ("720", "720p (HD)"),
            ("480", "480p"),
            ("360", "360p"),
            ("240", "240p"),
            ("144", "144p"),
        ]
        
        self.video_quality_combo = ttk.Combobox(
            quality_frame,
            textvariable=self.video_quality_var,
            values=[f"{desc}" for _, desc in video_qualities],
            state="readonly",
            width=40
        )
        self.video_quality_combo.current(0)
        self.video_quality_combo.pack(side=tk.LEFT, padx=10)
        
        # Audio bitrates
        self.audio_bitrate_var = tk.StringVar(value="best")
        audio_bitrates = [
            ("best", "Best available quality"),
            ("320", "320 kbps (Highest quality)"),
            ("256", "256 kbps"),
            ("192", "192 kbps"),
            ("160", "160 kbps"),
            ("128", "128 kbps (Standard)"),
            ("96", "96 kbps"),
            ("64", "64 kbps"),
        ]
        
        self.audio_bitrate_combo = ttk.Combobox(
            quality_frame,
            textvariable=self.audio_bitrate_var,
            values=[f"{desc}" for _, desc in audio_bitrates],
            state="readonly",
            width=40
        )
        self.audio_bitrate_combo.current(0)
        self.audio_bitrate_combo.pack(side=tk.LEFT, padx=10)
        
        # Biến để lưu trạng thái is_youtube
        self.is_youtube = False
        
        # Update quality display based on download type
        self.update_quality_display()
        self.download_type.trace('w', lambda *args: self.update_quality_display())
        
        # Ẩn quality frame ban đầu (chỉ hiện khi nhập YouTube URL)
        self.quality_frame.pack_forget()
        
        # Download button
        btn_frame = ttk.Frame(content_frame)
        btn_frame.pack(pady=20)
        
        self.download_btn = ttk.Button(
            btn_frame,
            text="Bắt đầu tải",
            command=self.start_direct_download,
            width=30
        )
        self.download_btn.pack()
        
        # Progress and log
        self.create_progress_section(content_frame)
    
    def is_youtube_url(self, url):
        """Kiểm tra xem URL có phải YouTube không"""
        if not url:
            return False
        url_lower = url.lower()
        return 'youtube.com' in url_lower or 'youtu.be' in url_lower
    
    def check_url_and_update_options(self, event=None):
        """Kiểm tra URL và cập nhật hiển thị quality options"""
        url = self.url_entry.get().strip()
        is_youtube = self.is_youtube_url(url)
        self.is_youtube = is_youtube
        
        if is_youtube:
            # Hiển thị quality frame nếu là YouTube
            self.quality_frame.pack(fill=tk.X, pady=10)
            self.update_quality_display()
        else:
            # Ẩn quality frame và set về "best" nếu không phải YouTube
            self.quality_frame.pack_forget()
            self.video_quality_var.set("best")
            self.audio_bitrate_var.set("best")
    
    def update_quality_display(self):
        """Cập nhật hiển thị quality dựa trên loại tải"""
        # Chỉ hiển thị nếu là YouTube URL
        if not self.is_youtube:
            return
        
        if self.download_type.get() == "video":
            self.video_quality_combo.pack(side=tk.LEFT, padx=10)
            self.audio_bitrate_combo.pack_forget()
        else:
            self.video_quality_combo.pack_forget()
            self.audio_bitrate_combo.pack(side=tk.LEFT, padx=10)
    
    def show_facebook_scan_window(self):
        """Hiển thị cửa sổ quét Facebook post"""
        # Clear existing widgets
        for widget in self.root.winfo_children():
            widget.destroy()
        
        # Title
        title_frame = ttk.Frame(self.root, padding="10")
        title_frame.pack(fill=tk.X)
        
        back_btn = ttk.Button(title_frame, text="← Quay lại", command=self.create_main_window)
        back_btn.pack(side=tk.LEFT, padx=5)
        
        title_label = ttk.Label(
            title_frame,
            text="Quét video từ Facebook post công khai",
            style='Heading.TLabel'
        )
        title_label.pack(side=tk.LEFT, padx=10)
        
        # Container for scrollable content
        scroll_container = ttk.Frame(self.root)
        scroll_container.pack(fill=tk.BOTH, expand=True)
        
        # Main content frame (scrollable)
        content_frame, self.fb_canvas = self.create_scrollable_frame(scroll_container)
        content_frame.configure(padding="20")
        
        # Facebook URL input
        url_frame = ttk.Frame(content_frame)
        url_frame.pack(fill=tk.X, pady=10)
        
        ttk.Label(url_frame, text="URL Facebook post:").pack(anchor=tk.W)
        self.fb_url_entry = ttk.Entry(url_frame, width=70)
        self.fb_url_entry.pack(fill=tk.X, pady=5)
        
        # Scan button
        scan_btn_frame = ttk.Frame(content_frame)
        scan_btn_frame.pack(pady=10)
        
        self.scan_btn = ttk.Button(
            scan_btn_frame,
            text="Quét video",
            command=self.start_facebook_scan,
            width=30
        )
        self.scan_btn.pack()
        
        # Video list
        list_frame = ttk.LabelFrame(content_frame, text="Danh sách video tìm thấy", padding="10")
        list_frame.pack(fill=tk.BOTH, expand=True, pady=10)

        # Split: left list + right preview
        list_container = ttk.Frame(list_frame)
        list_container.pack(fill=tk.BOTH, expand=True)
        list_container.columnconfigure(0, weight=3)
        list_container.columnconfigure(1, weight=2)
        list_container.rowconfigure(0, weight=1)

        left_frame = ttk.Frame(list_container)
        left_frame.grid(row=0, column=0, sticky="nsew", padx=(0, 10))

        right_frame = ttk.LabelFrame(list_container, text="Preview", padding="10")
        right_frame.grid(row=0, column=1, sticky="nsew")
        right_frame.columnconfigure(0, weight=1)
        right_frame.rowconfigure(1, weight=1)

        self.preview_title_var = tk.StringVar(value="Chọn 1 video để xem thumbnail")
        preview_title = ttk.Label(right_frame, textvariable=self.preview_title_var, wraplength=240)
        preview_title.grid(row=0, column=0, sticky="w")

        self.preview_image_label = ttk.Label(right_frame)
        self.preview_image_label.grid(row=1, column=0, sticky="n", pady=(10, 0))
        self._preview_image = None  # keep reference

        # Treeview with scrollbar (preview thumbnail on the right)
        self._fb_iid_to_url = {}         # iid -> actual URL
        self._thumb_url_by_iid = {}      # iid -> thumbnail URL (for preview)
        self._preview_images = {}        # iid -> PhotoImage (larger)
        self._preview_loading = set()    # iids being loaded for preview

        scrollbar = ttk.Scrollbar(left_frame)
        scrollbar.pack(side=tk.RIGHT, fill=tk.Y)

        self.video_tree = ttk.Treeview(
            left_frame,
            columns=("url",),
            show="tree",
            yscrollcommand=scrollbar.set,
            selectmode="browse",
        )
        self.video_tree.pack(side=tk.LEFT, fill=tk.BOTH, expand=True)
        scrollbar.config(command=self.video_tree.yview)

        self._placeholder_preview = self._make_placeholder_preview()
        self.preview_image_label.configure(image=self._placeholder_preview)
        self._preview_image = self._placeholder_preview

        def on_tree_select(event=None):
            sel = self.video_tree.selection()
            if sel:
                self.saved_listbox_selection = sel[0]
                self._update_preview_for_selection(sel[0])

        self.video_tree.bind("<<TreeviewSelect>>", on_tree_select)
        
        # Download options (hidden until video selected)
        self.download_options_frame = ttk.LabelFrame(content_frame, text="Tùy chọn tải", padding="10")
        
        # Download type
        type_frame = ttk.Frame(self.download_options_frame)
        type_frame.pack(fill=tk.X, pady=5)
        
        ttk.Label(type_frame, text="Loại tải:").pack(side=tk.LEFT, padx=5)
        self.fb_download_type = tk.StringVar(value="video")
        ttk.Radiobutton(
            type_frame,
            text="Video",
            variable=self.fb_download_type,
            value="video"
        ).pack(side=tk.LEFT, padx=10)
        
        ttk.Radiobutton(
            type_frame,
            text="Audio only",
            variable=self.fb_download_type,
            value="audio"
        ).pack(side=tk.LEFT, padx=10)
 
        # NOTE: Removed quality/bitrate options for Facebook scan mode.
        # Always download with best quality and no quality suffix in filename.
        
        # Download button
        download_btn_frame = ttk.Frame(self.download_options_frame)
        download_btn_frame.pack(pady=10)
        
        self.fb_download_btn = ttk.Button(
            download_btn_frame,
            text="Bắt đầu tải",
            command=self.start_facebook_download,
            width=30
        )
        self.fb_download_btn.pack()
        
        # Progress and log
        self.create_progress_section(content_frame)
    
    def preserve_listbox_selection(self):
        """Giữ selection (Treeview) khi đổi option/quality."""
        if hasattr(self, "video_tree"):
            sel = self.video_tree.selection()
            if sel:
                self.saved_listbox_selection = sel[0]
            if self.saved_listbox_selection:
                def restore():
                    try:
                        if self.saved_listbox_selection in self.video_tree.get_children():
                            self.video_tree.selection_set(self.saved_listbox_selection)
                            self.video_tree.see(self.saved_listbox_selection)
                    except Exception:
                        pass
                self.root.after(10, restore)
    
    def _make_placeholder_preview(self) -> ImageTk.PhotoImage:
        img = Image.new("RGB", (320, 180), color=(45, 45, 45))
        return ImageTk.PhotoImage(img)

    def _fetch_thumbnail_url(self, url: str) -> str | None:
        try:
            ydl_opts = {"quiet": True, "skip_download": True, "no_warnings": True}
            with yt_dlp.YoutubeDL(ydl_opts) as ydl:
                info = ydl.extract_info(url, download=False)
            if isinstance(info, dict):
                t = info.get("thumbnail")
                if t:
                    return t
                thumbs = info.get("thumbnails") or []
                if thumbs:
                    return thumbs[-1].get("url")
        except Exception:
            return None
        return None

    def _download_image(self, url: str) -> bytes | None:
        try:
            req = urllib.request.Request(
                url,
                headers={
                    "User-Agent": "Mozilla/5.0 (Windows NT 10.0; Win64; x64) "
                                  "AppleWebKit/537.36 (KHTML, like Gecko) "
                                  "Chrome/120.0.0.0 Safari/537.36"
                },
            )
            with urllib.request.urlopen(req, timeout=15) as resp:
                return resp.read()
        except Exception:
            return None

    def _update_preview_for_selection(self, iid: str) -> None:
        # Update title
        url = (getattr(self, "_fb_iid_to_url", {}) or {}).get(iid, "")
        if url:
            self.preview_title_var.set(url if len(url) <= 70 else url[:67] + "...")
        else:
            self.preview_title_var.set("Preview")

        # If already have a larger preview image, use it
        if iid in (getattr(self, "_preview_images", {}) or {}):
            img = self._preview_images[iid]
            self.preview_image_label.configure(image=img)
            self._preview_image = img
            return

        # Show placeholder while loading
        self.preview_image_label.configure(image=self._placeholder_preview)
        self._preview_image = self._placeholder_preview

        # Load thumbnail for preview lazily
        if iid in self._preview_loading:
            return
        self._preview_loading.add(iid)

        def worker():
            try:
                thumb_url = (getattr(self, "_thumb_url_by_iid", {}) or {}).get(iid)
                if not thumb_url:
                    thumb_url = self._fetch_thumbnail_url(url) if url else None
                    if thumb_url:
                        self._thumb_url_by_iid[iid] = thumb_url
                if not thumb_url:
                    return

                data = self._download_image(thumb_url)
                if not data:
                    return
                img = Image.open(BytesIO(data))
                img.thumbnail((320, 180))
                photo = ImageTk.PhotoImage(img)

                def apply():
                    if not hasattr(self, "preview_image_label"):
                        return
                    self._preview_images[iid] = photo
                    sel = self.video_tree.selection() if hasattr(self, "video_tree") else ()
                    if sel and sel[0] == iid:
                        self.preview_image_label.configure(image=photo)
                        self._preview_image = photo

                self.root.after(0, apply)
            finally:
                self._preview_loading.discard(iid)

        threading.Thread(target=worker, daemon=True).start()
    
    def create_progress_section(self, parent):
        """Tạo phần hiển thị progress và log"""
        progress_frame = ttk.LabelFrame(parent, text="Tiến trình", padding="10")
        progress_frame.pack(fill=tk.BOTH, expand=True, pady=10)
        
        # Progress bar
        self.progress_var = tk.DoubleVar()
        self.progress_bar = ttk.Progressbar(
            progress_frame,
            variable=self.progress_var,
            maximum=100,
            mode='indeterminate'
        )
        self.progress_bar.pack(fill=tk.X, pady=5)
        
        # Log text area
        self.log_text = scrolledtext.ScrolledText(
            progress_frame,
            height=10,
            wrap=tk.WORD,
            state=tk.DISABLED
        )
        self.log_text.pack(fill=tk.BOTH, expand=True)
        self._flush_pending_logs()
    
    def log_message(self, message: str):
        """Thread-safe: enqueue message to be displayed in log."""
        if message is None:
            return
        msg = str(message).strip()
        if not msg:
            return
        self._log_queue.put(msg)

    def _append_log_to_widget(self, message: str) -> None:
        if not hasattr(self, "log_text"):
            self._pending_logs.append(message)
            # avoid unbounded growth
            if len(self._pending_logs) > 500:
                self._pending_logs = self._pending_logs[-500:]
            return
        self.log_text.config(state=tk.NORMAL)
        self.log_text.insert(tk.END, message + "\n")
        self.log_text.see(tk.END)
        self.log_text.config(state=tk.DISABLED)

    def _flush_pending_logs(self) -> None:
        if not self._pending_logs or not hasattr(self, "log_text"):
            return
        for msg in self._pending_logs:
            self._append_log_to_widget(msg)
        self._pending_logs.clear()

    def _start_log_pump(self) -> None:
        def pump():
            try:
                # Drain queue quickly
                for _ in range(200):
                    msg = self._log_queue.get_nowait()
                    self._append_log_to_widget(msg)
            except queue.Empty:
                pass
            self.root.after(100, pump)

        self.root.after(100, pump)
    
    def start_progress(self):
        """Bắt đầu progress bar"""
        self.progress_bar.start(10)
        self.is_downloading = True
    
    def stop_progress(self):
        """Dừng progress bar"""
        self.progress_bar.stop()
        self.is_downloading = False
    
    def get_quality_value(self, combo_var, is_video=True):
        """Lấy giá trị quality từ combo box"""
        selected_text = combo_var.get()
        if is_video:
            quality_map = {
                "Best available quality": "best",
                "4320p (8K)": 4320,
                "2160p (4K)": 2160,
                "1440p (2K)": 1440,
                "1080p (Full HD)": 1080,
                "720p (HD)": 720,
                "480p": 480,
                "360p": 360,
                "240p": 240,
                "144p": 144,
            }
        else:
            quality_map = {
                "Best available quality": "best",
                "320 kbps (Highest quality)": 320,
                "256 kbps": 256,
                "192 kbps": 192,
                "160 kbps": 160,
                "128 kbps (Standard)": 128,
                "96 kbps": 96,
                "64 kbps": 64,
            }
        
        return quality_map.get(selected_text, "best")
    
    def start_direct_download(self):
        """Bắt đầu tải trực tiếp"""
        if self.is_downloading:
            messagebox.showwarning("Cảnh báo", "Đang có quá trình tải đang chạy!")
            return
        
        url = self.url_entry.get().strip()
        if not url:
            messagebox.showerror("Lỗi", "Vui lòng nhập URL!")
            return
        
        # Disable nút download
        self.download_btn.config(state=tk.DISABLED)
        
        # Convert YouTube URL
        url = convert_youtube_url(url)
        
        download_type = self.download_type.get()
        visitor_data = None
        
        def download_thread():
            try:
                self.start_progress()
                self.log_message(f"Bắt đầu tải từ: {url}")
                
                # Redirect stdout/stderr to UI log (realtime)
                old_stdout = sys.stdout
                old_stderr = sys.stderr
                sys.stdout = _QueueWriter(self._log_queue)
                sys.stderr = _QueueWriter(self._log_queue, prefix="ERR: ")
                
                try:
                    if download_type == "video":
                        quality = self.get_quality_value(self.video_quality_var, is_video=True)
                        # Chỉ thêm quality vào filename nếu là YouTube
                        add_quality_to_filename = self.is_youtube
                        self.log_message(f"Chất lượng: {quality}")
                        download_video(
                            url,
                            quality,
                            add_quality_to_filename=add_quality_to_filename,
                            visitor_data=visitor_data,
                        )
                    else:
                        bitrate = self.get_quality_value(self.audio_bitrate_var, is_video=False)
                        # Chỉ thêm bitrate vào filename nếu là YouTube
                        add_bitrate_to_filename = self.is_youtube
                        self.log_message(f"Bitrate: {bitrate}")
                        download_audio(
                            url,
                            bitrate,
                            add_bitrate_to_filename=add_bitrate_to_filename,
                            visitor_data=visitor_data,
                        )
                finally:
                    sys.stdout = old_stdout
                    sys.stderr = old_stderr
                
                self.stop_progress()
                self.log_message("Tải hoàn tất!")
                messagebox.showinfo("Thành công", "Tải hoàn tất!")
                
            except Exception as e:
                # Restore stdout/stderr before showing error
                sys.stdout = old_stdout if 'old_stdout' in locals() else sys.stdout
                sys.stderr = old_stderr if 'old_stderr' in locals() else sys.stderr
                self.stop_progress()
                self.log_message(f"Lỗi: {str(e)}")
                messagebox.showerror("Lỗi", f"Lỗi khi tải: {str(e)}")
            finally:
                # Enable lại nút download
                self.download_btn.config(state=tk.NORMAL)
        
        thread = threading.Thread(target=download_thread, daemon=True)
        thread.start()
    
    def start_facebook_scan(self):
        """Bắt đầu quét Facebook post"""
        if self.is_downloading:
            messagebox.showwarning("Cảnh báo", "Đang có quá trình đang chạy!")
            return
        
        post_url = self.fb_url_entry.get().strip()
        if not post_url:
            messagebox.showerror("Lỗi", "Vui lòng nhập URL Facebook post!")
            return
        
        if 'facebook.com' not in post_url.lower():
            messagebox.showerror("Lỗi", "URL không phải là Facebook post!")
            return
        
        # Disable nút scan
        self.scan_btn.config(state=tk.DISABLED)
        
        def scan_thread():
            try:
                self.start_progress()
                self.log_message("Đang quét video từ Facebook post...")
                self.log_message("Vui lòng đợi, quá trình này có thể mất vài phút...")
                
                # Redirect stdout/stderr to UI log (realtime)
                old_stdout = sys.stdout
                old_stderr = sys.stderr
                sys.stdout = _QueueWriter(self._log_queue)
                sys.stderr = _QueueWriter(self._log_queue, prefix="ERR: ")
                
                try:
                    video_urls = get_video_urls(
                        post_url=post_url,
                        headless=True,
                        load_all_comments=True,
                        max_scrolls=5,  # Giảm từ 10 xuống 5 để nhanh hơn
                        verbose=True
                    )
                finally:
                    sys.stdout = old_stdout
                    sys.stderr = old_stderr
                
                self.stop_progress()
                
                if not video_urls:
                    self.log_message("Không tìm thấy video nào trong post này.")
                    messagebox.showinfo("Thông báo", "Không tìm thấy video nào trong post này.")
                    return
                
                # Clear list + maps
                for iid in list(getattr(self, "_fb_iid_to_url", {}).keys()):
                    try:
                        self.video_tree.delete(iid)
                    except Exception:
                        pass
                self._fb_iid_to_url = {}
                self._thumb_url_by_iid = {}
                self._preview_images = {}
                self._preview_loading = set()
                self.saved_listbox_selection = None  # Reset saved selection khi load video mới
                
                for i, url in enumerate(video_urls, 1):
                    display_url = url if len(url) <= 80 else url[:77] + "..."
                    iid = self.video_tree.insert(
                        "",
                        "end",
                        text=f"{i}. {display_url}",
                    )
                    self._fb_iid_to_url[iid] = url
                
                self.log_message(f"Tìm thấy {len(video_urls)} video!")
                
                # Show download options
                self.download_options_frame.pack(fill=tk.X, pady=10)
                # Update canvas scrollregion after packing new widget
                if hasattr(self, 'fb_canvas'):
                    self.root.after(100, lambda: self.fb_canvas.configure(scrollregion=self.fb_canvas.bbox("all")))
                
            except Exception as e:
                # Restore stdout/stderr before showing error
                sys.stdout = old_stdout if 'old_stdout' in locals() else sys.stdout
                sys.stderr = old_stderr if 'old_stderr' in locals() else sys.stderr
                self.stop_progress()
                self.log_message(f"Lỗi khi quét: {str(e)}")
                messagebox.showerror("Lỗi", f"Lỗi khi quét video: {str(e)}")
            finally:
                # Enable lại nút scan
                self.scan_btn.config(state=tk.NORMAL)
        
        thread = threading.Thread(target=scan_thread, daemon=True)
        thread.start()
    
    def start_facebook_download(self):
        """Bắt đầu tải video từ Facebook"""
        if self.is_downloading:
            messagebox.showwarning("Cảnh báo", "Đang có quá trình tải đang chạy!")
            return
        
        selected = self.video_tree.selection() if hasattr(self, "video_tree") else ()
        if not selected:
            messagebox.showerror("Lỗi", "Vui lòng chọn video để tải!")
            return

        iid = selected[0]
        selected_url = (getattr(self, "_fb_iid_to_url", {}) or {}).get(iid)
        if not selected_url:
            messagebox.showerror("Lỗi", "Lựa chọn không hợp lệ!")
            return

        # Disable nút download
        self.fb_download_btn.config(state=tk.DISABLED)

        download_type = self.fb_download_type.get()
        
        def download_thread():
            try:
                self.start_progress()
                self.log_message(f"Bắt đầu tải từ: {selected_url}")
                
                # Redirect stdout/stderr to UI log (realtime)
                old_stdout = sys.stdout
                old_stderr = sys.stderr
                sys.stdout = _QueueWriter(self._log_queue)
                sys.stderr = _QueueWriter(self._log_queue, prefix="ERR: ")
                
                try:
                    if download_type == "video":
                        self.log_message("Chất lượng: best")
                        download_video(selected_url, "best", add_quality_to_filename=False)
                    else:
                        self.log_message("Bitrate: best")
                        download_audio(selected_url, "best", add_bitrate_to_filename=False)
                finally:
                    sys.stdout = old_stdout
                    sys.stderr = old_stderr
                
                self.stop_progress()
                self.log_message("Tải hoàn tất!")
                messagebox.showinfo("Thành công", "Tải hoàn tất!")
                
            except Exception as e:
                # Restore stdout/stderr before showing error
                sys.stdout = old_stdout if 'old_stdout' in locals() else sys.stdout
                sys.stderr = old_stderr if 'old_stderr' in locals() else sys.stderr
                self.stop_progress()
                self.log_message(f"Lỗi: {str(e)}")
                messagebox.showerror("Lỗi", f"Lỗi khi tải: {str(e)}")
            finally:
                # Enable lại nút download
                self.fb_download_btn.config(state=tk.NORMAL)
        
        thread = threading.Thread(target=download_thread, daemon=True)
        thread.start()


def main():
    """Hàm main để chạy GUI"""
    root = tk.Tk()
    app = VideoDownloaderGUI(root)
    root.mainloop()


if __name__ == "__main__":
    main()
