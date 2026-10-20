package ai.sotuvchi.app.app

import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory

val LocalAppContainer = compositionLocalOf<AppContainer> { error("AppContainer not provided") }
val LocalAppModel = compositionLocalOf<AppModel> { error("AppModel not provided") }

/** Creates a screen's ViewModel with the shared model and container at hand. */
@Composable
inline fun <reified VM : ViewModel> appViewModel(
    key: String? = null,
    crossinline create: (AppModel) -> VM,
): VM {
    val model = LocalAppModel.current
    return viewModel(
        key = key,
        factory = viewModelFactory { initializer { create(model) } },
    )
}
