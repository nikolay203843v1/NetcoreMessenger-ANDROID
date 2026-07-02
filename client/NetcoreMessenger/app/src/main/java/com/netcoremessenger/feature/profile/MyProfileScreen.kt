package com.netcoremessenger.feature.profile

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AlternateEmail
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.*
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
import com.netcoremessenger.core.util.Constants
import com.netcoremessenger.ui.theme.*
import com.netcoremessenger.ui.anim.bouncingClickable
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material3.MaterialTheme
import android.net.Uri

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun MyProfileScreen(
    onNavigateBack: () -> Unit,
    onEdit: () -> Unit,
    vm: MyProfileViewModel = hiltViewModel()
) {
    val state by vm.ui.collectAsState()
    var cropSourceUri by remember { mutableStateOf<android.net.Uri?>(null) }
    var pendingCropUris by remember { mutableStateOf<List<Uri>>(emptyList()) }
    var photoMenuExpanded by remember { mutableStateOf(false) }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.GetMultipleContents()) { uris ->
        pendingCropUris = uris
        cropSourceUri = uris.firstOrNull()
    }
    Scaffold(
        containerColor = MaterialTheme.colorScheme.background
    ) { padding ->
        if (state.isLoading) {
            Box(modifier = Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
            }
            return@Scaffold
        }
        val user = state.user ?: return@Scaffold
        val photoIds = state.photoMediaIds
        val pagerState = rememberPagerState(pageCount = { photoIds.size.coerceAtLeast(1) })
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(380.dp)
                    .background(Color.Black)
            ) {
                if (photoIds.isNotEmpty()) {
                    HorizontalPager(
                        state = pagerState,
                        modifier = Modifier.fillMaxSize()
                    ) { page ->
                        AsyncImage(
                            model = ImageRequest.Builder(LocalContext.current)
                                .data("${Constants.BASE_URL}/api/v1/media/${photoIds[page]}").crossfade(true).build(),
                            contentDescription = null,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize()
                        )
                    }
                } else {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(MaterialTheme.colorScheme.surfaceVariant),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            Icons.Filled.Person,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(120.dp)
                        )
                    }
                }

                Row(
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .fillMaxWidth()
                        .padding(horizontal = 10.dp, vertical = 10.dp),
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    repeat(photoIds.size.coerceAtLeast(1)) { index ->
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .height(3.dp)
                                .clip(RoundedCornerShape(2.dp))
                                .background(
                                    Color.White.copy(
                                        alpha = if (index == pagerState.currentPage) 0.9f else 0.34f
                                    )
                                )
                        )
                    }
                }

                Row(
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .fillMaxWidth()
                        .padding(top = 32.dp, start = 8.dp, end = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Назад",
                            tint = Color.White
                        )
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (state.profilePhotos.isNotEmpty()) {
                            Box {
                                IconButton(onClick = { photoMenuExpanded = true }) {
                                    Icon(
                                        Icons.Filled.MoreVert,
                                        contentDescription = "Действия с фото",
                                        tint = Color.White
                                    )
                                }
                                DropdownMenu(
                                    expanded = photoMenuExpanded,
                                    onDismissRequest = { photoMenuExpanded = false }
                                ) {
                                    DropdownMenuItem(
                                        text = { Text("Удалить фото") },
                                        leadingIcon = {
                                            Icon(Icons.Filled.Delete, contentDescription = null)
                                        },
                                        enabled = !state.isSaving,
                                        onClick = {
                                            photoMenuExpanded = false
                                            state.profilePhotos.getOrNull(pagerState.currentPage)?.let {
                                                vm.deletePhoto(it.id)
                                            }
                                        }
                                    )
                                }
                            }
                        }
                        if (state.isDirty || state.isSaving) {
                            IconButton(
                                onClick = vm::saveProfile,
                                enabled = !state.isSaving
                            ) {
                                if (state.isSaving) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(22.dp),
                                        strokeWidth = 2.dp,
                                        color = Color.White
                                    )
                                } else {
                                    Icon(
                                        Icons.Filled.Check,
                                        contentDescription = "Сохранить",
                                        tint = MaterialTheme.colorScheme.primary
                                    )
                                }
                            }
                        } else if (state.profilePhotos.isEmpty()) {
                            Spacer(modifier = Modifier.size(48.dp))
                        }
                    }
                }

                Box(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .height(150.dp)
                        .background(
                            Brush.verticalGradient(
                                listOf(Color.Transparent, Color.Black.copy(alpha = 0.78f))
                            )
                        )
                )

                Column(
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .padding(start = 22.dp, end = 88.dp, bottom = 24.dp)
                ) {
                    Text(
                        text = state.displayName.ifBlank { "Без имени" },
                        color = Color.White,
                        style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Bold)
                    )
                    Text(
                        text = if (state.username.isBlank()) "username не указан" else "@${state.username}",
                        color = Color.White.copy(alpha = 0.78f),
                        style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Medium)
                    )
                }

                Box(
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(end = 22.dp, bottom = 26.dp)
                        .size(52.dp)
                        .clip(CircleShape)
                        .clickable { picker.launch("image/*") }
                        .background(
                            brush = Brush.linearGradient(listOf(GradientPrimaryStart, GradientPrimaryEnd))
                        )
                        .border(1.5.dp, Color.White.copy(alpha = 0.25f), CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Filled.CameraAlt, contentDescription = null, tint = Color.White, modifier = Modifier.size(24.dp))
                }
            }

            Spacer(modifier = Modifier.height(18.dp))

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 18.dp)
                    .background(
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                        shape = RoundedCornerShape(14.dp)
                    )
                    .border(
                        width = 0.5.dp,
                        color = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f),
                        shape = RoundedCornerShape(14.dp)
                    )
                    .padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                OutlinedTextField(
                    value = state.displayName,
                    onValueChange = vm::onNameChanged,
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Имя") },
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp)
                )
                OutlinedTextField(
                    value = state.username,
                    onValueChange = vm::onUsernameChanged,
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Username") },
                    singleLine = true,
                    prefix = { Text("@") },
                    shape = RoundedCornerShape(12.dp)
                )
                OutlinedTextField(
                    value = state.bio,
                    onValueChange = vm::onBioChanged,
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("О себе") },
                    minLines = 3,
                    maxLines = 5,
                    shape = RoundedCornerShape(12.dp)
                )
            }

            Spacer(modifier = Modifier.height(12.dp))
            state.error?.let {
                Text(
                    text = it,
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(horizontal = 18.dp)
                )
            }
        }
    }

    cropSourceUri?.let { uri ->
        AvatarCropDialog(
            sourceUri = uri,
            onCancel = {
                pendingCropUris = emptyList()
                cropSourceUri = null
            },
            onCropped = { croppedUri ->
                vm.updateAvatar(croppedUri)
                val rest = pendingCropUris.drop(1)
                pendingCropUris = rest
                cropSourceUri = rest.firstOrNull()
            }
        )
    }
}
