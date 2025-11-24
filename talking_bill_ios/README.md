# Talking Bill iOS - Enhanced Notification Collection

An iOS application that monitors banking notifications and announces money transactions using text-to-speech, with enhanced capabilities to collect any push notifications.

## 🚀 Enhanced Features

### **Comprehensive Notification Collection**
- **Universal Collection**: Collects notifications from all sources within iOS limitations
- **Real-time Processing**: Processes notifications as they arrive
- **Background Monitoring**: Continues monitoring when app is in background
- **Smart Detection**: Automatically detects banking-related content
- **Multiple App Support**: Supports 7+ major Vietnamese banking and payment apps

### **Advanced Notification Processing**
- **Generic Banking Detection**: Detects banking transactions even from unknown apps
- **Enhanced Regex Patterns**: Improved money amount extraction
- **Multi-language Support**: Handles Vietnamese and English banking terms
- **Notification Categories**: Organizes notifications by type
- **Duplicate Prevention**: Prevents duplicate notification collection

## 📱 Supported Apps

The app now supports comprehensive detection for:

- **Agribank** - Traditional banking notifications
- **MoMo** - Mobile payment platform
- **Vietcombank** - Major Vietnamese bank
- **Techcombank** - Technology-focused bank
- **ZaloPay** - Social payment platform
- **ShopeePay** - E-commerce payment
- **Generic Banking** - Any app with banking-related content

## 🔧 Technical Enhancements

### **Notification Collection Methods**

1. **Real-time Collection**
   - Processes notifications as they arrive
   - Uses `UNUserNotificationCenterDelegate` for immediate processing
   - Handles both foreground and background notifications

2. **Periodic Collection**
   - Checks for delivered notifications every 5 seconds
   - Collects notifications when app becomes active
   - Monitors app lifecycle events

3. **Simulation Mode**
   - Test notification collection with realistic data
   - Simulates various banking apps and scenarios
   - Useful for testing and demonstration

### **Enhanced Detection Algorithms**

```swift
// Generic banking keyword detection
let bankingKeywords = [
    "bank", "ngân hàng", "atm", "chuyển tiền", 
    "nhận tiền", "gửi tiền", "rút tiền", 
    "số dư", "balance", "transaction", "giao dịch"
]

// Advanced money pattern recognition
let genericPatterns = [
    "\\d{1,3}(?:[.,]\\d{3})*(?:[.,]\\d{2})?\\s*(?:đ|vnd|₫)",
    "\\d{1,3}(?:[.,]\\d{3})*(?:[.,]\\d{2})?\\s*(?:đồng|dong)",
    "\\+\\s*\\d{1,3}(?:[.,]\\d{3})*(?:[.,]\\d{2})?",
    "\\d{1,3}(?:[.,]\\d{3})*(?:[.,]\\d{2})?\\s*(?:k|nghìn|triệu|tỷ)"
]
```

## 🎯 Usage Instructions

### **1. Initial Setup**
1. Open the app and grant notification permissions
2. Enable "Start Collecting" to begin monitoring
3. Customize speech settings if needed

### **2. Collection Modes**

#### **Automatic Collection**
- Toggle "Start Collecting" to begin real-time monitoring
- App will automatically collect and process notifications
- Works in both foreground and background

#### **Simulation Mode**
- Use "Simulate Collection" for testing
- Generates realistic banking notifications
- Perfect for testing speech functionality

#### **Test Notifications**
- "Send Test Notification" - Single test notification
- "Send Multiple Test Notifications" - Multiple banking scenarios

### **3. Configuration**

#### **Speech Customization**
- **Prefix**: Text spoken before amount (default: "đã nhận")
- **Currency**: Text spoken after amount (default: "đồng")
- **Rate**: Speech speed (0.4x for clarity)

#### **Filtering Options**
- **Save Notifications**: Toggle to save notification history
- **Filter & Voice**: Toggle to enable/disable processing and speech

## 🔒 iOS Limitations & Workarounds

### **Security Limitations**
iOS doesn't allow apps to access notifications from other apps for security reasons. Our workarounds:

1. **Local Notifications**: Process our own test notifications
2. **Background App Refresh**: Check for notifications when app becomes active
3. **Notification Center Integration**: Use iOS notification system
4. **Simulation Mode**: Test with realistic banking scenarios

### **Background Execution**
- Limited background processing time
- Uses background app refresh when available
- Continues monitoring when app is active

## 🏗️ Architecture

### **Core Components**

```
TalkingBillApp (Main Entry)
├── ContentView (UI)
├── NotificationManager (Permissions & Processing)
├── NotificationService (Core Service)
├── NotificationCollector (Enhanced Collection)
├── SpeechManager (Text-to-Speech)
└── DataManager (Persistence)
```

### **Data Flow**

1. **Notification Arrives** → NotificationService
2. **Processing** → NotificationManager (filtering & amount extraction)
3. **Collection** → NotificationCollector (storage & deduplication)
4. **Speech** → SpeechManager (text-to-speech)
5. **Persistence** → DataManager (save to storage)

## 🧪 Testing Features

### **Test Notifications**
- Single test notification with MoMo scenario
- Multiple test notifications covering all supported apps
- Realistic Vietnamese banking messages

### **Simulation Mode**
- Generates 6 different banking scenarios
- Tests amount extraction algorithms
- Validates speech functionality

## 📊 Monitoring & Analytics

### **Collection Status**
- Real-time collection status indicator
- Notification count tracking
- Processing statistics

### **Data Export**
- Export notification history to JSON
- Import/restore functionality
- Data backup capabilities

## 🚀 Future Enhancements

1. **Push Notification Integration**: Real-time server notifications
2. **Machine Learning**: Improved transaction detection
3. **Widget Support**: iOS widget for quick access
4. **Apple Watch**: Extend to watchOS
5. **Siri Integration**: Voice commands and shortcuts

## 📋 Requirements

- **iOS**: 15.0 or higher
- **Xcode**: 15.0 or higher
- **Swift**: 5.0 or higher
- **Permissions**: Notification access required

## 🔧 Development

### **Building the Project**
```bash
# Open in Xcode
open TalkingBill.xcodeproj

# Build and run
⌘+R
```

### **Key Files**
- `NotificationCollector.swift` - Enhanced collection logic
- `NotificationManager.swift` - Processing and filtering
- `NotificationService.swift` - Core notification handling
- `app_config.json` - Banking app configurations

## 📝 License

This project is free and open source. Feel free to use, modify, and distribute it as you wish.

---

**Note**: Due to iOS security model, this app cannot directly access notifications from other apps. The enhanced collection features work within iOS limitations and provide the best possible notification monitoring experience.