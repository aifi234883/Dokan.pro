package com.example.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountBalanceWallet
import androidx.compose.material.icons.filled.Dashboard
import androidx.compose.material.icons.filled.Inventory2
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.PointOfSale
import androidx.compose.material.icons.filled.Translate
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.credentials.CredentialManager
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.example.data.local.PreferencesManager
import com.example.data.repository.DukanProRepository
import com.example.i18n.stringsFor
import com.example.ui.auth.AuthScreen
import com.example.ui.auth.CreateOrJoinShopScreen
import com.example.ui.auth.signOutUser
import com.example.ui.components.SyncStatusPill
import com.example.ui.screens.DashboardScreen
import com.example.ui.screens.InventoryScreen
import com.example.ui.screens.KhataScreen
import com.example.ui.screens.MoreManagementScreen
import com.example.ui.screens.PosScreen
import com.example.ui.theme.DukanProTheme
import com.example.ui.viewmodel.DukanProViewModel
import com.example.util.observeNetworkOnline
import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.auth
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable

@Serializable data object PosRoute
@Serializable data object InventoryRoute
@Serializable data object KhataRoute
@Serializable data object DashboardRoute
@Serializable data object MoreRoute

private data class NavTab(
  val route: Any,
  val routeName: String,
  val label: String,
  val icon: ImageVector,
)

@Composable
fun DukanProAppRoot() {
  val context = LocalContext.current
  val prefs = remember(context) { PreferencesManager(context.applicationContext) }
  val appPrefs by prefs.preferencesFlow.collectAsStateWithLifecycle(initialValue = com.example.data.local.AppPreferences())
  val scope = rememberCoroutineScope()
  val strings = remember(appPrefs.isUrdu) { stringsFor(appPrefs.isUrdu) }

  var currentUser by remember { mutableStateOf(Firebase.auth.currentUser) }
  DisposableEffect(Unit) {
    val listener = FirebaseAuth.AuthStateListener { auth -> currentUser = auth.currentUser }
    Firebase.auth.addAuthStateListener(listener)
    onDispose { Firebase.auth.removeAuthStateListener(listener) }
  }

  val layoutDir = if (appPrefs.isUrdu) LayoutDirection.Rtl else LayoutDirection.Ltr
  CompositionLocalProvider(LocalLayoutDirection provides layoutDir) {
    DukanProTheme(darkTheme = appPrefs.isDarkTheme) {
      val user = currentUser
      if (user == null) {
        AuthScreen(
          strings = strings,
          onToggleLanguage = { scope.launch { prefs.setUrdu(!appPrefs.isUrdu) } },
          onAuthSuccess = { currentUser = Firebase.auth.currentUser },
        )
      } else {
        androidx.compose.runtime.key(user.uid) {
          AuthenticatedShell(
            currentUserId = user.uid,
            currentUserName = user.displayName ?: user.email?.substringBefore("@") ?: "Owner",
            currentUserEmail = user.email ?: "",
            prefs = prefs,
          )
        }
      }
    }
  }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AuthenticatedShell(
  currentUserId: String,
  currentUserName: String,
  currentUserEmail: String,
  prefs: PreferencesManager,
) {
  val context = LocalContext.current
  val scope = rememberCoroutineScope()
  val credentialManager = remember(context) { CredentialManager.create(context) }

  val viewModel: DukanProViewModel =
    viewModel(
      key = currentUserId,
      factory =
        viewModelFactory {
          initializer {
            val app = checkNotNull(this[APPLICATION_KEY])
            val repo = DukanProRepository(app)
            DukanProViewModel(repo, prefs, currentUserId, currentUserName, currentUserEmail)
          }
        },
    )

  val uiState by viewModel.uiState.collectAsStateWithLifecycle()
  val posState by viewModel.posState.collectAsStateWithLifecycle()
  val toastEvent by viewModel.toastEvent.collectAsStateWithLifecycle()
  val isOnline by remember(context) { observeNetworkOnline(context) }.collectAsStateWithLifecycle(initialValue = true)
  val strings = remember(uiState.preferences.isUrdu) { stringsFor(uiState.preferences.isUrdu) }
  val snackbarHostState = remember { SnackbarHostState() }

  LaunchedEffect(toastEvent) {
    val msg = toastEvent
    if (msg != null) {
      snackbarHostState.showSnackbar(msg)
      viewModel.consumeToast()
    }
  }

  if (uiState.isLoadingShops) {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
      CircularProgressIndicator()
    }
    return
  }

  if (uiState.activeShop == null) {
    CreateOrJoinShopScreen(
      strings = strings,
      pendingInvites = uiState.pendingInvites,
      onCreateShop = viewModel::createShop,
      onAcceptInvite = viewModel::acceptInvite,
      onSignOut = { signOutUser(credentialManager, scope) },
    )
    return
  }

  val shop = uiState.activeShop!!
  val totals = remember(posState, shop) { viewModel.computeTotals(posState, shop) }
  val navController = rememberNavController()
  val navBackStack by navController.currentBackStackEntryAsState()
  val currentRoute = navBackStack?.destination?.route ?: ""

  val tabs =
    listOf(
      NavTab(PosRoute, PosRoute::class.qualifiedName!!, strings.navPos, Icons.Default.PointOfSale),
      NavTab(InventoryRoute, InventoryRoute::class.qualifiedName!!, strings.navStock, Icons.Default.Inventory2),
      NavTab(KhataRoute, KhataRoute::class.qualifiedName!!, strings.navKhata, Icons.Default.AccountBalanceWallet),
      NavTab(DashboardRoute, DashboardRoute::class.qualifiedName!!, strings.navDashboard, Icons.Default.Dashboard),
      NavTab(MoreRoute, MoreRoute::class.qualifiedName!!, strings.navMore, Icons.Default.Menu),
    )

  var showShopSwitcher by remember { mutableStateOf(false) }

  BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
    val useRail = maxWidth >= 840.dp

    Scaffold(
      contentWindowInsets = WindowInsets.safeDrawing,
      snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
      topBar = {
        TopAppBar(
          title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
              Box {
                TextButton(onClick = { if (uiState.shops.size > 1) showShopSwitcher = true }) {
                  Text(
                    shop.name,
                    style = MaterialTheme.typography.titleLarge,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                  )
                }
                DropdownMenu(expanded = showShopSwitcher, onDismissRequest = { showShopSwitcher = false }) {
                  uiState.shops.forEach { s ->
                    DropdownMenuItem(
                      text = { Text(s.name) },
                      onClick = {
                        viewModel.selectShop(s.id)
                        showShopSwitcher = false
                      },
                    )
                  }
                }
              }
              Spacer(Modifier.width(6.dp))
              SyncStatusPill(isOnline = isOnline, hasPendingWrites = false, strings = strings)
            }
          },
          actions = {
            IconButton(
              onClick = viewModel::toggleLanguage,
              modifier = Modifier.testTag("topbar_language_toggle"),
            ) {
              Icon(Icons.Default.Translate, contentDescription = "Toggle EN / اردو")
            }
          },
        )
      },
      bottomBar = {
        if (!useRail) {
          NavigationBar(windowInsets = WindowInsets.navigationBars) {
            tabs.forEach { tab ->
              val selected = currentRoute.contains(tab.routeName)
              NavigationBarItem(
                selected = selected,
                onClick = {
                  navController.navigate(tab.route) {
                    launchSingleTop = true
                    restoreState = true
                    popUpTo(PosRoute) { saveState = true }
                  }
                },
                icon = { Icon(tab.icon, contentDescription = tab.label) },
                label = { Text(tab.label, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                modifier = Modifier.testTag("nav_${tab.routeName.substringAfterLast('.')}"),
              )
            }
          }
        }
      },
    ) { innerPadding ->
      Row(modifier = Modifier.fillMaxSize().padding(innerPadding)) {
        if (useRail) {
          NavigationRail {
            tabs.forEach { tab ->
              val selected = currentRoute.contains(tab.routeName)
              NavigationRailItem(
                selected = selected,
                onClick = {
                  navController.navigate(tab.route) {
                    launchSingleTop = true
                    restoreState = true
                    popUpTo(PosRoute) { saveState = true }
                  }
                },
                icon = { Icon(tab.icon, contentDescription = tab.label) },
                label = { Text(tab.label) },
              )
            }
          }
        }

        NavHost(
          navController = navController,
          startDestination = PosRoute,
          modifier = Modifier.weight(1f).fillMaxSize(),
        ) {
          composable<PosRoute> {
            PosScreen(
              uiState = uiState,
              posState = posState,
              totals = totals,
              strings = strings,
              onScanBarcode = viewModel::handleBarcodeScannedInPos,
              onClearUnmatchedBarcode = viewModel::clearUnmatchedBarcode,
              onDismissLastScanned = viewModel::dismissLastScannedProduct,
              onQuickAddProduct = viewModel::quickAddAndCartProduct,
              onAddProductToCart = viewModel::addProductToCart,
              onUpdateCartQty = viewModel::updateCartQty,
              onSetDiscount = viewModel::setDiscount,
              onSetPaymentMethod = viewModel::setPaymentMethod,
              onSelectCustomer = viewModel::selectPosCustomer,
              onSetReceived = viewModel::setReceivedInput,
              onCompleteSale = viewModel::completeSale,
              onHoldBill = viewModel::holdCurrentBill,
              onResumeHeldSale = viewModel::resumeHeldSale,
              onReturnSale = viewModel::returnSale,
              onClearCart = viewModel::clearCart,
              onDismissReceipt = viewModel::dismissReceipt,
              onShowReceipt = viewModel::showReceipt,
              onDismissNegativeWarning = viewModel::dismissNegativeStockWarning,
            )
          }
          composable<InventoryRoute> {
            InventoryScreen(
              uiState = uiState,
              strings = strings,
              onSaveProduct = viewModel::saveProduct,
              onUpdateProductPhoto = viewModel::updateProductPhoto,
              onDeleteProduct = viewModel::deleteProduct,
              onAdjustStock = viewModel::adjustStock,
              onImportCsv = viewModel::importCsvProducts,
            )
          }
          composable<KhataRoute> {
            KhataScreen(
              uiState = uiState,
              strings = strings,
              onSaveCustomer = viewModel::saveCustomer,
              onDeleteCustomer = viewModel::deleteCustomer,
              onReceivePayment = viewModel::receiveUdhaarPayment,
            )
          }
          composable<DashboardRoute> {
            DashboardScreen(
              uiState = uiState,
              isOnline = isOnline,
              strings = strings,
              onSeedDemoData = viewModel::seedDemoData,
              onTrackAdImpression = viewModel::trackAdImpression,
              onTrackAdClick = viewModel::trackAdClick,
              onOpenPos = { navController.navigate(PosRoute) { launchSingleTop = true } },
            )
          }
          composable<MoreRoute> {
            MoreManagementScreen(
              uiState = uiState,
              isOnline = isOnline,
              strings = strings,
              onToggleLanguage = viewModel::toggleLanguage,
              onToggleTheme = viewModel::toggleTheme,
              onSignOut = { signOutUser(credentialManager, scope) },
              onSaveSettings = viewModel::updateShopSettings,
              onInviteStaff = viewModel::inviteStaff,
              onUpdateMember = viewModel::updateMemberRole,
              onAddExpense = viewModel::addExpense,
              onSaveSupplier = viewModel::saveSupplier,
              onRecordPurchase = viewModel::recordPurchase,
              onPaySupplier = viewModel::paySupplier,
              onSaveDayClosing = viewModel::saveDayClosing,
              onSubmitUpgrade = viewModel::submitUpgradeRequest,
              onSeedDemoData = viewModel::seedDemoData,
              onTrackAdImpression = viewModel::trackAdImpression,
              onTrackAdClick = viewModel::trackAdClick,
            )
          }
        }
      }
    }
  }
}
