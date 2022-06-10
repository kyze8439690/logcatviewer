package com.github.logviewer;


import android.annotation.SuppressLint;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.view.MenuItem;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.ListView;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;

import com.github.logviewer.databinding.LogcatViewerActivityLogcatBinding;
import com.google.android.material.snackbar.Snackbar;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.text.ParseException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.regex.Pattern;

public class LogcatActivity extends AppCompatActivity implements Toolbar.OnMenuItemClickListener {

    public static void start(Context context) {
        start(context, Collections.emptyList());
    }

    public static void start(Context context, List<Pattern> excludeList) {
        ArrayList<String> list = new ArrayList<>();
        for (Pattern pattern : excludeList) {
            list.add(pattern.pattern());
        }
        @SuppressLint("InlinedApi")
        Intent starter = new Intent(context, LogcatActivity.class)
                .addFlags(Intent.FLAG_ACTIVITY_LAUNCH_ADJACENT)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                .putStringArrayListExtra("exclude_list", list);
        context.startActivity(starter);
    }

    private static final int REQUEST_SCREEN_OVERLAY = 23453;

    private LogcatViewerActivityLogcatBinding mBinding;

    private final LogcatAdapter mAdapter = new LogcatAdapter();
    private boolean mReading = false;
    private final List<Pattern> mExcludeList = new ArrayList<>();

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        mBinding = LogcatViewerActivityLogcatBinding.inflate(getLayoutInflater());
        setContentView(mBinding.getRoot());
        mBinding.toolbar.setNavigationOnClickListener(v -> onBackPressed());
        mBinding.toolbar.inflateMenu(R.menu.logcat);
        mBinding.toolbar.setOnMenuItemClickListener(this);

        List<String> excludeList = getIntent().getStringArrayListExtra("exclude_list");
        for (String pattern : excludeList) {
            mExcludeList.add(Pattern.compile(pattern));
        }

        ArrayAdapter<CharSequence> spinnerAdapter = ArrayAdapter.createFromResource(this,
                R.array.logcat_viewer_logcat_spinner, R.layout.logcat_viewer_item_logcat_dropdown);
        spinnerAdapter.setDropDownViewResource(R.layout.logcat_viewer_item_logcat_dropdown);
        mBinding.spinner.setAdapter(spinnerAdapter);
        mBinding.spinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                String filter = getResources().getStringArray(R.array.logcat_viewer_logcat_spinner)[position];
                mAdapter.getFilter().filter(filter);
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {

            }
        });

        mBinding.list.setTranscriptMode(ListView.TRANSCRIPT_MODE_NORMAL);
        mBinding.list.setStackFromBottom(true);
        mBinding.list.setAdapter(mAdapter);
        mBinding.list.setOnItemClickListener((parent, view, position, id) ->
                LogcatDetailActivity.launch(LogcatActivity.this, mAdapter.getItem(position)));
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQUEST_SCREEN_OVERLAY && Build.VERSION.SDK_INT >= Build.VERSION_CODES.M
                && Settings.canDrawOverlays(getApplicationContext())) {
            FloatingLogcatService.launch(getApplicationContext(), mExcludeList);
            finish();
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        startReadLogcat();
    }

    @Override
    protected void onPause() {
        super.onPause();
        stopReadLogcat();
    }

    private void startReadLogcat() {
        new Thread("logcat-activity") {
            @Override
            public void run() {
                super.run();
                mReading = true;
                BufferedReader reader = null;
                try {
                    Process process = new ProcessBuilder("logcat", "-v", "threadtime").start();
                    reader = new BufferedReader(new InputStreamReader(process.getInputStream()));
                    String line;
                    while (mReading && (line = reader.readLine()) != null) {
                        if (LogItem.IGNORED_LOG.contains(line)) {
                            continue;
                        }
                        boolean skip = false;
                        for (Pattern pattern : mExcludeList) {
                            if (pattern.matcher(line).matches()) {
                                skip = true;
                                break;
                            }
                        }
                        if (skip) {
                            continue;
                        }
                        try {
                            final LogItem item = new LogItem(line);
                            mBinding.list.post(() -> mAdapter.append(item));
                        } catch (ParseException | NumberFormatException | IllegalStateException e) {
                            e.printStackTrace();
                        }
                    }
                    stopReadLogcat();
                } catch (IOException e) {
                    e.printStackTrace();
                    stopReadLogcat();
                }
                if (reader != null) {
                    try {
                        reader.close();
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                }
            }
        }.start();
    }

    private void stopReadLogcat() {
        mReading = false;
    }

    @Override
    public boolean onMenuItemClick(MenuItem item) {
        if (item.getItemId() == R.id.clear) {
            mAdapter.clear();
            return true;
        } else if (item.getItemId() == R.id.export) {
            @SuppressLint("StaticFieldLeak")
            ExportLogFileTask task = new ExportLogFileTask(getExternalCacheDir()) {
                @Override
                protected void onPostExecute(File file) {
                    if (file == null) {
                        Snackbar.make(mBinding.root, R.string.logcat_viewer_create_log_file_failed, Snackbar.LENGTH_SHORT)
                                .show();
                    } else {
                        Intent shareIntent = new Intent(Intent.ACTION_SEND);
                        shareIntent.setType("text/plain");
                        Uri uri = LogcatFileProvider.getUriForFile(getApplicationContext(),
                                getPackageName() + ".logcat_fileprovider", file);
                        shareIntent.putExtra(Intent.EXTRA_STREAM, uri);
                        if (getPackageManager().queryIntentActivities(
                                shareIntent, 0).isEmpty()) {
                            Snackbar.make(mBinding.root, R.string.logcat_viewer_not_support_on_this_device,
                                    Snackbar.LENGTH_SHORT).show();
                        } else {
                            startActivity(shareIntent);
                        }
                    }
                }
            };
            task.executeOnExecutor(AsyncTask.SERIAL_EXECUTOR, mAdapter.getData());
            return true;
        } else if (item.getItemId() == R.id.floating) {
            Context context = getApplicationContext();
            if(Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && !Settings.canDrawOverlays(context)) {
                Intent intent = new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                        Uri.parse("package:" + getPackageName()));
                if (getPackageManager().queryIntentActivities(intent, 0).isEmpty()) {
                    Snackbar.make(mBinding.root, R.string.logcat_viewer_not_support_on_this_device,
                            Snackbar.LENGTH_SHORT).show();
                } else {
                    startActivityForResult(intent, REQUEST_SCREEN_OVERLAY);
                }
            } else {
                FloatingLogcatService.launch(context, mExcludeList);
                finish();
            }
            return true;
        } else {
            return false;
        }
    }
}
