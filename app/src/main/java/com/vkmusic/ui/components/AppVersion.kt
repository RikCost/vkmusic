package com.vkmusic.ui.components

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.vkmusic.BuildConfig
import com.vkmusic.R

@Composable
fun appVersionLabel(): String = stringResource(R.string.app_version, BuildConfig.VERSION_NAME, BuildConfig.VERSION_CODE)

@Composable
fun AppVersionText(modifier: Modifier = Modifier) {
    Text(
        text = appVersionLabel(),
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = modifier,
    )
}

@Composable
fun AboutDialog(onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.app_name)) },
        text = { Text("${appVersionLabel()}\nНеофициальный плеер музыки ВКонтакте для личного использования.") },
        confirmButton = { TextButton(onClick = onDismiss) { Text("OK") } },
    )
}
