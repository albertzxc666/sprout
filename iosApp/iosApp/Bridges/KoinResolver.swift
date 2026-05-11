import Foundation
import Shared

/// Thin facade over Kotlin Koin: every shared ViewModel is resolved here.
enum DI {
    static func spaceListViewModel() -> SpaceListViewModel {
        return KoinHelper.shared.getSpaceListViewModel()
    }

    static func cardListViewModel(spaceId: Int64) -> CardListViewModel {
        return KoinHelper.shared.getCardListViewModel(spaceId: spaceId)
    }

    static func studyViewModel(
        spaceId: Int64,
        direction: StudyDirection,
        mode: StudyMode
    ) -> StudyViewModel {
        return KoinHelper.shared.getStudyViewModel(
            spaceId: spaceId,
            direction: direction,
            mode: mode
        )
    }

    static func gardenViewModel(spaceId: Int64) -> GardenViewModel {
        return KoinHelper.shared.getGardenViewModel(spaceId: spaceId)
    }

    static func preferences() -> Preferences {
        return KoinHelper.shared.getPreferences()
    }
}
