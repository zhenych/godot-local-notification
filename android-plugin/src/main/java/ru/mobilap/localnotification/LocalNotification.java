package ru.mobilap.localnotification;

import android.Manifest;
import android.app.Activity;
import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.util.Log;
import android.view.View;

import androidx.annotation.CallSuper;
import androidx.annotation.NonNull;
import androidx.collection.ArraySet;
import androidx.core.app.ActivityCompat;
import androidx.core.app.NotificationManagerCompat;

import org.godotengine.godot.Dictionary;
import org.godotengine.godot.Godot;
import org.godotengine.godot.plugin.GodotPlugin;
import org.godotengine.godot.plugin.SignalInfo;
import org.godotengine.godot.plugin.UsedByGodot;

import java.util.Arrays;
import java.util.Calendar;
import java.util.List;
import java.util.Set;

public class LocalNotification extends GodotPlugin {
    private static final int REQUEST_PERMISSION_CODE = 1001;
    private Activity activity;

    private final String TAG = LocalNotification.class.getName();
    private Dictionary notificationData = new Dictionary();
    private String action = null;
    private String uri = null;
    private Boolean intentWasChecked = false;

    public LocalNotification(Godot godot)
    {
        super(godot);

        activity = getActivity();

        intentWasChecked = false;
    }

    @Override
    public String getPluginName() {
        return "LocalNotification";
    }

    @Override
    public List<String> getPluginMethods() {
        return Arrays.asList(
                "init",
                "showLocalNotification",
                "showRepeatingNotification",
                "cancelLocalNotification",
                "cancelAllNotifications",
                "isInited",
                "isEnabled",
                "register_remote_notification",
                "get_device_token",
                "get_notification_data",
                "get_deeplink_action",
                "get_deeplink_uri"
        );
    }

    @Override
    public Set<SignalInfo> getPluginSignals() {
        Set<SignalInfo> signals = new ArraySet<>();

        signals.add(new SignalInfo("permission_result", Boolean.class));

        return signals;
    }

    @Override
    public View onMainCreate(Activity activity) {
        return null;
    }

    // Public methods
    @UsedByGodot
    public void init() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) {
            return;
        }

        ActivityCompat.requestPermissions(activity, new String[]{Manifest.permission.POST_NOTIFICATIONS}, REQUEST_PERMISSION_CODE);
    }

    @UsedByGodot
    public boolean isInited() {
        return true;
    }

    @UsedByGodot
    public boolean isEnabled() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) {
            return true;
        }

        return NotificationManagerCompat.from(activity).areNotificationsEnabled();
    }

    @UsedByGodot
    public void showLocalNotification(String message, String title, int delay_seconds, int tag) {
        if(delay_seconds <= 0) return;

        Log.d(TAG, "showLocalNotification: "+message+", "+Integer.toString(delay_seconds)+", "+Integer.toString(tag));

        PendingIntent sender = getPendingIntent(message, title, tag);

        Calendar calendar = Calendar.getInstance();
        calendar.setTimeInMillis(System.currentTimeMillis());
        calendar.add(Calendar.SECOND, delay_seconds);

        AlarmManager am = (AlarmManager) activity.getApplicationContext().getSystemService(Context.ALARM_SERVICE);

        am.set(AlarmManager.RTC_WAKEUP, calendar.getTimeInMillis(), sender);
    }

    @UsedByGodot
    public void showRepeatingNotification(String message, String title, int delay_seconds, int tag, int repeat_interval_seconds) {
        if(delay_seconds <= 0) return;

        Log.d(TAG, "showRepeatingNotification: "+message+", "+Integer.toString(delay_seconds)+", "+Integer.toString(tag)+" Repeat after: "+Integer.toString(repeat_interval_seconds));

        PendingIntent sender = getPendingIntent(message, title, tag);

        Calendar calendar = Calendar.getInstance();
        calendar.setTimeInMillis(System.currentTimeMillis());
        calendar.add(Calendar.SECOND, delay_seconds);

        AlarmManager am = (AlarmManager) activity.getApplicationContext().getSystemService(Context.ALARM_SERVICE);

        am.setRepeating(AlarmManager.RTC_WAKEUP, calendar.getTimeInMillis(), repeat_interval_seconds * 1000L, sender);
    }

    @UsedByGodot
    public void cancelLocalNotification(int tag) {
        AlarmManager am = (AlarmManager) activity.getApplicationContext().getSystemService(Context.ALARM_SERVICE);
        PendingIntent sender = getPendingIntent("", "", tag);

        am.cancel(sender);
        sender.cancel();
    }

    @UsedByGodot
    public void cancelAllNotifications() {
        Log.w(TAG, "cancelAllNotifications not implemented");
    }

    @UsedByGodot
    public void register_remote_notification() {
    }

    @UsedByGodot
    public String get_device_token() {
        return "";
    }

    // Internal methods

    private PendingIntent getPendingIntent(String message, String title, int tag) {
        Intent intent = new Intent(activity.getApplicationContext(), LocalNotificationReceiver.class);

        intent.putExtra("notification_id", tag);
        intent.putExtra("message", message);
        intent.putExtra("title", title);

        return PendingIntent.getBroadcast(activity.getApplicationContext(), tag, intent, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
    }

    @Override public void onMainActivityResult (int requestCode, int resultCode, Intent data)
    {
    }

    @Override public void onMainResume() {
        intentWasChecked = false;
    }

    @CallSuper
    @Override
    public void onMainRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        if (requestCode != REQUEST_PERMISSION_CODE) {
            return;
        }

        boolean granted = grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED;

        emitSignal("permission_result", granted);
    }

    private void checkIntent() {
        Log.w(TAG, "I'm going to check application intent");
        Intent intent = Godot.getCurrentIntent();
        if(intent == null) {
            Log.d(TAG, "No intent in app activity");
            return;
        }
        Log.w(TAG, "The intent isn't null, so check it closely.");
        if(intent.getExtras() != null) {
            Bundle extras = Godot.getCurrentIntent().getExtras();
            Log.d(TAG, "Extras:" + extras.toString());
            notificationData = new Dictionary();
            for (String key : extras.keySet()) {
                Object value = extras.get(key);
                try {
                    notificationData.put(key, value);
                    Log.w(TAG, "Get new value " + value.toString() + " for key " + key);
                } catch(Exception e) {
                    Log.d(TAG, "Conversion error: " + e.toString());
                    e.printStackTrace();
                }
            }
            Log.d(TAG, "Extras content: " + notificationData.toString());
        } else {
            Log.d(TAG, "No extra bundle in app activity!");
        }
        if(intent.getAction() != null) {
            Log.w(TAG, "Get deeplink action from intent");
            action = intent.getAction();
        }
        if(intent.getData() != null) {
            Log.w(TAG, "Get uri from intent");
            uri = intent.getData().toString();
        }
        intentWasChecked = true;
    }

    @UsedByGodot
    public Dictionary get_notification_data() {
        if(!intentWasChecked) checkIntent();
        return notificationData;
    }

    @UsedByGodot
    public String get_deeplink_action() {
        if(!intentWasChecked) checkIntent();
        return action;
    }

    @UsedByGodot
    public String get_deeplink_uri() {
        if(!intentWasChecked) checkIntent();
        return uri;
    }
}
