package com.lianyu.ai.feature.companion.ui.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.lianyu.ai.common.CompanionRole
import com.lianyu.ai.database.DefaultCompanionSeeder
import com.lianyu.ai.database.model.CompanionEntity
import com.lianyu.ai.database.repository.CompanionRepository
import com.lianyu.ai.database.repository.UserRepository
import com.lianyu.ai.common.ImageUtils
import com.lianyu.ai.domain.AiServiceProvider
import com.lianyu.ai.domain.ServiceRegistry
import androidx.core.content.edit
import com.lianyu.ai.common.SecureLog
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.Dispatchers
import kotlin.text.RegexOption

class CreateCompanionViewModel(application: Application) : AndroidViewModel(application) {
    // [C5 FIX] 改为 by lazy 延迟获取：原在构造函数立即 getOrThrow，若页面在 ServiceRegistry
    // 标记初始化完成前被导航到（冷启动 deep link / 配置变更），会抛 IllegalStateException 闪退。
    private val repository by lazy { ServiceRegistry.getOrThrow(CompanionRepository::class.java) }
    private val userRepository by lazy { ServiceRegistry.getOrThrow(UserRepository::class.java) }

    val selectedRole: StateFlow<CompanionRole> by lazy { userRepository.selectedRole }

    private val _existingCompanion = MutableStateFlow<CompanionEntity?>(null)
    val existingCompanion: StateFlow<CompanionEntity?> = _existingCompanion

    private val _saveCompleted = MutableStateFlow(false)
    val saveCompleted: StateFlow<Boolean> = _saveCompleted

    private val _isSaving = MutableStateFlow(false)
    val isSaving: StateFlow<Boolean> = _isSaving

    private val _saveError = MutableStateFlow<String?>(null)
    val saveError: StateFlow<String?> = _saveError

    private val _isGenerating = MutableStateFlow(false)
    val isGenerating: StateFlow<Boolean> = _isGenerating

    private val aiService by lazy { ServiceRegistry.getOrThrow(AiServiceProvider::class.java) }

    fun loadCompanion(id: Long) {
        viewModelScope.launch {
            _existingCompanion.value = repository.getCompanionById(id)
        }
    }

    /**
     * 直接保存人设。所有 IO/数据库操作在 Dispatchers.IO 执行，异常被捕获并暴露为 saveError，
     * 避免未处理异常导致应用闪退。
     */
    fun saveCompanion(companion: CompanionEntity, isEditMode: Boolean) {
        viewModelScope.launch {
            _isSaving.value = true
            _saveError.value = null
            try {
                val avatarUrl = companion.avatarUrl
                val savedAvatar = if (avatarUrl != null && avatarUrl.startsWith("content://")) {
                    ImageUtils.saveUriToInternalStorage(getApplication(), avatarUrl)
                } else avatarUrl

                val companionToSave = companion.copy(avatarUrl = savedAvatar)
                withContext(Dispatchers.IO) {
                    if (isEditMode) {
                        repository.updateCompanion(companionToSave)
                    } else {
                        repository.insertCompanion(companionToSave)
                    }
                }
                _saveCompleted.value = true
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                SecureLog.e("CreateCompanionVM", "保存人设失败", e)
                _saveError.value = "保存失败：${e.message ?: "未知错误"}"
            } finally {
                _isSaving.value = false
            }
        }
    }

    fun consumeSaveError() {
        _saveError.value = null
    }

    fun resetSaveCompleted() {
        _saveCompleted.value = false
    }

    fun deleteCompanion(companion: CompanionEntity) {
        viewModelScope.launch {
            if (companion.tags.orEmpty()
                    .split(',')
                    .map { it.trim() }
                    .any { it == DefaultCompanionSeeder.LEGACY_TAG }
            ) {
                getApplication<Application>()
                    .getSharedPreferences("default_companion", android.content.Context.MODE_PRIVATE)
                    .edit { putBoolean("deleted_by_user", true) }
            }
            repository.deleteCompanion(companion)
        }
    }

    /**
     * 根据当前角色返回身材建议选项。
     */
    fun bodyTypeSuggestions(role: CompanionRole): List<String> = when (role) {
        CompanionRole.GIRLFRIEND -> listOf("纤细", "匀称", "娇小", "高挑", "微胖", "元气")
        CompanionRole.BOYFRIEND -> listOf("偏瘦", "健壮", "高挑", "结实", "清瘦", "运动型")
    }

    /**
     * 根据当前角色返回职业建议选项。
     */
    fun professionSuggestions(role: CompanionRole): List<String> = when (role) {
        CompanionRole.GIRLFRIEND -> listOf("学生", "插画师", "幼师", "护士", "文员", "自由职业")
        CompanionRole.BOYFRIEND -> listOf("程序员", "设计师", "医生", "工程师", "教师", "自由职业")
    }

    /**
     * 根据当前角色返回典型性格标签。
     */
    fun personalityTags(role: CompanionRole): List<String> = when (role) {
        CompanionRole.GIRLFRIEND -> listOf(
            "温柔", "粘人", "体贴", "爱撒娇", "活泼", "害羞", "傲娇",
            "细心", "爱吃醋", "浪漫", "懂事", "软萌", "治愈", "俏皮"
        )
        CompanionRole.BOYFRIEND -> listOf(
            "可靠", "温柔", "有担当", "护短", "沉稳", "直率", "笨拙温柔",
            "细心", "爱吃醋", "爽朗", "理性", "宠溺", "安全感", "闷骚"
        )
    }

    fun generatePersonaByAi(
        name: String,
        role: CompanionRole,
        referenceCharacter: String? = null,
        bodyType: String? = null,
        profession: String? = null,
        personalityTags: List<String> = emptyList(),
        onResult: (String) -> Unit
    ) {
        if (name.isBlank()) return
        _isGenerating.value = true
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val roleLabel = when (role) {
                    CompanionRole.GIRLFRIEND -> "AI女友"
                    CompanionRole.BOYFRIEND -> "AI男友"
                }
                val pronoun = when (role) {
                    CompanionRole.GIRLFRIEND -> "她"
                    CompanionRole.BOYFRIEND -> "他"
                }
                val refPart = if (!referenceCharacter.isNullOrBlank()) {
                    "\n参考角色风格：$referenceCharacter"
                } else ""
                val bodyPart = if (!bodyType.isNullOrBlank()) {
                    "\n身材特征：$bodyType"
                } else ""
                val profPart = if (!profession.isNullOrBlank()) {
                    "\n职业背景：$profession"
                } else ""
                val tagPart = if (personalityTags.isNotEmpty()) {
                    "\n必须体现的性格标签：${personalityTags.joinToString("、")}"
                } else ""
                val prompt = """你是一个专业的人设/角色设定生成器。请为名为「${name}」的${roleLabel}生成详细、完整、不截断的人设。

要求：
1. 输出纯文本，不要JSON、不要markdown格式符
2. 必须包含以下完整板块，每个板块都要有具体内容：
   - 性格特点（5-8条，立体有层次，有优点也有小缺点）
   - 说话风格（语气、用词习惯、口头禅、特殊表达方式，要符合${roleLabel}的性别特征）
   - 背景故事（成长经历、家庭环境、重要转折点，要有记忆点）
   - 行为习惯（日常爱好、小动作、饮食偏好、作息等细节）
   - 情感模式（对待感情的态度、表达方式、敏感点）
   - 互动特点（如何回应他人、生气时的表现、开心时的表现）
3. 说话风格要自然，像真人而不是AI
4. 每个板块内容要充实，不要一句话带过
5. 总长度 800~1500 字，必须完整输出，不要截断
6. 角色性别为${roleLabel}，请用${pronoun}来指代角色，避免性别混淆$bodyPart$profPart$tagPart$refPart

直接输出人设内容，不要任何前缀或解释。"""

                SecureLog.d("CreateCompanionVM", "开始AI生成人设，name=$name, role=$roleLabel")
                val result = aiService.callGeneration(prompt)
                SecureLog.d("CreateCompanionVM", "AI生成结果长度=${result.length}")

                val cleaned = result
                    .removePrefix("```")
                    .removeSuffix("```")
                    .replace(Regex("^json\\s*", RegexOption.MULTILINE), "")
                    .trim()

                withContext(Dispatchers.Main) {
                    onResult(cleaned)
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                SecureLog.e("CreateCompanionVM", "AI生成人设失败", e)
                withContext(Dispatchers.Main) {
                    onResult("")
                }
            } finally {
                _isGenerating.value = false
                SecureLog.d("CreateCompanionVM", "AI生成结束")
            }
        }
    }
}
