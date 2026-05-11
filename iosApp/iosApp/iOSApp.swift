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

private struct RootView: View {
    @State private var onboardingComplete: Bool = {
        DI.preferences().getBoolean(key: PrefKeys.shared.ONBOARDING_COMPLETE, default: false)
    }()

    var body: some View {
        if onboardingComplete {
            NavigationStack { SpaceListView() }
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
