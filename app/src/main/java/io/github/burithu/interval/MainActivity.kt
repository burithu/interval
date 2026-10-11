package io.github.burithu.interval

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import io.github.burithu.interval.onboarding.SetupScreen
import io.github.burithu.interval.onboarding.SetupStage
import io.github.burithu.interval.onboarding.WelcomeScreen
import io.github.burithu.interval.ui.editor.EditorScreen
import io.github.burithu.interval.ui.editor.EditorViewModel
import io.github.burithu.interval.ui.home.HomeScreen
import io.github.burithu.interval.ui.home.HomeViewModel
import io.github.burithu.interval.ui.log.LogScreen
import io.github.burithu.interval.ui.log.LogViewModel
import io.github.burithu.interval.ui.theme.IntervalTheme
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val container = (application as IntervalApp).container
        setContent {
            IntervalTheme {
                IntervalNav(container)
            }
        }
    }
}

@Composable
private fun IntervalNav(container: AppContainer) {
    val settings by container.settings.settings.collectAsStateWithLifecycle(initialValue = null)
    val loaded = settings ?: run {
        Box(Modifier.fillMaxSize().background(IntervalTheme.colors.bg))
        return
    }
    val start = remember { if (loaded.onboardingDone) "home" else "welcome" }
    val nav = rememberNavController()
    val scope = rememberCoroutineScope()
    val finishSetup: () -> Unit = {
        scope.launch { container.settings.setOnboardingDone(true) }
        nav.navigate("home") { popUpTo(nav.graph.id) { inclusive = true } }
    }
    NavHost(navController = nav, startDestination = start) {
        composable("welcome") { WelcomeScreen(onStart = { nav.navigate("setup/permissions") }) }
        composable("setup/permissions") {
            SetupScreen(container, SetupStage.PERMISSIONS, onNext = { nav.navigate("setup/reliability") }, onSkip = finishSetup)
        }
        composable("setup/reliability") {
            SetupScreen(container, SetupStage.RELIABILITY, onNext = finishSetup, onSkip = finishSetup)
        }
        composable("home") {
            val vm: HomeViewModel = viewModel(factory = viewModelFactory { initializer { HomeViewModel(container) } })
            HomeScreen(
                vm = vm,
                onOpen = { id -> nav.navigate("editor/$id") },
                onAdd = { nav.navigate("editor/0") },
                onLog = { nav.navigate("log") },
                onSetup = { nav.navigate("setup/permissions") },
            )
        }
        composable("editor/{id}", arguments = listOf(navArgument("id") { type = NavType.LongType })) {
            val vm: EditorViewModel = viewModel(
                factory = viewModelFactory { initializer { EditorViewModel(container, createSavedStateHandle()) } },
            )
            EditorScreen(vm = vm, onClose = { nav.popBackStack() })
        }
        composable("log") {
            val vm: LogViewModel = viewModel(factory = viewModelFactory { initializer { LogViewModel(container) } })
            LogScreen(vm = vm, onBack = { nav.popBackStack() })
        }
    }
}
