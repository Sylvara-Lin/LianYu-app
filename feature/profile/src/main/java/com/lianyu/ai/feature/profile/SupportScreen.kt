package com.lianyu.ai.feature.profile

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.viewmodel.compose.viewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SupportScreen(
    onNavigateBack: () -> Unit
) {
    val colorScheme = MaterialTheme.colorScheme

    val context = LocalContext.current
    val sponsorUrl = stringResource(R.string.support_sponsor_url)

    var showAgreement by remember { mutableStateOf(false) }
    var ageAgreed by remember { mutableStateOf(false) }

    fun openSponsorSite() {
        try {
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(sponsorUrl))
            context.startActivity(intent)
        } catch (_: Exception) { }
    }

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
                    IconButton(onClick = onNavigateBack, modifier = Modifier.size(32.dp)) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "返回",
                            tint = colorScheme.onSurface,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    Text(
                        text = stringResource(R.string.support_us_title),
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontWeight = FontWeight.SemiBold, fontSize = 15.sp
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
            Spacer(modifier = Modifier.height(40.dp))
            Box(
                modifier = Modifier.size(88.dp).clip(CircleShape).background(
                    Brush.radialGradient(
                        colors = listOf(
                            Color(0xFFE8547E).copy(alpha = 0.35f),
                            Color(0xFFF4A6B5).copy(alpha = 0.18f)
                        )
                    )
                ),
                contentAlignment = Alignment.Center
            ) { Text(text = "\uD83D\uDC95", fontSize = 42.sp) }
            Spacer(modifier = Modifier.height(20.dp))
            Text(
                text = stringResource(R.string.support_us_thanks),
                style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold, fontSize = 24.sp),
                color = colorScheme.onSurface
            )
            Spacer(modifier = Modifier.height(10.dp))
            Text(
                text = stringResource(R.string.support_us_desc2),
                style = MaterialTheme.typography.bodyMedium.copy(fontSize = 14.sp, lineHeight = 22.sp, textAlign = TextAlign.Center),
                color = colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 32.dp)
            )
            Spacer(modifier = Modifier.height(36.dp))
            Column(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp).clip(RoundedCornerShape(20.dp)).background(colorScheme.surfaceVariant).padding(24.dp)
            ) {
                Text(
                    text = stringResource(R.string.support_ways),
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold, fontSize = 17.sp),
                    color = colorScheme.onSurface
                )
                Spacer(modifier = Modifier.height(20.dp))
                Row(
                    modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(colorScheme.surface).clickable { ageAgreed = false; showAgreement = true }.padding(18.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(modifier = Modifier.size(48.dp).clip(RoundedCornerShape(14.dp)).background(Color(0xFFE8547E).copy(alpha = 0.12f)), contentAlignment = Alignment.Center) {
                        Text(text = "\uD83D\uDC95", fontSize = 26.sp)
                    }
                    Spacer(modifier = Modifier.width(16.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(text = stringResource(R.string.support_sponsor_site), style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold, fontSize = 16.sp), color = Color(0xFFE8547E))
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(text = stringResource(R.string.support_sponsor_site_desc), style = MaterialTheme.typography.bodySmall.copy(fontSize = 13.sp), color = Color.Gray)
                    }
                    Icon(imageVector = Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null, tint = Color.Gray.copy(alpha = 0.5f), modifier = Modifier.size(16.dp))
                }
            }
            Spacer(modifier = Modifier.height(24.dp))
            Text(
                text = stringResource(R.string.support_hint),
                style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp, lineHeight = 18.sp, textAlign = TextAlign.Center),
                color = colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                modifier = Modifier.padding(horizontal = 40.dp)
            )
            Spacer(modifier = Modifier.height(40.dp))
        }
    }

    if (showAgreement) {
        Dialog(
            onDismissRequest = { showAgreement = false },
            properties = DialogProperties(usePlatformDefaultWidth = false)
        ) {
            Box(modifier = Modifier.fillMaxWidth(0.92f).fillMaxSize(0.88f).clip(RoundedCornerShape(24.dp)).background(colorScheme.surfaceVariant)) {
                Column(modifier = Modifier.fillMaxSize()) {
                    Row(modifier = Modifier.fillMaxWidth().padding(20.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                        Column {
                            Text(text = stringResource(R.string.support_agreement_title), style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold, fontSize = 20.sp), color = colorScheme.onSurface)
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(text = stringResource(R.string.support_agreement_subtitle), style = MaterialTheme.typography.bodySmall.copy(fontSize = 13.sp), color = colorScheme.onSurfaceVariant)
                        }
                        IconButton(onClick = { showAgreement = false }, modifier = Modifier.size(36.dp)) {
                            Icon(imageVector = Icons.Filled.Close, contentDescription = "关闭", tint = colorScheme.onSurfaceVariant, modifier = Modifier.size(22.dp))
                        }
                    }
                    Column(modifier = Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 20.dp)) {
                        SupportAgreementSection("一、自愿赠与性质", "1. 本软件《恋语》(以下简称本软件)由个人开发者独立开发并免费提供给用户使用，不以盈利为目的，也未设置任何强制性付费功能或付费门槛。\n2. 用户向开发者提供的任何资金支持(包括但不限于通过赞助网站转账、打赏、赞赏等)均属于完全自愿的无偿赠与行为，不构成任何商品购买、服务购买、会员充值、虚拟货币兑换、订阅服务或任何其他形式的对价交易关系。\n3. 赠与行为不会为用户带来任何特权、优先服务、功能解锁、专属内容、身份标识或其他任何形式的回报。用户不得以'我已经支持了'为由要求开发者提供超出一般用户范围的特殊服务或功能。\n4. 赠与金额完全由用户自行决定，开发者不设置任何最低金额限制，也不暗示或建议任何特定金额。每一分钱都是对开发者的鼓励，开发者对此深表感谢。\n5. 开发者保留随时停止接受赠与的权利，无需提前通知。开发者同时保留拒绝接受任何特定用户赠与的权利，包括但不限于怀疑资金来源不明、怀疑用户存在不良动机等情况。\n6. 用户确认其赠与的资金来源合法，并非来自洗钱、诈骗、赌博等任何非法活动。如因资金来源问题导致任何法律纠纷，由用户自行承担全部责任。")
                        SupportAgreementSection("二、未成年人严禁参与", "1. 未满18周岁的未成年人严禁进行任何形式的赠与或打赏。本软件仅供年满18周岁的成年用户使用，未成年人不得使用本软件的任何功能，包括但不限于对话、赠与、浏览等。\n2. 如您未满18周岁，请立即退出本页面并卸载本软件。开发者不欢迎任何未成年人使用本软件，这是出于对未成年人身心健康的保护考虑。\n3. 开发者不接受未成年人的任何资金支持。若经核实发现未成年人赠与，开发者将在30个工作日内依法予以原路退还，退款过程中产生的手续费由开发者承担。\n4. 用户在进行赠与时，即视为已确认自己年满18周岁，并对此声明的真实性承担全部法律责任。如用户虚报年龄进行赠与，事后不得以'我是未成年人'为由要求退款或追究开发者责任。\n5. 开发者有权要求疑似未成年人的用户提供有效身份证明文件以验证年龄。若用户无法提供或拒绝提供，开发者有权拒绝接受其赠与并暂停其使用本软件的权利。\n6. 家长或监护人有责任监督未成年人使用智能设备的情况。若发现未成年人使用本软件或进行赠与，请立即联系开发者处理退款事宜。")
                        SupportAgreementSection("三、资金用途说明", "1. 所有赠与资金将专项用于《恋语》软件的持续开发与维护，具体包括但不限于以下方面：\n　(1)服务器租赁费用：包括云服务器ECS实例、对象存储OSS、CDN加速、数据库RDS等基础设施的月租或年租费用；\n　(2)域名注册与续费费用：包括主域名、子域名及备用域名的年度续费；\n　(3)SSL证书费用：确保数据传输安全的HTTPS加密证书购买与续期；\n　(4)AI接口调用费用：对接各类AI大语言模型(如OpenAI GPT系列、DeepSeek、Claude等)的API调用费用，按Token量计费；\n　(5)第三方服务费用：包括但不限于消息推送服务、异常监控服务、数据分析服务、云函数计算等第三方平台的服务费；\n　(6)开发工具与软件许可费用：包括IDE授权、代码托管平台高级功能、设计工具订阅、测试设备采购等；\n　(7)应用商店相关费用：包括但不限于Google Play开发者账号年费、应用审核相关费用等。\n2. 赠与资金可能部分用于组建开发团队、引入新技术、聘请专业设计师优化用户体验、开展用户调研等方面，以提升软件整体品质。\n3. 开发者承诺所有赠与资金仅用于软件相关支出，不会用于与软件无关的个人消费。开发者将对赠与资金的使用保持最大程度的审慎和节约。\n4. 开发者不对赠与资金的具体使用细节承担披露义务，但欢迎用户通过合理渠道咨询资金使用概况。")
                        SupportAgreementSection("四、退款与撤销", "1. 赠与行为一经完成即不可撤销、不可退款、不可更改金额。用户在确认赠与前应仔细确认金额，并对自己的操作负责。\n2. 用户不得以任何理由要求退还已赠与的资金，包括但不限于以下情形：\n　(1)使用体验不佳或不满意；\n　(2)软件功能不符合个人预期；\n　(3)软件版本更新后功能变更或移除；\n　(4)个人经济状况发生变化；\n　(5)因误操作、手误导致的赠与；\n　(6)第三方原因(如支付平台故障、网络延迟等)；\n　(7)与开发者产生意见分歧或其他主观原因。\n3. 若因技术故障导致重复扣款，用户可提供相关交易记录等证据，开发者在核实后将在3至5个工作日内退还多余部分。\n4. 赠与行为完成后，开发者不会主动联系用户确认或发送收款凭证，请用户自行保留相关交易记录。\n5. 若用户通过非法手段(如盗刷他人银行卡、使用他人支付账户等)进行赠与，开发者有权拒绝退款并配合司法机关调查。")
                        SupportAgreementSection("五、免责声明", "1. 开发者不对本软件的功能完整性、服务持续性、数据安全性、无错误性作任何明示或默示的保证，包括但不限于适销性、特定目的适用性、不侵犯第三方权利等默示保证。\n2. 赠与资金仅用于表达对开发者的支持与鼓励，不换取任何特权、服务承诺或功能保障。用户不得因为进行了赠与而对软件功能产生超出一般用户期望的要求。\n3. 本软件可能因第三方AI服务提供商的政策变更、服务中断、停止运营、接口调整等原因导致部分或全部功能无法使用，这不构成退款理由。开发者不对第三方服务提供商的任何行为承担责任。\n4. 开发者不对因使用或无法使用本软件而产生的任何直接、间接、附带、特殊、惩罚性或后果性损害承担责任，包括但不限于数据丢失、精神损害、经济损失、商誉损失、业务中断等，即使开发者已被告知发生此类损害的可能性。\n5. 用户理解并同意，赠与行为并不保证本软件将持续运营或更新。开发者保留随时停止本软件运营的权利，包括但不限于因个人精力有限、资金不足、政策变化等原因永久或暂时停止维护。\n6. 本软件为开源项目，用户应理解开源软件的固有特性：无担保、无保证、按现状提供。使用本软件的风险由用户自行承担。")
                        SupportAgreementSection("六、知识产权与内容声明", "1. 本软件的所有知识产权(包括但不限于源代码、界面设计、图标、文案、动画、音效、Logo、品牌名称等)归开发者所有，受《中华人民共和国著作权法》《计算机软件保护条例》及相关法律法规保护。任何未经授权的复制、修改、分发、反编译、反向工程等行为均构成侵权。\n2. 赠与行为不转让任何知识产权或授予任何许可权利。用户不得因进行了赠与而主张本软件的任何知识产权或要求开发者开放源代码以外的额外权利。\n3. 本软件中AI生成的内容(包括但不限于AI对话回复、AI生成的文字、AI创作的图像等)其权利归属遵循相关AI服务提供商的使用条款。用户不得将AI生成内容用于违法违规或商业目的，也不得将AI生成内容冒充为人类创作进行传播。\n4. 用户在使用本软件过程中上传的头像、图片、文字等内容，其知识产权仍归用户所有。但用户授予开发者在本软件范围内展示、存储和处理这些内容的非独占、免版税的许可。\n5. 用户不得利用本软件或AI生成功能创作、传播侵犯他人知识产权的内容。如因此产生纠纷，由用户自行承担全部责任。")
                        SupportAgreementSection("七、用户隐私与数据保护", "1. 赞助网站由第三方平台运营，您在赞助过程中提供的个人信息将受该平台的隐私政策保护，请参阅相应平台的隐私条款。\n2. 开发者不会获取、存储或处理您的敏感支付信息(如银行卡号、银行预留手机号、支付密码、CVV安全码等)，所有支付操作均在第三方平台的安全环境下完成，开发者无法接触到这些信息。\n3. 开发者仅能在赞助平台的后台查看到基本赞助信息，无法获取用户的真实姓名、联系方式、详细地址等个人身份信息。开发者不会将赞助记录用于任何商业分析或用户画像。\n4. 用户的赞助记录仅存储在本地设备中，不上传至任何远程服务器。用户可以随时清除本地数据来删除这些记录。\n5. 开发者承诺不会向任何第三方出售、出租或分享用户的赞助信息。开发者仅在与软件维护直接相关的最小必要范围内使用这些信息。\n6. 若本软件停止运营，开发者将确保所有用户数据的妥善处置，包括但不限于在合理期限内提示用户导出数据。")
                        SupportAgreementSection("八、禁止行为", "1. 用户不得利用赞助功能进行任何违法违规活动，包括但不限于以下行为：\n　(1)洗钱：通过赞助渠道将非法所得资金伪装为合法资金；\n　(2)赌博：以赞助名义变相进行博彩、押注等赌博活动；\n　(3)诈骗：以虚假理由诱导他人进行赞助或获取不当利益；\n　(4)非法集资：以赞助的名义向不特定公众吸收资金；\n　(5)传销活动：利用赞助功能开展多层次传销或变相传销活动；\n　(6)恐怖主义融资：任何形式的为恐怖主义活动提供资金支持的行为。\n2. 用户不得以任何方式利用赞助功能进行商业运营或谋取不正当利益，包括但不限于：通过赞助换取商业合作机会、以赞助为条件要求开发者进行特定功能开发等。\n3. 用户不得使用盗取、他人代付或其他非法手段获取的资金进行赞助。如发现此类行为，开发者将配合执法机关进行调查并提供相关交易记录。\n4. 若发现上述行为，开发者有权立即停止接受赞助、冻结相关记录，并保留向公安机关报案、追究法律责任的权利。")
                        SupportAgreementSection("九、协议变更与终止", "1. 开发者保留随时修改本协议的权利，修改后的协议将在软件更新时生效。继续使用本软件即视为接受修改后的协议。\n2. 如您不同意修改后的协议，请停止使用本软件。您也可以选择不更新软件以保持旧版本，但开发者不对旧版本的兼容性和安全性提供保证。\n3. 开发者有权在任何时候停止接受赞助，无需提前通知或征得用户同意。停止接受赞助不影响已完成的赞助行为的有效性。\n4. 用户可随时停止使用本软件以终止本协议。卸载本软件即视为终止本协议。协议终止后，用户已赠与的资金不予退还，但开发者也无需对用户承担任何后续义务。")
                        SupportAgreementSection("十、不可抗力", "1. 因地震、火灾、洪水、战争、政府行为、电信故障、网络攻击、病毒大规模感染、网络屏蔽、国家法律法规或监管政策调整等不可抗力因素导致本软件无法正常运行或赞助服务中断的，开发者不承担任何责任。\n2. 因第三方AI服务提供商(如OpenAI、DeepSeek、Anthropic等)的突发故障、政策变更、服务关停、接口调整等原因导致本软件部分或全部功能受影响甚至无法使用的，属于不可抗力范畴，不构成开发者的违约责任。\n3. 因国家法律法规、监管政策变化导致本软件需要下架、停止运营或进行重大功能调整的，开发者不承担违约责任，已赠与的资金亦不予退还。\n4. 因第三方应用商店(如Google Play、华为应用市场等)的审核政策调整导致本软件被下架或限制分发的，开发者将尽力寻找替代分发渠道，但不对此承担任何保证责任。")
                        SupportAgreementSection("十一、争议解决", "1. 本协议的订立、效力、解释、履行及争议解决均适用中华人民共和国法律。如相关法律无明确规定，则参照行业惯例和公平原则处理。\n2. 因本协议引起的或与本协议有关的任何争议，双方应首先友好协商解决。协商期限为自一方书面通知另一方之日起30个自然日。\n3. 协商不成的，任何一方均有权向开发者所在地有管辖权的人民法院提起诉讼。诉讼费用(包括但不限于诉讼费、律师费、差旅费等)由败诉方承担。\n4. 在争议处理期间，本协议的其他条款仍然有效，双方应继续履行除争议条款外的其他义务。\n5. 用户同意，其与开发者之间的任何争议均应个案处理，不得以任何形式参与集体诉讼或代表诉讼。")
                        SupportAgreementSection("十二、其他条款", "1. 本协议构成双方就赞助事宜的完整协议，取代之前所有口头或书面的沟通、陈述或协议。用户确认其并非基于本协议以外的任何陈述、保证或承诺而进行赞助。\n2. 若本协议的任何条款被认定为无效或不可执行，该条款应在法律允许的最大范围内执行，且其他条款仍然有效。\n3. 开发者未行使或延迟行使本协议下的任何权利，不构成对该权利的放弃。开发者在任何情况下对用户违约行为的宽恕，不应被视为对后续违约行为的宽恕。\n4. 用户不得在未经开发者书面同意的情况下转让本协议下的任何权利或义务。\n5. 本协议中的标题仅为阅读方便而设，不构成本协议的实质性内容，不应影响本协议的解释。\n6. 用户确认已充分阅读、理解并同意本协议的全部条款。用户使用赞助功能即视为对本协议全部条款的明确同意和接受。")
                        Spacer(modifier = Modifier.height(16.dp))
                    }
                    Box(modifier = Modifier.fillMaxWidth().background(colorScheme.surface).padding(horizontal = 20.dp, vertical = 16.dp)) {
                        Column {
                            Row(modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).clickable { ageAgreed = !ageAgreed }.padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                                Checkbox(checked = ageAgreed, onCheckedChange = { ageAgreed = it }, colors = CheckboxDefaults.colors(checkedColor = Color(0xFF07C160), uncheckedColor = colorScheme.onSurfaceVariant.copy(alpha = 0.5f)))
                                Text(text = stringResource(R.string.support_agreement_age), style = MaterialTheme.typography.bodyMedium.copy(fontSize = 14.sp, lineHeight = 20.sp), color = colorScheme.onSurface)
                            }
                            Spacer(modifier = Modifier.height(12.dp))
                            Button(onClick = { if (ageAgreed) { showAgreement = false; openSponsorSite() } }, modifier = Modifier.fillMaxWidth().height(50.dp), shape = RoundedCornerShape(14.dp), colors = ButtonDefaults.buttonColors(containerColor = if (ageAgreed) Color(0xFF07C160) else Color.Gray.copy(alpha = 0.3f)), enabled = ageAgreed) {
                                Text(text = stringResource(R.string.support_agree), style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold, fontSize = 16.sp), color = Color.White)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SupportAgreementSection(title: String, body: String) {
    val colorScheme = MaterialTheme.colorScheme
    Spacer(modifier = Modifier.height(20.dp))
    Text(text = title, style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold, fontSize = 15.sp), color = colorScheme.onSurface)
    Spacer(modifier = Modifier.height(8.dp))
    Text(text = body, style = MaterialTheme.typography.bodySmall.copy(fontSize = 13.sp, lineHeight = 22.sp), color = colorScheme.onSurfaceVariant)
}
