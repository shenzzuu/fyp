package com.example.planprep;

import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Build;
import androidx.core.app.NotificationCompat;
import com.google.firebase.firestore.FirebaseFirestore;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;

public class NotificationReceiver extends BroadcastReceiver {

    private static final String CHANNEL_ID = "planprep_channel_01";

    @Override
    public void onReceive(Context context, Intent intent) {
        final PendingResult pendingResult = goAsync();

        // 1. Get content (Include "type")
        String title = intent.getStringExtra("title");
        String message = intent.getStringExtra("message");
        int notificationId = intent.getIntExtra("id", 0);
        String userId = intent.getStringExtra("userId");
        String type = intent.getStringExtra("type"); // <--- GET TYPE

        // 2. Show UI Notification
        showSystemNotification(context, title, message, notificationId);

        // 3. Save to Firebase
        saveNotificationToFirebase(userId, title, message, type, pendingResult);
    }

    private void saveNotificationToFirebase(String userId, String title, String message, String type, PendingResult pendingResult) {
        if (userId == null || userId.isEmpty()) {
            pendingResult.finish();
            return;
        }

        FirebaseFirestore db = FirebaseFirestore.getInstance();

        Map<String, Object> notifMap = new HashMap<>();
        notifMap.put("userId", userId);
        notifMap.put("title", title);
        notifMap.put("message", message);
        notifMap.put("type", type);     // <--- ADDED: Matches your DB
        notifMap.put("read", false);    // <--- CHANGED: "read" instead of "isRead"
        notifMap.put("timestamp", new Date());

        db.collection("notifications") // Ensure this matches your collection name
                .add(notifMap)
                .addOnCompleteListener(task -> {
                    pendingResult.finish();
                });
    }

    private void showSystemNotification(Context context, String title, String message, int notificationId) {
        Intent activityIntent = new Intent(context, MainActivity.class);
        activityIntent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);

        PendingIntent contentIntent = PendingIntent.getActivity(
                context, 0, activityIntent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );

        NotificationManager manager = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
        createNotificationChannel(manager);

        Bitmap largeIcon = BitmapFactory.decodeResource(context.getResources(), R.drawable.logo);

        NotificationCompat.Builder builder = new NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(R.drawable.logo)
                .setLargeIcon(largeIcon)
                .setContentTitle(title)
                .setContentText(message)
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setContentIntent(contentIntent)
                .setAutoCancel(true)
                .setDefaults(NotificationCompat.DEFAULT_ALL);

        if (manager != null) {
            manager.notify(notificationId, builder.build());
        }
    }

    private void createNotificationChannel(NotificationManager manager) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                    CHANNEL_ID, "Meal Planner Alerts", NotificationManager.IMPORTANCE_HIGH
            );
            manager.createNotificationChannel(channel);
        }
    }
}