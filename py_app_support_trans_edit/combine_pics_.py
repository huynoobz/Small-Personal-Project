from PIL import Image
import os

parent_folder = input("Enter parent folder path: ")

def img_path(num):
    return os.path.join(parent_folder, f"{num}.jpg")

def combine_2_pic(img1, img2):
    if isinstance(img1, int):
        image1 = Image.open(img_path(img1))
    else:
        image1 = img1

    image2 = Image.open(img_path(img2))

    width1, height1 = image1.size
    width2, height2 = image2.size

    combined_image = Image.new("RGB", (width1, height1 + height2))

    combined_image.paste(image1, (0, 0))
    combined_image.paste(image2, (0, height1))

    return combined_image

while True:
    start_img, end_img = input(
        "Enter start and end img number: "
    ).split()

    start_img = int(start_img)
    end_img = int(end_img)

    combined_image = Image.open(img_path(start_img))

    for i in range(start_img + 1, end_img + 1):
        combined_image = combine_2_pic(combined_image, i)

    output_path = os.path.join(
        parent_folder,
        f"{start_img} {end_img}.jpg"
    )

    combined_image.save(output_path)

    for i in range(start_img, end_img + 1):
        os.remove(img_path(i))

    print(f"\n--- SAVED {start_img} {end_img}.jpg ---\n")

