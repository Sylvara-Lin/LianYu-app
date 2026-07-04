package com.lianyu.ai.network

import android.content.Context
import com.lianyu.ai.common.SecureLog
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress

/**
 * NTP 精确时间提供者。
 * 自实现轻量 SNTP 客户端，通过 NTP 服务器获取不受设备时钟影响的精确网络时间。
 * 无第三方依赖，优雅降级：NTP 未同步时回退到设备本地时钟。
 *
 * 原理：发送 48 字节 SNTP 请求包，解析服务器返回的 Transmit Timestamp，
 * 结合网络往返延迟 (RTT) 计算精确偏移量，缓存 offset 用于后续时间校正。
 */
object NtpTimeProvider {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    // NTP 服务器池（中国友好）
    private val NTP_HOSTS = listOf(
        "ntp.aliyun.com",       // 阿里云 NTP
        "time1.cloud.tencent.com", // 腾讯云 NTP
        "cn.ntp.org.cn",        // 中国 NTP
        "pool.ntp.org",         // 全球 NTP 池
    )

    private const val NTP_PORT = 123
    private const val TIMEOUT_MS = 5000
    private const val NTP_EPOCH_DIFF = 2208988800000L // 1900→1970 毫秒差

    @Volatile
    private var cachedOffset: Long = 0L  // NTP offset in ms

    @Volatile
    private var synced: Boolean = false

    @Volatile
    private var lastSyncTime: Long = 0L

    private const val CACHE_VALIDITY_MS = 60_000L // 缓存 1 分钟有效

    /** 在 Application.onCreate 中调用，后台同步 NTP */
    fun initialize(context: Context) {
        syncInBackground()
    }

    /** 获取当前时间毫秒数。NTP 已同步时返回校正后的网络时间，否则回退设备时钟 */
    fun getCurrentTimeMs(): Long {
        if (!synced) return System.currentTimeMillis()
        // 缓存过期则触发后台刷新
        if (System.currentTimeMillis() - lastSyncTime > CACHE_VALIDITY_MS) {
            syncInBackground()
        }
        return System.currentTimeMillis() + cachedOffset
    }

    /** NTP 是否已完成至少一次同步 */
    fun isNtpSynced(): Boolean = synced

    /** 触发后台同步 */
    fun syncInBackground() {
        scope.launch {
            performSync()
        }
    }

    /** 同步执行 NTP 同步，尝试多个服务器 */
    private suspend fun performSync() {
        for (host in NTP_HOSTS) {
            try {
                val offset = queryNtp(host)
                if (offset != null) {
                    cachedOffset = offset
                    synced = true
                    lastSyncTime = System.currentTimeMillis()
                    SecureLog.i("NtpTimeProvider", "NTP synced with $host, offset=${offset}ms")
                    return
                }
            } catch (e: Exception) {
                SecureLog.w("NtpTimeProvider", "NTP sync failed for $host: ${e.message}")
            }
        }
        SecureLog.w("NtpTimeProvider", "All NTP servers unreachable, using device clock")
    }

    /**
     * 向单个 NTP 服务器发送 SNTP 请求并计算偏移量。
     * 使用 RFC 4330 简化算法：offset = ((T2-T1) + (T3-T4)) / 2
     */
    private fun queryNtp(host: String): Long? {
        val socket = DatagramSocket()
        return try {
            socket.soTimeout = TIMEOUT_MS
            val address = InetAddress.getByName(host)

            // 构建 48 字节 SNTP 请求包
            val buffer = ByteArray(48)
            buffer[0] = 0x1B.toByte() // LI=0, VN=3, Mode=3 (client)

            val requestTime = System.currentTimeMillis()
            val sendNtpTime = requestTime + NTP_EPOCH_DIFF
            writeTimestamp(buffer, 40, sendNtpTime)

            // 发送
            val sendPacket = DatagramPacket(buffer, buffer.size, address, NTP_PORT)
            socket.send(sendPacket)

            // 接收
            val receiveBuffer = ByteArray(48)
            val receivePacket = DatagramPacket(receiveBuffer, receiveBuffer.size)
            socket.receive(receivePacket)

            val responseTime = System.currentTimeMillis()

            // 解析服务器时间戳
            val originateTimestamp = readTimestamp(receiveBuffer, 24)  // T1: 客户端发送时间
            val receiveTimestamp = readTimestamp(receiveBuffer, 32)    // T2: 服务器接收时间
            val transmitTimestamp = readTimestamp(receiveBuffer, 40)   // T3: 服务器发送时间
            // T4 = responseTime (客户端接收时间)

            if (originateTimestamp == 0L || transmitTimestamp == 0L) return null

            // 验证 originate timestamp 匹配（防止欺骗）
            val sendNtpMs = sendNtpTime
            if (kotlin.math.abs(originateTimestamp - sendNtpMs) > 1000) return null

            // RFC 4330: offset = ((T2 - T1) + (T3 - T4)) / 2
            val t1 = sendNtpTime - NTP_EPOCH_DIFF
            val t2 = receiveTimestamp - NTP_EPOCH_DIFF
            val t3 = transmitTimestamp - NTP_EPOCH_DIFF
            val t4 = responseTime

            val offset = ((t2 - t1) + (t3 - t4)) / 2
            val rtt = (t4 - t1) - (t3 - t2)

            // RTT 异常大则丢弃（>1秒）
            if (rtt > 1000 || rtt < 0) return null

            return offset
        } catch (e: Exception) {
            null
        } finally {
            socket.close()
        }
    }

    /** 将 NTP 时间戳写入 buffer（从 1900 年起的毫秒数） */
    private fun writeTimestamp(buffer: ByteArray, offset: Int, ntpTimeMs: Long) {
        val seconds = ntpTimeMs / 1000L
        val fraction = ((ntpTimeMs % 1000L) * 0x100000000L / 1000L).toInt()
        buffer[offset] = (seconds ushr 24).toByte()
        buffer[offset + 1] = (seconds ushr 16).toByte()
        buffer[offset + 2] = (seconds ushr 8).toByte()
        buffer[offset + 3] = seconds.toByte()
        buffer[offset + 4] = (fraction ushr 24).toByte()
        buffer[offset + 5] = (fraction ushr 16).toByte()
        buffer[offset + 6] = (fraction ushr 8).toByte()
        buffer[offset + 7] = fraction.toByte()
    }

    /** 从 buffer 读取 NTP 时间戳并转为毫秒（从 1900 年起） */
    private fun readTimestamp(buffer: ByteArray, offset: Int): Long {
        val seconds = ((buffer[offset].toLong() and 0xFF) shl 24) or
                ((buffer[offset + 1].toLong() and 0xFF) shl 16) or
                ((buffer[offset + 2].toLong() and 0xFF) shl 8) or
                (buffer[offset + 3].toLong() and 0xFF)
        val fraction = ((buffer[offset + 4].toLong() and 0xFF) shl 24) or
                ((buffer[offset + 5].toLong() and 0xFF) shl 16) or
                ((buffer[offset + 6].toLong() and 0xFF) shl 8) or
                (buffer[offset + 7].toLong() and 0xFF)
        // 使用浮点避免大数溢出：fraction / 2^32 * 1000
        val fractionMs = (fraction.toDouble() / 4294967296.0 * 1000.0).toLong()
        return seconds * 1000L + fractionMs
    }
}
