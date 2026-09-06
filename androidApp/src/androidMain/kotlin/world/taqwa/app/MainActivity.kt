package world.taqwa.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import world.taqwa.app.di.appContainer
import world.taqwa.app.settings.appContext

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Assigned before the container is first touched: createDataStore() resolves its path
        // from this context.
        appContext = applicationContext
        enableEdgeToEdge()
        setContent {
            App(appContainer)
        }
    }
}
