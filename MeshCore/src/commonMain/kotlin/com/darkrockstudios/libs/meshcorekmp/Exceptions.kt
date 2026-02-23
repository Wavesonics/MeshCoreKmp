package com.darkrockstudios.libs.meshcorekmp

import com.darkrockstudios.libs.meshcorekmp.protocol.ErrorCode

sealed class MeshCoreException(message: String, cause: Throwable? = null) : Exception(message, cause) {
	class ConnectionFailed(message: String, cause: Throwable? = null) :
		MeshCoreException(message, cause)

	class CommandTimeout(message: String) :
		MeshCoreException(message)

	class DeviceError(val errorCode: Int, message: String = ErrorCode.describe(errorCode)) :
		MeshCoreException(message)

	class ProtocolError(message: String) :
		MeshCoreException(message)

	class NotConnected(message: String = "Not connected to device") :
		MeshCoreException(message)

	class UnexpectedResponse(message: String) :
		MeshCoreException(message)
}
