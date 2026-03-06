package sample.app.screen

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.darkrockstudios.libs.meshcore.DeviceConnection
import com.darkrockstudios.libs.meshcore.model.ReceivedMessage
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DirectMessageScreen(
	contactName: String,
	publicKeyHex: String,
	connection: DeviceConnection,
	onBack: () -> Unit,
) {
	val scope = rememberCoroutineScope()
	var messageText by remember { mutableStateOf("") }
	var isSending by remember { mutableStateOf(false) }
	var sendError by remember { mutableStateOf<String?>(null) }
	val messages = remember { mutableStateListOf<DmDisplayMessage>() }
	val listState = rememberLazyListState()

	val publicKeyPrefixBytes = remember(publicKeyHex) {
		publicKeyHex.take(12).chunked(2).map { it.toInt(16).toByte() }.toByteArray()
	}
	val publicKeyPrefix = remember(publicKeyHex) { publicKeyHex.take(12) }

	// Collect incoming contact messages for this contact
	LaunchedEffect(Unit) {
		connection.incomingMessages.collect { msg ->
			if (msg is ReceivedMessage.ContactMessage && msg.publicKeyPrefix == publicKeyPrefix) {
				messages.add(
					DmDisplayMessage(
						text = msg.text,
						isOutgoing = false,
						timestamp = msg.timestamp,
						snr = msg.snr,
					)
				)
			}
		}
	}

	// Track acks for sent messages
	LaunchedEffect(Unit) {
		connection.acks.collect { ackCode ->
			val idx = messages.indexOfFirst { it.expectedAck == ackCode && !it.acked }
			if (idx >= 0) {
				messages[idx] = messages[idx].copy(acked = true)
			}
		}
	}

	// Auto-scroll when new messages arrive
	LaunchedEffect(messages.size) {
		if (messages.isNotEmpty()) {
			listState.animateScrollToItem(messages.lastIndex)
		}
	}

	Scaffold(
		topBar = {
			TopAppBar(
				title = { Text(contactName) },
				navigationIcon = {
					IconButton(onClick = onBack) {
						Text("<", style = MaterialTheme.typography.titleMedium)
					}
				}
			)
		}
	) { padding ->
		Column(
			modifier = Modifier
				.fillMaxSize()
				.padding(padding),
		) {
			// Message list
			LazyColumn(
				modifier = Modifier
					.weight(1f)
					.fillMaxWidth()
					.padding(horizontal = 16.dp),
				state = listState,
				verticalArrangement = Arrangement.spacedBy(8.dp),
			) {
				if (messages.isEmpty()) {
					item {
						Text(
							text = "No messages yet. Send one or wait for incoming messages.",
							style = MaterialTheme.typography.bodyMedium,
							color = MaterialTheme.colorScheme.onSurfaceVariant,
							modifier = Modifier.padding(vertical = 32.dp),
						)
					}
				}
				items(messages) { msg ->
					DmMessageBubble(msg)
				}
			}

			// Error display
			if (sendError != null) {
				Text(
					text = sendError!!,
					color = MaterialTheme.colorScheme.error,
					modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
				)
			}

			// Input bar
			Row(
				modifier = Modifier
					.fillMaxWidth()
					.padding(16.dp),
				horizontalArrangement = Arrangement.spacedBy(8.dp),
				verticalAlignment = Alignment.Bottom,
			) {
				OutlinedTextField(
					value = messageText,
					onValueChange = { messageText = it },
					modifier = Modifier.weight(1f),
					placeholder = { Text("Message...") },
					enabled = !isSending,
					singleLine = true,
				)
				Button(
					onClick = {
						val text = messageText.trim()
						if (text.isEmpty()) return@Button
						isSending = true
						sendError = null
						scope.launch {
							try {
								val confirmation = connection.sendDirectMessage(publicKeyPrefixBytes, text)
								messages.add(
									DmDisplayMessage(
										text = text,
										isOutgoing = true,
										timestamp = kotlin.time.Clock.System.now().epochSeconds,
										expectedAck = confirmation.expectedAck.ifEmpty { null },
									)
								)
								messageText = ""
							} catch (e: Exception) {
								sendError = "Send failed: ${e.message}"
							} finally {
								isSending = false
							}
						}
					},
					enabled = !isSending && messageText.isNotBlank(),
				) {
					Text(if (isSending) "..." else "Send")
				}
			}
		}
	}
}

private data class DmDisplayMessage(
	val text: String,
	val isOutgoing: Boolean,
	val timestamp: Long,
	val snr: Float? = null,
	val expectedAck: String? = null,
	val acked: Boolean = false,
)

@Composable
private fun DmMessageBubble(message: DmDisplayMessage) {
	val alignment = if (message.isOutgoing) Alignment.End else Alignment.Start
	val color = if (message.isOutgoing) {
		MaterialTheme.colorScheme.primaryContainer
	} else {
		MaterialTheme.colorScheme.surfaceVariant
	}

	Column(
		modifier = Modifier.fillMaxWidth(),
		horizontalAlignment = alignment,
	) {
		Card(
			colors = CardDefaults.cardColors(containerColor = color),
		) {
			Column(modifier = Modifier.padding(12.dp)) {
				Text(
					text = message.text,
					style = MaterialTheme.typography.bodyMedium,
				)
				message.snr?.let { snr ->
					Text(
						text = "SNR: $snr dB",
						style = MaterialTheme.typography.labelSmall,
						color = MaterialTheme.colorScheme.onSurfaceVariant,
					)
				}
				if (message.isOutgoing && message.expectedAck != null) {
					Text(
						text = if (message.acked) "Delivered" else "Pending...",
						style = MaterialTheme.typography.labelSmall,
						color = if (message.acked) {
							MaterialTheme.colorScheme.primary
						} else {
							MaterialTheme.colorScheme.onSurfaceVariant
						},
					)
				}
			}
		}
	}
}
