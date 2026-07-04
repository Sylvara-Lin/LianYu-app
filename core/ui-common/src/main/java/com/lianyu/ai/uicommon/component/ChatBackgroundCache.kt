package com.lianyu.ai.uicommon.component

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.compose.ui.platform.LocalContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.util.concurrent.ConcurrentHashMap

object ChatBackgroundCache {

    // app 级后台作用域，用于预加载聊天背景图片（替代裸 Thread，获得协程取消与命名能力）
    private val preloadScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val cache = ConcurrentHashMap<String, Bitmap?>()
    private val maxCacheSize = 3

    fun getCachedBitmap(key: String): Bitmap? {
        return cache[key]
    }

    fun loadBitmap(context: Context, key: String): Bitmap? {
        cache[key]?.let { return it }

        val file = getCustomBackgroundFile(context, key)
        if (file == null || !file.exists()) {
            cache[key] = null
            return null
        }

        return try {
            val options = BitmapFactory.Options().apply {
                inJustDecodeBounds = true
            }
            BitmapFactory.decodeFile(file.absolutePath, options)

            val reqWidth = 1080
            val reqHeight = 1920
            options.inSampleSize = calculateInSampleSize(options, reqWidth, reqHeight)
            options.inJustDecodeBounds = false
            options.inPreferredConfig = Bitmap.Config.RGB_565

            val bitmap = BitmapFactory.decodeFile(file.absolutePath, options)

            if (cache.size >= maxCacheSize) {
                val oldest = cache.keys.firstOrNull()
                if (oldest != null && oldest != key) {
                    cache.remove(oldest)?.recycle()
                }
            }

            cache[key] = bitmap
            bitmap
        } catch (_: Exception) {
            cache[key] = null
            null
        }
    }

    fun preload(context: Context, key: String) {
        if (!isCustomBackground(key)) return
        // 用协程替代裸 Thread：获得结构化并发、可取消、命名线程（通过 Dispatchers.IO 复用）
        preloadScope.launch { loadBitmap(context, key) }
    }

    fun clear() {
        cache.values.forEach { it?.recycle() }
        cache.clear()
    }

    private fun calculateInSampleSize(
        options: BitmapFactory.Options,
        reqWidth: Int,
        reqHeight: Int
    ): Int {
        val (height: Int, width: Int) = options.outHeight to options.outWidth
        var inSampleSize = 1

        if (height > reqHeight || width > reqWidth) {
            val halfHeight: Int = height / 2
            val halfWidth: Int = width / 2
            while (halfHeight / inSampleSize >= reqHeight && halfWidth / inSampleSize >= reqWidth) {
                inSampleSize *= 2
            }
        }
        return inSampleSize
    }
}

@Composable
fun rememberBackgroundBitmap(key: String): BitmapPainter? {
    val context = LocalContext.current
    var bitmap by remember { mutableStateOf<Bitmap?>(null) }

    // 先从缓存同步读取（preload可能已完成）
    if (bitmap == null) {
        bitmap = ChatBackgroundCache.getCachedBitmap(key)
    }

    // 如果缓存未命中，异步加载（不阻塞主线程）
    LaunchedEffect(key) {
        if (ChatBackgroundCache.getCachedBitmap(key) == null) {
            withContext(Dispatchers.IO) {
                ChatBackgroundCache.loadBitmap(context, key)
            }
            withContext(Dispatchers.Main) {
                bitmap = ChatBackgroundCache.getCachedBitmap(key)
            }
        }
    }

    return remember(bitmap) {
        bitmap?.let { BitmapPainter(it.asImageBitmap()) }
    }
}
