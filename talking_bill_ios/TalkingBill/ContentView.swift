import SwiftUI

struct ContentView: View {
    @EnvironmentObject var notificationManager: NotificationManager
    @EnvironmentObject var speechManager: SpeechManager
    @EnvironmentObject var dataManager: DataManager
    @StateObject private var notificationCollector = NotificationCollector()
    
    @State private var showingSettings = false
    @State private var showingClearAlert = false
    @State private var showingResetAlert = false
    @State private var isLoading = false
    
    var body: some View {
        NavigationView {
            VStack(spacing: 20) {
                // Header
                headerView
                
                // Status Section
                statusSection
                
                // Configuration Section
                configurationSection
                
                // Controls Section
                controlsSection
                
                // Notifications List
                notificationsList
                
                Spacer()
            }
            .padding()
            .navigationTitle("Talking Bill")
            .navigationBarTitleDisplayMode(.large)
            .toolbar {
                ToolbarItem(placement: .navigationBarTrailing) {
                    Button("Settings") {
                        showingSettings = true
                    }
                }
            }
            .sheet(isPresented: $showingSettings) {
                SettingsView()
                    .environmentObject(notificationManager)
                    .environmentObject(speechManager)
                    .environmentObject(dataManager)
            }
            .alert("Clear All Notifications", isPresented: $showingClearAlert) {
                Button("Cancel", role: .cancel) { }
                Button("Clear", role: .destructive) {
                    dataManager.clearAllNotifications()
                }
            } message: {
                Text("Are you sure you want to delete all notifications?")
            }
            .alert("Reset App", isPresented: $showingResetAlert) {
                Button("Cancel", role: .cancel) { }
                Button("Reset", role: .destructive) {
                    resetApp()
                }
            } message: {
                Text("This will reset all settings and clear all data. Are you sure?")
            }
            .overlay {
                if isLoading {
                    loadingOverlay
                }
            }
        }
    }
    
    private var headerView: some View {
        VStack(spacing: 8) {
            Image(systemName: "speaker.wave.3.fill")
                .font(.system(size: 50))
                .foregroundColor(.blue)
            
            Text("Talking Bill")
                .font(.title)
                .fontWeight(.bold)
            
            Text("Monitor banking notifications with voice announcements")
                .font(.caption)
                .foregroundColor(.secondary)
                .multilineTextAlignment(.center)
        }
    }
    
    private var statusSection: some View {
        VStack(spacing: 12) {
            HStack {
                Image(systemName: notificationManager.isAuthorized ? "checkmark.circle.fill" : "exclamationmark.triangle.fill")
                    .foregroundColor(notificationManager.isAuthorized ? .green : .orange)
                
                Text(notificationManager.isAuthorized ? "Notification access enabled" : "Please enable notification access")
                    .font(.headline)
                
                Spacer()
            }
            
            if !notificationManager.isAuthorized {
                Button("Enable Notification Access") {
                    notificationManager.openNotificationSettings()
                }
                .buttonStyle(.borderedProminent)
            }
        }
        .padding()
        .background(Color(.systemGray6))
        .cornerRadius(12)
    }
    
    private var configurationSection: some View {
        VStack(alignment: .leading, spacing: 12) {
            Text("Configuration")
                .font(.headline)
            
            VStack(alignment: .leading, spacing: 8) {
                Text("Loaded Apps and Keywords:")
                    .font(.subheadline)
                    .fontWeight(.medium)
                
                ScrollView {
                    LazyVStack(alignment: .leading, spacing: 4) {
                        ForEach(notificationManager.appConfig?.keys.sorted() ?? [], id: \.self) { appName in
                            if let config = notificationManager.appConfig?[appName] {
                                VStack(alignment: .leading, spacing: 2) {
                                    Text(appName.capitalized)
                                        .font(.subheadline)
                                        .fontWeight(.semibold)
                                    
                                    Text("Keywords: \(config.receiveKeyword.joined(separator: ", "))")
                                        .font(.caption)
                                        .foregroundColor(.secondary)
                                    
                                    Text("Money Regex: \(config.mRegex.joined(separator: ", "))")
                                        .font(.caption)
                                        .foregroundColor(.secondary)
                                }
                                .padding(.vertical, 2)
                            }
                        }
                    }
                }
                .frame(maxHeight: 150)
            }
        }
        .padding()
        .background(Color(.systemGray6))
        .cornerRadius(12)
    }
    
    private var controlsSection: some View {
        VStack(spacing: 16) {
            // Toggle Controls
            VStack(spacing: 12) {
                Toggle("Save Notifications", isOn: $dataManager.saveEnabled)
                    .toggleStyle(SwitchToggleStyle())
                
                Toggle("Filter & Voice", isOn: $notificationManager.filterEnabled)
                    .toggleStyle(SwitchToggleStyle())
            }
            
                            // Action Buttons
                HStack(spacing: 16) {
                    Button("Clear All") {
                        showingClearAlert = true
                    }
                    .buttonStyle(.bordered)
                    .disabled(dataManager.notifications.isEmpty)
                    
                    Button("Reset App") {
                        showingResetAlert = true
                    }
                    .buttonStyle(.bordered)
                    .foregroundColor(.red)
                }
                
                // Collection Controls
                HStack(spacing: 16) {
                    Button(notificationCollector.isCollecting ? "Stop Collecting" : "Start Collecting") {
                        if notificationCollector.isCollecting {
                            notificationCollector.stopCollecting()
                        } else {
                            notificationCollector.startCollecting()
                        }
                    }
                    .buttonStyle(.borderedProminent)
                    .foregroundColor(notificationCollector.isCollecting ? .red : .green)
                    
                    Button("Simulate Collection") {
                        notificationCollector.simulateNotificationCollection()
                    }
                    .buttonStyle(.bordered)
                    .foregroundColor(.blue)
                }
        }
        .padding()
        .background(Color(.systemGray6))
        .cornerRadius(12)
    }
    
    private var notificationsList: some View {
        VStack(alignment: .leading, spacing: 12) {
            HStack {
                Text("Notifications")
                    .font(.headline)
                
                Spacer()
                
                Text("\(dataManager.notifications.count) items")
                    .font(.caption)
                    .foregroundColor(.secondary)
            }
            
            if dataManager.notifications.isEmpty {
                VStack(spacing: 8) {
                    Image(systemName: "bell.slash")
                        .font(.system(size: 40))
                        .foregroundColor(.secondary)
                    
                    Text("No notifications yet")
                        .font(.subheadline)
                        .foregroundColor(.secondary)
                }
                .frame(maxWidth: .infinity)
                .padding(.vertical, 40)
            } else {
                ScrollView {
                    LazyVStack(spacing: 8) {
                        ForEach(Array(dataManager.notifications.enumerated()), id: \.offset) { index, notification in
                            NotificationRowView(notification: notification) {
                                dataManager.deleteNotification(at: index)
                            }
                        }
                    }
                }
                .frame(maxHeight: 300)
            }
        }
        .padding()
        .background(Color(.systemGray6))
        .cornerRadius(12)
    }
    
    private var loadingOverlay: some View {
        ZStack {
            Color.black.opacity(0.3)
                .ignoresSafeArea()
            
            VStack(spacing: 16) {
                ProgressView()
                    .scaleEffect(1.5)
                
                Text("Loading...")
                    .font(.headline)
            }
            .padding(30)
            .background(Color(.systemBackground))
            .cornerRadius(16)
        }
    }
    
    private func resetApp() {
        isLoading = true
        
        Task {
            // Reset all managers
            await notificationManager.reset()
            await speechManager.reset()
            await dataManager.reset()
            
            // Reload configuration
            await notificationManager.loadConfiguration()
            
            await MainActor.run {
                isLoading = false
            }
        }
    }
}

struct NotificationRowView: View {
    let notification: NotificationData
    let onDelete: () -> Void
    
    @State private var showingDeleteAlert = false
    
    var body: some View {
        VStack(alignment: .leading, spacing: 8) {
            HStack {
                VStack(alignment: .leading, spacing: 4) {
                    Text(notification.packageName)
                        .font(.caption)
                        .fontWeight(.semibold)
                        .foregroundColor(.blue)
                    
                    Text(notification.formattedDate)
                        .font(.caption2)
                        .foregroundColor(.secondary)
                }
                
                Spacer()
                
                if !notification.amount.isEmpty && notification.amount != "0" {
                    Text("\(notification.amount) VND")
                        .font(.subheadline)
                        .fontWeight(.bold)
                        .foregroundColor(.green)
                }
                
                Button(action: {
                    showingDeleteAlert = true
                }) {
                    Image(systemName: "trash")
                        .foregroundColor(.red)
                }
                .buttonStyle(PlainButtonStyle())
            }
            
            Text(notification.content)
                .font(.caption)
                .foregroundColor(.primary)
                .lineLimit(3)
        }
        .padding()
        .background(Color(.systemBackground))
        .cornerRadius(8)
        .shadow(radius: 1)
        .alert("Delete Notification", isPresented: $showingDeleteAlert) {
            Button("Cancel", role: .cancel) { }
            Button("Delete", role: .destructive) {
                onDelete()
            }
        } message: {
            Text("Are you sure you want to delete this notification?")
        }
    }
}

#Preview {
    ContentView()
        .environmentObject(NotificationManager())
        .environmentObject(SpeechManager())
        .environmentObject(DataManager())
}
