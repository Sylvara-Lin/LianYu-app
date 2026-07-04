package com.lianyu.ai.common

import android.content.Context
import android.os.Build
import android.os.Environment
import java.io.File
import java.io.FileInputStream
import java.util.Locale
import java.util.Properties

/**
 * ROM / 厂商识别工具。
 *
 * 用于区分 ColorOS / OriginOS / FuntouchOS / MIUI / HyperOS / EMUI / HarmonyOS /
 * OnePlus / Samsung 等常见国产 ROM，以便做针对性的系统设置跳转和权限引导。
 */
object RomUtils {

    enum class RomType {
        COLOR_OS,       // OPPO / Realme
        ORIGIN_OS,      // vivo / iQOO 新系统
        FUNTOUCH_OS,    // vivo / iQOO 旧系统
        MIUI,           // 小米 / Redmi
        HYPER_OS,       // 小米澎湃 OS
        EMUI,           // 华为旧系统
        HARMONY_OS,     // 华为鸿蒙
        ONEPLUS,        // 一加氧 OS / ColorOS for OnePlus
        SAMSUNG,
        OTHER
    }

    private const val KEY_VERSION_OPPO = "ro.build.version.opporom"
    private const val KEY_VERSION_VIVO = "ro.vivo.os.version"
    private const val KEY_VERSION_VIVO_NAME = "ro.vivo.os.name"
    private const val KEY_VERSION_VIVO_SDK = "ro.vivo.os.version.sdk"
    // OriginOS 6 设备上可能只存在这些属性，需要一并检测
    private const val KEY_VERSION_VIVO_PRODUCT = "ro.vivo.product.version"
    private const val KEY_VERSION_VIVO_MODEL = "ro.vivo.hardware.subproduct"
    private const val KEY_VERSION_VIVO_DISPLAY = "ro.vivo.display.version"
    private const val KEY_VERSION_MIUI = "ro.miui.ui.version.name"
    private const val KEY_VERSION_MIUI_OS = "ro.miui.os.version.name"
    private const val KEY_VERSION_EMUI = "ro.build.version.emui"
    private const val KEY_VERSION_HARMONY = "hw_sc.build.platform.version"
    private const val KEY_VERSION_ONEPLUS = "ro.rom.version"

    private var cachedType: RomType? = null
    private var cachedVersion: String? = null
    private var cachedMajorVersion: Int? = null

    val romType: RomType
        get() {
            if (cachedType == null) {
                cachedType = detectRomType()
            }
            return cachedType!!
        }

    val romVersion: String
        get() {
            if (cachedVersion == null) {
                cachedVersion = detectRomVersion()
            }
            return cachedVersion ?: ""
        }

    /**
     * 主版本号缓存，避免重复解析字符串。
     */
    private val majorVersion: Int
        get() {
            if (cachedMajorVersion == null) {
                cachedMajorVersion = parseMajorVersion(romVersion)
            }
            return cachedMajorVersion ?: 0
        }

    val isOppo: Boolean
        get() = romType == RomType.COLOR_OS

    val isVivo: Boolean
        get() = romType == RomType.ORIGIN_OS || romType == RomType.FUNTOUCH_OS

    val isXiaomi: Boolean
        get() = romType == RomType.MIUI || romType == RomType.HYPER_OS

    val isHuawei: Boolean
        get() = romType == RomType.EMUI || romType == RomType.HARMONY_OS

    fun isOppoOrVivo(): Boolean = isOppo || isVivo

    /**
     * 判断当前 ROM 是否属于 ColorOS 12+（后台限制更严格）。
     */
    fun isColorOS12OrAbove(): Boolean {
        if (!isOppo) return false
        return majorVersion >= 12
    }

    /**
     * 判断当前 ROM 是否属于 OriginOS 3+（后台限制更严格）。
     */
    fun isOriginOS3OrAbove(): Boolean {
        if (romType != RomType.ORIGIN_OS) return false
        return majorVersion >= 3
    }

    /**
     * 判断当前 ROM 是否属于 OriginOS 5+。
     */
    fun isOriginOS5OrAbove(): Boolean {
        if (romType != RomType.ORIGIN_OS) return false
        return majorVersion >= 5
    }

    /**
     * 判断当前 ROM 是否属于 OriginOS 6+（基于 Android 15，后台限制最严格）。
     */
    fun isOriginOS6OrAbove(): Boolean {
        if (romType != RomType.ORIGIN_OS) return false
        return majorVersion >= 6
    }

    /**
     * 解析版本字符串，兼容 "OriginOS 6" / "OriginOS 6.1" / "6" / "6.0" 等格式。
     */
    private fun parseMajorVersion(version: String): Int {
        if (version.isBlank()) return 0
        val normalized = version
            .replace("OriginOS", "", ignoreCase = true)
            .replace("FuntouchOS", "", ignoreCase = true)
            .replace("origin", "", ignoreCase = true)
            .replace("funtouch", "", ignoreCase = true)
            .replace("OS", "", ignoreCase = true)
            .trim()
        return try {
            normalized.substringBefore(".").toIntOrNull() ?: 0
        } catch (_: Exception) {
            0
        }
    }

    private fun detectRomType(): RomType {
        val props = readBuildProps()

        return when {
            !props.getProperty(KEY_VERSION_OPPO).isNullOrBlank() -> RomType.COLOR_OS
            !props.getProperty(KEY_VERSION_ONEPLUS).isNullOrBlank() -> RomType.ONEPLUS
            !props.getProperty(KEY_VERSION_HARMONY).isNullOrBlank() -> RomType.HARMONY_OS
            !props.getProperty(KEY_VERSION_EMUI).isNullOrBlank() -> RomType.EMUI
            isHyperOs(props) -> RomType.HYPER_OS
            !props.getProperty(KEY_VERSION_MIUI).isNullOrBlank() -> RomType.MIUI
            isVivoDevice(props) -> {
                if (isOriginOsByProps(props)) {
                    RomType.ORIGIN_OS
                } else {
                    RomType.FUNTOUCH_OS
                }
            }
            else -> matchByManufacturer()
        }
    }

    /**
     * 判断当前设备是否为 vivo / iQOO 设备。
     */
    private fun isVivoDevice(props: Properties): Boolean {
        if (Build.MANUFACTURER.contains("vivo", ignoreCase = true) ||
            Build.MANUFACTURER.contains("iqoo", ignoreCase = true) ||
            Build.BRAND.contains("vivo", ignoreCase = true) ||
            Build.BRAND.contains("iqoo", ignoreCase = true)
        ) {
            return true
        }

        // 通过 vivo 专属属性二次确认
        val hasVivoProp = listOf(
            KEY_VERSION_VIVO,
            KEY_VERSION_VIVO_NAME,
            KEY_VERSION_VIVO_SDK,
            KEY_VERSION_VIVO_PRODUCT,
            KEY_VERSION_VIVO_MODEL,
            KEY_VERSION_VIVO_DISPLAY
        ).any { !props.getProperty(it, "").isNullOrBlank() }

        return hasVivoProp
    }

    /**
     * 判断 vivo 设备上运行的 ROM 是否为 OriginOS（新版）。
     * OriginOS 6+ 的 build.prop 中，ro.vivo.os.version / ro.vivo.os.name 可能缺失或返回
     * 类似 "PD2415B_A_6.12.2" 的版本号，导致按名称前缀匹配失败。本方法综合利用
     * 多个属性以及 Build 字段，提高识别准确率。
     */
    private fun isOriginOsByProps(props: Properties): Boolean {
        val vivoVersion = props.getProperty(KEY_VERSION_VIVO, "")
        val vivoName = props.getProperty(KEY_VERSION_VIVO_NAME, "")

        // 1. 明确名称匹配
        if (vivoVersion.startsWith("OriginOS", ignoreCase = true) ||
            vivoVersion.startsWith("origin", ignoreCase = true) ||
            vivoName.startsWith("OriginOS", ignoreCase = true) ||
            vivoName.startsWith("origin", ignoreCase = true)
        ) {
            return true
        }

        // 2. vivo 相关属性任一存在，则大概率是 OriginOS/FuntouchOS；进一步通过版本号判断。
        // OriginOS 4+ 开始，ro.vivo.os.version 通常包含主版本号（如 "6.1" / "5.12"）。
        val rawVersion = vivoVersion.ifBlank { vivoName }
        val major = parseMajorVersion(rawVersion)
        if (major >= 4) {
            return true
        }

        // 3. 通过其他 vivo 属性综合判断
        val hasVivoProp = listOf(
            KEY_VERSION_VIVO,
            KEY_VERSION_VIVO_NAME,
            KEY_VERSION_VIVO_SDK,
            KEY_VERSION_VIVO_PRODUCT,
            KEY_VERSION_VIVO_MODEL,
            KEY_VERSION_VIVO_DISPLAY
        ).any { !props.getProperty(it, "").isNullOrBlank() }

        // 4. Build 字段兜底：vivo 较新的设备通常是 OriginOS
        if (hasVivoProp || Build.MANUFACTURER.contains("vivo", ignoreCase = true)) {
            // 版本号 >= 3 认为 OriginOS；版本号缺失时，默认把近年 vivo 视为 OriginOS
            return major >= 3 || rawVersion.isBlank()
        }

        return false
    }

    private fun isHyperOs(props: Properties): Boolean {
        // HyperOS 通常保留 MIUI version name，但会新增 os version name 或特定 mod device 标记
        val miuiVersion = props.getProperty(KEY_VERSION_MIUI, "")
        val miuiOsVersion = props.getProperty(KEY_VERSION_MIUI_OS, "")
        return miuiOsVersion.contains("HyperOS", ignoreCase = true) ||
            miuiOsVersion.contains("hyperos", ignoreCase = true) ||
            miuiVersion.contains("HyperOS", ignoreCase = true)
    }

    private fun matchByManufacturer(): RomType {
        val manufacturer = Build.MANUFACTURER.lowercase(Locale.getDefault())
        return when {
            manufacturer.contains("oppo") || manufacturer.contains("realme") -> RomType.COLOR_OS
            manufacturer.contains("vivo") || manufacturer.contains("iqoo") -> RomType.ORIGIN_OS
            manufacturer.contains("xiaomi") || manufacturer.contains("redmi") -> RomType.MIUI
            manufacturer.contains("huawei") || manufacturer.contains("honor") -> RomType.EMUI
            manufacturer.contains("oneplus") -> RomType.ONEPLUS
            manufacturer.contains("samsung") -> RomType.SAMSUNG
            else -> RomType.OTHER
        }
    }

    private fun detectRomVersion(): String {
        val props = readBuildProps()
        return when (romType) {
            RomType.COLOR_OS -> props.getProperty(KEY_VERSION_OPPO, "")
            RomType.ORIGIN_OS, RomType.FUNTOUCH_OS -> props.getProperty(KEY_VERSION_VIVO, "")
            RomType.MIUI, RomType.HYPER_OS -> props.getProperty(KEY_VERSION_MIUI, "")
            RomType.EMUI -> props.getProperty(KEY_VERSION_EMUI, "")
            RomType.HARMONY_OS -> props.getProperty(KEY_VERSION_HARMONY, "")
            RomType.ONEPLUS -> props.getProperty(KEY_VERSION_ONEPLUS, "")
            else -> ""
        }
    }

    /**
     * 读取 build.prop 中的部分字段。优先读 /system/build.prop，失败则通过反射 SystemProperty。
     */
    private fun readBuildProps(): Properties {
        val props = Properties()
        try {
            val buildProp = File(Environment.getRootDirectory(), "build.prop")
            if (buildProp.canRead()) {
                FileInputStream(buildProp).use { props.load(it) }
            }
        } catch (_: Exception) {
            // ignore
        }

        // 反射兜底：android.os.SystemProperties
        try {
            val clazz = Class.forName("android.os.SystemProperties")
            val getMethod = clazz.getMethod("get", String::class.java, String::class.java)
            arrayOf(
                KEY_VERSION_OPPO,
                KEY_VERSION_VIVO,
                KEY_VERSION_VIVO_NAME,
                KEY_VERSION_VIVO_SDK,
                KEY_VERSION_VIVO_PRODUCT,
                KEY_VERSION_VIVO_MODEL,
                KEY_VERSION_VIVO_DISPLAY,
                KEY_VERSION_MIUI,
                KEY_VERSION_MIUI_OS,
                KEY_VERSION_EMUI,
                KEY_VERSION_HARMONY,
                KEY_VERSION_ONEPLUS
            ).forEach { key ->
                if (props.getProperty(key).isNullOrBlank()) {
                    val value = getMethod.invoke(null, key, "") as? String
                    if (!value.isNullOrBlank()) {
                        props.setProperty(key, value)
                    }
                }
            }
        } catch (_: Exception) {
            // ignore
        }

        return props
    }

    /**
     * 判断某个 Intent 目标 Activity 是否存在，用于 ROM 设置页跳转前的可用性检查。
     */
    fun isComponentAvailable(context: Context, packageName: String, className: String): Boolean {
        return try {
            val intent = android.content.Intent().setClassName(packageName, className)
            context.packageManager.resolveActivity(intent, 0) != null
        } catch (_: Exception) {
            false
        }
    }

    /**
     * 尝试按候选列表找到第一个可用的 ComponentName。
     */
    fun findAvailableComponent(
        context: Context,
        candidates: List<Pair<String, String>>
    ): android.content.ComponentName? {
        for ((pkg, cls) in candidates) {
            if (isComponentAvailable(context, pkg, cls)) {
                return android.content.ComponentName(pkg, cls)
            }
        }
        return null
    }

    /**
     * 获取一个可读的 ROM 名称，用于埋点和用户提示。
     */
    fun getRomDisplayName(): String {
        return when (romType) {
            RomType.COLOR_OS -> "ColorOS"
            RomType.ORIGIN_OS -> "OriginOS"
            RomType.FUNTOUCH_OS -> "FuntouchOS"
            RomType.MIUI -> "MIUI"
            RomType.HYPER_OS -> "HyperOS"
            RomType.EMUI -> "EMUI"
            RomType.HARMONY_OS -> "HarmonyOS"
            RomType.ONEPLUS -> "OxygenOS/ColorOS"
            RomType.SAMSUNG -> "OneUI"
            RomType.OTHER -> Build.MANUFACTURER
        }
    }

    private fun String.contains(other: String, ignoreCase: Boolean): Boolean {
        return if (ignoreCase) {
            this.lowercase(Locale.getDefault()).contains(other.lowercase(Locale.getDefault()))
        } else {
            this.contains(other)
        }
    }
}
