package com.transcard.di

import com.transcard.data.api.YandexDictionaryApi
import com.transcard.data.api.createHttpClient
import com.transcard.data.db.createDatabase
import com.transcard.data.remote.SproutApi
import com.transcard.data.repository.AuthRepositoryImpl
import com.transcard.data.repository.CardGroupRepositoryImpl
import com.transcard.data.repository.CardRepositoryImpl
import com.transcard.data.repository.ProgressRepositoryImpl
import com.transcard.data.repository.SpaceRepositoryImpl
import com.transcard.data.repository.TranslationRepositoryImpl
import com.transcard.data.storage.TokenStorage
import com.transcard.data.translation.LocalDictionary
import com.transcard.domain.model.StudyDirection
import com.transcard.domain.model.StudyMode
import com.transcard.domain.model.StudyScope
import com.transcard.domain.repository.AuthRepository
import com.transcard.domain.repository.CardGroupRepository
import com.transcard.domain.repository.CardRepository
import com.transcard.domain.repository.ProgressRepository
import com.transcard.domain.repository.SpaceRepository
import com.transcard.domain.repository.TranslationRepository
import com.transcard.domain.usecase.CheckAnswerUseCase
import com.transcard.domain.usecase.GetSpaceStatsUseCase
import com.transcard.domain.usecase.GetStudyCardsUseCase
import com.transcard.domain.usecase.ReviewCardUseCase
import com.transcard.presentation.viewmodel.CardListViewModel
import com.transcard.presentation.viewmodel.GardenViewModel
import com.transcard.presentation.viewmodel.GroupListViewModel
import com.transcard.presentation.viewmodel.SpaceListViewModel
import com.transcard.presentation.viewmodel.StudyViewModel
import org.koin.core.context.startKoin
import org.koin.core.module.dsl.factoryOf
import org.koin.dsl.KoinAppDeclaration
import org.koin.dsl.module

val sharedModule = module {
    single { createDatabase(get()) }

    single<SpaceRepository> { SpaceRepositoryImpl(get()) }
    single<CardGroupRepository> { CardGroupRepositoryImpl(get()) }
    single<CardRepository> { CardRepositoryImpl(get()) }
    single<ProgressRepository> { ProgressRepositoryImpl(get()) }

    single { createHttpClient() }
    single { YandexDictionaryApi(get()) }

    single { LocalDictionary() }
    single<TranslationRepository> { TranslationRepositoryImpl(get(), get()) }

    // sprout-server: secure token storage + Ktor wrapper + auth repo.
    // platformModule() даёт Settings (Keychain / EncryptedSharedPreferences / JvmPreferences).
    single { TokenStorage(get()) }
    single { SproutApi(tokenStorage = get(), httpClientFactory = ::createHttpClient) }
    single<AuthRepository> { AuthRepositoryImpl(get(), get()) }

    factoryOf(::GetStudyCardsUseCase)
    factoryOf(::CheckAnswerUseCase)
    factoryOf(::GetSpaceStatsUseCase)
    factoryOf(::ReviewCardUseCase)

    factory { SpaceListViewModel(get(), get(), get()) }
    factory { params -> GroupListViewModel(params.get<Long>(), get(), get(), get()) }
    factory { params -> CardListViewModel(params.get<Long>(), get(), get(), get(), get()) }
    factory { params -> GardenViewModel(params.get<Long>(), get(), get()) }
    factory { params ->
        StudyViewModel(
            params.get<StudyScope>(),
            params.get<StudyDirection>(),
            params.get<StudyMode>(),
            get(), get(), get()
        )
    }
}

fun initKoin(appDeclaration: KoinAppDeclaration = {}) =
    startKoin {
        appDeclaration()
        modules(platformModule(), sharedModule)
    }
