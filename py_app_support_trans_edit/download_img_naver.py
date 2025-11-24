import requests
from bs4 import BeautifulSoup
import os

def download_images_from_url_list(url_list, output_folder):
  """Downloads images from a list of URLs to a specified output folder.

  Args:
    url_list: A list of image URLs to download.
    output_folder: The path to the output folder.
  """
  headers = {'User-Agent': 'Mozilla/5.0 (Windows NT 10.0; Win64; x64)'}

  if not os.path.exists(output_folder):
    os.makedirs(output_folder)
  
  i=0
  for url in url_list:
    try:
      response = requests.get(url, headers=headers, stream=True)
      response.raise_for_status()  # Raise an exception for error HTTP status codes

      i+=1
      filename = str(i)+'.'+url.split('/')[-1].split('.')[-1]
      filepath = os.path.join(output_folder, filename)

      with open(filepath, 'wb') as f:
        for chunk in response.iter_content(1024):
          f.write(chunk)
      print(f"Downloaded: {filepath}")
    except requests.exceptions.RequestException as e:
      print(f"Error downloading image from {url}: {e}")

def download_images_from_div(url):
  """Downloads all image srcs from a div with id 'sectionContWide' from a given URL.

  Args:
    url: The URL of the webpage to scrape.

  Returns:
    A list of image srcs.
  """

  response = requests.get(url)
  soup = BeautifulSoup(response.text, 'html.parser')

  div = soup.find('div', {'id': 'sectionContWide'})
  if not div:
    print(f"Div with id 'sectionContWide' not found on page {url}")
    return []

  image_tags = div.find_all('img')
  image_srcs = [img['src'] for img in image_tags]

  return image_srcs

if __name__ == '__main__':
  url = input("Enter the URL of Chapter: ")
  image_srcs = download_images_from_div(url)
  output_folder = input("Enter the Folder Path: ")
  download_images_from_url_list(image_srcs, output_folder)
