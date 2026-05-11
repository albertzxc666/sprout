import SwiftUI
import Shared

@MainActor
final class CardListObservable: ObservableObject {
    @Published var cards: [Card] = []
    @Published var suggestions: SuggestionsState = SuggestionsState(
        query: "", fromCards: [], fromDictionary: []
    )

    let viewModel: CardListViewModel
    private var cardsSub: FlowSubscription?
    private var suggSub: FlowSubscription?

    init(spaceId: Int64) {
        self.viewModel = DI.cardListViewModel(spaceId: spaceId)
        cardsSub = FlowSubscription(flow: viewModel.cards) { [weak self] (list: [Card]) in
            self?.cards = list
        }
        suggSub = FlowSubscription(flow: viewModel.suggestions) { [weak self] (s: SuggestionsState) in
            self?.suggestions = s
        }
    }
}

struct CardListView: View {
    let spaceId: Int64
    let title: String
    @StateObject private var state: CardListObservable

    @State private var showCreate = false
    @State private var cardToEdit: Card?
    @State private var cardToDelete: Card?

    init(spaceId: Int64, title: String) {
        self.spaceId = spaceId
        self.title = title
        _state = StateObject(wrappedValue: CardListObservable(spaceId: spaceId))
    }

    var body: some View {
        ZStack {
            AppPalette.background.ignoresSafeArea()
            VStack(spacing: 0) {
                if !state.cards.isEmpty {
                    NavigationLink(destination: StudySetupView(spaceId: spaceId)) {
                        Text("Начать обучение")
                            .font(.headline)
                            .frame(maxWidth: .infinity)
                            .frame(height: 50)
                            .background(AppPalette.primary)
                            .foregroundColor(.white)
                            .clipShape(RoundedRectangle(cornerRadius: 12))
                    }
                    .padding(16)
                }
                if state.cards.isEmpty {
                    Spacer()
                    VStack(spacing: 12) {
                        Image(systemName: "tray")
                            .font(.system(size: 48))
                            .foregroundColor(AppPalette.textSecondary)
                        Text("Добавьте первую карточку").font(.headline)
                        Text("Карточка — это пара слов: на родном языке и на изучаемом. Sprout будет показывать одно и проверять перевод.")
                            .font(.subheadline)
                            .foregroundColor(AppPalette.textSecondary)
                            .multilineTextAlignment(.center)
                            .padding(.horizontal, 32)
                    }
                    Spacer()
                } else {
                    ScrollView {
                        LazyVStack(spacing: 8) {
                            ForEach(state.cards, id: \.id) { c in
                                CardRow(
                                    card: c,
                                    onEdit: { cardToEdit = c },
                                    onDelete: { cardToDelete = c }
                                )
                            }
                        }
                        .padding(16)
                    }
                }
            }
        }
        .navigationTitle(title)
        .navigationBarTitleDisplayMode(.inline)
        .toolbar {
            ToolbarItem(placement: .primaryAction) {
                Button { showCreate = true } label: {
                    Label("Добавить", systemImage: "plus")
                }
            }
        }
        .sheet(isPresented: $showCreate, onDismiss: {
            state.viewModel.clearSuggestions()
        }) {
            CardEditorSheet(
                initial: nil,
                suggestions: state.suggestions,
                onNativeChanged: { state.viewModel.onNativeWordChanged(text: $0) },
                onSave: { native, target, hint in
                    state.viewModel.addCard(nativeWord: native, targetWord: target, hint: hint)
                    showCreate = false
                }
            )
        }
        .sheet(item: $cardToEdit, onDismiss: {
            state.viewModel.clearSuggestions()
        }) { c in
            CardEditorSheet(
                initial: c,
                suggestions: state.suggestions,
                onNativeChanged: { state.viewModel.onNativeWordChanged(text: $0) },
                onSave: { native, target, hint in
                    let updated = c.doCopy(
                        id: c.id, spaceId: c.spaceId,
                        nativeWord: native, targetWord: target,
                        hint: hint,
                        intervalDays: c.intervalDays,
                        easiness: c.easiness,
                        repetitions: c.repetitions,
                        nextReviewAt: c.nextReviewAt
                    )
                    state.viewModel.updateCard(card: updated)
                    cardToEdit = nil
                }
            )
        }
        .alert(item: $cardToDelete) { c in
            Alert(
                title: Text("Удалить карточку?"),
                message: Text("«\(c.nativeWord)» — «\(c.targetWord)»"),
                primaryButton: .destructive(Text("Удалить")) {
                    state.viewModel.deleteCard(id: c.id)
                },
                secondaryButton: .cancel(Text("Отмена"))
            )
        }
    }
}

extension Card: @retroactive Identifiable {}

struct CardRow: View {
    let card: Card
    let onEdit: () -> Void
    let onDelete: () -> Void

    var body: some View {
        HStack {
            VStack(alignment: .leading, spacing: 4) {
                Text(card.nativeWord).font(.body)
                Text("↔  \(card.targetWord)")
                    .font(.subheadline)
                    .foregroundColor(AppPalette.textSecondary)
                if let hint = card.hint, !hint.isEmpty {
                    Text("💡 \(hint)")
                        .font(.caption)
                        .foregroundColor(AppPalette.textSecondary)
                }
            }
            Spacer()
            Button(action: onEdit) {
                Image(systemName: "pencil").foregroundColor(AppPalette.textSecondary)
            }
            .buttonStyle(.plain)
            Button(action: onDelete) {
                Image(systemName: "trash").foregroundColor(AppPalette.textSecondary)
            }
            .buttonStyle(.plain)
        }
        .padding(16)
        .background(AppPalette.surface)
        .clipShape(RoundedRectangle(cornerRadius: 16))
    }
}

struct CardEditorSheet: View {
    let initial: Card?
    let suggestions: SuggestionsState
    var onNativeChanged: (String) -> Void
    var onSave: (String, String, String?) -> Void

    @State private var native: String
    @State private var target: String
    @State private var hint: String
    @Environment(\.dismiss) private var dismiss

    init(
        initial: Card?,
        suggestions: SuggestionsState,
        onNativeChanged: @escaping (String) -> Void,
        onSave: @escaping (String, String, String?) -> Void
    ) {
        self.initial = initial
        self.suggestions = suggestions
        self.onNativeChanged = onNativeChanged
        self.onSave = onSave
        _native = State(initialValue: initial?.nativeWord ?? "")
        _target = State(initialValue: initial?.targetWord ?? "")
        _hint = State(initialValue: initial?.hint ?? "")
    }

    var body: some View {
        NavigationStack {
            ScrollView {
                VStack(alignment: .leading, spacing: 14) {
                    LabeledField(title: "Слово (родной)") {
                        TextField("", text: $native)
                            .onChange(of: native) { newValue in
                                onNativeChanged(newValue)
                            }
                    }

                    SuggestionsBlock(
                        fromCards: suggestions.fromCards,
                        fromDictionary: suggestions.fromDictionary,
                        onPick: { target = $0.text }
                    )

                    LabeledField(title: "Слово (изучаемый)") {
                        TextField("", text: $target)
                    }
                    LabeledField(title: "Подсказка (необязательно)") {
                        TextField("", text: $hint)
                    }
                }
                .padding(16)
            }
            .background(AppPalette.background)
            .navigationTitle(initial == nil ? "Новая карточка" : "Редактировать")
            .toolbar {
                ToolbarItem(placement: .cancellationAction) {
                    Button("Отмена") { dismiss() }
                }
                ToolbarItem(placement: .confirmationAction) {
                    Button("Сохранить") {
                        onSave(native, target, hint.isEmpty ? nil : hint)
                    }
                    .disabled(native.isEmpty || target.isEmpty)
                }
            }
        }
    }
}

private struct LabeledField<Content: View>: View {
    let title: String
    @ViewBuilder let content: () -> Content

    var body: some View {
        VStack(alignment: .leading, spacing: 6) {
            Text(title)
                .font(.caption)
                .foregroundColor(AppPalette.textSecondary)
            content()
                .textFieldStyle(.roundedBorder)
        }
    }
}

private struct SuggestionsBlock: View {
    let fromCards: [TranslationSuggestion]
    let fromDictionary: [TranslationSuggestion]
    let onPick: (TranslationSuggestion) -> Void

    private var hasOnline: Bool {
        fromDictionary.contains { $0.source == .onlineDictionary }
    }

    private var yandexConfigured: Bool {
        KoinHelper.shared.isYandexConfigured()
    }

    var body: some View {
        VStack(alignment: .leading, spacing: 8) {
            if !fromCards.isEmpty {
                SuggestionGroup(
                    label: "Из ваших карточек",
                    items: fromCards,
                    onPick: onPick
                )
            }
            if !fromDictionary.isEmpty {
                SuggestionGroup(
                    label: "Из словаря",
                    items: fromDictionary,
                    onPick: onPick
                )
            }
            if hasOnline {
                Text("Реализовано с помощью сервиса «Яндекс.Словарь»")
                    .font(.caption2)
                    .foregroundColor(AppPalette.textSecondary)
            }
            if !yandexConfigured {
                HStack(spacing: 6) {
                    Text("🔑")
                    Text("Онлайн-словарь не настроен в этой сборке")
                        .font(.caption2)
                        .foregroundColor(AppPalette.textSecondary)
                }
            }
        }
    }
}

private struct SuggestionGroup: View {
    let label: String
    let items: [TranslationSuggestion]
    let onPick: (TranslationSuggestion) -> Void

    var body: some View {
        VStack(alignment: .leading, spacing: 4) {
            Text(label)
                .font(.caption2)
                .foregroundColor(AppPalette.textSecondary)
            FlowLayout(spacing: 6) {
                ForEach(0..<items.count, id: \.self) { i in
                    let s = items[i]
                    Button(action: { onPick(s) }) {
                        Text(chipLabel(s))
                            .font(.subheadline)
                            .lineLimit(1)
                            .truncationMode(.tail)
                            .padding(.horizontal, 12)
                            .padding(.vertical, 6)
                            .background(chipColor(s.source))
                            .foregroundColor(AppPalette.textPrimary)
                            .clipShape(Capsule())
                            .overlay(Capsule().stroke(AppPalette.divider, lineWidth: 1))
                    }
                    .buttonStyle(.plain)
                }
            }
        }
    }

    private func chipLabel(_ s: TranslationSuggestion) -> String {
        s.source == .onlineDictionary ? "☁ \(s.text)" : s.text
    }

    private func chipColor(_ source: SuggestionSource) -> Color {
        switch source {
        case .userCards: return AppPalette.primary.opacity(0.12)
        default: return AppPalette.surface
        }
    }
}
