package tw.com.johnnyhng.eztalk.asr

import android.Manifest
import android.app.Activity
import android.content.pm.PackageManager
import android.graphics.BitmapFactory
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MaterialTheme.colorScheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.AlertDialog
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.Image
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.HelpOutline
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.outlined.AccountCircle
import androidx.compose.material.icons.filled.Settings
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.app.ActivityCompat
import androidx.lifecycle.lifecycleScope
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import tw.com.johnnyhng.eztalk.asr.auth.GoogleAccountSession
import tw.com.johnnyhng.eztalk.asr.auth.GoogleSignInManager
import tw.com.johnnyhng.eztalk.asr.auth.displayLabel
import tw.com.johnnyhng.eztalk.asr.utils.TLSExpireResolver
import tw.com.johnnyhng.eztalk.asr.managers.HomeViewModel
import tw.com.johnnyhng.eztalk.asr.managers.SettingsManager
import tw.com.johnnyhng.eztalk.asr.screens.DataCollectScreen
import tw.com.johnnyhng.eztalk.asr.screens.FileManagerScreen
import tw.com.johnnyhng.eztalk.asr.screens.HelpScreen
import tw.com.johnnyhng.eztalk.asr.screens.HomeScreen
import tw.com.johnnyhng.eztalk.asr.screens.SettingsScreen
import tw.com.johnnyhng.eztalk.asr.screens.TranslateScreen
import tw.com.johnnyhng.eztalk.asr.ui.speaker.SpeakerScreen
import tw.com.johnnyhng.eztalk.asr.ui.theme.SimulateStreamingAsrTheme
import java.io.File
import java.net.URL
import java.util.Locale

const val TAG = "eztalk"
private const val REQUEST_RECORD_AUDIO_PERMISSION = 200

@Suppress("DEPRECATION")
class MainActivity : ComponentActivity() {
    private val permissions: Array<String> = arrayOf(Manifest.permission.RECORD_AUDIO)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val locale = Locale.getDefault()
        Locale.setDefault(locale)
        val config = resources.configuration
        config.setLocale(locale)
        resources.updateConfiguration(config, resources.displayMetrics)
        val initialEntryRoute = runBlocking {
            val settingsManager = SettingsManager(this@MainActivity)
            sanitizeEntryScreenRoute(settingsManager.userSettings.first().entryScreenRoute)
        }

        setContent {
            SimulateStreamingAsrTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = colorScheme.background
                ) {
                    MainScreen(initialEntryRoute = initialEntryRoute)
                }
            }
        }

        ActivityCompat.requestPermissions(this, permissions, REQUEST_RECORD_AUDIO_PERMISSION)

        lifecycleScope.launch(Dispatchers.Default) {
            val assets = assets
            SimulateStreamingAsr.initVad(assets)
        }
    }

    @Deprecated("Deprecated in Java")
    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        val permissionToRecordAccepted = if (requestCode == REQUEST_RECORD_AUDIO_PERMISSION) {
            grantResults[0] == PackageManager.PERMISSION_GRANTED
        } else {
            false
        }

        if (!permissionToRecordAccepted) {
            Log.e(TAG, "Audio record is disallowed")
            Toast.makeText(
                this,
                getString(R.string.microphone_permission_required),
                Toast.LENGTH_SHORT
            )
                .show()
            finish()
        }

        Log.i(TAG, "Audio record is permitted")
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(
    initialEntryRoute: String = NavRoutes.Home.route,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val navController = rememberNavController()
    val homeViewModel: HomeViewModel = viewModel()
    val scope = rememberCoroutineScope()
    val isAsrModelLoading by homeViewModel.isAsrModelLoading.collectAsState()
    val signInManager = remember { GoogleSignInManager() }
    val googleSignInClient = remember { signInManager.getSignInClient(context) }
    var googleSession by remember { mutableStateOf<GoogleAccountSession?>(null) }

    val googleSignInLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            signInManager.getSessionFromIntent(result.data)
                .onSuccess { session ->
                    googleSession = session
                    homeViewModel.updateUserId(session.email)
                    Log.i(
                        TAG,
                        "Google sign in succeeded email=${session.email} hasIdToken=${!session.idToken.isNullOrBlank()}"
                    )
                    scope.launch {
                        signInManager.signInToFirebase(session)
                            .onSuccess {
                                val firebaseUser = com.google.firebase.auth.FirebaseAuth.getInstance().currentUser
                                Toast.makeText(
                                    context,
                                    context.getString(
                                        R.string.firebase_sign_in_success,
                                        firebaseUser?.uid.orEmpty()
                                    ),
                                    Toast.LENGTH_SHORT
                                ).show()
                            }
                            .onFailure { error ->
                                Log.w(TAG, "Firebase sign in failed", error)
                                Toast.makeText(
                                    context,
                                    context.getString(
                                        R.string.firebase_sign_in_failed,
                                        error.message ?: error.javaClass.simpleName
                                    ),
                                    Toast.LENGTH_LONG
                                ).show()
                            }
                    }
                    Toast.makeText(
                        context,
                        context.getString(R.string.google_account_status, session.displayLabel()),
                        Toast.LENGTH_SHORT
                    ).show()
                }
                .onFailure { error ->
                    Log.w(TAG, "Google sign in failed", error)
                    Toast.makeText(context, "Google sign in failed", Toast.LENGTH_SHORT).show()
                }
        }
    }

    LaunchedEffect(signInManager, context) {
        val session = signInManager.getCurrentSession(context)
        googleSession = session
        if (session != null) {
            signInManager.restoreFirebaseSession(context)
                .onSuccess {
                    val firebaseUser = com.google.firebase.auth.FirebaseAuth.getInstance().currentUser
                    Log.i(TAG, "Firebase session restore succeeded uid=${firebaseUser?.uid.orEmpty()}")
                }
                .onFailure { error ->
                    Log.w(TAG, "Firebase session restore failed", error)
                }
        }
    }
    val profilePhoto = produceState<androidx.compose.ui.graphics.ImageBitmap?>(initialValue = null, googleSession?.email, googleSession?.photoUrl) {
        value = googleSession?.let { loadCachedGoogleProfilePhoto(context, it) }
    }

    if (isAsrModelLoading) {
        AlertDialog(
            onDismissRequest = { },
            title = {
                Text(text = stringResource(R.string.asr_loading_title))
            },
            text = {
                Column {
                    CircularProgressIndicator()
                    Text(
                        text = stringResource(R.string.asr_loading_message),
                        modifier = Modifier.padding(top = 16.dp)
                    )
                }
            },
            confirmButton = {}
        )
    }

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = colorScheme.primaryContainer,
                    titleContentColor = colorScheme.primary,
                ),
                navigationIcon = {
                    androidx.compose.foundation.layout.Row {
                        IconButton(
                            onClick = {
                                if (googleSession == null) {
                                    googleSignInLauncher.launch(googleSignInClient.signInIntent)
                                } else {
                                    signInManager.signOut(context) { result ->
                                        result
                                            .onSuccess {
                                                googleSession = null
                                                homeViewModel.updateUserId("user@example.com")
                                                Toast.makeText(
                                                    context,
                                                    context.getString(R.string.sign_out),
                                                    Toast.LENGTH_SHORT
                                                ).show()
                                            }
                                            .onFailure { error ->
                                                Log.w(TAG, "Google sign out failed", error)
                                                Toast.makeText(context, "Google sign out failed", Toast.LENGTH_SHORT).show()
                                            }
                                    }
                                }
                            }
                        ) {
                            if (googleSession != null && profilePhoto.value != null) {
                                Image(
                                    bitmap = profilePhoto.value!!,
                                    contentDescription = stringResource(R.string.sign_out),
                                    modifier = Modifier
                                        .size(28.dp)
                                        .clip(CircleShape)
                                )
                            } else {
                                Icon(
                                    imageVector = if (googleSession == null) {
                                        Icons.Outlined.AccountCircle
                                    } else {
                                        Icons.Filled.AccountCircle
                                    },
                                    contentDescription = stringResource(
                                        if (googleSession == null) R.string.sign_in_with_google else R.string.sign_out
                                    )
                                )
                            }
                        }
                        IconButton(onClick = { navController.navigateSingleTopTo(NavRoutes.Help.route) }) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.HelpOutline,
                                contentDescription = stringResource(R.string.help)
                            )
                        }
                    }
                },
                title = {
                    Text(
                        stringResource(R.string.app_top_title),
                        fontWeight = FontWeight.Bold,
                    )
                },
                actions = {
                    IconButton(onClick = { navController.navigateSingleTopTo(NavRoutes.FileManager.route) }) {
                        Icon(
                            imageVector = Icons.Filled.Folder,
                            contentDescription = stringResource(R.string.file_manager)
                        )
                    }
                    IconButton(onClick = { navController.navigateSingleTopTo(NavRoutes.Settings.route) }) {
                        Icon(
                            imageVector = Icons.Filled.Settings,
                            contentDescription = stringResource(R.string.settings)
                        )
                    }
                },
            )
        },
        content = { padding ->
            Column(Modifier.padding(padding)) {
                NavigationHost(
                    navController = navController,
                    startDestination = sanitizeEntryScreenRoute(initialEntryRoute),
                    homeViewModel = homeViewModel
                )

            }
        },
        bottomBar = {
            BottomNavigationBar(navController = navController)
        }
    )
}

private suspend fun loadCachedGoogleProfilePhoto(
    context: android.content.Context,
    session: GoogleAccountSession
): androidx.compose.ui.graphics.ImageBitmap? = withContext(Dispatchers.IO) {
    val photoUrl = session.photoUrl ?: return@withContext null
    val cacheDir = File(context.cacheDir, "google-profile").apply { mkdirs() }
    val cacheKey = "${session.email}_${photoUrl}".hashCode().toString(16)
    val cacheFile = File(cacheDir, "$cacheKey.img")

    runCatching {
        if (!cacheFile.exists()) {
            URL(photoUrl).openStream().use { input ->
                cacheFile.outputStream().use { output ->
                    input.copyTo(output)
                }
            }
        }
        BitmapFactory.decodeFile(cacheFile.absolutePath)?.asImageBitmap()
    }.onFailure { error ->
        val detail = TLSExpireResolver.resolveMessage(error, "Profile photo download failed")
        Log.w(TAG, "Failed to load Google profile photo: $detail", error)
    }.getOrNull()
}

@Composable
fun NavigationHost(
    navController: NavHostController,
    startDestination: String,
    homeViewModel: HomeViewModel
) {
    NavHost(navController = navController, startDestination = startDestination) {
        composable(NavRoutes.Home.route) {
            HomeScreen(homeViewModel = homeViewModel)
        }

        composable(NavRoutes.Translate.route) {
            TranslateScreen(homeViewModel = homeViewModel)
        }

        composable(NavRoutes.DataCollect.route) {
            DataCollectScreen(homeViewModel = homeViewModel)
        }

        composable(NavRoutes.FileManager.route) {
            FileManagerScreen(homeViewModel = homeViewModel)
        }

        composable(NavRoutes.Speaker.route) {
            SpeakerScreen(homeViewModel = homeViewModel)
        }

        composable(NavRoutes.Settings.route) {
            SettingsScreen(homeViewModel = homeViewModel)
        }

        composable(NavRoutes.Help.route) {
            HelpScreen()
        }
    }
}

@Composable
fun BottomNavigationBar(navController: NavHostController) {
    NavigationBar {
        val backStackEntry by navController.currentBackStackEntryAsState()
        val currentRoute = backStackEntry?.destination?.route

        NavBarItems.BarItems.forEach { navItem ->
            NavigationBarItem(selected = currentRoute == navItem.route,
                onClick = {
                    navController.navigateSingleTopTo(navItem.route)
                },
                icon = {
                    Icon(imageVector = navItem.image, contentDescription = navItem.title)
                }, label = {
                    Text(text = navItem.title)
                })
        }
    }
}

private fun NavHostController.navigateSingleTopTo(route: String) {
    navigate(route) {
        popUpTo(graph.findStartDestination().id) {
            saveState = true
        }
        launchSingleTop = true
        restoreState = true
    }
}
