package com.transcard.desktop

import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import com.transcard.App
import com.transcard.di.initKoin

fun main() {
    initKoin()
    application {
        Window(
            onCloseRequest = ::exitApplication,
            title = "Sprout",
            state = rememberWindowState(width = 480.dp, height = 800.dp)
        ) {
            App()
        }
    }
}
