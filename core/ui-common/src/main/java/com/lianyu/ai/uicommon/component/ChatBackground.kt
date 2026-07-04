package com.lianyu.ai.uicommon.component

import android.content.Context
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.res.stringResource
import coil.compose.AsyncImage
import com.lianyu.ai.uicommon.R
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.util.UUID

data class ChatBackgroundOption(
    val key: String,
    val name: String,
    val color: Color,
    val gradient: Brush? = null,
    val isCustom: Boolean = false
)

fun chatBackgroundOptions(context: Context): List<ChatBackgroundOption> {
    return listOf(
        ChatBackgroundOption(
            key = "default",
            name = context.getString(R.string.default_white),
            color = Color(0xFFF5F5F5)
        ),
        ChatBackgroundOption(
            key = "warm_pink",
            name = context.getString(R.string.warm_pink),
            color = Color(0xFFFFF0F3),
            gradient = Brush.verticalGradient(
                colors = listOf(Color(0xFFFFF5F7), Color(0xFFFFF0F3), Color(0xFFFFE8EC))
            )
        ),
        ChatBackgroundOption(
            key = "lavender",
            name = context.getString(R.string.lavender),
            color = Color(0xFFF8F5FF),
            gradient = Brush.verticalGradient(
                colors = listOf(Color(0xFFFBF8FF), Color(0xFFF5F0FF), Color(0xFFEDE5F8))
            )
        ),
        ChatBackgroundOption(
            key = "ocean",
            name = context.getString(R.string.ocean),
            color = Color(0xFFF0F5FA),
            gradient = Brush.verticalGradient(
                colors = listOf(Color(0xFFF5FAFF), Color(0xFFE8F2FC), Color(0xFFDBEAF5))
            )
        ),
        ChatBackgroundOption(
            key = "forest",
            name = context.getString(R.string.forest),
            color = Color(0xFFF2F8F2),
            gradient = Brush.verticalGradient(
                colors = listOf(Color(0xFFF8FCF8), Color(0xFFEEF5EE), Color(0xFFE3EFE3))
            )
        ),
        ChatBackgroundOption(
            key = "sunset",
            name = context.getString(R.string.sunset),
            color = Color(0xFFFFF5F0),
            gradient = Brush.verticalGradient(
                colors = listOf(Color(0xFFFFF8F5), Color(0xFFFFF0E8), Color(0xFFFFE8D8))
            )
        ),
        ChatBackgroundOption(
            key = "night",
            name = context.getString(R.string.night_sky),
            color = Color(0xFFF0F0F5),
            gradient = Brush.verticalGradient(
                colors = listOf(Color(0xFFF5F5FA), Color(0xFFEEEEF5), Color(0xFFE5E5F0))
            )
        )
    )
}

private const val CHAT_BG_PREF = "chat_background"
private const val CUSTOM_BG_PREFIX = "custom_"
private const val CUSTOM_BG_DIR = "chat_backgrounds"
private const val MAX_CUSTOM_BG_BYTES = 8L * 1024L * 1024L
private val CUSTOM_BG_FILE_REGEX = Regex("^bg_[0-9a-fA-F-]{36}\\.jpg$")
private val CUSTOM_BG_TEMP_FILE_REGEX = Regex("^\\.tmp_bg_[0-9a-fA-F-]{36}\\.part$")

fun getChatBackgroundKey(context: Context): String {
    return context.getSharedPreferences("chat_prefs", Context.MODE_PRIVATE)
        .getString(CHAT_BG_PREF, "default") ?: "default"
}

fun setChatBackgroundKey(context: Context, key: String) {
    context.getSharedPreferences("chat_prefs", Context.MODE_PRIVATE)
        .edit()
        .putString(CHAT_BG_PREF, key)
        .apply()
    // 设置背景时预加载到内存缓存
    if (isCustomBackground(key)) {
        ChatBackgroundCache.preload(context, key)
    }
}

fun isCustomBackground(key: String): Boolean {
    return key.startsWith(CUSTOM_BG_PREFIX)
}

fun getCustomBackgroundFile(context: Context, key: String): File? {
    if (!isCustomBackground(key)) return null
    val fileName = key.removePrefix(CUSTOM_BG_PREFIX)
    if (!CUSTOM_BG_FILE_REGEX.matches(fileName)) return null

    return try {
        val dir = File(context.filesDir, CUSTOM_BG_DIR)
        val canonicalDir = dir.canonicalFile
        val candidate = File(canonicalDir, fileName).canonicalFile
        if (candidate.parentFile != canonicalDir) null else candidate
    } catch (_: IOException) {
        null
    } catch (_: SecurityException) {
        null
    }
}

fun saveCustomBackground(context: Context, uri: Uri): String? {
    var tempFile: File? = null
    var finalFile: File? = null
    return try {
        val dir = File(context.filesDir, CUSTOM_BG_DIR).canonicalFile
        if (!dir.exists() && !dir.mkdirs()) return null
        if (!dir.isDirectory) return null

        val id = UUID.randomUUID().toString()
        val fileName = "bg_${id}.jpg"
        val tempFileName = ".tmp_bg_${id}.part"
        if (!CUSTOM_BG_FILE_REGEX.matches(fileName)) return null
        if (!CUSTOM_BG_TEMP_FILE_REGEX.matches(tempFileName)) return null
        tempFile = File(dir, tempFileName).canonicalFile
        finalFile = File(dir, fileName).canonicalFile
        val targetTempFile = tempFile
        val targetFinalFile = finalFile
        if (targetTempFile.parentFile != dir || targetFinalFile.parentFile != dir) return null

        var copied = 0L
        context.contentResolver.openInputStream(uri)?.use { input ->
            FileOutputStream(targetTempFile).use { output ->
                val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                while (true) {
                    val read = input.read(buffer)
                    if (read < 0) break
                    copied += read.toLong()
                    if (copied > MAX_CUSTOM_BG_BYTES) {
                        output.close()
                        targetTempFile.delete()
                        return null
                    }
                    output.write(buffer, 0, read)
                }
                output.fd.sync()
            }
        } ?: return null

        if (!isReadableImage(targetTempFile)) {
            targetTempFile.delete()
            return null
        }

        if (targetFinalFile.exists()) {
            targetTempFile.delete()
            return null
        }

        if (!targetTempFile.renameTo(targetFinalFile)) {
            targetTempFile.delete()
            return null
        }

        CUSTOM_BG_PREFIX + fileName
    } catch (_: IOException) {
        tempFile?.delete()
        finalFile?.delete()
        null
    } catch (_: SecurityException) {
        tempFile?.delete()
        finalFile?.delete()
        null
    }
}

fun deleteCustomBackground(context: Context, key: String) {
    if (!isCustomBackground(key)) return
    getCustomBackgroundFile(context, key)?.delete()
}

private fun isReadableImage(file: File): Boolean {
    val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeFile(file.absolutePath, options)
    return options.outWidth > 0 && options.outHeight > 0
}

fun getChatBackground(context: Context, isDark: Boolean): Pair<Color, Brush?> {
    val key = getChatBackgroundKey(context)
    return getChatBackgroundByKey(context, key, isDark)
}

fun getChatBackgroundByKey(context: Context, key: String, isDark: Boolean): Pair<Color, Brush?> {
    if (isCustomBackground(key)) {
        val fallback = if (isDark) Color(0xFF1A1216) else Color(0xFFF5F5F5)
        return fallback to null
    }
    val allOptions = chatBackgroundOptions(context)
    val option = allOptions.find { it.key == key } ?: allOptions.first()
    return option.color to option.gradient
}

fun getCustomBackgroundUri(context: Context, key: String): Uri? {
    val file = getCustomBackgroundFile(context, key) ?: return null
    return Uri.fromFile(file)
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ChatBackgroundPickerDialog(
    currentKey: String,
    onDismiss: () -> Unit,
    onSelect: (String) -> Unit
) {
    val context = LocalContext.current
    var selectedKey by remember { mutableStateOf(currentKey) }
    var customKeys by remember {
        mutableStateOf(loadCustomBackgroundKeys(context))
    }

    val imagePicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let {
            val key = saveCustomBackground(context, it)
            key?.let { newKey ->
                customKeys = customKeys + newKey
                selectedKey = newKey
                setChatBackgroundKey(context, newKey)
            }
        }
    }

    val options = chatBackgroundOptions(context)

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                stringResource(R.string.select_chat_bg),
                style = MaterialTheme.typography.titleMedium.copy(
                    fontWeight = FontWeight.SemiBold
                ),
                color = MaterialTheme.colorScheme.onSurface
            )
        },
        text = {
            Column {
                Text(
                    stringResource(R.string.select_bg_hint),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(16.dp))

                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    // 预设背景
                    options.forEach { option ->
                        val isSelected = option.key == selectedKey
                        BackgroundOptionItem(
                            name = option.name,
                            isSelected = isSelected,
                            color = option.color,
                            gradient = option.gradient,
                            onClick = {
                                selectedKey = option.key
                                setChatBackgroundKey(context, option.key)
                                onSelect(option.key)
                            }
                        )
                    }

                    // 自定义背景图片
                    customKeys.forEach { key ->
                        val isSelected = key == selectedKey
                        val uri = getCustomBackgroundUri(context, key)
                        CustomBackgroundItem(
                            uri = uri,
                            isSelected = isSelected,
                            onClick = {
                                selectedKey = key
                                setChatBackgroundKey(context, key)
                                onSelect(key)
                            },
                            onDelete = {
                                deleteCustomBackground(context, key)
                                customKeys = customKeys - key
                                if (selectedKey == key) {
                                    selectedKey = "default"
                                    setChatBackgroundKey(context, "default")
                                    onSelect("default")
                                }
                            }
                        )
                    }

                    // 添加自定义背景按钮
                    AddCustomBackgroundItem {
                        imagePicker.launch("image/*")
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.close), color = MaterialTheme.colorScheme.primary)
            }
        },
        containerColor = MaterialTheme.colorScheme.surface
    )
}

@Composable
private fun BackgroundOptionItem(
    name: String,
    isSelected: Boolean,
    color: Color,
    gradient: Brush?,
    onClick: () -> Unit
) {
    val primary = MaterialTheme.colorScheme.primary
    val onSurfaceVariant = MaterialTheme.colorScheme.onSurfaceVariant
    Column(
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier
                .size(56.dp)
                .clip(RoundedCornerShape(14.dp))
                .border(
                    width = if (isSelected) 2.dp else 0.dp,
                    color = if (isSelected) primary.copy(alpha = 0.8f) else Color.Transparent,
                    shape = RoundedCornerShape(14.dp)
                )
                .background(
                    if (gradient != null) gradient
                    else Brush.linearGradient(listOf(color, color))
                )
                .clickable { onClick() }
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = name,
            style = MaterialTheme.typography.labelSmall.copy(
                fontSize = 11.sp
            ),
            color = if (isSelected) primary else onSurfaceVariant
        )
    }
}

@Composable
private fun CustomBackgroundItem(
    uri: Uri?,
    isSelected: Boolean,
    onClick: () -> Unit,
    onDelete: () -> Unit
) {
    val primary = MaterialTheme.colorScheme.primary
    val onSurfaceVariant = MaterialTheme.colorScheme.onSurfaceVariant
    val surfaceVariant = MaterialTheme.colorScheme.surfaceVariant
    Column(
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier
                .size(56.dp)
                .clip(RoundedCornerShape(14.dp))
                .border(
                    width = if (isSelected) 2.dp else 0.dp,
                    color = if (isSelected) primary.copy(alpha = 0.8f) else Color.Transparent,
                    shape = RoundedCornerShape(14.dp)
                )
                .clickable { onClick() }
        ) {
            if (uri != null) {
                AsyncImage(
                    model = uri,
                    contentDescription = stringResource(R.string.custom),
                    modifier = Modifier.fillMaxWidth().height(56.dp),
                    contentScale = ContentScale.Crop
                )
            } else {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(56.dp)
                        .background(surfaceVariant),
                    contentAlignment = Alignment.Center
                ) {
                    Text("?", color = onSurfaceVariant)
                }
            }

            // 删除按钮
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .size(18.dp)
                    .clip(RoundedCornerShape(50))
                    .background(MaterialTheme.colorScheme.error)
                    .clickable { onDelete() },
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Filled.Delete,
                    contentDescription = stringResource(R.string.delete),
                    tint = MaterialTheme.colorScheme.onError,
                    modifier = Modifier.size(10.dp)
                )
            }
        }
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = stringResource(R.string.custom),
            style = MaterialTheme.typography.labelSmall.copy(
                fontSize = 11.sp
            ),
            color = if (isSelected) primary else onSurfaceVariant
        )
    }
}

@Composable
private fun AddCustomBackgroundItem(
    onClick: () -> Unit
) {
    val primary = MaterialTheme.colorScheme.primary
    Column(
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier
                .size(56.dp)
                .clip(RoundedCornerShape(14.dp))
                .border(
                    width = 1.dp,
                    color = primary.copy(alpha = 0.5f),
                    shape = RoundedCornerShape(14.dp)
                )
                .background(MaterialTheme.colorScheme.surfaceVariant)
                .clickable { onClick() },
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Filled.Add,
                contentDescription = stringResource(R.string.add_custom_bg),
                tint = primary,
                modifier = Modifier.size(24.dp)
            )
        }
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = stringResource(R.string.upload_image),
            style = MaterialTheme.typography.labelSmall.copy(
                fontSize = 11.sp
            ),
            color = primary
        )
    }
}

private fun loadCustomBackgroundKeys(context: Context): List<String> {
    val dir = File(context.filesDir, CUSTOM_BG_DIR)
    if (!dir.exists()) return emptyList()
    return dir.listFiles()
        ?.asSequence()
        ?.onEach { if (CUSTOM_BG_TEMP_FILE_REGEX.matches(it.name)) it.delete() }
        ?.map { it.name }
        ?.filter { CUSTOM_BG_FILE_REGEX.matches(it) }
        ?.map { CUSTOM_BG_PREFIX + it }
        ?.toList()
        ?: emptyList()
}
