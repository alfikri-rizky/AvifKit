import SwiftUI

@main
struct iOSApp: App {

    init() {
        AvifKitSetup.registerNativeHandler()
    }

    var body: some Scene {
        WindowGroup {
            ContentView()
        }
    }
}