package ai.sotuvchi.app.app

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** The server a build talks to, for the banner: production is the one to be careful with. */
fun serverLabel(apiUrl: String): Pair<String, Boolean> {
    val host = apiUrl.substringAfter("://").substringBefore('/')
    val local = host.startsWith("localhost") || host.startsWith("127.0.0.1") || host.startsWith("10.0.2.2")
    return host to !local
}

/**
 * Debug and QA builds only: a strip across the status bar that names the
 * server, red for a real one and amber for a local mock, so a test build
 * pointed at production cannot go unnoticed.
 */
@Composable
fun ServerBanner(apiUrl: String, modifier: Modifier = Modifier) {
    val (host, real) = serverLabel(apiUrl)
    val height = with(LocalDensity.current) { WindowInsets.statusBars.getTop(this).toDp() }
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(height.coerceAtLeast(20.dp))
            .background(if (real) Color(0xFFC62828) else Color(0xFFB26A00)),
        contentAlignment = Alignment.CenterStart,
    ) {
        Text(
            text = (if (real) "TEST BUILD - LIVE SERVER " else "TEST BUILD - MOCK ") + host,
            color = Color.White,
            fontSize = 10.sp,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            modifier = Modifier.padding(start = 72.dp),
        )
    }
}
