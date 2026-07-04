package com.lianyu.ai.feature.groupchat.mention

import com.lianyu.ai.database.model.CompanionEntity

object MentionNormalizer {

    fun normalizeImplicitMentions(
        content: String,
        members: List<CompanionEntity>
    ): String {
        var result = content
        val existingMentions = MentionParser.extractAllMentions(content)

        for (member in members) {
            if (existingMentions.any { it.equals(member.name, ignoreCase = true) }) continue
            if (result.contains("@${member.name}")) continue

            val hasImplicit = isImplicitlyMentioned(result, member.name)
            if (hasImplicit) {
                result = "@${member.name} $result"
            }
        }

        return result.trim()
    }

    private fun isImplicitlyMentioned(content: String, name: String): Boolean {
        val nameEscaped = Regex.escape(name)

        val patterns = listOf(
            Regex("${nameEscaped}(觉得呢|怎么看|你呢|呢\\?|认为呢|的想法|怎么说|说呢)"),
            Regex("(问问|请问|问下|让|问问看)${nameEscaped}"),
            Regex(".*${nameEscaped}[吧呀吗呢]?\\s*[？?]\\s*$")
        )

        return patterns.any { it.containsMatchIn(content) }
    }
}
