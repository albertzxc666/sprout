import SwiftUI
import Shared

@MainActor
final class PostLoginRestoreObservable: ObservableObject {
    @Published var state: PostLoginRestoreUiState
    let viewModel: PostLoginRestoreViewModel
    private var subscription: FlowSubscription?
    var onDone: (() -> Void)?

    init() {
        let vm = DI.postLoginRestoreViewModel()
        self.viewModel = vm
        self.state = PostLoginRestoreUiStateLoading()
        subscription = FlowSubscription(flow: vm.state) { [weak self] (s: PostLoginRestoreUiState) in
            self?.state = s
            if s is PostLoginRestoreUiStateDone {
                self?.onDone?()
            }
        }
    }
}

struct PostLoginRestoreView: View {
    @StateObject private var state = PostLoginRestoreObservable()
    @Environment(\.dismiss) private var dismiss

    var body: some View {
        ZStack {
            content
        }
        .navigationTitle("")
        .navigationBarBackButtonHidden(true)
        .onAppear {
            state.onDone = { dismiss() }
        }
    }

    @ViewBuilder
    private var content: some View {
        switch state.state {
        case is PostLoginRestoreUiStateLoading:
            progressBlock("Проверяем данные на сервере…")
        case is PostLoginRestoreUiStateRestoring:
            progressBlock("Восстанавливаем…")
        case is PostLoginRestoreUiStateLoggingOut:
            progressBlock("Отмена…")
        case let confirm as PostLoginRestoreUiStateConfirm:
            restorePrompt(latest: confirm.latest)
        case let err as PostLoginRestoreUiStateError:
            errorOverlay(err)
        default:
            EmptyView()
        }
    }

    private func progressBlock(_ label: String) -> some View {
        VStack(spacing: 16) {
            ProgressView()
            Text(label).font(.subheadline).foregroundColor(AppPalette.textSecondary)
        }
        .frame(maxWidth: .infinity, maxHeight: .infinity)
    }

    private func restorePrompt(latest: SnapshotHistoryEntry) -> some View {
        VStack(spacing: 16) {
            Spacer()
            VStack(alignment: .leading, spacing: 12) {
                Text("У этого аккаунта уже есть резервная копия")
                    .font(.headline)
                Text(snapshotSubtitle(latest))
                    .font(.subheadline)
                    .foregroundColor(AppPalette.textSecondary)
                Text("Что сделать с текущими локальными карточками?")
                    .font(.subheadline)
            }
            .frame(maxWidth: .infinity, alignment: .leading)
            .padding(20)
            .background(AppPalette.surface)
            .cornerRadius(20)

            Button {
                state.viewModel.confirmRestore()
            } label: {
                Text("Восстановить с сервера")
                    .frame(maxWidth: .infinity)
                    .padding(.vertical, 12)
            }
            .buttonStyle(.borderedProminent)

            Button {
                state.viewModel.keepLocal()
            } label: {
                Text("Оставить локальные")
                    .frame(maxWidth: .infinity)
                    .padding(.vertical, 12)
            }
            .buttonStyle(.bordered)

            Button(role: .cancel) {
                state.viewModel.cancel()
            } label: {
                Text("Отмена")
                    .frame(maxWidth: .infinity)
                    .padding(.vertical, 10)
            }
            Spacer()
        }
        .padding(24)
    }

    private func errorOverlay(_ err: PostLoginRestoreUiStateError) -> some View {
        let canDismiss = err.source == ErrorSource.restore
        return VStack(spacing: 16) {
            Spacer()
            VStack(spacing: 12) {
                Text("Ошибка").font(.headline)
                Text(err.message)
                    .font(.subheadline)
                    .foregroundColor(.red)
                    .multilineTextAlignment(.center)
            }
            .padding(20)
            .background(AppPalette.surface)
            .cornerRadius(20)

            Button {
                state.viewModel.retry()
            } label: {
                Text("Повторить").frame(maxWidth: .infinity).padding(.vertical, 12)
            }
            .buttonStyle(.borderedProminent)

            if canDismiss {
                Button {
                    state.viewModel.dismissError()
                } label: {
                    Text("Назад").frame(maxWidth: .infinity).padding(.vertical, 12)
                }
                .buttonStyle(.bordered)
            } else {
                Button(role: .cancel) {
                    state.viewModel.cancel()
                } label: {
                    Text("Отмена").frame(maxWidth: .infinity).padding(.vertical, 10)
                }
            }
            Spacer()
        }
        .padding(24)
    }

    private func snapshotSubtitle(_ entry: SnapshotHistoryEntry) -> String {
        let date = Date(timeIntervalSince1970: TimeInterval(entry.createdAt) / 1000.0)
        let f = DateFormatter()
        f.dateStyle = .medium
        f.timeStyle = .short
        var s = "Последний снапшот: \(f.string(from: date)), \(entry.sizeBytes / 1024) КБ"
        if let info = entry.clientInfo { s += " · \(info)" }
        return s + "."
    }
}
