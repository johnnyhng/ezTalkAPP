package tw.com.johnnyhng.eztalk.asr

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Translate
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource

object NavBarItems {
    val BarItems: List<BarItem>
        @Composable
        get() = listOf(
            BarItem(
                title = stringResource(R.string.home),
                image = Icons.Filled.Home,
                route = "home",
            ),
            BarItem(
                title = stringResource(R.string.translate),
                image = Icons.Filled.Translate,
                route = "translate",
            ),
            BarItem(
                title = stringResource(R.string.file_manager),
                image = Icons.Filled.Folder,
                route = "file_manager"
            ),
            BarItem(
                title = stringResource(R.string.settings),
                image = Icons.Filled.Settings,
                route = "settings"
            ),
            BarItem(
                title = stringResource(R.string.help),
                image = Icons.Filled.Info,
                route = "help",
            ),
        )
}
