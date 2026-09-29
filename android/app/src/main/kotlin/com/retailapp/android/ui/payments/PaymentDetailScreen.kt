@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.retailapp.android.ui.payments

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Card
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.retailapp.android.data.model.PaymentDetail
import com.retailapp.android.data.remote.AttachmentEntity
import com.retailapp.android.data.remote.NetworkModule
import com.retailapp.android.session.Session
import com.retailapp.android.ui.common.AttachmentsSection
import com.retailapp.android.ui.common.ErrorBox
import com.retailapp.android.ui.common.LoadingBox
import com.retailapp.android.ui.common.Terms
import com.retailapp.android.ui.common.displayDate
import com.retailapp.android.ui.common.displayDateTime
import com.retailapp.android.ui.common.money
import kotlinx.coroutines.launch

class PaymentDetailViewModel(private val id: Int) : ViewModel() {
    class Factory(private val id: Int) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T = PaymentDetailViewModel(id) as T
    }

    var payment by mutableStateOf<PaymentDetail?>(null)
        private set
    var isLoading by mutableStateOf(true)
        private set
    var errorMessage by mutableStateOf<String?>(null)
        private set

    init {
        load()
    }

    fun load() {
        viewModelScope.launch {
            isLoading = true
            errorMessage = null
            NetworkModule.safeCall { NetworkModule.paymentApi.getPayment(id) }
                .onSuccess { payment = it }
                .onFailure { errorMessage = it.message }
            isLoading = false
        }
    }
}

@Composable
fun PaymentDetailScreen(id: Int, onBack: () -> Unit, onOpenPhoto: (index: Int, title: String) -> Unit) {
    val viewModel: PaymentDetailViewModel = viewModel(key = "payment-$id", factory = PaymentDetailViewModel.Factory(id))
    val payment = viewModel.payment

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Payment") },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back") }
                },
            )
        },
    ) { padding ->
        when {
            viewModel.isLoading -> LoadingBox(modifier = Modifier.padding(padding))
            viewModel.errorMessage != null && payment == null ->
                ErrorBox(viewModel.errorMessage!!, onRetry = viewModel::load, modifier = Modifier.padding(padding))
            payment != null -> {
                val collected = payment.PartyType == "shop"
                val title = "${if (collected) "Collected from" else "Paid to"} ${payment.PartyName} · ${money(payment.Amount)}"
                LazyColumn(
                    modifier = Modifier.fillMaxSize().padding(padding).padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    item {
                        Card(modifier = Modifier.fillMaxWidth()) {
                            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Text(
                                    if (collected) "Collected from ${Terms.CUSTOMER.lowercase()}" else "Paid to ${Terms.SUPPLIER.lowercase()}",
                                    style = MaterialTheme.typography.labelLarge,
                                )
                                Text(payment.PartyName, style = MaterialTheme.typography.titleLarge)
                                Text(money(payment.Amount), style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
                                HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                                DetailLine("Mode", payment.PaymentMode)
                                DetailLine("Date", displayDate(payment.PaymentDate))
                                DetailLine("Recorded", displayDateTime(payment.CreatedAt))
                                if (!payment.Notes.isNullOrBlank()) DetailLine("Notes", payment.Notes)
                            }
                        }
                    }
                    item {
                        AttachmentsSection(
                            entity = AttachmentEntity.PAYMENT,
                            entityId = payment.ID,
                            canAdd = if (collected) Session.canSell else Session.canPurchase,
                            onOpenPhoto = { index -> onOpenPhoto(index, title) },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun DetailLine(label: String, value: String) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.bodyMedium)
    }
}
