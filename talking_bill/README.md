# Talking Bill

An Android application that monitors banking notifications and announces money transactions using text-to-speech.

## Features

- **Notification Monitoring**: Monitors notifications from banking apps
- **Transaction Announcements**: Announces money transactions using text-to-speech
- **Background Service**: Runs continuously in the background to monitor notifications
- **Customizable Speech**: Customize the prefix and currency text for announcements
- **Notification History**: Saves and displays notification history
- **Battery Optimization**: Handles battery optimization settings for reliable background operation

## Requirements

- Android 8.0 (API level 26) or higher
- Notification access permission
- Battery optimization exemption

## Setup

1. Install the app
2. Grant notification access permission
3. Disable battery optimization for the app
4. Enable the service using the main switch
5. Customize speech settings if needed

## Configuration

The app uses a JSON configuration file (`app_config.json`) to define:
- Supported banking apps
- Keywords for transaction detection
- Regular expressions for money amount extraction

## Usage

1. **Enable Service**: Toggle the main switch to start monitoring notifications
2. **Save Notifications**: Toggle the save switch to store notification history
3. **Filter & Voice**: Toggle to enable/disable filtering and voice announcements
4. **Clear History**: Use the clear button to remove all stored notifications
5. **Reset App**: Use the reset button to restore default settings

## Customization

- **Speech Prefix**: Customize the text spoken before the amount
- **Currency Text**: Customize the currency text spoken after the amount

## Troubleshooting

If the app stops monitoring notifications:
1. Check if notification access is still enabled
2. Verify battery optimization is disabled for the app
3. Try restarting the service
4. If issues persist, use the reset function

## Development

Built with:
- Kotlin
- Android Jetpack
- Material Design components
- Android Notification Listener Service

## License

This project is free and open source. Feel free to use, modify, and distribute it as you wish.