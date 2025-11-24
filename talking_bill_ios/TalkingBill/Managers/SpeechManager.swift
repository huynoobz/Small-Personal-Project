import Foundation
import AVFoundation
import Combine

@MainActor
class SpeechManager: NSObject, ObservableObject {
    @Published var isSpeaking = false
    @Published var speechPrefix = "đã nhận"
    @Published var speechCurrency = "đồng"
    
    private let synthesizer = AVSpeechSynthesizer()
    private let userDefaults = UserDefaults.standard
    
    override init() {
        super.init()
        synthesizer.delegate = self
        loadUserPreferences()
    }
    
    func setup() {
        configureAudioSession()
    }
    
    // MARK: - Audio Configuration
    
    private func configureAudioSession() {
        do {
            let audioSession = AVAudioSession.sharedInstance()
            try audioSession.setCategory(.playback, mode: .default, options: [.mixWithOthers])
            try audioSession.setActive(true)
        } catch {
            print("Error configuring audio session: \(error)")
        }
    }
    
    // MARK: - Speech Functions
    
    func speakAmount(_ amount: String) {
        guard !amount.isEmpty && amount != "0" else { return }
        
        let text = "\(speechPrefix), \(amount), \(speechCurrency)"
        speak(text)
    }
    
    func speak(_ text: String) {
        // Stop any current speech
        if synthesizer.isSpeaking {
            synthesizer.stopSpeaking(at: .immediate)
        }
        
        let utterance = AVSpeechUtterance(string: text)
        
        // Configure speech settings
        utterance.rate = 0.4 // Slower speech rate (0.0 to 1.0)
        utterance.pitchMultiplier = 1.0
        utterance.volume = 1.0
        
        // Try to use Vietnamese voice
        if let vietnameseVoice = AVSpeechSynthesisVoice(language: "vi-VN") {
            utterance.voice = vietnameseVoice
        } else if let vietnameseVoice = AVSpeechSynthesisVoice(language: "vi") {
            utterance.voice = vietnameseVoice
        } else {
            // Fallback to default voice
            utterance.voice = AVSpeechSynthesisVoice(language: "en-US")
        }
        
        synthesizer.speak(utterance)
    }
    
    func stopSpeaking() {
        if synthesizer.isSpeaking {
            synthesizer.stopSpeaking(at: .immediate)
        }
    }
    
    // MARK: - User Preferences
    
    private func loadUserPreferences() {
        speechPrefix = userDefaults.string(forKey: NotificationConstants.keySpeechPrefix) ?? "đã nhận"
        speechCurrency = userDefaults.string(forKey: NotificationConstants.keySpeechCurrency) ?? "đồng"
    }
    
    func saveUserPreferences() {
        userDefaults.set(speechPrefix, forKey: NotificationConstants.keySpeechPrefix)
        userDefaults.set(speechCurrency, forKey: NotificationConstants.keySpeechCurrency)
    }
    
    func reset() async {
        userDefaults.removeObject(forKey: NotificationConstants.keySpeechPrefix)
        userDefaults.removeObject(forKey: NotificationConstants.keySpeechCurrency)
        
        await MainActor.run {
            self.speechPrefix = "đã nhận"
            self.speechCurrency = "đồng"
            self.loadUserPreferences()
        }
    }
}

// MARK: - AVSpeechSynthesizerDelegate

extension SpeechManager: AVSpeechSynthesizerDelegate {
    func speechSynthesizer(_ synthesizer: AVSpeechSynthesizer, didStart utterance: AVSpeechUtterance) {
        isSpeaking = true
    }
    
    func speechSynthesizer(_ synthesizer: AVSpeechSynthesizer, didFinish utterance: AVSpeechUtterance) {
        isSpeaking = false
    }
    
    func speechSynthesizer(_ synthesizer: AVSpeechSynthesizer, didCancel utterance: AVSpeechUtterance) {
        isSpeaking = false
    }
}
