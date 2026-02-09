package io.github.jwtiyar.simplertask

import android.Manifest
import android.content.Context
import android.content.res.Configuration
import android.os.Build
import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import android.view.View
import androidx.activity.OnBackPressedCallback
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.GravityCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.google.android.material.snackbar.Snackbar
import dagger.hilt.android.AndroidEntryPoint
import io.github.jwtiyar.simplertask.data.backup.BackupManager
import io.github.jwtiyar.simplertask.databinding.ActivityMainBinding
import io.github.jwtiyar.simplertask.service.NotificationHelper
import io.github.jwtiyar.simplertask.ui.MainActivityBackupDelegate
import io.github.jwtiyar.simplertask.ui.MainUiDelegate
import io.github.jwtiyar.simplertask.ui.NavigationDelegate
import io.github.jwtiyar.simplertask.ui.SearchDelegate
import io.github.jwtiyar.simplertask.ui.UiEvent
import io.github.jwtiyar.simplertask.ui.dialogs.TaskDialogManager
import io.github.jwtiyar.simplertask.utils.PermissionManager
import io.github.jwtiyar.simplertask.viewmodel.TaskViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
@OptIn(ExperimentalCoroutinesApi::class, FlowPreview::class)
class MainActivity : AppCompatActivity() {
    private lateinit var binding: ActivityMainBinding
    
    @Inject
    lateinit var notificationHelper: NotificationHelper
    
    @Inject
    lateinit var backupManager: BackupManager
    
    private lateinit var dialogManager: TaskDialogManager
    private val taskViewModel: TaskViewModel by viewModels()
    private lateinit var permissionManager: PermissionManager
    private lateinit var backupDelegate: MainActivityBackupDelegate

    // Delegates for different responsibilities
    private lateinit var uiDelegate: MainUiDelegate
    private lateinit var navigationDelegate: NavigationDelegate
    private lateinit var searchDelegate: SearchDelegate

    // Modern permission launcher using ActivityResultContracts
    private val notificationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            Snackbar.make(binding.root, getString(R.string.notification_permission_granted), Snackbar.LENGTH_SHORT).show()
        } else {
            Snackbar.make(binding.root, getString(R.string.notification_permission_denied), Snackbar.LENGTH_LONG).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        WindowCompat.setDecorFitsSystemWindows(window, false)

        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        setSupportActionBar(binding.topAppBar)

        dialogManager = TaskDialogManager(this)
        permissionManager = PermissionManager(this)
        backupDelegate = MainActivityBackupDelegate(this, taskViewModel, backupManager)

        // Initialize delegates
        uiDelegate = MainUiDelegate(this, binding, taskViewModel, notificationHelper, dialogManager)
        navigationDelegate = NavigationDelegate(this, binding, taskViewModel, backupDelegate, notificationHelper, uiDelegate)
        searchDelegate = SearchDelegate(this, binding, taskViewModel, uiDelegate)

        // Apply window insets
        ViewCompat.setOnApplyWindowInsetsListener(binding.root) { _, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            val density = resources.displayMetrics.density
            val isLandscape = resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
            val baseFabBottomMarginDp = if (isLandscape) 96 else 112
            val baseFabBottomMarginPx = (baseFabBottomMarginDp * density).toInt()

            (binding.topAppBar.parent as? View)?.setPadding(0, systemBars.top, 0, 0)
            binding.topAppBar.setPadding(0, 0, 0, 0)

            val fabLp = (binding.fabAddTask.layoutParams as? android.view.ViewGroup.MarginLayoutParams)
            fabLp?.let { lp ->
                lp.bottomMargin = systemBars.bottom + baseFabBottomMarginPx
                binding.fabAddTask.layoutParams = lp
            }
            insets
        }

        binding.fabAddTask.post { binding.root.requestApplyInsets() }

        observeViewModel()
        requestNotificationPermission()
        permissionManager.checkAndRequestExactAlarmPermission(binding.root)

        // Setup UI through delegates
        uiDelegate.setupViewPager()
        uiDelegate.setupButtons()
        uiDelegate.setupSearchBar()
        navigationDelegate.setupNavigationDrawer()
        searchDelegate.observeSearchResults()
        setupBackPressHandler()
    }

    private fun observeViewModel() {
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch {
                    taskViewModel.events.collect { event ->
                        when (event) {
                            is UiEvent.ShowToast -> {
                                android.widget.Toast.makeText(this@MainActivity, event.message, android.widget.Toast.LENGTH_SHORT).show()
                            }
                            is UiEvent.ShowSnackbar -> {
                                val sb = Snackbar.make(binding.root, event.message, Snackbar.LENGTH_LONG)
                                if (event.actionLabel != null && event.action != null) {
                                    sb.setAction(event.actionLabel) { event.action.invoke() }
                                }
                                sb.show()
                            }
                            is UiEvent.RefreshList -> {}
                        }
                    }
                }
            }
        }
    }

    /**
     * Request notification permission using modern ActivityResultContracts API
     */
    private fun requestNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.menu_main, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean = when (item.itemId) {
        R.id.action_theme -> { navigationDelegate.showThemeSelectionDialog(); true }
        R.id.action_notification_settings -> { navigationDelegate.handleNotificationSettings(); true }
        R.id.action_about -> { navigationDelegate.showAboutDialog(); true }
        R.id.action_export_backup -> { navigationDelegate.startExportBackup(); true }
        R.id.action_import_backup -> { navigationDelegate.startImportBackup(); true }
        else -> super.onOptionsItemSelected(item)
    }

    private fun setupBackPressHandler() { 
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) { 
            override fun handleOnBackPressed() { 
                val drawer = binding.drawerLayout
                if (drawer.isDrawerOpen(GravityCompat.START)) drawer.closeDrawer(GravityCompat.START) else finish() 
            } 
        }) 
    }
}