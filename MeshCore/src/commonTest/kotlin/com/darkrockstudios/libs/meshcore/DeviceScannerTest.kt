@file:OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)

package com.darkrockstudios.libs.meshcore

import com.darkrockstudios.libs.meshcore.ble.DiscoveredDevice
import com.darkrockstudios.libs.meshcore.ble.ScanFilter
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class DeviceScannerTest {

	@Test
	fun isBluetoothEnabled_delegatesToAdapter() {
		val adapter = FakeBleAdapter()
		val scanner = DeviceScanner(adapter)

		adapter.isBluetoothEnabled = true
		assertTrue(scanner.isBluetoothEnabled)

		adapter.isBluetoothEnabled = false
		assertFalse(scanner.isBluetoothEnabled)
	}

	@Test
	fun startScan_startsAdapter() = runTest {
		val adapter = FakeBleAdapter()
		val scanner = DeviceScanner(adapter)

		scanner.startScan(scope = backgroundScope)
		// scan() is called synchronously before launching coroutine
		assertTrue(adapter.scanStarted)
		scanner.stopScan()
	}

	@Test
	fun startScan_accumulatesDevices() = runTest(UnconfinedTestDispatcher()) {
		val adapter = FakeBleAdapter()
		val scanner = DeviceScanner(adapter)

		scanner.startScan(scope = backgroundScope)

		adapter.emitScanResult(DiscoveredDevice("dev1", "Radio A", -50))
		assertEquals(1, scanner.discoveredDevices.value.size, "Expected 1 device after first emit")
		assertEquals("Radio A", scanner.discoveredDevices.value[0].name)

		adapter.emitScanResult(DiscoveredDevice("dev2", "Radio B", -70))
		assertEquals(2, scanner.discoveredDevices.value.size, "Expected 2 devices after second emit")

		scanner.stopScan()
	}

	@Test
	fun startScan_updatesExistingDevice() = runTest(UnconfinedTestDispatcher()) {
		val adapter = FakeBleAdapter()
		val scanner = DeviceScanner(adapter)

		scanner.startScan(scope = backgroundScope)

		adapter.emitScanResult(DiscoveredDevice("dev1", "Radio A", -50))
		assertEquals(1, scanner.discoveredDevices.value.size)
		assertEquals(-50, scanner.discoveredDevices.value[0].rssi)

		adapter.emitScanResult(DiscoveredDevice("dev1", "Radio A", -30))
		assertEquals(1, scanner.discoveredDevices.value.size)
		assertEquals(-30, scanner.discoveredDevices.value[0].rssi)

		scanner.stopScan()
	}

	@Test
	fun stopScan_stopsAdapter() = runTest {
		val adapter = FakeBleAdapter()
		val scanner = DeviceScanner(adapter)

		scanner.startScan(scope = backgroundScope)
		scanner.stopScan()
		assertTrue(adapter.scanStopped)
	}

	@Test
	fun startScan_clearsPreviousResults() = runTest(UnconfinedTestDispatcher()) {
		val adapter = FakeBleAdapter()
		val scanner = DeviceScanner(adapter)

		scanner.startScan(scope = backgroundScope)
		adapter.emitScanResult(DiscoveredDevice("dev1", "Radio A", -50))
		assertEquals(1, scanner.discoveredDevices.value.size)

		// Start new scan - should clear (discoveredDevices is set synchronously)
		scanner.startScan(scope = backgroundScope)
		assertEquals(0, scanner.discoveredDevices.value.size)
		scanner.stopScan()
	}

	@Test
	fun startScan_withFilter() = runTest {
		val adapter = FakeBleAdapter()
		val scanner = DeviceScanner(adapter)

		val filter = ScanFilter(namePrefix = "MeshCore")
		scanner.startScan(filter = filter, scope = backgroundScope)
		assertTrue(adapter.scanStarted)
		scanner.stopScan()
	}
}
