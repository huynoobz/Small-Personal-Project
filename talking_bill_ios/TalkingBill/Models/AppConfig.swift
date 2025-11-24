import Foundation

// MARK: - App Configuration Models

struct AppConfig: Codable {
    let agribank: BankConfig
    let momo: BankConfig
    let messaging: BankConfig
    let vietcombank: BankConfig
    let techcombank: BankConfig
    let zalopay: BankConfig
    let shopeepay: BankConfig
    
    var keys: [String] {
        return ["agribank", "momo", "messaging", "vietcombank", "techcombank", "zalopay", "shopeepay"]
    }
    
    subscript(key: String) -> BankConfig? {
        switch key {
        case "agribank": return agribank
        case "momo": return momo
        case "messaging": return messaging
        case "vietcombank": return vietcombank
        case "techcombank": return techcombank
        case "zalopay": return zalopay
        case "shopeepay": return shopeepay
        default: return nil
        }
    }
}

struct BankConfig: Codable {
    let receiveKeyword: [String]
    let mRegex: [String]
    
    enum CodingKeys: String, CodingKey {
        case receiveKeyword = "receive_keyword"
        case mRegex = "m_regex"
    }
}

// MARK: - Notification Data Model

struct NotificationData: Identifiable, Codable {
    let id = UUID()
    let packageName: String
    let title: String
    let content: String
    let amount: String
    let timestamp: Date
    let formattedDate: String
    
    init(packageName: String, title: String, content: String, amount: String = "0") {
        self.packageName = packageName
        self.title = title
        self.content = content
        self.amount = amount
        self.timestamp = Date()
        
        let formatter = DateFormatter()
        formatter.dateFormat = "yyyy-MM-dd HH:mm:ss"
        self.formattedDate = formatter.string(from: timestamp)
    }
}

// MARK: - User Preferences

struct UserPreferences: Codable {
    var saveEnabled: Bool = true
    var filterEnabled: Bool = true
    var speechPrefix: String = "đã nhận"
    var speechCurrency: String = "đồng"
    var serviceState: Bool = false
    
    static let shared = UserPreferences()
    
    private init() {}
}

// MARK: - Notification Service Constants

struct NotificationConstants {
    static let notificationReceivedAction = "com.example.talkingbill.NOTIFICATION_RECEIVED"
    static let notificationDataKey = "notification_data"
    static let notificationChannelId = "talking_bill_channel"
    static let notificationId = 1
    static let prefsName = "NotificationPrefs"
    
    // UserDefaults keys
    static let keySaveEnabled = "save_enabled"
    static let keyFilterEnabled = "filter_enabled"
    static let keyServiceState = "service_state"
    static let keySpeechPrefix = "speech_prefix"
    static let keySpeechCurrency = "speech_currency"
    static let keyBackgroundEnabled = "background_enabled"
}
