package com.pdtoscillo.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.pdtoscillo.R
import com.pdtoscillo.core.database.export.WaveformExporter
import com.pdtoscillo.core.network.InstrumentSession
import com.pdtoscillo.core.ui.component.UnavailableNotice
import com.pdtoscillo.feature.automation.AutomationScreen
import com.pdtoscillo.feature.automation.AutomationViewModel
import com.pdtoscillo.feature.connection.ConnectionScreen
import com.pdtoscillo.feature.connection.ConnectionViewModel
import com.pdtoscillo.feature.connection.EscopeScreen
import com.pdtoscillo.feature.console.ConsoleScreen
import com.pdtoscillo.feature.console.ConsoleViewModel
import com.pdtoscillo.feature.files.FilesScreen
import com.pdtoscillo.feature.files.FilesViewModel
import com.pdtoscillo.feature.measurement.MeasurementScreen
import com.pdtoscillo.feature.measurement.MeasurementViewModel
import com.pdtoscillo.feature.oscilloscope.ChannelsScreen
import com.pdtoscillo.feature.oscilloscope.OptionsScreen
import com.pdtoscillo.feature.oscilloscope.OptionsViewModel
import com.pdtoscillo.feature.oscilloscope.OscilloscopeViewModel
import com.pdtoscillo.feature.oscilloscope.OverviewScreen
import com.pdtoscillo.feature.oscilloscope.TriggerScreen
import com.pdtoscillo.feature.settings.SettingsScreen
import com.pdtoscillo.feature.waveform.WaveformScreen
import com.pdtoscillo.feature.waveform.WaveformViewModel
import com.pdtoscillo.navigation.ESCOPE_ROUTE
import com.pdtoscillo.navigation.ESCOPE_URL_ARG
import com.pdtoscillo.navigation.PdtDestination
import java.io.File
import java.net.URLDecoder
import java.net.URLEncoder

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PdtApp(session: InstrumentSession) {
    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route
    val connectionState by session.client.connectionState.collectAsStateWithLifecycle()

    fun navigateTo(destination: PdtDestination) {
        if (currentRoute != destination.route) {
            navController.navigate(destination.route) {
                popUpTo(PdtDestination.CONNECTION.route)
                launchSingleTop = true
            }
        }
    }

    Scaffold(
        topBar = {
            // 下部ナビゲーションへ 10 画面を並べると 1 つずつが小さくなりすぎるため、
            // 主要 5 画面を下部に置き、残りはここのメニューから開く。
            PdtTopBar(
                currentRoute = currentRoute,
                connected = connectionState.isConnected,
                onSelect = ::navigateTo,
            )
        },
        bottomBar = {
            NavigationBar {
                PdtDestination.bottomBar.forEach { destination ->
                    val enabled = !destination.requiresConnection || connectionState.isConnected
                    NavigationBarItem(
                        selected = currentRoute == destination.route,
                        enabled = enabled,
                        onClick = { navigateTo(destination) },
                        icon = { Icon(destination.icon, contentDescription = null) },
                        label = { Text(stringResource(destination.labelRes)) },
                    )
                }
            }
        },
    ) { padding ->
        NavHost(
            navController = navController,
            startDestination = PdtDestination.CONNECTION.route,
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            composable(PdtDestination.CONNECTION.route) {
                val viewModel: ConnectionViewModel = viewModel(
                    factory = ConnectionViewModel.factory(session),
                )
                val context = LocalContext.current
                ConnectionScreen(
                    viewModel = viewModel,
                    onOpenEscope = { url ->
                        val encoded = URLEncoder.encode(url, Charsets.UTF_8.name())
                        navController.navigate("$ESCOPE_ROUTE/$encoded")
                    },
                    onShareLog = { file -> shareLogFile(context, file) },
                )
            }

            composable("$ESCOPE_ROUTE/{$ESCOPE_URL_ARG}") { entry ->
                val encoded = entry.arguments?.getString(ESCOPE_URL_ARG).orEmpty()
                val url = runCatching { URLDecoder.decode(encoded, Charsets.UTF_8.name()) }.getOrDefault("")
                if (url.isBlank()) {
                    NotAvailableYet(PdtDestination.CONNECTION)
                } else {
                    EscopeScreen(url = url)
                }
            }

            composable(PdtDestination.OVERVIEW.route) {
                if (!connectionState.isConnected) {
                    RequiresConnection()
                } else {
                    OverviewScreen(
                        viewModel = viewModel(factory = OscilloscopeViewModel.factory(session)),
                        onOpenChannels = { navController.navigate(PdtDestination.CHANNELS.route) },
                    )
                }
            }

            composable(PdtDestination.CHANNELS.route) {
                if (!connectionState.isConnected) {
                    RequiresConnection()
                } else {
                    ChannelsScreen(viewModel = viewModel(factory = OscilloscopeViewModel.factory(session)))
                }
            }

            composable(PdtDestination.WAVEFORM.route) {
                if (!connectionState.isConnected) {
                    RequiresConnection()
                } else {
                    val context = LocalContext.current
                    val exporter = remember(context) { WaveformExporter(context.applicationContext) }
                    WaveformScreen(
                        viewModel = viewModel(factory = WaveformViewModel.factory(session, exporter)),
                    )
                }
            }

            composable(PdtDestination.TRIGGER.route) {
                if (!connectionState.isConnected) {
                    RequiresConnection()
                } else {
                    TriggerScreen(viewModel = viewModel(factory = OscilloscopeViewModel.factory(session)))
                }
            }

            composable(PdtDestination.MEASUREMENT.route) {
                if (!connectionState.isConnected) {
                    RequiresConnection()
                } else {
                    MeasurementScreen(viewModel = viewModel(factory = MeasurementViewModel.factory(session)))
                }
            }

            composable(PdtDestination.OPTIONS.route) {
                if (!connectionState.isConnected) {
                    RequiresConnection()
                } else {
                    OptionsScreen(viewModel = viewModel(factory = OptionsViewModel.factory(session)))
                }
            }

            composable(PdtDestination.CONSOLE.route) {
                if (!connectionState.isConnected) {
                    RequiresConnection()
                } else {
                    ConsoleScreen(viewModel = viewModel(factory = ConsoleViewModel.factory(session)))
                }
            }

            composable(PdtDestination.FILES.route) {
                if (!connectionState.isConnected) {
                    RequiresConnection()
                } else {
                    val context = LocalContext.current
                    val downloadDir = remember(context) { File(context.filesDir, "downloads") }
                    FilesScreen(viewModel = viewModel(factory = FilesViewModel.factory(session, downloadDir)))
                }
            }

            composable(PdtDestination.AUTOMATION.route) {
                if (!connectionState.isConnected) {
                    RequiresConnection()
                } else {
                    val context = LocalContext.current
                    val exporter = remember(context) { WaveformExporter(context.applicationContext) }
                    AutomationScreen(
                        viewModel = viewModel(factory = AutomationViewModel.factory(session, exporter)),
                    )
                }
            }

            composable(PdtDestination.SETTINGS.route) {
                SettingsScreen(session = session)
            }

            // すべての画面を実装済み。念のため残す分岐（到達しない）。
            PdtDestination.entries
                .filter { it !in IMPLEMENTED_DESTINATIONS }
                .forEach { destination ->
                    composable(destination.route) {
                        if (destination.requiresConnection && !connectionState.isConnected) {
                            RequiresConnection()
                        } else {
                            NotAvailableYet(destination)
                        }
                    }
                }
        }
    }
}

/** 既に実装済みで、個別に composable を登録している画面。 */
private val IMPLEMENTED_DESTINATIONS = setOf(
    PdtDestination.CONNECTION,
    PdtDestination.OVERVIEW,
    PdtDestination.CHANNELS,
    PdtDestination.WAVEFORM,
    PdtDestination.TRIGGER,
    PdtDestination.MEASUREMENT,
    PdtDestination.OPTIONS,
    PdtDestination.CONSOLE,
    PdtDestination.FILES,
    PdtDestination.AUTOMATION,
    PdtDestination.SETTINGS,
)

/**
 * ログファイルを他アプリへ渡す。
 *
 * アプリ専用ディレクトリのファイルはそのままでは共有できないため、FileProvider 経由で
 * 一時的な読み取り権限を付けて渡す。**送信先は利用者が選ぶ。** アプリからは自動送信しない。
 */
private fun shareLogFile(context: android.content.Context, file: java.io.File) {
    if (!file.exists()) return
    val uri = androidx.core.content.FileProvider.getUriForFile(
        context,
        "${context.packageName}.fileprovider",
        file,
    )
    val intent = android.content.Intent(android.content.Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(android.content.Intent.EXTRA_STREAM, uri)
        putExtra(android.content.Intent.EXTRA_SUBJECT, file.name)
        addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    // 送信先を選ぶ画面を必ず出す。既定のアプリへ黙って送らない。
    val chooser = android.content.Intent.createChooser(intent, file.name)
    runCatching { context.startActivity(chooser) }
}

/**
 * 上部バー。
 *
 * 下部ナビゲーションに載らない画面（チャンネル / トリガ / オプション / ファイル /
 * 自動測定 / 設定）はここのメニューから開く。実装済みなのに到達できない画面を作らない。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PdtTopBar(currentRoute: String?, connected: Boolean, onSelect: (PdtDestination) -> Unit) {
    var menuExpanded by remember { mutableStateOf(false) }
    val current = PdtDestination.fromRoute(currentRoute)

    TopAppBar(
        title = {
            Text(
                text = current?.let { stringResource(it.labelRes) } ?: stringResource(R.string.app_name),
            )
        },
        actions = {
            IconButton(
                onClick = { menuExpanded = true },
                modifier = Modifier.testTag(TOP_BAR_MENU_TAG),
            ) {
                Icon(Icons.Filled.MoreVert, contentDescription = stringResource(R.string.menu_more))
            }
            DropdownMenu(expanded = menuExpanded, onDismissRequest = { menuExpanded = false }) {
                PdtDestination.overflowMenu.forEach { destination ->
                    val enabled = !destination.requiresConnection || connected
                    DropdownMenuItem(
                        text = { Text(stringResource(destination.labelRes)) },
                        leadingIcon = { Icon(destination.icon, contentDescription = null) },
                        enabled = enabled,
                        onClick = {
                            menuExpanded = false
                            onSelect(destination)
                        },
                    )
                }
            }
        },
    )
}

/** UI テストからメニューを開くための目印。 */
const val TOP_BAR_MENU_TAG = "topBarMenu"

@Composable
private fun RequiresConnection() {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        contentAlignment = Alignment.Center,
    ) {
        UnavailableNotice(stringResource(R.string.requires_connection))
    }
}

/**
 * まだ実装していない画面。
 *
 * 「動くように見えて何も起きない」状態を作らないため、未実装であることを明示する。
 */
@Composable
private fun NotAvailableYet(destination: PdtDestination) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = stringResource(destination.labelRes),
                style = MaterialTheme.typography.titleLarge,
            )
            Text(
                text = "この画面は未実装です。実装状況は README のフェーズ一覧を参照してください。",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = 8.dp),
            )
        }
    }
}
