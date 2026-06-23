package tw.com.johnnyhng.eztalk.asr.screens

import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity

/** Debug-only host that keeps connected Compose tests visible on unattended devices. */
class ComposeTestActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
    }
}
