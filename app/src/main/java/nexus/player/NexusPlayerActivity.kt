package nexus.player

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent

class NexusPlayerActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            NexusPlayerRoute(
                url = "",
                title = "Nexus TV",
                onBack = { finish() }
            )
        }
    }
}
