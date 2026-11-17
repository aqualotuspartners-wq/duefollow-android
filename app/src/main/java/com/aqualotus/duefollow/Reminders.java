package com.aqualotus.duefollow;

import android.app.*;
import android.content.*;
import org.json.*;
import java.time.*;

public class Reminders extends BroadcastReceiver {
    static final String CHANNEL = "invoices";

    static SharedPreferences store(Context context) {
        return context.getSharedPreferences("duefollow", Context.MODE_PRIVATE);
    }

    static JSONArray read(Context context) {
        try { return new JSONArray(store(context).getString("invoices", "[]")); }
        catch (JSONException e) { throw new IllegalStateException(e); }
    }

    static PendingIntent alarm(Context context, JSONObject row) {
        Intent intent = new Intent(context, Reminders.class);
        intent.setAction(row.optString("id"));
        return PendingIntent.getBroadcast(context, 0, intent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
    }

    static void schedule(Context context, JSONObject row) {
        AlarmManager alarms = context.getSystemService(AlarmManager.class);
        PendingIntent pending = alarm(context, row);
        alarms.cancel(pending);
        if (row.optBoolean("paused") || row.optLong("paid") >= row.optLong("total")) {
            context.getSystemService(NotificationManager.class).cancel(row.optString("id"), 1);
            return;
        }
        if (row.optBoolean("reminded") && !(row.optBoolean("repeat") &&
                store(context).getBoolean("pro", false))) return;
        long time = LocalDate.parse(row.optString("due")).atTime(9, 0)
                .atZone(ZoneId.systemDefault()).toInstant().toEpochMilli();
        time = Math.max(time, row.optLong("next"));
        alarms.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP,
                Math.max(time, System.currentTimeMillis() + 1000), pending);
    }

    @Override public void onReceive(Context context, Intent intent) {
        JSONArray rows = read(context);
        for (int i = 0; i < rows.length(); i++) {
            JSONObject row = rows.optJSONObject(i);
            if (Intent.ACTION_BOOT_COMPLETED.equals(intent.getAction())) {
                schedule(context, row);
                continue;
            }
            String id = row.optString("id");
            if (!id.equals(intent.getAction()) || row.optBoolean("paused") ||
                    row.optLong("paid") >= row.optLong("total")) continue;
            NotificationManager notifications = context.getSystemService(NotificationManager.class);
            notifications.createNotificationChannel(new NotificationChannel(CHANNEL,
                    "Invoice reminders", NotificationManager.IMPORTANCE_DEFAULT));
            if (!notifications.areNotificationsEnabled()) return;
            Intent open = new Intent(context, MainActivity.class).setAction(id);
            PendingIntent launch = PendingIntent.getActivity(context, 0, open, PendingIntent.FLAG_IMMUTABLE);
            notifications.notify(id, 1, new Notification.Builder(context, CHANNEL)
                    .setSmallIcon(android.R.drawable.ic_dialog_info)
                    .setContentTitle("Time to follow up")
                    .setContentText("Tap to open your invoice")
                    .setVisibility(Notification.VISIBILITY_PRIVATE)
                    .setContentIntent(launch).setAutoCancel(true).build());
            try {
                row.put("reminded", true);
                row.put("next", System.currentTimeMillis() + 3L * 86400000);
            } catch (JSONException e) { throw new IllegalStateException(e); }
            store(context).edit().putString("invoices", rows.toString()).apply();
            schedule(context, row);
        }
    }
}
