package com.lianyu.ai.feature.profile

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.lianyu.ai.feature.profile.R

data class TeamMember(
    val name: String,
    val role: String,
    val description: String,
    val color: Color,
    val avatarRes: Int? = null
)

val teamMembers = listOf(
    TeamMember(
        name = "\u6797\u6893\u6DB5",
        role = "\u521B\u5EFA\u8005 & \u5168\u6808",
        description = "\u8D1F\u8D23\u6574\u4F53\u67B6\u6784\u8BBE\u8BA1\u3001Android\u5BA2\u6237\u7AEF\u5F00\u53D1\u3001AI\u63A5\u53E3\u96C6\u6210\u4E0E\u9879\u76EE\u7BA1\u7406",
        color = Color(0xFF07C160),
        avatarRes = R.drawable.team_linruoxo
    ),
    TeamMember(
        name = "\u4E0B\u5317\u6CFD\u4F20\u5947",
        role = "\u6838\u5FC3\u534F\u4F5C\u8005",
        description = "\u4EA7\u54C1\u89C4\u5212\u3001UI\u8BBE\u8BA1\u3001\u6D4B\u8BD5\u4E0E\u793E\u533A\u8FD0\u8425",
        color = Color(0xFFF4A6B5),
        avatarRes = R.drawable.team_xiabeize
    ),
    TeamMember(
        name = "\u7948\u613F\u5C0F\u82CF",
        role = "\u5B89\u5168\u52A0\u5BC6 \u00B7 \u540E\u7AEF",
        description = "\u8D1F\u8D23\u5E94\u7528\u5B89\u5168\u52A0\u56FA\u3001JNI/NDK\u539F\u751F\u5B89\u5168\u3001\u540E\u7AEF\u670D\u52A1\u652F\u6301",
        color = Color(0xFF07C160),
        avatarRes = R.drawable.team_qiyuanxiaosu
    ),
    TeamMember(
        name = "\u7740\u9B54",
        role = "\u604B\u7231\u6559\u7A0B",
        description = "\u4E3A\u865A\u62DF\u604B\u4EBA\u63D0\u4F9B\u60C5\u611F\u4EA4\u6D41\u6307\u5BFC\u4E0E\u604B\u7231\u6559\u7A0B\u5185\u5BB9",
        color = Color(0xFFE85D75),
        avatarRes = R.drawable.team_zhaomo
    ),
    TeamMember(
        name = "\u9E22\u7940",
        role = "\u591A\u6A21\u6001",
        description = "\u8D1F\u8D23\u56FE\u7247\u751F\u6210\u3001\u8BED\u97F3\u5408\u6210\u7B49\u591A\u6A21\u6001AI\u529F\u80FD\u7684\u96C6\u6210\u4E0E\u4F18\u5316",
        color = Color(0xFF9B59B6),
        avatarRes = R.drawable.team_yuansi
    ),
    TeamMember(
        name = "\u6545\u6E0A",
        role = "\u540E\u7AEF\u652F\u6301",
        description = "\u8D1F\u8D23\u670D\u52A1\u5668\u7EF4\u62A4\u3001\u540E\u7AEF\u67B6\u6784\u8BBE\u8BA1\u4E0E\u90E8\u7F72\u652F\u6301",
        color = Color(0xFF3498DB),
        avatarRes = R.drawable.team_guyuan
    ),
    TeamMember(
        name = "\u5C06\u548C\u5E73",
        role = "\u865A\u62DF\u4EBA\u7269\u4EA4\u4E92",
        description = "\u8D1F\u8D23\u865A\u62DF\u4EBA\u7269\u4EA4\u4E92\u6A21\u5757\u5F00\u53D1\u4E0E\u4F53\u9A8C\u4F18\u5316",
        color = Color(0xFFE67E22),
        avatarRes = R.drawable.team_jiangheping
    )
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TeamScreen(
    onNavigateBack: () -> Unit
) {
    val colorScheme = MaterialTheme.colorScheme

    Scaffold(
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .windowInsetsPadding(WindowInsets.statusBars)
                    .padding(horizontal = 16.dp, vertical = 8.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(colorScheme.surfaceVariant, shape = RoundedCornerShape(20.dp))
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    IconButton(
                        onClick = onNavigateBack,
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.back),
                            tint = colorScheme.onSurface,
                            modifier = Modifier.size(20.dp)
                        )
                    }

                    Text(
                        text = stringResource(R.string.dev_team_title),
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 15.sp
                        ),
                        color = colorScheme.onSurface
                    )

                    Spacer(modifier = Modifier.width(32.dp))
                }
            }
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(colorScheme.background)
                .padding(top = paddingValues.calculateTopPadding())
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(modifier = Modifier.height(24.dp))

            Text(
                text = stringResource(R.string.dev_team_subtitle),
                style = MaterialTheme.typography.bodyMedium.copy(
                    fontSize = 14.sp
                ),
                color = colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 24.dp)
            )

            Spacer(modifier = Modifier.height(24.dp))

            teamMembers.forEachIndexed { index, member ->
                TeamMemberCard(
                    member = member,
                    delayIndex = index
                )
                Spacer(modifier = Modifier.height(12.dp))
            }

            Spacer(modifier = Modifier.height(16.dp))

            Text(
                text = stringResource(R.string.dev_team_bottom),
                style = MaterialTheme.typography.bodySmall.copy(
                    fontSize = 12.sp,
                    textAlign = TextAlign.Center
                ),
                color = colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                modifier = Modifier.padding(horizontal = 24.dp)
            )

            Spacer(modifier = Modifier.height(32.dp))
        }
    }
}

@Composable
fun TeamMemberCard(
    member: TeamMember,
    delayIndex: Int
) {
    val colorScheme = MaterialTheme.colorScheme
    Column(
        modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .background(colorScheme.surfaceVariant, shape = RoundedCornerShape(16.dp))
                .padding(20.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(56.dp)
                    .clip(CircleShape)
                    .background(
                        Brush.radialGradient(
                            colors = listOf(
                                member.color.copy(alpha = 0.3f),
                                member.color.copy(alpha = 0.15f)
                            )
                        )
                    ),
                contentAlignment = Alignment.Center
            ) {
                if (member.avatarRes != null) {
                    Image(
                        painter = painterResource(id = member.avatarRes),
                        contentDescription = member.name,
                        modifier = Modifier
                            .size(56.dp)
                            .clip(CircleShape),
                        contentScale = ContentScale.Crop
                    )
                }
            }

            Spacer(modifier = Modifier.width(16.dp))

            Column {
                Text(
                    text = member.name,
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 16.sp
                    ),
                    color = colorScheme.onSurface
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = member.role,
                    style = MaterialTheme.typography.bodySmall.copy(
                        fontSize = 13.sp
                    ),
                    color = member.color
                )
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        Text(
            text = member.description,
            style = MaterialTheme.typography.bodyMedium.copy(
                fontSize = 13.sp,
                lineHeight = 20.sp
            ),
            color = colorScheme.onSurfaceVariant
        )
    }
}
