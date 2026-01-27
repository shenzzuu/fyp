package com.example.planprep;

import android.annotation.SuppressLint;
import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.util.Log;
import android.widget.Toast;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import java.util.Calendar;

public class NotificationScheduler {

    public static void scheduleMealNotifications(Context context, int mealId, String mealName, long mealTimeMillis) {
        try {
            FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
            String userId = (user != null) ? user.getUid() : "";
            if (userId.isEmpty()) return;

            AlarmManager alarmManager = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);

            // CHECK PERMISSION FOR ANDROID 12+ (API 31)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                if (!alarmManager.canScheduleExactAlarms()) {
                    Log.e("Scheduler", "Permission for exact alarms not granted.");
                    // Optional: You could show a Toast here or prompt user to settings
                    return;
                }
            }

            int baseId = mealId * 100;

            // 1. Reminder: 5 Minutes Before
            long fiveMinutesBefore = mealTimeMillis - (5 * 60 * 1000);
            if (fiveMinutesBefore > System.currentTimeMillis()) {
                scheduleAlarm(context, alarmManager, baseId + 1, "Get Ready!", "5 mins until " + mealName, fiveMinutesBefore, userId, "meal_reminder");
            }

            // 2. Reminder: At Meal Time
            if (mealTimeMillis > System.currentTimeMillis()) {
                scheduleAlarm(context, alarmManager, baseId + 2, "Meal Time!", "Time for " + mealName, mealTimeMillis, userId, "meal_reminder");
            }
        } catch (Exception e) {
            Log.e("Scheduler", "Error scheduling meal: " + e.getMessage());
        }
    }

    public static void scheduleDailyCheck(Context context) {
        try {
            FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
            String userId = (user != null) ? user.getUid() : "";
            if (userId.isEmpty()) return;

            Calendar calendar = Calendar.getInstance();
            calendar.set(Calendar.HOUR_OF_DAY, 20);
            calendar.set(Calendar.MINUTE, 0);
            calendar.set(Calendar.SECOND, 0);

            if (calendar.getTimeInMillis() < System.currentTimeMillis()) {
                calendar.add(Calendar.DAY_OF_YEAR, 1);
            }

            AlarmManager alarmManager = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
            // Check permission again if needed, or wrap in try-catch
            scheduleAlarm(context, alarmManager, 99999, "Plan Tomorrow", "Don't forget to plan your meals!", calendar.getTimeInMillis(), userId, "daily_reminder");
        } catch (Exception e) {
            Log.e("Scheduler", "Error scheduling daily check: " + e.getMessage());
        }
    }

    @SuppressLint("ScheduleExactAlarm")
    private static void scheduleAlarm(Context context, AlarmManager alarmManager, int notifId, String title, String msg, long time, String userId, String type) {
        try {
            Intent intent = new Intent(context, NotificationReceiver.class);
            intent.putExtra("title", title);
            intent.putExtra("message", msg);
            intent.putExtra("id", notifId);
            intent.putExtra("userId", userId);
            intent.putExtra("type", type);

            PendingIntent pendingIntent = PendingIntent.getBroadcast(
                    context, notifId, intent,
                    PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
            );

            if (alarmManager != null) {
                alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, time, pendingIntent);
            }
        } catch (SecurityException se) {
            Log.e("Scheduler", "Security Exception (Permission missing): " + se.getMessage());
        } catch (Exception e) {
            Log.e("Scheduler", "General Error: " + e.getMessage());
        }
    }
}