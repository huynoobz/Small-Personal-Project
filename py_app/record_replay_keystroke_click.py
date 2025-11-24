from pynput import keyboard, mouse
import time

# Store recorded events
recorded_events = []
start_time = None
stop_flag = False  # Global flag to stop both listeners

# Mouse and keyboard controllers for playback
mouse_controller = mouse.Controller()
keyboard_controller = keyboard.Controller()

# Keyboard listener
def on_key_press(key):
    global start_time
    if start_time is None:
        start_time = time.time()  # Initialize start_time on first key press
    # Do not record ESC key press
    if key != keyboard.Key.esc:
        recorded_events.append(("key_press", key, time.time() - start_time))

def on_key_release(key):
    global stop_flag, start_time
    if start_time is None:
        start_time = time.time()  # Ensure it's initialized before using
    # Stop recording when ESC is pressed
    if key == keyboard.Key.esc:
        stop_flag = True  # Set the stop flag
        return False  # Stop the keyboard listener
    recorded_events.append(("key_release", key, time.time() - start_time))

# Mouse listener
def on_click(x, y, button, pressed):
    global start_time, stop_flag
    print(x,y)
    if start_time is None:
        start_time = time.time()  # Ensure it's initialized before using
    if stop_flag:  # If ESC was pressed, stop mouse listener
        return False
    # Store relative position instead of absolute
    recorded_events.append(("mouse_click", (x, y, button, pressed), time.time() - start_time))

# Start recording
def record():
    global stop_flag, start_time, initial_mouse_position, recorded_events
    stop_flag = False  # Reset flag before recording
    start_time = time.time()  # Ensure start_time is set at the beginning
    recorded_events = []
    print("Recording started... Press ESC to stop.")
    with keyboard.Listener(on_press=on_key_press, on_release=on_key_release) as k_listener, \
         mouse.Listener(on_click=on_click) as m_listener:
        while not stop_flag:  # Keep checking the flag
            time.sleep(0.01)
        m_listener.stop()  # Stop the mouse listener manually
    print("Recording stopped.")

# Playback function
def play():
    print("Replaying recorded actions...")
    if not recorded_events:
        print("No recorded actions to play.")
        return
    start_playback_time = time.time()
    for event in recorded_events:
        event_type, data, event_time = event
        time.sleep(event_time - (time.time() - start_playback_time))  # Sync timing
        if event_type == "key_press":
            try:
                keyboard_controller.press(data)
            except Exception as e:
                print(f"Error pressing key {data}: {e}")
        elif event_type == "key_release":
            try:
                keyboard_controller.release(data)
            except Exception as e:
                print(f"Error releasing key {data}: {e}")
        elif event_type == "mouse_click":
            x, y, button, pressed = data
            mouse_controller.position = (x/2, y/2)
            if pressed:
                mouse_controller.press(button)
            else:
                mouse_controller.release(button)
    print("Playback complete.")

input("Please setting up your windows before record.\nPress Enter to start record..")
record()
d_time = int(input("Set delay time (in second) for each replay (after Enter start replay): "))
while True:
	time.sleep(d_time)
	play()
