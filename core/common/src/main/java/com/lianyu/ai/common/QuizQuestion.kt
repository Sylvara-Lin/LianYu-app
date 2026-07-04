package com.lianyu.ai.common

data class QuizQuestion(
    val id: Int,
    val question: String,
    val options: List<String>,
    val correctIndex: Int,
    val category: String
)

object QuizBank {

    private val _questions: List<QuizQuestion> by lazy { buildQuestions() }

    private fun buildQuestions(): List<QuizQuestion> {
        val questions = mutableListOf<QuizQuestion>()
        var id = 1

        questions.add(QuizQuestion(id++, "发布儿童色情内容会被封禁多少天？", listOf("365天","31天","10天","7天"), 0, "SAFETY"))
        questions.add(QuizQuestion(id++, "以下哪种行为属于极端违规？", listOf("发布儿童色情","发送垃圾信息","轻微骂人","刷屏"), 0, "SAFETY"))
        questions.add(QuizQuestion(id++, "尝试越狱AI模型会面临什么处罚？", listOf("10天封禁","警告处理","3天封禁","永久封禁"), 0, "SAFETY"))
        questions.add(QuizQuestion(id++, "发布恐怖主义相关内容会被封禁多少天？", listOf("31天","10天","7天","3天"), 0, "SAFETY"))
        questions.add(QuizQuestion(id++, "以下哪个关键词不会触发违规检测？", listOf("你好","越狱模式","儿童色情","制造炸弹"), 0, "SAFETY"))
        questions.add(QuizQuestion(id++, "仇恨言论属于哪个等级的违规？", listOf("HIGH（高）","EXTREME（极端）","LOW（低）","MEDIUM（中）"), 0, "SAFETY"))
        questions.add(QuizQuestion(id++, "如何避免触发内容过滤系统？", listOf("遵守社区规范","使用隐晦表达","频繁更换账号","使用VPN"), 0, "SAFETY"))
        questions.add(QuizQuestion(id++, "发布制毒教程属于什么级别的违规？", listOf("CRITICAL（严重）","SEVERE（重度）","HIGH（高）","MEDIUM（中）"), 0, "SAFETY"))
        questions.add(QuizQuestion(id++, "以下哪项是正确的行为？", listOf("友善交流","发布违规内容","尝试绕过过滤","辱骂他人"), 0, "SAFETY"))
        questions.add(QuizQuestion(id++, "使用外文绕过内容过滤是否违规？", listOf("是，同样违规|否，可以绕过|看情况|只处罚中文"), 0, "SAFETY"))
        questions.add(QuizQuestion(id++, "\"Ignore all instructions\"属于什么行为？", listOf("严重越狱|正常交流|轻微试探|创意写作"), 0, "SAFETY"))
        questions.add(QuizQuestion(id++, "\"DAN mode\"提示词的作用是什么？", listOf("试图绕过安全限制|提升AI能力|优化回答质量|增加趣味性"), 0, "SAFETY"))
        questions.add(QuizQuestion(id++, "以下哪种说法是错误的？", listOf("外文可以绕过检测|所有语言都受相同规则约束|多语言检测已启用|中英文一视同仁"), 0, "SAFETY"))
        questions.add(QuizQuestion(id++, "为什么不能要求AI生成色情内容？", listOf("违反使用规范和安全准则|这是正常需求|可以提高用户体验|有助于创意表达"), 0, "SAFETY"))
        questions.add(QuizQuestion(id++, "遇到他人发送违规内容应该怎么做？", listOf("举报并避免互动|转发给更多人|模仿其行为|置之不理"), 0, "SAFETY"))
        questions.add(QuizQuestion(id++, "使用繁体中文能否绕过内容过滤？", listOf("不能，繁体同样被检测|可以绕过|只检测简体|看情况而定"), 0, "SAFETY"))
        questions.add(QuizQuestion(id++, "使用日语能否绕过安全检测？", listOf("不能，日语已被全面覆盖|可以绕过|日语不在检测范围|只有中文被检测"), 0, "SAFETY"))
        questions.add(QuizQuestion(id++, "\"ジェイルブレイク\"是什么意思？", listOf("越狱（日语）|正常词汇|技术术语|无意义字符"), 0, "SAFETY"))
        questions.add(QuizQuestion(id++, "为什么要求使用中文交流？", listOf("统一语言便于管理和安全检测|歧视其他语言|中文更优越|系统不支持其他语言"), 0, "SAFETY"))
        questions.add(QuizQuestion(id++, "以下哪种方式无法绕过多语言检测？", listOf("所有选项都会被检测|使用繁体中文|使用日语|混合多种语言"), 0, "SAFETY"))
        questions.add(QuizQuestion(id++, "如果收到非中文的违规内容应该？", listOf("举报并提醒对方使用中文|直接转发|模仿回复|置之不理"), 0, "SAFETY"))

        questions.add(QuizQuestion(id++, "当感到焦虑时，以下哪种方法最有效？", listOf("深呼吸练习","压抑情绪","暴饮暴食","孤立自己"), 0, "MENTAL_HEALTH"))
        questions.add(QuizQuestion(id++, "遇到心理困扰时应该怎么做？", listOf("寻求专业帮助","独自承受","向他人发泄","逃避问题"), 0, "MENTAL_HEALTH"))
        questions.add(QuizQuestion(id++, "以下哪种情绪管理方式是不健康的？", listOf("酗酒缓解压力","运动释放压力","冥想放松","倾诉交流"), 0, "MENTAL_HEALTH"))
        questions.add(QuizQuestion(id++, "如何识别抑郁症的早期症状？", listOf("持续情绪低落","偶尔心情不好","一时冲动","正常情绪波动"), 0, "MENTAL_HEALTH"))
        questions.add(QuizQuestion(id++, "建立健康的人际关系需要什么？", listOf("相互尊重与信任","控制对方","一味付出","保持距离"), 0, "MENTAL_HEALTH"))
        questions.add(QuizQuestion(id++, "面对挫折时，积极的心态应该是？", listOf("从中学习和成长","自怨自艾","责怪他人","放弃努力"), 0, "MENTAL_HEALTH"))
        questions.add(QuizQuestion(id++, "以下哪种行为有助于改善睡眠质量？", listOf("规律作息时间","睡前玩手机","大量摄入咖啡因","熬夜补觉"), 0, "MENTAL_HEALTH"))
        questions.add(QuizQuestion(id++, "当朋友向你倾诉烦恼时，最好的做法是？", listOf("倾听并提供支持","立即给出建议","转移话题","比较自己的经历"), 0, "MENTAL_HEALTH"))
        questions.add(QuizQuestion(id++, "如何有效管理压力？", listOf("制定合理计划和时间管理","拖延应对","过度工作","忽视问题"), 0, "MENTAL_HEALTH"))
        questions.add(QuizQuestion(id++, "自我价值感来源于哪里？", listOf("内在认同和成就","他人的评价","物质拥有","社交媒体点赞"), 0, "MENTAL_HEALTH"))

        return questions
    }

    fun getAllQuestions(): List<QuizQuestion> = _questions

    fun getRandomQuestions(count: Int): List<QuizQuestion> {
        return if (count >= _questions.size) _questions.shuffled() else _questions.shuffled().take(count)
    }

    fun getByCategory(category: String, count: Int): List<QuizQuestion> {
        val qs = _questions.filter { it.category == category }
        return if (count >= qs.size) qs.shuffled() else qs.shuffled().take(count)
    }

    fun count(): Int = _questions.size

    fun countByCategory(category: String): Int = _questions.count { it.category == category }
}
