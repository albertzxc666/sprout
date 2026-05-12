import SwiftUI
import Shared

struct StudySetupView: View {
    let scope: StudyScope
    @State private var direction: StudyDirection = .nativeToTarget
    @State private var mode: StudyMode = .scheduled
    @State private var startNow = false

    var body: some View {
        ZStack {
            AppPalette.background.ignoresSafeArea()
            VStack(alignment: .leading, spacing: 16) {
                Text("Режим")
                    .font(.headline)
                    .padding(.top, 8)

                VStack(spacing: 0) {
                    OptionRow(
                        title: "🌱 Расписание",
                        subtitle: "Только слова, которые пора повторить. Сад растёт, серия дней идёт.",
                        selected: mode == .scheduled,
                        onSelect: { mode = .scheduled }
                    )
                    Divider().padding(.leading, 16)
                    OptionRow(
                        title: "🔁 Тренажёр",
                        subtitle: "Все слова в случайном порядке. Без интервалов — для быстрой прокачки.",
                        selected: mode == .drill,
                        onSelect: { mode = .drill }
                    )
                }
                .background(AppPalette.surface)
                .clipShape(RoundedRectangle(cornerRadius: 16))

                Text("Направление перевода")
                    .font(.headline)

                VStack(spacing: 0) {
                    OptionRow(
                        title: "Родной → Изучаемый",
                        subtitle: "Видите слово на родном языке, вводите перевод",
                        selected: direction == .nativeToTarget,
                        onSelect: { direction = .nativeToTarget }
                    )
                    Divider().padding(.leading, 16)
                    OptionRow(
                        title: "Изучаемый → Родной",
                        subtitle: "Видите слово на изучаемом языке, вводите перевод",
                        selected: direction == .targetToNative,
                        onSelect: { direction = .targetToNative }
                    )
                }
                .background(AppPalette.surface)
                .clipShape(RoundedRectangle(cornerRadius: 16))

                NavigationLink(
                    destination: StudyView(scope: scope, direction: direction, mode: mode),
                    isActive: $startNow
                ) { EmptyView() }

                Button {
                    startNow = true
                } label: {
                    Text("Начать")
                        .font(.headline)
                        .frame(maxWidth: .infinity)
                        .frame(height: 50)
                        .background(AppPalette.primary)
                        .foregroundColor(.white)
                        .clipShape(RoundedRectangle(cornerRadius: 12))
                }

                Spacer()
            }
            .padding(16)
        }
        .navigationTitle("Настройка сессии")
        .navigationBarTitleDisplayMode(.inline)
    }
}

struct OptionRow: View {
    let title: String
    let subtitle: String
    let selected: Bool
    let onSelect: () -> Void

    var body: some View {
        Button(action: onSelect) {
            HStack(alignment: .top, spacing: 12) {
                Image(systemName: selected ? "largecircle.fill.circle" : "circle")
                    .foregroundColor(selected ? AppPalette.primary : AppPalette.textSecondary)
                VStack(alignment: .leading, spacing: 4) {
                    Text(title).foregroundColor(AppPalette.textPrimary)
                    Text(subtitle)
                        .font(.caption)
                        .foregroundColor(AppPalette.textSecondary)
                }
                Spacer()
            }
            .padding(16)
        }
        .buttonStyle(.plain)
    }
}
