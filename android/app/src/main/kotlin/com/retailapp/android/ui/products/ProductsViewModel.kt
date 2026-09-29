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
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch

class ProductsViewModel : ViewModel() {
    private val productApi = NetworkModule.productApi
    private val categoryApi = NetworkModule.categoryApi
    private val unitApi = NetworkModule.unitApi

    var products by mutableStateOf<List<Product>>(emptyList())
        private set
    var categories by mutableStateOf<List<Category>>(emptyList())
        private set
    var subcategories by mutableStateOf<List<Subcategory>>(emptyList())
        private set
    var units by mutableStateOf<List<UnitDto>>(emptyList())
        private set
    var isLoading by mutableStateOf(true)
        private set
    var errorMessage by mutableStateOf<String?>(null)
        private set
    var isSubmitting by mutableStateOf(false)
        private set

    init {
        load()
    }

    fun load() {
        viewModelScope.launch {
            isLoading = true
            errorMessage = null
            coroutineScope {
                val productsDeferred = async { NetworkModule.safeCall { productApi.listProducts() } }
                val categoriesDeferred = async { NetworkModule.safeCall { categoryApi.listCategories() } }
                val unitsDeferred = async { NetworkModule.safeCall { unitApi.listUnits() } }

                productsDeferred.await().onSuccess { products = it }.onFailure { errorMessage = it.message }
                categoriesDeferred.await().onSuccess { categories = it }
                unitsDeferred.await().onSuccess { units = it }
            }
            isLoading = false
        }
    }

    fun categoryName(categoryId: Int) = categories.find { it.ID == categoryId }?.Name ?: "Unknown"

    fun loadSubcategories(categoryId: Int) {
        viewModelScope.launch {
            NetworkModule.safeCall { categoryApi.listSubcategories(categoryId) }
                .onSuccess { subcategories = it }
                .onFailure { subcategories = emptyList() }
        }
    }

    fun addCategory(name: String, onDone: (Category?) -> Unit) {
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

    fun addSubcategory(categoryId: Int, name: String, onDone: (Subcategory?) -> Unit) {
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

    fun addProduct(input: ProductInput, onDone: (Boolean) -> Unit) {
        viewModelScope.launch {
            isSubmitting = true
            errorMessage = null
            NetworkModule.safeCall { productApi.createProduct(input) }
                .onSuccess { created ->
                    products = products + created
                    onDone(true)
                }
                .onFailure {
                    errorMessage = it.message
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
                .onSuccess { products = products.map { if (it.ID == product.ID) it.copy(Pinned = pin) else it } }
                .onFailure { Toast.makeText(RetailApp.instance, it.message, Toast.LENGTH_LONG).show() }
        }
    }

    fun deleteProduct(id: Int) {
        viewModelScope.launch {
            NetworkModule.safeCall { productApi.deleteProduct(id) }
                .onSuccess { products = products.filter { it.ID != id } }
                .onFailure { errorMessage = it.message }
        }
    }
}
