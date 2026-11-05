package app.kvity.ui

import app.kvity.R
import androidx.compose.ui.res.stringResource
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import app.kvity.KvityApp
import app.kvity.notify.NotificationNav
import app.kvity.data.AttachmentOwner
import app.kvity.data.DebtView
import app.kvity.ui.components.AttachmentsCard
import app.kvity.data.EventKind
import app.kvity.domain.InstallmentState
import app.kvity.ui.backup.BackupScreen
import app.kvity.ui.calendar.CalendarScreen
import app.kvity.ui.exchange.ExportScreen
import app.kvity.ui.exchange.ImportScreen
import app.kvity.ui.calendar.CalendarViewModel
import app.kvity.ui.dashboard.DashboardScreen
import app.kvity.ui.dashboard.DashboardViewModel
import app.kvity.ui.debt.DebtDetailScreen
import app.kvity.ui.debt.DebtDetailViewModel
import app.kvity.ui.debt.NewDebtScreen
import app.kvity.ui.more.MoreItem
import app.kvity.ui.more.MoreScreen
import app.kvity.ui.people.PeopleScreen
import app.kvity.ui.people.PersonDetailScreen
import app.kvity.ui.reminder.ReminderContext
import app.kvity.ui.reminder.ReminderDialog
import androidx.fragment.app.FragmentActivity
import app.kvity.security.Biometrics
import app.kvity.ui.settings.GeneralSettingsScreen
import app.kvity.ui.settings.NotificationsSection
import app.kvity.ui.settings.SecuritySection
import app.kvity.ui.settings.RatesScreen
import app.kvity.ui.settings.TemplatesScreen
import app.kvity.ui.subscription.SubscriptionDetailScreen
import app.kvity.ui.subscription.SubscriptionFormScreen
import java.time.LocalDate
import java.time.temporal.ChronoUnit
import kotlinx.coroutines.launch

object Routes {
    const val DASHBOARD = "dashboard"
    const val CALENDAR = "calendar"
    const val PEOPLE = "people"
    const val MORE = "more"
    const val SETTINGS = "settings"
    const val RATES = "settings/rates"
    const val TEMPLATES = "settings/templates"
    const val BACKUP = "backup"
    const val EXPORT = "export"
    const val IMPORT = "import"
    const val NEW_DEBT = "debt/new"
    const val DEBT = "debt/{id}"
    const val EDIT_DEBT = "debt/{id}/edit"
    const val PERSON = "person/{id}"
    const val NEW_SUB = "sub/new"
    const val SUB = "sub/{id}"
    const val EDIT_SUB = "sub/{id}/edit"
    fun debt(id: String) = "debt/$id"
    fun editDebt(id: String) = "debt/$id/edit"
    fun person(id: String) = "person/$id"
    fun sub(id: String) = "sub/$id"
    fun editSub(id: String) = "sub/$id/edit"
}

private val tabs = listOf(
    Routes.DASHBOARD to R.string.nav_tab_home,
    Routes.CALENDAR to R.string.nav_tab_calendar,
    Routes.PEOPLE to R.string.nav_tab_people,
    Routes.MORE to R.string.nav_tab_more,
)

@Composable
fun AppNavigation() {
    val context = LocalContext.current
    val resources = androidx.compose.ui.platform.LocalResources.current
    val app = context.applicationContext as KvityApp
    val nav = rememberNavController()
    val scope = rememberCoroutineScope()
    val settings by app.settings.settings.collectAsStateWithLifecycle()
    val route = nav.currentBackStackEntryAsState().value?.destination?.route
    var reminder by remember { mutableStateOf<ReminderContext?>(null) }

    // переход по нажатию на уведомление
    val notifyRoute by NotificationNav.pendingRoute.collectAsStateWithLifecycle()
    LaunchedEffect(notifyRoute) {
        notifyRoute?.let {
            nav.navigate(it) { launchSingleTop = true }
            NotificationNav.consume()
        }
    }

    val peopleVm: PeopleViewModel = viewModel(
        key = "people",
        factory = viewModelFactory { initializer { PeopleViewModel(app.people, app.debts, app.subscriptions) } },
    )
    val persons by peopleVm.persons.collectAsStateWithLifecycle()
    // архивные контрагенты в подсказках имён не показываются
    val knownNames = persons.filter { !it.archived }.map { it.person.name }
    val templates by app.reference.templates.collectAsStateWithLifecycle(emptyList())

    Scaffold(
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        bottomBar = {
            if (route in tabs.map { it.first }) {
                NavigationBar {
                    tabs.forEach { (r, title) ->
                        NavigationBarItem(
                            selected = route == r,
                            onClick = {
                                nav.navigate(r) {
                                    popUpTo(Routes.DASHBOARD) { saveState = true }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            },
                            icon = { Text(tabIcon(r)) },
                            label = { Text(stringResource(title)) },
                        )
                    }
                }
            }
        },
    ) { inner ->
        NavHost(navController = nav, startDestination = Routes.DASHBOARD, modifier = Modifier.padding(inner)) {

            composable(Routes.DASHBOARD) {
                val vm: DashboardViewModel = viewModel(
                    factory = viewModelFactory { initializer { DashboardViewModel(app.debts, app.subscriptions, app.settings) } },
                )
                val state by vm.state.collectAsStateWithLifecycle()
                DashboardScreen(
                    state = state,
                    onAddDebt = { nav.navigate(Routes.NEW_DEBT) },
                    onOpenDebt = { nav.navigate(Routes.debt(it)) },
                    onAddSubscription = { nav.navigate(Routes.NEW_SUB) },
                    onOpenSubscription = { nav.navigate(Routes.sub(it)) },
                    onMarkPaid = vm::markPaid,
                    onOpenSettings = { nav.navigate(Routes.SETTINGS) },
                    backupHint = (state.debts.isNotEmpty() || state.subs.isNotEmpty()) &&
                        System.currentTimeMillis() - maxOf(settings.lastBackupMillis, settings.installedAtMillis) > 30L * 24 * 3600 * 1000,
                    onOpenBackup = { nav.navigate(Routes.BACKUP) },
                    onUnarchiveDebt = vm::unarchiveDebt,
                )
            }

            composable(Routes.CALENDAR) {
                val vm: CalendarViewModel = viewModel(
                    factory = viewModelFactory { initializer { CalendarViewModel(app.debts, app.subscriptions) } },
                )
                val events by vm.events.collectAsStateWithLifecycle()
                val undated by vm.undated.collectAsStateWithLifecycle()
                CalendarScreen(
                    events = events,
                    undated = undated,
                    mainCurrency = settings.defaultCurrency,
                    firstDayOfWeek = settings.firstDayOfWeek,
                    onOpenEvent = { e -> nav.navigate(if (e.kind == EventKind.DEBT) Routes.debt(e.refId) else Routes.sub(e.refId)) },
                    onMarkSharePaid = vm::markSharePaid,
                )
            }

            composable(Routes.PEOPLE) {
                PeopleScreen(
                    persons.filter { !it.archived }, onOpen = { nav.navigate(Routes.person(it)) },
                    archived = persons.filter { it.archived }, onUnarchive = peopleVm::unarchive,
                )
            }

            composable(Routes.MORE) {
                MoreScreen(moreItems(nav))
            }

            composable(Routes.PERSON, arguments = listOf(navArgument("id") { type = NavType.StringType })) { entry ->
                val id = requireNotNull(entry.arguments?.getString("id"))
                val debts by peopleVm.debts.collectAsStateWithLifecycle()
                val subs by peopleVm.subs.collectAsStateWithLifecycle()
                PersonDetailScreen(
                    person = persons.firstOrNull { it.person.id == id },
                    debts = debts.filter { it.debt.counterpartyId == id },
                    subs = subs.filter { v -> v.shares.any { it.share.counterpartyId == id } || v.subscription.payerCounterpartyId == id },
                    onBack = { nav.popBackStack() },
                    onSave = { n, p, note -> peopleVm.save(id, n, p, note) },
                    onDelete = { done -> peopleVm.delete(id) { ok -> done(ok); if (ok) nav.popBackStack() } },
                    onArchive = { peopleVm.archive(id) },
                    onUnarchive = { peopleVm.unarchive(id) },
                    onOpenDebt = { nav.navigate(Routes.debt(it)) },
                    onOpenSubscription = { nav.navigate(Routes.sub(it)) },
                    otherNames = persons.filter { it.person.id != id }.map { it.person.name.trim().lowercase() }.toSet(),
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
                    defaultCurrency = settings.defaultCurrency,
                    currencies = settings.enabledCurrencies,
                    knownNames = knownNames,
                )
            }

            composable(Routes.DEBT, arguments = listOf(navArgument("id") { type = NavType.StringType })) { entry ->
                val id = requireNotNull(entry.arguments?.getString("id"))
                val vm: DebtDetailViewModel = viewModel(
                    key = id, factory = viewModelFactory { initializer { DebtDetailViewModel(app.debts, id) } },
                )
                val view by vm.view.collectAsStateWithLifecycle()
                val recalcOffer by vm.recalcOffer.collectAsStateWithLifecycle()
                DebtDetailScreen(
                    view = view,
                    onBack = { nav.popBackStack() },
                    onAddPayment = { date, minor, plannedId, monthOnly, method, note ->
                        vm.addPayment(date, minor, plannedId, monthOnly, method, note)
                    },
                    onUpdatePayment = vm::updatePayment,
                    onSetArchived = vm::setArchived,
                    onSetNotify = vm::setNotify,
                    onUpdatePlanned = vm::updatePlanned,
                    onSkipPlanned = vm::skipPlanned,
                    onDeletePlanned = vm::deletePlanned,
                    recalcOffer = recalcOffer,
                    onRecalc = vm::recalc,
                    onDismissRecalc = vm::dismissRecalc,
                    onDeletePayment = vm::deletePayment,
                    onDeleteDebt = { vm.deleteDebt(); nav.popBackStack() },
                    onEdit = { nav.navigate(Routes.editDebt(id)) },
                    onRemind = { view?.let { reminder = debtReminder(it, resources.getString(R.string.nav_due_not_set)) } },
                    extra = { AttachmentsCard(app.attachments, AttachmentOwner.DEBT, id, title = stringResource(R.string.nav_attachments_debt)) },
                    paymentAttachments = { pid -> AttachmentsCard(app.attachments, AttachmentOwner.PAYMENT, pid, title = stringResource(R.string.nav_attachments_receipts), framed = false) },
                )
            }

            composable(Routes.EDIT_DEBT, arguments = listOf(navArgument("id") { type = NavType.StringType })) { entry ->
                val id = requireNotNull(entry.arguments?.getString("id"))
                val vm: DebtDetailViewModel = viewModel(
                    key = id, factory = viewModelFactory { initializer { DebtDetailViewModel(app.debts, id) } },
                )
                val view by vm.view.collectAsStateWithLifecycle()
                view?.let {
                    NewDebtScreen(
                        initial = it,
                        onBack = { nav.popBackStack() },
                        onSave = { input -> scope.launch { app.debts.updateDebt(id, input); nav.popBackStack() } },
                        defaultCurrency = settings.defaultCurrency,
                        currencies = (settings.enabledCurrencies + it.debt.currency).distinct(),
                        knownNames = knownNames,
                    )
                }
            }

            composable(Routes.NEW_SUB) {
                SubscriptionFormScreen(
                    initial = null, defaultCurrency = settings.defaultCurrency, currencies = settings.enabledCurrencies,
                    knownNames = knownNames, onBack = { nav.popBackStack() },
                    onSave = { input ->
                        scope.launch {
                            val id = app.subscriptions.add(input)
                            nav.navigate(Routes.sub(id)) { popUpTo(Routes.DASHBOARD) }
                        }
                    },
                )
            }

            composable(Routes.SUB, arguments = listOf(navArgument("id") { type = NavType.StringType })) { entry ->
                val id = requireNotNull(entry.arguments?.getString("id"))
                val vm: SubscriptionDetailViewModel = viewModel(
                    key = id, factory = viewModelFactory { initializer { SubscriptionDetailViewModel(app.subscriptions, id) } },
                )
                val view by vm.view.collectAsStateWithLifecycle()
                SubscriptionDetailScreen(
                    view = view,
                    onBack = { nav.popBackStack() },
                    onEdit = { nav.navigate(Routes.editSub(id)) },
                    onDelete = { vm.delete(); nav.popBackStack() },
                    onSetState = vm::setState,
                    onSetNotify = vm::setNotify,
                    onAddShare = vm::addShare,
                    onDeleteShare = vm::deleteShare,
                    onMarkPaid = vm::markPaid,
                    onMarkUnpaid = vm::markUnpaid,
                    onMarkChargePaid = vm::markChargePaid,
                    onMarkChargeUnpaid = vm::markChargeUnpaid,
                    onRemind = { sh, period ->
                        reminder = ReminderContext(
                            name = sh.counterpartyName, phone = sh.counterpartyPhone,
                            amount = formatMoney(sh.share.amountMinor, sh.share.currency),
                            remaining = formatMoney(sh.share.amountMinor, sh.share.currency),
                            due = "${view?.subscription?.name.orEmpty()}, ${formatDate(period)}",
                            overdueDays = ChronoUnit.DAYS.between(period, LocalDate.now()).coerceAtLeast(0),
                            shareId = sh.share.id, counterpartyId = sh.share.counterpartyId,
                        )
                    },
                    knownNames = knownNames,
                    attachments = { sid -> AttachmentsCard(app.attachments, AttachmentOwner.SUBSCRIPTION, sid) },
                )
            }

            composable(Routes.EDIT_SUB, arguments = listOf(navArgument("id") { type = NavType.StringType })) { entry ->
                val id = requireNotNull(entry.arguments?.getString("id"))
                val vm: SubscriptionDetailViewModel = viewModel(
                    key = id, factory = viewModelFactory { initializer { SubscriptionDetailViewModel(app.subscriptions, id) } },
                )
                val view by vm.view.collectAsStateWithLifecycle()
                view?.let {
                    SubscriptionFormScreen(
                        initial = it, defaultCurrency = settings.defaultCurrency,
                        currencies = (settings.enabledCurrencies + it.subscription.currency).distinct(),
                        knownNames = knownNames, onBack = { nav.popBackStack() },
                        onSave = { input -> vm.update(input); nav.popBackStack() },
                    )
                }
            }

            composable(Routes.SETTINGS) {
                GeneralSettingsScreen(
                    settings = settings,
                    onChange = { app.settings.update(it) },
                    onBack = { nav.popBackStack() },
                    onOpenRates = { nav.navigate(Routes.RATES) },
                    onOpenTemplates = { nav.navigate(Routes.TEMPLATES) },
                    sections = {
                        NotificationsSection(settings) { app.settings.update(it) }
                        SecuritySection(
                            settings = settings, pinStore = app.pin,
                            biometricAvailable = (context as? FragmentActivity)?.let { Biometrics.isAvailable(it) } == true,
                            onChange = { app.settings.update(it) },
                        )
                    },
                )
            }

            composable(Routes.RATES) {
                val rates by app.reference.rates.collectAsStateWithLifecycle(emptyList())
                RatesScreen(
                    rates = rates, currencies = settings.enabledCurrencies,
                    onSave = { f, t, r, id -> scope.launch { app.reference.saveRate(f, t, r, id = id) } },
                    onDelete = { scope.launch { app.reference.deleteRate(it) } },
                    onBack = { nav.popBackStack() },
                )
            }

            composable(Routes.EXPORT) { ExportScreen(app, onBack = { nav.popBackStack() }) }
            composable(Routes.IMPORT) { ImportScreen(app, onBack = { nav.popBackStack() }) }

            composable(Routes.BACKUP) {
                BackupScreen(manager = app.backup, lastBackupMillis = settings.lastBackupMillis, onBack = { nav.popBackStack() })
            }

            composable(Routes.TEMPLATES) {
                TemplatesScreen(
                    templates = templates,
                    onSave = { n, t, id -> scope.launch { app.reference.saveTemplate(n, t, id) } },
                    onDelete = { scope.launch { app.reference.deleteTemplate(it) } },
                    onBack = { nav.popBackStack() },
                )
            }
        }
    }

    reminder?.let { ctx ->
        ReminderDialog(
            ctx = ctx, templates = templates,
            onLog = { channel, template -> scope.launch { app.reference.logReminder(ctx.debtId, ctx.shareId, ctx.counterpartyId, channel, template) } },
            onDismiss = { reminder = null },
        )
    }
}

private fun tabIcon(route: String) = when (route) {
    Routes.DASHBOARD -> "▦"
    Routes.CALENDAR -> "📅"
    Routes.PEOPLE -> "👥"
    else -> "⋯"
}

@Composable
private fun moreItems(nav: NavHostController) = listOf(
    MoreItem(stringResource(R.string.nav_more_settings), stringResource(R.string.nav_more_settings_sub)) { nav.navigate(Routes.SETTINGS) },
    MoreItem(stringResource(R.string.nav_more_backup), stringResource(R.string.nav_more_backup_sub)) { nav.navigate(Routes.BACKUP) },
    MoreItem(stringResource(R.string.nav_more_export), stringResource(R.string.nav_more_export_sub)) { nav.navigate(Routes.EXPORT) },
    MoreItem(stringResource(R.string.nav_more_import), stringResource(R.string.nav_more_import_sub)) { nav.navigate(Routes.IMPORT) },
    MoreItem(stringResource(R.string.nav_more_rates), stringResource(R.string.nav_more_rates_sub)) { nav.navigate(Routes.RATES) },
    MoreItem(stringResource(R.string.nav_more_templates), stringResource(R.string.nav_more_templates_sub)) { nav.navigate(Routes.TEMPLATES) },
)

private fun debtReminder(v: DebtView, notSet: String): ReminderContext {
    val s = v.summary
    val firstUnpaid = s.plan.firstOrNull { it.state != InstallmentState.PAID }
    return ReminderContext(
        name = v.counterpartyName, phone = v.counterpartyPhone,
        amount = formatMoney(s.principalMinor, v.debt.currency),
        remaining = formatMoney(s.remainingMinor, v.debt.currency),
        due = firstUnpaid?.let { formatDate(it.dueDate) } ?: notSet,
        overdueDays = s.overdueDays, debtId = v.debt.id, counterpartyId = v.debt.counterpartyId,
    )
}
