package tw.com.johnnyhng.eztalk.asr

sealed class NavRoutes(val route: String) {
    object Home : NavRoutes("home")
    object Translate : NavRoutes("translate")
    object DataCollect : NavRoutes("data_collect")
    object FileManager : NavRoutes("file_manager")
    object Speaker : NavRoutes("speaker")
    object Help : NavRoutes("help")
    object Settings : NavRoutes("settings")
}
