package com.github.logviewer

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.github.logviewer.databinding.LogcatViewerItemLogcatBinding
import java.text.SimpleDateFormat
import java.util.Locale

class LogcatAdapter : ListAdapter<LogItem, LogcatAdapter.Holder>(DIFF) {
    private val rawData = mutableListOf<LogItem>()
    private val lock = Any()
    private var levelFilter: String? = null
    private var textFilter: String? = null

    fun appendAll(items: List<LogItem>) {
        if (items.isEmpty()) return
        val visible: List<LogItem>
        synchronized(lock) {
            rawData.addAll(items)
            visible = computeVisibleLocked()
        }
        submitList(visible)
    }

    fun clear() {
        val visible: List<LogItem>
        synchronized(lock) {
            rawData.clear()
            visible = computeVisibleLocked()
        }
        submitList(visible)
    }

    fun setLevelFilter(filter: String?) {
        val visible: List<LogItem>
        synchronized(lock) {
            levelFilter = filter
            visible = computeVisibleLocked()
        }
        submitList(visible)
    }

    fun setTextFilter(filter: String?) {
        val visible: List<LogItem>
        synchronized(lock) {
            textFilter = filter?.takeIf { it.isNotEmpty() }?.lowercase(Locale.getDefault())
            visible = computeVisibleLocked()
        }
        submitList(visible)
    }

    fun snapshot(): Array<LogItem> = synchronized(lock) { rawData.toTypedArray() }

    private fun computeVisibleLocked(): List<LogItem> {
        val filtered = if (levelFilter == null && textFilter == null) {
            rawData
        } else {
            rawData.filter { !shouldHide(it) }
        }
        return filtered.reversed()
    }

    private fun shouldHide(item: LogItem): Boolean {
        levelFilter?.let { if (item.isFiltered(it)) return true }
        textFilter?.let { needle ->
            val haystack = "${item.tag} ${item.content}".lowercase(Locale.getDefault())
            return !haystack.contains(needle)
        }
        return false
    }

    override fun onCreateViewHolder(
        parent: ViewGroup,
        viewType: Int,
    ): Holder {
        val binding =
            LogcatViewerItemLogcatBinding.inflate(
                LayoutInflater.from(parent.context),
                parent,
                false,
            )
        return Holder(binding)
    }

    override fun onBindViewHolder(
        holder: Holder,
        position: Int,
    ) {
        holder.bind(getItem(position))
    }

    class Holder(
        private val binding: LogcatViewerItemLogcatBinding,
    ) : RecyclerView.ViewHolder(binding.root) {
        fun bind(data: LogItem) {
            binding.time.text =
                String.format(
                    Locale.getDefault(),
                    "%s %d-%d",
                    TIME_FORMAT.format(data.time),
                    data.processId,
                    data.threadId,
                )
            binding.content.text = data.content
            binding.level.text = data.priority
            binding.level.setBackgroundResource(data.colorRes)
            binding.tag.text = data.tag
        }
    }

    companion object {
        private val TIME_FORMAT = SimpleDateFormat("MM-dd HH:mm:ss.SSS", Locale.getDefault())

        private val DIFF =
            object : DiffUtil.ItemCallback<LogItem>() {
                override fun areItemsTheSame(
                    oldItem: LogItem,
                    newItem: LogItem,
                ) = oldItem === newItem

                override fun areContentsTheSame(
                    oldItem: LogItem,
                    newItem: LogItem,
                ) = oldItem === newItem
            }
    }
}
