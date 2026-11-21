package com.admi.mifi

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerDefaults
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.PowerSettingsNew
import androidx.compose.material.icons.rounded.RestartAlt
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import androidx.core.view.WindowCompat
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.admi.mifi.data.DesignStyle
import com.admi.mifi.data.DeviceAction
import com.admi.mifi.ui.MainTab
import com.admi.mifi.ui.MifiViewModel
import com.admi.mifi.ui.UiEvent
import com.admi.mifi.ui.components.FastPagerSpringSpec
import com.admi.mifi.ui.components.MifiBottomBar
import com.admi.mifi.ui.components.PagerNavigationSpringSpec
import com.admi.mifi.ui.screens.DevicesScreen
import com.admi.mifi.ui.screens.HomeScreen
import com.admi.mifi.ui.screens.SettingsScreen
import com.admi.mifi.ui.screens.TrafficScreen
import com.admi.mifi.ui.theme.MifiTheme
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import dev.chrisbanes.haze.hazeSource
import dev.chrisbanes.haze.rememberHazeState
import kotlinx.coroutines.launch
import kotlin.math.abs

@Composable
fun MifiApp(viewModel: MifiViewModel = viewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }
    var pendingAction by remember { mutableStateOf<DeviceAction?>(null) }
    val hazeState = rememberHazeState()
    val liquidBackdrop = rememberLayerBackdrop()
    val saveableStateHolder = rememberSaveableStateHolder()
    val pagerState = rememberPagerState(initialPage = 0, pageCount = { MainTab.entries.size })
    var selectedTab by rememberSaveable { mutableStateOf(MainTab.HOME) }
    val onTabSelect = remember { { tab: MainTab -> selectedTab = tab } }
    val floatingBar =
        state.settings.designStyle == DesignStyle.MIUIX || state.settings.floatingBottomBar
    val barSpace =
        if (floatingBar) {
            88.dp + WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
        } else {
            0.dp
        }

    // Single source of truth: tapping a tab sets selectedTab, this effect drives
    // the pager. Swiping only writes selectedTab back after the page settles, so
    // intermediate pages can never feed back and cancel the animation.
    LaunchedEffect(selectedTab) {
        val spec =
            if (abs(selectedTab.ordinal - pagerState.currentPage) > 1) {
                FastPagerSpringSpec
            } else {
                PagerNavigationSpringSpec
            }
        pagerState.animateScrollToPage(page = selectedTab.ordinal, animationSpec = spec)
    }
    LaunchedEffect(pagerState.settledPage) {
        val settled = MainTab.entries[pagerState.settledPage]
        if (settled != selectedTab) selectedTab = settled
    }

    LifecycleResumeEffect(Unit) {
        viewModel.onForeground()
        onPauseOrDispose { viewModel.onBackground() }
    }

    LaunchedEffect(viewModel, snackbarHostState) {
        viewModel.events.collect { event ->
            when (event) {
                is UiEvent.Message -> snackbarHostState.showSnackbar(event.text)
            }
        }
    }

    val showMessage: (String) -> Unit = remember(scope, snackbarHostState) {
        { message ->
            scope.launch { snackbarHostState.showSnackbar(message) }
        }
    }

    val openUrl: (String) -> Unit = remember(context, scope, snackbarHostState) {
        { url ->
            val intent = Intent(Intent.ACTION_VIEW, url.toUri())
            try {
                context.startActivity(intent)
            } catch (_: ActivityNotFoundException) {
                scope.launch { snackbarHostState.showSnackbar("没有可打开此链接的应用") }
            } catch (_: Exception) {
                scope.launch { snackbarHostState.showSnackbar("无法打开链接：$url") }
            }
        }
    }

    MifiTheme(settings = state.settings) {
        val view = LocalView.current
        val lightBackground = MaterialTheme.colorScheme.surface.luminance() >= 0.4f
        SideEffect {
            val window = view.context.findActivity()?.window ?: return@SideEffect
            WindowCompat.getInsetsController(window, view).apply {
                isAppearanceLightStatusBars = lightBackground
                isAppearanceLightNavigationBars = lightBackground
            }
        }
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background),
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .hazeSource(hazeState)
                    .layerBackdrop(liquidBackdrop),
            ) {
                MifiBackground()
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .statusBarsPadding(),
                ) {
                    HorizontalPager(
                        state = pagerState,
                        modifier = Modifier.fillMaxSize().padding(bottom = barSpace),
                        // Keep all four lightweight screens composed so a switch never
                        // has to inflate a page in the middle of the animation.
                        beyondViewportPageCount = MainTab.entries.size - 1,
                        overscrollEffect = null,
                        flingBehavior = PagerDefaults.flingBehavior(
                            state = pagerState,
                            snapAnimationSpec = PagerNavigationSpringSpec,
                        ),
                        pageNestedScrollConnection = PagerDefaults.pageNestedScrollConnection(
                            state = pagerState,
                            orientation = Orientation.Horizontal,
                        ),
                    ) { page ->
                        val tab = MainTab.entries[page]
                        saveableStateHolder.SaveableStateProvider(tab.name) {
                            when (tab) {
                                MainTab.HOME -> HomeScreen(
                                    state = state,
                                    onRefresh = viewModel::refresh,
                                    onAction = { pendingAction = it },
                                    onNavigate = onTabSelect,
                                    onEnableDemo = {
                                        viewModel.enableDemo()
                                        showMessage("已进入演示模式")
                                    },
                                    onOpenUrl = openUrl,
                                    onMessage = showMessage,
                                )
                                MainTab.DEVICES -> DevicesScreen(
                                    state = state,
                                    onRefresh = viewModel::refresh,
                                    onOpenAdmin = { openUrl(adminUrl(state.resolvedAdminUrl ?: state.settings.host)) },
                                    onMessage = showMessage,
                                )
                                MainTab.TRAFFIC -> TrafficScreen(
                                    state = state,
                                    onRefresh = viewModel::refresh,
                                )
                                MainTab.SETTINGS -> SettingsScreen(
                                    state = state,
                                    viewModel = viewModel,
                                    onOpenUrl = openUrl,
                                    onMessage = showMessage,
                                )
                            }
                        }
                    }
                }
            }

            MifiBottomBar(
                selected = selectedTab,
                onSelect = onTabSelect,
                hazeState = hazeState,
                liquidBackdrop = liquidBackdrop,
                floating = state.settings.floatingBottomBar,
                modifier = Modifier.align(Alignment.BottomCenter),
            )

            SnackbarHost(
                hostState = snackbarHostState,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .navigationBarsPadding()
                    .padding(start = 20.dp, end = 20.dp, bottom = 100.dp),
            )
        }

        val action = pendingAction
        if (action != null) {
            val isReboot = action == DeviceAction.REBOOT
            AlertDialog(
                onDismissRequest = {
                    if (state.actionInProgress == null) pendingAction = null
                },
                icon = {
                    Icon(
                        imageVector = if (isReboot) Icons.Rounded.RestartAlt else Icons.Rounded.PowerSettingsNew,
                        contentDescription = null,
                        tint = if (isReboot) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
                    )
                },
                title = { Text(if (isReboot) "重启随身 WiFi？" else "关闭随身 WiFi？") },
                text = {
                    Text(
                        if (isReboot) {
                            "设备将断开所有连接并重新启动，通常需要约一分钟恢复。"
                        } else {
                            "设备会立即关机，所有已连接设备将断开。需要按机身电源键才能重新开机。"
                        },
                    )
                },
                confirmButton = {
                    Button(
                        onClick = {
                            viewModel.performAction(action)
                            pendingAction = null
                        },
                        colors = if (isReboot) {
                            ButtonDefaults.buttonColors()
                        } else {
                            ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.error,
                                contentColor = MaterialTheme.colorScheme.onError,
                            )
                        },
                    ) {
                        Text(if (isReboot) "确认重启" else "确认关机")
                    }
                },
                dismissButton = {
                    TextButton(onClick = { pendingAction = null }) { Text("取消") }
                },
            )
        }
    }
}

@Composable
private fun MifiBackground() {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
    )
}

private fun adminUrl(host: String): String {
    val trimmed = host.trim()
    return when {
        trimmed.startsWith("http://") || trimmed.startsWith("https://") -> trimmed
        else -> "http://${trimmed.trimEnd('/')}/"
    }
}

private fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}
