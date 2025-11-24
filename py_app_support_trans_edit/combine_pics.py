from PIL import Image
import os

parent_folder = input("Enter parent folder path: ")

def combine_2_pic(img1, img2):
	# Load the images
	if(type(img1) == int):
		image1 = Image.open(parent_folder + "\\" + str(img1) + ".jpg")		
	else:
		image1 = img1
	image2 = Image.open(parent_folder + "\\" + str(img2) + ".jpg")

	# Get the width and height of the first image
	width1, height1 = image1.size
	# Get the width and height of the second image
	width2, height2 = image2.size

	# Create a new image with the width of the first image and the combined height of both images
	combined_image = Image.new("RGB", (width1, height1 + height2))

	# Paste the first image at the top
	combined_image.paste(image1, (0, 0))
	# Paste the second image at the bottom
	combined_image.paste(image2, (0, height1))
	
	return combined_image

while(True):
	start_img, end_img = input("Enter start and end img number: ").split()
	start_img, end_img = int(start_img), int(end_img)
	combined_image = Image.open(parent_folder + "\\" + str(start_img) + ".jpg")

	for i in range(start_img+1, end_img+1):
		combined_image = combine_2_pic(combined_image, i)

	# Save or show the combined image
	combined_image.save(parent_folder + "\\" + str(start_img) + " " + str(end_img) + ".jpg")
	for i in range(start_img, end_img+1):
		os.remove(parent_folder + "\\" + str(i) + ".jpg")
	print("\n--- SAVED " + str(start_img) + " " + str(end_img) + ".jpg" + " ---\n")
	
