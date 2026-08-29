package com.example.rebeka

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.example.rebeka.accessibility.AccessibilityUtils
import com.example.rebeka.admin.AdminUtils
import com.example.rebeka.blocking.BlockService
import com.example.rebeka.data.StatsRepository
import com.example.rebeka.navigation.Routes
import com.example.rebeka.ui.home.HomeScreen
import com.example.rebeka.ui.home.OnboardingScreen
import com.example.rebeka.ui.home.PinSetupScreen
import com.example.rebeka.usage.UsageStatsHelper

class MainActivity : ComponentActivity() {

    private val requestRuntimePermissions =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
            // Результат нам не нужен здесь — состояние заново читается при onResume
            // внутри OnboardingScreen/isOnboardingComplete, не через колбэк лаунчера.
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // ACTIVITY_RECOGNITION и POST_NOTIFICATIONS — единственные два разрешения
        // из полного набора, которые вообще выдаются системным диалогом; без явного
        // запроса здесь пользователь никогда его не увидит.
        val toRequest = buildList {
            if (ContextCompat.checkSelfPermission(this@MainActivity, Manifest.permission.ACTIVITY_RECOGNITION)
                != PackageManager.PERMISSION_GRANTED
            ) add(Manifest.permission.ACTIVITY_RECOGNITION)

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                ContextCompat.checkSelfPermission(this@MainActivity, Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED
            ) add(Manifest.permission.POST_NOTIFICATIONS)
        }
        if (toRequest.isNotEmpty()) requestRuntimePermissions.launch(toRequest.toTypedArray())

        val app = application as RebekaApp
        val repository = StatsRepository(app.database.dayStatsDao(), app.database.appSettingsDao())

        setContent {
            MaterialTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    RebekaNavHost(repository)
                }
            }
        }
    }
}

private fun isOnboardingComplete(context: android.content.Context): Boolean {
    val usageHelper = UsageStatsHelper(context)
    val stepsGranted = ContextCompat.checkSelfPermission(context, Manifest.permission.ACTIVITY_RECOGNITION) ==
        PackageManager.PERMISSION_GRANTED
    return stepsGranted &&
        AccessibilityUtils.isServiceEnabled(context) &&
        usageHelper.hasUsageAccess() &&
        AdminUtils.isAdminActive(context) &&
        Settings.canDrawOverlays(context)
}

@Composable
private fun RebekaNavHost(repository: StatsRepository) {
    val navController = rememberNavController()
    val context = androidx.compose.ui.platform.LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    var onboardingComplete by remember { mutableStateOf(isOnboardingComplete(context)) }
    // null = ещё не прочитали из БД, не решаем стартовый экран раньше времени
    var pinSet by remember { mutableStateOf<Boolean?>(null) }

    LaunchedEffect(Unit) { pinSet = repository.isPinSet() }

    // Сервис слежения стартует, как только все разрешения реально выданы
    // И задан PIN — без PIN блокировку было бы нечем снять.
    LaunchedEffect(onboardingComplete, pinSet) {
        if (onboardingComplete && pinSet == true) BlockService.start(context)
    }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                onboardingComplete = isOnboardingComplete(context)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    val start = when {
        pinSet == null -> null
        pinSet == false -> Routes.PIN_SETUP
        !onboardingComplete -> Routes.ONBOARDING
        else -> Routes.HOME
    }

    if (start == null) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
        return
    }

    NavHost(navController = navController, startDestination = start) {
        composable(Routes.PIN_SETUP) {
            PinSetupScreen(repository, onPinSet = {
                pinSet = true
                val next = if (isOnboardingComplete(context)) Routes.HOME else Routes.ONBOARDING
                navController.navigate(next) { popUpTo(Routes.PIN_SETUP) { inclusive = true } }
            })
        }
        composable(Routes.HOME) { HomeScreen(repository) }
        composable(Routes.ONBOARDING) {
            OnboardingScreen(onAllGranted = {
                onboardingComplete = true
                navController.navigate(Routes.HOME) { popUpTo(Routes.ONBOARDING) { inclusive = true } }
            })
        }
    }
}
