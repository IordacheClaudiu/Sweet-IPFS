package services;

import android.app.ActivityManager;
import android.content.Context;
import android.util.Log;

import java.util.List;

public class ServiceUtils {
    private static final String LOG_TAG = ServiceUtils.class.getName();

    public static boolean isServiceRunning(Context context, Class<?> serviceClass) {
        final ActivityManager activityManager = (ActivityManager) context.getSystemService(Context.ACTIVITY_SERVICE);
        final List<ActivityManager.RunningServiceInfo> services = activityManager.getRunningServices(Integer.MAX_VALUE);

        for (ActivityManager.RunningServiceInfo runningServiceInfo : services) {
            Log.d(LOG_TAG, String.format("Service:%s", runningServiceInfo.service.getClassName()));
            if (runningServiceInfo.service.getClassName().equals(serviceClass.getName())) {
                return true;
            }
        }
        return false;
    }
}
