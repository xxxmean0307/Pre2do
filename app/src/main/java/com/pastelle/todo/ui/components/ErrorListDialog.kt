package com.pastelle.todo.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.pastelle.todo.R
import com.pastelle.todo.ui.theme.Ink
import com.pastelle.todo.ui.theme.InkSoft
import com.pastelle.todo.ui.theme.PastelleType

/**
 * 白色圆角错误对话框：逐条列出第几条任务、什么问题（规格 6.1）。
 */
@Composable
fun ErrorListDialog(
    errors: List<String>,
    onDismiss: () -> Unit,
) {
    Dialog(onDismissRequest = onDismiss) {
        PastelleCard {
            Text(
                stringResource(R.string.import_error_title),
                style = PastelleType.PageTitle,
                color = Ink,
            )
            Spacer(Modifier.height(12.dp))
            Column(
                Modifier
                    .heightIn(max = 320.dp)
                    .verticalScroll(rememberScrollState())
            ) {
                errors.forEach { message ->
                    Row(Modifier.padding(vertical = 4.dp)) {
                        Text("・", style = PastelleType.Body, color = Ink)
                        Text(message, style = PastelleType.Body, color = Ink)
                    }
                }
            }
            Spacer(Modifier.height(8.dp))
            Text(
                stringResource(R.string.import_error_hint),
                style = PastelleType.Caption,
                color = InkSoft,
            )
            Spacer(Modifier.height(16.dp))
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Spacer(Modifier.width(12.dp))
                BlackPillButton(
                    label = stringResource(R.string.import_ok),
                    onClick = onDismiss,
                    height = 52.dp,
                    trailingIcon = PillTrailingIcon.Check,
                )
            }
        }
    }
}
