package tw.com.johnnyhng.eztalk.asr

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Settings

object NavBarItems {
    val BarItems = listOf(
        BarItem(
            title = "Home",
            image = Icons.Filled.Home,
            route = "home",
        ),
        BarItem(
            title = "File Manager",
            image = Icons.Filled.Folder,
            route = "file_manager"
        ),
        BarItem(
            title = "Settings",
            image = Icons.Filled.Settings,
            route = "settings"
        ),
        BarItem(
            title = "Help",
            image = Icons.Filled.Info,
            route = "help",
        ),
    )
}