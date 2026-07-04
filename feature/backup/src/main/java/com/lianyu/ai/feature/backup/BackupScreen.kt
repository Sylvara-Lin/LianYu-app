package com.lianyu.ai.feature.backup

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * 数据备份与恢复界面。
 *
 * 导出流程：点击导出 → 密码弹窗 → export(password) → 收集加密数据 → SAF 保存文件
 * 导入流程：点击导入 → SAF 选择文件 → 密码弹窗 → import(uri, password)
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BackupScreen(onNavigateBack: () -> Unit) {
    val context = LocalContext.current
    val colorScheme = MaterialTheme.colorScheme
    val scope = rememberCoroutineScope()
    val viewModel: BackupViewModel = viewModel()
    val uiState by viewModel.uiState.collectAsState()

    var isVisible by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { delay(80); isVisible = true }

    // 密码弹窗状态
    var showPasswordDialog by remember { mutableStateOf(false) }
    var passwordMode by remember { mutableStateOf(PasswordMode.EXPORT) }
    var pendingImportUri by remember { mutableStateOf<Uri?>(null) }

    // SAF 导出：收到加密数据后打开保存对话框
    var pendingExportBytes by remember { mutableStateOf<ByteArray?>(null) }

    val exportSaveLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/octet-stream")
    ) { uri: Uri? ->
        uri?.let { dest ->
            pendingExportBytes?.let { bytes ->
                scope.launch {
                    try {
                        kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                            context.contentResolver.openOutputStream(dest)?.use { it.write(bytes) }
                        }
                        viewModel.onExportComplete()
                    } catch (e: Exception) {
                        viewModel.onError("保存文件失败: ${e.localizedMessage}")
                    }
                    pendingExportBytes = null
                }
            }
        }
    }

    // 收集导出结果 → 打开 SAF
    LaunchedEffect(Unit) {
        viewModel.exportResult.collect { encryptedBytes ->
            pendingExportBytes = encryptedBytes
            exportSaveLauncher.launch("lianyu_backup_${dateString()}.lybk")
        }
    }

    // SAF 导入：选择文件
    val importFileLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        uri?.let {
            pendingImportUri = it
            passwordMode = PasswordMode.IMPORT
            showPasswordDialog = true
        }
    }

    // 收集导入请求 → 打开 SAF
    LaunchedEffect(Unit) {
        viewModel.importRequest.collect {
            importFileLauncher.launch(arrayOf("application/octet-stream", "*/*"))
        }
    }

    // 密码弹窗
    if (showPasswordDialog) {
        PasswordDialog(
            mode = passwordMode,
            isLoading = uiState is BackupViewModel.UiState.Exporting || uiState is BackupViewModel.UiState.Importing,
            onConfirm = { password ->
                when (passwordMode) {
                    PasswordMode.EXPORT -> viewModel.export(password)
                    PasswordMode.IMPORT -> {
                        pendingImportUri?.let { viewModel.import(it, password) }
                        pendingImportUri = null
                    }
                }
                showPasswordDialog = false
            },
            onDismiss = {
                showPasswordDialog = false
                pendingImportUri = null
                if (uiState !is BackupViewModel.UiState.Exporting &&
                    uiState !is BackupViewModel.UiState.Importing) {
                    viewModel.resetState()
                }
            }
        )
    }

    // 结果 Snackbar
    val snackbarHostState = remember { SnackbarHostState() }
    LaunchedEffect(uiState) {
        when (val s = uiState) {
            is BackupViewModel.UiState.Success -> {
                snackbarHostState.showSnackbar(s.message)
                viewModel.resetState()
            }
            is BackupViewModel.UiState.Error -> {
                snackbarHostState.showSnackbar(s.message)
                viewModel.resetState()
            }
            else -> {}
        }
    }

    Scaffold(
        modifier = Modifier.fillMaxSize().background(colorScheme.background),
        containerColor = colorScheme.background,
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.backup_title), fontWeight = FontWeight.SemiBold) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.backup_back), tint = colorScheme.onSurface)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = colorScheme.background)
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
        ) {
            Spacer(modifier = Modifier.height(16.dp))

            // 导出卡片
            AnimatedVisibility(
                visible = isVisible,
                enter = fadeIn(tween(350)) + slideInVertically(tween(350)) { it / 4 }
            ) {
                BackupCard(
                    icon = Icons.Filled.SaveAlt,
                    title = stringResource(R.string.backup_export_title),
                    description = stringResource(R.string.backup_export_desc),
                    buttonText = stringResource(R.string.backup_export_btn),
                    buttonColor = Color(0xFF07C160),
                    isLoading = uiState is BackupViewModel.UiState.Exporting,
                    onClick = {
                        passwordMode = PasswordMode.EXPORT
                        showPasswordDialog = true
                    }
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            // 导入卡片
            AnimatedVisibility(
                visible = isVisible,
                enter = fadeIn(tween(400, delayMillis = 80)) + slideInVertically(tween(400, delayMillis = 80)) { it / 4 }
            ) {
                BackupCard(
                    icon = Icons.Filled.FileOpen,
                    title = stringResource(R.string.backup_import_title),
                    description = stringResource(R.string.backup_import_desc),
                    buttonText = stringResource(R.string.backup_import_btn),
                    buttonColor = Color(0xFFFA5151),
                    isLoading = uiState is BackupViewModel.UiState.Importing,
                    onClick = { viewModel.requestImport() }
                )
            }

            Spacer(modifier = Modifier.height(24.dp))

            // 说明文字
            AnimatedVisibility(
                visible = isVisible,
                enter = fadeIn(tween(500, delayMillis = 160))
            ) {
                Card(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = colorScheme.surfaceVariant)
                ) {
                    Column(Modifier.padding(16.dp)) {
                        Text(
                            stringResource(R.string.backup_notice),
                            style = MaterialTheme.typography.bodySmall,
                            color = colorScheme.onSurfaceVariant,
                            lineHeight = 20.sp
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(80.dp))
        }
    }
}

// ============================================================================
// 密码弹窗
// ============================================================================

enum class PasswordMode { EXPORT, IMPORT }

@Composable
private fun PasswordDialog(
    mode: PasswordMode,
    isLoading: Boolean,
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit
) {
    var password by remember { mutableStateOf("") }
    var confirmPassword by remember { mutableStateOf("") }
    var showPassword by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = { if (!isLoading) onDismiss() },
        title = {
            Text(
                if (mode == PasswordMode.EXPORT) "设置备份密码" else "输入备份密码",
                fontWeight = FontWeight.SemiBold
            )
        },
        text = {
            Column {
                if (mode == PasswordMode.EXPORT) {
                    Text("请设置6位以上密码保护备份文件", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                } else {
                    Text("请输入备份时设置的密码", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Spacer(Modifier.height(12.dp))
                OutlinedTextField(
                    value = password,
                    onValueChange = { password = it; error = null },
                    label = { Text("密码") },
                    singleLine = true,
                    visualTransformation = if (showPassword) VisualTransformation.None else PasswordVisualTransformation(),
                    trailingIcon = {
                        IconButton(onClick = { showPassword = !showPassword }) {
                            Icon(
                                if (showPassword) Icons.Filled.VisibilityOff else Icons.Filled.Visibility,
                                contentDescription = if (showPassword) "隐藏密码" else "显示密码"
                            )
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !isLoading
                )
                if (mode == PasswordMode.EXPORT) {
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(
                        value = confirmPassword,
                        onValueChange = { confirmPassword = it; error = null },
                        label = { Text("确认密码") },
                        singleLine = true,
                        visualTransformation = PasswordVisualTransformation(),
                        modifier = Modifier.fillMaxWidth(),
                        enabled = !isLoading
                    )
                }
                error?.let {
                    Spacer(Modifier.height(4.dp))
                    Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    when {
                        password.length < 6 -> error = "密码至少6位"
                        mode == PasswordMode.EXPORT && password != confirmPassword -> error = "两次密码不一致"
                        else -> onConfirm(password)
                    }
                },
                enabled = !isLoading
            ) {
                Text(if (isLoading) "处理中..." else "确定")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, enabled = !isLoading) {
                Text("取消")
            }
        }
    )
}

// ============================================================================
// 功能卡片
// ============================================================================

@Composable
private fun BackupCard(
    icon: ImageVector,
    title: String,
    description: String,
    buttonText: String,
    buttonColor: Color,
    isLoading: Boolean,
    onClick: () -> Unit
) {
    val colorScheme = MaterialTheme.colorScheme

    Card(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = colorScheme.surfaceVariant)
    ) {
        Column(Modifier.padding(20.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(icon, title, Modifier.size(28.dp), tint = buttonColor)
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(title, style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold, fontSize = 17.sp), color = colorScheme.onSurface)
                    Spacer(Modifier.height(4.dp))
                    Text(description, style = MaterialTheme.typography.bodySmall.copy(fontSize = 13.sp), color = colorScheme.onSurfaceVariant)
                }
            }
            Spacer(Modifier.height(16.dp))
            Button(
                onClick = onClick,
                modifier = Modifier.fillMaxWidth().height(44.dp),
                shape = RoundedCornerShape(10.dp),
                colors = ButtonDefaults.buttonColors(containerColor = buttonColor),
                enabled = !isLoading
            ) {
                if (isLoading) {
                    CircularProgressIndicator(Modifier.size(20.dp), color = Color.White, strokeWidth = 2.dp)
                    Spacer(Modifier.width(8.dp))
                }
                Text(if (isLoading) "处理中..." else buttonText, color = Color.White)
            }
        }
    }
}

// ============================================================================
// 工具函数
// ============================================================================

private fun dateString(): String {
    val cal = java.util.Calendar.getInstance()
    return "${cal.get(java.util.Calendar.YEAR)}-${(cal.get(java.util.Calendar.MONTH) + 1).toString().padStart(2, '0')}-${cal.get(java.util.Calendar.DAY_OF_MONTH).toString().padStart(2, '0')}"
}
