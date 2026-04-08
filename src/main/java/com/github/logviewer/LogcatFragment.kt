package com.github.logviewer

import android.content.Intent
import android.os.Bundle
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import androidx.core.content.getSystemService
import androidx.core.widget.doAfterTextChanged
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import android.view.LayoutInflater
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.ListView
import androidx.appcompat.widget.Toolbar
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.github.logviewer.databinding.LogcatViewerFragmentLogcatBinding
import com.google.android.material.snackbar.Snackbar
import kotlinx.coroutines.launch
import java.io.IOException
import java.text.ParseException
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.Scanner
import java.util.regex.Pattern

class LogcatFragment :
    Fragment(),
    Toolbar.OnMenuItemClickListener {
    companion object {
        @JvmStatic
        fun newInstance(excludeList: List<Pattern> = emptyList()): LogcatFragment {
            val args = Bundle()
            args.putStringArrayList("exclude_list", ArrayList(excludeList.map { it.pattern() }))
            val fragment = LogcatFragment()
            fragment.arguments = args
            return fragment
        }
    }

    private lateinit var binding: LogcatViewerFragmentLogcatBinding
    private val excludeList: MutableList<Pattern> = ArrayList()
    private val adapter = LogcatAdapter()
    private var reading = false
    private var latestTime: Date? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        arguments?.getStringArrayList("exclude_list")?.let {
            for (pattern in it) {
                excludeList.add(Pattern.compile(pattern))
            }
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View {
        binding = LogcatViewerFragmentLogcatBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(
        view: View,
        savedInstanceState: Bundle?,
    ) {
        ViewCompat.setOnApplyWindowInsetsListener(binding.root) { _, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            val imeHeight = insets.getInsets(WindowInsetsCompat.Type.ime()).bottom

            // 键盘弹出时调整 root 的 bottom padding，让输入框保持在键盘上方
            val bottomPadding = if (imeHeight > 0) imeHeight else systemBars.bottom
            binding.root.updatePadding(bottom = bottomPadding)

            insets
        }
        binding.toolbar.setNavigationOnClickListener {
            activity?.onBackPressedDispatcher?.onBackPressed()
        }
        binding.toolbar.setOnMenuItemClickListener(this)
        val spinnerAdapter =
            ArrayAdapter.createFromResource(
                requireContext(),
                R.array.logcat_viewer_logcat_spinner,
                R.layout.logcat_viewer_item_logcat_dropdown,
            )
        spinnerAdapter.setDropDownViewResource(R.layout.logcat_viewer_item_logcat_dropdown)
        binding.spinner.adapter = spinnerAdapter
        binding.spinner.onItemSelectedListener =
            object : AdapterView.OnItemSelectedListener {
                override fun onItemSelected(
                    parent: AdapterView<*>?,
                    view: View?,
                    position: Int,
                    id: Long,
                ) {
                    val filter =
                        resources
                            .getStringArray(R.array.logcat_viewer_logcat_spinner)[position]
                    adapter.filter.filter(filter)
                }

                override fun onNothingSelected(parent: AdapterView<*>?) = Unit
            }

        binding.list.transcriptMode = ListView.TRANSCRIPT_MODE_NORMAL
        binding.list.isStackFromBottom = true
        binding.list.adapter = adapter
        binding.list.emptyView = binding.emptyView

        // 初始化过滤输入框
        setupTextFilter()
    }

    override fun onResume() {
        super.onResume()
        startReadLogcat()
    }

    override fun onPause() {
        super.onPause()
        stopReadLogcat()
    }

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

    override fun onMenuItemClick(item: MenuItem) =
        when (item.itemId) {
            R.id.clear -> {
                adapter.clear()
                true
            }

            R.id.export -> {
                lifecycleScope.launch {
                    val exportedFile =
                        ExportLogFileUtils.exportLogs(
                            requireContext().externalCacheDir,
                            adapter.data,
                        )
                    if (exportedFile == null) {
                        Snackbar
                            .make(
                                binding.root,
                                R.string.logcat_viewer_create_log_file_failed,
                                Snackbar.LENGTH_SHORT,
                            ).show()
                    } else {
                        val shareIntent = Intent(Intent.ACTION_SEND)
                        shareIntent.setType("text/plain")
                        val uri =
                            LogcatFileProvider.getUriForFile(
                                requireContext(),
                                "${requireContext().packageName}.logcat_fileprovider",
                                exportedFile,
                            )
                        shareIntent.putExtra(Intent.EXTRA_STREAM, uri)
                        if (
                            requireContext()
                                .packageManager
                                .queryIntentActivities(
                                    shareIntent,
                                    0,
                                ).isEmpty()
                        ) {
                            Snackbar
                                .make(
                                    binding.root,
                                    R.string.logcat_viewer_not_support_on_this_device,
                                    Snackbar.LENGTH_SHORT,
                                ).show()
                        } else {
                            startActivity(shareIntent)
                        }
                    }
                }
                true
            }

            else -> {
                false
            }
        }

    @OptIn(FlowPreview::class)
    private fun setupTextFilter() {
        val textFilterFlow = MutableStateFlow("")

        // 使用 300ms 防抖处理文字变化
        textFilterFlow
            .debounce(300)
            .onEach { filterText ->
                adapter.setTextFilter(filterText.takeIf { it.isNotEmpty() })
            }
            .launchIn(viewLifecycleOwner.lifecycleScope)

        binding.filterInput.doAfterTextChanged { editable ->
            textFilterFlow.value = editable?.toString() ?: ""
        }

        // 处理键盘搜索按钮
        binding.filterInput.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_SEARCH) {
                hideKeyboard()
                true
            } else {
                false
            }
        }

        // 处理清空按钮点击（收起键盘）
        binding.filterInputLayout.setEndIconOnClickListener {
            binding.filterInput.text?.clear()
            hideKeyboard()
        }
    }

    private fun hideKeyboard() {
        val imm = requireContext().getSystemService<InputMethodManager>()
        imm?.hideSoftInputFromWindow(binding.filterInput.windowToken, 0)
    }
}
