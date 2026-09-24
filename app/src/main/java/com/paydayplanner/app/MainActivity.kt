package com.paydayplanner.app

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import com.paydayplanner.app.ui.bills.BillsScreen
import com.paydayplanner.app.ui.bills.BillsViewModel
import com.paydayplanner.app.ui.home.HomeScreen
import com.paydayplanner.app.ui.home.HomeViewModel
import com.paydayplanner.app.ui.settings.SettingsScreen
import com.paydayplanner.app.ui.settings.SettingsViewModel
import com.paydayplanner.app.ui.theme.PaydayTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val container = (application as PaydayApp).container
        setContent {
            PaydayTheme { AppRoot(container) }
        }
    }
}

private enum class Tab(val label: String, val icon: ImageVector) {
    Budget("Budget", Icons.Filled.Home),
    Bills("Bills", Icons.Filled.DateRange),
    Settings("Settings", Icons.Filled.Settings),
}

@Composable
private fun AppRoot(container: AppContainer) {
    var tab by rememberSaveable { mutableStateOf(Tab.Budget) }
    RequestNotificationPermission()

    Scaffold(
        bottomBar = {
            NavigationBar {
                Tab.entries.forEach { t ->
                    NavigationBarItem(
                        selected = tab == t,
                        onClick = { tab = t },
                        icon = { Icon(t.icon, contentDescription = null) },
                        label = { Text(t.label) },
                    )
                }
            }
        },
    ) { padding ->
        Box(Modifier.padding(bottom = padding.calculateBottomPadding())) {
            when (tab) {
                Tab.Budget -> HomeScreen(viewModel { HomeViewModel(container) })
                Tab.Bills -> BillsScreen(viewModel { BillsViewModel(container) })
                Tab.Settings -> SettingsScreen(viewModel { SettingsViewModel(container) })
            }
        }
    }
}

@Composable
private fun RequestNotificationPermission() {
    if (Build.VERSION.SDK_INT < 33) return
    val context = LocalContext.current
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }
    LaunchedEffect(Unit) {
        val granted = ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) ==
            PackageManager.PERMISSION_GRANTED
        if (!granted) launcher.launch(Manifest.permission.POST_NOTIFICATIONS)
    }
}
