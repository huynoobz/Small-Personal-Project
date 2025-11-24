import keyboard
import time

input("Please setting up your windows before record.\nPress Enter to start record..")
print("Start recording... Press ESC to stop record.")
events = keyboard.record(until='esc')
events = events[:-1]
d_time = int(input("Set delay time (in second) for each replay (after Enter start replay): "))
print("Replaying keystrokes.... Ctrl + C to stop.")
while True:
	time.sleep(d_time)
	keyboard.play(events, speed_factor=1.0)