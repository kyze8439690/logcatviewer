package com.github.logviewer

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.widget.AdapterView
import android.widget.ArrayAdapter
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.widget.Toolbar
import androidx.core.content.getSystemService
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.isVisible
import androidx.core.view.updatePadding
import androidx.core.widget.doAfterTextChanged
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.DividerItemDecoration
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.github.logviewer.databinding.LogcatViewerFragmentLogcatBinding
import com.google.android.material.snackbar.Snackbar
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import me.zhanghai.android.fastscroll.DefaultAnimationHelper
import me.zhanghai.android.fastscroll.FastScrollerBuilder
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
        private const val FLUSH_INTERVAL_MS = 50L

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

    @Volatile
    private var logProcess: Process? = null

    @Volatile
    private var logReader: Scanner? = null
    private val logBuffer = ArrayList<LogItem>()
    private val flushHandler = android.os.Handler(android.os.Looper.getMainLooper())
    private val flushRunnable = Runnable { flushBuffer() }
    private var emptyViewObserver: RecyclerView.AdapterDataObserver? = null

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
        // 根布局只处理键盘高度的 padding，不处理 navigation bar
        ViewCompat.setOnApplyWindowInsetsListener(binding.content) { view, insets ->
            val imeHeight = insets.getInsets(WindowInsetsCompat.Type.ime()).bottom
            view.updatePadding(bottom = imeHeight)
            insets
        }

        // AppBarLayout 设置 status bar 高度的 padding top（背景延伸到 status bar 后面）
        ViewCompat.setOnApplyWindowInsetsListener(binding.appbar) { view, insets ->
            val statusBarHeight = insets.getInsets(WindowInsetsCompat.Type.statusBars()).top
            view.updatePadding(top = statusBarHeight)
            insets
        }

        // 输入框负责 navigation bar padding：
        // - 键盘收起时：padding bottom = navigation bar 高度 + 8dp（背景延伸到 navigation bar 后面）
        // - 键盘弹出时：padding bottom = 8dp（去掉 navigation bar 部分，因为键盘已占用空间）
        ViewCompat.setOnApplyWindowInsetsListener(binding.filterInputLayout) { view, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            val imeHeight = insets.getInsets(WindowInsetsCompat.Type.ime()).bottom

            val navBarPadding = if (imeHeight > 0) 0 else systemBars.bottom
            view.updatePadding(
                left = resources.getDimensionPixelSize(R.dimen.logcat_filter_padding_horizontal),
                right = resources.getDimensionPixelSize(R.dimen.logcat_filter_padding_horizontal),
                top = resources.getDimensionPixelSize(R.dimen.logcat_filter_padding_vertical),
                bottom = navBarPadding + resources.getDimensionPixelSize(R.dimen.logcat_filter_padding_vertical),
            )
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
                    adapter.setLevelFilter(filter.substring(0, 1))
                }

                override fun onNothingSelected(parent: AdapterView<*>?) = Unit
            }

        binding.list.layoutManager = LinearLayoutManager(requireContext())
        binding.list.itemAnimator = null
        binding.list.addItemDecoration(
            DividerItemDecoration(requireContext(), DividerItemDecoration.VERTICAL),
        )
        binding.list.adapter = adapter
        val animHelper = DefaultAnimationHelper(binding.list)
        animHelper.isScrollbarAutoHideEnabled = false
        FastScrollerBuilder(binding.list)
            .useMd2Style()
            .apply { setAnimationHelper(animHelper) }
            .build()
        emptyViewObserver =
            object : RecyclerView.AdapterDataObserver() {
                override fun onChanged() = updateEmptyView()

                override fun onItemRangeInserted(positionStart: Int, itemCount: Int) {
                    updateEmptyView()
                    val lm = binding.list.layoutManager as LinearLayoutManager
                    val firstVisible = lm.findFirstCompletelyVisibleItemPosition()
                    if (firstVisible == 0 || firstVisible == RecyclerView.NO_POSITION) {
                        lm.scrollToPositionWithOffset(0, 0)
                    }
                }

                override fun onItemRangeRemoved(positionStart: Int, itemCount: Int) =
                    updateEmptyView()
            }
        emptyViewObserver?.let { adapter.registerAdapterDataObserver(it) }
        updateEmptyView()

        // 拦截 BACK 键，在 Container Transition 触发 layout 之前先清空 adapter，避免 RecyclerView 被无限高度 measure
        requireActivity().onBackPressedDispatcher.addCallback(
            viewLifecycleOwner,
            object : OnBackPressedCallback(true) {
                override fun handleOnBackPressed() {
                    binding.list.adapter = null
                    if (parentFragmentManager.backStackEntryCount > 0) {
                        parentFragmentManager.popBackStack()
                    } else {
                        requireActivity().finishAfterTransition()
                    }
                }
            },
        )

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
        // 直接销毁进程并关闭 reader，让阻塞的 hasNextLine() 立即返回
        logProcess?.destroy()
        logReader?.close()
        // 取消待执行的 flush 任务，避免 onPause 后继续 post Runnable
        flushHandler.removeCallbacks(flushRunnable)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        emptyViewObserver?.let { adapter.unregisterAdapterDataObserver(it) }
    }

    private fun startReadLogcat() {
        object : Thread("logcat-activity") {
            override fun run() {
                super.run()
                reading = true
                try {
                    val cmd = ArrayList(mutableListOf("logcat", "-v", "threadtime"))
                    latestTime?.let {
                        val sdf = SimpleDateFormat("MM-dd HH:mm:ss.SSS", Locale.getDefault())
                        cmd.add("-T")
                        cmd.add(sdf.format(it))
                    }
                    val process = ProcessBuilder(cmd).start()
                    logProcess = process
                    val reader = Scanner(process.inputStream)
                    logReader = reader
                    scheduleFlush()

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
                            synchronized(logBuffer) {
                                logBuffer.add(item)
                                if (logBuffer.size > 1000) {
                                    logBuffer.subList(0, logBuffer.size - 1000).clear()
                                }
                                if (logBuffer.size > 300) {
                                    Thread.sleep(10)
                                }
                            }
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
                    logProcess?.destroy()
                    logReader?.close()
                    stopReadLogcat()
                    flushHandler.removeCallbacks(flushRunnable)
                }
            }
        }.start()
    }

    private fun stopReadLogcat() {
        reading = false
    }

    private fun scheduleFlush() {
        if (!reading) return
        flushHandler.postDelayed(flushRunnable, FLUSH_INTERVAL_MS)
    }

    private fun flushBuffer() {
        if (!isAdded || !reading) return
        val batch: ArrayList<LogItem>
        synchronized(logBuffer) {
            if (logBuffer.isEmpty()) {
                scheduleFlush()
                return
            }
            val count = minOf(logBuffer.size, 100)
            batch = ArrayList(logBuffer.subList(0, count))
            logBuffer.subList(0, count).clear()
        }
        adapter.appendAll(batch)
        scheduleFlush()
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
                            adapter.snapshot(),
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
            }.launchIn(viewLifecycleOwner.lifecycleScope)

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

    private fun updateEmptyView() {
        val empty = adapter.itemCount == 0
        binding.emptyView.isVisible = empty
        binding.list.isVisible = !empty
    }
}
