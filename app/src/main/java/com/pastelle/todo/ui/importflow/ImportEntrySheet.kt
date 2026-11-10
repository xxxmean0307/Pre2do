package com.pastelle.todo.ui.importflow

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ContentPaste
import androidx.compose.material.icons.outlined.FolderOpen
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.pastelle.todo.R
import com.pastelle.todo.ui.components.pressScale
import com.pastelle.todo.ui.theme.CardWhite
import com.pastelle.todo.ui.theme.Ink
import com.pastelle.todo.ui.theme.InkSoft
import com.pastelle.todo.ui.theme.PastelleShapes
import com.pastelle.todo.ui.theme.PastelleType
import com.pastelle.todo.ui.theme.Pink
import com.pastelle.todo.ui.theme.PinkSoft
import com.pastelle.todo.ui.theme.Purple
import com.pastelle.todo.ui.theme.PurpleSoft
import com.pastelle.todo.ui.theme.Blue
import com.pastelle.todo.ui.theme.BlueSoft

/**
 * 「一键导入日程」入口底部卡片（规格 6.2 第 1 步）：
 * 粘贴剪贴板 / 选择 JSON 文件 / 从分享进入（第三项为引导说明）。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ImportEntrySheet(
    onDismiss: () -> Unit,
    onClipboard: () -> Unit,
    onFile: () -> Unit,
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = CardWhite,
        shape = PastelleShapes.BottomBar,
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 24.dp)
                .padding(bottom = 28.dp),
        ) {
            Text(
                stringResource(R.string.import_sheet_title),
                style = PastelleType.PageTitle,
                color = Ink,
            )
            Spacer(Modifier.height(16.dp))
            ImportOptionRow(
                icon = Icons.Outlined.ContentPaste,
                iconTint = Pink,
                iconBackground = PinkSoft,
                title = stringResource(R.string.import_opt_clipboard),
                hint = stringResource(R.string.import_opt_clipboard_hint),
                onClick = onClipboard,
            )
            ImportOptionRow(
                icon = Icons.Outlined.FolderOpen,
                iconTint = Purple,
                iconBackground = PurpleSoft,
                title = stringResource(R.string.import_opt_file),
                hint = stringResource(R.string.import_opt_file_hint),
                onClick = onFile,
            )
            ImportOptionRow(
                icon = Icons.Outlined.Share,
                iconTint = Blue,
                iconBackground = BlueSoft,
                title = stringResource(R.string.import_opt_share),
                hint = stringResource(R.string.import_opt_share_hint),
                onClick = onDismiss,
            )
        }
    }
}

@Composable
private fun ImportOptionRow(
    icon: ImageVector,
    iconTint: Color,
    iconBackground: Color,
    title: String,
    hint: String,
    onClick: () -> Unit,
) {
    val interactionSource = remember { MutableInteractionSource() }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(PastelleShapes.Card)
            .pressScale(interactionSource)
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick,
            )
            .padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(48.dp)
                .clip(CircleShape)
                .background(iconBackground),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = iconTint,
                modifier = Modifier.size(24.dp),
            )
        }
        Spacer(Modifier.width(14.dp))
        Column {
            Text(title, style = PastelleType.CardTitle, color = Ink)
            Text(hint, style = PastelleType.Caption, color = InkSoft)
        }
    }
}
