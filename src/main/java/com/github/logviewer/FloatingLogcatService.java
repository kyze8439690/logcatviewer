package com.github.logviewer;


import android.annotation.SuppressLint;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.graphics.PixelFormat;
import android.graphics.Point;
import android.os.Build;
import android.os.IBinder;
import android.util.TypedValue;
import android.view.ContextThemeWrapper;
import android.view.Display;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.WindowManager;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.ListView;

import androidx.annotation.Nullable;

import com.github.logviewer.databinding.LogcatViewerActivityLogcatBinding;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.text.ParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

public class FloatingLogcatService extends Service {

    public static void launch(Context context, List<Pattern> excludeList) {
        ArrayList<String> list = new ArrayList<>();
        for (Pattern pattern : excludeList) {
            list.add(pattern.pattern());
        }
        context.startService(new Intent(context, FloatingLogcatService.class)
                .putStringArrayListExtra("exclude_list", list));
    }

    @Nullable private LogcatViewerActivityLogcatBinding mBinding = null;
    private final LogcatAdapter mAdapter = new LogcatAdapter();
    private volatile boolean mReading = false;
    private final List<Pattern> mExcludeList = new ArrayList<>();
    private Context mThemedContext;

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public void onCreate() {
        super.onCreate();
        mThemedContext = new ContextThemeWrapper(
                this, com.google.android.material.R.style.Theme_MaterialComponents_DayNight
        );
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (mReading) {
            return super.onStartCommand(intent, flags, startId);
        }

        mBinding = LogcatViewerActivityLogcatBinding.inflate(LayoutInflater.from(mThemedContext));
        TypedValue typedValue = new TypedValue();
        if (mBinding != null && mThemedContext.getTheme().resolveAttribute(
                android.R.attr.windowBackground, typedValue, true)) {
            int colorWindowBackground = typedValue.data;
            mBinding.getRoot().setBackgroundColor(colorWindowBackground);
        }

        List<String> excludeList = intent.getStringArrayListExtra("exclude_list");
        for (String pattern : excludeList) {
            mExcludeList.add(Pattern.compile(pattern));
        }

        initViews();
        startReadLogcat();

        return super.onStartCommand(intent, flags, startId);
    }

    @Override
    public void onDestroy() {
        WindowManager wm = (WindowManager) getSystemService(WINDOW_SERVICE);
        if (wm != null && mBinding != null) {
            wm.removeView(mBinding.root);
        }

        stopReadLogcat();
        super.onDestroy();
    }

    @SuppressLint("ClickableViewAccessibility")
    private void initViews() {
        final WindowManager.LayoutParams params;
        final WindowManager wm = (WindowManager) getSystemService(WINDOW_SERVICE);
        if (wm == null || mBinding == null) {
            return;
        } else {
            Display display = wm.getDefaultDisplay();
            Point size = new Point();
            display.getSize(size);
            int width = size.x;
            int height = size.y;

            params = new WindowManager.LayoutParams(
                    Build.VERSION.SDK_INT >= Build.VERSION_CODES.O ?
                            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY :
                            WindowManager.LayoutParams.TYPE_SYSTEM_ALERT,

                    WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                            | WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,

                    PixelFormat.TRANSLUCENT);
            params.alpha = .8f;
            params.dimAmount = 0f;
            params.gravity = Gravity.CENTER;
            params.windowAnimations = android.R.style.Animation_Dialog;

            if (height > width) {
                params.width = (int) (width * .7);
                params.height = (int) (height * .5);
            } else {
                params.width = (int) (width * .7);
                params.height = (int) (height * .8);
            }

            wm.addView(mBinding.root, params);
        }

        mBinding.toolbar.getLayoutParams().height = getResources().getDimensionPixelSize(
                R.dimen.floating_toolbar_height);
        mBinding.toolbar.setNavigationOnClickListener(v -> stopSelf());

        ArrayAdapter<CharSequence> spinnerAdapter = ArrayAdapter.createFromResource(mThemedContext,
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
                LogcatDetailActivity.launch(getApplicationContext(), mAdapter.getItem(position)));

        mBinding.toolbar.setOnTouchListener(new View.OnTouchListener() {

            boolean mIntercepted = false;
            int mLastX;
            int mLastY;
            int mFirstX;
            int mFirstY;
            final int mTouchSlop = ViewConfiguration.get(getApplicationContext()).getScaledTouchSlop();

            @Override
            public boolean onTouch(View v, MotionEvent event) {
                int totalDeltaX = mLastX - mFirstX;
                int totalDeltaY = mLastY - mFirstY;

                switch(event.getActionMasked()) {
                    case MotionEvent.ACTION_DOWN:
                        mLastX = (int) event.getRawX();
                        mLastY = (int) event.getRawY();
                        mFirstX = mLastX;
                        mFirstY = mLastY;
                        break;
                    case MotionEvent.ACTION_UP:
                        if (!mIntercepted) {
                            v.performClick();
                        }
                        break;
                    case MotionEvent.ACTION_MOVE:
                        int deltaX = (int) event.getRawX() - mLastX;
                        int deltaY = (int) event.getRawY() - mLastY;
                        mLastX = (int) event.getRawX();
                        mLastY = (int) event.getRawY();

                        if (Math.abs(totalDeltaX) >= mTouchSlop || Math.abs(totalDeltaY) >= mTouchSlop) {
                            if (event.getPointerCount() == 1) {
                                params.x += deltaX;
                                params.y += deltaY;
                                mIntercepted = true;
                                wm.updateViewLayout(mBinding.root, params);
                            }
                            else{
                                mIntercepted = false;
                            }
                        }else{
                            mIntercepted = false;
                        }
                        break;
                    default:
                        break;
                }
                return mIntercepted;
            }
        });
    }

    private void startReadLogcat() {
        new Thread("logcat-service") {
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
                            if (mBinding != null) {
                                mBinding.list.post(() -> mAdapter.append(item));
                            }
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
