package com.retailapp.android.ui.products

import android.widget.Toast
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.retailapp.android.RetailApp
import com.retailapp.android.data.model.Category
import com.retailapp.android.data.model.CreateCategoryRequest
import com.retailapp.android.data.model.CreateSubcategoryRequest
import com.retailapp.android.data.model.Product
import com.retailapp.android.data.model.ProductInput
import com.retailapp.android.data.model.Subcategory
import com.retailapp.android.data.model.UnitDto
import com.retailapp.android.data.remote.NetworkModule
import com.retailapp.android.ui.common.DataChanges
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch

/** Category/subcategory/unit lists and "+ New category" for the product form; shared with EditProductViewModel. */
open class ProductFormViewModel : ViewModel(), ProductFormSource {
    protected val productApi = NetworkModule.productApi
    protected val categoryApi = NetworkModule.categoryApi
    protected val unitApi = NetworkModule.unitApi

    final override var categories by mutableStateOf<List<Category>>(emptyList())
        protected set
    final override var subcategories by mutableStateOf<List<Subcategory>>(emptyList())
        private set
    final override var units by mutableStateOf<List<UnitDto>>(emptyList())
        protected set
    var errorMessage by mutableStateOf<String?>(null)
        protected set

    fun categoryName(categoryId: Int) = categories.find { it.ID == categoryId }?.Name ?: "Unknown"

    protected suspend fun loadLookups() = coroutineScope {
        val categoriesDeferred = async { NetworkModule.safeCall { categoryApi.listCategories() } }
        val unitsDeferred = async { NetworkModule.safeCall { unitApi.listUnits() } }
        categoriesDeferred.await().onSuccess { categories = it }
        unitsDeferred.await().onSuccess { units = it }
    }

    final override fun loadSubcategories(categoryId: Int) {
        viewModelScope.launch {
            NetworkModule.safeCall { categoryApi.listSubcategories(categoryId) }
                .onSuccess { subcategories = it }
                .onFailure { subcategories = emptyList() }
        }
    }

    final override fun addCategory(name: String, onDone: (Category?) -> Unit) {
        viewModelScope.launch {
            NetworkModule.safeCall { categoryApi.createCategory(CreateCategoryRequest(name)) }
                .onSuccess { created ->
                    categories = categories + created
                    onDone(created)
                }
                .onFailure {
                    errorMessage = it.message
                    onDone(null)
                }
        }
    }

    final override fun addSubcategory(categoryId: Int, name: String, onDone: (Subcategory?) -> Unit) {
        viewModelScope.launch {
            NetworkModule.safeCall { categoryApi.createSubcategory(categoryId, CreateSubcategoryRequest(name)) }
                .onSuccess { created ->
                    subcategories = subcategories + created
                    onDone(created)
                }
                .onFailure {
                    errorMessage = it.message
                    onDone(null)
                }
        }
    }
}

class ProductsViewModel : ProductFormViewModel() {
    var products by mutableStateOf<List<Product>>(emptyList())
        private set
    var isLoading by mutableStateOf(true)
        private set
    var isRefreshing by mutableStateOf(false)
        private set
    var isSubmitting by mutableStateOf(false)
        private set

    /** Cycle 5 "Show archived": archived products are listed greyed, with Restore. */
    var showArchived by mutableStateOf(false)
        private set

    private var seenVersion = -1

    val visibleProducts: List<Product>
        get() = if (showArchived) products.sortedBy { it.isArchived } else products.filter { !it.isArchived }

    init {
        load()
    }

    fun load(pull: Boolean = false) {
        viewModelScope.launch {
            seenVersion = DataChanges.version
            if (pull) isRefreshing = true else isLoading = products.isEmpty()
            errorMessage = null
            coroutineScope {
                val productsDeferred = async { NetworkModule.safeCall { productApi.listProducts(includeArchived = showArchived.takeIf { it }) } }
                val lookups = async { loadLookups() }
                productsDeferred.await().onSuccess { products = it }.onFailure { errorMessage = it.message }
                lookups.await()
            }
            isLoading = false
            isRefreshing = false
        }
    }

    fun refreshIfChanged() {
        if (seenVersion != DataChanges.version) load()
    }

    fun toggleShowArchived() {
        showArchived = !showArchived
        load()
    }

    fun clearError() {
        errorMessage = null
    }

    fun addProduct(input: ProductInput, onDone: (Boolean) -> Unit) {
        viewModelScope.launch {
            isSubmitting = true
            errorMessage = null
            NetworkModule.safeCall { productApi.createProduct(input) }
                .onSuccess { created ->
                    products = products + created
                    DataChanges.bump()
                    seenVersion = DataChanges.version
                    onDone(true)
                }
                .onFailure {
                    errorMessage = if (isSkuConflict(it.message)) archivedSkuMessage(input.sku) ?: it.message else it.message
                    onDone(false)
                }
            isSubmitting = false
        }
    }

    /**
     * Pins/unpins an item for the sale and purchase "most used" chips (Company Admin only; the
     * backend caps it at 10). Failures are toasted since the list has no inline error slot.
     */
    fun togglePin(product: Product) {
        viewModelScope.launch {
            val pin = !product.Pinned
            NetworkModule.safeCall { if (pin) productApi.pin(product.ID) else productApi.unpin(product.ID) }
                .onSuccess {
                    products = products.map { if (it.ID == product.ID) it.copy(Pinned = pin) else it }
                    DataChanges.bump()
                    seenVersion = DataChanges.version
                }
                .onFailure { Toast.makeText(RetailApp.instance, it.message, Toast.LENGTH_LONG).show() }
        }
    }

    /** Company Admin only; the backend refuses anyone else. */
    fun restore(product: Product) {
        viewModelScope.launch {
            NetworkModule.safeCall { productApi.restoreProduct(product.ID) }
                .onSuccess {
                    DataChanges.bump()
                    Toast.makeText(RetailApp.instance, "${product.Name} restored", Toast.LENGTH_SHORT).show()
                    load()
                }
                .onFailure { Toast.makeText(RetailApp.instance, "Couldn't restore: ${it.message}", Toast.LENGTH_LONG).show() }
        }
    }
}
