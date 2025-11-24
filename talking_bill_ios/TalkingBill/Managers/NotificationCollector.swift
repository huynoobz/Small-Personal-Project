import Foundation
import UserNotifications
import UIKit

/// Enhanced notification collector that attempts to capture all possible notifications
@MainActor
class NotificationCollector: ObservableObject {
    @Published var collectedNotifications: [NotificationData] = []
    @Published var isCollecting = false
    
    private let notificationManager = NotificationManager()
    private let dataManager = DataManager()
    private var collectionTimer: Timer?
    
    init() {
        setupNotificationObservers()
    }
    
    // MARK: - Setup
    
    private func setupNotificationObservers() {
        // Listen for app lifecycle events
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
        
        NotificationCenter.default.addObserver(
            self,
            selector: #selector(appDidEnterBackground),
            name: UIApplication.didEnterBackgroundNotification,
            object: nil
        )
    }
    
    // MARK: - Collection Methods
    
    func startCollecting() {
        isCollecting = true
        
        // Start periodic collection
        collectionTimer = Timer.scheduledTimer(withTimeInterval: 5.0, repeats: true) { _ in
            Task { @MainActor in
                await self.collectPendingNotifications()
            }
        }
        
        // Initial collection
        Task {
            await collectPendingNotifications()
        }
    }
    
    func stopCollecting() {
        isCollecting = false
        collectionTimer?.invalidate()
        collectionTimer = nil
    }
    
    @objc private func appDidBecomeActive() {
        if isCollecting {
            Task {
                await collectPendingNotifications()
            }
        }
    }
    
    @objc private func appWillEnterForeground() {
        if isCollecting {
            Task {
                await collectPendingNotifications()
            }
        }
    }
    
    @objc private func appDidEnterBackground() {
        // Continue collecting in background if possible
        if isCollecting {
            Task {
                await collectPendingNotifications()
            }
        }
    }
    
    // MARK: - Notification Collection
    
    private func collectPendingNotifications() async {
        // Get all delivered notifications
        let deliveredNotifications = await getDeliveredNotifications()
        
        // Get notification history (if available)
        let notificationHistory = await getNotificationHistory()
        
        // Process all collected notifications
        let allNotifications = deliveredNotifications + notificationHistory
        
        for notification in allNotifications {
            await processNotification(notification)
        }
    }
    
    private func getDeliveredNotifications() async -> [UNNotification] {
        return await withCheckedContinuation { continuation in
            UNUserNotificationCenter.current().getDeliveredNotifications { notifications in
                continuation.resume(returning: notifications)
            }
        }
    }
    
    private func getNotificationHistory() async -> [UNNotification] {
        // Note: iOS doesn't provide direct access to notification history
        // This is a limitation of the iOS security model
        // We can only work with currently delivered notifications
        return []
    }
    
    private func processNotification(_ notification: UNNotification) async {
        let (shouldProcess, amount) = notificationManager.shouldProcessNotification(notification)
        
        if shouldProcess {
            let notificationData = createNotificationData(from: notification, amount: amount)
            
            // Check if we already have this notification
            if !collectedNotifications.contains(where: { $0.id == notificationData.id }) {
                collectedNotifications.insert(notificationData, at: 0)
                dataManager.addNotification(notificationData)
            }
        }
    }
    
    private func createNotificationData(from notification: UNNotification, amount: String) -> NotificationData {
        let content = notification.request.content
        let packageName = content.userInfo["packageName"] as? String ?? 
                         content.userInfo["app"] as? String ?? 
                         Bundle.main.bundleIdentifier ?? "Unknown"
        
        return NotificationData(
            packageName: packageName,
            title: content.title,
            content: content.body,
            amount: amount
        )
    }
    
    // MARK: - Manual Collection Methods
    
    func simulateNotificationCollection() {
        // Simulate collecting notifications from various sources
        let simulatedNotifications = [
            ("MoMo", "MoMo: Đã nhận 150,000đ từ Nguyễn Văn A"),
            ("Agribank", "Agribank: +2,500,000 VND từ chuyển khoản"),
            ("ZaloPay", "ZaloPay: Nhận được 75,000đ từ giao dịch"),
            ("Vietcombank", "Vietcombank: Số dư tài khoản: 8,000,000 VND"),
            ("Techcombank", "Techcombank: Giao dịch ATM: -300,000 VND"),
            ("ShopeePay", "ShopeePay: Nhận được 200,000đ từ hoàn tiền")
        ]
        
        for (app, message) in simulatedNotifications {
            let notificationData = NotificationData(
                packageName: "com.test.\(app.lowercased())",
                title: app,
                content: message,
                amount: extractAmountFromText(message)
            )
            
            if !collectedNotifications.contains(where: { $0.id == notificationData.id }) {
                collectedNotifications.insert(notificationData, at: 0)
                dataManager.addNotification(notificationData)
            }
        }
    }
    
    private func extractAmountFromText(_ text: String) -> String {
        // Simple amount extraction for simulation
        let patterns = [
            "\\d{1,3}(?:[.,]\\d{3})*(?:[.,]\\d{2})?\\s*đ",
            "\\d{1,3}(?:[.,]\\d{3})*(?:[.,]\\d{2})?\\s*VND",
            "\\+\\s*\\d{1,3}(?:[.,]\\d{3})*(?:[.,]\\d{2})?"
        ]
        
        for pattern in patterns {
            if let range = text.range(of: pattern, options: .regularExpression) {
                let matchedText = String(text[range])
                let cleanedText = matchedText.replacingOccurrences(of: "[^0-9.,]", with: "", options: .regularExpression)
                let amount = cleanedText.replacingOccurrences(of: "[,.]", with: "", options: .regularExpression)
                if let amountInt = Int(amount), amountInt > 0 {
                    return String(amountInt)
                }
            }
        }
        
        return "0"
    }
    
    // MARK: - Cleanup
    
    deinit {
        NotificationCenter.default.removeObserver(self)
        collectionTimer?.invalidate()
    }
}
