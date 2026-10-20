package ai.sotuvchi.app.features.orders

import ai.sotuvchi.app.app.LocalAppContainer
import ai.sotuvchi.app.core.l10n.L
import ai.sotuvchi.app.design.Space
import ai.sotuvchi.app.design.Spinner
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil3.compose.SubcomposeAsyncImage

/** Full-screen, pinch-to-zoom viewer for receipts and chat pictures. */
@Composable
fun ReceiptViewer(url: String, onClose: () -> Unit) {
    val container = LocalAppContainer.current
    val resolved = remember(url) { container.api.resolve(url)?.toString() }
    var scale by remember { mutableFloatStateOf(1f) }
    var offset by remember { mutableStateOf(Offset.Zero) }

    Dialog(
        onDismissRequest = onClose,
        properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false),
    ) {
        Box(Modifier.fillMaxSize().background(Color.Black).testTag("receiptViewer")) {
            val loader = container.imageLoader
            if (resolved != null && loader != null) {
                SubcomposeAsyncImage(
                    model = resolved,
                    contentDescription = L("Receipt"),
                    imageLoader = loader,
                    contentScale = ContentScale.Fit,
                    modifier = Modifier
                        .fillMaxSize()
                        .pointerInput(Unit) {
                            detectTapGestures(onDoubleTap = {
                                if (scale > 1f) {
                                    scale = 1f
                                    offset = Offset.Zero
                                } else {
                                    scale = 2.5f
                                }
                            })
                        }
                        .pointerInput(Unit) {
                            awaitEachGesture {
                                awaitFirstDown(requireUnconsumed = false)
                                do {
                                    val event = awaitPointerEvent()
                                    val zoom = event.calculateZoom()
                                    val pan = event.calculatePan()
                                    val newScale = (scale * zoom).coerceIn(1f, 5f)
                                    // Panning only makes sense once zoomed in.
                                    if (newScale > 1f || event.changes.size > 1) {
                                        offset = if (newScale > 1f) {
                                            val maxX = size.width * (newScale - 1) / 2f
                                            val maxY = size.height * (newScale - 1) / 2f
                                            Offset(
                                                (offset.x + pan.x).coerceIn(-maxX, maxX),
                                                (offset.y + pan.y).coerceIn(-maxY, maxY),
                                            )
                                        } else Offset.Zero
                                        scale = newScale
                                        event.changes.forEach { it.consume() }
                                    }
                                } while (event.changes.any { it.pressed })
                            }
                        }
                        .graphicsLayer(
                            scaleX = scale,
                            scaleY = scale,
                            translationX = offset.x,
                            translationY = offset.y,
                        ),
                    loading = { Box(Modifier.fillMaxSize(), Alignment.Center) { Spinner(color = Color.White) } },
                    error = {
                        Column(
                            Modifier.fillMaxSize().padding(Space.xl),
                            verticalArrangement = androidx.compose.foundation.layout.Arrangement.Center,
                            horizontalAlignment = Alignment.CenterHorizontally,
                        ) {
                            Icon(Icons.Outlined.Image, null, tint = Color.White.copy(alpha = 0.8f), modifier = Modifier.size(40.dp))
                            Text(
                                L("The picture could not be loaded. The link may have expired; go back and refresh."),
                                color = Color.White.copy(alpha = 0.8f),
                                textAlign = TextAlign.Center,
                                modifier = Modifier.padding(top = Space.sm),
                            )
                        }
                    },
                )
            }
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .safeDrawingPadding()
                    .padding(Space.md)
                    .size(40.dp)
                    .background(Color.White.copy(alpha = 0.18f), CircleShape)
                    .clickable(onClick = onClose)
                    .testTag("receiptViewer.close"),
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.Outlined.Close, contentDescription = L("Close"), tint = Color.White)
            }
        }
    }
}
