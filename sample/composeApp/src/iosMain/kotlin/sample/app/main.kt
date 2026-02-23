import androidx.compose.ui.window.ComposeUIViewController
import com.darkrockstudios.libs.meshcorekmp.ble.IosBleAdapter
import platform.UIKit.UIViewController
import sample.app.App

fun MainViewController(): UIViewController = ComposeUIViewController {
	App(IosBleAdapter())
}
