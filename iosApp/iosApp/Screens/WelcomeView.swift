import SwiftUI
import Shared

struct WelcomeView: View {
    var onFinished: () -> Void

    var body: some View {
        ZStack {
            AppPalette.background.ignoresSafeArea()
            VStack(spacing: 0) {
                Spacer().frame(height: 40)
                Text("🌱").font(.system(size: 64))
                Spacer().frame(height: 16)
                Text("Sprout")
                    .font(.system(size: 34, weight: .bold))
                    .foregroundColor(AppPalette.textPrimary)
                Spacer().frame(height: 8)
                Text("Слова, которые растут с вами")
                    .font(.body)
                    .foregroundColor(AppPalette.textSecondary)
                    .multilineTextAlignment(.center)

                Spacer().frame(height: 48)

                VStack(alignment: .leading, spacing: 20) {
                    FeatureRow(
                        emoji: "📚",
                        title: "Пространства и карточки",
                        subtitle: "Создавайте пространство для каждого языка и наполняйте его карточками — парами слов на двух языках"
                    )
                    FeatureRow(
                        emoji: "📖",
                        title: "Учим по интервалам",
                        subtitle: "Каждое слово возвращается ровно тогда, когда его пора повторить — память работает лучше"
                    )
                    FeatureRow(
                        emoji: "🌱",
                        title: "Сад слов",
                        subtitle: "Выученные слова прорастают и со временем превращаются в деревья"
                    )
                }
                .frame(maxWidth: 360)

                Spacer()

                Text("🔒 Всё локально — никаких аккаунтов и подписок")
                    .font(.footnote)
                    .foregroundColor(AppPalette.textSecondary)
                    .multilineTextAlignment(.center)
                Spacer().frame(height: 14)

                Button(action: onFinished) {
                    Text("Начать")
                        .font(.headline)
                        .frame(maxWidth: .infinity)
                        .frame(height: 52)
                        .background(AppPalette.primary)
                        .foregroundColor(.white)
                        .clipShape(RoundedRectangle(cornerRadius: 12))
                }
                .frame(maxWidth: 360)
            }
            .padding(.horizontal, 32)
            .padding(.vertical, 48)
        }
    }
}

private struct FeatureRow: View {
    let emoji: String
    let title: String
    let subtitle: String

    var body: some View {
        HStack(alignment: .top, spacing: 14) {
            ZStack {
                RoundedRectangle(cornerRadius: 10)
                    .fill(AppPalette.primary.opacity(0.12))
                    .frame(width: 44, height: 44)
                Text(emoji).font(.title2)
            }
            VStack(alignment: .leading, spacing: 2) {
                Text(title)
                    .font(.headline)
                    .foregroundColor(AppPalette.textPrimary)
                Text(subtitle)
                    .font(.subheadline)
                    .foregroundColor(AppPalette.textSecondary)
            }
        }
    }
}
