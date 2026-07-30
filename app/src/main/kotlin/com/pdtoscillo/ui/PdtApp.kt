package com.pdtoscillo.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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
import com.pdtoscillo.core.network.InstrumentSession
import com.pdtoscillo.core.ui.component.UnavailableNotice
import com.pdtoscillo.feature.connection.ConnectionScreen
import com.pdtoscillo.feature.connection.ConnectionViewModel
import com.pdtoscillo.feature.connection.EscopeScreen
import com.pdtoscillo.feature.oscilloscope.ChannelsScreen
import com.pdtoscillo.feature.oscilloscope.OscilloscopeViewModel
import com.pdtoscillo.feature.oscilloscope.OverviewScreen
import com.pdtoscillo.navigation.ESCOPE_ROUTE
import com.pdtoscillo.navigation.ESCOPE_URL_ARG
import com.pdtoscillo.navigation.PdtDestination
import java.net.URLDecoder
import java.net.URLEncoder

@Composable
fun PdtApp(session: InstrumentSession) {
    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route
    val connectionState by session.client.connectionState.collectAsStateWithLifecycle()

    Scaffold(
        bottomBar = {
            NavigationBar {
                PdtDestination.bottomBar.forEach { destination ->
                    val enabled = !destination.requiresConnection || connectionState.isConnected
                    NavigationBarItem(
                        selected = currentRoute == destination.route,
                        enabled = enabled,
                        onClick = {
                            if (currentRoute != destination.route) {
                                navController.navigate(destination.route) {
                                    popUpTo(PdtDestination.CONNECTION.route)
                                    launchSingleTop = true
                                }
                            }
                        },
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
                ConnectionScreen(
                    viewModel = viewModel,
                    onOpenEscope = { url ->
                        val encoded = URLEncoder.encode(url, Charsets.UTF_8.name())
                        navController.navigate("$ESCOPE_ROUTE/$encoded")
                    },
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

            // Phase 3 以降で実装する画面。未実装であることを画面上で明示する。
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
)

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
