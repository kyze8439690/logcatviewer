package com.github.logviewer;


import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.support.annotation.Nullable;
import android.support.v7.app.AppCompatActivity;
import android.view.MenuItem;
import android.widget.TextView;


import java.text.SimpleDateFormat;
import java.util.Locale;

public class LogcatDetailActivity extends AppCompatActivity {

    private static final SimpleDateFormat DATE_FORMAT = new SimpleDateFormat(
            "MM-dd hh:mm:ss.SSS", Locale.getDefault());
    private static final String CONTENT_TEMPLATE
            = "Time: %s\nPid: %d\nTid: %d\nPriority: %s\nTag: %s\n\nContent: \n%s";

    public static void launch(Context context, LogItem log) {
        context.startActivity(new Intent(context, LogcatDetailActivity.class)
                .putExtra("log", log));
    }

    private TextView mContent;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_logcat_detail);
        mContent = findViewById(R.id.content);

        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        }

        LogItem log = getIntent().getParcelableExtra("log");
        mContent.setText(String.format(Locale.getDefault(), CONTENT_TEMPLATE,
                DATE_FORMAT.format(log.time), log.processId, log.threadId, log.priority, log.tag,
                log.content));
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
