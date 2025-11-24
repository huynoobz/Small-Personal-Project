import Foundation
import UserNotifications
import Combine

@MainActor
class NotificationManager: NSObject, ObservableObject {
    @Published var isAuthorized = false
    @Published var filterEnabled = true
    @Published var appConfig: AppConfig?
    
    private let userDefaults = UserDefaults.standard
    private var cancellables = Set<AnyCancellable>()
    
    override init() {
        super.init()
        loadUserPreferences()
        loadConfiguration()
    }
    
    func setup() {
        checkAuthorizationStatus()
    }
    
    // MARK: - Permission Management
    
    func requestPermissions() async {
        do {
            let granted = try await UNUserNotificationCenter.current().requestAuthorization(
                options: [.alert, .sound, .badge, .provisional]
            )
            
            await MainActor.run {
                self.isAuthorized = granted
                self.saveUserPreferences()
            }
            
            if granted {
                await registerForRemoteNotifications()
                await setupNotificationCategories()
            }
        } catch {
            print("Error requesting notification permissions: \(error)")
        }
    }
    
    private func registerForRemoteNotifications() async {
        await UIApplication.shared.registerForRemoteNotifications()
    }
    
    private func setupNotificationCategories() async {
        // Create notification categories for better handling
        let bankingCategory = UNNotificationCategory(
            identifier: "BANKING_TRANSACTION",
            actions: [],
            intentIdentifiers: [],
            options: [.customDismissAction]
        )
        
        let generalCategory = UNNotificationCategory(
            identifier: "GENERAL_NOTIFICATION",
            actions: [],
            intentIdentifiers: [],
            options: [.customDismissAction]
        )
        
        UNUserNotificationCenter.current().setNotificationCategories([
            bankingCategory,
            generalCategory
        ])
    }
    
    private func checkAuthorizationStatus() {
        UNUserNotificationCenter.current().getNotificationSettings { settings in
            DispatchQueue.main.async {
                self.isAuthorized = settings.authorizationStatus == .authorized
            }
        }
    }
    
    func openNotificationSettings() {
        if let settingsUrl = URL(string: UIApplication.openSettingsURLString) {
            UIApplication.shared.open(settingsUrl)
        }
    }
    
    // MARK: - Configuration Management
    
    func loadConfiguration() {
        guard let configURL = Bundle.main.url(forResource: "app_config", withExtension: "json") else {
            print("Could not find app_config.json")
            return
        }
        
        do {
            let data = try Data(contentsOf: configURL)
            let config = try JSONDecoder().decode(AppConfig.self, from: data)
            
            DispatchQueue.main.async {
                self.appConfig = config
            }
        } catch {
            print("Error loading configuration: \(error)")
        }
    }
    
    // MARK: - Notification Processing
    
    func shouldProcessNotification(_ notification: UNNotification) -> (shouldProcess: Bool, amount: String) {
        let request = notification.request
        let content = request.content
        
        // Always collect notification data for analysis
        let fullContent = buildNotificationContent(content)
        
        // Extract package name from userInfo or use bundle identifier
        let packageName = content.userInfo["packageName"] as? String ?? 
                         content.userInfo["app"] as? String ?? 
                         Bundle.main.bundleIdentifier ?? "Unknown"
        
        // If filtering is disabled, collect all notifications
        guard filterEnabled else {
            return (true, "0")
        }
        
        // If no config available, still collect but with amount 0
        guard let config = appConfig else {
            return (true, "0")
        }
        
        return processNotificationContent(packageName: packageName, content: fullContent, config: config)
    }
    
    private func buildNotificationContent(_ content: UNNotificationContent) -> String {
        var fullContent = ""
        
        if !content.title.isEmpty {
            fullContent += "Title: \(content.title)\n"
        }
        
        if !content.body.isEmpty {
            fullContent += "Text: \(content.body)\n"
        }
        
        if !content.subtitle.isEmpty {
            fullContent += "SubText: \(content.subtitle)\n"
        }
        
        // Add category information
        if !content.categoryIdentifier.isEmpty {
            fullContent += "Category: \(content.categoryIdentifier)\n"
        }
        
        // Add thread identifier for grouped notifications
        if !content.threadIdentifier.isEmpty {
            fullContent += "Thread: \(content.threadIdentifier)\n"
        }
        
        // Add userInfo with more details
        fullContent += "Additional Info:\n"
        for (key, value) in content.userInfo {
            fullContent += "\(key): \(value)\n"
        }
        
        return fullContent
    }
    
    private func processNotificationContent(packageName: String, content: String, config: AppConfig) -> (shouldProcess: Bool, amount: String) {
        let lowerPackageName = packageName.lowercased()
        let lowerContent = normalizeContent(content).lowercased()
        
        // Stage 1: Check if package name contains any app name from config
        var matchingApp: (String, BankConfig)?
        
        for (appName, appConfig) in [
            ("agribank", config.agribank),
            ("momo", config.momo),
            ("messaging", config.messaging),
            ("vietcombank", config.vietcombank),
            ("techcombank", config.techcombank),
            ("zalopay", config.zalopay),
            ("shopeepay", config.shopeepay)
        ] {
            if lowerPackageName.contains(appName.lowercased()) {
                matchingApp = (appName, appConfig)
                break
            }
        }
        
        // If no specific app match, try to detect banking-related content
        if matchingApp == nil {
            let bankingKeywords = ["bank", "ngân hàng", "atm", "chuyển tiền", "nhận tiền", "gửi tiền", "rút tiền", "số dư", "balance", "transaction", "giao dịch"]
            let hasBankingKeyword = bankingKeywords.contains { keyword in
                lowerContent.contains(keyword.lowercased())
            }
            
            if hasBankingKeyword {
                // Use generic banking detection
                return detectGenericBankingTransaction(content: lowerContent)
            }
        }
        
        guard let (appName, bankConfig) = matchingApp else {
            return (true, "0") // Still collect but with amount 0
        }
        
        // Stage 2: Check if notification content contains any of the app's keywords
        let hasKeyword = bankConfig.receiveKeyword.contains { keyword in
            lowerContent.contains(keyword.lowercased())
        }
        
        guard hasKeyword else {
            return (true, "0") // Still collect but with amount 0
        }
        
        // Stage 3: Check money regex pattern
        let mRegexList = bankConfig.mRegex
        
        if !mRegexList.isEmpty {
            for mRegex in mRegexList {
                if let amount = extractMoneyAmount(from: lowerContent, using: mRegex) {
                    return (true, amount)
                }
            }
        }
        
        // Try generic money detection if specific regex fails
        return detectGenericBankingTransaction(content: lowerContent)
    }
    
    private func detectGenericBankingTransaction(content: String) -> (shouldProcess: Bool, amount: String) {
        // Generic money patterns for Vietnamese banking
        let genericPatterns = [
            "\\d{1,3}(?:[.,]\\d{3})*(?:[.,]\\d{2})?\\s*(?:đ|vnd|₫)",
            "\\d{1,3}(?:[.,]\\d{3})*(?:[.,]\\d{2})?\\s*(?:đồng|dong)",
            "\\+\\s*\\d{1,3}(?:[.,]\\d{3})*(?:[.,]\\d{2})?",
            "\\d{1,3}(?:[.,]\\d{3})*(?:[.,]\\d{2})?\\s*(?:k|nghìn|triệu|tỷ)"
        ]
        
        for pattern in genericPatterns {
            if let amount = extractMoneyAmount(from: content, using: pattern) {
                return (true, amount)
            }
        }
        
        return (true, "0")
    }
    
    private func normalizeContent(_ content: String) -> String {
        return content.replacingOccurrences(of: "\u{00A0}", with: " ")
    }
    
    private func extractMoneyAmount(from content: String, using pattern: String) -> String? {
        let mRegex = pattern.lowercased()
        
        // Extract prefix, separator and postfix from m_regex
        let prefix = mRegex.hasPrefix("0") ? "" : String(mRegex.prefix(while: { $0 != "0" }))
        let postfix = mRegex.hasSuffix("0") ? "" : String(mRegex.suffix(while: { $0 != "0" }))
        let separator = String(mRegex.dropFirst(prefix.count).dropLast(postfix.count))
        
        // Create regex pattern that matches numbers with the given format
        let numberPattern = "\\d+(?:\(separator)\\d+)*"
        let fullPattern = prefix.isEmpty && postfix.isEmpty ? numberPattern : "\(prefix)\(numberPattern)\(postfix)"
        
        do {
            let regex = try NSRegularExpression(pattern: fullPattern, options: [])
            let range = NSRange(location: 0, length: content.utf16.count)
            
            if let match = regex.firstMatch(in: content, options: [], range: range) {
                let matchedText = String(content[Range(match.range, in: content)!])
                
                // Extract money amount as integer
                let cleanedText = matchedText.replacingOccurrences(of: "[^0-9.,]", with: "", options: .regularExpression)
                let moneyAmount = cleanedText.replacingOccurrences(of: "[,.]", with: "", options: .regularExpression)
                
                if let amount = Int(moneyAmount), amount > 0 {
                    return String(amount)
                }
            }
        } catch {
            print("Error creating regex: \(error)")
        }
        
        return nil
    }
    
    // MARK: - User Preferences
    
    private func loadUserPreferences() {
        filterEnabled = userDefaults.bool(forKey: NotificationConstants.keyFilterEnabled)
        if !userDefaults.object(forKey: NotificationConstants.keyFilterEnabled) != nil {
            filterEnabled = true // Default value
        }
    }
    
    private func saveUserPreferences() {
        userDefaults.set(filterEnabled, forKey: NotificationConstants.keyFilterEnabled)
    }
    
    func reset() async {
        userDefaults.removeObject(forKey: NotificationConstants.keyFilterEnabled)
        userDefaults.removeObject(forKey: NotificationConstants.keySaveEnabled)
        userDefaults.removeObject(forKey: NotificationConstants.keyServiceState)
        userDefaults.removeObject(forKey: NotificationConstants.keySpeechPrefix)
        userDefaults.removeObject(forKey: NotificationConstants.keySpeechCurrency)
        userDefaults.removeObject(forKey: NotificationConstants.keyBackgroundEnabled)
        
        await MainActor.run {
            self.filterEnabled = true
            self.loadUserPreferences()
        }
    }
}
