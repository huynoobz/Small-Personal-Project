import os
import shutil

def move_files_with_copy(folder):
    # Get the current folder name
    current_folder = folder
    folder_name = os.path.basename(current_folder)
    new_folder_name = f"{folder_name}_"
    
    # Create the new folder if it doesn't exist
    new_folder_path = os.path.join(current_folder, new_folder_name)
    if not os.path.exists(new_folder_path):
        os.makedirs(new_folder_path)
    
    # Iterate over files in the current folder
    for file_name in os.listdir(current_folder):
        file_path = os.path.join(current_folder, file_name)
        
        # Check if it's a file and contains "copy" in its name
        if os.path.isfile(file_path) and "copy" in file_name:
            shutil.move(file_path, new_folder_path)
            print(f"Moved: {file_name} to {new_folder_name}")

if __name__ == "__main__":
    move_files_with_copy(input("input folder: "))
