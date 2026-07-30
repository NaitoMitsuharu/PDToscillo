package com.pdtoscillo.navigation

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ShowChart
import androidx.compose.material.icons.filled.Cable
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.PlayCircle
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material.icons.filled.Tune
import androidx.compose.ui.graphics.vector.ImageVector
import com.pdtoscillo.R

/**
 * 画面の一覧。
 *
 * 接続以外の画面は接続が確立するまで意味を持たないため、[requiresConnection] を持たせて
 * 未接続時は入れないようにする（クラッシュではなく無効化で示す）。
 */
enum class PdtDestination(val route: String, val labelRes: Int, val icon: ImageVector, val requiresConnection: Boolean) {
    CONNECTION("connection", R.string.nav_connection, Icons.Filled.Cable, requiresConnection = false),
    OVERVIEW("overview", R.string.nav_overview, Icons.Filled.Speed, requiresConnection = true),
    WAVEFORM("waveform", R.string.nav_waveform, Icons.AutoMirrored.Filled.ShowChart, requiresConnection = true),
    CHANNELS("channels", R.string.nav_channels, Icons.Filled.Tune, requiresConnection = true),
    MEASUREMENT("measurement", R.string.nav_measurement, Icons.Filled.Speed, requiresConnection = true),
    AUTOMATION("automation", R.string.nav_automation, Icons.Filled.PlayCircle, requiresConnection = true),
    FILES("files", R.string.nav_files, Icons.Filled.Folder, requiresConnection = true),
    CONSOLE("console", R.string.nav_console, Icons.Filled.Terminal, requiresConnection = true),
    SETTINGS("settings", R.string.nav_settings, Icons.Filled.Settings, requiresConnection = false),
    ;

    companion object {
        /** 下部ナビゲーションへ出す主要画面。全部並べると小さくなりすぎるため絞る。 */
        val bottomBar: List<PdtDestination> = listOf(CONNECTION, OVERVIEW, WAVEFORM, MEASUREMENT, CONSOLE)

        fun fromRoute(route: String?): PdtDestination? = entries.firstOrNull { it.route == route }
    }
}

/** e*Scope はナビゲーション上は独立した画面として扱う。 */
const val ESCOPE_ROUTE = "escope"
const val ESCOPE_URL_ARG = "url"
