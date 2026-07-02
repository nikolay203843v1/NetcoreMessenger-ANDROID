package com.netcoremessenger.feature.auth.onboarding

import android.app.Activity
import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.togetherWith
import androidx.compose.animation.core.*
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInClient
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.common.api.ApiException
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.GoogleAuthProvider
import com.netcoremessenger.core.util.Constants
import com.netcoremessenger.ui.anim.bouncingClickable
import com.netcoremessenger.ui.theme.GradientPrimaryEnd
import com.netcoremessenger.ui.theme.GradientPrimaryStart
import com.netcoremessenger.ui.theme.OnlineGreen

@Suppress("DEPRECATION") // GoogleSignIn deprecated в пользу Credential Manager — мигрируем отдельно
@Composable
fun OnboardingScreen(
    onNavigateToChatList: () -> Unit,
    viewModel: OnboardingViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val scrollState = rememberScrollState()
    val context = LocalContext.current

    LaunchedEffect(uiState.step) {
        if (uiState.step == AuthStep.DONE) {
            onNavigateToChatList()
        }
    }

    val googleSignInClient: GoogleSignInClient = remember {
        val gso = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
            .requestIdToken(context.getString(com.netcoremessenger.R.string.default_web_client_id))
            .build()
        GoogleSignIn.getClient(context, gso)
    }

    val pickAvatarLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri ->
        if (uri != null) viewModel.onAvatarSelected(uri)
    }

    val signInLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val data: Intent? = result.data
            val task = GoogleSignIn.getSignedInAccountFromIntent(data)
            try {
                val account = task.getResult(ApiException::class.java)
                val googleIdToken = account?.idToken
                if (googleIdToken.isNullOrBlank()) {
                    viewModel.onGoogleSignInError("Google did not return an ID token")
                } else {
                    // Обмениваем Google OAuth-токен на Firebase ID-token,
                    // потому что сервер верифицирует именно Firebase-токен.
                    val credential = GoogleAuthProvider.getCredential(googleIdToken, null)
                    FirebaseAuth.getInstance().signInWithCredential(credential)
                        .addOnSuccessListener { authResult ->
                            authResult.user?.getIdToken(true)
                                ?.addOnSuccessListener { tokenResult ->
                                    val firebaseIdToken = tokenResult.token
                                    if (firebaseIdToken.isNullOrBlank()) {
                                        viewModel.onGoogleSignInError("Firebase returned empty ID token")
                                    } else {
                                        viewModel.googleLogin(firebaseIdToken)
                                    }
                                }
                                ?.addOnFailureListener { e ->
                                    viewModel.onGoogleSignInError("getIdToken failed: ${e.message}")
                                }
                        }
                        .addOnFailureListener { e ->
                            viewModel.onGoogleSignInError("Firebase signIn failed: ${e.message}")
                        }
                }
            } catch (e: ApiException) {
                viewModel.onGoogleSignInError("Google sign-in failed (code ${e.statusCode})")
            }
        } else {
            viewModel.onGoogleSignInError("Sign-in cancelled")
        }
    }

    // Чистый фон (Aurora ⨯ Minimal): без угловых свечений, только воздух.
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        AnimatedContent(
            targetState = uiState.step,
            transitionSpec = {
                fadeIn(animationSpec = tween(400)) + slideInHorizontally(
                    animationSpec = tween(400),
                    initialOffsetX = { if (targetState == AuthStep.PROFILE_SETUP) it else -it }
                ) togetherWith fadeOut(animationSpec = tween(300)) + slideOutHorizontally(
                    animationSpec = tween(300),
                    targetOffsetX = { if (targetState == AuthStep.PROFILE_SETUP) -it else it }
                )
            },
            label = "auth_step_transition",
            modifier = Modifier.fillMaxSize()
        ) { step ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(scrollState)
                    .statusBarsPadding()
                    .navigationBarsPadding()
                    .padding(horizontal = 32.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                if (step == AuthStep.SIGN_IN) {
                    Spacer(modifier = Modifier.height(56.dp))

                    // Лого с живым glow: расходящиеся кольца + перелив оттенка индиго↔пурпур.
                    LogoWithGlow()

                    Spacer(modifier = Modifier.height(40.dp))

                    ShimmerTitle()

                    Spacer(modifier = Modifier.height(12.dp))

                    Text(
                        text = "Experience communication in a stunning new way",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center
                    )

                    Spacer(modifier = Modifier.height(56.dp))

                    // Индиго-кнопка Google с soft-glow снизу.
                    Box(
                        modifier = Modifier.fillMaxWidth(),
                        contentAlignment = Alignment.TopCenter
                    ) {
                        // Soft glow под кнопкой: растянутый мягкий индиго-блик.
                        Box(
                            modifier = Modifier
                                .align(Alignment.BottomCenter)
                                .fillMaxWidth(0.85f)
                                .height(36.dp)
                                .background(
                                    brush = Brush.radialGradient(
                                        colors = listOf(
                                            GradientPrimaryStart.copy(alpha = 0.35f),
                                            Color.Transparent
                                        )
                                    )
                                )
                        )
                        PrimaryGradientButton(
                            text = "Sign in with Google",
                            isLoading = uiState.isLoading,
                            enabled = !uiState.isLoading,
                            leadingContent = {
                                Image(
                                    painter = painterResource(id = com.netcoremessenger.R.drawable.ic_google_color),
                                    contentDescription = null,
                                    modifier = Modifier.size(20.dp)
                                )
                                Spacer(modifier = Modifier.width(12.dp))
                            }
                        ) {
                            signInLauncher.launch(googleSignInClient.signInIntent)
                        }
                    }

                    if (uiState.error != null) {
                        Spacer(modifier = Modifier.height(20.dp))
                        Text(
                            text = uiState.error!!,
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodyMedium,
                            textAlign = TextAlign.Center
                        )
                    }

                    Spacer(modifier = Modifier.height(56.dp))

                    Text(
                        text = "By continuing you agree to our Terms of Service and Privacy Policy",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(horizontal = 16.dp)
                    )
                }

                if (step == AuthStep.PROFILE_SETUP) {
                    Spacer(modifier = Modifier.height(24.dp))

                    AvatarWithGlow(
                        avatarUri = uiState.avatarUri,
                        existingAvatarMediaId = uiState.existingAvatarMediaId,
                        onPick = { pickAvatarLauncher.launch("image/*") }
                    )

                    Spacer(modifier = Modifier.height(28.dp))

                    Text(
                        text = "Customize Profile",
                        style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.onBackground
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    Text(
                        text = "Set up your handle and username to start connecting",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center
                    )

                    Spacer(modifier = Modifier.height(32.dp))

                    OutlinedTextField(
                        value = uiState.displayName,
                        onValueChange = viewModel::onDisplayNameChanged,
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("Display Name *") },
                        singleLine = true,
                        shape = RoundedCornerShape(16.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedContainerColor = MaterialTheme.colorScheme.surface,
                            unfocusedContainerColor = MaterialTheme.colorScheme.surface,
                            focusedBorderColor = MaterialTheme.colorScheme.primary,
                            unfocusedBorderColor = MaterialTheme.colorScheme.outline,
                            cursorColor = MaterialTheme.colorScheme.primary,
                            focusedTextColor = MaterialTheme.colorScheme.onBackground,
                            unfocusedTextColor = MaterialTheme.colorScheme.onBackground
                        )
                    )

                    Spacer(modifier = Modifier.height(18.dp))

                    OutlinedTextField(
                        value = uiState.username,
                        onValueChange = viewModel::onUsernameChanged,
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("Username") },
                        singleLine = true,
                        shape = RoundedCornerShape(16.dp),
                        trailingIcon = {
                            when (uiState.usernameStatus) {
                                UsernameStatus.CHECKING -> CircularProgressIndicator(
                                    modifier = Modifier.size(20.dp),
                                    strokeWidth = 2.dp
                                )
                                UsernameStatus.AVAILABLE -> Icon(
                                    imageVector = Icons.Filled.Check,
                                    contentDescription = "Available",
                                    tint = OnlineGreen
                                )
                                UsernameStatus.TAKEN -> Icon(
                                    imageVector = Icons.Filled.Close,
                                    contentDescription = "Taken",
                                    tint = MaterialTheme.colorScheme.error
                                )
                                else -> {}
                            }
                        },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedContainerColor = MaterialTheme.colorScheme.surface,
                            unfocusedContainerColor = MaterialTheme.colorScheme.surface,
                            focusedBorderColor = MaterialTheme.colorScheme.primary,
                            unfocusedBorderColor = MaterialTheme.colorScheme.outline,
                            cursorColor = MaterialTheme.colorScheme.primary,
                            focusedTextColor = MaterialTheme.colorScheme.onBackground,
                            unfocusedTextColor = MaterialTheme.colorScheme.onBackground
                        )
                    )

                    if (uiState.usernameStatus == UsernameStatus.TAKEN) {
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = "Username already taken",
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.align(Alignment.Start).padding(horizontal = 8.dp)
                        )
                    }

                    Spacer(modifier = Modifier.height(18.dp))

                    OutlinedTextField(
                        value = uiState.bio,
                        onValueChange = viewModel::onBioChanged,
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("Bio (optional)") },
                        minLines = 3,
                        maxLines = 4,
                        shape = RoundedCornerShape(16.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedContainerColor = MaterialTheme.colorScheme.surface,
                            unfocusedContainerColor = MaterialTheme.colorScheme.surface,
                            focusedBorderColor = MaterialTheme.colorScheme.primary,
                            unfocusedBorderColor = MaterialTheme.colorScheme.outline,
                            cursorColor = MaterialTheme.colorScheme.primary,
                            focusedTextColor = MaterialTheme.colorScheme.onBackground,
                            unfocusedTextColor = MaterialTheme.colorScheme.onBackground
                        )
                    )

                    if (uiState.error != null) {
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = uiState.error!!,
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodySmall
                        )
                    }

                    Spacer(modifier = Modifier.height(36.dp))

                    val isSaveEnabled = uiState.displayName.isNotBlank() && !uiState.isLoading
                    PrimaryGradientButton(
                        text = "Start Messaging",
                        isLoading = uiState.isLoading,
                        enabled = isSaveEnabled
                    ) {
                        viewModel.onStartMessaging()
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    TextButton(onClick = viewModel::onSkip) {
                        Text(
                            text = "Skip for now",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(48.dp))
            }
        }
    }
}

/**
 * Логотип с «живым» glow позади: мягкое пульсирующее ядро + расходящиеся
 * кольца (ripple) + перелив оттенка индиго↔пурпур. Единственный эффект на экране.
 *
 * Тайминги согласованы с заголовком и кнопкой (~3 сек, мягкий EaseInOut),
 * чтобы экран «дышал», а не мельтешил.
 */
@Composable
private fun LogoWithGlow() {
    val transition = rememberInfiniteTransition(label = "logo_glow")
    // Перелив оттенка ядра индиго → пурпур.
    val hueT by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 3000, easing = EaseInOut),
            repeatMode = RepeatMode.Reverse
        ),
        label = "hue"
    )
    // Лёгкая пульсация яркости ядра (мягче, чем раньше).
    val corePulse by transition.animateFloat(
        initialValue = 0.38f,
        targetValue = 0.55f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 3000, easing = EaseInOut),
            repeatMode = RepeatMode.Reverse
        ),
        label = "core"
    )
    // Два кольца со смещёнными фазами — эффект непрерывно расходящихся волн.
    val ring1 by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(animation = tween(3000, easing = LinearEasing)),
        label = "ring1"
    )
    val ring2 by transition.animateFloat(
        initialValue = 0.5f,
        targetValue = 1.5f,
        animationSpec = infiniteRepeatable(animation = tween(3000, easing = LinearEasing)),
        label = "ring2"
    )

    val coreColor = lerp(GradientPrimaryStart, GradientPrimaryEnd, hueT)

    Box(
        modifier = Modifier
            .size(140.dp)
            .drawBehind {
                val center = Offset(size.width / 2f, size.height / 2f)
                val maxRadius = size.minDimension / 2f

                // Ядро свечения (мягкий радиальный градиент + пульсация).
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(coreColor.copy(alpha = corePulse), Color.Transparent),
                        center = center,
                        radius = maxRadius * 0.55f
                    ),
                    center = center,
                    radius = maxRadius * 0.55f
                )

                // Расходящиеся кольца: радиус растёт 0→1, альфа падает 0.5→0.
                drawRippleRing(ring1 % 1f, coreColor, center, maxRadius)
                drawRippleRing(ring2 % 1f, coreColor, center, maxRadius)
            },
        contentAlignment = Alignment.Center
    ) {
        Image(
            painter = painterResource(id = com.netcoremessenger.R.drawable.logo),
            contentDescription = "Logo",
            modifier = Modifier
                .size(72.dp)
                .clip(RoundedCornerShape(22.dp))
                .border(
                    width = 1.5.dp,
                    color = Color.White.copy(alpha = 0.3f),
                    shape = RoundedCornerShape(22.dp)
                )
        )
    }
}

/** Рисует одно расходящееся кольцо по нормализованному прогрессу [0;1). */
private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawRippleRing(
    progress: Float,
    color: Color,
    center: Offset,
    maxRadius: Float
) {
    val radius = maxRadius * (0.55f + 0.45f * progress)
    val alpha = (1f - progress) * 0.4f
    if (alpha > 0.01f) {
        drawCircle(
            color = color.copy(alpha = alpha),
            radius = radius,
            center = center,
            style = Stroke(width = 2.dp.toPx())
        )
    }
}

/**
 * Заголовок «NetCore» с перетекающим индиго→пурпурным градиентом.
 * Движение градиента согласовано с переливом glow за лого (3 сек).
 */
@Composable
private fun ShimmerTitle() {
    val transition = rememberInfiniteTransition(label = "title_shimmer")
    val progress by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 3000, easing = LinearEasing)
        ),
        label = "title_progress"
    )

    // Градиент «перетекает» по ширине заголовка: три точки indigo→violet→indigo.
    val brush = Brush.linearGradient(
        colors = listOf(GradientPrimaryStart, GradientPrimaryEnd, GradientPrimaryStart),
        start = Offset(x = -300f + 600f * progress, y = 0f),
        end = Offset(x = 300f + 600f * progress, y = 0f)
    )

    Text(
        text = "NetCore",
        style = MaterialTheme.typography.displayLarge.copy(
            fontWeight = FontWeight.Bold,
            brush = brush
        )
    )
}

/**
 * Аватарка профиля с мягким индиго-glow позади — в едином стиле с логотипом.
 */
@Composable
private fun AvatarWithGlow(
    avatarUri: android.net.Uri?,
    existingAvatarMediaId: Long?,
    onPick: () -> Unit
) {
    val context = LocalContext.current
    Box(
        modifier = Modifier.size(140.dp),
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = Modifier
                .size(120.dp)
                .background(
                    brush = Brush.radialGradient(
                        colors = listOf(
                            GradientPrimaryStart.copy(alpha = 0.35f),
                            Color.Transparent
                        )
                    ),
                    shape = CircleShape
                )
        )
        Box(
            modifier = Modifier
                .size(110.dp)
                .clip(CircleShape)
                .clickable(onClick = onPick)
                .background(MaterialTheme.colorScheme.surface)
                .border(
                    width = 1.5.dp,
                    brush = Brush.linearGradient(listOf(GradientPrimaryStart, GradientPrimaryEnd)),
                    shape = CircleShape
                ),
            contentAlignment = Alignment.Center
        ) {
            when {
                avatarUri != null -> {
                    AsyncImage(
                        model = avatarUri,
                        contentDescription = "Avatar",
                        modifier = Modifier.fillMaxSize().clip(CircleShape)
                    )
                }
                existingAvatarMediaId != null -> {
                    AsyncImage(
                        model = ImageRequest.Builder(context)
                            .data("${Constants.BASE_URL}/api/v1/media/$existingAvatarMediaId")
                            .crossfade(true)
                            .build(),
                        contentDescription = "Avatar",
                        modifier = Modifier.fillMaxSize().clip(CircleShape)
                    )
                }
                else -> {
                    Icon(
                        imageVector = Icons.Filled.Person,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(56.dp)
                    )
                }
            }
        }

        Box(
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .size(38.dp)
                .clip(CircleShape)
                .background(
                    brush = Brush.linearGradient(
                        colors = listOf(GradientPrimaryStart, GradientPrimaryEnd)
                    )
                ),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Filled.CameraAlt,
                contentDescription = "Pick avatar",
                tint = Color.White,
                modifier = Modifier.size(18.dp)
            )
        }
    }
}

/**
 * Единая солидная индиго-градиентная кнопка для обоих шагов онбординга.
 * Поверх градиента бежит мягкий светлый блик (shimmer) — согласован с
 * переливом заголовка и glow за лого (~3 сек, линейно).
 */
@Composable
private fun PrimaryGradientButton(
    text: String,
    isLoading: Boolean,
    enabled: Boolean,
    leadingContent: @Composable (RowScope.() -> Unit)? = null,
    onClick: () -> Unit
) {
    val transition = rememberInfiniteTransition(label = "btn_shimmer")
    val shimmerProgress by transition.animateFloat(
        initialValue = -1f,
        targetValue = 2f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 3000, easing = LinearEasing)
        ),
        label = "shimmer"
    )

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(56.dp)
            .bouncingClickable(enabled = enabled, onClick = onClick)
            .background(
                brush = if (enabled) {
                    Brush.linearGradient(listOf(GradientPrimaryStart, GradientPrimaryEnd))
                } else {
                    Brush.linearGradient(
                        listOf(
                            MaterialTheme.colorScheme.surfaceVariant,
                            MaterialTheme.colorScheme.surfaceVariant
                        )
                    )
                },
                shape = RoundedCornerShape(16.dp)
            )
            .border(
                width = 1.dp,
                color = Color.White.copy(alpha = if (enabled) 0.25f else 0.08f),
                shape = RoundedCornerShape(16.dp)
            )
            .drawWithContent {
                drawContent()
                // Бегущий светлый блик — только когда кнопка активна.
                if (enabled) {
                    val shimmerX = size.width * shimmerProgress
                    val shimmerWidth = size.width * 0.4f
                    drawRect(
                        brush = Brush.horizontalGradient(
                            colors = listOf(
                                Color.Transparent,
                                Color.White.copy(alpha = 0.12f),
                                Color.White.copy(alpha = 0.18f),
                                Color.White.copy(alpha = 0.12f),
                                Color.Transparent
                            ),
                            startX = shimmerX - shimmerWidth / 2f,
                            endX = shimmerX + shimmerWidth / 2f
                        ),
                        topLeft = Offset.Zero,
                        size = size
                    )
                }
            },
        contentAlignment = Alignment.Center
    ) {
        if (isLoading) {
            CircularProgressIndicator(
                modifier = Modifier.size(24.dp),
                color = if (enabled) Color.White else MaterialTheme.colorScheme.onSurfaceVariant,
                strokeWidth = 2.5.dp
            )
        } else {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center) {
                if (leadingContent != null) leadingContent()
                Text(
                    text = text,
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 0.4.sp
                    ),
                    color = if (enabled) Color.White else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}
