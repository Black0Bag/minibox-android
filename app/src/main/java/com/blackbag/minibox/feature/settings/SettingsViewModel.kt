package com.blackbag.minibox.feature.settings

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.blackbag.minibox.core.model.PermissionsData
import com.blackbag.minibox.core.model.ToolsData
import com.blackbag.minibox.core.network.MiniboxHttpClient
import com.blackbag.minibox.core.network.RestClient
import com.blackbag.minibox.core.security.CredentialStore
import com.blackbag.minibox.data.PermissionsRepository
import com.blackbag.minibox.data.ToolsRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * 设置屏 UI 状态：权限模式 + 工具列表。
 */
data class SettingsUiState(
    val loading: Boolean = true,
    val permissions: PermissionsData? = null,
    val tools: ToolsData? = null,
    val settingMode: Boolean = false,
    val error: String? = null,
) {
    /** yolo 是高风险模式（api.md §5：前端必须明显提示） */
    val isHighRiskMode: Boolean get() = permissions?.mode == "yolo"
}

/**
 * 设置 ViewModel：加载权限模式与工具列表，支持运行时切换模式。
 */
class SettingsViewModel(
    application: Application,
) : AndroidViewModel(application) {

    private val credentialStore = CredentialStore(application)

    private val _uiState = MutableStateFlow(SettingsUiState())
    val uiState: StateFlow<SettingsUiState> = _uiState.asStateFlow()

    private fun restClient(): RestClient? {
        val config = credentialStore.loadConnectionConfig() ?: return null
        return RestClient(MiniboxHttpClient.create(config), config)
    }

    fun load() {
        val rest = restClient() ?: run {
            _uiState.value = _uiState.value.copy(loading = false, error = "未配置连接，请先完成诊断")
            return
        }
        _uiState.value = _uiState.value.copy(loading = true, error = null)
        viewModelScope.launch {
            val permResult = PermissionsRepository(rest).get()
            val toolsResult = ToolsRepository(rest).list()

            val newState = when {
                permResult is RestClient.Result.Ok && toolsResult is RestClient.Result.Ok -> {
                    _uiState.value.copy(
                        loading = false,
                        permissions = permResult.value,
                        tools = toolsResult.value,
                    )
                }
                permResult is RestClient.Result.Unauthorized || toolsResult is RestClient.Result.Unauthorized -> {
                    _uiState.value.copy(loading = false, error = "凭据无效（401）")
                }
                permResult is RestClient.Result.HttpError -> {
                    _uiState.value.copy(loading = false, error = permResult.problem.detail)
                }
                toolsResult is RestClient.Result.HttpError -> {
                    // 权限加载成功但工具失败：仍展示权限区
                    if (permResult is RestClient.Result.Ok) {
                        _uiState.value.copy(
                            loading = false,
                            permissions = permResult.value,
                            error = "工具列表加载失败：${toolsResult.problem.detail}",
                        )
                    } else {
                        _uiState.value.copy(loading = false, error = toolsResult.problem.detail)
                    }
                }
                else -> {
                    val msg = (permResult as? RestClient.Result.NetworkFailure)?.message
                        ?: (toolsResult as? RestClient.Result.NetworkFailure)?.message
                        ?: "网络错误"
                    _uiState.value.copy(loading = false, error = msg)
                }
            }
            _uiState.value = newState
        }
    }

    /**
     * 切换权限模式。yolo 需 UI 层先二次确认，这里不做拦截。
     */
    fun setMode(mode: String) {
        val rest = restClient() ?: return
        _uiState.value = _uiState.value.copy(settingMode = true)
        viewModelScope.launch {
            when (val result = PermissionsRepository(rest).setMode(mode)) {
                is RestClient.Result.Ok -> {
                    _uiState.value = _uiState.value.copy(
                        settingMode = false,
                        permissions = result.value,
                    )
                }
                is RestClient.Result.HttpError -> {
                    _uiState.value = _uiState.value.copy(
                        settingMode = false,
                        error = result.problem.detail,
                    )
                }
                is RestClient.Result.Unauthorized -> {
                    _uiState.value = _uiState.value.copy(settingMode = false, error = "凭据无效（401）")
                }
                is RestClient.Result.NetworkFailure -> {
                    _uiState.value = _uiState.value.copy(settingMode = false, error = result.message)
                }
            }
        }
    }

    fun clearError() {
        _uiState.value = _uiState.value.copy(error = null)
    }
}
