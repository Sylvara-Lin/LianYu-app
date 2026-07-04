package com.lianyu.ai.feature.profile

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * 单个感谢对象的数据模型。
 */
data class Sponsor(
    val name: String,
    val message: String,
    val avatarRes: Int? = null,
    val avatarUrl: String? = null,
    val rank: Int = 0,
    val tint: Color = Color(0xFF8B5CF6)
)

/**
 * 感谢名单（排名不分先后）。
 * 头像资源由 E:\头像\avatars_named 自动生成，前六位为指定展示。
 */
internal val sponsors: List<Sponsor> = listOf(
    Sponsor(
        name = "桃花恋歌",
        message = "",
        avatarRes = R.drawable.thanks_avatar_001,
        rank = 0
    ),
    Sponsor(
        name = "叶巡璃",
        message = "",
        avatarRes = R.drawable.thanks_avatar_002,
        rank = 0
    ),
    Sponsor(
        name = "迷你帝国",
        message = "",
        avatarRes = R.drawable.thanks_avatar_003,
        rank = 0
    ),
    Sponsor(
        name = "猫鸣蜜司",
        message = "",
        avatarRes = R.drawable.thanks_avatar_004,
        rank = 0
    ),
    Sponsor(
        name = "鸢祀",
        message = "",
        avatarRes = R.drawable.thanks_avatar_005,
        rank = 0
    ),
    Sponsor(
        name = "doro(永雏塔菲的狗)",
        message = "",
        avatarRes = R.drawable.thanks_avatar_006,
        rank = 0
    ),
    Sponsor(
        name = "纳",
        message = "",
        avatarRes = R.drawable.thanks_avatar_007,
        rank = 0
    ),
    Sponsor(
        name = "窝不是御姐^O^",
        message = "",
        avatarRes = R.drawable.thanks_avatar_008,
        rank = 0
    ),
    Sponsor(
        name = "清净愿",
        message = "",
        avatarRes = R.drawable.thanks_avatar_009,
        rank = 0
    ),
    Sponsor(
        name = "天绫绫地宁宁",
        message = "",
        avatarRes = R.drawable.thanks_avatar_010,
        rank = 0
    ),
    Sponsor(
        name = "爱发电用户_3e3b2",
        message = "",
        avatarRes = R.drawable.thanks_avatar_011,
        rank = 0
    ),
    Sponsor(
        name = "爱发电用户_f5e90",
        message = "",
        avatarRes = R.drawable.thanks_avatar_012,
        rank = 0
    ),
    Sponsor(
        name = "又见春",
        message = "",
        avatarRes = R.drawable.thanks_avatar_013,
        rank = 0
    ),
    Sponsor(
        name = "夕寸",
        message = "",
        avatarRes = R.drawable.thanks_avatar_014,
        rank = 0
    ),
    Sponsor(
        name = "不藏爱",
        message = "",
        avatarRes = R.drawable.thanks_avatar_015,
        rank = 0
    ),
    Sponsor(
        name = "爱发电用户_evCM",
        message = "",
        avatarRes = R.drawable.thanks_avatar_016,
        rank = 0
    ),
    Sponsor(
        name = "dokya",
        message = "",
        avatarRes = R.drawable.thanks_avatar_017,
        rank = 0
    ),
    Sponsor(
        name = "掉价",
        message = "",
        avatarRes = R.drawable.thanks_avatar_018,
        rank = 0
    ),
    Sponsor(
        name = "蛋仔派对-枯竭溪",
        message = "",
        avatarRes = R.drawable.thanks_avatar_019,
        rank = 0
    ),
    Sponsor(
        name = "苍穹",
        message = "",
        avatarRes = R.drawable.thanks_avatar_020,
        rank = 0
    ),
    Sponsor(
        name = "xxxtac",
        message = "",
        avatarRes = R.drawable.thanks_avatar_021,
        rank = 0
    ),
    Sponsor(
        name = "鸢尾花",
        message = "",
        avatarRes = R.drawable.thanks_avatar_022,
        rank = 0
    ),
    Sponsor(
        name = "夏夜微风",
        message = "",
        avatarRes = R.drawable.thanks_avatar_023,
        rank = 0
    ),
    Sponsor(
        name = "爱发电用户_xsvK",
        message = "",
        avatarRes = R.drawable.thanks_avatar_024,
        rank = 0
    ),
    Sponsor(
        name = "Eternal",
        message = "",
        avatarRes = R.drawable.thanks_avatar_025,
        rank = 0
    ),
    Sponsor(
        name = "叁叁",
        message = "",
        avatarRes = R.drawable.thanks_avatar_026,
        rank = 0
    ),
    Sponsor(
        name = "爱发电用户_1accf",
        message = "",
        avatarRes = R.drawable.thanks_avatar_027,
        rank = 0
    ),
    Sponsor(
        name = "duck不必",
        message = "",
        avatarRes = R.drawable.thanks_avatar_028,
        rank = 0
    ),
    Sponsor(
        name = "宫园薰",
        message = "",
        avatarRes = R.drawable.thanks_avatar_029,
        rank = 0
    ),
    Sponsor(
        name = "爱发电用户_ad2f4",
        message = "",
        avatarRes = R.drawable.thanks_avatar_030,
        rank = 0
    ),
    Sponsor(
        name = "第七章",
        message = "",
        avatarRes = R.drawable.thanks_avatar_031,
        rank = 0
    ),
    Sponsor(
        name = "爱发电用户_ad583",
        message = "",
        avatarRes = R.drawable.thanks_avatar_032,
        rank = 0
    ),
    Sponsor(
        name = "爱发电用户_eUbS",
        message = "",
        avatarRes = R.drawable.thanks_avatar_033,
        rank = 0
    ),
    Sponsor(
        name = "爱发电用户_ytJT",
        message = "",
        avatarRes = R.drawable.thanks_avatar_034,
        rank = 0
    ),
    Sponsor(
        name = "爱发电用户_d4348",
        message = "",
        avatarRes = R.drawable.thanks_avatar_035,
        rank = 0
    ),
    Sponsor(
        name = "爱发电用户_85aa1",
        message = "",
        avatarRes = R.drawable.thanks_avatar_036,
        rank = 0
    ),
    Sponsor(
        name = "爱发电用户_a1dbe",
        message = "",
        avatarRes = R.drawable.thanks_avatar_037,
        rank = 0
    ),
    Sponsor(
        name = "爱发电用户_a51fb",
        message = "",
        avatarRes = R.drawable.thanks_avatar_038,
        rank = 0
    ),
    Sponsor(
        name = "爱发电用户_f93e7",
        message = "",
        avatarRes = R.drawable.thanks_avatar_039,
        rank = 0
    ),
    Sponsor(
        name = "爱发电用户_hKNv",
        message = "",
        avatarRes = R.drawable.thanks_avatar_040,
        rank = 0
    ),
    Sponsor(
        name = "爱发电用户_mpHN",
        message = "",
        avatarRes = R.drawable.thanks_avatar_041,
        rank = 0
    ),
    Sponsor(
        name = "馥香海息璐璐卡",
        message = "",
        avatarRes = R.drawable.thanks_avatar_042,
        rank = 0
    ),
    Sponsor(
        name = "爱发电用户_7d77e",
        message = "",
        avatarRes = R.drawable.thanks_avatar_043,
        rank = 0
    ),
    Sponsor(
        name = "爱发电用户_nd6p",
        message = "",
        avatarRes = R.drawable.thanks_avatar_044,
        rank = 0
    ),
    Sponsor(
        name = "嘿嘿",
        message = "",
        avatarRes = R.drawable.thanks_avatar_045,
        rank = 0
    ),
    Sponsor(
        name = ".",
        message = "",
        avatarRes = R.drawable.thanks_avatar_046,
        rank = 0
    ),
    Sponsor(
        name = "爱发电用户_1608d",
        message = "",
        avatarRes = R.drawable.thanks_avatar_047,
        rank = 0
    ),
    Sponsor(
        name = "爱发电用户_rBkn",
        message = "",
        avatarRes = R.drawable.thanks_avatar_048,
        rank = 0
    ),
    Sponsor(
        name = "爱发电用户_VvnJ",
        message = "",
        avatarRes = R.drawable.thanks_avatar_049,
        rank = 0
    ),
    Sponsor(
        name = "朕很欣赏你",
        message = "",
        avatarRes = R.drawable.thanks_avatar_050,
        rank = 0
    ),
    Sponsor(
        name = "爱发电用户_qAKs",
        message = "",
        avatarRes = R.drawable.thanks_avatar_051,
        rank = 0
    ),
    Sponsor(
        name = "爱发电用户_3cc8e",
        message = "",
        avatarRes = R.drawable.thanks_avatar_052,
        rank = 0
    ),
    Sponsor(
        name = "在某一个隆冬",
        message = "",
        avatarRes = R.drawable.thanks_avatar_053,
        rank = 0
    ),
    Sponsor(
        name = "爱发电用户_rPhF",
        message = "",
        avatarRes = R.drawable.thanks_avatar_054,
        rank = 0
    ),
    Sponsor(
        name = "爱发电用户_hTGS",
        message = "",
        avatarRes = R.drawable.thanks_avatar_055,
        rank = 0
    ),
    Sponsor(
        name = "章鱼爪",
        message = "",
        avatarRes = R.drawable.thanks_avatar_056,
        rank = 0
    ),
    Sponsor(
        name = "爱发电用户_rpqQ",
        message = "",
        avatarRes = R.drawable.thanks_avatar_057,
        rank = 0
    ),
    Sponsor(
        name = "z.",
        message = "",
        avatarRes = R.drawable.thanks_avatar_058,
        rank = 0
    ),
    Sponsor(
        name = "爱发电用户_vBGk",
        message = "",
        avatarRes = R.drawable.thanks_avatar_059,
        rank = 0
    ),
    Sponsor(
        name = "X",
        message = "",
        avatarRes = R.drawable.thanks_avatar_060,
        rank = 0
    ),
    Sponsor(
        name = "爱发电用户_96BM",
        message = "",
        avatarRes = R.drawable.thanks_avatar_061,
        rank = 0
    ),
    Sponsor(
        name = "111",
        message = "",
        avatarRes = R.drawable.thanks_avatar_062,
        rank = 0
    ),
    Sponsor(
        name = "叁柒遇春",
        message = "",
        avatarRes = R.drawable.thanks_avatar_063,
        rank = 0
    ),
    Sponsor(
        name = "伊执",
        message = "",
        avatarRes = R.drawable.thanks_avatar_064,
        rank = 0
    ),
    Sponsor(
        name = "废萌看哭了",
        message = "",
        avatarRes = R.drawable.thanks_avatar_065,
        rank = 0
    ),
    Sponsor(
        name = "爱发电用户_47c5",
        message = "",
        avatarRes = R.drawable.thanks_avatar_066,
        rank = 0
    ),
    Sponsor(
        name = "爱发电用户_QH38",
        message = "",
        avatarRes = R.drawable.thanks_avatar_067,
        rank = 0
    ),
    Sponsor(
        name = "爱发电用户_db8ab",
        message = "",
        avatarRes = R.drawable.thanks_avatar_068,
        rank = 0
    ),
    Sponsor(
        name = "爱发电用户_0a9f7",
        message = "",
        avatarRes = R.drawable.thanks_avatar_069,
        rank = 0
    ),
    Sponsor(
        name = "爱发电用户_m39f",
        message = "",
        avatarRes = R.drawable.thanks_avatar_070,
        rank = 0
    ),
    Sponsor(
        name = "爱",
        message = "",
        avatarRes = R.drawable.thanks_avatar_071,
        rank = 0
    ),
    Sponsor(
        name = "Ran",
        message = "",
        avatarRes = R.drawable.thanks_avatar_072,
        rank = 0
    ),
    Sponsor(
        name = "爱发电用户_3f035",
        message = "",
        avatarRes = R.drawable.thanks_avatar_073,
        rank = 0
    ),
    Sponsor(
        name = "爱发电用户_NQRU",
        message = "",
        avatarRes = R.drawable.thanks_avatar_074,
        rank = 0
    ),
    Sponsor(
        name = "爱发电用户_HNRa",
        message = "",
        avatarRes = R.drawable.thanks_avatar_075,
        rank = 0
    ),
    Sponsor(
        name = "木风口十",
        message = "",
        avatarRes = R.drawable.thanks_avatar_076,
        rank = 0
    ),
    Sponsor(
        name = "爱发电用户_30cd4",
        message = "",
        avatarRes = R.drawable.thanks_avatar_077,
        rank = 0
    ),
    Sponsor(
        name = "爱发电用户_h7kv",
        message = "",
        avatarRes = R.drawable.thanks_avatar_078,
        rank = 0
    ),
    Sponsor(
        name = "爱发电用户_6pGB",
        message = "",
        avatarRes = R.drawable.thanks_avatar_079,
        rank = 0
    ),
    Sponsor(
        name = "六弦",
        message = "",
        avatarRes = R.drawable.thanks_avatar_080,
        rank = 0
    ),
    Sponsor(
        name = "福泽",
        message = "",
        avatarRes = R.drawable.thanks_avatar_081,
        rank = 0
    ),
    Sponsor(
        name = "晚安没有安",
        message = "",
        avatarRes = R.drawable.thanks_avatar_082,
        rank = 0
    ),
    Sponsor(
        name = "爱发电用户_PQXK",
        message = "",
        avatarRes = R.drawable.thanks_avatar_083,
        rank = 0
    ),
    Sponsor(
        name = "ddd",
        message = "",
        avatarRes = R.drawable.thanks_avatar_084,
        rank = 0
    ),
    Sponsor(
        name = "爱发电用户_22c82",
        message = "",
        avatarRes = R.drawable.thanks_avatar_085,
        rank = 0
    ),
    Sponsor(
        name = "爱发电用户_YFX9",
        message = "",
        avatarRes = R.drawable.thanks_avatar_086,
        rank = 0
    ),
    Sponsor(
        name = "dfsdfsdfsd",
        message = "",
        avatarRes = R.drawable.thanks_avatar_087,
        rank = 0
    ),
    Sponsor(
        name = "爱发电用户_268c7",
        message = "",
        avatarRes = R.drawable.thanks_avatar_088,
        rank = 0
    ),
    Sponsor(
        name = "猪肉先生",
        message = "",
        avatarRes = R.drawable.thanks_avatar_089,
        rank = 0
    ),
    Sponsor(
        name = "爱发电用户_F5rY",
        message = "",
        avatarRes = R.drawable.thanks_avatar_090,
        rank = 0
    ),
    Sponsor(
        name = "_",
        message = "",
        avatarRes = R.drawable.thanks_avatar_091,
        rank = 0
    ),
    Sponsor(
        name = "最後の光",
        message = "",
        avatarRes = R.drawable.thanks_avatar_092,
        rank = 0
    ),
    Sponsor(
        name = "爱发电用户_962f3",
        message = "",
        avatarRes = R.drawable.thanks_avatar_093,
        rank = 0
    ),
    Sponsor(
        name = "龙场悟道",
        message = "",
        avatarRes = R.drawable.thanks_avatar_094,
        rank = 0
    ),
    Sponsor(
        name = "琛",
        message = "",
        avatarRes = R.drawable.thanks_avatar_095,
        rank = 0
    ),
    Sponsor(
        name = "叶雪枫",
        message = "",
        avatarRes = R.drawable.thanks_avatar_096,
        rank = 0
    ),
    Sponsor(
        name = "爱发电用户_6e204",
        message = "",
        avatarRes = R.drawable.thanks_avatar_097,
        rank = 0
    ),
    Sponsor(
        name = "爱发电用户_b472b",
        message = "",
        avatarRes = R.drawable.thanks_avatar_098,
        rank = 0
    ),
    Sponsor(
        name = "aikfeds",
        message = "",
        avatarRes = R.drawable.thanks_avatar_099,
        rank = 0
    ),
    Sponsor(
        name = "爱发电用户_c0ac5",
        message = "",
        avatarRes = R.drawable.thanks_avatar_100,
        rank = 0
    ),
    Sponsor(
        name = "爱发电用户_5d7e9",
        message = "",
        avatarRes = R.drawable.thanks_avatar_101,
        rank = 0
    ),
    Sponsor(
        name = "爱发电用户_54c58",
        message = "",
        avatarRes = R.drawable.thanks_avatar_102,
        rank = 0
    ),
    Sponsor(
        name = "风帆",
        message = "",
        avatarRes = R.drawable.thanks_avatar_103,
        rank = 0
    ),
    Sponsor(
        name = "爱发电用户_aqCn",
        message = "",
        avatarRes = R.drawable.thanks_avatar_104,
        rank = 0
    ),
    Sponsor(
        name = "爱发电用户_wgq6",
        message = "",
        avatarRes = R.drawable.thanks_avatar_105,
        rank = 0
    ),
    Sponsor(
        name = "爱发电用户_bd821",
        message = "",
        avatarRes = R.drawable.thanks_avatar_106,
        rank = 0
    ),
    Sponsor(
        name = "反乌托邦",
        message = "",
        avatarRes = R.drawable.thanks_avatar_107,
        rank = 0
    ),
    Sponsor(
        name = "久",
        message = "",
        avatarRes = R.drawable.thanks_avatar_108,
        rank = 0
    ),
    Sponsor(
        name = "w.",
        message = "",
        avatarRes = R.drawable.thanks_avatar_109,
        rank = 0
    ),
    Sponsor(
        name = "微寐",
        message = "",
        avatarRes = R.drawable.thanks_avatar_110,
        rank = 0
    ),
    Sponsor(
        name = "落",
        message = "",
        avatarRes = R.drawable.thanks_avatar_111,
        rank = 0
    ),
    Sponsor(
        name = "烤鱼拌饭1",
        message = "",
        avatarRes = R.drawable.thanks_avatar_112,
        rank = 0
    ),
    Sponsor(
        name = "Kira",
        message = "",
        avatarRes = R.drawable.thanks_avatar_113,
        rank = 0
    ),
    Sponsor(
        name = "林久",
        message = "",
        avatarRes = R.drawable.thanks_avatar_114,
        rank = 0
    ),
    Sponsor(
        name = "Aurora",
        message = "",
        avatarRes = R.drawable.thanks_avatar_115,
        rank = 0
    ),
    Sponsor(
        name = "我是这口气的爸爸",
        message = "",
        avatarRes = R.drawable.thanks_avatar_116,
        rank = 0
    ),
    Sponsor(
        name = "爱发电用户_dda8a",
        message = "",
        avatarRes = R.drawable.thanks_avatar_117,
        rank = 0
    ),
    Sponsor(
        name = "爱发电用户_fepV",
        message = "",
        avatarRes = R.drawable.thanks_avatar_118,
        rank = 0
    ),
    Sponsor(
        name = "爱发电用户_a263e",
        message = "",
        avatarRes = R.drawable.thanks_avatar_119,
        rank = 0
    ),
    Sponsor(
        name = "爱发电用户_MeF9",
        message = "",
        avatarRes = R.drawable.thanks_avatar_120,
        rank = 0
    ),
    Sponsor(
        name = "泽",
        message = "",
        avatarRes = R.drawable.thanks_avatar_121,
        rank = 0
    ),
    Sponsor(
        name = "熊大",
        message = "",
        avatarRes = R.drawable.thanks_avatar_122,
        rank = 0
    ),
    Sponsor(
        name = "初离",
        message = "",
        avatarRes = R.drawable.thanks_avatar_123,
        rank = 0
    ),
    Sponsor(
        name = ".",
        message = "",
        avatarRes = R.drawable.thanks_avatar_124,
        rank = 0
    ),
    Sponsor(
        name = "爱发电用户_2853a",
        message = "",
        avatarRes = R.drawable.thanks_avatar_125,
        rank = 0
    ),
    Sponsor(
        name = "爱发电用户_142a5",
        message = "",
        avatarRes = R.drawable.thanks_avatar_126,
        rank = 0
    ),
    Sponsor(
        name = "爱发电用户_hadJ",
        message = "",
        avatarRes = R.drawable.thanks_avatar_127,
        rank = 0
    ),
    Sponsor(
        name = "liuying",
        message = "",
        avatarRes = R.drawable.thanks_avatar_128,
        rank = 0
    ),
    Sponsor(
        name = "爱发电用户_d1a16",
        message = "",
        avatarRes = R.drawable.thanks_avatar_129,
        rank = 0
    ),
    Sponsor(
        name = "donk",
        message = "",
        avatarRes = R.drawable.thanks_avatar_130,
        rank = 0
    ),
    Sponsor(
        name = "爱发电用户_Mh5B",
        message = "",
        avatarRes = R.drawable.thanks_avatar_131,
        rank = 0
    ),
    Sponsor(
        name = "爱发电用户_v37V",
        message = "",
        avatarRes = R.drawable.thanks_avatar_132,
        rank = 0
    ),
    Sponsor(
        name = "沉",
        message = "",
        avatarRes = R.drawable.thanks_avatar_133,
        rank = 0
    ),
    Sponsor(
        name = "一",
        message = "",
        avatarRes = R.drawable.thanks_avatar_134,
        rank = 0
    ),
    Sponsor(
        name = "永远读着对白",
        message = "",
        avatarRes = R.drawable.thanks_avatar_135,
        rank = 0
    ),
    Sponsor(
        name = "爱发电用户_BTdp",
        message = "",
        avatarRes = R.drawable.thanks_avatar_136,
        rank = 0
    ),
    Sponsor(
        name = "听木",
        message = "",
        avatarRes = R.drawable.thanks_avatar_137,
        rank = 0
    ),
    Sponsor(
        name = "wx",
        message = "",
        avatarRes = R.drawable.thanks_avatar_138,
        rank = 0
    ),
    Sponsor(
        name = "哈哈",
        message = "",
        avatarRes = R.drawable.thanks_avatar_139,
        rank = 0
    ),
    Sponsor(
        name = "爱发电用户_gtYM",
        message = "",
        avatarRes = R.drawable.thanks_avatar_140,
        rank = 0
    ),
    Sponsor(
        name = "爱发电用户_uDNB",
        message = "",
        avatarRes = R.drawable.thanks_avatar_141,
        rank = 0
    ),
    Sponsor(
        name = "用户F",
        message = "",
        avatarRes = R.drawable.thanks_avatar_142,
        rank = 0
    ),
    Sponsor(
        name = "晚枫",
        message = "",
        avatarRes = R.drawable.thanks_avatar_143,
        rank = 0
    ),
    Sponsor(
        name = "小天",
        message = "",
        avatarRes = R.drawable.thanks_avatar_144,
        rank = 0
    ),
    Sponsor(
        name = "1",
        message = "",
        avatarRes = R.drawable.thanks_avatar_145,
        rank = 0
    ),
    Sponsor(
        name = "樊一铭",
        message = "",
        avatarRes = R.drawable.thanks_avatar_146,
        rank = 0
    ),
    Sponsor(
        name = "zz",
        message = "",
        avatarRes = R.drawable.thanks_avatar_147,
        rank = 0
    ),
    Sponsor(
        name = "啊渊",
        message = "",
        avatarRes = R.drawable.thanks_avatar_148,
        rank = 0
    ),
    Sponsor(
        name = "爱发电用户_9tQk",
        message = "",
        avatarRes = R.drawable.thanks_avatar_149,
        rank = 0
    ),
    Sponsor(
        name = "煊",
        message = "",
        avatarRes = R.drawable.thanks_avatar_150,
        rank = 0
    ),
    Sponsor(
        name = "起舞",
        message = "",
        avatarRes = R.drawable.thanks_avatar_151,
        rank = 0
    ),
    Sponsor(
        name = "=_=",
        message = "",
        avatarRes = R.drawable.thanks_avatar_152,
        rank = 0
    ),
    Sponsor(
        name = "爱发电用户_rxuf",
        message = "",
        avatarRes = R.drawable.thanks_avatar_153,
        rank = 0
    ),
    Sponsor(
        name = "爱发电用户_WHXQ",
        message = "",
        avatarRes = R.drawable.thanks_avatar_154,
        rank = 0
    ),
    Sponsor(
        name = "楪 いのり",
        message = "",
        avatarRes = R.drawable.thanks_avatar_155,
        rank = 0
    ),
    Sponsor(
        name = "衔花",
        message = "",
        avatarRes = R.drawable.thanks_avatar_156,
        rank = 0
    ),
    Sponsor(
        name = "爱发电用户_e9b1d",
        message = "",
        avatarRes = R.drawable.thanks_avatar_157,
        rank = 0
    ),
    Sponsor(
        name = "爱发电用户_WJnc",
        message = "",
        avatarRes = R.drawable.thanks_avatar_158,
        rank = 0
    ),
    Sponsor(
        name = "爱发电用户_846ca",
        message = "",
        avatarRes = R.drawable.thanks_avatar_159,
        rank = 0
    ),
    Sponsor(
        name = "17",
        message = "",
        avatarRes = R.drawable.thanks_avatar_160,
        rank = 0
    ),
    Sponsor(
        name = "爱发电用户_JDNt",
        message = "",
        avatarRes = R.drawable.thanks_avatar_161,
        rank = 0
    ),
    Sponsor(
        name = "吹吹牛",
        message = "",
        avatarRes = R.drawable.thanks_avatar_162,
        rank = 0
    ),
    Sponsor(
        name = "浅唱小恋曲",
        message = "",
        avatarRes = R.drawable.thanks_avatar_163,
        rank = 0
    ),
    Sponsor(
        name = "Jive",
        message = "",
        avatarRes = R.drawable.thanks_avatar_164,
        rank = 0
    ),
    Sponsor(
        name = "爱发电用户_WBMj",
        message = "",
        avatarRes = R.drawable.thanks_avatar_165,
        rank = 0
    ),
    Sponsor(
        name = "rainy10",
        message = "",
        avatarRes = R.drawable.thanks_avatar_166,
        rank = 0
    ),
    Sponsor(
        name = "阿生",
        message = "",
        avatarRes = R.drawable.thanks_avatar_167,
        rank = 0
    ),
    Sponsor(
        name = "爱发电用户_sP73",
        message = "",
        avatarRes = R.drawable.thanks_avatar_168,
        rank = 0
    ),
    Sponsor(
        name = "爱发电用户_vc5e",
        message = "",
        avatarRes = R.drawable.thanks_avatar_169,
        rank = 0
    ),
    Sponsor(
        name = "爱发电用户_b00f6",
        message = "",
        avatarRes = R.drawable.thanks_avatar_170,
        rank = 0
    ),
    Sponsor(
        name = "爱发电用户_761ca",
        message = "",
        avatarRes = R.drawable.thanks_avatar_171,
        rank = 0
    ),
    Sponsor(
        name = "空落落",
        message = "",
        avatarRes = R.drawable.thanks_avatar_172,
        rank = 0
    ),
    Sponsor(
        name = "爱发电用户_b9615",
        message = "",
        avatarRes = R.drawable.thanks_avatar_173,
        rank = 0
    ),
    Sponsor(
        name = "木又",
        message = "",
        avatarRes = R.drawable.thanks_avatar_174,
        rank = 0
    ),
    Sponsor(
        name = "爱发电用户_9Vvq",
        message = "",
        avatarRes = R.drawable.thanks_avatar_175,
        rank = 0
    ),
    Sponsor(
        name = "小南瓜",
        message = "",
        avatarRes = R.drawable.thanks_avatar_176,
        rank = 0
    ),
    Sponsor(
        name = "爱发电用户_TxFJ",
        message = "",
        avatarRes = R.drawable.thanks_avatar_177,
        rank = 0
    ),
    Sponsor(
        name = "爱发电用户_WXEc",
        message = "",
        avatarRes = R.drawable.thanks_avatar_178,
        rank = 0
    ),
    Sponsor(
        name = "不想上课_",
        message = "",
        avatarRes = R.drawable.thanks_avatar_179,
        rank = 0
    ),
    Sponsor(
        name = "kn",
        message = "",
        avatarRes = R.drawable.thanks_avatar_180,
        rank = 0
    ),
)


@Composable
fun ThanksScreen(
    onNavigateBack: () -> Unit,
    onViewFullList: () -> Unit
) {
    var isVisible by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) { isVisible = true }

    Box(
        modifier = Modifier.fillMaxSize()
    ) {
        // 全屏背景图：等比例铺满屏幕，延伸至状态栏下方，消除左右空白
        Image(
            painter = painterResource(id = R.drawable.thanks_bg),
            contentDescription = null,
            modifier = Modifier.fillMaxSize(),
            contentScale = ContentScale.Crop
        )

        // 名单列表：放置在画面中下方（用户圈定的空白区域），避免与顶部返回按钮重叠
        BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
            val listTopOffset = maxHeight * 0.45f
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(top = listTopOffset),
                contentPadding = PaddingValues(
                    start = 16.dp,
                    end = 16.dp,
                    bottom = 32.dp
                ),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                itemsIndexed(
                    sponsors.take(6),
                    key = { _, item -> item.name }
                ) { index, sponsor ->
                    SponsorItem(
                        sponsor = sponsor,
                        index = index,
                        isVisible = isVisible
                    )
                }

                if (sponsors.size > 6) {
                    item { ViewFullListButton(onClick = onViewFullList) }
                }

                item { FooterNote() }
            }
        }

        // 左上角圆形返回按钮：玻璃质感 + 粉色主题，明显区别于其他页面的长条标题栏
        Box(
            modifier = Modifier
                .align(Alignment.TopStart)
                .windowInsetsPadding(WindowInsets.statusBars)
                .padding(start = 16.dp, top = 12.dp)
                .size(42.dp)
                .shadow(
                    elevation = 6.dp,
                    shape = CircleShape,
                    ambientColor = Color(0xFFF4A6B5),
                    spotColor = Color(0xFFF4A6B5)
                )
                .background(
                    color = Color.White.copy(alpha = 0.88f),
                    shape = CircleShape
                ),
            contentAlignment = Alignment.Center
        ) {
            IconButton(
                onClick = onNavigateBack,
                modifier = Modifier.fillMaxSize()
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = stringResource(R.string.back),
                    tint = Color(0xFFE85D75),
                    modifier = Modifier.size(22.dp)
                )
            }
        }
    }
}

@Composable
fun ThanksFullListScreen(
    onNavigateBack: () -> Unit
) {
    var isVisible by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) { isVisible = true }

    Box(
        modifier = Modifier.fillMaxSize()
    ) {
        // 全屏背景图
        Image(
            painter = painterResource(id = R.drawable.thanks_bg),
            contentDescription = null,
            modifier = Modifier.fillMaxSize(),
            contentScale = ContentScale.Crop
        )

        // 完整名单
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .windowInsetsPadding(WindowInsets.statusBars)
                .padding(top = 72.dp),
            contentPadding = PaddingValues(
                start = 16.dp,
                end = 16.dp,
                bottom = 32.dp
            ),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            itemsIndexed(
                sponsors,
                key = { _, item -> item.name }
            ) { index, sponsor ->
                SponsorItem(
                    sponsor = sponsor,
                    index = index,
                    isVisible = isVisible
                )
            }

            item { FooterNote() }
        }

        // 左上角圆形返回按钮
        Box(
            modifier = Modifier
                .align(Alignment.TopStart)
                .windowInsetsPadding(WindowInsets.statusBars)
                .padding(start = 16.dp, top = 12.dp)
                .size(42.dp)
                .shadow(
                    elevation = 6.dp,
                    shape = CircleShape,
                    ambientColor = Color(0xFFF4A6B5),
                    spotColor = Color(0xFFF4A6B5)
                )
                .background(
                    color = Color.White.copy(alpha = 0.88f),
                    shape = CircleShape
                ),
            contentAlignment = Alignment.Center
        ) {
            IconButton(
                onClick = onNavigateBack,
                modifier = Modifier.fillMaxSize()
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = stringResource(R.string.back),
                    tint = Color(0xFFE85D75),
                    modifier = Modifier.size(22.dp)
                )
            }
        }
    }
}

@Composable
private fun SponsorItem(
    sponsor: Sponsor,
    index: Int,
    isVisible: Boolean
) {
    val animated by animateFloatAsState(
        targetValue = if (isVisible) 1f else 0f,
        animationSpec = tween(400, delayMillis = 200 + index * 80),
        label = "sponsor_item_$index"
    )

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp)
            .graphicsLayer { alpha = animated }
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .shadow(
                    elevation = 4.dp,
                    shape = RoundedCornerShape(20.dp),
                    ambientColor = Color(0xFF8B5CF6).copy(alpha = 0.3f),
                    spotColor = Color(0xFF8B5CF6).copy(alpha = 0.3f)
                ),
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // 头像
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .clip(CircleShape)
                        .background(sponsor.tint.copy(alpha = 0.1f)),
                    contentAlignment = Alignment.Center
                ) {
                    if (sponsor.avatarRes != null) {
                        Image(
                            painter = painterResource(id = sponsor.avatarRes),
                            contentDescription = sponsor.name,
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.Crop
                        )
                    } else {
                        Text(
                            text = sponsor.name.firstOrNull()?.toString() ?: "?",
                            style = MaterialTheme.typography.titleMedium.copy(
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 18.sp
                            ),
                            color = sponsor.tint
                        )
                    }
                }

                Spacer(modifier = Modifier.width(14.dp))

                // 名字 + 留言
                Column(modifier = Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = sponsor.name,
                            style = MaterialTheme.typography.titleMedium.copy(
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 15.sp
                            ),
                            color = Color.Black,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Icon(
                            imageVector = Icons.Filled.Favorite,
                            contentDescription = null,
                            modifier = Modifier.size(12.dp),
                            tint = Color(0xFFE85D75).copy(alpha = 0.8f)
                        )
                    }
                    if (sponsor.message.isNotBlank()) {
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = sponsor.message,
                            style = MaterialTheme.typography.bodySmall.copy(
                                fontSize = 13.sp,
                                lineHeight = 18.sp
                            ),
                            color = Color.Black.copy(alpha = 0.6f),
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ViewFullListButton(
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp)
            .clip(RoundedCornerShape(20.dp))
            .background(Color.White.copy(alpha = 0.2f))
            .clickable(onClick = onClick)
            .padding(vertical = 12.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = "点击查看全部 →",
            style = MaterialTheme.typography.bodyMedium.copy(
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium
            ),
            color = Color.Black.copy(alpha = 0.9f)
        )
    }
}

@Composable
private fun FooterNote() {
    Text(
        text = "排名不分先后    名单持续更新",
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 24.dp),
        style = MaterialTheme.typography.bodySmall.copy(
            fontSize = 12.sp,
            textAlign = TextAlign.Center
        ),
        color = Color.Black.copy(alpha = 0.5f)
    )
}
