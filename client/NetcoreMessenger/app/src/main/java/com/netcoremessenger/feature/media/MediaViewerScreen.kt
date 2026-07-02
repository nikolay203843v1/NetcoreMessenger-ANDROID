package com.netcoremessenger.feature.media

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BrokenImage
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import coil.compose.SubcomposeAsyncImage
import coil.request.ImageRequest
import com.netcoremessenger.core.util.Constants

@Composable
fun MediaViewerScreen(
    mediaId: Long,
    albumMediaIds: List<Long> = emptyList(),
    initialIndex: Int = 0,
    onClose: () -> Unit
) {
    val pages = if (albumMediaIds.isNotEmpty()) albumMediaIds else listOf(mediaId)
    val startPage = initialIndex.coerceIn(0, (pages.size - 1).coerceAtLeast(0))
    val pagerState = rememberPagerState(initialPage = startPage, pageCount = { pages.size })

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {
        HorizontalPager(
            state = pagerState,
            modifier = Modifier.fillMaxSize()
        ) { page ->
            ViewerImage(mediaId = pages[page])
        }

        if (pages.size > 1) {
            Row(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .fillMaxWidth()
                    .padding(start = 16.dp, top = 52.dp, end = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                repeat(pages.size) { index ->
                    Box(
                        modifier = Modifier
                            .height(3.dp)
                            .weight(1f)
                            .background(
                                Color.White.copy(alpha = if (index == pagerState.currentPage) 0.92f else 0.25f),
                                RoundedCornerShape(2.dp)
                            )
                    )
                }
            }
            Text(
                text = "${pagerState.currentPage + 1}/${pages.size}",
                color = Color.White.copy(alpha = 0.86f),
                style = MaterialTheme.typography.labelMedium,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 24.dp)
            )
        }

        IconButton(
            onClick = onClose,
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(top = 78.dp, end = 12.dp)
        ) {
            Icon(Icons.Filled.Close, contentDescription = "Close", tint = Color.White)
        }
    }
}

@Composable
private fun ViewerImage(mediaId: Long) {
    SubcomposeAsyncImage(
        model = ImageRequest.Builder(LocalContext.current)
            .data("${Constants.BASE_URL}/api/v1/media/$mediaId")
            .crossfade(true)
            .build(),
        contentDescription = null,
        modifier = Modifier.fillMaxSize(),
        contentScale = ContentScale.Fit,
        loading = {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = Color.White)
            }
        },
        error = {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Icon(
                    imageVector = Icons.Default.BrokenImage,
                    contentDescription = null,
                    tint = Color.White.copy(alpha = 0.72f),
                    modifier = Modifier.padding(bottom = 42.dp)
                )
            }
        }
    )
}
