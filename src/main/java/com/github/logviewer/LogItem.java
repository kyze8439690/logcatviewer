package com.github.logviewer;

import android.annotation.SuppressLint;

import androidx.annotation.ColorRes;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.Locale;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class LogItem {

    private static final String PRIORITY_VERBOSE = "V";
    private static final String PRIORITY_DEBUG = "D";
    private static final String PRIORITY_INFO = "I";
    private static final String PRIORITY_WARNING = "W";
    private static final String PRIORITY_ERROR = "E";
    private static final String PRIORITY_FATAL = "F";

    private static final Pattern sLogcatPattern = Pattern.compile(
              "([0-9]{2}-[0-9]{2} [0-9]{2}:[0-9]{2}:[0-9]{2}\\.[0-9]{3})\\s+(\\d+)\\s+(\\d+)\\s+([VDIWEF])\\s+([^:]+):\\s*(.*)");

    @SuppressLint("ConstantLocale")
    private static final SimpleDateFormat sDateFormat =
        new SimpleDateFormat("MM-dd HH:mm:ss.SSS", Locale.getDefault());

    private static final HashMap<String, Integer> LOGCAT_COLORS = new HashMap<String, Integer>() {{
        put(PRIORITY_VERBOSE, R.color.logcat_verbose);
        put(PRIORITY_DEBUG, R.color.logcat_debug);
        put(PRIORITY_INFO, R.color.logcat_info);
        put(PRIORITY_WARNING, R.color.logcat_warning);
        put(PRIORITY_ERROR, R.color.logcat_error);
        put(PRIORITY_FATAL, R.color.logcat_fatal);
    }};

    private static final ArrayList<String> SUPPORTED_FILTERS = new ArrayList<String>() {{
        add(PRIORITY_VERBOSE);
        add(PRIORITY_DEBUG);
        add(PRIORITY_INFO);
        add(PRIORITY_WARNING);
        add(PRIORITY_ERROR);
        add(PRIORITY_FATAL);
    }};

    static final Pattern IGNORED_LOG = Pattern.compile("--------- beginning of (.*)");

    public final Date time;
    public final int processId;
    public final int threadId;
    public final String priority;
    public final String tag;
    public final String content;
    public final String origin;

    LogItem(String line) throws IllegalStateException, ParseException {
        Matcher matcher = sLogcatPattern.matcher(line);
        if (!matcher.find()) {
            throw new IllegalStateException("logcat pattern not match: " + line);
        }

        String timeText = matcher.group(1);
        String pidText = matcher.group(2);
        String tidText = matcher.group(3);
        String tagText = matcher.group(4);
        String prefixText = matcher.group(5);
        String contentText = matcher.group(6);

        time = sDateFormat.parse(timeText);
        processId = Integer.parseInt(pidText);
        threadId = Integer.parseInt(tidText);
        priority = tagText;
        tag = prefixText;
        content = contentText;
        origin = line;
    }

    @ColorRes
    int getColorRes() {
        return LOGCAT_COLORS.get(priority);
    }

    boolean isFiltered(String filter) {
        return SUPPORTED_FILTERS.indexOf(priority) < SUPPORTED_FILTERS.indexOf(filter);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        LogItem logItem = (LogItem) o;
        return processId == logItem.processId &&
                threadId == logItem.threadId &&
                Objects.equals(time, logItem.time) &&
                Objects.equals(priority, logItem.priority) &&
                Objects.equals(tag, logItem.tag) &&
                Objects.equals(content, logItem.content) &&
                Objects.equals(origin, logItem.origin);
    }

    @Override
    public int hashCode() {
        return Objects.hash(time, processId, threadId, priority, tag, content, origin);
    }
}
