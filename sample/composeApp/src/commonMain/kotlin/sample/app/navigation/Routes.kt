package sample.app.navigation

import androidx.navigation3.runtime.NavKey
import kotlinx.serialization.Serializable

@Serializable
sealed interface Route : NavKey

@Serializable
data object ScanRoute : Route

@Serializable
data class ConnectedRoute(val deviceId: String) : Route

@Serializable
data object ChannelsRoute : Route

@Serializable
data class ChannelRoute(val channelIndex: Int, val channelName: String) : Route

@Serializable
data object ContactsRoute : Route

@Serializable
data class DirectMessageRoute(val contactName: String, val publicKeyHex: String) : Route
