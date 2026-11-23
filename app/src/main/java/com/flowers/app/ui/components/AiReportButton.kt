package com.flowers.app.ui.components

import android.content.Context
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.flowers.app.FlowersApplication
import com.flowers.app.data.local.entity.CatalogPlantEntity
import com.flowers.app.data.report.AiReport
import com.flowers.app.data.report.AiReportRepository
import com.flowers.app.data.report.ReportReason
import com.flowers.app.data.report.ReportResult
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

object ReportTexts {
    const val BUTTON = "Поскаржитися на відповідь ШІ"
    const val TITLE = "Поскаржитися на відповідь ШІ"
    const val REASON_HINT = "Що не так?"
    const val COMMENT_LABEL = "Коментар (необов'язково)"
    const val PRIVACY =
        "Не вказуйте особисті дані. Буде надіслано текст картки, обрану причину й версію застосунку. " +
            "Скарги зберігаються 12 місяців."
    const val SEND = "Надіслати"
    const val CANCEL = "Скасувати"
    const val CLOSE = "Закрити"
}

/** Кнопка «Поскаржитися на відповідь ШІ» біля ШІ-картки (TASK-027). Не виводить із застосунку. */
@Composable
fun AiReportButton(plant: CatalogPlantEntity, modifier: Modifier = Modifier) {
    var open by remember { mutableStateOf(false) }
    TextButton(onClick = { open = true }, contentPadding = PaddingValues(0.dp), modifier = modifier) {
        Text(ReportTexts.BUTTON, style = MaterialTheme.typography.labelSmall)
    }
    if (open) AiReportDialog(plant = plant, onDismiss = { open = false })
}

@Composable
private fun AiReportDialog(plant: CatalogPlantEntity, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val app = context.applicationContext as FlowersApplication
    val scope = rememberCoroutineScope()
    var reason by remember { mutableStateOf(ReportReason.WRONG) }
    var comment by remember { mutableStateOf("") }
    var sending by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }
    var sent by remember { mutableStateOf(false) }
    var queued by remember { mutableStateOf(false) }
    // Один ID документа на діалог: повторне надсилання не створює дубль (TASK-048).
    val docId = remember { app.aiReportRepository.newReportId() }

    AlertDialog(
        onDismissRequest = { if (!sending) onDismiss() },
        title = { Text(ReportTexts.TITLE) },
        text = {
            Column {
                if (sent) {
                    Text(if (queued) AiReportRepository.MSG_QUEUED else AiReportRepository.MSG_THANKS)
                } else {
                    Text(ReportTexts.REASON_HINT, style = MaterialTheme.typography.labelLarge)
                    Column(Modifier.selectableGroup()) {
                        ReportReason.entries.forEach { r ->
                            Row(
                                Modifier.fillMaxWidth().selectable(
                                    selected = reason == r,
                                    enabled = !sending,
                                    onClick = { reason = r },
                                    role = Role.RadioButton
                                ),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                RadioButton(selected = reason == r, onClick = null, enabled = !sending)
                                Text(r.label, modifier = Modifier.padding(start = 8.dp))
                            }
                        }
                    }
                    OutlinedTextField(
                        value = comment,
                        onValueChange = { if (it.length <= AiReport.MAX_COMMENT) comment = it },
                        label = { Text(ReportTexts.COMMENT_LABEL) },
                        enabled = !sending,
                        minLines = 2,
                        modifier = Modifier.fillMaxWidth().padding(top = 8.dp)
                    )
                    Text(
                        ReportTexts.PRIVACY,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 8.dp)
                    )
                    message?.let {
                        Text(
                            it,
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.padding(top = 8.dp)
                        )
                    }
                }
            }
        },
        confirmButton = {
            if (sent) {
                TextButton(onClick = onDismiss) { Text(ReportTexts.CLOSE) }
            } else {
                TextButton(
                    enabled = !sending,
                    onClick = {
                        if (sending) return@TextButton
                        sending = true
                        message = null
                        scope.launch {
                            val pods = runCatching { app.repository.getPodsForSpecies(plant.id).first() }
                                .getOrDefault(emptyList())
                            val report = AiReport.forPlantCard(plant, pods, reason, comment, appVersion(context))
                            when (val r = app.aiReportRepository.submit(docId, report)) {
                                ReportResult.Sent -> sent = true
                                ReportResult.Queued -> { queued = true; sent = true }
                                is ReportResult.Failed -> message = r.message
                            }
                            sending = false
                        }
                    }
                ) { Text(if (sending) "…" else ReportTexts.SEND) }
            }
        },
        dismissButton = {
            if (!sent) TextButton(enabled = !sending, onClick = onDismiss) { Text(ReportTexts.CANCEL) }
        }
    )
}

private fun appVersion(context: Context): String = try {
    context.packageManager.getPackageInfo(context.packageName, 0).versionName ?: "?"
} catch (e: Exception) {
    "?"
}
