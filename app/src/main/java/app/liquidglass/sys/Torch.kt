package app.liquidglass.sys

import android.content.Context
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager

/**
 * Torch control through the camera service. setTorchMode needs no CAMERA permission and no
 * preview stream, so the flashlight works without our app ever holding the camera.
 */
class Torch(context: Context) {

    private val manager = context.applicationContext
        .getSystemService(Context.CAMERA_SERVICE) as? CameraManager

    private var cameraId: String? = null
    private var resolved = false

    var isOn: Boolean = false
        private set

    private fun resolve(): String? {
        if (resolved) return cameraId
        resolved = true
        cameraId = runCatching {
            manager?.cameraIdList?.firstOrNull { id ->
                manager.getCameraCharacteristics(id)
                    .get(CameraCharacteristics.FLASH_INFO_AVAILABLE) == true
            }
        }.getOrNull()
        return cameraId
    }

    fun set(on: Boolean): Boolean {
        val id = resolve() ?: return false
        val ok = runCatching { manager?.setTorchMode(id, on) }.isSuccess
        if (ok) isOn = on
        return ok
    }

    fun toggle(): Boolean = set(!isOn)
}
