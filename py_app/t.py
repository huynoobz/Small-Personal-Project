import time
from pynput import mouse, keyboard

# List to store recorded actions
actions = []
start_time = None

# Function to record mouse movements and clicks
def on_move(x, y):
    global start_time
    if start_time is None:
        start_time = time.time()
    actions.append(('move', (x, y), time.time() - start_time))

def on_click(x, y, button, pressed):
    global start_time
    if start_time is None:
        start_time = time.time()
    actions.append(('click', (x, y, button, pressed), time.time() - start_time))

def on_scroll(x, y, dx, dy):
    global start_time
    if start_time is None:
        start_time = time.time()
    actions.append(('scroll', (x, y, dx, dy), time.time() - start_time))

# Function to record keyboard events
def on_press(key):
    global start_time
    if start_time is None:
        start_time = time.time()
    actions.append(('key_press', key, time.time() - start_time))

def on_release(key):
    global start_time
    if start_time is None:
        start_time = time.time()
    actions.append(('key_release', key, time.time() - start_time))
    if key == keyboard.Key.esc:  # Stop listener
        return False

# Start recording
def start_recording():
    # Start mouse listener
    with mouse.Listener(on_move=on_move, on_click=on_click, on_scroll=on_scroll) as mouse_listener:
        # Start keyboard listener
        with keyboard.Listener(on_press=on_press, on_release=on_release) as keyboard_listener:
            print("Recording... Press ESC to stop.")
            keyboard_listener.join()  # Wait for the keyboard listener to finish

# Function to playback recorded actions
def playback():
    if not actions:
        print("No actions recorded.")
        return

    # Get the start time of the first action
    start_time = actions[0][2]
    
    for action in actions:
        action_type, action_data, action_time = action
        time_to_wait = action_time  # Wait for the appropriate time
        time.sleep(time_to_wait)  # Wait for the appropriate time

        if action_type == 'move':
            x, y = action_data
            mouse_controller = mouse.Controller()
            mouse_controller.position = (x, y)
        elif action_type == 'click':
            x, y, button, pressed = action_data
            mouse_controller = mouse.Controller()
            if pressed:
                mouse_controller.click(button)
        elif action_type == 'scroll':
            x, y, dx, dy = action_data
            mouse_controller = mouse.Controller()
            mouse_controller.scroll(dx, dy)
        elif action_type == 'key_press':
            key = action_data
            keyboard_controller = keyboard.Controller()
            keyboard_controller.press(key)
        elif action_type == 'key_release':
            key = action_data
            keyboard_controller = keyboard.Controller()
            keyboard_controller.release(key)

# Main function to run the application
if __name__ == "__main__":
    start_recording()
    playback()