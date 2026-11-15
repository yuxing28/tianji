package com.tianji.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Explore
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Style
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.tianji.app.data.AppSettingsStore
import com.tianji.app.ui.screens.AppTabs
import com.tianji.app.ui.screens.BaziScreen
import com.tianji.app.ui.screens.FengshuiScreen
import com.tianji.app.ui.screens.HistoryScreen
import com.tianji.app.ui.screens.MarriageScreen
import com.tianji.app.ui.screens.SettingsScreen
import com.tianji.app.ui.screens.TarotScreen
import com.tianji.app.ui.screens.TodayScreen
import com.tianji.app.ui.theme.TianjiTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.auto(android.graphics.Color.TRANSPARENT, android.graphics.Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.auto(android.graphics.Color.TRANSPARENT, android.graphics.Color.TRANSPARENT),
        )
        setContent {
            val themeMode by AppSettingsStore.flow(this).collectAsState(initial = "system")
            val dynamicColor by AppSettingsStore.dynamicColorFlow(this).collectAsState(initial = false)
            TianjiTheme(themeMode, dynamicColor) {
                TianjiApp()
            }
        }
    }
}

private data class Tab(val route: String, val label: String, val icon: ImageVector)

private val tabs = listOf(
    Tab("today", "今日", Icons.Filled.Home),
    Tab("bazi", "八字", Icons.Filled.GridView),
    Tab("marriage", "姻缘", Icons.Filled.FavoriteBorder),
    Tab("tarot", "塔罗", Icons.Filled.Style),
    Tab("fengshui", "风水", Icons.Filled.Explore),
)

private val tabRoutes = tabs.map { it.route }.toSet()

@Composable
fun TianjiApp() {
    val navController = rememberNavController()
    val backStack by navController.currentBackStackEntryAsState()
    val currentRoute = backStack?.destination?.route

    Scaffold(
        bottomBar = {
            NavigationBar {
                tabs.forEach { tab ->
                    NavigationBarItem(
                        selected = currentRoute == tab.route,
                        onClick = {
                            when {
                                currentRoute == tab.route -> {
                                    // 再点当前 tab：滚回顶部，给一个明确响应
                                    AppTabs.retap(tab.route)
                                }
                                currentRoute in tabRoutes -> {
                                    // inclusive=true 全清存档再恢复：若不含起始页，回起始页会被
                                    // launchSingleTop 短路成 no-op（pop 后栈顶即目标）
                                    navController.navigate(tab.route) {
                                        popUpTo(navController.graph.findStartDestination().id) {
                                            inclusive = true
                                            saveState = true
                                        }
                                        launchSingleTop = true
                                        restoreState = true
                                    }
                                }
                                else -> {
                                    // 二级页（设置/历史）：直接弹回目标 tab，绕开 save/restoreState 与二级页的纠缠
                                    if (!navController.popBackStack(tab.route, false)) {
                                        navController.navigate(tab.route) {
                                            popUpTo(navController.graph.findStartDestination().id) {
                                                inclusive = true
                                                saveState = true
                                            }
                                            launchSingleTop = true
                                            restoreState = true
                                        }
                                    }
                                }
                            }
                        },
                        icon = { Icon(tab.icon, contentDescription = tab.label) },
                        label = { Text(tab.label) },
                    )
                }
            }
        },
    ) { padding ->
        NavHost(
            navController = navController,
            startDestination = "today",
            modifier = Modifier.padding(padding),
        ) {
            composable("today") {
                TodayScreen(
                    onSettings = { navController.navigate("settings") },
                    onBazi = { navController.navigate("bazi") },
                    onMarriage = { navController.navigate("marriage") },
                    onTarot = { navController.navigate("tarot") },
                    onFengshui = { navController.navigate("fengshui") },
                )
            }
            composable("bazi") { BaziScreen(onGoSettings = { navController.navigate("settings") }) }
            composable("marriage") { MarriageScreen(onGoSettings = { navController.navigate("settings") }) }
            composable("tarot") { TarotScreen(onGoSettings = { navController.navigate("settings") }) }
            composable("fengshui") { FengshuiScreen(onGoSettings = { navController.navigate("settings") }) }
            composable("settings") {
                SettingsScreen(
                    onBack = { navController.popBackStack() },
                    onHistory = { navController.navigate("history") },
                )
            }
            composable("history") { HistoryScreen(onBack = { navController.popBackStack() }) }
        }
    }
}
