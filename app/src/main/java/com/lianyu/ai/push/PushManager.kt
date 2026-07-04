package com.lianyu.ai.push

import android.app.Application
import com.lianyu.ai.common.RomUtils
import com.lianyu.ai.common.SecureLog
import com.lianyu.ai.push.vendor.HuaweiPushInitializer
import com.lianyu.ai.push.vendor.OppoPushInitializer
import com.lianyu.ai.push.vendor.VivoPushInitializer
import com.lianyu.ai.push.vendor.XiaomiPushInitializer

/**
 * 厂商推送统一初始化入口。
 *
 * 根据当前 ROM 品牌选择性地注册对应厂商 Push SDK，避免在非目标厂商手机上
 * 执行无效初始化，同时降低包体积和运行时开销。
 */
object PushManager {

    private const val TAG = "PushManager"

    fun init(application: Application) {
        SecureLog.d(TAG, "Initializing vendor push on ${RomUtils.getRomDisplayName()}")

        when {
            RomUtils.isOppo -> OppoPushInitializer.register(application)
            RomUtils.isVivo -> VivoPushInitializer.register(application)
            RomUtils.isXiaomi -> XiaomiPushInitializer.register(application)
            RomUtils.isHuawei -> HuaweiPushInitializer.register(application)
            else -> SecureLog.d(TAG, "No vendor push match for this device")
        }
    }
}
