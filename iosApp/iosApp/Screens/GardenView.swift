import SwiftUI
import Shared

@MainActor
final class GardenObservable: ObservableObject {
    @Published var state: GardenUiState = GardenUiState(
        space: nil,
        totalCards: 0,
        byStage: [:]
    )
    let viewModel: GardenViewModel
    private var subscription: FlowSubscription?

    init(spaceId: Int64) {
        self.viewModel = DI.gardenViewModel(spaceId: spaceId)
        subscription = FlowSubscription(flow: viewModel.state) { [weak self] (s: GardenUiState) in
            self?.state = s
        }
    }
}

struct GardenView: View {
    let spaceId: Int64
    let title: String

    @StateObject private var obs: GardenObservable

    init(spaceId: Int64, title: String) {
        self.spaceId = spaceId
        self.title = title
        _obs = StateObject(wrappedValue: GardenObservable(spaceId: spaceId))
    }

    private let stageOrder: [GardenStage] = [.tree, .flower, .bush, .sprout, .seed]

    var body: some View {
        ZStack {
            AppPalette.background.ignoresSafeArea()
            if obs.state.totalCards == 0 {
                emptyState
            } else {
                ScrollView {
                    VStack(alignment: .leading, spacing: 16) {
                        StagesSummary(byStage: obs.state.byStage)
                        ForEach(stageOrder, id: \.self) { stage in
                            let cards = obs.state.byStage[stage] ?? []
                            if !cards.isEmpty {
                                StageSection(stage: stage, cards: cards)
                            }
                        }
                    }
                    .padding(16)
                }
            }
        }
        .navigationTitle("🌳 \(title)")
        .navigationBarTitleDisplayMode(.inline)
    }

    private var emptyState: some View {
        VStack(spacing: 12) {
            Text("🌰").font(.system(size: 64))
            Text("Здесь будет ваш сад")
                .font(.headline)
            Text("Каждое выученное слово прорастает и со временем превращается в дерево")
                .font(.subheadline)
                .foregroundColor(AppPalette.textSecondary)
                .multilineTextAlignment(.center)
                .padding(.horizontal, 32)
        }
        .padding(32)
    }
}

private struct StagesSummary: View {
    let byStage: [GardenStage: [Card]]

    private let order: [GardenStage] = [.seed, .sprout, .bush, .flower, .tree]

    var body: some View {
        HStack {
            ForEach(order, id: \.self) { stage in
                VStack(spacing: 4) {
                    Text(stage.emoji).font(.system(size: 30))
                    Text("\((byStage[stage] ?? []).count)")
                        .font(.headline)
                        .foregroundColor(AppPalette.textPrimary)
                }
                .frame(maxWidth: .infinity)
            }
        }
        .padding(.vertical, 16)
        .padding(.horizontal, 8)
        .background(AppPalette.surface)
        .clipShape(RoundedRectangle(cornerRadius: 16))
    }
}

private struct StageSection: View {
    let stage: GardenStage
    let cards: [Card]

    var body: some View {
        VStack(alignment: .leading, spacing: 10) {
            HStack(spacing: 8) {
                Text(stage.emoji).font(.title2)
                Text(stage.label)
                    .font(.headline)
                    .foregroundColor(AppPalette.textPrimary)
                Text("(\(cards.count))")
                    .font(.subheadline)
                    .foregroundColor(AppPalette.textSecondary)
            }
            FlowLayout(spacing: 8) {
                ForEach(cards, id: \.id) { card in
                    PlantChip(stage: stage, word: card.targetWord)
                }
            }
        }
    }
}

private struct PlantChip: View {
    let stage: GardenStage
    let word: String

    var body: some View {
        HStack(spacing: 6) {
            Text(stage.emoji)
            Text(word)
                .font(.subheadline)
                .foregroundColor(AppPalette.textPrimary)
        }
        .padding(.horizontal, 12)
        .padding(.vertical, 8)
        .background(AppPalette.surface)
        .clipShape(RoundedRectangle(cornerRadius: 10))
    }
}

/// Minimal flow layout for chips — wraps to new line as needed.
struct FlowLayout: Layout {
    var spacing: CGFloat = 8

    func sizeThatFits(proposal: ProposedViewSize, subviews: Subviews, cache: inout ()) -> CGSize {
        let width = proposal.width ?? .infinity
        var x: CGFloat = 0
        var y: CGFloat = 0
        var rowHeight: CGFloat = 0
        for s in subviews {
            let size = s.sizeThatFits(.unspecified)
            if x + size.width > width && x > 0 {
                x = 0
                y += rowHeight + spacing
                rowHeight = 0
            }
            x += size.width + spacing
            rowHeight = max(rowHeight, size.height)
        }
        return CGSize(width: width.isFinite ? width : x, height: y + rowHeight)
    }

    func placeSubviews(in bounds: CGRect, proposal: ProposedViewSize, subviews: Subviews, cache: inout ()) {
        var x = bounds.minX
        var y = bounds.minY
        var rowHeight: CGFloat = 0
        for s in subviews {
            let size = s.sizeThatFits(.unspecified)
            if x + size.width > bounds.maxX && x > bounds.minX {
                x = bounds.minX
                y += rowHeight + spacing
                rowHeight = 0
            }
            s.place(at: CGPoint(x: x, y: y), proposal: ProposedViewSize(size))
            x += size.width + spacing
            rowHeight = max(rowHeight, size.height)
        }
    }
}
