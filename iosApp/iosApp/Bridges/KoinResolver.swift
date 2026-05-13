import Foundation
import Shared

/// Thin facade over Kotlin Koin: every shared ViewModel is resolved here.
enum DI {
    static func spaceListViewModel() -> SpaceListViewModel {
        return KoinHelper.shared.getSpaceListViewModel()
    }

    static func groupListViewModel(spaceId: Int64) -> GroupListViewModel {
        return KoinHelper.shared.getGroupListViewModel(spaceId: spaceId)
    }

    static func cardListViewModel(groupId: Int64) -> CardListViewModel {
        return KoinHelper.shared.getCardListViewModel(groupId: groupId)
    }

    static func studyViewModel(
        scope: StudyScope,
        direction: StudyDirection,
        mode: StudyMode
    ) -> StudyViewModel {
        return KoinHelper.shared.getStudyViewModel(
            scope: scope,
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

    static func loginViewModel() -> LoginViewModel {
        return KoinHelper.shared.getLoginViewModel()
    }

    static func registerViewModel() -> RegisterViewModel {
        return KoinHelper.shared.getRegisterViewModel()
    }

    static func accountViewModel() -> AccountViewModel {
        return KoinHelper.shared.getAccountViewModel()
    }

    static func postLoginRestoreViewModel() -> PostLoginRestoreViewModel {
        return KoinHelper.shared.getPostLoginRestoreViewModel()
    }
}
