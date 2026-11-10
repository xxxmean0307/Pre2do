package com.pastelle.todo.ui.importflow

import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.pastelle.todo.PastelleApp
import com.pastelle.todo.R
import com.pastelle.todo.data.PastelleRepository
import com.pastelle.todo.data.json.ParsedSchedule
import com.pastelle.todo.data.json.ScheduleParser
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/** 一键导入流程的界面状态 */
sealed interface ImportUiState {
    /** 尚未解析（直接进入预览页时短暂显示） */
    data object Idle : ImportUiState

    /** 解析失败 / 校验失败：逐条错误，不写入任何数据 */
    data class Errors(val errors: List<String>) : ImportUiState

    /** 解析通过：预览（含软冲突提示、覆盖提示） */
    data class Preview(
        val schedule: ParsedSchedule,
        val warnings: List<String>,
        val willOverwrite: Boolean,
        val saving: Boolean = false,
        val saved: Boolean = false,
    ) : ImportUiState
}

/** 一键导入状态机：解析 → 预览 → 确认写入 Room + 排闹钟（规格 6 节） */
class ImportViewModel(
    private val repository: PastelleRepository,
    private val engine: com.pastelle.todo.timing.TimingEngine,
) : ViewModel() {

    private val _state = MutableStateFlow<ImportUiState>(ImportUiState.Idle)
    val state: StateFlow<ImportUiState> = _state.asStateFlow()

    /** 任意入口（剪贴板 / 文件 / 分享）拿到文本后解析并进入预览 */
    fun parse(text: String?) {
        viewModelScope.launch {
            if (text.isNullOrBlank()) {
                _state.value = ImportUiState.Errors(listOf("没有读取到日程文本（剪贴板为空或分享内容不可读）"))
                return@launch
            }
            val result = ScheduleParser.parse(text)
            if (!result.isValid || result.schedule == null) {
                _state.value = ImportUiState.Errors(result.errors)
                return@launch
            }
            val overwrite = repository.hasSchedule(result.schedule.date)
            _state.value = ImportUiState.Preview(
                schedule = result.schedule,
                warnings = result.warnings,
                willOverwrite = overwrite,
            )
        }
    }

    /** 确认导入：写入 Room（覆盖式 upsert）并为每个任务排 T0−5min 与 T0 闹钟（规格 6.2.3） */
    fun confirm() {
        val current = _state.value as? ImportUiState.Preview ?: return
        if (current.saving || current.saved) return
        _state.value = current.copy(saving = true)
        viewModelScope.launch {
            try {
                repository.importSchedule(current.schedule)
                engine.planImportedTasks(repository.tasksOfDate(current.schedule.date))
                _state.value = current.copy(saving = false, saved = true)
            } catch (e: Exception) {
                // ★ 写库/排闹钟失败时不能让按钮永久停在「正在保存…」、1.2s 跳转永不触发
                _state.value = ImportUiState.Errors(
                    listOf(
                        repository.context.getString(R.string.import_save_failed) +
                            (e.localizedMessage?.let { "：$it" } ?: ""),
                    ),
                )
            }
        }
    }

    fun reset() {
        _state.value = ImportUiState.Idle
    }
}

/** Activity 作用域的导入状态机 */
@Composable
fun rememberImportViewModel(): ImportViewModel {
    val app = LocalContext.current.applicationContext as PastelleApp
    return viewModel(factory = viewModelFactory { initializer { ImportViewModel(app.repository, app.timingEngine) } })
}
