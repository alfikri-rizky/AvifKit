import SwiftUI

@main
struct iOSApp: App {

    init() {
        // Explicit registration for the demo app.
        // Consumer apps using SPM get auto-registration via AvifKitAutoRegister.m
        AvifKitSetup.registerNativeHandler()
    }

    var body: some Scene {
        WindowGroup {
            ContentView()
        }
    }
}