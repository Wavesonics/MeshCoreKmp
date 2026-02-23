package com.darkrockstudios.libs.meshcorekmp.model

import kotlinx.serialization.Serializable

@Serializable
data class Advertisement(
	val publicKey: String,
	val timestamp: Long,
	val name: String? = null,
	val latitude: Double? = null,
	val longitude: Double? = null,
	val nodeType: NodeType = NodeType.UNKNOWN,
) {
	@Serializable
	enum class NodeType {
		UNKNOWN, CHAT_NODE, REPEATER, ROOM_SERVER, SENSOR
	}
}
