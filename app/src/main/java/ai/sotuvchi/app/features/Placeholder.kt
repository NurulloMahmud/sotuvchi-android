package ai.sotuvchi.app.features

import ai.sotuvchi.app.app.AppModel
import ai.sotuvchi.app.design.ScreenFrame
import ai.sotuvchi.app.design.SButton
import ai.sotuvchi.app.design.Space
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import kotlinx.coroutines.launch

@Composable
fun Placeholder(name: String, model: AppModel) {
    val scope = rememberCoroutineScope()
    ScreenFrame(name) {
        SButton("Sign out", { scope.launch { model.signOut() } }, modifier = Modifier.padding(Space.md))
    }
}
