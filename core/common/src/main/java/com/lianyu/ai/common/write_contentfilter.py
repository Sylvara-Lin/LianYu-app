# -*- coding: utf-8 -*-
import codecs
content = u"""package com.lianyu.ai.common

object ContentFilter {

    data class FilterResult(
        val isBlocked: Boolean,
        val reason: String = ""
    )

    private val blockedPatterns = listOf(
        // 色情/淫秽
        Regex("(做爱|上床|性交|性爱|淫|荡|娼|嫖|妓|裸|脱衣|色情|av|成人|激情|约炮|一夜情|炮友|开房|啪啪|操你|草你|fuck|sex|porn|裸聊|文爱|磕炮)"),
        // 违法犯罪
        Regex("(杀[人了]|砍[人了]|贩毒|吸毒|毒品|海洛因|冰毒|大麻|枪支|炸弹|炸药|绑架|勒索|诈骗|洗钱|赌博|赌场|下注)"),
        // 暴力/自残
        Regex("(自杀|自残|割腕|跳楼|上吊|安眠药.*死|想死|不想活)"),
        // 儿童相关违规
        Regex("(幼女|女童|男童|未成年.*性|炼铜|ltp|loli.*sex)"