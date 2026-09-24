package com.paydayplanner.app.ui.bills

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.paydayplanner.app.AppContainer
import com.paydayplanner.app.data.Bill
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class BillsViewModel(container: AppContainer) : ViewModel() {
    private val billDao = container.db.billDao()

    val bills: StateFlow<List<Bill>?> = billDao.all()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    val currency: StateFlow<String> = container.settings.settings.map { it.currency }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), "")

    fun save(bill: Bill) {
        viewModelScope.launch { billDao.upsert(bill) }
    }

    /** Past payments stay in your expenses; only the bill itself is removed. */
    fun delete(bill: Bill) {
        viewModelScope.launch { billDao.delete(bill) }
    }
}
