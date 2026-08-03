package com.example.pantryparty

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Kitchen
import androidx.compose.material.icons.filled.Restaurant
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import com.example.pantryparty.data.PantryDao
import com.example.pantryparty.data.PantryDatabase
import com.example.pantryparty.ui.PantryScreen
import com.example.pantryparty.ui.RecipeScreen
import com.example.pantryparty.ui.theme.PantryPartyTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val dao = PantryDatabase.getInstance(this).pantryDao()
        setContent {
            PantryPartyTheme {
                MainScaffold(dao = dao)
            }
        }
    }
}

/** Top-level screens reachable from the bottom navigation bar. */
private enum class Tab(val label: String, val icon: ImageVector) {
    PANTRY("Pantry", Icons.Filled.Kitchen),
    RECIPES("Recipes", Icons.Filled.Restaurant)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScaffold(dao: PantryDao) {
    var tab by remember { mutableStateOf(Tab.PANTRY) }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        topBar = {
            // Branded title bar; shows the active tab's name.
            CenterAlignedTopAppBar(
                title = {
                    Text(
                        "Pantry Party",
                        fontWeight = FontWeight.Bold
                    )
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    titleContentColor = MaterialTheme.colorScheme.onPrimaryContainer
                )
            )
        },
        bottomBar = {
            NavigationBar {
                Tab.entries.forEach { t ->
                    NavigationBarItem(
                        selected = tab == t,
                        onClick = { tab = t },
                        icon = { Icon(t.icon, contentDescription = t.label) },
                        label = { Text(t.label) }
                    )
                }
            }
        }
    ) { innerPadding ->
        // Each tab manages its own scrolling content.
        Box(
            modifier = Modifier
                .padding(innerPadding)
                .fillMaxSize()
        ) {
            when (tab) {
                Tab.PANTRY -> PantryScreen(dao = dao)
                Tab.RECIPES -> RecipeScreen(dao = dao)
            }
        }
    }
}
