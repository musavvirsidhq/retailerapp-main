@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.retailapp.android.ui.ledger

import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.retailapp.android.data.model.FactoryInput
import com.retailapp.android.data.model.ShopUpdate
import com.retailapp.android.data.remote.NetworkModule
import com.retailapp.android.ui.common.DataChanges
import com.retailapp.android.ui.common.DiscardDialog
import com.retailapp.android.ui.common.ErrorBox
import com.retailapp.android.ui.common.InlineError
import com.retailapp.android.ui.common.LoadingBox
import com.retailapp.android.ui.common.money
import kotlinx.coroutines.launch

/** The editable contact details shared by customers and suppliers. [place] is Area or Address. */
data class PartyFields(
    val name: String = "",
    val contactName: String = "",
    val primaryPhone: String = "",
    val secondaryPhone: String = "",
    val place: String = "",
)

class EditPartyViewModel(private val kind: PartyKind, private val id: Int) : ViewModel() {
    class Factory(private val kind: PartyKind, private val id: Int) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T = EditPartyViewModel(kind, id) as T
    }

    var original by mutableStateOf<PartyFields?>(null)
        private set
    var fields by mutableStateOf(PartyFields())
    var openingBalance by mutableStateOf<String?>(null)
        private set
    var isLoading by mutableStateOf(true)
        private set
    var isSaving by mutableStateOf(false)
        private set
    var errorMessage by mutableStateOf<String?>(null)
        private set

    val isDirty: Boolean get() = original != null && fields != original

    init {
        load()
    }

    fun load() {
        viewModelScope.launch {
            isLoading = true
            errorMessage = null
            val result = if (kind == PartyKind.CUSTOMER) {
                NetworkModule.safeCall { NetworkModule.shopApi.getShop(id) }.map { shop ->
                    openingBalance = shop.OpeningBalance
                    PartyFields(shop.Name, shop.OwnerName.orEmpty(), shop.PrimaryPhone, shop.SecondaryPhone.orEmpty(), shop.Area.orEmpty())
                }
            } else {
                NetworkModule.safeCall { NetworkModule.factoryApi.getFactory(id) }.map { factory ->
                    PartyFields(factory.Name, factory.ContactPerson.orEmpty(), factory.PrimaryPhone, factory.SecondaryPhone.orEmpty(), factory.Address.orEmpty())
                }
            }
            result
                .onSuccess {
                    original = it
                    fields = it
                }
                .onFailure { errorMessage = it.message }
            isLoading = false
        }
    }

    fun save(onDone: () -> Unit) {
        val f = fields
        viewModelScope.launch {
            isSaving = true
            errorMessage = null
            val result = if (kind == PartyKind.CUSTOMER) {
                NetworkModule.safeCall {
                    NetworkModule.shopApi.updateShop(
                        id,
                        ShopUpdate(f.name.trim(), f.contactName.trim(), f.primaryPhone.trim(), f.secondaryPhone.trim(), f.place.trim()),
                    )
                }.map { }
            } else {
                NetworkModule.safeCall {
                    NetworkModule.factoryApi.updateFactory(
                        id,
                        FactoryInput(f.name.trim(), f.contactName.trim(), f.primaryPhone.trim(), f.secondaryPhone.trim(), f.place.trim()),
                    )
                }.map { }
            }
            result
                .onSuccess {
                    DataChanges.bump()
                    onDone()
                }
                .onFailure { errorMessage = it.message }
            isSaving = false
        }
    }
}

/**
 * Cycle 5 section 4: edit a customer's or supplier's contact details, opened from the ledger's
 * ⋮ menu. The opening balance is shown but not editable - it is financial history, so a
 * correction goes through a payment or a note instead.
 */
@Composable
fun EditPartyScreen(kind: PartyKind, id: Int, onBack: () -> Unit) {
    val viewModel: EditPartyViewModel = viewModel(key = "edit-${kind.name}-$id", factory = EditPartyViewModel.Factory(kind, id))
    val context = LocalContext.current
    var confirmDiscard by remember { mutableStateOf(false) }
    val requestBack = { if (viewModel.isDirty) confirmDiscard = true else onBack() }
    BackHandler(enabled = !viewModel.isSaving) { requestBack() }
    if (confirmDiscard) DiscardDialog(onDiscard = { confirmDiscard = false; onBack() }, onKeep = { confirmDiscard = false })

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Edit ${kind.singular.lowercase()}") },
                navigationIcon = {
                    IconButton(onClick = requestBack, enabled = !viewModel.isSaving) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
            )
        },
    ) { padding ->
        when {
            viewModel.isLoading -> LoadingBox(modifier = Modifier.padding(padding))
            viewModel.original == null ->
                ErrorBox(viewModel.errorMessage ?: "Couldn't load.", onRetry = viewModel::load, modifier = Modifier.padding(padding))
            else -> {
                val f = viewModel.fields
                fun update(transform: (PartyFields) -> PartyFields) {
                    viewModel.fields = transform(viewModel.fields)
                }
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(padding)
                        .imePadding()
                        .verticalScroll(rememberScrollState())
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    OutlinedTextField(
                        value = f.name,
                        onValueChange = { v -> update { it.copy(name = v) } },
                        label = { Text("Name") },
                        isError = f.name.isBlank(),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    OutlinedTextField(
                        value = f.contactName,
                        onValueChange = { v -> update { it.copy(contactName = v) } },
                        label = { Text("Contact name") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    OutlinedTextField(
                        value = f.primaryPhone,
                        onValueChange = { v -> update { it.copy(primaryPhone = v) } },
                        label = { Text("Primary phone") },
                        isError = f.primaryPhone.isBlank(),
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                        modifier = Modifier.fillMaxWidth(),
                    )
                    OutlinedTextField(
                        value = f.secondaryPhone,
                        onValueChange = { v -> update { it.copy(secondaryPhone = v) } },
                        label = { Text("Secondary phone") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                        modifier = Modifier.fillMaxWidth(),
                    )
                    OutlinedTextField(
                        value = f.place,
                        onValueChange = { v -> update { it.copy(place = v) } },
                        label = { Text(if (kind == PartyKind.CUSTOMER) "Area" else "Address") },
                        singleLine = kind == PartyKind.CUSTOMER,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    viewModel.openingBalance?.let { opening ->
                        OutlinedTextField(
                            value = money(opening),
                            onValueChange = {},
                            enabled = false,
                            label = { Text("Opening balance") },
                            supportingText = { Text("Can't be edited. Record a payment or a note to correct it.") },
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                    InlineError(viewModel.errorMessage)
                    Button(
                        enabled = !viewModel.isSaving && viewModel.isDirty && f.name.isNotBlank() && f.primaryPhone.isNotBlank(),
                        onClick = {
                            viewModel.save {
                                Toast.makeText(context, "${kind.singular} saved", Toast.LENGTH_SHORT).show()
                                onBack()
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        if (viewModel.isSaving) {
                            CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                        } else {
                            Text("Save changes")
                        }
                    }
                    if (!viewModel.isDirty) {
                        Text("Nothing changed yet.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Spacer(Modifier.height(16.dp))
                }
            }
        }
    }
}
