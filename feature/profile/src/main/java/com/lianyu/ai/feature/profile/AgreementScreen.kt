package com.lianyu.ai.feature.profile

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Security
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import com.lianyu.ai.feature.profile.R
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp


internal val sec1Title = "一、关于本软件"
internal val sec1Body = "恋语(以下简称本软件)是一款基于人工智能技术的虚拟伴侣应用。本软件仅供年满18周岁的成年用户用于个人娱乐、情感陪伴及日常交流。本软件不构成任何人际关系、医疗建议、心理咨询或法律服务的替代。"

internal val sec2Title = "二、用户资格与设备绑定"
internal val sec2Body = """
1. 您必须年满18周岁方可使用本软件。未满18周岁的未成年人严禁使用本软件。
2. 本软件会采集您的设备唯一标识符用于授权验证，每台设备仅允许一个有效授权。
3. 如发现用户未满18周岁或存在违规传播行为，本软件有权通过设备标识追查并永久封禁。
4. 根据中国法律法规及监管要求，AI生成内容服务提供者有义务落实用户实名制和内容安全管理。
""".trim()

internal val sec3Title = "三、AI内容免责声明"
internal val sec3Body = """
1. 本软件所有AI生成的内容均由第三方AI大模型(包括但不限于OpenAI、Anthropic、Google Gemini等)自动生成，开发者不对AI生成内容的准确性、完整性、合法性、适切性作任何明示或默示的保证。
2. AI的回复不代表开发者的观点、立场或建议。用户应自行判断AI回复的合理性，并独立承担依据AI回复做出任何决定或行为所产生的全部后果。
3. 本软件已部署内容安全过滤机制，但由于AI技术本身的局限性，无法保证100%拦截所有不当内容。如您发现任何不当内容，请立即停止使用并联系开发者。
""".trim()

internal val sec4Title = "四、用户行为规范"
internal val sec4Body = """
1. 您不得利用本软件从事任何违法违规活动，包括但不限于：传播色情、淫秽、暴力、恐怖主义、赌博、毒品、诈骗等信息；侵犯他人知识产权、隐私权、名誉权等合法权益；干扰、破坏本软件的正常运行。
2. 您不得诱导AI生成违反法律法规或社会主义核心价值观的内容。
3. 您不得将本软件用于任何商业目的，包括但不限于转售、出租、出借或以此牟利。
4. 您不得对本软件进行反向工程、反编译、反汇编或试图提取源代码。
""".trim()

internal val sec5Title = "五、隐私与数据保护"
internal val sec5Body = """
1. 本软件采集的设备标识符(ANDROID_ID)仅用于设备授权验证和违规追查，不会用于任何商业营销或出售给第三方。
2. 您的聊天记录存储在您的设备本地，开发者无法查看、获取或使用您的聊天内容。
3. 您发送给AI的消息会通过加密网络传输至第三方AI服务商进行处理，请勿发送任何个人敏感信息(如身份证号、银行卡号、密码等)。
4. 本软件的GitHub更新检测功能仅用于查询版本信息，不会上传您的任何个人数据。
""".trim()

internal val sec6Title = "六、服务可用性"
internal val sec6Body = """
1. 本软件按「现状」提供，不保证服务无中断、无错误或完全满足您的需求。
2. 第三方AI服务提供商的可用性、服务条款变更或关停可能导致本软件部分或全部功能无法使用，开发者不承担由此产生的任何责任。
3. 本软件会通过GitHub自动检测版本更新，但更新内容由开发者自行决定，不构成任何持续维护的承诺。
""".trim()

internal val sec7Title = "七、知识产权"
internal val sec7Body = "本软件(包括但不限于代码、界面设计、图标、文案)的著作权及其他知识产权归开发者所有。未经开发者书面许可，任何人不得复制、修改、发布、出售本软件或其任何部分。用户通过本软件与AI互动所产生的对话内容，其权利归属遵循相关AI服务商的使用条款。"

internal val sec8Title = "八、责任限制"
internal val sec8Body = "在适用法律允许的最大范围内，开发者不对因使用或无法使用本软件而产生的任何直接、间接、附带、特殊、惩罚性或后果性损害(包括但不限于数据丢失、精神损害、经济损失等)承担责任，即使开发者已被告知此类损害的可能性。若您不同意上述责任限制，请勿使用本软件。"

internal val sec9Title = "九、协议变更与终止"
internal val sec9Body = """
1. 开发者保留随时修改本协议的权利，修改后的协议将在软件更新时生效。继续使用本软件即视为接受修改后的协议。
2. 如您违反本协议任何条款，开发者有权立即终止您使用本软件的权利。
3. 您可以随时卸载本软件以终止使用。
""".trim()

internal val sec10Title = "十、法律适用与争议解决"
internal val sec10Body = "本协议的订立、效力、解释、履行及争议解决均适用中华人民共和国法律。因本协议引起的或与本协议有关的任何争议，双方应首先友好协商解决；协商不成的，任何一方均有权向开发者所在地有管辖权的人民法院提起诉讼。"

internal val sec11Title = "十一、特别声明"
internal val sec11Body = "本软件为个人开发者的学习与分享项目，不构成商业运营。软件中所有虚拟角色的形象、性格、设定均为虚构，仅供娱乐，与现实人物、事件无任何关联。使用本软件即表示您已充分理解并同意：开发者对您使用本软件所产生的任何后果不承担任何责任。"

@Composable
fun AgreementScreen(
    onAgree: () -> Unit,
    onDisagree: () -> Unit
) {
    var agreedToTerms by remember { mutableStateOf(false) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        Color(0xFFF5F5F5),
                        Color(0xFFEEEEEE),
                        Color(0xFFF5F5F5)
                    )
                )
            )
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp, vertical = 32.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(
                modifier = Modifier
                    .size(64.dp)
                    .clip(CircleShape)
                    .background(
                        Brush.radialGradient(
                            colors = listOf(MaterialTheme.colorScheme.primary.copy(alpha = 0.15f), MaterialTheme.colorScheme.primary.copy(alpha = 0.08f))
                        )
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Filled.Security,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.7f),
                    modifier = Modifier.size(32.dp)
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            Text(
                text = stringResource(R.string.agreement_title),
                style = MaterialTheme.typography.titleLarge.copy(
                    fontWeight = FontWeight.Bold,
                    fontSize = 20.sp
                ),
                color = MaterialTheme.colorScheme.onSurface
            )

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = stringResource(R.string.agreement_read),
                style = MaterialTheme.typography.bodySmall.copy(fontSize = 13.sp),
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
            )

            Spacer(modifier = Modifier.height(20.dp))

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .background(Color.White)
                    .padding(16.dp)
            ) {
                Column {
                    AgreementSection(sec1Title, sec1Body)
                    AgreementSection(sec2Title, sec2Body)
                    AgreementSection(sec3Title, sec3Body)
                    AgreementSection(sec4Title, sec4Body)
                    AgreementSection(sec5Title, sec5Body)
                    AgreementSection(sec6Title, sec6Body)
                    AgreementSection(sec7Title, sec7Body)
                    AgreementSection(sec8Title, sec8Body)
                    AgreementSection(sec9Title, sec9Body)
                    AgreementSection(sec10Title, sec10Body)
                    AgreementSection(sec11Title, sec11Body)
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Checkbox(
                    checked = agreedToTerms,
                    onCheckedChange = { agreedToTerms = it },
                    colors = CheckboxDefaults.colors(
                        checkedColor = MaterialTheme.colorScheme.primary,
                        uncheckedColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                    )
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = stringResource(R.string.agreement_age),
                    style = MaterialTheme.typography.bodyMedium.copy(
                        fontWeight = if (agreedToTerms) FontWeight.Medium else FontWeight.Normal,
                        fontSize = 14.sp
                    ),
                    color = if (agreedToTerms) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                )
            }

            Spacer(modifier = Modifier.height(24.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Button(
                    onClick = onDisagree,
                    modifier = Modifier.weight(1f).height(48.dp),
                    shape = RoundedCornerShape(24.dp),
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(0.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color.Transparent,
                        contentColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                    )
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp)
                            .clip(RoundedCornerShape(24.dp))
                            .background(Color.Transparent)
                            .padding(1.dp)
                            .clip(RoundedCornerShape(23.dp))
                            .background(Color.White),
                        contentAlignment = Alignment.Center
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Center
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Close,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                stringResource(R.string.agreement_disagree),
                                style = MaterialTheme.typography.titleSmall.copy(
                                    fontWeight = FontWeight.SemiBold,
                                    fontSize = 14.sp
                                ),
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                            )
                        }
                    }
                }

                Button(
                    onClick = { if (agreedToTerms) onAgree() },
                    modifier = Modifier.weight(1f).height(48.dp),
                    shape = RoundedCornerShape(24.dp),
                    enabled = agreedToTerms,
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(0.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (agreedToTerms)
                            MaterialTheme.colorScheme.primary.copy(alpha = 0.9f)
                        else
                            MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f),
                        contentColor = Color.White,
                        disabledContainerColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f),
                        disabledContentColor = Color.White
                    )
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp)
                            .clip(RoundedCornerShape(24.dp))
                            .background(Color.Transparent)
                            .padding(1.dp)
                            .clip(RoundedCornerShape(23.dp))
                            .background(
                                if (agreedToTerms)
                                    Brush.horizontalGradient(
                                        colors = listOf(MaterialTheme.colorScheme.primary.copy(alpha = 0.9f), MaterialTheme.colorScheme.primary.copy(alpha = 0.85f))
                                    )
                                else
                                    Brush.horizontalGradient(
                                        colors = listOf(MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f), MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.2f))
                                    )
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Center
                        ) {
                            Icon(
                                imageVector = Icons.Filled.CheckCircle,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                stringResource(R.string.agreement_agree),
                                style = MaterialTheme.typography.titleSmall.copy(
                                    fontWeight = FontWeight.SemiBold,
                                    fontSize = 14.sp
                                )
                            )
                        }
                    }
                }
            }
        }

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 8.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = stringResource(R.string.version_info),
                style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
            )
        }
    }
}

@Composable
internal fun AgreementSection(title: String, content: String) {
    Column(modifier = Modifier.padding(bottom = 14.dp)) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleSmall.copy(
                fontWeight = FontWeight.SemiBold,
                fontSize = 13.sp
            ),
            color = MaterialTheme.colorScheme.onSurface
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = content,
            style = MaterialTheme.typography.bodySmall.copy(
                fontSize = 11.sp,
                lineHeight = 17.sp
            ),
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
        )
    }
}
