package com.github.logviewer;


import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.view.MenuItem;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import com.github.logviewer.databinding.LogcatViewerActivityLogcatDetailBinding;

import java.text.SimpleDateFormat;
import java.util.Locale;

public class LogcatDetailActivity extends AppCompatActivity {

    private static final String CONTENT_TEMPLATE
            = "Time: %s\nPid: %d\nTid: %d\nPriority: %s\nTag: %s\n\nContent: \n%s";

    public static void launch(Context context, LogItem log) {
        Intent intent = new Intent(context, LogcatDetailActivity.class)
                .putExtra("log", log);
        if (!(context instanceof Activity)) {
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        }
        context.startActivity(intent);
    }

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        LogcatViewerActivityLogcatDetailBinding binding =
                LogcatViewerActivityLogcatDetailBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        }

        LogItem log = getIntent().getParcelableExtra("log");
        if (log != null) {
            binding.content.setText(String.format(Locale.getDefault(), CONTENT_TEMPLATE,
                    new SimpleDateFormat("MM-dd hh:mm:ss.SSS", Locale.getDefault()).format(
                            log.time), log.processId, log.threadId, log.priority, log.tag,
                    log.content));
        }
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == android.R.id.home) {
            finish();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }
}
