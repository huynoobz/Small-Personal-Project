import SwiftUI

@main
struct TalkingBillApp: App {
    @StateObject private var notificationManager = NotificationManager()
    @StateObject private var speechManager = SpeechManager()
    @StateObject private var dataManager = DataManager()
    
    var body: some Scene {
        WindowGroup {
            ContentView()
                .environmentObject(notificationManager)
                .environmentObject(speechManager)
                .environmentObject(dataManager)
                .onAppear {
                    setupApp()
                }
        }
    }
    
    private func setupApp() {
        // Initialize managers
        notificationManager.setup()
        speechManager.setup()
        dataManager.setup()
        
        // Start notification monitoring if permissions are granted
        Task {
            await notificationManager.requestPermissions()
            
            // Start notification service
            NotificationService.shared.startMonitoring()
        }
    }
}
