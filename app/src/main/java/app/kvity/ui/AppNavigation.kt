package app.kvity.ui

import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
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
import app.kvity.data.AttachmentOwner
import app.kvity.data.DebtView
import app.kvity.ui.components.AttachmentsCard
import app.kvity.data.EventKind
import app.kvity.domain.InstallmentState
import app.kvity.ui.backup.BackupScreen
import app.kvity.ui.calendar.CalendarScreen
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
    Routes.DASHBOARD to "Главная",
    Routes.CALENDAR to "Календарь",
    Routes.PEOPLE to "Люди",
    Routes.MORE to "Ещё",
)

@Composable
fun AppNavigation() {
    val context = LocalContext.current
    val app = context.applicationContext as KvityApp
    val nav = rememberNavController()
    val scope = rememberCoroutineScope()
    val settings by app.settings.settings.collectAsStateWithLifecycle()
    val route = nav.currentBackStackEntryAsState().value?.destination?.route
    var reminder by remember { mutableStateOf<ReminderContext?>(null) }

    val peopleVm: PeopleViewModel = viewModel(
        key = "people",
        factory = viewModelFactory { initializer { PeopleViewModel(app.people, app.debts, app.subscriptions) } },
    )
    val persons by peopleVm.persons.collectAsStateWithLifecycle()
    val knownNames = persons.map { it.person.name }
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
                            label = { Text(title) },
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
                    onOpenSettings = { nav.navigate(Routes.SETTINGS) },
                    backupHint = (state.debts.isNotEmpty() || state.subs.isNotEmpty()) &&
                        System.currentTimeMillis() - maxOf(settings.lastBackupMillis, settings.installedAtMillis) > 30L * 24 * 3600 * 1000,
                    onOpenBackup = { nav.navigate(Routes.BACKUP) },
                )
            }

            composable(Routes.CALENDAR) {
                val vm: CalendarViewModel = viewModel(
                    factory = viewModelFactory { initializer { CalendarViewModel(app.debts, app.subscriptions) } },
                )
                val events by vm.events.collectAsStateWithLifecycle()
                CalendarScreen(
                    events = events,
                    mainCurrency = settings.defaultCurrency,
                    firstDayOfWeek = settings.firstDayOfWeek,
                    onOpenEvent = { e -> nav.navigate(if (e.kind == EventKind.DEBT) Routes.debt(e.refId) else Routes.sub(e.refId)) },
                    onMarkSharePaid = vm::markSharePaid,
                )
            }

            composable(Routes.PEOPLE) {
                PeopleScreen(persons, onOpen = { nav.navigate(Routes.person(it)) })
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
                    onDelete = { peopleVm.delete(id); nav.popBackStack() },
                    onOpenDebt = { nav.navigate(Routes.debt(it)) },
                    onOpenSubscription = { nav.navigate(Routes.sub(it)) },
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
                DebtDetailScreen(
                    view = view,
                    onBack = { nav.popBackStack() },
                    onAddPayment = vm::addPayment,
                    onDeletePayment = vm::deletePayment,
                    onDeleteDebt = { vm.deleteDebt(); nav.popBackStack() },
                    onEdit = { nav.navigate(Routes.editDebt(id)) },
                    onRemind = { view?.let { reminder = debtReminder(it) } },
                    extra = { AttachmentsCard(app.attachments, AttachmentOwner.DEBT, id, title = "Вложения (расписка, документы)") },
                    paymentAttachments = { pid -> AttachmentsCard(app.attachments, AttachmentOwner.PAYMENT, pid, title = "Чеки", framed = false) },
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
                    onAddShare = vm::addShare,
                    onDeleteShare = vm::deleteShare,
                    onMarkPaid = vm::markPaid,
                    onMarkUnpaid = vm::markUnpaid,
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

private fun moreItems(nav: NavHostController) = listOf(
    MoreItem("Настройки", "Тема, валюты, уведомления, безопасность") { nav.navigate(Routes.SETTINGS) },
    MoreItem("Резервная копия", "Сохранить данные в файл или Google Drive, восстановить") { nav.navigate(Routes.BACKUP) },
    MoreItem("Курсы валют", "Для необязательного пересчёта итогов") { nav.navigate(Routes.RATES) },
    MoreItem("Шаблоны сообщений", "Напоминания должникам") { nav.navigate(Routes.TEMPLATES) },
)

private fun debtReminder(v: DebtView): ReminderContext {
    val s = v.summary
    val firstUnpaid = s.plan.firstOrNull { it.state != InstallmentState.PAID }
    return ReminderContext(
        name = v.counterpartyName, phone = v.counterpartyPhone,
        amount = formatMoney(s.principalMinor, v.debt.currency),
        remaining = formatMoney(s.remainingMinor, v.debt.currency),
        due = firstUnpaid?.let { formatDate(it.dueDate) } ?: "не задан",
        overdueDays = s.overdueDays, debtId = v.debt.id, counterpartyId = v.debt.counterpartyId,
    )
}
