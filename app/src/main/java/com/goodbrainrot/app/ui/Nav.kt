package com.goodbrainrot.app.ui

import androidx.compose.runtime.Composable
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.goodbrainrot.app.QuizType

object Routes {
    const val SPLASH = "splash"
    const val WARNING = "warning"
    const val HOME = "home"

    // Hubs
    const val HUB_MUSIC = "hub_music"
    const val HUB_GUESS = "hub_guess"
    const val HUB_DEVINE = "hub_devine"
    const val HUB_LISTEN = "hub_listen"
    const val HUB_GAMES = "hub_games"
    const val HUB_CREATE = "hub_create"

    // Musique
    const val PIANO = "piano"
    const val XYLOPHONE = "xylophone"
    const val GUITAR = "guitar"
    const val MPC_HIPHOP = "mpc_hiphop"
    const val MPC_ELECTRO = "mpc_electro"

    // Écoute
    const val ANIMAL_SOUNDS = "animal_sounds"
    const val VEHICLES = "vehicles"
    const val QUIZ_SOUND = "quiz_sound"

    // Devine (abstrait)
    const val QUIZ_NUMBER = "quiz_number"
    const val QUIZ_LETTER = "quiz_letter"
    const val QUIZ_COLOR = "quiz_color"
    const val QUIZ_SHAPE = "quiz_shape"
    const val QUIZ_COUNT = "quiz_count"

    // Reconnais (images)
    const val QUIZ_IMG_ANIMAL = "quiz_img_animal"
    const val QUIZ_IMG_VEHICLE = "quiz_img_vehicle"
    const val QUIZ_IMG_DINO = "quiz_img_dino"
    const val QUIZ_IMG_MIXED = "quiz_img_mixed"
    const val QUIZ_MAMA = "quiz_mama"

    // Jeux & créer
    const val BUBBLES = "bubbles"
    const val STARS = "stars"
    const val SHMUP = "shmup"
    const val VROUM = "vroum"
    const val GARDEN = "garden"
    const val DRAWING = "drawing"

    const val PARENTS = "parents"

    const val OPTIONS = "options?tab={tab}"
    fun options(tab: String = OptionsTab.GENERAL) = "options?tab=$tab"
}

object OptionsTab {
    const val GENERAL = "general"
    const val PIANO = "piano"
    const val SOUNDS = "sounds"
    const val QUIZ = "quiz"
    const val ARCADE = "arcade"
}

@Composable
fun AppNavHost() {
    val navController = rememberNavController()
    val openActivity: (String) -> Unit = { route -> navController.navigate(route) }
    val back: () -> Unit = { navController.popBackStack() }

    NavHost(navController = navController, startDestination = Routes.SPLASH) {
        composable(Routes.SPLASH) {
            SplashScreen(onDone = {
                navController.navigate(Routes.WARNING) {
                    popUpTo(Routes.SPLASH) { inclusive = true }
                }
            })
        }
        composable(Routes.WARNING) {
            WarningScreen(onContinue = {
                navController.navigate(Routes.HOME) {
                    popUpTo(Routes.WARNING) { inclusive = true }
                }
            })
        }
        composable(Routes.HOME) {
            HomeScreen(
                onOpenActivity = openActivity,
                onOpenOptions = { navController.navigate(Routes.options()) }
            )
        }

        // Hubs
        composable(Routes.HUB_MUSIC) { HubScreen(Routes.HUB_MUSIC, openActivity, back) }
        composable(Routes.HUB_GUESS) { HubScreen(Routes.HUB_GUESS, openActivity, back) }
        composable(Routes.HUB_DEVINE) { HubScreen(Routes.HUB_DEVINE, openActivity, back) }
        composable(Routes.HUB_LISTEN) { HubScreen(Routes.HUB_LISTEN, openActivity, back) }
        composable(Routes.HUB_GAMES) { HubScreen(Routes.HUB_GAMES, openActivity, back) }
        composable(Routes.HUB_CREATE) { HubScreen(Routes.HUB_CREATE, openActivity, back) }

        // Musique
        composable(Routes.PIANO) {
            PianoScreen(onBack = back, onOpenOptions = { navController.navigate(Routes.options(OptionsTab.PIANO)) })
        }
        composable(Routes.XYLOPHONE) { XylophoneScreen(onBack = back) }
        composable(Routes.GUITAR) { GuitarScreen(onBack = back) }
        composable(Routes.MPC_HIPHOP) { MpcScreen(kit = "hiphop", onBack = back) }
        composable(Routes.MPC_ELECTRO) { MpcScreen(kit = "electro", onBack = back) }

        // Écoute
        composable(Routes.ANIMAL_SOUNDS) { AnimalSoundsScreen(onBack = back) }
        composable(Routes.VEHICLES) { VehiclesScreen(onBack = back) }
        composable(Routes.QUIZ_SOUND) { QuizScreen(type = QuizType.SOUND, onBack = back) }

        // Devine (abstrait)
        composable(Routes.QUIZ_NUMBER) { QuizScreen(type = QuizType.NUMBER, onBack = back) }
        composable(Routes.QUIZ_LETTER) { QuizScreen(type = QuizType.LETTER, onBack = back) }
        composable(Routes.QUIZ_COLOR) { QuizScreen(type = QuizType.COLOR, onBack = back) }
        composable(Routes.QUIZ_SHAPE) { QuizScreen(type = QuizType.SHAPE, onBack = back) }
        composable(Routes.QUIZ_COUNT) { QuizScreen(type = QuizType.COUNT, onBack = back) }

        // Reconnais (images)
        composable(Routes.QUIZ_IMG_ANIMAL) { QuizScreen(type = QuizType.IMG_ANIMAL, onBack = back) }
        composable(Routes.QUIZ_IMG_VEHICLE) { QuizScreen(type = QuizType.IMG_VEHICLE, onBack = back) }
        composable(Routes.QUIZ_IMG_DINO) { QuizScreen(type = QuizType.IMG_DINO, onBack = back) }
        composable(Routes.QUIZ_IMG_MIXED) { QuizScreen(type = QuizType.IMG_MIXED, onBack = back) }
        composable(Routes.QUIZ_MAMA) { QuizScreen(type = QuizType.MAMA, onBack = back) }

        // Jeux & créer
        composable(Routes.BUBBLES) { BubblesScreen(onBack = back) }
        composable(Routes.STARS) { StarsScreen(onBack = back) }
        composable(Routes.SHMUP) { ShmupScreen(onBack = back) }
        composable(Routes.VROUM) { VroumScreen(onBack = back) }
        composable(Routes.GARDEN) { GardenScreen(onBack = back) }
        composable(Routes.DRAWING) { DrawingScreen(onBack = back) }
        composable(Routes.PARENTS) { ParentScreen(onBack = back, onOpenActivity = openActivity) }

        composable(
            route = Routes.OPTIONS,
            arguments = listOf(navArgument("tab") {
                type = NavType.StringType
                defaultValue = OptionsTab.GENERAL
            })
        ) { backStackEntry ->
            OptionsScreen(
                initialTab = backStackEntry.arguments?.getString("tab") ?: OptionsTab.GENERAL,
                onBack = back
            )
        }
    }
}
