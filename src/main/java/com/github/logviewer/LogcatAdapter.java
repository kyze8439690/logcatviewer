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

    private ArrayList<LogItem> mData;
    @Nullable private ArrayList<LogItem> mFilteredData = null;
    @Nullable private String mFilter = null;

    LogcatAdapter() {
        mData = new ArrayList<>();
    }

    void append(LogItem item) {
        synchronized (LogcatAdapter.class) {
            mData.add(item);
            if (mFilter != null && mFilteredData != null) {
                if (!item.isFiltered(mFilter)) {
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

    public LogItem[] getData() {
        synchronized (LogcatAdapter.class) {
            return mData.toArray(new LogItem[0]);
        }
    }

    @Override
    public int getCount() {
        return mFilteredData != null ? mFilteredData.size() : mData.size();
    }

    @Override
    public LogItem getItem(int position) {
        return mFilteredData != null ? mFilteredData.get(position) : mData.get(position);
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
                    FilterResults results = new FilterResults();

                    if (constraint == null) {
                        mFilter = null;
                        results.count = mData.size();
                        results.values = null;
                        return results;
                    } else {
                        mFilter = String.valueOf(constraint.charAt(0));
                    }

                    ArrayList<LogItem> filtered = new ArrayList<>();
                    for (LogItem item : mData) {
                        if (!item.isFiltered(mFilter)) {
                            filtered.add(item);
                        }
                    }

                    results.values = filtered;
                    results.count = filtered.size();
                    return results;
                }
            }

            @Override
            protected void publishResults(CharSequence constraint, FilterResults results) {
                if (results.values == null) {
                    mFilteredData = null;
                } else {
                    //noinspection unchecked
                    mFilteredData = (ArrayList<LogItem>) results.values;
                }
                notifyDataSetChanged();
            }
        };
    }

    public static class Holder {

        private LogcatViewerItemLogcatBinding mBinding;

        Holder(LogcatViewerItemLogcatBinding binding) {
            mBinding = binding;
            binding.getRoot().setTag(this);
        }

        void parse(LogItem data) {
            mBinding.time.setText(String.format(Locale.getDefault(),"%s %d-%d/%s",
                    new SimpleDateFormat("MM-dd hh:mm:ss.SSS", Locale.getDefault())
                            .format(data.time), data.processId, data.threadId, data.tag));
            mBinding.content.setText(data.content);
            mBinding.tag.setText(data.priority);
            mBinding.tag.setBackgroundResource(data.getColorRes());
        }
    }
}
