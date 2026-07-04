package com.lianyu.ai.domain

import java.util.concurrent.ConcurrentHashMap

/**
 * 轻量级手动依赖注入注册中心，用于跨 feature 模块通信。
 * 消除 feature→feature 的 project() 依赖。
 *
 * 线程安全：所有存储使用 [ConcurrentHashMap]，可在任意线程注册/获取。
 *
 * 两种注册语义：
 * - [registerSingleton]：首次 [get] 时创建实例并缓存，后续返回同一实例。适用于无状态或可重入的服务。
 * - [register]：每次 [get] 都调用工厂创建新实例（工厂模式）。适用于需要隔离状态的场景。
 *
 * 使用示例（在 LianYuApplication.onCreate 中注册）：
 * ```
 * ServiceRegistry.registerSingleton(AiServiceProvider::class.java) { AiService(app) }
 * ServiceRegistry.register(LocalModelProvider::class.java) { LocalModelProviderImpl(app) }
 * ```
 *
 * 在 feature 模块中获取：
 * ```
 * val aiService = ServiceRegistry.getOrThrow(AiServiceProvider::class.java)
 * ```
 */
object ServiceRegistry {

    /** 工厂模式：每次 get 都新建实例 */
    private val factories = ConcurrentHashMap<Class<*>, () -> Any?>()

    /** 单例工厂：首次 get 时创建并缓存 */
    private val singletonFactories = ConcurrentHashMap<Class<*>, () -> Any?>()

    /** 单例实例缓存 */
    private val singletons = ConcurrentHashMap<Class<*>, Any>()

    /**
     * 注册中心初始化完成状态。
     * 在 [LianYuApplication.registerServiceProviders] 完成后标记为 true，
     * 供 UI 层在启动阶段等待，避免 ServiceRegistry 未就绪时访问导致闪退。
     */
    private val _initialized = kotlinx.coroutines.flow.MutableStateFlow(false)
    val initialized: kotlinx.coroutines.flow.StateFlow<Boolean> = _initialized

    /**
     * 注册工厂模式服务：每次 [get] 都会调用 [factory] 创建新实例。
     * 适用于需要隔离状态、或实例本身极轻量的场景。
     */
    fun <T : Any> register(type: Class<T>, factory: () -> T?) {
        factories[type] = factory as () -> Any?
    }

    /**
     * 注册单例服务：首次 [get] 时调用 [factory] 创建实例并缓存，后续返回同一实例。
     * 适用于无状态服务或可重入的有状态服务（如 AiService、Provider 实现）。
     */
    fun <T : Any> registerSingleton(type: Class<T>, factory: () -> T) {
        singletonFactories[type] = factory as () -> Any?
    }

    /**
     * 标记注册中心已完成初始化。
     * 由 [LianYuApplication.registerServiceProviders] 在所有 Repository 与 Provider
     * 注册完毕后调用。
     */
    fun markInitialized() {
        _initialized.value = true
    }

    /**
     * 重置初始化状态。仅在测试或 Application 重建场景使用。
     */
    fun resetInitialized() {
        _initialized.value = false
    }

    /**
     * 获取服务实例。优先返回单例缓存，其次单例工厂（首次创建后缓存），最后工厂模式（每次新建）。
     * 未注册时返回 null。
     */
    @Suppress("UNCHECKED_CAST")
    fun <T : Any> get(type: Class<T>): T? {
        // 1. 已缓存的单例
        singletons[type]?.let { return it as T }
        // 2. 单例工厂：首次创建并缓存
        singletonFactories[type]?.let { factory ->
            val instance = singletons.computeIfAbsent(type) {
                factory.invoke() ?: throw NullPointerException("Singleton factory returned null for ${type.name}")
            }
            return instance as T
        }
        // 3. 工厂模式：每次新建
        return factories[type]?.invoke() as? T
    }

    /**
     * 获取服务实例，未注册时抛出 [IllegalStateException]。
     * 语义化方法，替代 `get(...) ?: throw IllegalStateException(...)` 模式。
     */
    fun <T : Any> getOrThrow(type: Class<T>): T {
        return get(type) ?: throw IllegalStateException("${type.name} not registered in ServiceRegistry")
    }

    /**
     * 移除指定类型的服务注册（含单例缓存）。
     */
    fun <T : Any> unregister(type: Class<T>) {
        factories.remove(type)
        singletonFactories.remove(type)
        singletons.remove(type)
    }

    /**
     * 清空所有注册项与单例缓存。仅在 Application.onTerminate 调用。
     */
    fun clear() {
        factories.clear()
        singletonFactories.clear()
        singletons.clear()
    }
}
