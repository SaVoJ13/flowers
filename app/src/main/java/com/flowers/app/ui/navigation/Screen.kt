package com.flowers.app.ui.navigation

sealed class Screen(val route: String) {
    object Today : Screen("today")
    object Plants : Screen("plants")
    object AddPlant : Screen("add_plant?plantId={plantId}") {
        fun createRoute(plantId: Long? = null) = if (plantId != null) "add_plant?plantId=$plantId" else "add_plant"
    }
    object PlantDetail : Screen("plant_detail/{plantId}") {
        fun createRoute(plantId: Long) = "plant_detail/$plantId"
    }
    object PlantDiagnosis : Screen("plant_diagnosis/{plantId}") {
        fun createRoute(plantId: Long) = "plant_diagnosis/$plantId"
    }
    object Catalog : Screen("catalog")
    object Settings : Screen("settings")
}
