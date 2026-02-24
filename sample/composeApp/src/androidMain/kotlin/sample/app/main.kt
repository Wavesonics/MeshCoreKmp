package sample.app

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import com.darkrockstudios.libs.meshcore.ble.BlueFalconBleAdapter
import dev.bluefalcon.BlueFalcon
import io.github.aakira.napier.DebugAntilog
import io.github.aakira.napier.Napier

class AppActivity : ComponentActivity() {
	private lateinit var bleAdapter: BlueFalconBleAdapter

	private val permissionLauncher = registerForActivityResult(
		ActivityResultContracts.RequestMultiplePermissions()
	) { results ->
		val allGranted = results.values.all { it }
		if (allGranted) {
			showApp()
		} else {
			showApp() // Show app anyway; scanner will show bluetooth disabled state
		}
	}

	override fun onCreate(savedInstanceState: Bundle?) {
		super.onCreate(savedInstanceState)
		enableEdgeToEdge()
		Napier.base(DebugAntilog())
		val blueFalcon = BlueFalcon(context = application)
		bleAdapter = BlueFalconBleAdapter(blueFalcon)

		if (hasRequiredPermissions()) {
			showApp()
		} else {
			requestBlePermissions()
		}
	}

	private fun showApp() {
		setContent { App(bleAdapter) }
	}

	private fun hasRequiredPermissions(): Boolean {
		val permissions = getRequiredPermissions()
		return permissions.all {
			ContextCompat.checkSelfPermission(this, it) == PackageManager.PERMISSION_GRANTED
		}
	}

	private fun getRequiredPermissions(): List<String> {
		return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
			listOf(
				Manifest.permission.BLUETOOTH_SCAN,
				Manifest.permission.BLUETOOTH_CONNECT,
				Manifest.permission.ACCESS_FINE_LOCATION,
			)
		} else {
			listOf(
				Manifest.permission.ACCESS_FINE_LOCATION,
			)
		}
	}

	private fun requestBlePermissions() {
		permissionLauncher.launch(getRequiredPermissions().toTypedArray())
	}
}
