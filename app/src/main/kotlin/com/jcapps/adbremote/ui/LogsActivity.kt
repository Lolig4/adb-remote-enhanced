package com.jcapps.adbremote.ui

import android.content.Intent
import android.content.SharedPreferences
import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import androidx.lifecycle.lifecycleScope
import androidx.preference.PreferenceManager
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.snackbar.Snackbar
import com.jcapps.adbremote.R
import com.jcapps.adbremote.adapters.LogsAdapter
import com.jcapps.adbremote.databinding.ActivityLogsBinding
import com.jcapps.adbremote.utils.Logger
import com.jcapps.adbremote.utils.copyToClipboard
import com.jcapps.adbremote.utils.toUri
import com.jcapps.adbremote.views.State
import com.jcapps.adbremote.views.StateView
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.sample
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

/** "App logs": shows [Logger.logs], enable switch, clear / copy all / share. */
class LogsActivity : BaseActivity() {
    // obf: C1520

    // obf: f1641 / m853
    private val binding: ActivityLogsBinding by lazy { ActivityLogsBinding.inflate(layoutInflater) }

    // obf: f1642
    private lateinit var adapter: LogsAdapter

    // obf: f1644
    private val stateView: StateView by lazy { StateView(binding.includeViewState) }

    // obf: f1643
    private val prefs: SharedPreferences by lazy { PreferenceManager.getDefaultSharedPreferences(this) }

    @OptIn(FlowPreview::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(binding.root)
        setSupportActionBar(binding.toolbarCollapsing)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.setTitle(R.string.app_logs)
        applySystemBarInsets(binding.root)
        adapter = LogsAdapter { copyLog(it) }
        binding.list.adapter = adapter
        stateView.setState(State.EMPTY)
        binding.switchEnableLogs.isChecked = Logger.isEnabled
        // obf: C3220 case 0
        binding.switchEnableLogs.setOnCheckedChangeListener { _, isChecked ->
            Logger.isEnabled = isChecked
            prefs.edit().putBoolean("enable_connection_history", isChecked).apply()
        }
        // obf: C3851 (Ref.BooleanRef, element = true) + C2800 (collect Logger.logs.sample(500))
        var wasEmpty = true
        lifecycleScope.launch {
            // obf: C3416 case 0
            Logger.logs.sample(500L).collect { logs ->
                adapter.submitList(logs)
                val isEmpty = logs.isEmpty()
                if (wasEmpty != isEmpty) {
                    wasEmpty = isEmpty
                    val state = if (isEmpty) State.EMPTY else State.SUCCESS
                    // obf: C3601
                    withContext(Dispatchers.Main) {
                        stateView.setState(state)
                    }
                }
            }
        }
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.logs, menu)
        return super.onCreateOptionsMenu(menu)
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        when (item.itemId) {
            // obf: Logger.f6231 (assigned directly)
            R.id.action_clear_logs -> Logger._logs.value = emptyList()
            R.id.action_copy_logs -> copyLog(getLogs().joinToString("\n\n")) // no transform in the original: Pair.toString()
            R.id.action_share -> if (getLogs().isEmpty()) {
                MaterialAlertDialogBuilder(this)
                    .setMessage(R.string.view_logs_enable_logs_connect)
                    .setPositiveButton(R.string.ok, null)
                    .show()
            } else {
                // obf: C2565 (share logs file)
                lifecycleScope.launch(Dispatchers.IO) {
                    val text = getLogs().joinToString("\n\n")
                    val file = File(externalCacheDir, "adb_remote_logs.txt")
                    file.writeText(text)
                    // obf: C0420
                    withContext(Dispatchers.Main) {
                        val intent = Intent(Intent.ACTION_SEND)
                        intent.type = "text/plain"
                        intent.putExtra(Intent.EXTRA_STREAM, file.toUri(this@LogsActivity))
                        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                        startActivity(Intent.createChooser(intent, "adb_remote_logs.txt"))
                    }
                }
            }
        }
        return super.onOptionsItemSelected(item)
    }

    // obf: m854
    private fun getLogs(): List<Pair<Int, String>> = adapter.currentList

    // obf: m855
    private fun copyLog(text: String) {
        copyToClipboard(text)
        Snackbar.make(binding.root, binding.root.resources.getText(R.string.copied_clipboard), Snackbar.LENGTH_SHORT).show()
    }
}
