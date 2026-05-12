import SwiftUI
import Shared

@MainActor
final class AccountObservable: ObservableObject {
    @Published var authState: AuthState
    @Published var syncStatus: SyncStatus
    let viewModel: AccountViewModel
    private var authSubscription: FlowSubscription?
    private var syncSubscription: FlowSubscription?

    init() {
        let vm = DI.accountViewModel()
        self.viewModel = vm
        self.authState = AuthState.companion.Anonymous
        self.syncStatus = SyncStatusNotAuthenticated()

        authSubscription = FlowSubscription(flow: vm.authState) { [weak self] (s: AuthState) in
            self?.authState = s
        }
        syncSubscription = FlowSubscription(flow: vm.syncStatus) { [weak self] (s: SyncStatus) in
            self?.syncStatus = s
        }
    }
}

struct AccountView: View {
    @StateObject private var state = AccountObservable()

    var body: some View {
        Group {
            if state.authState.isAuthenticated {
                authenticated
            } else {
                anonymous
            }
        }
        .navigationTitle("Аккаунт")
        .navigationBarTitleDisplayMode(.large)
    }

    private var anonymous: some View {
        VStack(spacing: 16) {
            Text("Войдите или создайте аккаунт, чтобы хранить копию карточек на сервере и восстанавливать их на новом устройстве.")
                .font(.subheadline)
                .foregroundColor(AppPalette.textSecondary)
                .multilineTextAlignment(.leading)
                .frame(maxWidth: .infinity, alignment: .leading)

            NavigationLink {
                LoginView()
            } label: {
                Text("Войти")
                    .frame(maxWidth: .infinity)
                    .padding(.vertical, 12)
            }
            .buttonStyle(.borderedProminent)

            NavigationLink {
                RegisterView()
            } label: {
                Text("Создать аккаунт")
                    .frame(maxWidth: .infinity)
                    .padding(.vertical, 12)
            }
            .buttonStyle(.bordered)

            Spacer()
        }
        .padding(24)
    }

    private var authenticated: some View {
        VStack(spacing: 16) {
            VStack(alignment: .leading, spacing: 8) {
                Text("Вы вошли как").font(.caption).foregroundColor(AppPalette.textSecondary)
                Text(state.authState.email ?? "—").font(.headline)
                syncStatusLine
            }
            .frame(maxWidth: .infinity, alignment: .leading)
            .padding(16)
            .background(AppPalette.surface)
            .cornerRadius(16)

            Button { state.viewModel.syncNow() } label: {
                Text("Синхронизировать сейчас").frame(maxWidth: .infinity).padding(.vertical, 12)
            }
            .buttonStyle(.borderedProminent)
            .disabled(state.syncStatus is SyncStatusPushing || state.syncStatus is SyncStatusPulling)

            NavigationLink {
                SnapshotHistoryView()
            } label: {
                Text("История снапшотов").frame(maxWidth: .infinity).padding(.vertical, 12)
            }
            .buttonStyle(.bordered)

            Button(role: .destructive) {
                state.viewModel.signOut()
            } label: {
                Text("Выйти").frame(maxWidth: .infinity).padding(.vertical, 12)
            }

            Spacer()
        }
        .padding(24)
    }

    @ViewBuilder
    private var syncStatusLine: some View {
        if state.syncStatus is SyncStatusIdle {
            Text("Синхронизировано").font(.caption).foregroundColor(AppPalette.textSecondary)
        } else if state.syncStatus is SyncStatusPushing {
            HStack(spacing: 8) {
                ProgressView().scaleEffect(0.8)
                Text("Отправка изменений…").font(.caption)
            }
        } else if state.syncStatus is SyncStatusPulling {
            HStack(spacing: 8) {
                ProgressView().scaleEffect(0.8)
                Text("Получение с сервера…").font(.caption)
            }
        } else if let err = state.syncStatus as? SyncStatusError {
            Text("Ошибка: \(err.message)").font(.caption).foregroundColor(.red)
        } else {
            Text("Не подключено").font(.caption).foregroundColor(AppPalette.textSecondary)
        }
    }
}
