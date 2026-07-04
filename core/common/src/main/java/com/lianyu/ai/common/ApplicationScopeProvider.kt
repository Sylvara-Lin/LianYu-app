package com.lianyu.ai.common

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

/**
 * 全局应用级 CoroutineScope 提供者。
 *
 * 生命周期与 Application 一致，用于执行需要跨越 Activity/ViewModel 的后台任务
 * （例如 AI 请求），避免用户退出页面后因 ViewModel 销毁而取消正在运行的请求。
 *
 * 必须在 [LianYuApplication.onCreate] 中通过 [init] 初始化。
 */
object ApplicationScopeProvider {

    private var _scope: CoroutineScope? = null

    /**
     * 应用级作用域。未初始化前使用一个安全的默认 SupervisorJob + IO 作用域，
     * 但正常流程应在 Application 启动时注入真实作用域。
     */
    val scope: CoroutineScope
        get() = _scope ?: CoroutineScope(SupervisorJob() + Dispatchers.IO).also {
            // 兜底：如果业务未初始化则记录警告，避免 NPE。
            SecureLog.w("ApplicationScopeProvider", "Using fallback scope; did you forget to call init()?")
        }

    /**
     * 注入应用级作用域。应在 Application.onCreate 中调用一次。
     */
    fun init(scope: CoroutineScope) {
        _scope = scope
    }
}
