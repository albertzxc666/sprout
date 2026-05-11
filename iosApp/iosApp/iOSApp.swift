import SwiftUI
import Shared

@main
struct iOSApp: App {
    init() {
        KoinHelper.shared.start()
    }

    var body: some Scene {
        WindowGroup {
            RootView()
                .accentColor(AppPalette.primary)
        }
    }
}

/// Holds the global NavigationPath so any descendant can pop to root via the environment.
final class AppRouter: ObservableObject {
    @Published var path = NavigationPath()
    func popToRoot() { path = NavigationPath() }
}

private struct RootView: View {
    @State private var onboardingComplete: Bool = {
        DI.preferences().getBoolean(key: PrefKeys.shared.ONBOARDING_COMPLETE, default: false)
    }()
    @StateObject private var router = AppRouter()

    var body: some View {
        if onboardingComplete {
            NavigationStack(path: $router.path) {
                SpaceListView()
            }
            .environmentObject(router)
        } else {
            WelcomeView {
                DI.preferences().setBoolean(
                    key: PrefKeys.shared.ONBOARDING_COMPLETE,
                    value: true
                )
                onboardingComplete = true
            }
        }
    }
}
