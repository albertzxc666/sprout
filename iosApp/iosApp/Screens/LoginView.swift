import SwiftUI
import Shared

@MainActor
final class LoginObservable: ObservableObject {
    @Published var state: AuthFormState
    let viewModel: LoginViewModel
    private var subscription: FlowSubscription?
    var onSuccess: (() -> Void)?

    init() {
        let vm = DI.loginViewModel()
        self.viewModel = vm
        self.state = AuthFormState(email: "", password: "", isLoading: false, error: nil, success: false)
        subscription = FlowSubscription(flow: vm.state) { [weak self] (s: AuthFormState) in
            self?.state = s
            if s.success { self?.onSuccess?() }
        }
    }
}

struct LoginView: View {
    @StateObject private var state = LoginObservable()
    @Environment(\.dismiss) private var dismiss

    var body: some View {
        Form {
            Section {
                Text("Войдите, чтобы восстановить свои карточки и держать их синхронизированными между устройствами.")
                    .font(.subheadline)
                    .foregroundColor(AppPalette.textSecondary)
            }
            Section("Учётные данные") {
                TextField("Email", text: Binding(
                    get: { state.state.email },
                    set: { state.viewModel.onEmailChange(value: $0) }
                ))
                .keyboardType(.emailAddress)
                .autocapitalization(.none)
                .disableAutocorrection(true)

                SecureField("Пароль", text: Binding(
                    get: { state.state.password },
                    set: { state.viewModel.onPasswordChange(value: $0) }
                ))
            }
            if let err = state.state.error {
                Section { Text(err).foregroundColor(.red).font(.subheadline) }
            }
            Section {
                Button {
                    state.viewModel.submit()
                } label: {
                    if state.state.isLoading {
                        ProgressView()
                    } else {
                        Text("Войти").frame(maxWidth: .infinity)
                    }
                }
                .disabled(!state.state.canSubmit)
            }
        }
        .navigationTitle("Вход")
        .navigationBarTitleDisplayMode(.inline)
        .onAppear {
            state.onSuccess = { dismiss() }
        }
    }
}
