package com.transcard

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import cafe.adriel.voyager.core.screen.Screen
import cafe.adriel.voyager.navigator.Navigator
import cafe.adriel.voyager.transitions.SlideTransition
import com.transcard.data.preferences.PrefKeys
import com.transcard.data.preferences.Preferences
import com.transcard.domain.repository.SyncRepository
import com.transcard.presentation.screen.SpaceListScreen
import com.transcard.presentation.screen.WelcomeScreen
import com.transcard.presentation.theme.TransCardTheme
import org.koin.compose.koinInject

@Composable
fun App() {
    TransCardTheme {
        val prefs: Preferences = koinInject()
        val syncRepository: SyncRepository = koinInject()
        val initialScreen: Screen = remember {
            if (prefs.getBoolean(PrefKeys.ONBOARDING_COMPLETE, false)) SpaceListScreen
            else WelcomeScreen
        }
        LaunchedEffect(Unit) {
            // Best-effort: при старте подтянуть свежие данные с сервера (если залогинены).
            // Не блокируем UI: ошибки уже отражены в SyncStatus, который UI может показать.
            syncRepository.pullOnStart()
        }
        Navigator(initialScreen) { navigator ->
            SlideTransition(navigator)
        }
    }
}
