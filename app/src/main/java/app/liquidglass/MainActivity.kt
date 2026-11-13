package app.liquidglass

import android.graphics.Color
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import app.liquidglass.ui.LiquidGlassApp

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(Color.TRANSPARENT)
        )

        setContent {
            LiquidGlassApp(
                onBrightness = ::applyBrightness
            )
        }
    }

    /**
     * Brightness is applied to this window only: adjusting the system-wide brightness needs
     * WRITE_SETTINGS, and skipping it keeps the app permission-light.
     */
    private fun applyBrightness(value: Float) {
        window.attributes = window.attributes.apply {
            screenBrightness = value.coerceIn(0.02f, 1f)
        }
    }
}
