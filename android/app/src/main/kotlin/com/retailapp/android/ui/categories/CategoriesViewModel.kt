package com.retailapp.android.ui.categories

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.retailapp.android.data.model.Category
import com.retailapp.android.data.model.CreateCategoryRequest
import com.retailapp.android.data.model.CreateSubcategoryRequest
import com.retailapp.android.data.model.Subcategory
import com.retailapp.android.data.remote.NetworkModule
import kotlinx.coroutines.launch

class CategoriesViewModel : ViewModel() {
    private val api = NetworkModule.categoryApi

    var categories by mutableStateOf<List<Category>>(emptyList())
        private set
    var isLoading by mutableStateOf(true)
        private set
    var errorMessage by mutableStateOf<String?>(null)
        private set
    var isSubmitting by mutableStateOf(false)
        private set

    var expandedCategoryId by mutableStateOf<Int?>(null)
        private set
    val subcategoriesByCategory = mutableStateMapOf<Int, List<Subcategory>>()

    init {
        load()
    }

    fun load() {
        viewModelScope.launch {
            isLoading = true
            errorMessage = null
            NetworkModule.safeCall { api.listCategories() }
                .onSuccess { categories = it }
                .onFailure { errorMessage = it.message }
            isLoading = false
        }
    }

    fun toggleExpand(categoryId: Int) {
        expandedCategoryId = if (expandedCategoryId == categoryId) null else categoryId
        if (expandedCategoryId != null && !subcategoriesByCategory.containsKey(categoryId)) {
            viewModelScope.launch {
                NetworkModule.safeCall { api.listSubcategories(categoryId) }
                    .onSuccess { subcategoriesByCategory[categoryId] = it }
            }
        }
    }

    fun addCategory(name: String, onDone: (Boolean) -> Unit) {
        if (name.isBlank()) return
        viewModelScope.launch {
            isSubmitting = true
            errorMessage = null
            NetworkModule.safeCall { api.createCategory(CreateCategoryRequest(name.trim())) }
                .onSuccess {
                    load()
                    onDone(true)
                }
                .onFailure {
                    errorMessage = it.message
                    onDone(false)
                }
            isSubmitting = false
        }
    }

    fun addSubcategory(categoryId: Int, name: String, onDone: (Boolean) -> Unit) {
        if (name.isBlank()) return
        viewModelScope.launch {
            isSubmitting = true
            errorMessage = null
            NetworkModule.safeCall { api.createSubcategory(categoryId, CreateSubcategoryRequest(name.trim())) }
                .onSuccess { created ->
                    subcategoriesByCategory[categoryId] = (subcategoriesByCategory[categoryId] ?: emptyList()) + created
                    onDone(true)
                }
                .onFailure {
                    errorMessage = it.message
                    onDone(false)
                }
            isSubmitting = false
        }
    }
}
