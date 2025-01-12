package com.github.logviewer

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.MenuItem
import android.view.View
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.ListView
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.ActivityResultLauncher
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.Toolbar
import androidx.lifecycle.lifecycleScope
import com.github.logviewer.databinding.LogcatViewerActivityLogcatBinding
import com.google.android.material.snackbar.Snackbar
import kotlinx.coroutines.launch
import java.io.IOException
import java.text.ParseException
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.Scanner
import java.util.regex.Pattern

class LogcatActivity : AppCompatActivity(), Toolbar.OnMenuItemClickListener {

    companion object {
        @JvmOverloads
        fun start(context: Context, excludeList: List<Pattern> = emptyList()) {
            val list = ArrayList<String>()
            for (pattern in excludeList) {
                list.add(pattern.pattern())
            }
            val starter = getIntent(context, list)
            context.startActivity(starter)
        }

        private fun getIntent(context: Context?, list: ArrayList<String>?): Intent {
            return Intent(context, LogcatActivity::class.java)
                .putStringArrayListExtra("exclude_list", list)
        }
    }

    private lateinit var binding: LogcatViewerActivityLogcatBinding
    private val adapter = LogcatAdapter()
    private var reading = false
    private val excludeList: MutableList<Pattern> = ArrayList()
    private lateinit var launcher: ActivityResultLauncher<Unit>

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        binding = LogcatViewerActivityLogcatBinding.inflate(layoutInflater)
        setContentView(binding.root)
        binding.toolbar.setNavigationOnClickListener { v: View? -> onBackPressed() }
        binding.toolbar.inflateMenu(R.menu.logcat)
        binding.toolbar.setOnMenuItemClickListener(this)

        intent.getStringArrayListExtra("exclude_list")?.let {
            for (pattern in it) {
                excludeList.add(Pattern.compile(pattern))
            }
        }

        val spinnerAdapter = ArrayAdapter.createFromResource(
            this,
            R.array.logcat_viewer_logcat_spinner, R.layout.logcat_viewer_item_logcat_dropdown
        )
        spinnerAdapter.setDropDownViewResource(R.layout.logcat_viewer_item_logcat_dropdown)
        binding.spinner.adapter = spinnerAdapter
        binding.spinner.onItemSelectedListener =
            object : AdapterView.OnItemSelectedListener {
                override fun onItemSelected(
                    parent: AdapterView<*>?,
                    view: View?,
                    position: Int,
                    id: Long
                ) {
                    val filter = resources
                        .getStringArray(R.array.logcat_viewer_logcat_spinner)[position]
                    adapter.filter.filter(filter)
                }

                override fun onNothingSelected(parent: AdapterView<*>?) = Unit
            }

        binding.list.transcriptMode = ListView.TRANSCRIPT_MODE_NORMAL
        binding.list.isStackFromBottom = true
        binding.list.adapter = adapter

        launcher = registerForActivityResult(RequestOverlayPermission(this)) { result ->
            if (result) {
                FloatingLogcatService.launch(applicationContext, excludeList)
                finish()
            }
        }
    }

    override fun onResume() {
        super.onResume()
        startReadLogcat()
    }

    override fun onPause() {
        super.onPause()
        stopReadLogcat()
    }

    private var latestTime: Date? = null

    private fun startReadLogcat() {
        object : Thread("logcat-activity") {
            override fun run() {
                super.run()
                reading = true
                var process: Process? = null
                var reader: Scanner? = null
                try {
                    val cmd = ArrayList(mutableListOf("logcat", "-v", "threadtime"))
                    latestTime?.let {
                        val sdf = SimpleDateFormat("MM-dd HH:mm:ss.mmm", Locale.getDefault())
                        cmd.add("-T")
                        cmd.add(sdf.format(it))
                    }
                    process = ProcessBuilder(cmd).start()
                    reader = Scanner(process.inputStream)

                    while (reading && reader.hasNextLine()) {
                        val line = reader.nextLine()
                        if (LogItem.IGNORED_LOG.matcher(line).matches()) {
                            continue
                        }
                        var skip = false
                        for (pattern in excludeList) {
                            if (pattern.matcher(line).matches()) {
                                skip = true
                                break
                            }
                        }
                        if (skip) {
                            continue
                        }
                        try {
                            val item = LogItem(line)
                            latestTime = item.time
                            binding.list.post { adapter.append(item) }
                        } catch (e: ParseException) {
                            e.printStackTrace()
                        } catch (e: NumberFormatException) {
                            e.printStackTrace()
                        } catch (e: IllegalStateException) {
                            e.printStackTrace()
                        }
                    }
                } catch (e: IOException) {
                    e.printStackTrace()
                } finally {
                    process?.destroy()
                    reader?.close()
                    stopReadLogcat()
                }
            }
        }.start()
    }

    private fun stopReadLogcat() {
        reading = false
    }

    override fun onMenuItemClick(item: MenuItem) = when (item.itemId) {
        R.id.clear -> {
            adapter.clear()
            true
        }
        R.id.export -> {
            lifecycleScope.launch {
                val exportedFile = ExportLogFileUtils.exportLogs(externalCacheDir, adapter.data)
                if (exportedFile == null) {
                    Snackbar.make(
                        binding.root,
                        R.string.logcat_viewer_create_log_file_failed,
                        Snackbar.LENGTH_SHORT
                    ).show()
                } else {
                    val shareIntent = Intent(Intent.ACTION_SEND)
                    shareIntent.setType("text/plain")
                    val uri = LogcatFileProvider.getUriForFile(
                        applicationContext,
                        "$packageName.logcat_fileprovider", exportedFile
                    )
                    shareIntent.putExtra(Intent.EXTRA_STREAM, uri)
                    if (packageManager.queryIntentActivities(shareIntent, 0).isEmpty()) {
                        Snackbar.make(
                            binding.root,
                            R.string.logcat_viewer_not_support_on_this_device,
                            Snackbar.LENGTH_SHORT
                        ).show()
                    } else {
                        startActivity(shareIntent)
                    }
                }
            }
            true
        }
        R.id.floating -> {
            launcher.launch(Unit)
            true
        }
        else -> {
            false
        }
    }
}
