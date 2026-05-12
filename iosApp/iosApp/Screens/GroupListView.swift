import SwiftUI
import Shared

@MainActor
final class GroupListObservable: ObservableObject {
    @Published var state: GroupListUiState = GroupListUiState(
        space: nil, items: [], totalDue: 0, isLoading: true
    )
    let viewModel: GroupListViewModel
    private var subscription: FlowSubscription?

    init(spaceId: Int64) {
        self.viewModel = DI.groupListViewModel(spaceId: spaceId)
        subscription = FlowSubscription(flow: viewModel.state) { [weak self] (s: GroupListUiState) in
            self?.state = s
        }
    }
}

struct GroupListView: View {
    let spaceId: Int64
    @StateObject private var state: GroupListObservable
    @State private var showCreate = false
    @State private var groupToRename: GroupCardItem?
    @State private var groupToDelete: GroupCardItem?

    init(spaceId: Int64) {
        self.spaceId = spaceId
        _state = StateObject(wrappedValue: GroupListObservable(spaceId: spaceId))
    }

    var body: some View {
        ZStack {
            AppPalette.background.ignoresSafeArea()
            content
        }
        .navigationTitle(state.state.space?.name ?? "")
        .toolbar {
            ToolbarItem(placement: .primaryAction) {
                Button { showCreate = true } label: {
                    Label("Группа", systemImage: "plus")
                }
            }
        }
        .sheet(isPresented: $showCreate) {
            GroupNameSheet(title: "Новая группа", initial: "") { name in
                state.viewModel.createGroup(name: name)
                showCreate = false
            }
        }
        .sheet(item: $groupToRename) { gi in
            GroupNameSheet(title: "Переименовать", initial: gi.group.name) { name in
                state.viewModel.renameGroup(id: gi.group.id, name: name)
                groupToRename = nil
            }
        }
        .alert(item: $groupToDelete) { gi in
            Alert(
                title: Text("Удалить группу?"),
                message: Text("«\(gi.group.name)» и \(gi.cardsCount) карточек будут удалены."),
                primaryButton: .destructive(Text("Удалить")) {
                    state.viewModel.deleteGroup(id: gi.group.id)
                },
                secondaryButton: .cancel(Text("Отмена"))
            )
        }
    }

    @ViewBuilder
    private var content: some View {
        ScrollView {
            LazyVStack(spacing: 12) {
                if state.state.totalDue > 0 {
                    StudyAllBanner(due: Int(state.state.totalDue), spaceId: spaceId)
                }
                ForEach(state.state.items, id: \.group.id) { item in
                    GroupRow(
                        item: item,
                        onRename: { groupToRename = item },
                        onDelete: { groupToDelete = item }
                    )
                }
            }
            .padding(16)
        }
    }
}

private struct StudyAllBanner: View {
    let due: Int
    let spaceId: Int64

    var body: some View {
        NavigationLink(
            destination: StudySetupView(scope: StudyScope.Space(spaceId: spaceId))
        ) {
            HStack {
                VStack(alignment: .leading) {
                    Text("Изучать всё пространство").font(.headline)
                    Text("\(due) к повторению")
                        .font(.caption)
                        .foregroundColor(AppPalette.textSecondary)
                }
                Spacer()
                Image(systemName: "play.fill")
                    .padding(10)
                    .background(AppPalette.primary)
                    .foregroundColor(.white)
                    .clipShape(Circle())
            }
            .padding(16)
            .background(AppPalette.surface)
            .clipShape(RoundedRectangle(cornerRadius: 12))
        }
        .buttonStyle(.plain)
    }
}

private struct GroupRow: View {
    let item: GroupCardItem
    let onRename: () -> Void
    let onDelete: () -> Void

    var body: some View {
        NavigationLink(destination: CardListView(groupId: item.group.id, title: item.group.name)) {
            VStack(alignment: .leading, spacing: 8) {
                HStack {
                    Text(item.group.name)
                        .font(.title3.weight(.semibold))
                        .foregroundColor(AppPalette.textPrimary)
                    Spacer()
                    Menu {
                        Button("Переименовать", action: onRename)
                        Button("Удалить", role: .destructive, action: onDelete)
                    } label: {
                        Image(systemName: "ellipsis")
                            .foregroundColor(AppPalette.textSecondary)
                            .padding(8)
                    }
                }
                Text("\(Int(item.cardsCount)) карточек" +
                     (item.dueCount > 0 ? " · \(Int(item.dueCount)) к повторению" : ""))
                    .font(.subheadline)
                    .foregroundColor(AppPalette.textSecondary)
                if item.dueCount > 0 {
                    NavigationLink(
                        destination: StudySetupView(scope: StudyScope.Group(groupId: item.group.id))
                    ) {
                        Label("Изучать", systemImage: "play.fill")
                            .font(.subheadline.weight(.medium))
                            .foregroundColor(AppPalette.primary)
                    }
                    .buttonStyle(.plain)
                }
            }
            .padding(16)
            .background(AppPalette.surface)
            .clipShape(RoundedRectangle(cornerRadius: 16))
        }
        .buttonStyle(.plain)
    }
}

extension GroupCardItem: @retroactive Identifiable {
    public var id: Int64 { group.id }
}

private struct GroupNameSheet: View {
    let title: String
    let initial: String
    var onConfirm: (String) -> Void

    @State private var name: String
    @Environment(\.dismiss) private var dismiss

    init(title: String, initial: String, onConfirm: @escaping (String) -> Void) {
        self.title = title
        self.initial = initial
        self.onConfirm = onConfirm
        _name = State(initialValue: initial)
    }

    var body: some View {
        NavigationStack {
            Form {
                Section("Название") {
                    TextField("Например, «Еда»", text: $name)
                }
            }
            .navigationTitle(title)
            .toolbar {
                ToolbarItem(placement: .cancellationAction) {
                    Button("Отмена") { dismiss() }
                }
                ToolbarItem(placement: .confirmationAction) {
                    Button("Сохранить") {
                        onConfirm(name)
                    }
                    .disabled(name.trimmingCharacters(in: .whitespaces).isEmpty)
                }
            }
        }
    }
}
