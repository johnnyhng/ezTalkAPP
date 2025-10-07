package com.k2fsa.sherpa.onnx.simulate.streaming.asr

sealed class NavRoutes(val route: String) {
    object Home : NavRoutes("home")
    object FileManager : NavRoutes("file_manager")
    object Help : NavRoutes("help")
    object Settings : NavRoutes("settings")
}