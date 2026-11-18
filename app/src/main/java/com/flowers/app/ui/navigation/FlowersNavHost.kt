package com.flowers.app.ui.navigation

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.flowers.app.FlowersApplication
import com.flowers.app.ui.catalog.CatalogScreen
import com.flowers.app.ui.catalog.CatalogViewModel
import com.flowers.app.ui.components.BottomNavBar
import com.flowers.app.ui.diagnosis.DiagnosisViewModel
import com.flowers.app.ui.diagnosis.PlantDiagnosisScreen
import com.flowers.app.ui.plants.AddEditPlantScreen
import com.flowers.app.ui.plants.PlantDetailScreen
import com.flowers.app.ui.plants.PlantsListScreen
import com.flowers.app.ui.plants.PlantsViewModel
import com.flowers.app.ui.settings.SettingsScreen
import com.flowers.app.ui.today.TodayScreen
import com.flowers.app.ui.today.TodayViewModel

@Composable
fun FlowersApp(application: FlowersApplication) {
    val navController = rememberNavController()
    val repository = application.repository

    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route

    val showBottomBar = currentRoute in listOf(
        Screen.Today.route,
        Screen.Plants.route,
        Screen.Catalog.route,
        Screen.Settings.route
    )

    Scaffold(
        bottomBar = {
            if (showBottomBar) {
                BottomNavBar(navController = navController)
            }
        }
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = Screen.Today.route,
            modifier = Modifier.padding(innerPadding)
        ) {
            // Today Screen
            composable(Screen.Today.route) {
                val todayViewModel: TodayViewModel = viewModel(
                    factory = TodayViewModel.Factory(repository)
                )
                TodayScreen(
                    viewModel = todayViewModel,
                    onNavigateToAddPlant = { navController.navigate(Screen.AddPlant.createRoute()) },
                    onNavigateToPlantDetail = { plantId -> navController.navigate(Screen.PlantDetail.createRoute(plantId)) },
                    onNavigateToDiagnosis = { plantId -> navController.navigate(Screen.PlantDiagnosis.createRoute(plantId)) }
                )
            }

            // Plants List Screen
            composable(Screen.Plants.route) {
                val plantsViewModel: PlantsViewModel = viewModel(
                    factory = PlantsViewModel.Factory(repository)
                )
                PlantsListScreen(
                    viewModel = plantsViewModel,
                    onNavigateToAddPlant = { navController.navigate(Screen.AddPlant.createRoute()) },
                    onNavigateToPlantDetail = { plantId -> navController.navigate(Screen.PlantDetail.createRoute(plantId)) }
                )
            }

            // Add / Edit Plant Screen
            composable(
                route = Screen.AddPlant.route,
                arguments = listOf(
                    navArgument("plantId") {
                        type = NavType.StringType
                        nullable = true
                        defaultValue = null
                    }
                )
            ) { backStackEntry ->
                val plantIdStr = backStackEntry.arguments?.getString("plantId")
                val plantId = plantIdStr?.toLongOrNull()
                val plantsViewModel: PlantsViewModel = viewModel(
                    factory = PlantsViewModel.Factory(repository)
                )
                AddEditPlantScreen(
                    plantId = plantId,
                    viewModel = plantsViewModel,
                    repository = repository,
                    onNavigateBack = { navController.popBackStack() }
                )
            }

            // Plant Detail Screen
            composable(
                route = Screen.PlantDetail.route,
                arguments = listOf(
                    navArgument("plantId") { type = NavType.LongType }
                )
            ) { backStackEntry ->
                val plantId = backStackEntry.arguments?.getLong("plantId") ?: 0L
                val plantsViewModel: PlantsViewModel = viewModel(
                    factory = PlantsViewModel.Factory(repository)
                )
                PlantDetailScreen(
                    plantId = plantId,
                    viewModel = plantsViewModel,
                    onNavigateBack = { navController.popBackStack() },
                    onNavigateToDiagnosis = { id -> navController.navigate(Screen.PlantDiagnosis.createRoute(id)) }
                )
            }

            // Plant Diagnosis Screen
            composable(
                route = Screen.PlantDiagnosis.route,
                arguments = listOf(
                    navArgument("plantId") { type = NavType.LongType }
                )
            ) { backStackEntry ->
                val plantId = backStackEntry.arguments?.getLong("plantId") ?: 0L
                val diagnosisViewModel: DiagnosisViewModel = viewModel(
                    factory = DiagnosisViewModel.Factory(plantId, repository)
                )
                PlantDiagnosisScreen(
                    viewModel = diagnosisViewModel,
                    onNavigateBack = { navController.popBackStack() }
                )
            }

            // Catalog Screen
            composable(Screen.Catalog.route) {
                val catalogViewModel: CatalogViewModel = viewModel(
                    factory = CatalogViewModel.Factory(repository)
                )
                CatalogScreen(viewModel = catalogViewModel)
            }

            // Settings Screen
            composable(Screen.Settings.route) {
                SettingsScreen()
            }
        }
    }
}
