package com.example.rebeka

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.foundation.layout.fillMaxSize
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.example.rebeka.blocking.BlockService
import com.example.rebeka.data.StatsRepository
import com.example.rebeka.navigation.Routes
import com.example.rebeka.ui.home.HomeScreen
import com.example.rebeka.ui.home.OnboardingScreen

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val app = application as RebekaApp
        val repository = StatsRepository(app.database.dayStatsDao(), app.database.appSettingsDao())

        // Сервис слежения стартует при первом открытии приложения родителем
        // после того как onboarding пройден; повторные запуски идемпотентны.
        BlockService.start(this)

        setContent {
            MaterialTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    RebekaNavHost(repository)
                }
            }
        }
    }
}

@Composable
private fun RebekaNavHost(repository: StatsRepository) {
    val navController = rememberNavController()
    NavHost(navController = navController, startDestination = Routes.HOME) {
        composable(Routes.HOME) { HomeScreen(repository) }
        composable(Routes.ONBOARDING) { OnboardingScreen() }
    }
}
