package com.lianyu.ai

import android.app.Application
import android.content.Context
import android.content.res.Configuration
import com.lianyu.ai.common.ContentFilter
import com.lianyu.ai.common.DeviceIdProvider
import com.lianyu.ai.common.RomUtils
import com.lianyu.ai.common.SaltStore
import com.lianyu.ai.common.SecureLog
import com.lianyu.ai.common.embedding.VectorLibrary
import com.lianyu.ai.common.safety.ContentSafetyVerifier
import com.lianyu.ai.database.AppDatabase
import com.lianyu.ai.database.DefaultCompanionSeeder
import com.lianyu.ai.database.SecurityDataSeeder
import com.lianyu.ai.database.repository.ChatRepository
import com.lianyu.ai.database.repository.CompanionRepository
import com.lianyu.ai.database.repository.MemoryRepository
import com.lianyu.ai.database.repository.UserRepository
import com.lianyu.ai.common.AppSettingsStore
import com.lianyu.ai.common.YandereModeManager
import com.lianyu.ai.domain.CompanionProvider
import com.lianyu.ai.domain.AiServiceProvider
import com.lianyu.ai.domain.CoffeeOrderProvider
import com.lianyu.ai.domain.LocalModelProvider
import com.lianyu.ai.domain.MemoryProvider
import com.lianyu.ai.domain.ServiceRegistry
import com.lianyu.ai.domain.UserProfileProvider

import com.lianyu.ai.feature.notification.NotificationHelper
import com.lianyu.ai.push.PushManager
import com.lianyu.ai.feature.wechat.data.WeChatTokenStore
import com.lianyu.ai.feature.wechat.service.WeChatNotificationHelper
import com.lianyu.ai.feature.wechat.service.WeChatPollingService
import com.lianyu.ai.feature.wechat.service.WeChatPollingWorker
import com.lianyu.ai.network.AiService
import com.lianyu.ai.network.NtpTimeProvider
import com.lianyu.ai.security.G0
import com.lianyu.ai.security.SecurityState
import com.lianyu.ai.uicommon.component.ChatBackgroundCache
import com.lianyu.ai.uicommon.component.getChatBackgroundKey
import coil.ImageLoader
import coil.ImageLoaderFactory
import coil.memory.MemoryCache
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import java.util.Locale

class LianYuApplication : Application(), ImageLoaderFactory, androidx.work.Configuration.Provider {

    private val _startupState = MutableStateFlow<AppStartupState>(AppStartupState.CriticalInit)
    val startupState: StateFlow<AppStartupState> = _startupState.asStateFlow()

    override fun newImageLoader(): ImageLoader = ImageLoader.Builder(this)
        .memoryCache {
            MemoryCache.Builder(this).maxSizeBytes(128 * 1024 * 1024).build()
        }
        .build()

    override val workManagerConfiguration: androidx.work.Configuration
        get() = androidx.work.Configuration.Builder()
            .setMinimumLoggingLevel(android.util.Log.WARN)
            .build()

    override fun attachBaseContext(base: Context) {
        super.attachBaseContext(base)
    }

        override fun onCreate() {
        // Global DNS resolution timeout (5s) — prevents DNS hang blocking all OkHttp calls
        java.security.Security.setProperty("networkaddress.cache.ttl", "0")
        java.security.Security.setProperty("networkaddress.cache.negative.ttl", "0")
        System.setProperty("sun.net.spi.nameservice.nameservers", "8.8.8.8")
        System.setProperty("sun.net.spi.nameservice.domain", ".")
        super.onCreate()
        instance = this
        initBusiness(this)
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        applyStoredLanguage(this)
    }

    override fun onTerminate() {
        bgScope.launch {
            ContentFilter.destroy()
            SaltStore.shutdown()
            // EncryptedDatabaseWrapper.sealDatabase(this@LianYuApplication)
            AppDatabase.shutdown()
            ChatBackgroundCache.clear()
        }
        ServiceRegistry.clear()
        super.onTerminate()
    }

    companion object {
        lateinit var instance: LianYuApplication
            private set

        private val bgScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

        fun initBusiness(app: Application) {
            SaltStore.init(app)
            SecureLog.init(com.lianyu.ai.BuildConfig.DEBUG)
            applyStoredLanguage(app)
            AiService.initialize(app)
            NtpTimeProvider.initialize(app)
            registerServiceProviders(app)
            clearUpdateIgnore(app)

            // 注入应用级后台作用域，供跨越 ViewModel 生命周期的任务使用
            com.lianyu.ai.common.ApplicationScopeProvider.init(bgScope)

            // Seed default companion asynchronously — must exist before any chat opens
            bgScope.launch { seedDefaultCompanion(app) }

            bgScope.launch { ContentFilter.initialize(app) }
            bgScope.launch { preloadBackground(app) }
            bgScope.launch { initWeChat(app) }
            bgScope.launch { initSecurityData(app) }
            bgScope.launch { autoBackupDatabase(app) }
            bgScope.launch { initVectorLibrary(app) }
            bgScope.launch { initSafetyClassifier(app) }
            bgScope.launch { initSafetyVerifier(app) }
            bgScope.launch { initYandereMode(app) }
        }

        private suspend fun initYandereMode(app: Application) {
            try {
                if (AppSettingsStore(app).getYandereModeEnabled()) {
                    ServiceRegistry.getOrThrow(YandereModeManager::class.java).start()
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                SecureLog.e("LianYuApplication", "initYandereMode failed", e)
            }
        }

        private fun preloadBackground(app: Application) {
            ChatBackgroundCache.preload(app, getChatBackgroundKey(app))
        }

        private suspend fun seedDefaultCompanion(app: Application) {
            DefaultCompanionSeeder.seedIfNeeded(app)
        }

        private suspend fun initWeChat(app: Application) {
            // 提前创建通知渠道，避免 OPPO/vivo 首次通知被系统折叠或延迟。
            NotificationHelper.createNotificationChannel(app)
            WeChatNotificationHelper.createChannel(app)
            SecureLog.d("LianYuApplication", "ROM: ${RomUtils.getRomDisplayName()} ${RomUtils.romVersion}")
            // 初始化厂商 Push SDK，提升 OPPO / vivo / 小米 / 华为 设备的消息到达率
            runCatching { PushManager.init(app) }
            val tokenStore = WeChatTokenStore(app)
            if (runCatching { tokenStore.isLoggedIn() }.getOrDefault(false)) {
                WeChatPollingService.start(app)
                WeChatPollingWorker.schedule(app)
            }
        }

        private suspend fun initSecurityData(app: Application) {
            SecurityDataSeeder.seedIfNeeded(app)
        }

        private fun autoBackupDatabase(app: Application) {
            // [M6 FIX] 备份涉及文件 IO，已从 AppDatabase.buildDatabase 主路径移除，
            // 在此异步执行，不阻塞首屏渲染。同时顺带清理过期备份。
            runCatching { AppDatabase.autoBackupIfNeeded(app.applicationContext) }
            runCatching { AppDatabase.clearOldBackups(app.applicationContext) }
        }

        private fun initVectorLibrary(app: Application) {
            runCatching {
                app.assets.open("safety/violation_vectors.bin").use { it.readBytes() }
                    .let { VectorLibrary.Loader().load(it) }
                    ?.let { ContentFilter.setVectorLibrary(it) }
            }
        }

        private fun initSafetyClassifier(app: Application) {
            runCatching {
                if (!com.lianyu.ai.feature.localmodel.LocalAiService.isNativeLibrarySupported) return
                val svc = com.lianyu.ai.feature.localmodel.LocalAiService.getInstance(app)
                com.lianyu.ai.feature.localmodel.LocalModelCatalog.all.firstOrNull { it.modelFile(app).exists() }
                    ?.let { svc.setActiveModel(it); ContentFilter.setSafetyClassifier(com.lianyu.ai.feature.localmodel.LocalSafetyClassifier(svc)) }
            }
        }

        private suspend fun initSafetyVerifier(app: Application) {
            runCatching {
                ContentSafetyVerifier.init(app)
                val keywords = SecurityDataSeeder.getEnabledKeywords(app)
                if (keywords.isNotEmpty()) {
                    ContentSafetyVerifier.bootstrap(keywords)
                }
            }
        }

        private fun registerServiceProviders(app: Application) {
            // ── Repository 单例注册 ──
            // 统一 Repository 获取方式，供 QQ Bot 等跨模块消费者通过 ServiceRegistry 获取。
            ServiceRegistry.registerSingleton(CompanionRepository::class.java) {
                CompanionRepository(AppDatabase.getDatabase(app).companionDao())
            }
            ServiceRegistry.registerSingleton(ChatRepository::class.java) {
                ChatRepository(AppDatabase.getDatabase(app).chatMessageDao())
            }
            ServiceRegistry.registerSingleton(MemoryRepository::class.java) {
                MemoryRepository(AppDatabase.getDatabase(app).memoryDao(), DeviceIdProvider.getDeviceId(app))
            }
            ServiceRegistry.registerSingleton(UserRepository::class.java) {
                UserRepository(app)
            }

            // ── 跨 feature 服务接口注册 ──
            ServiceRegistry.registerSingleton(LocalModelProvider::class.java) {
                com.lianyu.ai.feature.localmodel.LocalModelProviderImpl(app)
            }
            ServiceRegistry.registerSingleton(UserProfileProvider::class.java) {
                com.lianyu.ai.feature.profile.UserProfileProviderImpl(app)
            }
            ServiceRegistry.registerSingleton(CompanionProvider::class.java) {
                com.lianyu.ai.feature.companion.CompanionProviderImpl(app)
            }
            // MemoryProvider：跨会话记忆上下文与提取（feature:memory 实现，core:network/feature:groupchat 消费）
            // 必须在 AiService 之前注册，因为 AiService.init 会通过 ServiceRegistry 获取 MemoryProvider
            ServiceRegistry.registerSingleton(MemoryProvider::class.java) {
                com.lianyu.ai.feature.memory.engine.MemoryManager.getInstance(app)
            }
            ServiceRegistry.registerSingleton(AiServiceProvider::class.java) {
                AiService(app)
            }
            ServiceRegistry.registerSingleton(YandereModeManager::class.java) {
                YandereModeManager(app)
            }

            // ── 瑞幸咖啡 MCP 工具（AI 对话可调用） ──
            // CoffeeOrderProvider 暴露给 feature:chat 的 AiTool 实现
            ServiceRegistry.registerSingleton(CoffeeOrderProvider::class.java) {
                com.lianyu.ai.feature.coffee.CoffeeOrderProviderImpl(app)
            }
            // 注册瑞幸工具到 ToolRegistry，供 AiService 随请求一并发给 AI
            com.lianyu.ai.feature.coffee.LuckinCoffeeTools.registerAll(
                ServiceRegistry.getOrThrow(CoffeeOrderProvider::class.java)
            )

            ServiceRegistry.markInitialized()
        }

        private fun clearUpdateIgnore(app: Application) {
            app.getSharedPreferences("update_config", android.content.Context.MODE_PRIVATE)
                .edit().remove("ignored_version").apply()
        }

        private fun applyStoredLanguage(app: Application) {
            Locale.setDefault(
                when (app.getSharedPreferences("language_prefs", android.content.Context.MODE_PRIVATE)
                    .getString("language", "zh-CN") ?: "zh-CN"
                ) {
                    "zh-TW" -> Locale.TRADITIONAL_CHINESE
                    "en" -> Locale.ENGLISH
                    "ja" -> Locale.JAPANESE
                    "ko" -> Locale.KOREAN
                    else -> Locale.SIMPLIFIED_CHINESE
                }
            )
        }
    }
}
