package world.taqwa.app

import androidx.compose.ui.window.ComposeUIViewController
import platform.UIKit.UIViewController
import world.taqwa.app.crash.crashLogStore
import world.taqwa.app.crash.installCrashHandler
import world.taqwa.app.di.appContainer

fun MainViewController(): UIViewController {
    // Before the container exists, so that a crash while it is built is already recorded.
    installCrashHandler(crashLogStore)
    return ComposeUIViewController { App(appContainer) }
}
