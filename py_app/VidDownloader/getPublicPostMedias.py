"""
getPublicPostMedias - Module để lấy media URLs từ Facebook public posts

Module này sử dụng Selenium với Chrome browser để scrape media URLs từ 
Facebook public posts mà không cần đăng nhập hoặc cookies.

Ví dụ sử dụng:
    from getPublicPostMedias import get_media_urls
    
    # Lấy media từ một post
    urls = get_media_urls("https://www.facebook.com/share/p/1Djg11camw/")
    
    # Với các tùy chọn
    urls = get_media_urls(
        post_url="https://www.facebook.com/share/p/1Djg11camw/",
        headless=True,
        max_scrolls=15,
        load_all_comments=True  # scroll để load thêm nội dung
    )
"""

import re
import time
from typing import List, Optional
from selenium import webdriver
from selenium.webdriver.common.by import By
from selenium.webdriver.chrome.options import Options


class FacebookMediaExtractor:
    """Class để extract media từ Facebook posts"""
    
    def __init__(self, headless: bool = False, verbose: bool = False):
        """
        Khởi tạo extractor
        
        Args:
            headless: Chạy Chrome ở chế độ headless (không hiển thị cửa sổ)
            verbose: In thông tin debug
        """
        self.headless = headless
        self.verbose = verbose
        self.driver = None
    
    def _setup_driver(self) -> webdriver.Chrome:
        """Setup Chrome WebDriver"""
        chrome_options = Options()
        if self.headless:
            chrome_options.add_argument("--headless=new")
        chrome_options.add_argument("--no-sandbox")
        chrome_options.add_argument("--disable-dev-shm-usage")
        chrome_options.add_argument("--disable-blink-features=AutomationControlled")
        chrome_options.add_experimental_option("excludeSwitches", ["enable-automation"])
        chrome_options.add_experimental_option('useAutomationExtension', False)
        chrome_options.add_argument("--user-agent=Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36")
        
        try:
            driver = webdriver.Chrome(options=chrome_options)
            driver.execute_script("Object.defineProperty(navigator, 'webdriver', {get: () => undefined})")
            driver.set_window_size(1920, 1080)
            return driver
        except Exception as e:
            if self.verbose:
                print(f"Error setting up Chrome: {e}")
            try:
                from selenium.webdriver.chrome.service import Service
                from webdriver_manager.chrome import ChromeDriverManager
                service = Service(ChromeDriverManager().install())
                driver = webdriver.Chrome(service=service, options=chrome_options)
                driver.execute_script("Object.defineProperty(navigator, 'webdriver', {get: () => undefined})")
                driver.set_window_size(1920, 1080)
                return driver
            except ImportError:
                raise ImportError("Please install webdriver-manager: pip install webdriver-manager")
    
    def _resolve_url(self, url: str) -> str:
        """Resolve share link to actual post URL"""
        if self.verbose:
            print(f"Resolving URL: {url}")
        self.driver.get(url)
        time.sleep(1)  # Giảm từ 2 xuống 1 giây
        resolved_url = self.driver.current_url
        if self.verbose and resolved_url != url:
            print(f"Resolved to: {resolved_url}")
        return resolved_url
    
    def _scroll_to_load_comments(self, max_scrolls: int = 10):
        """Scroll down to load more comments"""
        if self.verbose:
            print(f"Scrolling to load comments (max {max_scrolls} scrolls)...")
        
        last_height = self.driver.execute_script("return document.body.scrollHeight")
        
        for i in range(max_scrolls):
            self.driver.execute_script("window.scrollTo(0, document.body.scrollHeight);")
            time.sleep(1)  # Giảm từ 2 xuống 1 giây
            
            new_height = self.driver.execute_script("return document.body.scrollHeight")
            if new_height == last_height:
                if self.verbose:
                    print(f"No more content after {i+1} scrolls")
                break
            last_height = new_height
            if self.verbose:
                print(f"Scroll {i+1}/{max_scrolls} - Page height: {new_height}")
        
        self.driver.execute_script("window.scrollTo(0, 0);")
        time.sleep(0.5)  # Giảm từ 1 xuống 0.5 giây
    
    def _extract_videos_from_html(self, html: str) -> List[str]:
        """Extract only video URLs from HTML (exclude images)"""
        video_urls = set()
        
        # Video URLs (direct video files)
        video_patterns = [
            r'https?://[^"\s]+\.(mp4|webm|m3u8)',
            r'https?://[^"\s]*video[^"\s]*\.facebook\.com[^"\s]+',
            r'playable_url[":]\s*"([^"]+)"',
            r'playable_url_quality_hd[":]\s*"([^"]+)"',
            r'hd_src[":]\s*"([^"]+)"',
            r'sd_src[":]\s*"([^"]+)"',
        ]
        
        for pattern in video_patterns:
            matches = re.findall(pattern, html, re.IGNORECASE)
            for match in matches:
                url = match if isinstance(match, str) and match.startswith('http') else f"https://{match}"
                if url.startswith(('http://', 'https://')):
                    video_urls.add(url)
        
        # Facebook video post links
        video_post_patterns = [
            r'https?://(?:www\.)?facebook\.com/[^/\s"]+/videos/\d+',
            r'https?://(?:www\.)?facebook\.com/[^/\s"]+/video\.php\?v=\d+',
            r'href="(https?://[^"]*facebook\.com[^"]*video[^"]*)"',
            r'data-uri="(https?://[^"]*facebook\.com[^"]*video[^"]*)"',
        ]
        
        for pattern in video_post_patterns:
            matches = re.findall(pattern, html, re.IGNORECASE)
            for match in matches:
                clean_url = match.split('?')[0].split('&')[0].rstrip('/')
                if 'facebook.com' in clean_url and ('/videos/' in clean_url or '/video' in clean_url):
                    video_urls.add(clean_url)
        
        # Filter out invalid URLs and images
        filtered_urls = []
        for url in video_urls:
            # Skip image URLs
            if any(url.lower().endswith(ext) for ext in ['.jpg', '.jpeg', '.png', '.gif', '.webp']):
                continue
            if '/photo' in url.lower() and 'facebook.com' in url:
                continue
            
            if (len(url) > 20 and
                not url.endswith(('://png', '://jpg', '://jpeg', '://gif', '://webp')) and
                not url.startswith(('https://png', 'https://jpg', 'http://png', 'http://jpg')) and
                ('facebook.com' in url or '.' in url.split('://')[1].split('/')[0])):
                filtered_urls.append(url)
        
        return filtered_urls
    
    def _extract_media_from_html(self, html: str) -> List[str]:
        """Extract all media URLs from HTML"""
        media_urls = set()
        
        # Video URLs (direct video files)
        video_patterns = [
            r'https?://[^"\s]+\.(mp4|webm|m3u8)',
            r'https?://[^"\s]*video[^"\s]*\.facebook\.com[^"\s]+',
            r'playable_url[":]\s*"([^"]+)"',
            r'playable_url_quality_hd[":]\s*"([^"]+)"',
            r'hd_src[":]\s*"([^"]+)"',
            r'sd_src[":]\s*"([^"]+)"',
        ]
        
        for pattern in video_patterns:
            matches = re.findall(pattern, html, re.IGNORECASE)
            for match in matches:
                url = match if isinstance(match, str) and match.startswith('http') else f"https://{match}"
                if url.startswith(('http://', 'https://')):
                    media_urls.add(url)
        
        # Facebook video post links
        video_post_patterns = [
            r'https?://(?:www\.)?facebook\.com/[^/\s"]+/videos/\d+',
            r'https?://(?:www\.)?facebook\.com/[^/\s"]+/video\.php\?v=\d+',
            r'href="(https?://[^"]*facebook\.com[^"]*video[^"]*)"',
            r'data-uri="(https?://[^"]*facebook\.com[^"]*video[^"]*)"',
        ]
        
        for pattern in video_post_patterns:
            matches = re.findall(pattern, html, re.IGNORECASE)
            for match in matches:
                clean_url = match.split('?')[0].split('&')[0].rstrip('/')
                if 'facebook.com' in clean_url and ('/videos/' in clean_url or '/video' in clean_url):
                    media_urls.add(clean_url)
        
        # Image URLs
        image_patterns = [
            r'https?://[^"\s<>]+\.(jpg|jpeg|png|gif|webp)(\?[^"\s<>]*)?',
            r'image[":]\s*"https?://([^"]+)"',
            r'image_uri[":]\s*"https?://([^"]+)"',
            r'src="(https?://[^"]*facebook\.com[^"]*photo[^"]*)"',
        ]
        
        for pattern in image_patterns:
            matches = re.findall(pattern, html, re.IGNORECASE)
            for match in matches:
                if isinstance(match, tuple):
                    url = match[0] if match[0].startswith('http') else f"https://{match[0]}"
                else:
                    url = match if isinstance(match, str) and match.startswith('http') else f"https://{match}"
                
                if (url.startswith(('http://', 'https://')) and 
                    len(url) > 10 and 
                    (not url.endswith(('.png', '.jpg', '.jpeg', '.gif', '.webp')) or
                     ('facebook.com' in url or '/' in url.split('://')[1].split('/')[0]))):
                    media_urls.add(url)
        
        # Filter out invalid URLs
        filtered_urls = []
        for url in media_urls:
            if (len(url) > 20 and
                not url.endswith(('://png', '://jpg', '://jpeg', '://gif', '://webp')) and
                not url.startswith(('https://png', 'https://jpg', 'http://png', 'http://jpg')) and
                ('facebook.com' in url or '.' in url.split('://')[1].split('/')[0])):
                filtered_urls.append(url)
        
        return filtered_urls
    
    def get_video_urls(
        self,
        post_url: str,
        load_all_comments: bool = True,
        max_scrolls: int = 10,
        save_debug_html: Optional[str] = None
    ) -> List[str]:
        """
        Lấy chỉ video URLs từ Facebook post (bỏ qua ảnh)
        
        Args:
            post_url: URL của Facebook post (có thể là share link)
            load_all_comments: Có scroll để load thêm nội dung không
            max_scrolls: Số lần scroll tối đa để load thêm nội dung
            save_debug_html: Nếu được cung cấp, lưu HTML vào file này (optional)
        
        Returns:
            List các video URLs tìm được
        """
        try:
            if self.verbose:
                print("Setting up Chrome browser...")
            self.driver = self._setup_driver()
            
            # Resolve URL if it's a share link
            real_url = self._resolve_url(post_url)
            
            if self.verbose:
                print(f"Loading page: {real_url}")
            self.driver.get(real_url)
            time.sleep(3)  # Giảm từ 5 xuống 3 giây
            
            # Save initial HTML if requested
            initial_html = self.driver.page_source
            if save_debug_html:
                with open(f"{save_debug_html}_initial.html", "w", encoding="utf-8") as f:
                    f.write(initial_html)
            
            # Extract videos from initial page first (faster)
            video_urls = self._extract_videos_from_html(initial_html)
            
            # Only scroll if we want to load comments and haven't found many videos
            if load_all_comments and len(video_urls) < 3:
                if self.verbose:
                    print("Trying to load more videos from comments...")
                # Reduce scrolls for faster processing
                reduced_scrolls = min(max_scrolls, 5)  # Max 5 scrolls instead of 10
                self._scroll_to_load_comments(max_scrolls=reduced_scrolls)
            
            # Get final HTML
            final_html = self.driver.page_source
            
            # Save final HTML if requested
            if save_debug_html:
                with open(f"{save_debug_html}_final.html", "w", encoding="utf-8") as f:
                    f.write(final_html)
            
            # Extract only videos from HTML (merge with initial results)
            if self.verbose:
                print("Extracting video URLs from HTML...")
            final_video_urls = self._extract_videos_from_html(final_html)
            # Merge results (use set to avoid duplicates)
            video_urls = list(set(video_urls + final_video_urls))
            
            if self.verbose:
                print(f"Found {len(video_urls)} video URLs")
            
            return video_urls
            
        except Exception as e:
            if self.verbose:
                import traceback
                traceback.print_exc()
            raise
        finally:
            if self.driver:
                if self.verbose:
                    print("Closing browser...")
                self.driver.quit()
                self.driver = None
    
    def get_media_urls(
        self,
        post_url: str,
        load_all_comments: bool = True,
        max_scrolls: int = 10,
        save_debug_html: Optional[str] = None
    ) -> List[str]:
        """
        Lấy tất cả media URLs từ Facebook post
        
        Args:
            post_url: URL của Facebook post (có thể là share link)
            load_all_comments: Có scroll để load thêm nội dung không
            max_scrolls: Số lần scroll tối đa để load thêm nội dung
            save_debug_html: Nếu được cung cấp, lưu HTML vào file này (optional)
        
        Returns:
            List các media URLs tìm được
        """
        try:
            if self.verbose:
                print("Setting up Chrome browser...")
            self.driver = self._setup_driver()
            
            # Resolve URL if it's a share link
            real_url = self._resolve_url(post_url)
            
            if self.verbose:
                print(f"Loading page: {real_url}")
            self.driver.get(real_url)
            time.sleep(5)
            
            # Save initial HTML if requested
            if save_debug_html:
                initial_html = self.driver.page_source
                with open(f"{save_debug_html}_initial.html", "w", encoding="utf-8") as f:
                    f.write(initial_html)
            
            # Scroll to load more content if needed
            if load_all_comments:
                self._scroll_to_load_comments(max_scrolls=max_scrolls)
            
            # Get final HTML
            final_html = self.driver.page_source
            
            # Save final HTML if requested
            if save_debug_html:
                with open(f"{save_debug_html}_final.html", "w", encoding="utf-8") as f:
                    f.write(final_html)
            
            # Extract media from HTML
            if self.verbose:
                print("Extracting media URLs from HTML...")
            media_urls = self._extract_media_from_html(final_html)
            
            if self.verbose:
                print(f"Found {len(media_urls)} media URLs")
            
            return media_urls
            
        except Exception as e:
            if self.verbose:
                import traceback
                traceback.print_exc()
            raise
        finally:
            if self.driver:
                if self.verbose:
                    print("Closing browser...")
                self.driver.quit()
                self.driver = None


def get_video_urls(
    post_url: str,
    headless: bool = False,
    load_all_comments: bool = True,
    max_scrolls: int = 10,
    verbose: bool = False,
    save_debug_html: Optional[str] = None
) -> List[str]:
    """
    Hàm chính để lấy chỉ video URLs từ Facebook post (bỏ qua ảnh)
    
    Đây là hàm convenience để sử dụng nhanh module này.
    
    Args:
        post_url: URL của Facebook post (có thể là share link)
        headless: Chạy Chrome ở chế độ headless (không hiển thị cửa sổ)
        load_all_comments: Có scroll để load thêm nội dung không
        max_scrolls: Số lần scroll tối đa để load thêm nội dung
        verbose: In thông tin debug
        save_debug_html: Nếu được cung cấp, lưu HTML vào file này (optional)
    
    Returns:
        List các video URLs tìm được
        
    Example:
        >>> from getPublicPostMedias import get_video_urls
        >>> urls = get_video_urls("https://www.facebook.com/share/p/1Djg11camw/")
        >>> print(urls)
        ['https://www.facebook.com/user/videos/123456789']
    """
    extractor = FacebookMediaExtractor(headless=headless, verbose=verbose)
    return extractor.get_video_urls(
        post_url=post_url,
        load_all_comments=load_all_comments,
        max_scrolls=max_scrolls,
        save_debug_html=save_debug_html
    )


def get_media_urls(
    post_url: str,
    headless: bool = False,
    load_all_comments: bool = True,
    max_scrolls: int = 10,
    verbose: bool = False,
    save_debug_html: Optional[str] = None
) -> List[str]:
    """
    Hàm chính để lấy media URLs từ Facebook post
    
    Đây là hàm convenience để sử dụng nhanh module này.
    
    Args:
        post_url: URL của Facebook post (có thể là share link)
        headless: Chạy Chrome ở chế độ headless (không hiển thị cửa sổ)
        load_all_comments: Có scroll để load thêm nội dung không
        max_scrolls: Số lần scroll tối đa để load thêm nội dung
        verbose: In thông tin debug
        save_debug_html: Nếu được cung cấp, lưu HTML vào file này (optional)
    
    Returns:
        List các media URLs tìm được
        
    Example:
        >>> from getPublicPostMedias import get_media_urls
        >>> urls = get_media_urls("https://www.facebook.com/share/p/1Djg11camw/")
        >>> print(urls)
        ['https://www.facebook.com/user/videos/123456789']
    """
    extractor = FacebookMediaExtractor(headless=headless, verbose=verbose)
    return extractor.get_media_urls(
        post_url=post_url,
        load_all_comments=load_all_comments,
        max_scrolls=max_scrolls,
        save_debug_html=save_debug_html
    )


# ========== TEST/EXAMPLE ==========
if __name__ == "__main__":
    import json
    
    post_url = "https://www.facebook.com/share/p/1Djg11camw/"
    expected_url = "https://www.facebook.com/huy.bui.597301/videos/738014022705447"
    
    print("="*60)
    print("Facebook Public Post Media Extractor")
    print("="*60)
    
    try:
        urls = get_media_urls(
            post_url=post_url,
            headless=False,
            load_all_comments=True,
            max_scrolls=15,
            verbose=True,
            save_debug_html="debug_page"
        )
        
        print(f"\n{'='*60}")
        print(f"Found {len(urls)} media URLs:")
        print(f"{'='*60}")
        for i, url in enumerate(urls, 1):
            print(f"{i}. {url}")
        
        # Check if expected video URL is found
        found_expected = any(expected_url in url or "738014022705447" in url for url in urls)
        if found_expected:
            print(f"\n[SUCCESS] Found expected video URL!")
        elif len(urls) > 0:
            print(f"\nFound {len(urls)} media URLs")
        else:
            print(f"\n[WARNING] No media found")
        
        # Save results to file
        with open("media_urls.json", "w", encoding="utf-8") as f:
            json.dump(urls, f, indent=2, ensure_ascii=False)
        print(f"\nResults saved to media_urls.json")
        
    except Exception as e:
        print(f"\nError: {e}")
        import traceback
        traceback.print_exc()
