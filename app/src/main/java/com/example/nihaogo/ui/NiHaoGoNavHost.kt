package com.example.nihaogo.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.toRoute
import com.example.nihaogo.NiHaoGoApp
import com.example.nihaogo.data.content.Scene
import com.example.nihaogo.data.content.Step
import com.example.nihaogo.ui.boss.BossScreen
import com.example.nihaogo.ui.boss.BossTarget
import com.example.nihaogo.ui.special.DialogueSceneScreen
import com.example.nihaogo.ui.special.FindSceneScreen
import com.example.nihaogo.ui.special.InterrogateSceneScreen
import com.example.nihaogo.ui.special.ModeScreen
import com.example.nihaogo.ui.special.PuzzleSceneScreen
import com.example.nihaogo.ui.special.RoleplaySceneScreen
import com.example.nihaogo.ui.special.TrainingSceneScreen
import com.example.nihaogo.ui.cover.CoverScreen
import com.example.nihaogo.ui.home.CategoryScreen
import com.example.nihaogo.ui.home.HomeScreen
import com.example.nihaogo.ui.lesson.ListeningStepScreen
import com.example.nihaogo.ui.lesson.SentenceStepScreen
import com.example.nihaogo.ui.lesson.VocabStepScreen
import com.example.nihaogo.ui.profile.ProfileScreen
import kotlinx.serialization.Serializable

@Serializable
object CoverRoute

@Serializable
object HomeRoute

@Serializable
object ProfileRoute

@Serializable
data class CategoryRoute(val categoryId: String)

@Serializable
data class StepRoute(val categoryId: String, val step: Step)

@Serializable
data class ModeRoute(val modeId: String)

@Serializable
data class SceneRoute(val modeId: String, val index: Int)

@Composable
fun NiHaoGoNavHost() {
    val nav = rememberNavController()
    NavHost(navController = nav, startDestination = CoverRoute) {
        composable<CoverRoute> {
            CoverScreen(onDone = {
                // Guard against a tap and the timer both firing.
                if (nav.currentDestination?.hasRoute<CoverRoute>() == true) {
                    nav.navigate(HomeRoute) { popUpTo<CoverRoute> { inclusive = true } }
                }
            })
        }
        composable<HomeRoute> {
            HomeScreen(
                onOpenCategory = { nav.navigate(CategoryRoute(it)) },
                onOpenMode = { nav.navigate(ModeRoute(it)) },
                onOpenProfile = { nav.navigate(ProfileRoute) },
            )
        }
        composable<ProfileRoute> {
            ProfileScreen(onBack = { nav.popBackStack() })
        }
        composable<ModeRoute> { entry ->
            val route = entry.toRoute<ModeRoute>()
            ModeScreen(
                modeId = route.modeId,
                onBack = { nav.popBackStack() },
                onOpenScene = { index -> nav.navigate(SceneRoute(route.modeId, index)) },
            )
        }
        composable<SceneRoute> { entry ->
            val route = entry.toRoute<SceneRoute>()
            val back: () -> Unit = { nav.popBackStack() }
            val scene = (LocalContext.current.applicationContext as NiHaoGoApp).container
                .content.mode(route.modeId).scenes[route.index]
            when (scene) {
                is Scene.Find -> FindSceneScreen(route.modeId, route.index, back)
                is Scene.Dialogue -> DialogueSceneScreen(route.modeId, route.index, back)
                is Scene.Puzzle -> PuzzleSceneScreen(route.modeId, route.index, back)
                is Scene.Training -> TrainingSceneScreen(route.modeId, route.index, back)
                is Scene.Interrogate -> InterrogateSceneScreen(route.modeId, route.index, back)
                is Scene.Roleplay -> RoleplaySceneScreen(route.modeId, route.index, back)
            }
        }
        composable<CategoryRoute> { entry ->
            val route = entry.toRoute<CategoryRoute>()
            CategoryScreen(
                categoryId = route.categoryId,
                onBack = { nav.popBackStack() },
                onOpenStep = { step -> nav.navigate(StepRoute(route.categoryId, step)) },
            )
        }
        composable<StepRoute> { entry ->
            val route = entry.toRoute<StepRoute>()
            val back: () -> Unit = { nav.popBackStack() }
            when (route.step) {
                Step.Vocab -> VocabStepScreen(route.categoryId, back)
                Step.Sentence -> SentenceStepScreen(route.categoryId, back)
                Step.Listening -> ListeningStepScreen(route.categoryId, back)
                Step.Boss -> BossScreen(BossTarget.CategoryBoss(route.categoryId), back)
            }
        }
    }
}
