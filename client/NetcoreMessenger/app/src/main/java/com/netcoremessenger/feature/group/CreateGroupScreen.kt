package com.netcoremessenger.feature.group

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ArrowForward
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.netcoremessenger.core.data.remote.dto.UserDto
import com.netcoremessenger.core.util.Constants
import com.netcoremessenger.ui.theme.*
import androidx.compose.material3.MaterialTheme

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CreateGroupScreen(
    onNavigateBack: () -> Unit,
    onGroupCreated: (Long) -> Unit,
    vm: CreateGroupViewModel = hiltViewModel()
) {
    val state by vm.ui.collectAsState()

    LaunchedEffect(state.createdChatId) {
        state.createdChatId?.let { onGroupCreated(it) }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Новая группа", color = MaterialTheme.colorScheme.onBackground) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Назад", tint = MaterialTheme.colorScheme.onBackground)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
            )
        },
        floatingActionButton = {
            val canCreate = state.groupName.isNotBlank() && state.selected.isNotEmpty() && !state.isCreating
            if (canCreate || state.isCreating) {
                FloatingActionButton(
                    onClick = vm::create,
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary
                ) {
                    if (state.isCreating) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(22.dp),
                            strokeWidth = 2.dp,
                            color = MaterialTheme.colorScheme.onPrimary
                        )
                    } else {
                        Icon(Icons.Filled.ArrowForward, contentDescription = "Далее")
                    }
                }
            }
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding).padding(horizontal = 16.dp)) {
            OutlinedTextField(
                value = state.groupName,
                onValueChange = vm::onGroupNameChanged,
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                placeholder = { Text("Название группы") },
                singleLine = true,
                shape = RoundedCornerShape(12.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedContainerColor = MaterialTheme.colorScheme.surface, unfocusedContainerColor = MaterialTheme.colorScheme.surface,
                    focusedBorderColor = MaterialTheme.colorScheme.primary, unfocusedBorderColor = MaterialTheme.colorScheme.outline,
                    focusedTextColor = MaterialTheme.colorScheme.onBackground, unfocusedTextColor = MaterialTheme.colorScheme.onBackground
                )
            )

            if (state.selected.isNotEmpty()) {
                Spacer(modifier = Modifier.height(8.dp))
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(state.selected, key = { it.id }) { user ->
                        SelectedChip(user = user, onRemove = { vm.toggleSelect(user) })
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            state.error?.let {
                Spacer(modifier = Modifier.height(8.dp))
                Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
            }

            Spacer(modifier = Modifier.height(14.dp))

            Text(
                text = "Участники",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.labelLarge
            )
            Spacer(modifier = Modifier.height(6.dp))

            LazyColumn(modifier = Modifier.fillMaxSize()) {
                if (state.isLoadingContacts) {
                    item {
                        Box(
                            modifier = Modifier.fillMaxWidth().padding(top = 32.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                        }
                    }
                } else if (state.contacts.isEmpty()) {
                    item {
                        Text(
                            text = "Пока нет людей из личных чатов. Сначала начните диалог, затем добавьте пользователя в группу.",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.padding(top = 24.dp)
                        )
                    }
                }
                items(state.contacts, key = { it.id }) { user ->
                    val checked = state.selected.any { it.id == user.id }
                    UserPickRow(user = user, checked = checked, onTap = { vm.toggleSelect(user) })
                }
            }
        }
    }
}

@Composable
private fun SelectedChip(user: UserDto, onRemove: () -> Unit) {
    Row(
        modifier = Modifier
            .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(20.dp))
            .clickable { onRemove() }
            .padding(horizontal = 10.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(user.displayName ?: "?", color = MaterialTheme.colorScheme.onBackground, style = MaterialTheme.typography.bodySmall)
        Spacer(modifier = Modifier.width(4.dp))
        Icon(Icons.Filled.Close, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(14.dp))
    }
}

@Composable
private fun UserPickRow(user: UserDto, checked: Boolean, onTap: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onTap).padding(vertical = 10.dp, horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier.size(48.dp).clip(CircleShape).background(MaterialTheme.colorScheme.surfaceVariant),
            contentAlignment = Alignment.Center
        ) {
            if (user.avatarMediaId != null) {
                AsyncImage(
                    model = ImageRequest.Builder(LocalContext.current)
                        .data("${Constants.BASE_URL}/api/v1/media/${user.avatarMediaId}").crossfade(true).build(),
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize().clip(CircleShape)
                )
            } else {
                Icon(Icons.Filled.Person, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        Spacer(modifier = Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(user.displayName ?: "Unknown", color = MaterialTheme.colorScheme.onBackground, style = MaterialTheme.typography.bodyLarge)
            if (!user.username.isNullOrBlank()) Text("@${user.username}", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
        }
        Box(
            modifier = Modifier.size(24.dp).clip(CircleShape).border(2.dp, if (checked) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline, CircleShape).background(if (checked) MaterialTheme.colorScheme.primary else androidx.compose.ui.graphics.Color.Transparent),
            contentAlignment = Alignment.Center
        ) {
            if (checked) Icon(Icons.Filled.Check, contentDescription = null, tint = MaterialTheme.colorScheme.onPrimary, modifier = Modifier.size(16.dp))
        }
    }
}
