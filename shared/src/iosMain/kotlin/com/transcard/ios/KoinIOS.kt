package com.transcard.ios

import com.transcard.data.api.YandexDictionaryApi
import com.transcard.data.preferences.Preferences
import com.transcard.data.storage.TokenStorage
import com.transcard.di.initKoin as initKoinShared
import com.transcard.domain.model.StudyDirection
import com.transcard.domain.model.StudyMode
import com.transcard.domain.model.StudyScope
import com.transcard.presentation.viewmodel.AccountViewModel
import com.transcard.presentation.viewmodel.CardListViewModel
import com.transcard.presentation.viewmodel.GardenViewModel
import com.transcard.presentation.viewmodel.GroupListViewModel
import com.transcard.presentation.viewmodel.LoginViewModel
import com.transcard.presentation.viewmodel.PostLoginRestoreViewModel
import com.transcard.presentation.viewmodel.RegisterViewModel
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

    /**
     * Чистит токены из Keychain. Нужно для случая «удалили приложение → переустановили»:
     * на iOS Keychain переживает удаление приложения, поэтому свежий установ
     * увидел бы старые токены и автоматически залогинился без ведома пользователя.
     */
    fun clearTokens() { koin.get<TokenStorage>().clear() }

    fun getLoginViewModel(): LoginViewModel = koin.get()
    fun getRegisterViewModel(): RegisterViewModel = koin.get()
    fun getAccountViewModel(): AccountViewModel = koin.get()
    fun getPostLoginRestoreViewModel(): PostLoginRestoreViewModel = koin.get()

    fun isYandexConfigured(): Boolean = koin.get<YandexDictionaryApi>().isConfigured
}
