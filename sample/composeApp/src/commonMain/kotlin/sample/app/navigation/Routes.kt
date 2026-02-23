package sample.app.navigation

import androidx.navigation3.runtime.NavKey
import kotlinx.serialization.Serializable

@Serializable
sealed interface Route : NavKey

@Serializable
data object ScanRoute : Route

@Serializable
data class ConnectedRoute(val deviceId: String) : Route
