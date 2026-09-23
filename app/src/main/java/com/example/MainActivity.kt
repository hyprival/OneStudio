package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoFixHigh
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Queue
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Smartphone
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.ui.AntiTamperScreen
import com.example.ui.AppDetailDialog
import com.example.ui.AppDrawerContent
import com.example.ui.BatchQueueScreen
import com.example.ui.DashboardScreen
import com.example.ui.HistoryScreen
import com.example.ui.MainViewModel
import com.example.ui.PatchHistoryScreen
import com.example.ui.QuickFixScreen
import com.example.ui.theme.MyApplicationTheme
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MyApplicationTheme {
                OneApkMainApp()
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OneApkMainApp(viewModel: MainViewModel = viewModel()) {
    val context = LocalContext.current
    val selectedTab by viewModel.selectedTab.collectAsStateWithLifecycle()
    val selectedAppForDetail by viewModel.selectedAppForDetail.collectAsStateWithLifecycle()

    val batchQueue by viewModel.batchQueue.collectAsStateWithLifecycle()
    val allPatchHistory by viewModel.allPatchHistory.collectAsStateWithLifecycle()
    val allModifiedApps by viewModel.allModifiedApps.collectAsStateWithLifecycle()
    val dashboardSecurity by viewModel.dashboardSecurity.collectAsStateWithLifecycle()

    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
    val coroutineScope = rememberCoroutineScope()

    val categoryTitles = listOf(
        "⚡ Fix for My Phone",
        "📦 Queue Patcher",
        "🕒 Patch History",
        "🛡️ Security & Anti-Tamper",
        "📱 My Patched Apps",
        "📊 System Compatibility"
    )

    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            AppDrawerContent(
                selectedCategory = selectedTab,
                queueCount = batchQueue.size,
                historyCount = allPatchHistory.size,
                appsCount = allModifiedApps.size,
                threatCount = dashboardSecurity.threatCount,
                onSelectCategory = { tabIndex ->
                    viewModel.selectTab(tabIndex)
                    coroutineScope.launch { drawerState.close() }
                },
                onClearCache = {
                    viewModel.clearInstallerCache(context)
                    coroutineScope.launch { drawerState.close() }
                }
            )
        }
    ) {
        Scaffold(
            topBar = {
                CenterAlignedTopAppBar(
                    title = {
                        Text(
                            text = categoryTitles.getOrElse(selectedTab) { "OneAPK" },
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                    },
                    navigationIcon = {
                        IconButton(
                            onClick = {
                                coroutineScope.launch {
                                    if (drawerState.isClosed) drawerState.open() else drawerState.close()
                                }
                            }
                        ) {
                            Icon(
                                imageVector = Icons.Default.Menu,
                                contentDescription = "Open Navigation Menu",
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }
                    },
                    actions = {
                        IconButton(
                            onClick = { viewModel.selectTab(0) }
                        ) {
                            Icon(
                                imageVector = Icons.Default.Smartphone,
                                contentDescription = "Device Info",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    },
                    colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                        containerColor = MaterialTheme.colorScheme.surface
                    )
                )
            },
            bottomBar = {
                NavigationBar(
                    containerColor = MaterialTheme.colorScheme.surface
                ) {
                    NavigationBarItem(
                        selected = selectedTab == 0,
                        onClick = { viewModel.selectTab(0) },
                        icon = { Icon(Icons.Default.AutoFixHigh, contentDescription = "Fix Phone") },
                        label = { Text("Fix for Phone") }
                    )
                    NavigationBarItem(
                        selected = selectedTab == 1,
                        onClick = { viewModel.selectTab(1) },
                        icon = { Icon(Icons.Default.Queue, contentDescription = "Queue") },
                        label = { Text("Queue") }
                    )
                    NavigationBarItem(
                        selected = selectedTab == 2,
                        onClick = { viewModel.selectTab(2) },
                        icon = { Icon(Icons.Default.History, contentDescription = "History") },
                        label = { Text("History") }
                    )
                    NavigationBarItem(
                        selected = selectedTab == 3,
                        onClick = { viewModel.selectTab(3) },
                        icon = { Icon(Icons.Default.Security, contentDescription = "Security") },
                        label = { Text("Security") }
                    )
                }
            }
        ) { innerPadding ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .background(MaterialTheme.colorScheme.background)
            ) {
                when (selectedTab) {
                    0 -> QuickFixScreen(
                        viewModel = viewModel,
                        onNavigateToQueue = { viewModel.selectTab(1) }
                    )
                    1 -> BatchQueueScreen(
                        viewModel = viewModel,
                        onNavigateToQuickFix = { viewModel.selectTab(0) }
                    )
                    2 -> PatchHistoryScreen(
                        viewModel = viewModel,
                        onNavigateToQuickFix = { viewModel.selectTab(0) }
                    )
                    3 -> AntiTamperScreen(viewModel = viewModel)
                    4 -> HistoryScreen(viewModel = viewModel)
                    5 -> DashboardScreen(
                        viewModel = viewModel,
                        onNavigateToPatch = { viewModel.selectTab(0) },
                        onNavigateToBatch = { viewModel.selectTab(1) }
                    )
                    else -> QuickFixScreen(
                        viewModel = viewModel,
                        onNavigateToQueue = { viewModel.selectTab(1) }
                    )
                }
            }
        }
    }

    // Detail Bottom Sheet Dialog
    if (selectedAppForDetail != null) {
        AppDetailDialog(
            app = selectedAppForDetail,
            onDismiss = { viewModel.openAppDetail(null) },
            onInstall = { file -> viewModel.installApk(context, file) },
            onShare = { file -> viewModel.shareApk(context, file) },
            onAuditAntiTamper = { file, hash ->
                viewModel.selectTab(3)
                viewModel.openAppDetail(null)
                viewModel.runAntiTamperCheck(file, hash)
            },
            onExportLog = { app -> viewModel.exportAppDiagnosticLogs(context, app) },
            onDelete = { id -> viewModel.deleteAppRecord(id) }
        )
    }
}
