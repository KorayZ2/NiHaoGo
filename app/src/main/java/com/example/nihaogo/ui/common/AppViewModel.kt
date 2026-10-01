package com.example.nihaogo.ui.common

import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.example.nihaogo.AppContainer
import com.example.nihaogo.NiHaoGoApp
import com.example.nihaogo.data.content.SpecialMode
import com.example.nihaogo.data.content.Step
import com.example.nihaogo.data.user.GameRules
import com.example.nihaogo.data.user.ProgressRepository
import com.example.nihaogo.data.user.SpecialRules

/** Creates a ViewModel scoped to the current navigation destination, with access to [AppContainer]. */
@Composable
inline fun <reified VM : ViewModel> appViewModel(crossinline create: (AppContainer) -> VM): VM {
    val container = (LocalContext.current.applicationContext as NiHaoGoApp).container
    return viewModel(factory = viewModelFactory { initializer { create(container) } })
}

/** Saves a finished step and returns what it earned. */
suspend fun ProgressRepository.recordStep(categoryId: String, step: Step, stars: Int): GameRules.StepOutcome {
    lateinit var outcome: GameRules.StepOutcome
    update { current ->
        GameRules.applyStepResult(current, categoryId, step, stars).also { outcome = it }.progress
    }
    return outcome
}

/** Saves a finished special-mode scene and returns what it earned. */
suspend fun ProgressRepository.recordScene(mode: SpecialMode, index: Int, stars: Int): GameRules.StepOutcome {
    lateinit var outcome: GameRules.StepOutcome
    update { current ->
        SpecialRules.applySceneResult(current, mode, index, stars).also { outcome = it }.progress
    }
    return outcome
}

/** Final result shown at the end of each step. */
data class StepResult(val score: Int, val stars: Int, val coinsEarned: Int)
