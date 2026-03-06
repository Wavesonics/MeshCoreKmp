package sample.app.screen

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.darkrockstudios.libs.meshcore.DeviceConnection
import com.darkrockstudios.libs.meshcore.model.Contact
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ContactsScreen(
	connection: DeviceConnection,
	onBack: () -> Unit,
	onContactSelected: (Contact) -> Unit,
) {
	val scope = rememberCoroutineScope()
	val contacts by connection.contacts.collectAsState()
	var isLoading by remember { mutableStateOf(false) }
	var errorMessage by remember { mutableStateOf<String?>(null) }
	var filterText by remember { mutableStateOf("") }
	val filteredContacts = remember(contacts, filterText) {
		if (filterText.isBlank()) contacts
		else contacts.filter { it.name.contains(filterText, ignoreCase = true) }
	}

	Scaffold(
		topBar = {
			TopAppBar(
				title = { Text("Contacts") },
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
							connection.getContacts()
						} catch (e: Exception) {
							errorMessage = "Failed to fetch contacts: ${e.message}"
						} finally {
							isLoading = false
						}
					}
				},
				enabled = !isLoading,
				modifier = Modifier.fillMaxWidth(),
			) {
				Text(if (isLoading) "Fetching..." else "Refresh Contacts")
			}

			OutlinedTextField(
				value = filterText,
				onValueChange = { filterText = it },
				modifier = Modifier.fillMaxWidth(),
				placeholder = { Text("Filter contacts...") },
				singleLine = true,
			)

			if (errorMessage != null) {
				Text(
					text = errorMessage!!,
					color = MaterialTheme.colorScheme.error,
				)
			}

			if (isLoading && contacts.isEmpty()) {
				Box(
					modifier = Modifier.fillMaxWidth().padding(vertical = 32.dp),
					contentAlignment = Alignment.Center,
				) {
					CircularProgressIndicator(modifier = Modifier.size(32.dp))
				}
			}

			if (contacts.isEmpty() && !isLoading) {
				Text(
					text = "No contacts found. Tap Refresh to load.",
					style = MaterialTheme.typography.bodyMedium,
					color = MaterialTheme.colorScheme.onSurfaceVariant,
					modifier = Modifier.padding(vertical = 16.dp),
				)
			}

			LazyColumn(
				modifier = Modifier.fillMaxSize(),
				verticalArrangement = Arrangement.spacedBy(8.dp),
			) {
				items(filteredContacts, key = { it.publicKeyPrefix }) { contact ->
					ContactCard(
						contact = contact,
						onClick = { onContactSelected(contact) },
					)
				}
			}
		}
	}
}

@Composable
private fun ContactCard(
	contact: Contact,
	onClick: () -> Unit,
) {
	Card(
		modifier = Modifier
			.fillMaxWidth()
			.clickable(onClick = onClick),
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
					text = contact.name,
					style = MaterialTheme.typography.titleMedium,
				)
				Text(
					text = contact.publicKeyPrefix,
					style = MaterialTheme.typography.bodySmall,
					color = MaterialTheme.colorScheme.onSurfaceVariant,
				)
			}
		}
	}
}
