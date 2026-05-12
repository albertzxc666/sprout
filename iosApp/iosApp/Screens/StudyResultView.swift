import SwiftUI
import Shared

struct StudyResultView: View {
    let scope: StudyScope
    let direction: StudyDirection
    let mode: StudyMode
    let correct: Int
    let total: Int
    @Environment(\.dismiss) private var dismiss
    @EnvironmentObject private var router: AppRouter

    private var percent: Int {
        total == 0 ? 0 : Int((Double(correct) / Double(total)) * 100.0)
    }

    private var message: String {
        switch percent {
        case 90...: return "Отлично! 🎉"
        case 70..<90: return "Хороший результат"
        case 50..<70: return "Неплохо, есть куда расти"
        default: return "Стоит повторить"
        }
    }

    var body: some View {
        ZStack {
            AppPalette.background.ignoresSafeArea()
            VStack(spacing: 24) {
                Image(systemName: "trophy.fill")
                    .font(.system(size: 72))
                    .foregroundColor(AppPalette.primary)
                Text(message).font(.title2.weight(.semibold))
                VStack(spacing: 4) {
                    Text("\(correct) из \(total)")
                        .font(.title)
                        .foregroundColor(AppPalette.primary)
                    Text("\(percent)% правильных")
                        .font(.subheadline)
                        .foregroundColor(AppPalette.textSecondary)
                }
                .padding(20)
                .frame(maxWidth: .infinity)
                .background(AppPalette.surface)
                .clipShape(RoundedRectangle(cornerRadius: 16))

                NavigationLink {
                    StudyView(scope: scope, direction: direction, mode: mode)
                } label: {
                    Text("Учить снова")
                        .frame(maxWidth: .infinity)
                        .padding()
                        .background(AppPalette.primary)
                        .foregroundColor(.white)
                        .clipShape(RoundedRectangle(cornerRadius: 12))
                }

                Button {
                    router.popToRoot()
                } label: {
                    Text("На главную")
                        .frame(maxWidth: .infinity)
                        .padding()
                        .overlay(
                            RoundedRectangle(cornerRadius: 12)
                                .stroke(AppPalette.primary)
                        )
                        .foregroundColor(AppPalette.primary)
                }
            }
            .padding(24)
        }
        .navigationTitle("Итоги")
        .navigationBarTitleDisplayMode(.inline)
    }
}

