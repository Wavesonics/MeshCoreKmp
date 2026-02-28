package com.darkrockstudios.libs.meshcore.model

import kotlinx.serialization.Serializable
import kotlin.math.roundToInt

private const val LIPO_MIN_MV = 3000
private const val LIPO_MAX_MV = 4200

@Serializable
data class BatteryInfo(
	val milliVolts: Int,
	val levelPercent: Int = milliVoltsToPercent(milliVolts),
	val usedStorageKb: Int? = null,
	val totalStorageKb: Int? = null,
)

private fun milliVoltsToPercent(mv: Int): Int {
	val clamped = mv.coerceIn(LIPO_MIN_MV, LIPO_MAX_MV)
	return ((clamped - LIPO_MIN_MV).toDouble() / (LIPO_MAX_MV - LIPO_MIN_MV) * 100)
		.roundToInt()
}
