import Foundation
import Combine
import Shared

/// Observes a Kotlin Flow (passed as `Any`) and republishes values to SwiftUI.
/// Cast the values inside the closure to your expected Swift type.
@MainActor
final class FlowSubscription {
    private var cancellable: Cancellable?

    init<T>(flow: Any, onEach: @escaping (T) -> Void) {
        self.cancellable = FlowHelperKt.subscribe(flow: flow as! Kotlinx_coroutines_coreFlow) { value in
            if let typed = value as? T {
                Task { @MainActor in onEach(typed) }
            }
        }
    }

    func cancel() { cancellable?.cancel() }
    deinit { cancellable?.cancel() }
}
