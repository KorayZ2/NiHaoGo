package com.example.nihaogo.ui

import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
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
import com.example.nihaogo.ui.special.CaseIntroScreen
import com.example.nihaogo.ui.special.DialogueSceneScreen
import com.example.nihaogo.ui.special.FindSceneScreen
import com.example.nihaogo.ui.special.InterrogateSceneScreen
import com.example.nihaogo.ui.special.ModeScreen
import com.example.nihaogo.ui.special.PuzzleSceneScreen
import com.example.nihaogo.ui.special.RoleplaySceneScreen
import com.example.nihaogo.ui.special.TrainingSceneScreen
import com.example.nihaogo.ui.special.hasCaseIntro
import com.example.nihaogo.ui.cover.CoverScreen
import com.example.nihaogo.ui.home.CategoryScreen
import com.example.nihaogo.ui.home.HomeScreen
import com.example.nihaogo.ui.intro.PinyinIntroScreen
import com.example.nihaogo.ui.lesson.ListeningStepScreen
import com.example.nihaogo.ui.lesson.SentenceStepScreen
import com.example.nihaogo.ui.lesson.VocabStepScreen
import com.example.nihaogo.ui.login.LoginScreen
import com.example.nihaogo.ui.missions.DailyMissionsScreen
import com.example.nihaogo.ui.practice.ChallengeGameScreen
import com.example.nihaogo.ui.practice.ExerciseScreen
import com.example.nihaogo.ui.practice.ListenSpeakScreen
import com.example.nihaogo.ui.words.WordListScreen
import kotlinx.serialization.Serializable

@Serializable
object CoverRoute

@Serializable
object HomeRoute

/** Sign-in page; [firstRun]: shown by itself after the cover, rather than opened from the profile. */
@Serializable
data class LoginRoute(val firstRun: Boolean)

@Serializable
object MissionsRoute

/** Word-and-picture matching game over the opened categories' words. */
@Serializable
object ChallengeRoute

/** Ten multiple-choice questions over the opened categories' words. */
@Serializable
object ExerciseRoute

/** Hear a word, then say it back. */
@Serializable
object ListenSpeakRoute

/** [firstRun]: shown by itself after the cover, rather than opened again from the profile. */
@Serializable
data class PinyinIntroRoute(val firstRun: Boolean)

@Serializable
data class CategoryRoute(val categoryId: String)

/** Word list; [categoryId] null shows every opened category. */
@Serializable
data class WordListRoute(val categoryId: String? = null)

@Serializable
data class StepRoute(val categoryId: String, val step: Step)

@Serializable
data class ModeRoute(val modeId: String)

/** A mode's story intro; [firstRun]: opened by itself on the way into the mode, not replayed. */
@Serializable
data class CaseIntroRoute(val modeId: String, val firstRun: Boolean)

@Serializable
data class SceneRoute(val modeId: String, val index: Int)

@Composable
fun NiHaoGoNavHost() {
    val nav = rememberNavController()
    val settings = (LocalContext.current.applicationContext as NiHaoGoApp).container.settings
    NavHost(
        navController = nav,
        startDestination = CoverRoute,
        // Forward slides in from the right, back slides in from the left; the cover just fades.
        enterTransition = {
            if (initialState.destination.hasRoute<CoverRoute>()) fadeIn(tween(500))
            else slideInHorizontally(tween(320)) { it / 4 } + fadeIn(tween(320))
        },
        exitTransition = {
            if (initialState.destination.hasRoute<CoverRoute>()) fadeOut(tween(500))
            else slideOutHorizontally(tween(320)) { -it / 8 } + fadeOut(tween(240))
        },
        popEnterTransition = { slideInHorizontally(tween(320)) { -it / 8 } + fadeIn(tween(320)) },
        popExitTransition = { slideOutHorizontally(tween(320)) { it / 4 } + fadeOut(tween(240)) },
    ) {
        composable<CoverRoute> {
            CoverScreen(onDone = {
                // Guard against a tap and the timer both firing.
                if (nav.currentDestination?.hasRoute<CoverRoute>() == true) {
                    // A new player is offered sign-in, then learns pinyin and tones before the map.
                    val next: Any = when {
                        !settings.loginSeen -> LoginRoute(firstRun = true)
                        !settings.pinyinIntroSeen -> PinyinIntroRoute(firstRun = true)
                        else -> HomeRoute
                    }
                    nav.navigate(next) { popUpTo<CoverRoute> { inclusive = true } }
                }
            })
        }
        composable<LoginRoute> { entry ->
            val route = entry.toRoute<LoginRoute>()
            LoginScreen(firstRun = route.firstRun, onDone = {
                if (nav.currentDestination?.hasRoute<LoginRoute>() != true) return@LoginScreen
                settings.loginSeen = true
                if (route.firstRun) {
                    val next: Any = if (settings.pinyinIntroSeen) HomeRoute else PinyinIntroRoute(firstRun = true)
                    nav.navigate(next) { popUpTo<LoginRoute> { inclusive = true } }
                } else {
                    nav.popBackStack()
                }
            })
        }
        composable<PinyinIntroRoute> { entry ->
            val route = entry.toRoute<PinyinIntroRoute>()
            PinyinIntroScreen(firstRun = route.firstRun, onDone = {
                if (nav.currentDestination?.hasRoute<PinyinIntroRoute>() != true) return@PinyinIntroScreen
                if (route.firstRun) {
                    nav.navigate(HomeRoute) { popUpTo<PinyinIntroRoute> { inclusive = true } }
                } else {
                    nav.popBackStack()
                }
            })
        }
        composable<HomeRoute> {
            HomeScreen(
                onOpenCategory = { nav.navigate(CategoryRoute(it)) },
                onOpenPinyin = { nav.navigate(PinyinIntroRoute(firstRun = false)) },
                onOpenWords = { nav.navigate(WordListRoute()) },
                onOpenListenSpeak = { nav.navigate(ListenSpeakRoute) },
                onOpenExercise = { nav.navigate(ExerciseRoute) },
                onOpenChallenge = { nav.navigate(ChallengeRoute) },
                onOpenMissions = { nav.navigate(MissionsRoute) },
                onOpenMode = { modeId ->
                    // The first time in, the case is told before its scenes are shown.
                    val firstVisit = hasCaseIntro(modeId) && !settings.caseIntroSeen(modeId)
                    nav.navigate(if (firstVisit) CaseIntroRoute(modeId, firstRun = true) else ModeRoute(modeId))
                },
                onOpenLogin = { nav.navigate(LoginRoute(firstRun = false)) },
            )
        }
        composable<MissionsRoute> {
            DailyMissionsScreen(
                onBack = { nav.popBackStack() },
                onOpenLesson = { categoryId -> nav.navigate(CategoryRoute(categoryId)) },
                onOpenListenSpeak = { nav.navigate(ListenSpeakRoute) },
                onOpenExercise = { nav.navigate(ExerciseRoute) },
            )
        }
        composable<ChallengeRoute> { ChallengeGameScreen(onBack = { nav.popBackStack() }) }
        composable<ExerciseRoute> { ExerciseScreen(onBack = { nav.popBackStack() }) }
        composable<ListenSpeakRoute> { ListenSpeakScreen(onBack = { nav.popBackStack() }) }
        composable<WordListRoute> { entry ->
            WordListScreen(initialCategoryId = entry.toRoute<WordListRoute>().categoryId, onBack = { nav.popBackStack() })
        }
        composable<ModeRoute> { entry ->
            val route = entry.toRoute<ModeRoute>()
            ModeScreen(
                modeId = route.modeId,
                onBack = { nav.popBackStack() },
                onOpenScene = { index -> nav.navigate(SceneRoute(route.modeId, index)) },
                onOpenIntro = { nav.navigate(CaseIntroRoute(route.modeId, firstRun = false)) },
            )
        }
        composable<CaseIntroRoute> { entry ->
            val route = entry.toRoute<CaseIntroRoute>()
            CaseIntroScreen(modeId = route.modeId, firstRun = route.firstRun, onDone = {
                if (nav.currentDestination?.hasRoute<CaseIntroRoute>() != true) return@CaseIntroScreen
                if (route.firstRun) {
                    nav.navigate(ModeRoute(route.modeId)) { popUpTo<CaseIntroRoute> { inclusive = true } }
                } else {
                    nav.popBackStack()
                }
            })
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
                onOpenWords = { nav.navigate(WordListRoute(route.categoryId)) },
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
