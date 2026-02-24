package com.darkrockstudios.libs.meshcore.model

import kotlinx.serialization.Serializable

@Serializable
data class MessageSentConfirmation(
	val messageType: Int,
	val expectedAck: String,
	val suggestedTimeoutSeconds: Int,
)
