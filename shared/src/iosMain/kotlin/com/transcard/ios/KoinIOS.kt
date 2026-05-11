package com.transcard.ios

import com.transcard.data.preferences.Preferences
import com.transcard.di.initKoin as initKoinShared
import com.transcard.domain.model.StudyDirection
import com.transcard.domain.model.StudyMode
import com.transcard.presentation.viewmodel.CardListViewModel
import com.transcard.presentation.viewmodel.GardenViewModel
import com.transcard.presentation.viewmodel.SpaceListViewModel
import com.transcard.presentation.viewmodel.StudyViewModel
import org.koin.core.Koin
import org.koin.core.parameter.parametersOf

object KoinHelper {
    val shared: KoinHelper get() = this

    fun start() {
        initKoinShared()
    }

    val koin: Koin get() = org.koin.core.context.GlobalContext.get()

    fun getSpaceListViewModel(): SpaceListViewModel = koin.get()

    fun getCardListViewModel(spaceId: Long): CardListViewModel =
        koin.get { parametersOf(spaceId) }

    fun getStudyViewModel(
        spaceId: Long,
        direction: StudyDirection,
        mode: StudyMode
    ): StudyViewModel = koin.get { parametersOf(spaceId, direction, mode) }

    fun getGardenViewModel(spaceId: Long): GardenViewModel =
        koin.get { parametersOf(spaceId) }

    fun getPreferences(): Preferences = koin.get()
}
