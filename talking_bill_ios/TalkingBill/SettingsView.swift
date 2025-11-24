import SwiftUI

struct SettingsView: View {
    @EnvironmentObject var notificationManager: NotificationManager
    @EnvironmentObject var speechManager: SpeechManager
    @EnvironmentObject var dataManager: DataManager
    
    @Environment(\.dismiss) private var dismiss
    
    @State private var speechPrefix: String = ""
    @State private var speechCurrency: String = ""
    @State private var showingTestAlert = false
    @State private var showingExportAlert = false
    @State private var showingImportAlert = false
    
    var body: some View {
        NavigationView {
            Form {
                // Speech Settings Section
                Section("Speech Settings") {
                    VStack(alignment: .leading, spacing: 12) {
                        HStack {
                            Text("Speech Prefix")
                                .font(.subheadline)
                            
                            Spacer()
                            
                            TextField("e.g., đã nhận", text: $speechPrefix)
                                .textFieldStyle(RoundedBorderTextFieldStyle())
                                .frame(width: 150)
                        }
                        
                        HStack {
                            Text("Currency")
                                .font(.subheadline)
                            
                            Spacer()
                            
                            TextField("e.g., đồng", text: $speechCurrency)
                                .textFieldStyle(RoundedBorderTextFieldStyle())
                                .frame(width: 150)
                        }
                        
                        Button("Save Speech Settings") {
                            saveSpeechSettings()
                        }
                        .buttonStyle(.borderedProminent)
                        .frame(maxWidth: .infinity)
                    }
                    .padding(.vertical, 8)
                }
                
                // App Settings Section
                Section("App Settings") {
                    Toggle("Save Notifications", isOn: $dataManager.saveEnabled)
                        .onChange(of: dataManager.saveEnabled) { _ in
                            dataManager.saveUserPreferences()
                        }
                    
                    Toggle("Filter & Voice", isOn: $notificationManager.filterEnabled)
                        .onChange(of: notificationManager.filterEnabled) { _ in
                            notificationManager.saveUserPreferences()
                        }
                }
                
                // Testing Section
                Section("Testing") {
                    Button("Send Test Notification") {
                        NotificationService.shared.sendTestNotification()
                        showingTestAlert = true
                    }
                    .foregroundColor(.blue)
                    
                    Button("Send Multiple Test Notifications") {
                        NotificationService.shared.sendMultipleTestNotifications()
                        showingTestAlert = true
                    }
                    .foregroundColor(.green)
                }
                
                // Data Management Section
                Section("Data Management") {
                    HStack {
                        Button("Export Notifications") {
                            exportNotifications()
                        }
                        .foregroundColor(.green)
                        
                        Spacer()
                        
                        Button("Import Notifications") {
                            showingImportAlert = true
                        }
                        .foregroundColor(.blue)
                    }
                    
                    Button("Clear All Data") {
                        clearAllData()
                    }
                    .foregroundColor(.red)
                }
                
                // App Information Section
                Section("App Information") {
                    HStack {
                        Text("Version")
                        Spacer()
                        Text("1.0.0")
                            .foregroundColor(.secondary)
                    }
                    
                    HStack {
                        Text("Notifications Count")
                        Spacer()
                        Text("\(dataManager.notifications.count)")
                            .foregroundColor(.secondary)
                    }
                    
                    HStack {
                        Text("Configuration Status")
                        Spacer()
                        Text(notificationManager.appConfig != nil ? "Loaded" : "Error")
                            .foregroundColor(notificationManager.appConfig != nil ? .green : .red)
                    }
                }
            }
            .navigationTitle("Settings")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .navigationBarTrailing) {
                    Button("Done") {
                        dismiss()
                    }
                }
            }
            .onAppear {
                loadCurrentSettings()
            }
            .alert("Test Notification Sent", isPresented: $showingTestAlert) {
                Button("OK") { }
            } message: {
                Text("A test notification has been sent. Check if it's processed correctly.")
            }
            .alert("Export Successful", isPresented: $showingExportAlert) {
                Button("OK") { }
            } message: {
                Text("Notifications have been exported to the Documents folder.")
            }
            .alert("Import Notifications", isPresented: $showingImportAlert) {
                Button("Cancel", role: .cancel) { }
                Button("Import") {
                    importNotifications()
                }
            } message: {
                Text("This will replace all current notifications with imported data. Continue?")
            }
        }
    }
    
    private func loadCurrentSettings() {
        speechPrefix = speechManager.speechPrefix
        speechCurrency = speechManager.speechCurrency
    }
    
    private func saveSpeechSettings() {
        speechManager.speechPrefix = speechPrefix
        speechManager.speechCurrency = speechCurrency
        speechManager.saveUserPreferences()
    }
    
    private func exportNotifications() {
        if let exportURL = dataManager.exportNotifications() {
            print("Notifications exported to: \(exportURL)")
            showingExportAlert = true
        }
    }
    
    private func importNotifications() {
        // In a real app, you would use a document picker
        // For now, we'll just show the alert
        print("Import functionality would be implemented here")
    }
    
    private func clearAllData() {
        Task {
            await dataManager.reset()
            await notificationManager.reset()
            await speechManager.reset()
        }
    }
}

#Preview {
    SettingsView()
        .environmentObject(NotificationManager())
        .environmentObject(SpeechManager())
        .environmentObject(DataManager())
}
