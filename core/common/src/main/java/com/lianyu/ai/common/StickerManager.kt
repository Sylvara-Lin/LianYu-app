package com.lianyu.ai.common

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.zip.ZipInputStream

/**
 * 表情包管理器
 * 支持从assets和本地文件加载表情包，支持zip压缩包导入
 * 支持 custom_stickers.json 规则文件，AI可根据描述自动发送表情包
 */
class StickerManager(private val context: Context) {

    private val stickersDir = File(context.filesDir, "stickers")
    private val importedDir = File(stickersDir, "imported")
    private val stickerRulesFile = File(importedDir, "custom_stickers.json")

    // 缓存表情包规则映射: description -> StickerRule
    private var stickerRules: Map<String, StickerRule> = emptyMap()

    init {
        stickersDir.mkdirs()
        importedDir.mkdirs()
        loadRules()
    }

    /**
     * 表情包规则数据类
     */
    data class StickerRule(
        val description: String,
        val fileName: String,
        val path: String
    )

    /**
     * 加载 custom_stickers.json 规则文件
     */
    private fun loadRules() {
        try {
            Log.i("StickerManager", "Loading rules from: ${stickerRulesFile.absolutePath}")
            Log.i("StickerManager", "File exists: ${stickerRulesFile.exists()}")
            Log.i("StickerManager", "Imported dir: ${importedDir.absolutePath}")
            Log.i("StickerManager", "Imported dir exists: ${importedDir.exists()}")

            // 列出导入目录中的所有文件
            val files = importedDir.listFiles()
            if (files != null) {
                Log.i("StickerManager", "Files in imported dir (${files.size}):")
                files.forEach { Log.i("StickerManager", "  - ${it.name} (${it.length()} bytes)") }
            } else {
                Log.i("StickerManager", "No files in imported dir")
            }

            if (!stickerRulesFile.exists()) {
                Log.w("StickerManager", "Rules file not found!")
                stickerRules = emptyMap()
                return
            }
            val json = stickerRulesFile.readText()
            Log.i("StickerManager", "Rules file content length: ${json.length}")
            val array = JSONArray(json)
            Log.i("StickerManager", "JSON array length: ${array.length()}")
            val rules = mutableMapOf<String, StickerRule>()
            for (i in 0 until array.length()) {
                val obj = array.getJSONObject(i)
                val description = obj.optString("description", "")
                val fileName = obj.optString("fileName", "")
                Log.i("StickerManager", "Rule $i: description='$description', fileName='$fileName'")
                if (description.isNotEmpty() && fileName.isNotEmpty()) {
                    val file = File(importedDir, fileName)
                    Log.i("StickerManager", "Checking file: ${file.absolutePath}, exists=${file.exists()}")
                    if (file.exists()) {
                        rules[description] = StickerRule(
                            description = description,
                            fileName = fileName,
                            path = file.absolutePath
                        )
                        Log.i("StickerManager", "Added rule: $description")
                    } else {
                        Log.w("StickerManager", "File not found: $fileName")
                    }
                }
            }
            stickerRules = rules
            Log.i("StickerManager", "Loaded ${rules.size} sticker rules")
        } catch (e: Exception) {
            Log.e("StickerManager", "Failed to load sticker rules", e)
            stickerRules = emptyMap()
        }
    }

    /**
     * 获取所有可用的表情包
     * 包括内置的和用户导入的
     */
    suspend fun getAllStickers(): List<StickerInfo> = withContext(Dispatchers.IO) {
        val stickers = mutableListOf<StickerInfo>()

        // 1. 从assets加载内置表情包
        try {
            val assetStickers = context.assets.list("stickers") ?: emptyArray()
            assetStickers.forEach { filename ->
                if (isImageFile(filename)) {
                    stickers.add(StickerInfo(
                        name = filename.substringBeforeLast("."),
                        path = "asset://stickers/$filename",
                        category = "default",
                        isBuiltIn = true
                    ))
                }
            }
        } catch (e: Exception) {
            SecureLog.w("StickerManager", "No assets/stickers found")
        }

        // 2. 从导入目录加载用户表情包（优先使用规则中的描述作为name）
        val rules = stickerRules
        importedDir.listFiles()?.forEach { file ->
            if (isImageFile(file.name)) {
                // 查找规则中的描述
                val rule = rules.values.find { it.fileName == file.name }
                stickers.add(StickerInfo(
                    name = rule?.description ?: file.nameWithoutExtension,
                    path = file.absolutePath,
                    category = "imported",
                    isBuiltIn = false,
                    description = rule?.description,
                    fileName = file.name
                ))
            }
        }

        stickers
    }

    /**
     * 根据描述精确查找表情包
     * 用于AI发送表情包时严格匹配
     */
    fun findStickerByDescriptionExact(keyword: String): StickerInfo? {
        if (keyword.isBlank()) return null

        val rules = stickerRules

        // 只精确匹配描述
        rules[keyword]?.let { rule ->
            return StickerInfo(
                name = rule.description,
                path = rule.path,
                category = "imported",
                isBuiltIn = false,
                description = rule.description,
                fileName = rule.fileName
            )
        }

        return null
    }

    /**
     * 根据描述或文件名查找匹配的表情包
     * 用于AI根据情绪/场景自动选择表情包，也用于显示已发送的表情包
     */
    fun findStickerByDescription(keyword: String): StickerInfo? {
        if (keyword.isBlank()) return null

        val rules = stickerRules

        // 0. 先尝试直接匹配文件名（用于显示已发送的表情包）
        val directFile = File(importedDir, keyword)
        if (directFile.exists()) {
            val rule = rules.values.find { it.fileName == keyword }
            return StickerInfo(
                name = rule?.description ?: keyword.substringBeforeLast("."),
                path = directFile.absolutePath,
                category = "imported",
                isBuiltIn = false,
                description = rule?.description,
                fileName = keyword
            )
        }

        // 1. 精确匹配描述
        rules[keyword]?.let { rule ->
            return StickerInfo(
                name = rule.description,
                path = rule.path,
                category = "imported",
                isBuiltIn = false,
                description = rule.description,
                fileName = rule.fileName
            )
        }

        // 2. 模糊匹配 - 关键词包含在描述中
        rules.entries.find { it.key.contains(keyword) || keyword.contains(it.key) }?.let { entry ->
            return StickerInfo(
                name = entry.value.description,
                path = entry.value.path,
                category = "imported",
                isBuiltIn = false,
                description = entry.value.description,
                fileName = entry.value.fileName
            )
        }

        // 3. 尝试匹配文件名（不带扩展名）
        importedDir.listFiles()?.forEach { file ->
            if (file.nameWithoutExtension == keyword || file.name == keyword) {
                val rule = rules.values.find { it.fileName == file.name }
                return StickerInfo(
                    name = rule?.description ?: file.nameWithoutExtension,
                    path = file.absolutePath,
                    category = "imported",
                    isBuiltIn = false,
                    description = rule?.description,
                    fileName = file.name
                )
            }
        }

        return null
    }

    /**
     * 获取所有表情包规则，用于AI系统提示
     * 会重新加载规则文件以确保最新
     */
    fun getAllRules(): List<StickerRule> {
        loadRules()
        return stickerRules.values.toList()
    }

    /**
     * 导入表情包压缩包（zip）
     * 支持包含 custom_stickers.json 规则文件的压缩包
     * @param zipPath zip文件路径
     * @return 导入的表情包数量
     */
    suspend fun importStickerZip(zipPath: String): Int = withContext(Dispatchers.IO) {
        var count = 0
        var hasRules = false
        try {
            val zipFile = File(zipPath)
            Log.i("StickerManager", "Importing zip: $zipPath")
            Log.i("StickerManager", "Zip file exists: ${zipFile.exists()}")
            Log.i("StickerManager", "Zip file size: ${zipFile.length()} bytes")

            if (!zipFile.exists()) {
                Log.e("StickerManager", "Zip file not found: $zipPath")
                return@withContext 0
            }

            ZipInputStream(zipFile.inputStream()).use { zis ->
                var entry = zis.nextEntry
                while (entry != null) {
                    val entryName = entry.name.substringAfterLast("/")
                    Log.i("StickerManager", "ZIP entry: $entryName (isDir=${entry.isDirectory})")
                    when {
                        // 解析规则文件
                        entryName == "custom_stickers.json" -> {
                            val destFile = File(importedDir, entryName)
                            Log.i("StickerManager", "Extracting rules to: ${destFile.absolutePath}")
                            destFile.outputStream().use { output ->
                                zis.copyTo(output)
                            }
                            hasRules = true
                            Log.i("StickerManager", "Rules file extracted successfully, size=${destFile.length()}")
                        }
                        // 导入图片文件
                        !entry.isDirectory && isImageFile(entryName) -> {
                            val destFile = File(importedDir, entryName)
                            destFile.outputStream().use { output ->
                                zis.copyTo(output)
                            }
                            count++
                            Log.i("StickerManager", "Extracted image: $entryName")
                        }
                    }
                    entry = zis.nextEntry
                }
            }

            // 如果有规则文件，重新加载规则
            if (hasRules) {
                Log.i("StickerManager", "Has rules, reloading...")
                loadRules()
            } else {
                Log.w("StickerManager", "No rules file found in zip!")
            }

            Log.i("StickerManager", "Import complete: $count stickers, rules=$hasRules")
            count
        } catch (e: Exception) {
            Log.e("StickerManager", "Failed to import zip", e)
            count
        }
    }

    /**
     * 删除导入的表情包
     */
    suspend fun deleteImportedSticker(fileName: String): Boolean = withContext(Dispatchers.IO) {
        try {
            val file = File(importedDir, fileName)
            if (file.exists()) {
                file.delete()
                Log.i("StickerManager", "Deleted sticker: $fileName")
                true
            } else {
                false
            }
        } catch (e: Exception) {
            Log.e("StickerManager", "Failed to delete sticker", e)
            false
        }
    }

    /**
     * 删除所有导入的表情包和规则文件
     */
    suspend fun deleteAllImportedStickers(): Boolean = withContext(Dispatchers.IO) {
        try {
            var deleted = 0
            importedDir.listFiles()?.forEach { file ->
                if (file.delete()) deleted++
            }
            stickerRules = emptyMap()
            Log.i("StickerManager", "Deleted all $deleted imported stickers")
            true
        } catch (e: Exception) {
            Log.e("StickerManager", "Failed to delete all stickers", e)
            false
        }
    }

    /**
     * 获取表情包的Bitmap
     */
    suspend fun loadStickerBitmap(stickerPath: String): Bitmap? = withContext(Dispatchers.IO) {
        try {
            when {
                stickerPath.startsWith("asset://") -> {
                    val assetPath = stickerPath.removePrefix("asset://")
                    context.assets.open(assetPath).use { stream ->
                        BitmapFactory.decodeStream(stream)
                    }
                }
                else -> {
                    BitmapFactory.decodeFile(stickerPath)
                }
            }
        } catch (e: Exception) {
            SecureLog.e("StickerManager", "Failed to load bitmap", e)
            null
        }
    }

    /**
     * 获取导入目录路径
     */
    fun getImportedDir(): String = importedDir.absolutePath

    /**
     * 根据回复文本的情绪/内容主动选择合适的表情包
     * AI 不需要写 [描述] 标记也能自动发送表情包
     */
    fun pickStickerForMood(text: String): StickerInfo? {
        if (text.isBlank()) return null

        val lowerText = text.lowercase()
        val rules = stickerRules
        val allAvailableStickers = mutableListOf<StickerInfo>()

        rules.values.forEach { rule ->
            allAvailableStickers.add(StickerInfo(
                name = rule.description,
                path = rule.path,
                category = "imported",
                isBuiltIn = false,
                description = rule.description,
                fileName = rule.fileName
            ))
        }

        importedDir.listFiles()?.filter { isImageFile(it.name) }?.forEach { file ->
            val existingRule = rules.values.find { it.fileName == file.name }
            if (existingRule == null) {
                allAvailableStickers.add(StickerInfo(
                    name = file.nameWithoutExtension,
                    path = file.absolutePath,
                    category = "imported",
                    isBuiltIn = false,
                    fileName = file.name
                ))
            }
        }

        try {
            context.assets.list("stickers")?.filter { isImageFile(it) }?.forEach { filename ->
                allAvailableStickers.add(StickerInfo(
                    name = filename.substringBeforeLast("."),
                    path = "asset://stickers/$filename",
                    category = "default",
                    isBuiltIn = true
                ))
            }
        } catch (_: Exception) {}

        if (allAvailableStickers.isEmpty()) return null

        val moodKeywords = listOf(
            "开心" to listOf("开心", "高兴", "快乐", "笑", "哈哈", "嘻嘻", "嘿嘿", "好耶", "太棒了", "棒", "赞", "好", "nice"),
            "委屈" to listOf("委屈", "难过", "伤心", "哭", "呜呜", "呜", "眼泪", "可怜", "心疼", "好惨", "不想", "难过"),
            "生气" to listOf("生气", "气", "哼", "烦", "讨厌", "滚", "不理你", "不想理", "气鼓鼓", "怒"),
            "害羞" to listOf("害羞", "脸红", "不好意思", "羞", "呀", "哎呀", "啊这", "那个", "嗯..."),
            "惊讶" to listOf("惊讶", "哇", "天哪", "什么", "真的吗", "不会吧", "居然", "竟然", "啊？"),
            "撒娇" to listOf("撒娇", "嘛", "啦", "呢", "呀", "人家", "求你", "好不好", "嘛~", "拜托", "亲"),
            "可爱" to listOf("可爱", "萌", "乖", "乖巧", "听话", "小", "宝贝", "宝宝", "亲亲", "抱抱", "喜欢"),
            "无奈" to listOf("无奈", "算了", "服了", "无语", "行吧", "好吧", "随你", "随便", "额"),
            "思考" to listOf("思考", "想想", "嗯", "让我想", "不知道", "好像", "也许", "可能", "这个"),
            "困倦" to listOf("困", "累", "睡", "瞌睡", "哈欠", "晚安", "早安", " tired ")
        )

        for ((mood, keywords) in moodKeywords) {
            for (keyword in keywords) {
                if (lowerText.contains(keyword)) {
                    val matched = allAvailableStickers.find { 
                        (it.description?.contains(mood) == true) || 
                        (it.name.contains(mood)) ||
                        (it.fileName?.contains(mood) == true)
                    } ?: allAvailableStickers.find {
                        (it.description?.contains(keyword) == true) || 
                        (it.name.contains(keyword))
                    }
                    if (matched != null) {
                        Log.d("StickerManager", "Mood match: keyword='$keyword' → sticker='${matched.name}'")
                        return matched
                    }
                }
            }
        }

        val picked = allAvailableStickers.random()
        Log.d("StickerManager", "Random pick from ${allAvailableStickers.size} stickers: '${picked.name}'")
        return picked
    }

    private fun isImageFile(filename: String): Boolean {
        val ext = filename.substringAfterLast(".", "").lowercase()
        return ext in listOf("png", "jpg", "jpeg", "gif", "webp")
    }

    companion object {
        @Volatile
        private var instance: StickerManager? = null

        fun getInstance(context: Context): StickerManager {
            return instance ?: synchronized(this) {
                instance ?: StickerManager(context.applicationContext).also { instance = it }
            }
        }
    }
}

/**
 * 表情包信息
 */
data class StickerInfo(
    val name: String,
    val path: String,
    val category: String = "default",
    val isBuiltIn: Boolean = true,
    val description: String? = null,
    val fileName: String? = null
)