import Foundation
import UserNotifications
import UIKit

class NotificationService: NSObject {
    static let shared = NotificationService()
    
    private let notificationManager = NotificationManager()
    private let speechManager = SpeechManager()
    private let dataManager = DataManager()
    
    private override init() {
        super.init()
        setupNotificationCenter()
    }
    
    // MARK: - Setup
    
    private func setupNotificationCenter() {
        UNUserNotificationCenter.current().delegate = self
    }
    
    func startMonitoring() {
        // In iOS, we can't directly monitor other apps' notifications like Android
        // Instead, we'll use local notifications and background app refresh
        // This is a limitation of iOS security model
        
        scheduleBackgroundRefresh()
        setupNotificationObservers()
    }
    
    private func setupNotificationObservers() {
        // Listen for app state changes to detect when notifications might arrive
        NotificationCenter.default.addObserver(
            self,
            selector: #selector(appDidBecomeActive),
            name: UIApplication.didBecomeActiveNotification,
            object: nil
        )
        
        NotificationCenter.default.addObserver(
            self,
            selector: #selector(appWillEnterForeground),
            name: UIApplication.willEnterForegroundNotification,
            object: nil
        )
    }
    
    @objc private func appDidBecomeActive() {
        // Check for any pending notifications when app becomes active
        checkForPendingNotifications()
    }
    
    @objc private func appWillEnterForeground() {
        // Check for notifications when app enters foreground
        checkForPendingNotifications()
    }
    
    private func checkForPendingNotifications() {
        UNUserNotificationCenter.current().getDeliveredNotifications { notifications in
            DispatchQueue.main.async {
                for notification in notifications {
                    self.processIncomingNotification(notification)
                }
            }
        }
    }
    
    private func scheduleBackgroundRefresh() {
        let request = UNNotificationRequest(
            identifier: "background_refresh",
            content: createBackgroundRefreshContent(),
            trigger: UNTimeIntervalNotificationTrigger(timeInterval: 300, repeats: true) // 5 minutes
        )
        
        UNUserNotificationCenter.current().add(request) { error in
            if let error = error {
                print("Error scheduling background refresh: \(error)")
            }
        }
    }
    
    private func createBackgroundRefreshContent() -> UNMutableNotificationContent {
        let content = UNMutableNotificationContent()
        content.title = "Talking Bill"
        content.body = "Service is running in background"
        content.sound = .none
        content.badge = 1
        return content
    }
    
    // MARK: - Notification Processing
    
    func processIncomingNotification(_ notification: UNNotification) {
        Task { @MainActor in
            let (shouldProcess, amount) = notificationManager.shouldProcessNotification(notification)
            
            if shouldProcess {
                let notificationData = createNotificationData(from: notification, amount: amount)
                
                // Add to data manager
                dataManager.addNotification(notificationData)
                
                // Speak the amount if filtering is enabled and amount is not 0
                if notificationManager.filterEnabled && !amount.isEmpty && amount != "0" {
                    speechManager.speakAmount(amount)
                }
            }
        }
    }
    
    private func createNotificationData(from notification: UNNotification, amount: String) -> NotificationData {
        let content = notification.request.content
        let packageName = content.userInfo["packageName"] as? String ?? Bundle.main.bundleIdentifier ?? "Unknown"
        
        return NotificationData(
            packageName: packageName,
            title: content.title,
            content: content.body,
            amount: amount
        )
    }
    
    // MARK: - Manual Notification Testing
    
    func sendTestNotification() {
        let content = UNMutableNotificationContent()
        content.title = "Test Notification"
        content.body = "MoMo: Đã nhận 50,000đ từ Nguyễn Văn A"
        content.sound = .default
        content.categoryIdentifier = "BANKING_TRANSACTION"
        content.userInfo = [
            "packageName": "com.mservice.momotransfer",
            "app": "MoMo",
            "type": "banking"
        ]
        
        let request = UNNotificationRequest(
            identifier: "test_notification_\(Date().timeIntervalSince1970)",
            content: content,
            trigger: UNTimeIntervalNotificationTrigger(timeInterval: 1, repeats: false)
        )
        
        UNUserNotificationCenter.current().add(request) { error in
            if let error = error {
                print("Error sending test notification: \(error)")
            }
        }
    }
    
    func sendMultipleTestNotifications() {
        let testNotifications = [
            ("Agribank", "Agribank: +1,500,000 VND từ chuyển khoản"),
            ("MoMo", "MoMo: Đã nhận 250,000đ từ Nguyễn Văn B"),
            ("ZaloPay", "ZaloPay: Nhận được 75,000đ từ giao dịch"),
            ("Vietcombank", "Vietcombank: Số dư tài khoản: 5,000,000 VND"),
            ("Techcombank", "Techcombank: Giao dịch ATM: -200,000 VND")
        ]
        
        for (index, (app, message)) in testNotifications.enumerated() {
            let content = UNMutableNotificationContent()
            content.title = "Test \(app)"
            content.body = message
            content.sound = .default
            content.categoryIdentifier = "BANKING_TRANSACTION"
            content.userInfo = [
                "packageName": "com.test.\(app.lowercased())",
                "app": app,
                "type": "banking"
            ]
            
            let request = UNNotificationRequest(
                identifier: "test_\(app.lowercased())_\(Date().timeIntervalSince1970)",
                content: content,
                trigger: UNTimeIntervalNotificationTrigger(timeInterval: TimeInterval(index + 1), repeats: false)
            )
            
            UNUserNotificationCenter.current().add(request) { error in
                if let error = error {
                    print("Error sending test notification for \(app): \(error)")
                }
            }
        }
    }
}

// MARK: - UNUserNotificationCenterDelegate

extension NotificationService: UNUserNotificationCenterDelegate {
    func userNotificationCenter(
        _ center: UNUserNotificationCenter,
        willPresent notification: UNNotification,
        withCompletionHandler completionHandler: @escaping (UNNotificationPresentationOptions) -> Void
    ) {
        // Process the notification immediately when it arrives
        processIncomingNotification(notification)
        
        // Show the notification with all options
        completionHandler([.banner, .sound, .badge, .list])
    }
    
    func userNotificationCenter(
        _ center: UNUserNotificationCenter,
        didReceive response: UNNotificationResponse,
        withCompletionHandler completionHandler: @escaping () -> Void
    ) {
        // Handle notification tap or interaction
        let notification = response.notification
        processIncomingNotification(notification)
        
        // Log the interaction type
        print("Notification interaction: \(response.actionIdentifier)")
        
        completionHandler()
    }
    
    func userNotificationCenter(
        _ center: UNUserNotificationCenter,
        openSettingsFor notification: UNNotification?
    ) {
        // Handle when user opens notification settings
        print("User opened notification settings")
    }
}

// MARK: - Background App Refresh

extension NotificationService {
    func handleBackgroundAppRefresh() {
        // This would be called when the app is refreshed in background
        // In a real implementation, you might check for new notifications
        // or sync with a server
        
        print("Background app refresh triggered")
    }
}
