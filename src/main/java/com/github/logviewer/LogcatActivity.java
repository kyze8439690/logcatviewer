package com.github.logviewer;


import android.annotation.SuppressLint;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.ListView;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import com.github.logviewer.databinding.ActivityLogcatBinding;
import com.google.android.material.snackbar.Snackbar;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.text.ParseException;

public class LogcatActivity extends AppCompatActivity {

    public static void start(Context context) {
        Intent starter = new Intent(context, LogcatActivity.class);
        context.startActivity(starter);
    }

    private static final int REQUEST_SCREEN_OVERLAY = 23453;

    private ActivityLogcatBinding mBinding;

    private LogcatAdapter mAdapter = new LogcatAdapter();
    private boolean mReading = false;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        mBinding = ActivityLogcatBinding.inflate(getLayoutInflater());
        setContentView(mBinding.getRoot());

        setSupportActionBar(mBinding.toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        }

        ArrayAdapter<CharSequence> spinnerAdapter = ArrayAdapter.createFromResource(this,
                R.array.logcat_spinner, R.layout.item_logcat_dropdown);
        spinnerAdapter.setDropDownViewResource(R.layout.item_logcat_dropdown);
        mBinding.spinner.setAdapter(spinnerAdapter);
        mBinding.spinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                String filter = getResources().getStringArray(R.array.logcat_spinner)[position];
                mAdapter.getFilter().filter(filter);
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {

            }
        });

        mBinding.list.setTranscriptMode(ListView.TRANSCRIPT_MODE_NORMAL);
        mBinding.list.setStackFromBottom(true);
        mBinding.list.setAdapter(mAdapter);
        mBinding.list.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                LogcatDetailActivity.launch(LogcatActivity.this, mAdapter.getItem(position));
            }
        });
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.logcat, menu);
        return super.onCreateOptionsMenu(menu);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == android.R.id.home) {
            finish();
            return true;
        } else if (item.getItemId() == R.id.clear) {
            mAdapter.clear();
            return true;
        } else if (item.getItemId() == R.id.export) {
            @SuppressLint("StaticFieldLeak")
            ExportLogFileTask task = new ExportLogFileTask(getExternalCacheDir()) {
                @Override
                protected void onPostExecute(File file) {
                    if (file == null) {
                        Snackbar.make(mBinding.root, R.string.create_log_file_failed, Snackbar.LENGTH_SHORT)
                                .show();
                    } else {
                        Intent shareIntent = new Intent(Intent.ACTION_SEND);
                        shareIntent.setType("text/plain");
                        Uri uri = LogcatFileProvider.getUriForFile(getApplicationContext(),
                                getPackageName() + ".logcat_fileprovider", file);
                        shareIntent.putExtra(Intent.EXTRA_STREAM, uri);
                        if (getPackageManager().queryIntentActivities(
                                shareIntent, 0).isEmpty()) {
                            Snackbar.make(mBinding.root, R.string.not_support_on_this_device,
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
                    Snackbar.make(mBinding.root, R.string.not_support_on_this_device,
                            Snackbar.LENGTH_SHORT).show();
                } else {
                    startActivityForResult(intent, REQUEST_SCREEN_OVERLAY);
                }
            } else {
                FloatingLogcatService.launch(context);
                finish();
            }
            return true;
        } else {
            return super.onOptionsItemSelected(item);
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQUEST_SCREEN_OVERLAY && Build.VERSION.SDK_INT >= Build.VERSION_CODES.M
                && Settings.canDrawOverlays(getApplicationContext())) {
            FloatingLogcatService.launch(getApplicationContext());
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
        new Thread() {
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
                        try {
                            final LogItem item = new LogItem(line);
                            mBinding.list.post(new Runnable() {
                                @Override
                                public void run() {
                                    mAdapter.append(item);
                                }
                            });
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
}
