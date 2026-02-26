package com.darkrockstudios.libs.meshcore

import android.Manifest
import android.app.Application
import android.os.Build
import android.util.Log
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.rule.GrantPermissionRule
import com.darkrockstudios.libs.meshcore.ble.BlueFalconBleAdapter
import dev.bluefalcon.BlueFalcon
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import org.junit.After
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.seconds

/**
 * Integration test that connects to a real MeshCore BLE device.
 *
 * Requirements:
 * - Must run on a real Android device (not emulator)
 * - A MeshCore radio must be powered on and nearby
 *
 * Run with:
 * ./gradlew :MeshCore:connectedDebugAndroidTest --tests "*.BleIntegrationTest"
 */
@RunWith(AndroidJUnit4::class)
class BleIntegrationTest {

	@get:Rule
	val permissionRule: GrantPermissionRule = GrantPermissionRule.grant(
		*buildList {
			add(Manifest.permission.ACCESS_FINE_LOCATION)
			if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
				add(Manifest.permission.BLUETOOTH_SCAN)
				add(Manifest.permission.BLUETOOTH_CONNECT)
			}
		}.toTypedArray()
	)

	private var scope: CoroutineScope? = null
	private var connection: DeviceConnection? = null

	@After
	fun tearDown() {
		runBlocking {
			try {
				connection?.disconnect()
			} catch (_: Exception) {
			}
		}
		scope?.cancel()
	}

	@Test
	fun connectAndRunBasicCommands() = runBlocking {
		val app = InstrumentationRegistry.getInstrumentation().targetContext.applicationContext as Application
		val blueFalcon = BlueFalcon(context = app)
		val adapter = BlueFalconBleAdapter(blueFalcon)
		val scanner = DeviceScanner(adapter)

		scope = CoroutineScope(Dispatchers.Default + SupervisorJob())
		val testScope = scope!!

		// Scan for a device
		Log.i(TAG, "Starting BLE scan...")
		scanner.startScan(scope = testScope)
		val device = withTimeout(30.seconds) {
			scanner.discoveredDevices.first { it.isNotEmpty() }
		}.first()
		scanner.stopScan()
		Log.i(TAG, "Found device: ${device.name} (${device.identifier})")

		// Connect
		Log.i(TAG, "Connecting...")
		val config = ConnectionConfig(
			autoFetchChannels = false,
			autoFetchContacts = false,
			autoPollMessages = false,
		)
		connection = scanner.connect(device, testScope, config)
		Log.i(TAG, "Connected and initialized successfully")

		// Get channels with retry
		Log.i(TAG, "Getting channels...")
		val channels = withRetry("getAllChannels") { connection!!.getAllChannels() }
		Log.i(TAG, "Got ${channels.size} channels")
		assertTrue(channels.isNotEmpty(), "Expected at least one channel")

		// Get contacts with retry
		Log.i(TAG, "Getting contacts...")
		val contacts = withRetry("getContacts") { connection!!.getContacts() }
		Log.i(TAG, "Got ${contacts.size} contacts")
		assertTrue(contacts.isNotEmpty(), "Expected at least one contact")

		// Get battery with retry
		Log.i(TAG, "Getting battery...")
		val battery = withRetry("getBattery") { connection!!.getBattery() }
		Log.i(TAG, "Battery: ${battery.levelPercent}%")
		assertTrue(battery.levelPercent >= 0, "Battery level should be >= 0")
	}

	private suspend fun <T> withRetry(
		label: String,
		attempts: Int = 3,
		delayBetween: kotlin.time.Duration = 2.seconds,
		block: suspend () -> T,
	): T {
		var lastException: Exception? = null
		repeat(attempts) { attempt ->
			try {
				return block()
			} catch (e: Exception) {
				lastException = e
				Log.w(TAG, "$label attempt ${attempt + 1}/$attempts failed: ${e.message}")
				if (attempt < attempts - 1) {
					delay(delayBetween)
				}
			}
		}
		throw lastException!!
	}

	companion object {
		private const val TAG = "BleIntegrationTest"
	}
}
