package com.hydro.reedance.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.KeyboardType
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.hydro.reedance.data.AppModel

/** Password is deliberately not rememberSaveable, logged, passed to WorkManager or shown again. */
@Composable fun PackControls(model: AppModel) {
    var showPassword by remember { mutableStateOf(false) }
    var password by remember { mutableStateOf("") }
    val busy by model.busy.collectAsStateWithLifecycle()
    val saving by model.savingPackPassword.collectAsStateWithLifecycle()
    val progress by model.installProgress.collectAsStateWithLifecycle()
    Text(tr("بسته‌های استودیو", "Studio packages"), style = MaterialTheme.typography.titleLarge)
    Text(tr("رمز فایلِ تنظیمات استودیو را یک‌بار وارد کن. رمز در APK قرار نمی‌گیرد.", "Enter the file password from Studio Settings once. It is not embedded in the APK."))
    PressOutlinedButton({ showPassword = true }, enabled = !busy && !saving) { Text(tr("ورود یا تغییر رمز فایل", "Set or change file password")) }
    PressTextButton({ model.forgetPackPassword() }, enabled = !busy && !saving) { Text(tr("فراموش کردن رمز ذخیره‌شده", "Forget stored file password")) }
    if(busy) {
        LinearProgressIndicator(Modifier.fillMaxWidth()); Text(progress)
        PressTextButton(model::cancelInstall) { Text(tr("لغو نصب", "Cancel installation")) }
    }
    if(showPassword) AlertDialog(
        onDismissRequest = { showPassword = false; password = "" },
        title = { Text(tr("رمز فایل استودیو", "Studio file password")) },
        text = { OutlinedTextField(value = password, onValueChange = { if(it.length <= 256) password = it }, singleLine = true,
            visualTransformation = PasswordVisualTransformation(), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
            label = { Text(tr("رمز، با خط‌تیره‌ها", "Password, including hyphens")) }) },
        confirmButton = { PressTextButton({ model.savePackPassword(password); password = ""; showPassword = false }, enabled = password.isNotBlank()) { Text(tr("ذخیرهٔ امن", "Save securely")) } },
        dismissButton = { PressTextButton({ password = ""; showPassword = false }) { Text(tr("انصراف", "Cancel")) } })
}
