import SwiftUI
import ComposeApp

/// Hosts the shared Compose Multiplatform UI. Everything the user sees is drawn by
/// `:composeApp`, which is the same code the Android app runs.
struct ComposeView: UIViewControllerRepresentable {
    func makeUIViewController(context: Context) -> UIViewController {
        MainViewControllerKt.MainViewController()
    }

    func updateUIViewController(_ uiViewController: UIViewController, context: Context) {}
}

@main
struct iOSApp: App {
    var body: some Scene {
        WindowGroup {
            ComposeView()
                // All edges, deliberately. Compose applies the safe-area insets itself (the top
                // bar consumes the status bar, the bottom bar consumes the home indicator), so
                // letting SwiftUI inset the host as well leaves a strip of window background
                // below the bottom bar and double-pads the top.
                .ignoresSafeArea()
                .onOpenURL { url in
                    // "Open with" on an .avif from Files, Safari or another app.
                    MainViewControllerKt.handleIncomingUrl(url: url)
                }
        }
    }
}
