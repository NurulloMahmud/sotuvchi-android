package ai.sotuvchi.app.design

import ai.sotuvchi.app.app.LocalAppContainer
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import coil3.compose.SubcomposeAsyncImage

/**
 * Image view backed by the app's image loader. Shows a quiet placeholder while
 * loading and an icon when the link is missing or expired. Server-relative
 * paths are resolved against the site; our own host gets the session header.
 */
@Composable
fun RemoteImage(
    url: String?,
    modifier: Modifier = Modifier,
    contentScale: ContentScale = ContentScale.Crop,
) {
    val container = LocalAppContainer.current
    val resolved = url?.let { container.api.resolve(it)?.toString() }
    Box(modifier = modifier.background(Theme.colors.surfaceAlt).clipToBounds(), contentAlignment = Alignment.Center) {
        if (resolved == null) {
            Icon(Icons.Outlined.Image, contentDescription = null, tint = Theme.colors.inkTertiary)
        } else {
            val loader = container.imageLoader
            if (loader != null) {
                SubcomposeAsyncImage(
                    model = resolved,
                    contentDescription = null,
                    imageLoader = loader,
                    contentScale = contentScale,
                    modifier = Modifier.fillMaxSize(),
                    loading = { Box(Modifier.fillMaxSize(), Alignment.Center) { Spinner(size = 20.dp, color = Theme.colors.inkTertiary) } },
                    error = { Box(Modifier.fillMaxSize(), Alignment.Center) { Icon(Icons.Outlined.Image, contentDescription = null, tint = Theme.colors.inkTertiary) } },
                )
            }
        }
    }
}
