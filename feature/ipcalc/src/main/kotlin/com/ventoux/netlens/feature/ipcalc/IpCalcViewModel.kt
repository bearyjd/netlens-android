package com.ventoux.netlens.feature.ipcalc

import androidx.lifecycle.ViewModel
import com.ventoux.netlens.feature.ipcalc.engine.SubnetCalculator
import com.ventoux.netlens.feature.ipcalc.model.IpCalcUiState
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import javax.inject.Inject

@HiltViewModel
class IpCalcViewModel @Inject constructor(
    private val calculator: SubnetCalculator,
) : ViewModel() {

    private val _state = MutableStateFlow(IpCalcUiState())
    val state: StateFlow<IpCalcUiState> = _state.asStateFlow()

    fun onInputChange(input: String) {
        _state.update { it.copy(input = input, error = null) }
    }

    fun calculate() {
        val input = _state.value.input.trim()
        if (input.isEmpty()) {
            _state.update { it.copy(error = "Enter an IP address or CIDR") }
            return
        }
        try {
            val result = calculator.calculate(input)
            _state.update { it.copy(result = result, error = null) }
        } catch (e: IllegalArgumentException) {
            _state.update { it.copy(result = null, error = e.message) }
        }
    }
}
