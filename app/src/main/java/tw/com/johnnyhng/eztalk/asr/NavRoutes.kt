package tw.com.johnnyhng.eztalk.asr

sealed class NavRoutes(val route: String) {
    object Home : NavRoutes("home")
    object FileManager : NavRoutes("file_manager")
    object Help : NavRoutes("help")
    object Settings : NavRoutes("settings")
}