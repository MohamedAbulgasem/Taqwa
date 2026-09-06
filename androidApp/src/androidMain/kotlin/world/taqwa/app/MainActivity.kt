package world.taqwa.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import world.taqwa.app.di.appContainer

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // appContext is now assigned in TaqwaApplication.onCreate, which runs before this on
        // every launch path, including a cold boot receiver.
        enableEdgeToEdge()
        setContent {
            App(appContainer)
        }
    }
}
