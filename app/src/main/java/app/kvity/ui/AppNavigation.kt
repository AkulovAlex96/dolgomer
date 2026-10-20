package app.kvity.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import app.kvity.KvityApp
import app.kvity.ui.dashboard.DashboardScreen
import app.kvity.ui.dashboard.DashboardViewModel
import app.kvity.ui.debt.DebtDetailScreen
import app.kvity.ui.debt.DebtDetailViewModel
import app.kvity.ui.debt.NewDebtScreen
import kotlinx.coroutines.launch

private object Routes {
    const val DASHBOARD = "dashboard"
    const val NEW_DEBT = "debt/new"
    const val DEBT = "debt/{id}"
    fun debt(id: String) = "debt/$id"
}

@Composable
fun AppNavigation() {
    val app = LocalContext.current.applicationContext as KvityApp
    val nav = rememberNavController()
    val scope = rememberCoroutineScope()

    NavHost(navController = nav, startDestination = Routes.DASHBOARD) {
        composable(Routes.DASHBOARD) {
            val vm: DashboardViewModel = viewModel(factory = viewModelFactory { initializer { DashboardViewModel(app.debts) } })
            val state by vm.state.collectAsStateWithLifecycle()
            DashboardScreen(
                state = state,
                onAddDebt = { nav.navigate(Routes.NEW_DEBT) },
                onOpenDebt = { nav.navigate(Routes.debt(it)) },
            )
        }

        composable(Routes.NEW_DEBT) {
            NewDebtScreen(
                onBack = { nav.popBackStack() },
                onSave = { input ->
                    scope.launch {
                        val id = app.debts.addDebt(input)
                        nav.navigate(Routes.debt(id)) { popUpTo(Routes.DASHBOARD) }
                    }
                },
            )
        }

        composable(Routes.DEBT, arguments = listOf(navArgument("id") { type = NavType.StringType })) { entry ->
            val id = requireNotNull(entry.arguments?.getString("id"))
            val vm: DebtDetailViewModel = viewModel(
                key = id,
                factory = viewModelFactory { initializer { DebtDetailViewModel(app.debts, id) } },
            )
            val view by vm.view.collectAsStateWithLifecycle()
            DebtDetailScreen(
                view = view,
                onBack = { nav.popBackStack() },
                onAddPayment = vm::addPayment,
                onDeletePayment = vm::deletePayment,
                onDeleteDebt = {
                    vm.deleteDebt()
                    nav.popBackStack()
                },
            )
        }
    }
}
