import SwiftUI
import Shared

@MainActor
final class SpaceListObservable: ObservableObject {
    @Published var home: HomeUiState = HomeUiState(
        isLoading: true,
        totalWords: 0,
        streakDays: 0,
        accuracy: nil,
        dueToday: 0,
        spaces: []
    )
    let viewModel: SpaceListViewModel
    private var subscription: FlowSubscription?

    init() {
        self.viewModel = DI.spaceListViewModel()
        subscription = FlowSubscription(flow: viewModel.homeState) { [weak self] (s: HomeUiState) in
            self?.home = s
        }
    }
}

struct SpaceListView: View {
    @StateObject private var state = SpaceListObservable()
    @State private var showCreate = false
    @State private var spaceToDelete: Space?

    var body: some View {
        ZStack {
            AppPalette.background.ignoresSafeArea()
            content
        }
        .navigationTitle("Мои пространства")
        .toolbar {
            ToolbarItem(placement: .navigationBarLeading) {
                NavigationLink {
                    AccountView()
                } label: {
                    Image(systemName: "person.circle")
                }
            }
            ToolbarItem(placement: .primaryAction) {
                Button { showCreate = true } label: {
                    Label("Создать", systemImage: "plus")
                }
            }
        }
        .sheet(isPresented: $showCreate) {
            CreateSpaceSheet { name, native, target in
                state.viewModel.createSpace(name: name, nativeLang: native, targetLang: target)
                showCreate = false
            }
        }
        .alert(item: $spaceToDelete) { sp in
            Alert(
                title: Text("Удалить пространство?"),
                message: Text("«\(sp.name)» и все его карточки будут удалены."),
                primaryButton: .destructive(Text("Удалить")) {
                    state.viewModel.deleteSpace(id: sp.id)
                },
                secondaryButton: .cancel(Text("Отмена"))
            )
        }
    }

    @ViewBuilder
    private var content: some View {
        if state.home.spaces.isEmpty && !state.home.isLoading {
            VStack(spacing: 12) {
                Image(systemName: "rectangle.stack")
                    .font(.system(size: 56))
                    .foregroundColor(AppPalette.textSecondary)
                Text("Создайте первое пространство")
                    .font(.headline)
                Text("Пространство — это коллекция карточек для одного языка. Например: «Французский» с парой RU → FR.")
                    .font(.subheadline)
                    .foregroundColor(AppPalette.textSecondary)
                    .multilineTextAlignment(.center)
            }
            .padding(32)
        } else {
            ScrollView {
                LazyVStack(spacing: 12) {
                    HeroStats(home: state.home)

                    if state.home.dueToday > 0 {
                        ReviewBanner(due: Int(state.home.dueToday))
                    }

                    ForEach(state.home.spaces, id: \.space.id) { item in
                        SpaceRow(
                            item: item,
                            onDelete: { spaceToDelete = item.space }
                        )
                    }
                }
                .padding(16)
            }
        }
    }
}

private struct HeroStats: View {
    let home: HomeUiState

    var body: some View {
        HStack(spacing: 0) {
            statCol(
                value: "\(home.totalWords)",
                label: pluralRu(Int(home.totalWords), "слово", "слова", "слов")
            )
            divider
            statCol(
                value: "\(home.streakDays)",
                label: (home.streakDays > 0 ? "🔥 " : "")
                    + pluralRu(Int(home.streakDays), "день", "дня", "дней")
            )
            divider
            statCol(
                value: home.accuracy.map { "\(Int(round($0.floatValue * 100)))%" } ?? "—",
                label: "точность"
            )
        }
        .padding(.vertical, 8)
    }

    private func statCol(value: String, label: String) -> some View {
        VStack(spacing: 4) {
            Text(value)
                .font(.title2.weight(.bold))
                .foregroundColor(AppPalette.textPrimary)
            Text(label)
                .font(.caption)
                .foregroundColor(AppPalette.textSecondary)
                .multilineTextAlignment(.center)
        }
        .frame(maxWidth: .infinity)
    }

    private var divider: some View {
        Rectangle()
            .fill(AppPalette.divider)
            .frame(width: 1, height: 36)
    }
}

private struct ReviewBanner: View {
    let due: Int

    var body: some View {
        HStack(spacing: 12) {
            Text("📖").font(.title3)
            VStack(alignment: .leading, spacing: 2) {
                Text("Пора повторить")
                    .font(.headline)
                    .foregroundColor(AppPalette.primary)
                Text("\(due) \(pluralRu(due, "слово ждёт", "слова ждут", "слов ждут")) вашего внимания")
                    .font(.caption)
                    .foregroundColor(AppPalette.primary)
            }
            Spacer()
        }
        .padding(.horizontal, 16)
        .padding(.vertical, 12)
        .background(AppPalette.primary.opacity(0.12))
        .clipShape(RoundedRectangle(cornerRadius: 12))
    }
}

private struct SpaceRow: View {
    let item: SpaceCardItem
    let onDelete: () -> Void

    private var progress: Double {
        guard item.totalCards > 0 else { return 0 }
        return Double(item.studiedCards) / Double(item.totalCards)
    }

    var body: some View {
        NavigationLink(destination: GroupListView(spaceId: item.space.id)) {
            VStack(alignment: .leading, spacing: 0) {
                HStack(alignment: .center) {
                    LanguagePill(native: item.space.nativeLang, target: item.space.targetLang)
                    Spacer()
                    Button(action: onDelete) {
                        Image(systemName: "trash")
                            .foregroundColor(AppPalette.textSecondary)
                    }
                    .buttonStyle(.plain)
                }

                Text(item.space.name)
                    .font(.title3.weight(.semibold))
                    .foregroundColor(AppPalette.textPrimary)
                    .padding(.top, 8)

                metaRow
                    .padding(.top, 4)

                if item.totalCards > 0 {
                    progressRow
                        .padding(.top, 14)
                    NavigationLink(destination: GardenView(spaceId: item.space.id, title: item.space.name)) {
                        GardenStrip(stages: item.stages)
                    }
                    .buttonStyle(.plain)
                    .padding(.top, 14)
                }
            }
            .padding(16)
            .background(AppPalette.surface)
            .clipShape(RoundedRectangle(cornerRadius: 16))
        }
        .buttonStyle(.plain)
    }

    @ViewBuilder private var metaRow: some View {
        HStack(spacing: 0) {
            Text("\(item.totalCards) \(pluralRu(Int(item.totalCards), "слово", "слова", "слов"))")
                .font(.subheadline)
                .foregroundColor(AppPalette.textSecondary)
            if item.dueCount > 0 {
                Text("  ·  ").font(.subheadline).foregroundColor(AppPalette.textSecondary)
                Circle().fill(AppPalette.primary).frame(width: 6, height: 6)
                Text(" \(item.dueCount) на повторение")
                    .font(.subheadline)
                    .foregroundColor(AppPalette.primary)
            }
            if let last = item.lastStudiedAt {
                Text("  ·  \(relativeTimeRu(last.int64Value))")
                    .font(.subheadline)
                    .foregroundColor(AppPalette.textSecondary)
            }
            Spacer()
        }
    }

    @ViewBuilder private var progressRow: some View {
        let percent = Int(round(progress * 100))
        HStack(spacing: 12) {
            GeometryReader { geo in
                ZStack(alignment: .leading) {
                    Capsule().fill(AppPalette.divider).frame(height: 6)
                    Capsule()
                        .fill(AppPalette.primary)
                        .frame(width: geo.size.width * min(max(progress, 0), 1), height: 6)
                }
            }
            .frame(height: 6)
            Text("\(percent)%")
                .font(.subheadline.weight(.medium))
                .foregroundColor(AppPalette.textSecondary)
            NavigationLink(destination: StudySetupView(scope: StudyScope.Space(spaceId: item.space.id))) {
                Image(systemName: "play.fill")
                    .foregroundColor(.white)
                    .padding(10)
                    .background(AppPalette.primary)
                    .clipShape(Circle())
            }
            .buttonStyle(.plain)
        }
    }
}

private struct LanguagePill: View {
    let native: String
    let target: String

    var body: some View {
        HStack(spacing: 0) {
            Text(languageFlag(native))
            Text("  \(native.uppercased())  →  \(target.uppercased())  ")
                .font(.caption.weight(.medium))
                .foregroundColor(AppPalette.textSecondary)
            Text(languageFlag(target))
        }
        .padding(.horizontal, 10)
        .padding(.vertical, 5)
        .background(AppPalette.background)
        .clipShape(RoundedRectangle(cornerRadius: 8))
    }
}

private struct GardenStrip: View {
    let stages: [GardenStage: KotlinInt]

    private let order: [GardenStage] = [.seed, .sprout, .bush, .flower, .tree]

    var body: some View {
        HStack {
            HStack(spacing: 0) {
                ForEach(Array(order.enumerated()), id: \.offset) { idx, stage in
                    if idx > 0 {
                        Text("·")
                            .foregroundColor(AppPalette.divider)
                            .padding(.horizontal, 6)
                    }
                    Text(stage.emoji).font(.body)
                    Text(" \(count(for: stage))")
                        .font(.subheadline.weight(.medium))
                        .foregroundColor(AppPalette.textSecondary)
                }
            }
            Spacer()
            Text("Сад →")
                .font(.subheadline.weight(.medium))
                .foregroundColor(AppPalette.primary)
        }
        .padding(.horizontal, 12)
        .padding(.vertical, 8)
        .background(AppPalette.background)
        .clipShape(RoundedRectangle(cornerRadius: 10))
    }

    private func count(for stage: GardenStage) -> Int {
        Int(truncating: stages[stage] ?? 0)
    }
}

extension Space: @retroactive Identifiable {}

struct CreateSpaceSheet: View {
    var onCreate: (String, String, String) -> Void

    @State private var name = ""
    @State private var native = "ru"
    @State private var target = "en"
    @Environment(\.dismiss) private var dismiss

    private let langs = LanguagePair.companion.DEFAULT

    var body: some View {
        NavigationStack {
            Form {
                Section("Название") {
                    TextField("Например, «Английский»", text: $name)
                }
                Section("Языки") {
                    Picker("Родной", selection: $native) {
                        ForEach(0..<langs.count, id: \.self) { i in
                            Text("\(langs[i].nativeLabel) (\(langs[i].code.uppercased()))")
                                .tag(langs[i].code)
                        }
                    }
                    Picker("Изучаемый", selection: $target) {
                        ForEach(0..<langs.count, id: \.self) { i in
                            Text("\(langs[i].nativeLabel) (\(langs[i].code.uppercased()))")
                                .tag(langs[i].code)
                        }
                    }
                }
            }
            .navigationTitle("Новое пространство")
            .toolbar {
                ToolbarItem(placement: .cancellationAction) {
                    Button("Отмена") { dismiss() }
                }
                ToolbarItem(placement: .confirmationAction) {
                    Button("Создать") {
                        onCreate(name, native, target)
                    }
                    .disabled(name.isEmpty || native == target)
                }
            }
        }
    }
}
