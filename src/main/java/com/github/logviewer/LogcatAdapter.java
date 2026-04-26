package com.github.logviewer;


import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.Filter;
import android.widget.Filterable;

import androidx.annotation.Nullable;

import com.github.logviewer.databinding.LogcatViewerItemLogcatBinding;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Locale;

public class LogcatAdapter extends BaseAdapter implements Filterable {

    private final ArrayList<LogItem> mData;
    @Nullable private ArrayList<LogItem> mFilteredData = null;
    @Nullable private String mFilter = null;
    @Nullable private String mTextFilter = null;

    LogcatAdapter() {
        mData = new ArrayList<>();
    }

    void append(LogItem item) {
        synchronized (LogcatAdapter.class) {
            mData.add(item);
            if (!isItemFiltered(item)) {
                if (mFilteredData != null) {
                    mFilteredData.add(item);
                }
            }
            notifyDataSetChanged();
        }
    }

    void clear() {
        synchronized (LogcatAdapter.class) {
            mData.clear();
            mFilteredData = null;
            notifyDataSetChanged();
        }
    }

    public void setTextFilter(@Nullable String textFilter) {
        synchronized (LogcatAdapter.class) {
            mTextFilter = textFilter;
            applyFilters();
        }
    }

    private void applyFilters() {
        synchronized (LogcatAdapter.class) {
            if (mFilter == null && (mTextFilter == null || mTextFilter.isEmpty())) {
                mFilteredData = null;
            } else {
                ArrayList<LogItem> filtered = new ArrayList<>();
                for (LogItem item : mData) {
                    if (!isItemFiltered(item)) {
                        filtered.add(item);
                    }
                }
                mFilteredData = filtered;
            }
            notifyDataSetChanged();
        }
    }

    private boolean isItemFiltered(LogItem item) {
        // 检查级别过滤
        if (mFilter != null && item.isFiltered(mFilter)) {
            return true;
        }
        // 检查文字过滤
        if (mTextFilter != null && !mTextFilter.isEmpty()) {
            String searchText = mTextFilter.toLowerCase(java.util.Locale.getDefault());
            String tagContent = (item.tag + " " + item.content).toLowerCase(java.util.Locale.getDefault());
            return !tagContent.contains(searchText);
        }
        return false;
    }

    public LogItem[] getData() {
        synchronized (LogcatAdapter.class) {
            return mData.toArray(new LogItem[0]);
        }
    }

    @Override
    public int getCount() {
        synchronized (LogcatAdapter.class) {
            return mFilteredData != null ? mFilteredData.size() : mData.size();
        }
    }

    @Override
    public LogItem getItem(int position) {
        synchronized (LogcatAdapter.class) {
            return mFilteredData != null ? mFilteredData.get(position) : mData.get(position);
        }
    }

    @Override
    public long getItemId(int position) {
        return position;
    }

    @Override
    public View getView(int position, View convertView, ViewGroup parent) {
        Holder holder;
        if (convertView == null) {
            LogcatViewerItemLogcatBinding binding = LogcatViewerItemLogcatBinding.inflate(
                    LayoutInflater.from(parent.getContext()), parent, false);
            holder = new Holder(binding);
            convertView = binding.getRoot();
        } else {
            holder = (Holder) convertView.getTag();
        }
        holder.parse(getItem(position));
        return convertView;
    }

    @Override
    public Filter getFilter() {
        return new Filter() {
            @Override
            protected FilterResults performFiltering(CharSequence constraint) {
                synchronized (LogcatAdapter.class) {
                    if (constraint == null || constraint.length() == 0) {
                        mFilter = null;
                    } else {
                        mFilter = String.valueOf(constraint.charAt(0));
                    }

                    FilterResults results = new FilterResults();
                    results.count = mFilteredData != null ? mFilteredData.size() : mData.size();
                    return results;
                }
            }

            @Override
            protected void publishResults(CharSequence constraint, FilterResults results) {
                synchronized (LogcatAdapter.class) {
                    applyFilters();
                }
            }
        };
    }

    public static class Holder {

        private final LogcatViewerItemLogcatBinding mBinding;

        Holder(LogcatViewerItemLogcatBinding binding) {
            mBinding = binding;
            binding.getRoot().setTag(this);
        }

        void parse(LogItem data) {
            mBinding.time.setText(String.format(Locale.getDefault(), "%s %d-%d",
                    new SimpleDateFormat("MM-dd HH:mm:ss.SSS", Locale.getDefault())
                            .format(data.time), data.processId, data.threadId));
            mBinding.content.setText(data.content);
            mBinding.level.setText(data.priority);
            mBinding.level.setBackgroundResource(data.getColorRes());
            mBinding.tag.setText(data.tag);
        }
    }
}
