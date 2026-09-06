package world.taqwa.app

import androidx.compose.ui.window.ComposeUIViewController
import platform.UIKit.UIViewController
import world.taqwa.app.di.appContainer

fun MainViewController(): UIViewController = ComposeUIViewController { App(appContainer) }
