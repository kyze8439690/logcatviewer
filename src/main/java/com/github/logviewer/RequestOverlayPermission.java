package com.github.logviewer;

import static android.os.Build.VERSION_CODES.M;

import android.content.Context;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.net.Uri;
import android.os.Build.VERSION;
import android.provider.Settings;
import android.widget.Toast;

import androidx.activity.result.contract.ActivityResultContract;
import androidx.annotation.NonNull;

import org.jetbrains.annotations.Nullable;

import kotlin.Unit;


public class RequestOverlayPermission extends ActivityResultContract<Unit, Boolean> {

    private final Context mContext;

    public RequestOverlayPermission(Context context) {
        mContext = context;
    }

    private Intent getIntent(Context context) {
        if (VERSION.SDK_INT >= M) {
            return new Intent(
                    Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                    Uri.parse("package:" + context.getPackageName())
            );
        } else {
            throw new IllegalStateException();
        }
    }

    @NonNull
    @Override
    public Intent createIntent(@NonNull Context context, Unit unused) {
        return getIntent(context);
    }

    @Override
    public Boolean parseResult(int i, @Nullable Intent intent) {
        return VERSION.SDK_INT >= M && Settings.canDrawOverlays(mContext);
    }

    @Nullable
    @Override
    public SynchronousResult<Boolean> getSynchronousResult(@NonNull Context context, Unit input) {
        if (VERSION.SDK_INT < M || Settings.canDrawOverlays(context)) {
            return new SynchronousResult<>(true);
        }
        if (context.getPackageManager().queryIntentActivities(getIntent(context), 0).isEmpty()) {
            return new SynchronousResult<>(false);
        } else {
            ApplicationInfo applicationInfo = context.getApplicationInfo();
            int stringId = applicationInfo.labelRes;
            String appName = stringId == 0 ? applicationInfo.nonLocalizedLabel.toString() :
                    context.getString(stringId);
            String toast = context.getString(R.string.logcat_viewer_grant_permission_hint, appName);
            Toast.makeText(context, toast, Toast.LENGTH_SHORT).show();
            return null;
        }
    }
}
