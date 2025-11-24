import Foundation
import Combine

@MainActor
class DataManager: ObservableObject {
    @Published var notifications: [NotificationData] = []
    @Published var saveEnabled = true
    
    private let userDefaults = UserDefaults.standard
    private let documentsDirectory = FileManager.default.urls(for: .documentDirectory, in: .userDomainMask).first!
    private let notificationsFileURL: URL
    
    init() {
        notificationsFileURL = documentsDirectory.appendingPathComponent("notification_log.json")
        loadUserPreferences()
        loadNotifications()
    }
    
    func setup() {
        // Additional setup if needed
    }
    
    // MARK: - Notification Management
    
    func addNotification(_ notification: NotificationData) {
        guard saveEnabled else { return }
        
        notifications.insert(notification, at: 0)
        saveNotifications()
    }
    
    func deleteNotification(at index: Int) {
        guard index >= 0 && index < notifications.count else { return }
        
        notifications.remove(at: index)
        saveNotifications()
    }
    
    func clearAllNotifications() {
        notifications.removeAll()
        saveNotifications()
    }
    
    // MARK: - Data Persistence
    
    private func loadNotifications() {
        do {
            let data = try Data(contentsOf: notificationsFileURL)
            let loadedNotifications = try JSONDecoder().decode([NotificationData].self, from: data)
            
            // Sort by timestamp (newest first)
            notifications = loadedNotifications.sorted { $0.timestamp > $1.timestamp }
        } catch {
            print("Error loading notifications: \(error)")
            notifications = []
        }
    }
    
    private func saveNotifications() {
        do {
            let data = try JSONEncoder().encode(notifications)
            try data.write(to: notificationsFileURL)
        } catch {
            print("Error saving notifications: \(error)")
        }
    }
    
    // MARK: - User Preferences
    
    private func loadUserPreferences() {
        saveEnabled = userDefaults.bool(forKey: NotificationConstants.keySaveEnabled)
        if userDefaults.object(forKey: NotificationConstants.keySaveEnabled) == nil {
            saveEnabled = true // Default value
        }
    }
    
    func saveUserPreferences() {
        userDefaults.set(saveEnabled, forKey: NotificationConstants.keySaveEnabled)
    }
    
    func reset() async {
        // Clear all notifications
        await MainActor.run {
            self.notifications.removeAll()
        }
        
        // Remove saved file
        try? FileManager.default.removeItem(at: notificationsFileURL)
        
        // Reset user preferences
        userDefaults.removeObject(forKey: NotificationConstants.keySaveEnabled)
        
        await MainActor.run {
            self.saveEnabled = true
            self.loadUserPreferences()
        }
    }
    
    // MARK: - Export/Import
    
    func exportNotifications() -> URL? {
        do {
            let data = try JSONEncoder().encode(notifications)
            let exportURL = documentsDirectory.appendingPathComponent("talking_bill_export_\(Date().timeIntervalSince1970).json")
            try data.write(to: exportURL)
            return exportURL
        } catch {
            print("Error exporting notifications: \(error)")
            return nil
        }
    }
    
    func importNotifications(from url: URL) -> Bool {
        do {
            let data = try Data(contentsOf: url)
            let importedNotifications = try JSONDecoder().decode([NotificationData].self, from: data)
            
            await MainActor.run {
                self.notifications = importedNotifications.sorted { $0.timestamp > $1.timestamp }
                self.saveNotifications()
            }
            
            return true
        } catch {
            print("Error importing notifications: \(error)")
            return false
        }
    }
}
