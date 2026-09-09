package com.mercato.app

import android.app.AlarmManager
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import java.util.Calendar

/**
 * The daily reminder: one local notification, at the same hour every day,
 * saying the day's challenge is up.
 *
 * Local rather than push. The game is offline, it has no account and no
 * server, and a reminder that says "come and play" needs to know nothing
 * about the player to be worth sending. An alarm and a string do the job that
 * a messaging SDK, a token registry and a privacy disclosure would otherwise
 * be needed for.
 *
 * The alarm is inexact. An exact one needs a permission Play asks a game to
 * justify, and nobody is waiting on the second hand for a quiz reminder.
 */
object Reminders {

    /** Early evening: after work, before the day is written off. */
    private const val HOUR = 19

    private const val CHANNEL_ID = "daily"
    private const val REQUEST_CODE = 1001
    const val NOTIFICATION_ID = 1

    private fun intent(context: Context): PendingIntent =
        PendingIntent.getBroadcast(
            context,
            REQUEST_CODE,
            Intent(context, ReminderReceiver::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

    /** Next [HOUR] o'clock, today if it is still ahead, tomorrow otherwise. */
    private fun nextTrigger(now: Long): Long {
        val c = Calendar.getInstance().apply {
            timeInMillis = now
            set(Calendar.HOUR_OF_DAY, HOUR)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        if (c.timeInMillis <= now) c.add(Calendar.DAY_OF_YEAR, 1)
        return c.timeInMillis
    }

    fun schedule(context: Context) {
        val alarms = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return
        alarms.setInexactRepeating(
            AlarmManager.RTC_WAKEUP,
            nextTrigger(System.currentTimeMillis()),
            AlarmManager.INTERVAL_DAY,
            intent(context),
        )
    }

    fun cancel(context: Context) {
        val alarms = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return
        alarms.cancel(intent(context))
    }

    /** Called on every start so a reinstall or a reboot re-arms the alarm. */
    fun sync(context: Context, enabled: Boolean) {
        if (enabled) schedule(context) else cancel(context)
    }

    fun notify(context: Context) {
        val manager =
            context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager ?: return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            manager.createNotificationChannel(
                NotificationChannel(
                    CHANNEL_ID,
                    context.getString(R.string.notifChannel),
                    NotificationManager.IMPORTANCE_DEFAULT,
                )
            )
        }
        val open = PendingIntent.getActivity(
            context,
            0,
            Intent(context, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val notification = Notification.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(context.getString(R.string.notifTitle))
            .setContentText(context.getString(R.string.notifBody))
            .setAutoCancel(true)
            .setContentIntent(open)
            .build()
        runCatching { manager.notify(NOTIFICATION_ID, notification) }
    }
}

/** Fires the reminder, and re-arms it after a reboot wiped the alarm. */
class ReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        if (intent?.action == Intent.ACTION_BOOT_COMPLETED) {
            Reminders.schedule(context)
            return
        }
        Reminders.notify(context)
    }
}
