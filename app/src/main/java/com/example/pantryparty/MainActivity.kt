package com.example.pantryparty

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.pantryparty.data.PantryDao
import com.example.pantryparty.data.PantryDatabase
import com.example.pantryparty.data.PantryItem
import com.example.pantryparty.ui.theme.PantryPartyTheme
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val dao = PantryDatabase.getInstance(this).pantryDao()
        setContent {
            PantryPartyTheme {
                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    PantryTestScreen(
                        dao = dao,
                        modifier = Modifier.padding(innerPadding)
                    )
                }
            }
        }
    }
}

/** Quick scratch screen to exercise Room: add items, see them persist, delete them. */
@Composable
fun PantryTestScreen(dao: PantryDao, modifier: Modifier = Modifier) {
    val scope = rememberCoroutineScope()
    val items by dao.observeAll().collectAsState(initial = emptyList())

    var name by remember { mutableStateOf("") }
    var quantity by remember { mutableStateOf("1") }

    Column(modifier = modifier.fillMaxSize().padding(16.dp)) {
        Text("Pantry (Room test)", style = MaterialTheme.typography.headlineSmall)
        Spacer(Modifier.height(12.dp))

        OutlinedTextField(
            value = name,
            onValueChange = { name = it },
            label = { Text("Item name") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(Modifier.height(8.dp))
        OutlinedTextField(
            value = quantity,
            onValueChange = { input -> quantity = input.filter { it.isDigit() } },
            label = { Text("Quantity") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(Modifier.height(8.dp))
        Button(
            onClick = {
                val trimmed = name.trim()
                if (trimmed.isNotEmpty()) {
                    val qty = quantity.toIntOrNull() ?: 1
                    scope.launch { dao.upsert(PantryItem(name = trimmed, quantity = qty)) }
                    name = ""
                    quantity = "1"
                }
            },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Add item")
        }

        Spacer(Modifier.height(16.dp))
        HorizontalDivider()
        Spacer(Modifier.height(8.dp))

        if (items.isEmpty()) {
            Text("No items yet — add one above.")
        } else {
            LazyColumn {
                items(items, key = { it.id }) { item ->
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)
                    ) {
                        Text(
                            text = "${item.name}  ×${item.quantity}",
                            modifier = Modifier.weight(1f)
                        )
                        TextButton(onClick = { scope.launch { dao.delete(item) } }) {
                            Text("Delete")
                        }
                    }
                }
            }
        }
    }
}
