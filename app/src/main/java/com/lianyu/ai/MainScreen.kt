package com.lianyu.ai

import android.app.Activity
import androidx.activity.ComponentActivity
import androidx.compose.animation.core.tween
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerDefaults
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Velocity
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.lianyu.ai.common.AppForegroundTracker
import com.lianyu.ai.common.BatteryOptimizationHelper
import com.lianyu.ai.common.YandereModeManager
import com.lianyu.ai.domain.ServiceRegistry
import com.lianyu.ai.feature.chat.ui.screen.ChatDetailScreen
import com.lianyu.ai.feature.chat.ui.screen.ChatScreen
import com.lianyu.ai.feature.chat.ui.screen.VoiceCallScreen
import com.lianyu.ai.feature.companion.ui.screen.ContactsScreen
import com.lianyu.ai.feature.companion.ui.screen.CreateCompanionScreen
import com.lianyu.ai.feature.groupchat.ui.CreateGroupScreen
import com.lianyu.ai.feature.groupchat.ui.GroupChatScreen
import com.lianyu.ai.feature.groupchat.ui.GroupDetailScreen
import com.lianyu.ai.feature.memory.MemoryScreen
import com.lianyu.ai.feature.profile.*
import com.lianyu.ai.feature.settings.ui.screen.*
import com.lianyu.ai.feature.update.AppUpdateManager
import com.lianyu.ai.feature.wechat.ui.WeChatBindScreen
import com.lianyu.ai.feature.wechat.ui.WeChatSettingsScreen
import com.lianyu.ai.feature.qqbot.ui.QQBotSettingsScreen
import com.lianyu.ai.feature.backup.BackupScreen
import com.lianyu.ai.feature.coffee.ui.CoffeeScreen
import com.lianyu.ai.feature.coffee.ui.CoffeeOrderQueryScreen
import com.lianyu.ai.feature.coffee.ui.CoffeeSettingsScreen
import com.lianyu.ai.feature.coffee.ui.CoffeeTokenInputScreen
import com.lianyu.ai.feature.coffee.ui.ProductDetailScreen
import com.lianyu.ai.uicommon.component.UpdateDialog
import com.lianyu.ai.uicommon.theme.LianYuTheme
import com.lianyu.ai.uicommon.theme.ThemeViewModel

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChatBubble
import androidx.compose.material.icons.filled.Group
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.outlined.ChatBubbleOutline
import androidx.compose.material.icons.outlined.Group
import androidx.compose.material.icons.outlined.PersonOutline
import kotlinx.coroutines.launch
import kotlin.math.abs

/**
 * 主界面 — NavHost + Pager + Scaffold 编排。
 *
 * 状态空间模型:
 *   S ∈ {Home, Contacts, Profile, Chat(id), ChatDetail(id), VoiceCall(id),
 *        GroupChat(id), GroupDetail(id), CreateGroup, Settings, Theme, Language,
 *        CheckUpdate, About, FrameRate, Team, Support, Memory,
 *        ContextMemory, TtsSettings, TokenUsage, WeChatSettings, WeChatBind,
 *        AgreementView, CreateCompanion, EditCompanion(id)}
 *   差分方程: S[k+1] = f(S[k], E[k])
 *   验证: 所有状态出度 ≥ 1 (popBackStack 保证)
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun MainScreen(mainActivity: Activity) {
    val navController = rememberNavController()
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route

    val context = LocalContext.current
    val valActivity = context as ComponentActivity

    val updateManager = remember {
        (valActivity as? MainActivity)?.updateManager ?: AppUpdateManager(context)
    }
    val showUpdateDialog by updateManager.showUpdateDialog.collectAsState()
    val updateInfo by updateManager.updateInfo.collectAsState()
    val downloadProgress by updateManager.downloadProgress.collectAsState()

    fun openCompanionChat(companionId: Long) {
        LastOpenedCompanionStore.save(context, companionId)
        navController.navigate(MainRoute.Chat(companionId).route)
    }

    // 深度链接 / 冷启动恢复最近打开的单聊
    LaunchedEffect(Unit) {
        val intent = valActivity.intent
        if (intent.getBooleanExtra("open_chat", false)) {
            val companionId = intent.getLongExtra("companion_id", -1L)
            if (companionId != -1L) {
                openCompanionChat(companionId)
                intent.removeExtra("open_chat")
                intent.removeExtra("companion_id")
                return@LaunchedEffect
            }
        }

        val restoredCompanionId = LastOpenedCompanionStore.resolveInitialCompanionId(
            context,
            ServiceRegistry.getOrThrow(com.lianyu.ai.database.repository.CompanionRepository::class.java)
        )
        if (restoredCompanionId != null) {
            openCompanionChat(restoredCompanionId)
        }
    }

    // 引导弹窗
    MainScreenDialogs(
        onAutoStartSettings = { BatteryOptimizationHelper.openAutoStartSettings(context) },
        onBatterySettings = { BatteryOptimizationHelper.requestIgnoreBatteryOptimizations(context) }
    )

    val bottomNavItems = listOf(
        BottomNavItem(stringResource(R.string.nav_love), Icons.Filled.ChatBubble, Icons.Outlined.ChatBubbleOutline, "home"),
        BottomNavItem(stringResource(R.string.nav_contacts), Icons.Filled.Group, Icons.Outlined.Group, "contacts"),
        BottomNavItem(stringResource(R.string.nav_profile), Icons.Filled.Person, Icons.Outlined.PersonOutline, "profile")
    )

    val mainTabRoutes = bottomNavItems.map { it.route }
    val showBottomBar = currentRoute in mainTabRoutes

    val pagerState = rememberPagerState(pageCount = { 3 })
    val coroutineScope = rememberCoroutineScope()

    // 记住离开 tab 页面前的 pager 位置，返回时恢复（用 rememberSaveable 防止 NavHost 过渡重建丢失）
    var lastTabPage by rememberSaveable { mutableIntStateOf(0) }

    LaunchedEffect(currentRoute) {
        if (currentRoute !in mainTabRoutes) {
            // 离开 tab 页面（进入聊天等）—— 记住当前位置
            lastTabPage = pagerState.currentPage
            return@LaunchedEffect
        }
        // 返回 tab 页面 —— 恢复到离开前的位置，而非强制定位到首页
        val targetPage = when (currentRoute) {
            "contacts" -> 1
            "profile" -> 2
            else -> lastTabPage
        }
        if (pagerState.currentPage != targetPage) {
            pagerState.scrollToPage(targetPage)
        }
    }

    // 横向滑动冲突处理: 不阻断横向, 让 pager 和子层各自处理各自轴
    val angleNestedScrollConnection = remember {
        object : NestedScrollConnection {
            override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset = Offset.Zero
            override suspend fun onPreFling(available: Velocity): Velocity = Velocity.Zero
            override suspend fun onPostFling(consumed: Velocity, available: Velocity): Velocity = Velocity.Zero
        }
    }

    val themeViewModel: ThemeViewModel = viewModel()
    val isDark by themeViewModel.isDarkTheme.collectAsStateWithLifecycle()

    Scaffold(
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        bottomBar = {
            if (showBottomBar) {
                FloatingGlassBottomNav(
                    items = bottomNavItems,
                    currentIndex = pagerState.currentPage,
                    onItemClick = { index ->
                        lastTabPage = index
                        coroutineScope.launch { pagerState.animateScrollToPage(index) }
                    }
                )
            }
        }
    ) { paddingValues ->
        Box(
            modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).padding(paddingValues)
        ) {
            NavHost(
                navController = navController,
                startDestination = "home",
                enterTransition = {
                    fadeIn(animationSpec = tween(250)) + slideInHorizontally(animationSpec = tween(300), initialOffsetX = { it / 4 })
                },
                exitTransition = {
                    fadeOut(animationSpec = tween(200)) + slideOutHorizontally(animationSpec = tween(250), targetOffsetX = { -it / 6 })
                },
                popEnterTransition = {
                    fadeIn(animationSpec = tween(250)) + slideInHorizontally(animationSpec = tween(300), initialOffsetX = { -it / 4 })
                },
                popExitTransition = {
                    fadeOut(animationSpec = tween(200)) + slideOutHorizontally(animationSpec = tween(250), targetOffsetX = { it / 4 })
                }
            ) {
                // === 主页 Pager ===
                composable(MainRoute.Home.route) {
                    val pagerOffset by remember { derivedStateOf { pagerState.currentPageOffsetFraction } }
                    HorizontalPager(
                        state = pagerState,
                        beyondViewportPageCount = 1,
                        flingBehavior = PagerDefaults.flingBehavior(state = pagerState, snapPositionalThreshold = 0.4f),
                        modifier = Modifier.fillMaxSize()
                            .pointerInput(pagerState) {
                                awaitEachGesture {
                                    val down = awaitFirstDown(requireUnconsumed = false)
                                    var totalDragX = 0f
                                    do {
                                        val event = awaitPointerEvent(PointerEventPass.Initial)
                                        event.changes.forEach { change ->
                                            totalDragX += change.position.x - change.previousPosition.x
                                        }
                                        if (abs(totalDragX) > size.width * 0.6f) {
                                            val target = (pagerState.currentPage + if (totalDragX < 0) 1 else -1).coerceIn(0, pagerState.pageCount - 1)
                                            coroutineScope.launch { pagerState.animateScrollToPage(target) }
                                            event.changes.forEach { it.consume() }
                                        }
                                    } while (event.changes.any { it.pressed })
                                }
                            }
                            .nestedScroll(angleNestedScrollConnection)
                    ) { page ->
                        val cp = pagerState.currentPage
                        val visible = remember(page, cp, pagerOffset) {
                            when {
                                page == cp -> pagerOffset in -0.6f..0.6f
                                page == cp + 1 -> pagerOffset > 0.3f
                                page == cp - 1 -> pagerOffset < -0.3f
                                else -> false
                            }
                        }
                        AnimatedVisibility(
                            visible = visible,
                            enter = fadeIn(animationSpec = tween(350)) + scaleIn(initialScale = 0.94f, animationSpec = tween(350)),
                            exit = fadeOut(animationSpec = tween(200))
                        ) {
                            when (page) {
                                0 -> HomeScreen(
                                    onCompanionClick = { openCompanionChat(it) },
                                    onGroupClick = { navController.navigate(MainRoute.GroupChat(it).route) },
                                    onAddClick = { navController.navigate(MainRoute.CreateCompanion.route) },
                                    onCreateGroupClick = { navController.navigate(MainRoute.CreateGroup.route) }
                                )
                                1 -> ContactsScreen(
                                    onCompanionClick = { openCompanionChat(it) },
                                    onAddClick = { navController.navigate(MainRoute.CreateCompanion.route) },
                                    onEditClick = { navController.navigate(MainRoute.EditCompanion(it).route) },
                                    onGroupClick = { navController.navigate(MainRoute.GroupChat(it).route) },
                                    onCreateGroupClick = { navController.navigate(MainRoute.CreateGroup.route) }
                                )
                                2 -> ProfileScreen(
                                    // 记忆与管理
                                    onMemoryClick = { navController.navigate(MainRoute.Memory.route) },
                                    onContextMemoryClick = { navController.navigate(MainRoute.ContextMemory.route) },
                                    // AI与外观
                                    onSettingsClick = { navController.navigate(MainRoute.Settings.route) },
                                    onThemeClick = { navController.navigate(MainRoute.Theme.route) },
                                    // 总设置
                                    onGeneralSettingsClick = { navController.navigate(MainRoute.GeneralSettings.route) },
                                    // 角色管理
                                    onRoleManagerClick = { navController.navigate(MainRoute.RoleManager.route) },
                                    // 关于与支持
                                    onTeamClick = { navController.navigate(MainRoute.Team.route) },
                                    onSupportClick = { navController.navigate(MainRoute.Support.route) },
                                    onThanksClick = { navController.navigate(MainRoute.Thanks.route) },
                                    onAboutClick = { navController.navigate(MainRoute.About.route) }
                                )
                            }
                        }
                    }
                }

                // === 伴侣创建/编辑 ===
                composable(MainRoute.CreateCompanion.route) { CreateCompanionScreen(onNavigateBack = { navController.popBackStack() }) }
                composable(MainRoute.EditCompanion(0).route.replace("0", "{companionId}"), arguments = listOf(navArgument("companionId") { type = NavType.LongType })) { backStackEntry ->
                    val companionId = backStackEntry.arguments?.getLong("companionId") ?: 0L
                    CreateCompanionScreen(companionId = companionId, onNavigateBack = { navController.popBackStack() })
                }

                // === 聊天 ===
                composable(MainRoute.Chat(0).route.replace("0", "{companionId}"), arguments = listOf(navArgument("companionId") { type = NavType.LongType })) { backStackEntry ->
                    val companionId = backStackEntry.arguments?.getLong("companionId") ?: 0L
                    ChatScreen(companionId = companionId, onNavigateBack = { navController.popBackStack() }, onNavigateToDetail = { navController.navigate("chat_detail/$it") }, onNavigateToVoiceCall = { navController.navigate("voice_call/$it") })
                }
                composable(MainRoute.ChatDetail(0).route.replace("0", "{companionId}"), arguments = listOf(navArgument("companionId") { type = NavType.LongType })) { backStackEntry ->
                    val detailCompanionId = backStackEntry.arguments?.getLong("companionId") ?: 0L
                    ChatDetailScreen(companionId = detailCompanionId, onNavigateBack = { navController.popBackStack() })
                }
                composable(MainRoute.VoiceCall(0).route.replace("0", "{companionId}"), arguments = listOf(navArgument("companionId") { type = NavType.LongType })) { backStackEntry ->
                    val callCompanionId = backStackEntry.arguments?.getLong("companionId") ?: 0L
                    VoiceCallScreen(companionId = callCompanionId, onNavigateBack = { navController.popBackStack() })
                }

                // === 群聊 ===
                composable(MainRoute.GroupChat(0).route.replace("0", "{groupId}"), arguments = listOf(navArgument("groupId") { type = NavType.LongType })) { backStackEntry ->
                    val groupId = backStackEntry.arguments?.getLong("groupId") ?: 0L
                    GroupChatScreen(groupId = groupId, onNavigateBack = { navController.popBackStack() }, onNavigateToDetail = { navController.navigate("group_detail/$it") })
                }
                composable(MainRoute.GroupDetail(0).route.replace("0", "{groupId}"), arguments = listOf(navArgument("groupId") { type = NavType.LongType })) { backStackEntry ->
                    val groupId = backStackEntry.arguments?.getLong("groupId") ?: 0L
                    GroupDetailScreen(groupId = groupId, onNavigateBack = { navController.popBackStack() }, onGroupDeleted = { navController.popBackStack("group_chat/$groupId", inclusive = true) })
                }
                composable(MainRoute.CreateGroup.route) { CreateGroupScreen(onNavigateBack = { navController.popBackStack() }) }

                // === 设置 ===
                composable(MainRoute.Settings.route) { SettingsScreen(onNavigateBack = { navController.popBackStack() }) }
                composable(MainRoute.TtsSettings.route) { TtsSettingsScreen(onNavigateBack = { navController.popBackStack() }, isDarkTheme = isDark) }
                composable(MainRoute.TokenUsage.route) { TokenUsageScreen(onNavigateBack = { navController.popBackStack() }) }
                composable(MainRoute.Memory.route) { MemoryScreen(onNavigateBack = { navController.popBackStack() }) }
                composable(MainRoute.RoleManager.route) {
                    val roleManagerViewModel: com.lianyu.ai.feature.profile.ProfileViewModel = viewModel()
                    val managerCurrentRole by roleManagerViewModel.selectedRole.collectAsStateWithLifecycle()
                    val managerSwitchState by roleManagerViewModel.switchState.collectAsStateWithLifecycle()
                    com.lianyu.ai.feature.profile.RoleManagerScreen(
                        currentRole = managerCurrentRole,
                        switchState = managerSwitchState,
                        onSwitchRole = { role -> roleManagerViewModel.switchRole(role) { navController.popBackStack() } },
                        onNavigateBack = { navController.popBackStack() },
                        onConsumeError = { roleManagerViewModel.consumeSwitchError() }
                    )
                }
                composable(MainRoute.Theme.route) { ThemeScreen(onNavigateBack = { navController.popBackStack() }, activity = mainActivity) }
                composable(MainRoute.Language.route) { LanguageScreen(onNavigateBack = { navController.popBackStack() }, activity = mainActivity) }
                composable(MainRoute.CheckUpdate.route) { CheckUpdateScreen(onNavigateBack = { navController.popBackStack() }) }
                composable(MainRoute.About.route) { AboutScreen(onNavigateBack = { navController.popBackStack() }, onAgreementClick = { navController.navigate(MainRoute.AgreementView.route) }) }
                composable(MainRoute.AgreementView.route) { AgreementViewScreen(onNavigateBack = { navController.popBackStack() }) }
                composable(MainRoute.FrameRate.route) { FrameRateScreen(onNavigateBack = { navController.popBackStack() }, activity = mainActivity) }
                composable(MainRoute.YandereMode.route) {
                    val manager = ServiceRegistry.get(YandereModeManager::class.java)
                    if (manager != null) {
                        YandereModeScreen(onNavigateBack = { navController.popBackStack() }, yandereModeManager = manager)
                    }
                }
                composable(MainRoute.Team.route) { TeamScreen(onNavigateBack = { navController.popBackStack() }) }
                composable(MainRoute.Support.route) { SupportScreen(onNavigateBack = { navController.popBackStack() }) }
                composable(MainRoute.Thanks.route) {
                    ThanksScreen(
                        onNavigateBack = { navController.popBackStack() },
                        onViewFullList = { navController.navigate(MainRoute.ThanksFullList.route) }
                    )
                }
                composable(MainRoute.ThanksFullList.route) {
                    ThanksFullListScreen(onNavigateBack = { navController.popBackStack() })
                }
                composable(MainRoute.ContextMemory.route) { ContextMemoryScreen(onNavigateBack = { navController.popBackStack() }) }
                composable(MainRoute.OriginOSAdaption.route) {
                    OriginOSAdaptionScreen(onNavigateBack = { navController.popBackStack() })
                }
                composable(MainRoute.ExperimentalFeatures.route) {
                    ExperimentalFeaturesScreen(
                        onNavigateBack = { navController.popBackStack() },
                        onYandereModeClick = { navController.navigate(MainRoute.YandereMode.route) }
                    )
                }
                composable(MainRoute.GeneralSettings.route) {
                    GeneralSettingsScreen(
                        onNavigateBack = { navController.popBackStack() },
                        onLanguageClick = { navController.navigate(MainRoute.Language.route) },
                        onFrameRateClick = { navController.navigate(MainRoute.FrameRate.route) },
                        onTtsSettingsClick = { navController.navigate(MainRoute.TtsSettings.route) },
                        onTokenUsageClick = { navController.navigate(MainRoute.TokenUsage.route) },
                        onCheckUpdateClick = { navController.navigate(MainRoute.CheckUpdate.route) },
                        onWeChatClick = { navController.navigate(MainRoute.WeChatSettings.route) },
                        onQQBotClick = { navController.navigate(MainRoute.QQBotSettings.route) },
                        onDataBackupClick = { navController.navigate(MainRoute.DataBackup.route) },
                        onOriginOSAdaptionClick = { navController.navigate(MainRoute.OriginOSAdaption.route) },
                        onCoffeeClick = { navController.navigate(MainRoute.Coffee.route) },
                        onExperimentalFeaturesClick = { navController.navigate(MainRoute.ExperimentalFeatures.route) }
                    )
                }
                composable(MainRoute.WeChatSettings.route) {
                    WeChatSettingsScreen(onNavigateBack = { navController.popBackStack() }, onBindClick = { navController.navigate(MainRoute.WeChatBind.route) })
                }
                composable(MainRoute.WeChatBind.route) { WeChatBindScreen(onNavigateBack = { navController.popBackStack() }) }
                composable(MainRoute.QQBotSettings.route) { QQBotSettingsScreen(onNavigateBack = { navController.popBackStack() }) }
                composable(MainRoute.DataBackup.route) { BackupScreen(onNavigateBack = { navController.popBackStack() }) }
                composable(MainRoute.Coffee.route) {
                    CoffeeScreen(
                        onBack = { navController.popBackStack() },
                        onProductClick = { deptId, productId ->
                            navController.navigate(MainRoute.CoffeeProduct(deptId, productId).route)
                        },
                        onSettingsClick = { navController.navigate(MainRoute.CoffeeSettings.route) },
                        onOrderQueryClick = { navController.navigate(MainRoute.CoffeeOrderQuery.route) }
                    )
                }
                composable(
                    MainRoute.CoffeeProduct(0, 0).route.replace("0", "{deptId}/{productId}"),
                    arguments = listOf(
                        navArgument("deptId") { type = NavType.LongType },
                        navArgument("productId") { type = NavType.LongType }
                    )
                ) { backStackEntry ->
                    val deptId = backStackEntry.arguments?.getLong("deptId") ?: 0L
                    val productId = backStackEntry.arguments?.getLong("productId") ?: 0L
                    ProductDetailScreen(
                        deptId = deptId,
                        productId = productId,
                        onBack = { navController.popBackStack() },
                        onAddedToCart = { navController.popBackStack() }
                    )
                }
                composable(MainRoute.CoffeeSettings.route) {
                    CoffeeSettingsScreen(
                        onBack = { navController.popBackStack() },
                        onReplaceToken = { navController.navigate(MainRoute.CoffeeToken.route) },
                        onQueryOrder = { orderId ->
                            navController.navigate(MainRoute.CoffeeOrderQueryWithId(orderId).route)
                        }
                    )
                }
                composable(MainRoute.CoffeeToken.route) {
                    CoffeeTokenInputScreen(onBack = { navController.popBackStack() })
                }
                composable(MainRoute.CoffeeOrderQuery.route) {
                    CoffeeOrderQueryScreen(onBack = { navController.popBackStack() })
                }
                composable(
                    MainRoute.CoffeeOrderQueryWithId("placeholder").route.replace("placeholder", "{orderId}"),
                    arguments = listOf(navArgument("orderId") { type = NavType.StringType; nullable = false })
                ) { backStackEntry ->
                    val orderId = backStackEntry.arguments?.getString("orderId").orEmpty()
                    CoffeeOrderQueryScreen(
                        initialOrderId = orderId,
                        onBack = { navController.popBackStack() }
                    )
                }
            }

            // 更新弹窗
            if (showUpdateDialog && updateInfo != null) {
                UpdateDialog(
                    updateInfo = updateInfo!!,
                    downloadProgress = downloadProgress,
                    onUpdate = {
                        updateInfo?.let { info ->
                            if (info.updateUrl.isNotEmpty()) {
                                if (updateManager.checkInstallPermission()) updateManager.startDownload(info.updateUrl)
                                else updateManager.requestInstallPermission(valActivity)
                            }
                        }
                    },
                    onCancel = { updateManager.ignoreThisVersion() },
                    onDismiss = { updateManager.dismissUpdate() }
                )
            }
        }
    }
}
