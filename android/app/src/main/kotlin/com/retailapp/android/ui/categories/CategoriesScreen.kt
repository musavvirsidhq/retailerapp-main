package com.retailapp.android.ui.categories

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.retailapp.android.data.model.Category
import com.retailapp.android.ui.common.ErrorBox
import com.retailapp.android.ui.common.LoadingBox

@Composable
fun CategoriesScreen(viewModel: CategoriesViewModel = viewModel()) {
    var showAddCategoryDialog by remember { mutableStateOf(false) }
    var addSubcategoryForId by remember { mutableStateOf<Int?>(null) }

    Scaffold(
        floatingActionButton = {
            FloatingActionButton(onClick = { showAddCategoryDialog = true }) {
                Icon(Icons.Default.Add, contentDescription = "Add category")
            }
        },
    ) { padding ->
        when {
            viewModel.isLoading -> LoadingBox(modifier = Modifier.padding(padding))
            viewModel.errorMessage != null && viewModel.categories.isEmpty() ->
                ErrorBox(viewModel.errorMessage!!, onRetry = viewModel::load, modifier = Modifier.padding(padding))
            else -> CategoryList(
                categories = viewModel.categories,
                viewModel = viewModel,
                padding = padding,
                onAddSubcategory = { addSubcategoryForId = it },
            )
        }
    }

    if (showAddCategoryDialog) {
        NameInputDialog(
            title = "New category",
            isSubmitting = viewModel.isSubmitting,
            onDismiss = { showAddCategoryDialog = false },
            onConfirm = { name -> viewModel.addCategory(name) { ok -> if (ok) showAddCategoryDialog = false } },
        )
    }

    addSubcategoryForId?.let { categoryId ->
        NameInputDialog(
            title = "New subcategory",
            isSubmitting = viewModel.isSubmitting,
            onDismiss = { addSubcategoryForId = null },
            onConfirm = { name -> viewModel.addSubcategory(categoryId, name) { ok -> if (ok) addSubcategoryForId = null } },
        )
    }
}

@Composable
private fun CategoryList(
    categories: List<Category>,
    viewModel: CategoriesViewModel,
    padding: PaddingValues,
    onAddSubcategory: (Int) -> Unit,
) {
    if (categories.isEmpty()) {
        Box(modifier = Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
            Text("No categories yet. Tap + to add one.")
        }
        return
    }
    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(padding).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        items(categories, key = { it.ID }) { category ->
            val expanded = viewModel.expandedCategoryId == category.ID
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(category.Name, style = MaterialTheme.typography.titleMedium)
                        IconButton(onClick = { viewModel.toggleExpand(category.ID) }) {
                            Icon(
                                if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                                contentDescription = "Toggle subcategories",
                            )
                        }
                    }
                    if (expanded) {
                        val subcategories = viewModel.subcategoriesByCategory[category.ID].orEmpty()
                        Column(modifier = Modifier.padding(start = 8.dp, top = 8.dp)) {
                            if (subcategories.isEmpty()) {
                                Text("No subcategories", style = MaterialTheme.typography.bodySmall)
                            } else {
                                subcategories.forEach { sub -> Text("• ${sub.Name}") }
                            }
                            TextButton(onClick = { onAddSubcategory(category.ID) }) {
                                Text("+ Add subcategory")
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun NameInputDialog(
    title: String,
    isSubmitting: Boolean,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit,
) {
    var name by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text("Name") }, singleLine = true)
        },
        confirmButton = {
            TextButton(enabled = !isSubmitting, onClick = { onConfirm(name) }) { Text("Save") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        },
    )
}
