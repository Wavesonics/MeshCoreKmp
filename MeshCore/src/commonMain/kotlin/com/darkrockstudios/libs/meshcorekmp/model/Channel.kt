package com.darkrockstudios.libs.meshcorekmp.model

import kotlinx.serialization.Serializable

@Serializable
data class Channel(
	val index: Int,
	val name: String,
) {
	val isEmpty: Boolean get() = name.isBlank()
}
