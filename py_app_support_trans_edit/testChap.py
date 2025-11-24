import os
import tempfile
import webbrowser
import base64
from io import BytesIO
from tkinter import Tk, filedialog
from PIL import Image
from urllib.parse import quote

def choose_images():
    root = Tk()
    root.withdraw()
    image_files = filedialog.askopenfilenames(
        title="Select Multiple Images",
        filetypes=[
            ("Image files", "*.png *.jpg *.jpeg *.bmp *.gif"),
            ("PNG files", "*.png"),
            ("JPEG files", "*.jpg *.jpeg"),
            ("BMP files", "*.bmp"),
            ("GIF files", "*.gif"),
            ("All files", "*.*")
        ]
    )
    root.destroy()
    return image_files


def create_long_strip_image(image_files):
    # Process the selected image files
    try:
        if not image_files:
            print("No images selected.")
            return None
            
        # Load all images
        images = []
        max_width = 0
        total_height = 0
        
        for image_path in image_files:
            try:
                img = Image.open(image_path)
                # Convert to RGB if necessary (for PNG with transparency, etc.)
                if img.mode in ('RGBA', 'LA', 'P'):
                    img = img.convert('RGB')
                images.append(img)
                max_width = max(max_width, img.width)
                total_height += img.height
            except Exception as e:
                print(f"Error loading image {image_path}: {e}")
                continue
        
        if not images:
            print("No valid images could be loaded.")
            return None
        
        # Create the long strip image
        strip_image = Image.new('RGB', (max_width, total_height), 'white')
        
        # Paste images vertically
        y_offset = 0
        for img in images:
            # Center the image horizontally if it's narrower than max_width
            x_offset = (max_width - img.width) // 2
            strip_image.paste(img, (x_offset, y_offset))
            y_offset += img.height
        
        # Determine output folder (use the folder of the first image)
        first_image_folder = os.path.dirname(image_files[0])
        
        # Get folder name for the output filename
        folder_name = os.path.basename(first_image_folder)
        output_filename = f"{folder_name}.png"
        output_path = os.path.join(first_image_folder, output_filename)
        
        # Save the strip image
        strip_image.save(output_path, 'PNG', quality=95)
        print(f"Long strip image created: {output_path}")
        print(f"Dimensions: {max_width} x {total_height} pixels")
        
        return output_path
        
    except Exception as e:
        print(f"Error creating long strip image: {e}")
        return None


def create_html_with_images(image_files):
    # Create HTML file with embedded images
    try:
        if not image_files:
            print("No images selected.")
            return None
            
        # Create image tags for HTML with embedded base64 data
        img_tags = []
        for image_path in image_files:
            try:
                # Open and process the image
                img = Image.open(image_path)
                
                # Convert to RGB if necessary (for PNG with transparency, etc.)
                if img.mode in ('RGBA', 'LA', 'P'):
                    img = img.convert('RGB')
                
                # Convert image to base64
                buffer = BytesIO()
                img.save(buffer, format='JPEG', quality=85)
                img_base64 = base64.b64encode(buffer.getvalue()).decode('utf-8')
                
                # Create img tag with embedded base64 data
                img_tag = f'<img src="data:image/jpeg;base64,{img_base64}" style="display:block; margin:auto; max-width:100%; height:auto; ">'
                img_tags.append(img_tag)
                
            except Exception as e:
                print(f"Error processing image {image_path}: {e}")
                continue
        
        # Create HTML content
        html_content = f"""
        <!DOCTYPE html>
        <html lang="en">
        <head>
            <meta charset="UTF-8">
            <meta name="viewport" content="width=device-width, initial-scale=1.0">
            <style>
                body {{
                    margin: 0;
                    padding: 20px;
                    background: #ecf0f1;
                    min-height: 100vh;
                }}
            </style>
        </head>
        <body>
            <div>
                {''.join(img_tags)}
            </div>
        </body>
        </html>
        """
        
        # Determine output folder (use the folder of the first image)
        first_image_folder = os.path.dirname(image_files[0])
        
        # Get folder name for the output filename
        folder_name = os.path.basename(first_image_folder)
        output_filename = f"{folder_name}.html"
        output_path = os.path.join(first_image_folder, output_filename)
        
        # Save the HTML file
        with open(output_path, 'w', encoding='utf-8') as f:
            f.write(html_content)
        
        print(f"HTML gallery created: {output_path}")
        print(f"Number of images: {len(image_files)}")
        
        return output_path
        
    except Exception as e:
        print(f"Error creating HTML gallery: {e}")
        return None

def main():
    image_files = choose_images()
    if not image_files:
        print("No images selected.")
        return
    
    # Ask user what type of output they want
    #print("\nChoose output type:")
    #print("1. Create long strip image (PNG)")
    #print("2. Create HTML gallery")
    
    #choice = input("Enter your choice (1 or 2): ").strip()
    
    choice = "2"

    if choice == "1":
        output_path = create_long_strip_image(image_files)
        if output_path:
            # Open the folder containing the created image
            folder_path = os.path.dirname(output_path)
            webbrowser.open(f'file:///{folder_path}')
            print(f"Opening folder: {folder_path}")
        else:
            print("Failed to create long strip image.")
    
    elif choice == "2":
        output_path = create_html_with_images(image_files)
        if output_path:
            # Open the HTML file in browser
            webbrowser.open(f'file:///{output_path}')
            print(f"Opening HTML gallery in browser")
        else:
            print("Failed to create HTML gallery.")
    
    else:
        print("Invalid choice. Please run the program again and select 1 or 2.")

if __name__ == "__main__":
    main()