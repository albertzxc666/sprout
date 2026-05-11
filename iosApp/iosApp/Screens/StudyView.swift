import SwiftUI
import Shared

@MainActor
final class StudyObservable: ObservableObject {
    @Published var state: StudyState?

    let viewModel: StudyViewModel
    private var subscription: FlowSubscription?

    init(spaceId: Int64, direction: StudyDirection, mode: StudyMode) {
        self.viewModel = DI.studyViewModel(spaceId: spaceId, direction: direction, mode: mode)
        subscription = FlowSubscription(flow: viewModel.state) { [weak self] (s: StudyState) in
            self?.state = s
        }
    }
}

struct StudyView: View {
    let spaceId: Int64
    let direction: StudyDirection
    let mode: StudyMode
    @StateObject private var obs: StudyObservable
    @State private var goResult = false
    @State private var tooltipVisible = false
    @State private var tooltipDismissed: Bool

    init(spaceId: Int64, direction: StudyDirection, mode: StudyMode) {
        self.spaceId = spaceId
        self.direction = direction
        self.mode = mode
        _obs = StateObject(wrappedValue: StudyObservable(
            spaceId: spaceId, direction: direction, mode: mode
        ))
        _tooltipDismissed = State(initialValue: DI.preferences()
            .getBoolean(key: PrefKeys.shared.SRS_TOOLTIP_SEEN, default: false))
    }

    private var modeTitle: String {
        switch mode {
        case .scheduled: return "Расписание"
        case .drill: return "Тренажёр"
        default: return "Обучение"
        }
    }

    var body: some View {
        ZStack {
            AppPalette.background.ignoresSafeArea()
            if let s = obs.state {
                if s.isLoading {
                    ProgressView()
                } else if s.cards.isEmpty {
                    emptyState(state: s)
                } else {
                    content(state: s)
                }

                NavigationLink(
                    destination: StudyResultView(
                        spaceId: spaceId,
                        direction: direction,
                        mode: mode,
                        correct: Int(s.correctCount),
                        total: Int(s.total)
                    ),
                    isActive: $goResult
                ) { EmptyView() }
            } else {
                ProgressView()
            }
        }
        .navigationTitle(modeTitle)
        .navigationBarTitleDisplayMode(.inline)
        .onChange(of: obs.state?.isFinished ?? false) { finished in
            if finished, let s = obs.state, !s.cards.isEmpty {
                goResult = true
            }
        }
        .onChange(of: obs.state?.checked ?? false) { checked in
            if checked && !tooltipDismissed && mode == .scheduled {
                tooltipVisible = true
            }
        }
    }

    @ViewBuilder
    private func emptyState(state s: StudyState) -> some View {
        VStack(spacing: 12) {
            Text(s.nothingDue ? "🌱" : "📭").font(.system(size: 64))
            Text(s.nothingDue ? "Всё на сегодня повторено" : "В этом пространстве пока нет карточек")
                .font(.headline)
                .multilineTextAlignment(.center)
            if s.nothingDue {
                Text("Возвращайтесь позже — слова всплывут, когда придёт время повторить")
                    .font(.subheadline)
                    .foregroundColor(AppPalette.textSecondary)
                    .multilineTextAlignment(.center)
                    .padding(.horizontal, 32)
            }
        }
    }

    @ViewBuilder
    private func content(state s: StudyState) -> some View {
        let card = s.currentCard!
        let source = direction == .nativeToTarget ? card.nativeWord : card.targetWord
        let expected = direction == .nativeToTarget ? card.targetWord : card.nativeWord

        VStack(spacing: 16) {
            ProgressView(value: Double(s.progress))
                .accentColor(AppPalette.primary)
            Text("\(s.currentIndex + 1) / \(s.total)")
                .font(.caption)
                .foregroundColor(AppPalette.textSecondary)
                .frame(maxWidth: .infinity, alignment: .leading)

            if tooltipVisible {
                SrsTooltipBanner {
                    tooltipVisible = false
                    tooltipDismissed = true
                    DI.preferences().setBoolean(
                        key: PrefKeys.shared.SRS_TOOLTIP_SEEN, value: true
                    )
                }
            }

            cardView(state: s, source: source, expected: expected)
                .animation(.easeInOut(duration: 0.3), value: s.currentIndex)

            TextField(
                "Ваш перевод",
                text: Binding(
                    get: { s.inputText },
                    set: { obs.viewModel.onInputChanged(text: $0) }
                )
            )
            .textFieldStyle(.roundedBorder)
            .disabled(s.checked)

            Spacer()

            Button {
                if s.checked { obs.viewModel.nextCard() } else { obs.viewModel.checkAnswer() }
            } label: {
                Text(s.checked ? "Следующая" : "Проверить")
                    .font(.headline)
                    .frame(maxWidth: .infinity)
                    .frame(height: 50)
                    .background(AppPalette.primary)
                    .foregroundColor(.white)
                    .clipShape(RoundedRectangle(cornerRadius: 12))
            }
            .disabled(!s.checked && s.inputText.isEmpty)
        }
        .padding(16)
    }

    @ViewBuilder
    private func cardView(state s: StudyState, source: String, expected: String) -> some View {
        VStack(spacing: 12) {
            Text(source)
                .font(.title.weight(.semibold))
                .multilineTextAlignment(.center)
            if let hint = s.currentCard?.hint, !hint.isEmpty {
                Text("💡 \(hint)")
                    .font(.caption)
                    .foregroundColor(AppPalette.textSecondary)
            }
            if s.checked {
                HStack {
                    Image(systemName: s.isCorrect ? "checkmark.circle.fill" : "xmark.circle.fill")
                        .foregroundColor(s.isCorrect ? AppPalette.primary : AppPalette.error)
                    Text(s.isCorrect ? "Верно!" : "Правильный ответ:")
                        .foregroundColor(s.isCorrect ? AppPalette.primary : AppPalette.error)
                }
                if !s.isCorrect {
                    Text(expected).font(.headline)
                }
                if let days = s.nextIntervalDays?.doubleValue {
                    SrsHint(
                        days: days,
                        prevStage: s.prevStage,
                        nextStage: s.nextStage
                    )
                    .padding(.top, 6)
                }
            }
        }
        .padding(24)
        .frame(maxWidth: .infinity)
        .background(AppPalette.surface)
        .clipShape(RoundedRectangle(cornerRadius: 16))
        .overlay(
            RoundedRectangle(cornerRadius: 16)
                .stroke(
                    s.checked
                        ? (s.isCorrect ? AppPalette.primary : AppPalette.error)
                        : .clear,
                    lineWidth: 1.5
                )
        )
    }
}

private struct SrsHint: View {
    let days: Double
    let prevStage: GardenStage?
    let nextStage: GardenStage?

    var body: some View {
        let grew = prevStage != nil && nextStage != nil && prevStage != nextStage
        HStack(spacing: 6) {
            if grew, let prev = prevStage, let next = nextStage {
                Text(prev.emoji)
                Text("→").foregroundColor(AppPalette.primary)
                Text(next.emoji)
            } else if let next = nextStage {
                Text(next.emoji)
            }
            Text(humanizeIntervalRu(days) + " снова")
                .font(.subheadline.weight(.medium))
                .foregroundColor(AppPalette.primary)
        }
        .padding(.horizontal, 12)
        .padding(.vertical, 8)
        .background(AppPalette.primary.opacity(0.12))
        .clipShape(RoundedRectangle(cornerRadius: 10))
    }
}

private struct SrsTooltipBanner: View {
    let onDismiss: () -> Void

    var body: some View {
        HStack(alignment: .top, spacing: 10) {
            Text("💡").font(.title3)
            VStack(alignment: .leading, spacing: 2) {
                Text("Так работает повторение")
                    .font(.subheadline.weight(.semibold))
                    .foregroundColor(AppPalette.primary)
                Text("Правильно ответили — слово вернётся через несколько дней. Так оно лучше запомнится надолго.")
                    .font(.caption)
                    .foregroundColor(AppPalette.primary)
            }
            Spacer()
            Button(action: onDismiss) {
                Image(systemName: "xmark")
                    .font(.caption)
                    .foregroundColor(AppPalette.primary)
                    .padding(8)
            }
            .buttonStyle(.plain)
        }
        .padding(.horizontal, 14)
        .padding(.vertical, 10)
        .background(AppPalette.primary.opacity(0.12))
        .clipShape(RoundedRectangle(cornerRadius: 12))
    }
}
