import SwiftUI
import Shared

@MainActor
final class SnapshotHistoryObservable: ObservableObject {
    @Published var ui: HistoryUiState
    let viewModel: AccountViewModel
    private var subscription: FlowSubscription?

    init() {
        let vm = DI.accountViewModel()
        self.viewModel = vm
        self.ui = HistoryUiState(isLoading: false, items: [], error: nil)
        subscription = FlowSubscription(flow: vm.history) { [weak self] (s: HistoryUiState) in
            self?.ui = s
        }
        vm.loadHistory()
    }
}

struct SnapshotHistoryView: View {
    @StateObject private var state = SnapshotHistoryObservable()
    @State private var pendingRestore: SnapshotHistoryEntry?
    @Environment(\.dismiss) private var dismiss

    var body: some View {
        Group {
            if state.ui.isLoading {
                ProgressView()
                    .frame(maxWidth: .infinity, maxHeight: .infinity)
            } else if let err = state.ui.error {
                Text(err).foregroundColor(.red).padding()
            } else if state.ui.items.isEmpty {
                Text("На сервере пока нет снапшотов")
                    .foregroundColor(AppPalette.textSecondary)
                    .frame(maxWidth: .infinity, maxHeight: .infinity)
            } else {
                List(state.ui.items, id: \.id) { item in
                    Button { pendingRestore = item } label: {
                        VStack(alignment: .leading, spacing: 4) {
                            Text(formattedDate(item.createdAt))
                                .font(.headline)
                                .foregroundColor(AppPalette.textPrimary)
                            Text(
                                "\(item.sizeBytes / 1024) КБ" +
                                (item.clientInfo.map { " · \($0)" } ?? "")
                            )
                            .font(.caption)
                            .foregroundColor(AppPalette.textSecondary)
                        }
                    }
                }
            }
        }
        .navigationTitle("История снапшотов")
        .navigationBarTitleDisplayMode(.inline)
        .alert(item: $pendingRestore) { entry in
            Alert(
                title: Text("Восстановить снапшот?"),
                message: Text("Все текущие локальные карточки будут заменены данными из этого снапшота (\(formattedDate(entry.createdAt)), \(entry.sizeBytes / 1024) КБ)."),
                primaryButton: .destructive(Text("Восстановить")) {
                    state.viewModel.restoreSnapshot(snapshotId: entry.id) {
                        dismiss()
                    }
                },
                secondaryButton: .cancel(Text("Отмена"))
            )
        }
    }

    private func formattedDate(_ ms: Int64) -> String {
        let d = Date(timeIntervalSince1970: TimeInterval(ms) / 1000.0)
        let f = DateFormatter()
        f.dateStyle = .medium
        f.timeStyle = .short
        return f.string(from: d)
    }
}

extension SnapshotHistoryEntry: Identifiable {}
