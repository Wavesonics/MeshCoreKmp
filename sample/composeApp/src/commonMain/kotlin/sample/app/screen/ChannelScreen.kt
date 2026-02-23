package sample.app.screen

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.darkrockstudios.libs.meshcorekmp.DeviceConnection
import com.darkrockstudios.libs.meshcorekmp.model.ReceivedMessage
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChannelScreen(
	channelIndex: Int,
	channelName: String,
	connection: DeviceConnection,
	onBack: () -> Unit,
) {
	val scope = rememberCoroutineScope()
	var messageText by remember { mutableStateOf("") }
	var isSending by remember { mutableStateOf(false) }
	var sendError by remember { mutableStateOf<String?>(null) }
	val messages = remember { mutableStateListOf<DisplayMessage>() }
	val listState = rememberLazyListState()

	// Collect incoming channel messages
	LaunchedEffect(Unit) {
		connection.incomingMessages.collect { msg ->
			if (msg is ReceivedMessage.ChannelMessage && msg.channelIndex == channelIndex) {
				messages.add(
					DisplayMessage(
						text = msg.text,
						isOutgoing = false,
						timestamp = msg.timestamp,
						snr = msg.snr,
					)
				)
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
				title = { Text(channelName) },
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
					MessageBubble(msg)
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
								connection.sendChannelMessage(channelIndex, text)
								messages.add(
									DisplayMessage(
										text = text,
										isOutgoing = true,
										timestamp = kotlin.time.Clock.System.now().epochSeconds,
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

private data class DisplayMessage(
	val text: String,
	val isOutgoing: Boolean,
	val timestamp: Long,
	val snr: Float? = null,
)

@Composable
private fun MessageBubble(message: DisplayMessage) {
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
			colors = androidx.compose.material3.CardDefaults.cardColors(
				containerColor = color,
			),
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
			}
		}
	}
}
