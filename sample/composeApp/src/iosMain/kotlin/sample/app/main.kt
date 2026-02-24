import androidx.compose.ui.window.ComposeUIViewController
import com.darkrockstudios.libs.meshcore.ble.BlueFalconBleAdapter
import dev.bluefalcon.BlueFalcon
import platform.UIKit.UIApplication
import platform.UIKit.UIViewController
import sample.app.App

fun MainViewController(): UIViewController = ComposeUIViewController {
	val blueFalcon = BlueFalcon(context = UIApplication.sharedApplication)
	App(BlueFalconBleAdapter(blueFalcon))
}
