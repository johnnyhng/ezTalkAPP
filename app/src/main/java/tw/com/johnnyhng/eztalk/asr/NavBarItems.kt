package tw.com.johnnyhng.eztalk.asr

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.RecordVoiceOver
import androidx.compose.material.icons.filled.Science
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
                route = NavRoutes.Translate.route,
            ),
            BarItem(
                title = stringResource(R.string.speaker),
                image = Icons.Filled.RecordVoiceOver,
                route = NavRoutes.Speaker.route
            ),
            BarItem(
                title = stringResource(R.string.data_collect),
                image = Icons.Filled.Mic,
                route = NavRoutes.DataCollect.route,
            ),
            BarItem(
                title = stringResource(R.string.experiment),
                image = Icons.Filled.Science,
                route = NavRoutes.Experiment.route,
            ),
        )
}
