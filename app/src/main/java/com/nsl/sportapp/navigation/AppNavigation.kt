package com.nsl.sportapp.navigation

import androidx.compose.runtime.Composable
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import com.nsl.sportapp.ui.history.HistoryViewModel
import com.nsl.sportapp.ui.history.WorkoutDetailScreen
import com.nsl.sportapp.ui.history.WorkoutHistoryScreen
import com.nsl.sportapp.ui.home.HomeScreen
import com.nsl.sportapp.ui.interval.IntervalSetupScreen
import com.nsl.sportapp.ui.interval.IntervalSetupViewModel
import com.nsl.sportapp.ui.programs.TrainingProgramsScreen
import com.nsl.sportapp.ui.programs.TrainingProgramsViewModel
import com.nsl.sportapp.ui.workout.ActiveWorkoutScreen
import com.nsl.sportapp.ui.workout.WorkoutViewModel

object Routes {
    const val HOME = "home"
    const val INTERVAL_SETUP = "interval_setup"
    const val PROGRAMS = "programs"
    const val ACTIVE_WORKOUT = "active_workout"
    const val HISTORY = "history"
    const val WORKOUT_DETAIL = "workout_detail/{workoutId}"
    fun workoutDetail(id: Long) = "workout_detail/$id"
}

@Composable
fun AppNavigation(navController: NavHostController) {
    val workoutVm: WorkoutViewModel = viewModel()
    val historyVm: HistoryViewModel = viewModel()
    val programsVm: TrainingProgramsViewModel = viewModel()

    NavHost(navController = navController, startDestination = Routes.HOME) {

        composable(Routes.HOME) {
            HomeScreen(
                historyViewModel = historyVm,
                workoutViewModel = workoutVm,
                onStartFreeRun = {
                    navController.navigate(Routes.ACTIVE_WORKOUT)
                },
                onOpenIntervalSetup = {
                    navController.navigate(Routes.INTERVAL_SETUP)
                },
                onOpenHistory = {
                    navController.navigate(Routes.HISTORY)
                },
                onOpenPrograms = {
                    navController.navigate(Routes.PROGRAMS)
                }
            )
        }

        composable(Routes.INTERVAL_SETUP) {
            val intervalVm: IntervalSetupViewModel = viewModel()
            IntervalSetupScreen(
                viewModel = intervalVm,
                onStartWorkout = { config ->
                    workoutVm.startWorkout(
                        navController.context,
                        config
                    )
                    navController.navigate(Routes.ACTIVE_WORKOUT) {
                        popUpTo(Routes.HOME)
                    }
                },
                onSaveProgram = { name, config ->
                    programsVm.saveProgram(name, config)
                },
                onBack = { navController.popBackStack() }
            )
        }

        composable(Routes.PROGRAMS) {
            TrainingProgramsScreen(
                viewModel = programsVm,
                onStartProgram = { config ->
                    workoutVm.startWorkout(navController.context, config)
                    navController.navigate(Routes.ACTIVE_WORKOUT) {
                        popUpTo(Routes.HOME)
                    }
                },
                onCreateNew = {
                    navController.navigate(Routes.INTERVAL_SETUP)
                },
                onBack = { navController.popBackStack() }
            )
        }

        composable(Routes.ACTIVE_WORKOUT) {
            ActiveWorkoutScreen(
                viewModel = workoutVm,
                onWorkoutFinished = {
                    navController.navigate(Routes.HISTORY) {
                        popUpTo(Routes.HOME)
                    }
                },
                onBack = { navController.popBackStack() }
            )
        }

        composable(Routes.HISTORY) {
            WorkoutHistoryScreen(
                viewModel = historyVm,
                onWorkoutClick = { id ->
                    navController.navigate(Routes.workoutDetail(id))
                },
                onBack = { navController.popBackStack() }
            )
        }

        composable(
            route = Routes.WORKOUT_DETAIL,
            arguments = listOf(navArgument("workoutId") { type = NavType.LongType })
        ) { backStack ->
            val workoutId = backStack.arguments?.getLong("workoutId") ?: return@composable
            WorkoutDetailScreen(
                workoutId = workoutId,
                viewModel = historyVm,
                onBack = { navController.popBackStack() }
            )
        }
    }
}
