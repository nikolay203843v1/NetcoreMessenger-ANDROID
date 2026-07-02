package com.netcoremessenger.feature.group

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.netcoremessenger.core.data.remote.dto.UserDto
import com.netcoremessenger.core.util.Constants
import com.netcoremessenger.feature.profile.AvatarCropDialog
import com.netcoremessenger.ui.theme.GradientPrimaryEnd
import com.netcoremessenger.ui.theme.GradientPrimaryStart

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GroupSettingsScreen(
    onNavigateBack: () -> Unit,
    vm: GroupSettingsViewModel = hiltViewModel()
) {
    val state by vm.ui.collectAsState()
    var cropSourceUri by remember { mutableStateOf<Uri?>(null) }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        cropSourceUri = uri
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Группа", color = MaterialTheme.colorScheme.onBackground) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Назад")
                    }
                },
                actions = {
                    if (state.canSave || state.isSaving) {
                        IconButton(onClick = vm::saveTitle, enabled = !state.isSaving) {
                            if (state.isSaving) {
                                CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                            } else {
                                Icon(Icons.Filled.Check, contentDescription = "Сохранить", tint = MaterialTheme.colorScheme.primary)
                            }
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
            )
        },
        floatingActionButton = {
            if (state.selectedToAdd.isNotEmpty()) {
                FloatingActionButton(
                    onClick = vm::addSelected,
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary
                ) {
                    if (state.isSaving) {
                        CircularProgressIndicator(modifier = Modifier.size(22.dp), strokeWidth = 2.dp, color = MaterialTheme.colorScheme.onPrimary)
                    } else {
                        Icon(Icons.Filled.Check, contentDescription = "Добавить")
                    }
                }
            }
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { padding ->
        if (state.isLoading) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
            }
            return@Scaffold
        }
        val chat = state.chat ?: return@Scaffold
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(280.dp)
                        .background(MaterialTheme.colorScheme.surfaceVariant),
                    contentAlignment = Alignment.Center
                ) {
                    if (chat.avatarMediaId != null) {
                        AsyncImage(
                            model = ImageRequest.Builder(LocalContext.current)
                                .data("${Constants.BASE_URL}/api/v1/media/${chat.avatarMediaId}")
                                .crossfade(true)
                                .build(),
                            contentDescription = null,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize()
                        )
                    } else {
                        Icon(Icons.Filled.Person, contentDescription = null, modifier = Modifier.size(110.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Box(
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .padding(20.dp)
                            .size(52.dp)
                            .clip(CircleShape)
                            .clickable { picker.launch("image/*") }
                            .background(Brush.linearGradient(listOf(GradientPrimaryStart, GradientPrimaryEnd))),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Filled.CameraAlt, contentDescription = null, tint = Color.White)
                    }
                }
            }

            item {
                Column(modifier = Modifier.padding(horizontal = 16.dp)) {
                    OutlinedTextField(
                        value = state.title,
                        onValueChange = vm::onTitleChanged,
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("Название группы") },
                        singleLine = true,
                        shape = RoundedCornerShape(12.dp)
                    )
                    state.error?.let {
                        Spacer(Modifier.height(8.dp))
                        Text(it, color = MaterialTheme.colorScheme.error)
                    }
                }
            }

            item {
                Text(
                    text = "Участники",
                    modifier = Modifier.padding(horizontal = 16.dp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.labelLarge
                )
            }
            items(chat.participants.orEmpty(), key = { it.userId }) { participant ->
                val user = participant.user
                UserLine(
                    user = UserDto(
                        id = participant.userId,
                        phone = user?.phone,
                        googleId = user?.googleId,
                        username = user?.username,
                        displayName = user?.displayName,
                        bio = user?.bio,
                        avatarMediaId = user?.avatarMediaId,
                        status = user?.status,
                        lastOnlineAt = user?.lastOnlineAt
                    ),
                    checked = false,
                    onTap = {}
                )
            }

            item {
                Text(
                    text = "Добавить участников",
                    modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 8.dp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.labelLarge
                )
            }
            items(state.contacts, key = { it.id }) { user ->
                val checked = state.selectedToAdd.any { it.id == user.id }
                UserLine(user = user, checked = checked, onTap = { vm.toggleAdd(user) })
            }
        }
    }

    cropSourceUri?.let { uri ->
        AvatarCropDialog(
            sourceUri = uri,
            onCancel = { cropSourceUri = null },
            onCropped = { cropped ->
                vm.updateAvatar(cropped)
                cropSourceUri = null
            }
        )
    }
}

@Composable
private fun UserLine(user: UserDto, checked: Boolean, onTap: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onTap() }
            .padding(horizontal = 16.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier.size(48.dp).clip(CircleShape).background(MaterialTheme.colorScheme.surfaceVariant),
            contentAlignment = Alignment.Center
        ) {
            if (user.avatarMediaId != null) {
                AsyncImage(
                    model = ImageRequest.Builder(LocalContext.current)
                        .data("${Constants.BASE_URL}/api/v1/media/${user.avatarMediaId}")
                        .crossfade(true)
                        .build(),
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize().clip(CircleShape)
                )
            } else {
                Icon(Icons.Filled.Person, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (checked) {
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .size(20.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primary)
                        .border(1.dp, MaterialTheme.colorScheme.background, CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Filled.Check, contentDescription = null, tint = MaterialTheme.colorScheme.onPrimary, modifier = Modifier.size(14.dp))
                }
            }
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(
                text = user.displayName ?: "Без имени",
                color = MaterialTheme.colorScheme.onBackground,
                style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Medium)
            )
            if (!user.username.isNullOrBlank()) {
                Text("@${user.username}", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}
