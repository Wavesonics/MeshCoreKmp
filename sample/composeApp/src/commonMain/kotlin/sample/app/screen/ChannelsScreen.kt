package sample.app.screen

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.darkrockstudios.libs.meshcore.DeviceConnection
import com.darkrockstudios.libs.meshcore.model.Channel
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChannelsScreen(
	connection: DeviceConnection,
	onBack: () -> Unit,
	onChannelSelected: (Channel) -> Unit,
) {
	val scope = rememberCoroutineScope()
	val channels by connection.channels.collectAsState()
	var isLoading by remember { mutableStateOf(false) }
	var errorMessage by remember { mutableStateOf<String?>(null) }

	Scaffold(
		topBar = {
			TopAppBar(
				title = { Text("Channels") },
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
				.padding(padding)
				.padding(16.dp),
			verticalArrangement = Arrangement.spacedBy(12.dp),
		) {
			Button(
				onClick = {
					isLoading = true
					errorMessage = null
					scope.launch {
						try {
							connection.getAllChannels()
						} catch (e: Exception) {
							errorMessage = "Failed to fetch channels: ${e.message}"
						} finally {
							isLoading = false
						}
					}
				},
				enabled = !isLoading,
				modifier = Modifier.fillMaxWidth(),
			) {
				Text(if (isLoading) "Fetching..." else "Refresh Channels")
			}

			if (errorMessage != null) {
				Text(
					text = errorMessage!!,
					color = MaterialTheme.colorScheme.error,
				)
			}

			if (isLoading && channels.isEmpty()) {
				Box(
					modifier = Modifier.fillMaxWidth().padding(vertical = 32.dp),
					contentAlignment = Alignment.Center,
				) {
					CircularProgressIndicator(modifier = Modifier.size(32.dp))
				}
			}

			if (channels.isEmpty() && !isLoading) {
				Text(
					text = "No channels found. Tap Refresh to load.",
					style = MaterialTheme.typography.bodyMedium,
					color = MaterialTheme.colorScheme.onSurfaceVariant,
					modifier = Modifier.padding(vertical = 16.dp),
				)
			}

			LazyColumn(
				modifier = Modifier.fillMaxSize(),
				verticalArrangement = Arrangement.spacedBy(8.dp),
			) {
				items(channels, key = { it.index }) { channel ->
					ChannelCard(
						channel = channel,
						onClick = { onChannelSelected(channel) },
					)
				}
			}
		}
	}
}

@Composable
private fun ChannelCard(
	channel: Channel,
	onClick: () -> Unit,
) {
	Card(
		modifier = Modifier
			.fillMaxWidth()
			.clickable(enabled = !channel.isEmpty, onClick = onClick),
	) {
		Row(
			modifier = Modifier
				.fillMaxWidth()
				.padding(16.dp),
			horizontalArrangement = Arrangement.SpaceBetween,
			verticalAlignment = Alignment.CenterVertically,
		) {
			Column(modifier = Modifier.weight(1f)) {
				Text(
					text = if (channel.isEmpty) "(empty)" else channel.name,
					style = MaterialTheme.typography.titleMedium,
					color = if (channel.isEmpty) {
						MaterialTheme.colorScheme.onSurfaceVariant
					} else {
						MaterialTheme.colorScheme.onSurface
					},
				)
				Text(
					text = "Channel ${channel.index}",
					style = MaterialTheme.typography.bodySmall,
					color = MaterialTheme.colorScheme.onSurfaceVariant,
				)
			}
		}
	}
}
