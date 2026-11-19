package com.bird.fiber.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.bird.fiber.data.local.TagBackfillState

/** An ordinary small Surface: no window, scrim or focus capture. Outside remains interactive. */
@Composable
fun TagBackfillOverlay(state: TagBackfillState, onRetry: () -> Unit, modifier: Modifier = Modifier) {
    if (!state.visible) return
    Surface(modifier.widthIn(max = 280.dp), shape = MaterialTheme.shapes.medium,
        tonalElevation = 6.dp, shadowElevation = 4.dp) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(if (state.failed) "标签数据整理未完成" else "标签数据正在整理", style = MaterialTheme.typography.labelLarge)
            if (state.total > 0) {
                LinearProgressIndicator(progress = { (state.completed.toFloat() / state.total).coerceIn(0f, 1f) }, modifier = Modifier.fillMaxWidth())
                Text("${state.completed} / ${state.total}", style = MaterialTheme.typography.labelSmall)
            } else LinearProgressIndicator(Modifier.fillMaxWidth())
            if (state.failed) TextButton(onClick = onRetry) { Text("重试（${state.pending} 篇待整理）") }
        }
    }
}
