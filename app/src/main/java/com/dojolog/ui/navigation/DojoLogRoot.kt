package com.dojolog.ui.navigation

import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.Insights
import androidx.compose.material.icons.outlined.SportsMartialArts
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavBackStackEntry
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.dojolog.data.TrainingRepository
import com.dojolog.ui.calendar.CalendarScreen
import com.dojolog.ui.session.SessionDetailScreen
import com.dojolog.ui.session.SessionEditorScreen
import com.dojolog.ui.session.SessionEditorViewModel
import com.dojolog.ui.stats.StatsScreen
import com.dojolog.ui.techniques.TechniqueDetailScreen
import com.dojolog.ui.techniques.TechniquesScreen
import com.dojolog.ui.theme.DisciplineColors
import com.dojolog.ui.theme.LocalDisciplineColors
import java.time.LocalDate

private object Routes {
    const val CALENDAR = "calendar"
    const val TECHNIQUES = "techniques"
    const val STATS = "stats"
    const val SESSION = "session/{sessionId}"
    const val EDITOR = "editor?sessionId={sessionId}&date={date}"
    const val TECHNIQUE = "technique/{techniqueId}"

    fun session(id: Long) = "session/$id"
    fun newSession(date: LocalDate) = "editor?date=${date.toEpochDay()}"
    fun editSession(id: Long) = "editor?sessionId=$id"
    fun technique(id: Long) = "technique/$id"
}

private enum class TopLevel(val route: String, val label: String, val icon: ImageVector) {
    CALENDAR(Routes.CALENDAR, "Calendar", Icons.Outlined.CalendarMonth),
    TECHNIQUES(Routes.TECHNIQUES, "Techniques", Icons.Outlined.SportsMartialArts),
    STATS(Routes.STATS, "Stats", Icons.Outlined.Insights),
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun DojoLogRoot(repository: TrainingRepository) {
    // Every screen colours martial arts the same way, so the assignment is provided once here.
    val slotsFlow = remember(repository) { repository.observeDisciplineSlots() }
    val slots by slotsFlow.collectAsStateWithLifecycle(initialValue = emptyMap())
    val disciplineColors = remember(slots) { DisciplineColors(slots) }
    CompositionLocalProvider(LocalDisciplineColors provides disciplineColors) {
        DojoLogScaffold()
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun DojoLogScaffold() {
    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route
    val showBottomBar = TopLevel.entries.any { it.route == currentRoute }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        contentWindowInsets = WindowInsets(0),
        bottomBar = {
            if (showBottomBar) {
                NavigationBar(containerColor = MaterialTheme.colorScheme.surfaceContainerLow) {
                    TopLevel.entries.forEach { destination ->
                        NavigationBarItem(
                            selected = currentRoute == destination.route,
                            onClick = { navController.navigateTopLevel(destination.route) },
                            icon = { Icon(destination.icon, contentDescription = null) },
                            label = { Text(destination.label) },
                        )
                    }
                }
            }
        },
    ) { padding ->
        NavHost(
            navController = navController,
            startDestination = Routes.CALENDAR,
            modifier = Modifier
                .padding(padding)
                .consumeWindowInsets(padding),
        ) {
            composable(Routes.CALENDAR) {
                CalendarScreen(
                    onOpenSession = { navController.navigate(Routes.session(it)) },
                    onAddSession = { navController.navigate(Routes.newSession(it)) },
                )
            }
            composable(Routes.TECHNIQUES) {
                TechniquesScreen(onOpenTechnique = { navController.navigate(Routes.technique(it)) })
            }
            composable(Routes.STATS) {
                StatsScreen(onOpenTechnique = { navController.navigate(Routes.technique(it)) })
            }
            composable(
                Routes.SESSION,
                arguments = listOf(navArgument("sessionId") { type = NavType.LongType }),
            ) { entry ->
                SessionDetailScreen(
                    onBack = { navController.popFrom(entry) },
                    onEdit = { navController.navigate(Routes.editSession(it)) },
                    onOpenTechnique = { navController.navigate(Routes.technique(it)) },
                )
            }
            composable(
                Routes.EDITOR,
                arguments = listOf(
                    navArgument("sessionId") {
                        type = NavType.LongType
                        defaultValue = 0L
                    },
                    navArgument("date") {
                        type = NavType.LongType
                        defaultValue = SessionEditorViewModel.NO_DATE
                    },
                ),
            ) { entry ->
                SessionEditorScreen(onDone = { navController.popFrom(entry) })
            }
            composable(
                Routes.TECHNIQUE,
                arguments = listOf(navArgument("techniqueId") { type = NavType.LongType }),
            ) { entry ->
                TechniqueDetailScreen(
                    onBack = { navController.popFrom(entry) },
                    onOpenSession = { navController.navigate(Routes.session(it)) },
                )
            }
        }
    }
}

/** Pops [entry] only while it is the visible screen, so a double tap can't pop twice. */
private fun NavHostController.popFrom(entry: NavBackStackEntry) {
    if (entry.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) popBackStack()
}

private fun NavHostController.navigateTopLevel(route: String) {
    navigate(route) {
        popUpTo(graph.findStartDestination().id) { saveState = true }
        launchSingleTop = true
        restoreState = true
    }
}
