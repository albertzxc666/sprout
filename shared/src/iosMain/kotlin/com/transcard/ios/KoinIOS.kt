package com.transcard.ios

import com.transcard.data.api.YandexDictionaryApi
import com.transcard.data.preferences.Preferences
import com.transcard.di.initKoin as initKoinShared
import com.transcard.domain.model.StudyDirection
import com.transcard.domain.model.StudyMode
import com.transcard.domain.model.StudyScope
import com.transcard.presentation.viewmodel.CardListViewModel
import com.transcard.presentation.viewmodel.GardenViewModel
import com.transcard.presentation.viewmodel.GroupListViewModel
import com.transcard.presentation.viewmodel.SpaceListViewModel
import com.transcard.presentation.viewmodel.StudyViewModel
import org.koin.core.Koin
import org.koin.core.parameter.parametersOf

object KoinHelper {
    val shared: KoinHelper get() = this

    private lateinit var koinInstance: Koin

    fun start() {
        koinInstance = initKoinShared().koin
    }

    val koin: Koin get() = koinInstance

    fun getSpaceListViewModel(): SpaceListViewModel = koin.get()

    fun getGroupListViewModel(spaceId: Long): GroupListViewModel =
        koin.get { parametersOf(spaceId) }

    fun getCardListViewModel(groupId: Long): CardListViewModel =
        koin.get { parametersOf(groupId) }

    fun getStudyViewModel(
        scope: StudyScope,
        direction: StudyDirection,
        mode: StudyMode
    ): StudyViewModel = koin.get { parametersOf(scope, direction, mode) }

    fun getGardenViewModel(spaceId: Long): GardenViewModel =
        koin.get { parametersOf(spaceId) }

    fun getPreferences(): Preferences = koin.get()

    fun isYandexConfigured(): Boolean = koin.get<YandexDictionaryApi>().isConfigured
}
