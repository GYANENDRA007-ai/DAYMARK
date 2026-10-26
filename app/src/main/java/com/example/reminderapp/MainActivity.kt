package com.example.reminderapp

import android.Manifest
import android.app.Activity
import android.app.DatePickerDialog
import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.text.InputType
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.RadioButton
import android.widget.RadioGroup
import android.net.Uri
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.json.JSONArray
import org.json.JSONObject
import java.util.Calendar
import java.util.Locale
import kotlinx.coroutines.delay

data class KaamTask(
    val id: Long = System.currentTimeMillis(),
    val title: String,
    val details: String,
    // The user's intent (Task / Timer / Alarm / Alert) is distinct from its schedule.
    // Default preserves compatibility with tasks saved by older KAAM versions.
    val kind: String = "TASK",
    val whenType: String = "ANYTIME",
    val whenValue: String = "",
    val repeatType: String = "NONE",
    val repeatValue: Int = 0,
    // Canonical interval in minutes. Zero preserves compatibility with older hour-based records.
    val repeatIntervalMinutes: Int = 0,
    // 0 means unlimited; otherwise the total number of deliveries allowed.
    // Kept separate from repeatValue, which means interval hours.
    val repeatLimit: Int = 0,
    val repeatCount: Int = 0,
    // Optional same-day daily window for interval repeats; blank means all-day.
    val repeatWindowStart: String = "",
    val repeatWindowEnd: String = "",
    val completed: Boolean = false,
    // Persisted timestamps make daily/weekly reports meaningful.
    val createdAt: Long = System.currentTimeMillis(),
    val completedAt: Long? = null,
    // Absolute start time keeps a countdown's deadline stable across device restarts.
    val timerStartedAt: Long? = null,
    // Optional date-only deadline; stored at the end of the selected local day.
    val dueAt: Long? = null
)

private val Black = Color(0xFF050708)
private val Panel = Color(0xFF0C1213)
private val Border = Color(0xFF253032)
private val White = Color(0xFFE8EBE9)
private val Muted = Color(0xFF8C9795)
private val Dim = Color(0xFF596361)
private val WaterDark = Color(0xFF071517)
private val WaterMid = Color(0xFF0B2023)

private const val PREFS_NAME = "kaam_storage"
private const val TASKS_KEY = "tasks"
private const val TASKS_BACKUP_KEY = "tasks_last_valid_backup"
private const val CHANNEL_ID = "kaam_reminders"
private const val PENDING_EXACT_TASK_IDS_KEY = "pending_exact_alarm_task_ids"
private val pendingExactAlarmLock = Any()
private const val ACTION_NOTIFICATION_DONE = "com.example.reminderapp.NOTIFICATION_DONE"
private const val ACTION_NOTIFICATION_SNOOZE = "com.example.reminderapp.NOTIFICATION_SNOOZE"
private const val SNOOZE_MINUTES = 10
private const val ACTION_TASKS_CHANGED = "com.example.reminderapp.TASKS_CHANGED"


/* ============================================================
   MAIN ACTIVITY
   ============================================================ */

class MainActivity : ComponentActivity() {

    // A notification tap should take the user to the task that raised it,
    // whether MainActivity is being created or is already on top of the stack.
    internal var notificationTaskToOpen by mutableStateOf<Long?>(null)
        private set

    // Lets notification actions refresh an already-open Compose screen without
    // launching a second activity or making storage dependent on the UI process.
    internal var taskStateRefreshToken by mutableIntStateOf(0)
        private set

    private val tasksChangedReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            if (intent.action == ACTION_TASKS_CHANGED) taskStateRefreshToken++
        }
    }
    private var tasksChangedReceiverRegistered = false

    private fun captureNotificationTask(intent: Intent?) {
        val taskId = intent?.getLongExtra("open_task_id", -1L) ?: -1L
        notificationTaskToOpen = taskId.takeIf { it > 0L }
        intent?.removeExtra("open_task_id")
    }

    internal fun consumeNotificationTask(): Long? {
        val taskId = notificationTaskToOpen
        notificationTaskToOpen = null
        return taskId
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        captureNotificationTask(intent)
    }

    private val notificationPermissionLauncher =
        registerForActivityResult(
            ActivityResultContracts.RequestPermission()
        ) { }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        captureNotificationTask(intent)

        createNotificationChannel(this)

        if (
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)
            != PackageManager.PERMISSION_GRANTED
        ) {
            notificationPermissionLauncher.launch(
                Manifest.permission.POST_NOTIFICATIONS
            )
        }

        setContent {
            KAAMHome()
        }
    }

    override fun onStart() {
        super.onStart()
        if (!tasksChangedReceiverRegistered) {
            ContextCompat.registerReceiver(
                this,
                tasksChangedReceiver,
                IntentFilter(ACTION_TASKS_CHANGED),
                ContextCompat.RECEIVER_NOT_EXPORTED
            )
            tasksChangedReceiverRegistered = true
        }
        // Also refresh after returning from Settings/notification shade/background,
        // where an action may have changed persisted task state while stopped.
        taskStateRefreshToken++
    }

    override fun onStop() {
        if (tasksChangedReceiverRegistered) {
            unregisterReceiver(tasksChangedReceiver)
            tasksChangedReceiverRegistered = false
        }
        super.onStop()
    }

    override fun onResume() {
        super.onResume()
        // Do not reschedule every saved task here: doing so would reset timer
        // and interval trigger times. Only retry tasks explicitly deferred
        // while Android's exact-alarm access screen was open.
        if (!canScheduleExactAlarms(this)) return
        val prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        // Atomically claim the current retry batch. Without the shared lock, a
        // concurrent receiver could add an ID between snapshot and remove and
        // have its newly deferred reminder erased by this foreground retry.
        val pendingIds = synchronized(pendingExactAlarmLock) {
            val ids = prefs.getStringSet(PENDING_EXACT_TASK_IDS_KEY, emptySet())
                ?.toSet().orEmpty()
            if (ids.isNotEmpty()) prefs.edit().remove(PENDING_EXACT_TASK_IDS_KEY).commit()
            ids
        }
        if (pendingIds.isEmpty()) return

        val tasksById = loadTasks(this).associateBy { it.id.toString() }
        // scheduleReminder can add failed tasks back after this batch is claimed.
        pendingIds.forEach { id ->
            val task = tasksById[id] ?: return@forEach
            if (task.completed) return@forEach
            try {
                scheduleReminder(this, task)
            } catch (error: Exception) {
                // Do not let one corrupt/unsupported task block recovery of
                // the remaining reminders. Keep this ID queued for the next
                // foreground retry rather than silently losing its alarm.
                rememberPendingExactAlarm(this, task.id, add = true)
                Log.w("KAAM", "Could not retry deferred reminder ${task.id}", error)
            }
        }
    }
}


/* ============================================================
   STORAGE
   ============================================================ */

fun saveTasks(
    context: Context,
    tasks: List<KaamTask>
) {
    val array = JSONArray()

    tasks.forEach { task ->
        val obj = JSONObject()

        obj.put("id", task.id)
        obj.put("title", task.title)
        obj.put("details", task.details)
        obj.put("kind", task.kind)
        obj.put("whenType", task.whenType)
        obj.put("whenValue", task.whenValue)
        obj.put("repeatType", task.repeatType)
        obj.put("repeatValue", task.repeatValue)
        obj.put("repeatIntervalMinutes", task.repeatIntervalMinutes.coerceAtLeast(0))
        obj.put("repeatLimit", task.repeatLimit.coerceAtLeast(0))
        obj.put("repeatCount", task.repeatCount.coerceAtLeast(0))
        obj.put("repeatWindowStart", task.repeatWindowStart)
        obj.put("repeatWindowEnd", task.repeatWindowEnd)
        obj.put("completed", task.completed)
        obj.put("createdAt", task.createdAt)
        if (task.completedAt != null) obj.put("completedAt", task.completedAt)
        if (task.timerStartedAt != null) obj.put("timerStartedAt", task.timerStartedAt)
        if (task.dueAt != null) obj.put("dueAt", task.dueAt)

        array.put(obj)
    }

    val preferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    val serialized = array.toString()

    // Write the same freshly serialized, app-generated payload to both keys in
    // one SharedPreferences transaction. The backup therefore represents the
    // latest committed task state (including intentional deletions), not an old
    // snapshot that could resurrect tasks the user removed.
    // Commit the task snapshot before callers schedule/cancel its alarms. apply()
    // updates the in-memory map immediately but writes to disk asynchronously; a
    // process kill in that gap could leave a newly scheduled alarm whose task is
    // missing from durable storage when the receiver wakes up. The payload is a
    // small local JSON snapshot, so prefer durable ordering over a fire-and-forget
    // write for this reliability-critical state.
    val committed = preferences.edit()
        .putString(TASKS_KEY, serialized)
        .putString(TASKS_BACKUP_KEY, serialized)
        .commit()
    if (!committed) {
        Log.e("KAAM", "Task snapshot could not be committed to persistent storage")
    }
}

/**
 * Loads saved tasks defensively. A malformed individual record is skipped rather
 * than discarding every valid task. If the main JSON payload is damaged, the last
 * valid backup is restored and used instead of silently returning an empty list.
 */
fun loadTasks(context: Context): List<KaamTask> {
    val preferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun parseTasks(payload: String): List<KaamTask> {
        val array = JSONArray(payload)
        val result = mutableListOf<KaamTask>()
        // Keep duplicate-ID recovery linear even when an imported/corrupt backup
        // contains many records. The first valid record for an ID wins.
        val seenTaskIds = HashSet<Long>()
        for (i in 0 until array.length()) {
            val obj = try {
                array.getJSONObject(i)
            } catch (_: Exception) {
                continue
            }
            val task = try {
                val id = obj.getLong("id")
                // Reminder IDs are also used as PendingIntent identities. Reject
                // non-positive IDs (which receivers intentionally ignore) so a
                // corrupt record cannot become an invisible, unmanageable task.
                if (id <= 0L) continue
                val title = obj.getString("title").trim()
                if (title.isBlank()) continue
                KaamTask(
                    id = id,
                    title = title,
                    details = obj.optString("details", ""),
                    kind = obj.optString("kind", "TASK").takeIf { it in setOf("TASK", "TIMER", "ALARM", "ALERT") } ?: "TASK",
                    whenType = obj.optString("whenType", "ANYTIME").takeIf { it in setOf("ANYTIME", "MORNING", "AFTERNOON", "EVENING", "DURATION", "TIMER", "CUSTOM") } ?: "ANYTIME",
                    whenValue = obj.optString("whenValue", ""),
                    repeatType = obj.optString("repeatType", "NONE").takeIf { it in setOf("NONE", "DAILY", "INTERVAL") } ?: "NONE",
                    repeatValue = obj.optInt("repeatValue", 0).coerceAtLeast(0),
                    repeatIntervalMinutes = if (obj.optString("repeatType", "NONE") == "INTERVAL") {
                        canonicalIntervalMinutes(obj.optInt("repeatIntervalMinutes", 0), obj.optInt("repeatValue", 0))
                    } else {
                        obj.optInt("repeatIntervalMinutes", 0).coerceAtLeast(0)
                    },
                    repeatLimit = obj.optInt("repeatLimit", 0).coerceAtLeast(0),
                    repeatCount = obj.optInt("repeatCount", 0).coerceAtLeast(0),
                    repeatWindowStart = obj.optString("repeatWindowStart", ""),
                    repeatWindowEnd = obj.optString("repeatWindowEnd", ""),
                    completed = obj.optBoolean("completed", false),
                    createdAt = obj.optLong("createdAt", id),
                    completedAt = if (obj.has("completedAt") && !obj.isNull("completedAt")) obj.optLong("completedAt") else null,
                    timerStartedAt = if (obj.has("timerStartedAt") && !obj.isNull("timerStartedAt")) obj.optLong("timerStartedAt") else null,
                    dueAt = if (obj.has("dueAt") && !obj.isNull("dueAt")) obj.optLong("dueAt").takeIf { it > 0L } else null
                )
            } catch (_: Exception) {
                null
            }
            // A duplicate ID would make two saved tasks share one PendingIntent;
            // the later task could overwrite or cancel the earlier task's alarm.
            // Keep the first valid record deterministically and skip collisions.
            if (task != null && seenTaskIds.add(task.id)) result.add(task)
        }
        if (array.length() > 0 && result.isEmpty()) {
            throw IllegalStateException("Task payload contains no valid task records")
        }
        return result
    }

    val current = preferences.getString(TASKS_KEY, null)
    if (current.isNullOrBlank()) {
        // A missing/blank primary payload is also a recovery case; do not ignore
        // a valid last-known-good backup just because the key disappeared.
        val backup = preferences.getString(TASKS_BACKUP_KEY, null)
        if (backup.isNullOrBlank()) return emptyList()
        return try {
            val recovered = parseTasks(backup)
            preferences.edit().putString(TASKS_KEY, backup).apply()
            recovered
        } catch (_: Exception) {
            emptyList()
        }
    }

    return try {
        parseTasks(current)
    } catch (_: Exception) {
        val backup = preferences.getString(TASKS_BACKUP_KEY, null)
        if (backup.isNullOrBlank()) return emptyList()
        try {
            val recovered = parseTasks(backup)
            preferences.edit().putString(TASKS_KEY, backup).apply()
            recovered
        } catch (_: Exception) {
            emptyList()
        }
    }
}


/** Generate a positive task ID that cannot collide with an already-saved task. */
private fun nextTaskId(context: Context): Long {
    val latestSavedId = loadTasks(context).maxOfOrNull { it.id } ?: 0L
    val nextAfterSaved = if (latestSavedId == Long.MAX_VALUE) 1L else latestSavedId + 1L
    return maxOf(System.currentTimeMillis(), nextAfterSaved).coerceAtLeast(1L)
}


/* ============================================================
   NOTIFICATION CHANNEL
   ============================================================ */

private val reminderKinds = listOf("TASK", "TIMER", "ALARM", "ALERT")

private fun soundPreferenceKey(kind: String): String =
    "notification_sound_${kind.uppercase(Locale.ROOT)}"

private fun previewSound(context: Context, profile: String) {
    val resource = when (profile.uppercase(Locale.ROOT)) {
        "CHIME" -> R.raw.kaam_timer_chime
        "ALARM" -> R.raw.kaam_alarm_pulse
        "PULSE" -> R.raw.kaam_alert_double
        "SILENT" -> return
        else -> R.raw.kaam_focus_chime
    }
    val player = MediaPlayer.create(context, resource) ?: return
    player.setOnCompletionListener { it.release() }
    player.setOnErrorListener { mediaPlayer, _, _ ->
        mediaPlayer.release()
        true
    }
    try {
        player.start()
    } catch (_: Exception) {
        player.release()
    }
}

private fun selectedSoundProfile(context: Context, kind: String): String {
    val preferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    val normalizedKind = kind.uppercase(Locale.ROOT)
    val perKind = preferences.getString(soundPreferenceKey(normalizedKind), null)
    if (perKind != null) return perKind

    // Preserve the older single-profile preference for TASK reminders only.
    // Timer, Alarm and Alert receive their own defaults unless the user customizes them.
    if (normalizedKind == "TASK") {
        val legacy = preferences.getString("notification_sound", null)
        if (legacy != null) return legacy
    }

    return when (normalizedKind) {
        "ALARM" -> "ALARM"
        "ALERT" -> "PULSE"
        "TIMER" -> "CHIME"
        else -> "FOCUS"
    }
}

private fun selectedNotificationChannelId(context: Context, kind: String = "TASK"): String {
    val normalizedKind = kind.uppercase(Locale.ROOT)
    val sound = selectedSoundProfile(context, normalizedKind)
    return "kaam_${normalizedKind.lowercase(Locale.ROOT)}_${sound.lowercase(Locale.ROOT)}"
}

fun createNotificationChannel(context: Context) {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
        val manager = context.getSystemService(NotificationManager::class.java)
        reminderKinds.forEach { kind ->
            val sound = selectedSoundProfile(context, kind)
            // These short, bundled original tones give the app a consistent sound
            // identity across Android manufacturers. Existing channels may retain
            // their old sound, so the channel ID includes the selected profile.
            val soundResource = when (sound) {
                "CHIME" -> R.raw.kaam_timer_chime
                "ALARM" -> R.raw.kaam_alarm_pulse
                "PULSE" -> R.raw.kaam_alert_double
                "SILENT" -> null
                else -> R.raw.kaam_focus_chime
            }
            val soundUri = soundResource?.let {
                Uri.Builder().scheme("android.resource").authority(context.packageName).appendPath(it.toString()).build()
            }
            val channel = NotificationChannel(
                selectedNotificationChannelId(context, kind),
                "$kind · ${sound.lowercase(Locale.ROOT).replaceFirstChar { it.uppercase() }}",
                if (kind == "ALARM" || kind == "ALERT") NotificationManager.IMPORTANCE_HIGH
                else NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = when (kind) {
                    "TIMER" -> "Focus timer completion reminders"
                    "ALARM" -> "Clock-time alarm reminders"
                    "ALERT" -> "Priority alert reminders"
                    else -> "Task accountability reminders"
                }
                enableVibration(sound != "SILENT")
                vibrationPattern = when {
                    sound == "SILENT" -> longArrayOf(0)
                    kind == "ALARM" -> longArrayOf(0, 300, 120, 300, 120, 420)
                    kind == "ALERT" -> longArrayOf(0, 120, 80, 120, 80, 180)
                    kind == "TIMER" -> longArrayOf(0, 100, 70, 180)
                    else -> longArrayOf(0, 180, 90, 180)
                }
                setShowBadge(true)
                setSound(soundUri, AudioAttributes.Builder()
                    .setUsage(if (kind == "ALARM") AudioAttributes.USAGE_ALARM else AudioAttributes.USAGE_NOTIFICATION)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build())
            }
            manager.createNotificationChannel(channel)
        }
    }
}

/* ============================================================
   EXACT ALARM PERMISSION
   ============================================================ */

fun canScheduleExactAlarms(
    context: Context
): Boolean {

    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) {
        return true
    }

    val alarmManager =
        context.getSystemService(
            Context.ALARM_SERVICE
        ) as AlarmManager

    return alarmManager.canScheduleExactAlarms()
}


fun openExactAlarmSettings(
    context: Context
) {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return

    val intent = Intent(
        Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM,
        Uri.parse("package:${context.packageName}")
    )

    // A settings screen should only be launched from a visible Activity.
    // Receivers must defer the reminder instead of launching UI from the background.
    val activity = context.findActivity()
    if (activity != null) {
        try {
            activity.startActivity(intent)
        } catch (_: Exception) {
            // The reminder remains recorded as deferred; Settings can be opened manually.
        }
    }
}

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}

/** Opens this app's notification controls so a denied permission is easy to recover. */
private fun openNotificationSettings(context: Context) {
    val intent = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
        Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).apply {
            putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
        }
    } else {
        Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${context.packageName}"))
    }
    try {
        context.startActivity(intent)
    } catch (_: Exception) {
        context.startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${context.packageName}")))
    }
}


/* ============================================================
   PENDING INTENT
   ============================================================ */

private fun reminderPendingIntent(
    context: Context,
    task: KaamTask
): PendingIntent {
    val intent = Intent(context, KaamReminderReceiver::class.java).apply {
        // Request codes are only 32-bit and Long.hashCode() can collide. Include
        // the full ID in Intent data so Android treats each reminder as unique.
        data = Uri.parse("kaam://reminder/${task.id}")
        putExtra("task_id", task.id)
        putExtra("title", task.title)
        putExtra("details", task.details)
    }
    return PendingIntent.getBroadcast(
        context,
        task.id.hashCode(),
        intent,
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
    )
}

/** Separate PendingIntent identities keep notification actions from replacing the real reminder. */
private fun notificationActionPendingIntent(
    context: Context,
    taskId: Long,
    action: String
): PendingIntent {
    val intent = Intent(context, KaamReminderReceiver::class.java).apply {
        this.action = action
        // Keep action identity collision-free even when taskId.hashCode() collides.
        data = Uri.parse("kaam://notification-action/$taskId/${Uri.encode(action)}")
        putExtra("task_id", taskId)
    }
    return PendingIntent.getBroadcast(
        context,
        31 * taskId.hashCode() + action.hashCode(),
        intent,
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
    )
}


/* ============================================================
   CANCEL REMINDER
   ============================================================ */

/** Dismiss an already-delivered notification when its task is completed or deleted in the app. */
private fun dismissTaskNotification(context: Context, taskId: Long) {
    val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
    manager.cancel(taskId.hashCode())
}


fun cancelReminder(
    context: Context,
    task: KaamTask,
    clearSnooze: Boolean = true
) {

    val alarmManager =
        context.getSystemService(
            Context.ALARM_SERVICE
        ) as AlarmManager

    alarmManager.cancel(
        reminderPendingIntent(
            context,
            task
        )
    )
    // A completed/deleted task must not linger in the permission-retry set.
    rememberPendingExactAlarm(context, task.id, add = false)
    if (clearSnooze) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit().remove("snoozed_until_${task.id}").apply()
    }
}


/** Keep every reminder waiting on exact-alarm permission, not just the last one. */
private fun rememberPendingExactAlarm(context: Context, taskId: Long, add: Boolean) {
    val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    synchronized(pendingExactAlarmLock) {
        val ids = prefs.getStringSet(PENDING_EXACT_TASK_IDS_KEY, emptySet())
            ?.toMutableSet() ?: mutableSetOf()
        if (add) ids.add(taskId.toString()) else ids.remove(taskId.toString())
        prefs.edit().apply {
            if (ids.isEmpty()) remove(PENDING_EXACT_TASK_IDS_KEY)
            else putStringSet(PENDING_EXACT_TASK_IDS_KEY, ids)
        }.commit()
    }
}

/**
 * Android can revoke exact-alarm access between our permission check and the
 * scheduling call. Convert that race into a deferred reminder instead of a crash.
 */
private fun setExactReminderSafely(
    context: Context,
    alarmManager: AlarmManager,
    triggerAtMillis: Long,
    pendingIntent: PendingIntent,
    task: KaamTask
): Boolean {
    return try {
        alarmManager.setExactAndAllowWhileIdle(
            AlarmManager.RTC_WAKEUP,
            triggerAtMillis,
            pendingIntent
        )
        true
    } catch (_: SecurityException) {
        rememberPendingExactAlarm(context, task.id, add = true)
        if (context.findActivity() != null) openExactAlarmSettings(context)
        false
    }
}


/* ============================================================
   SCHEDULE REMINDER
   ============================================================ */

fun scheduleReminder(
    context: Context,
    task: KaamTask
) {

    // Replacing an alarm should preserve a deliberate snooze, including across
    // boot recovery. Explicit completion/deletion uses cancelReminder's default
    // clearSnooze=true behavior.
    cancelReminder(context, task, clearSnooze = false)

    if (task.completed) {
        return
    }

    // A finite recurrence is complete once its allowed regular deliveries have
    // happened. A persisted snooze is checked below first so it remains usable.

    // Snooze deadlines are persisted so a device reboot does not silently lose
    // the user's ten-minute follow-up. Restore this before checking whether the
    // task has a regular schedule: an edited ANYTIME/NONE task can still have a
    // deliberate one-off snooze waiting to fire.
    val snoozeKey = "snoozed_until_${task.id}"
    val savedSnoozeUntil = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        .getLong(snoozeKey, 0L)
    if (savedSnoozeUntil > 0L) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val snoozeTrigger = maxOf(savedSnoozeUntil, System.currentTimeMillis() + 1_000L)
        alarmManager.setAndAllowWhileIdle(
            AlarmManager.RTC_WAKEUP,
            snoozeTrigger,
            reminderPendingIntent(context, task)
        )
        return
    }

    /* ANYTIME + NONE has no regular reminder unless a saved snooze exists. */
    if (shouldSkipNoReminderSchedule(task.repeatType, task.whenType, savedSnoozeUntil)) return
    if (!shouldScheduleNextOccurrence(task.repeatType, task.repeatLimit, task.repeatCount)) return

    /*
     * Android 12+ requires special access
     * for exact alarms.
     */
    if (!canScheduleExactAlarms(context)) {
        // Remember exactly which task needs scheduling after access is restored.
        // Only a visible Activity may launch Settings; receivers must not launch UI.
        rememberPendingExactAlarm(context, task.id, add = true)
        if (context.findActivity() != null) openExactAlarmSettings(context)
        return
    }

    // Access is now available; this task no longer needs to be deferred.
    rememberPendingExactAlarm(context, task.id, add = false)

    val alarmManager =
        context.getSystemService(
            Context.ALARM_SERVICE
        ) as AlarmManager

    val pendingIntent =
        reminderPendingIntent(
            context,
            task
        )

    /*
     * TIMER means "remind me after a duration" rather than
     * "remind me at a clock time".
     * This deliberately bypasses the Android clock picker.
     */
    if (task.whenType == "TIMER") {

        val triggerAt =
            calculateTimerTrigger(task)
                ?: return

        setExactReminderSafely(
            context,
            alarmManager,
            triggerAt,
            pendingIntent,
            task
        )

        return
    }

    when (task.repeatType) {

        /*
         * EVERY 2 / 4 HOURS
         *
         * We schedule only the NEXT reminder.
         * After it fires, the receiver schedules
         * the following one.
         */
        "INTERVAL" -> {

            val intervalMinutes = canonicalIntervalMinutes(task.repeatIntervalMinutes, task.repeatValue)

            val now = System.currentTimeMillis()
            val triggerAt = if (task.repeatWindowStart.isBlank() && task.repeatWindowEnd.isBlank()) {
                intervalTriggerAtMinutes(now, intervalMinutes)
            } else {
                val start = parseKaamTime(task.repeatWindowStart) ?: return
                val end = parseKaamTime(task.repeatWindowEnd) ?: return
                nextWindowedIntervalTriggerAtMinutes(now, intervalMinutes, start.first, start.second, end.first, end.second) ?: return
            }

            setExactReminderSafely(
                context,
                alarmManager,
                triggerAt,
                pendingIntent,
                task
            )
        }


        /*
         * DAILY
         */
        "DAILY" -> {

            val triggerAt =
                calculateFixedTimeTrigger(task)
                    ?: return

            setExactReminderSafely(
                context,
                alarmManager,
                triggerAt,
                pendingIntent,
                task
            )
        }


        /*
         * ONE-TIME
         */
        "NONE" -> {

            val triggerAt =
                calculateFixedTimeTrigger(task)
                    ?: return

            setExactReminderSafely(
                context,
                alarmManager,
                triggerAt,
                pendingIntent,
                task
            )
        }
    }
}


/* ============================================================
   CALCULATE TIMER DURATION
   ============================================================ */

private fun calculateTimerTrigger(
    task: KaamTask
): Long? {

    val minutes = task.whenValue.toIntOrNull() ?: return null
    val startedAt = timerStartTimestamp(task.timerStartedAt, task.createdAt)
    return timerDeadlineAt(startedAt, minutes)
}


/* ============================================================
   CALCULATE FIXED TIME
   ============================================================ */

private fun calculateFixedTimeTrigger(
    task: KaamTask
): Long? {
    val (hour, minute) = when (task.whenType) {
        "MORNING" -> 9 to 0
        "AFTERNOON" -> 14 to 0
        "EVENING" -> 18 to 0
        "CUSTOM" -> parseKaamTime(task.whenValue) ?: return null
        else -> return null
    }
    return nextFixedTimeTriggerAt(System.currentTimeMillis(), hour, minute)
}


/* ============================================================
   REMINDER RECEIVER
   ============================================================ */

class KaamReminderReceiver : BroadcastReceiver() {

    override fun onReceive(
        context: Context,
        intent: Intent
    ) {
        val taskId = intent.getLongExtra("task_id", -1L)
        if (taskId <= 0L) return

        // Notification actions are handled before normal delivery. They read the
        // saved task first, so an old notification cannot mutate a deleted item.
        if (intent.action == ACTION_NOTIFICATION_DONE) {
            val savedTasks = loadTasks(context).toMutableList()
            val index = savedTasks.indexOfFirst { it.id == taskId }
            if (index >= 0 && !savedTasks[index].completed) {
                savedTasks[index] = savedTasks[index].copy(
                    completed = true,
                    completedAt = System.currentTimeMillis()
                )
                saveTasks(context, savedTasks)
                cancelReminder(context, savedTasks[index])
                context.sendBroadcast(Intent(ACTION_TASKS_CHANGED).setPackage(context.packageName))
            }
            dismissTaskNotification(context, taskId)
            return
        }

        if (intent.action == ACTION_NOTIFICATION_SNOOZE) {
            val task = loadTasks(context).firstOrNull { it.id == taskId && !it.completed }
            if (task == null) {
                dismissTaskNotification(context, taskId)
                return
            }
            val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
            // Snooze is intentionally inexact so it works even when exact-alarm
            // special access is unavailable; Android may deliver a little later.
            val snoozeAt = snoozeTriggerAt(System.currentTimeMillis(), SNOOZE_MINUTES)
            rememberPendingExactAlarm(context, taskId, add = false)
            context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .edit().putLong("snoozed_until_$taskId", snoozeAt).apply()
            alarmManager.setAndAllowWhileIdle(
                AlarmManager.RTC_WAKEUP,
                snoozeAt,
                reminderPendingIntent(context, task)
            )
            dismissTaskNotification(context, taskId)
            return
        }

        createNotificationChannel(context)

        // The saved task is the source of truth, not possibly stale extras held
        // by an old PendingIntent. Do not notify for tasks deleted/completed
        // after the alarm was originally scheduled.
        val scheduledTask = loadTasks(context).firstOrNull { it.id == taskId }
            ?: return
        if (scheduledTask.completed) return

        // Remember whether this was a snooze delivery. Snoozes are follow-ups,
        // not new recurrence occurrences, so they must not consume the limit.
        val wasSnoozedDelivery = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getLong("snoozed_until_$taskId", 0L) > 0L
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit().remove("snoozed_until_$taskId").apply()

        val title = scheduledTask.title
        val details = scheduledTask.details
        val reminderStyle = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getString("reminder_style", "BALANCED") ?: "BALANCED"
        val normalizedTitle = title.trim().ifBlank { "Your next action" }
        // Copy is intentionally bilingual: everyday Hinglish + light slang, not
        // formal translated Hindi. Keep each line short enough for a notification.
        val notificationLines = when (scheduledTask.kind) {
            "TIMER" -> listOf(
                "Timer done, boss. Ab next move.",
                "Focus session khatam — nice, ab progress lock kar.",
                "Time's up, yaar. Check it off or run it back.",
                "Ek focus round complete. Solid scene.",
                "Break le lo, phir next round phodte hain."
            )
            "ALARM" -> listOf(
                "Uth jao, scene set karna hai.",
                "It's go time, yaar. Jo plan kiya tha, let's do it.",
                "Alarm baj gaya — snooze se kuch nahi hoga 😏",
                "Chalo boss, apne time pe apna kaam.",
                "The moment is here. Ab action dikhao."
            )
            "ALERT" -> listOf(
                "Oye, important hai — ek baar idhar dhyaan do.",
                "Heads-up, boss. Isko ignore mat karna.",
                "Baaki sab baad mein, pehle ye niptao.",
                "Quick check, yaar. This one needs you.",
                "Priority ping: abhi dekh lo, scene mat miss karo."
            )
            else -> when (reminderStyle) {
                "GENTLE" -> listOf(
                    "Aram se, yaar. Bas chhota sa start le lo.",
                    "Perfect nahi chahiye, bas shuru toh karo.",
                    "Thoda sa progress bhi progress hai, boss.",
                    "Deep breath. Phir ek chhota step lete hain.",
                    "No pressure — aaj ka kaam thoda sa aage badha do."
                )
                "DIRECT" -> listOf(
                    "Bhai, plan banaya tha — ab action bhi.",
                    "Bas procrastination, ab kaam pe lag jao.",
                    "No more kal-se. Start it now, boss.",
                    "Phone side mein, kaam pe focus. Let's go.",
                    "You said you'd do it. Ab karke dikhao."
                )
                else -> listOf(
                    "Chalo, 5 minute hi sahi — start toh karo.",
                    "Small steps, big scene. Kaam aage badhao.",
                    "Yaar, future-you ko thoda proud feel kara do.",
                    "Ek kaam niptao, phir next ka scene dekhenge.",
                    "Mood ka wait mat karo, boss. Bas shuru karo.",
                    "Less soch-vichaar, more action. You got this.",
                    "Aaj ka kaam aaj — let's get it done.",
                    "Quick nudge: pending kaam ko done bana do."
                )
            }
        }
        // Avoid showing the same line twice in a row for a recurring reminder.
        // Save the actual previous line (not its hash): no hash collisions, and
        // the selected copy remains stable across process death/reboot.
        val copyPreferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val copyPreferenceKey = "notification_copy_last_$taskId"
        val previousCopy = copyPreferences.getString(copyPreferenceKey, null)
        val notificationCopy = chooseNotificationCopy(notificationLines, previousCopy)
        copyPreferences.edit().putString(copyPreferenceKey, notificationCopy).apply()

        val notificationBody = when {
            details.isNotBlank() -> "$notificationCopy\n$details"
            else -> notificationCopy
        }

        val openAppIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
            if (taskId != -1L) {
                data = Uri.parse("kaam://open-task/$taskId")
                putExtra("open_task_id", taskId)
            }
        }
        val contentPendingIntent = PendingIntent.getActivity(
            context,
            if (taskId != -1L) taskId.hashCode() else 0,
            openAppIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification =
            androidx.core.app.NotificationCompat
                .Builder(
                    context,
                    selectedNotificationChannelId(context, scheduledTask.kind)
                )
                .setContentIntent(contentPendingIntent)
                .setSmallIcon(
                    android.R.drawable
                        .ic_popup_reminder
                )
                .setContentTitle(
                    when (scheduledTask.kind) {
                        "TIMER" -> "Timer complete · $normalizedTitle"
                        "ALARM" -> "It's time · $normalizedTitle"
                        "ALERT" -> "Priority reminder · $normalizedTitle"
                        else -> normalizedTitle
                    }
                )
                .setContentText(notificationCopy)
                .setStyle(
                    androidx.core.app.NotificationCompat.BigTextStyle()
                        .bigText(notificationBody)
                )
                .setPriority(
                    androidx.core.app
                        .NotificationCompat
                        .PRIORITY_HIGH
                )
                .addAction(
                    android.R.drawable.checkbox_on_background,
                    "Done",
                    notificationActionPendingIntent(context, taskId, ACTION_NOTIFICATION_DONE)
                )
                .addAction(
                    android.R.drawable.ic_lock_idle_alarm,
                    "Snooze 10m",
                    notificationActionPendingIntent(context, taskId, ACTION_NOTIFICATION_SNOOZE)
                )
                .setAutoCancel(true)
                .build()


        val manager =
            context.getSystemService(
                Context.NOTIFICATION_SERVICE
            ) as NotificationManager


        manager.notify(
            if (taskId != -1L) {
                taskId.hashCode()
            } else {
                System.currentTimeMillis()
                    .hashCode()
            },
            notification
        )


        /*
         * For repeating reminders:
         * schedule the NEXT occurrence.
         */
        if (taskId != -1L && scheduledTask.repeatType != "NONE" && scheduledTask.whenType != "TIMER") {
            // Persist the count before scheduling again. A snooze repeats the
            // current occurrence and therefore does not consume the limit.
            val currentTasks = loadTasks(context).toMutableList()
            val index = currentTasks.indexOfFirst { it.id == taskId }
            if (index >= 0 && !currentTasks[index].completed) {
                val existing = currentTasks[index]
                val nextRepeatCount = repeatCountAfterDelivery(existing.repeatCount, wasSnoozedDelivery)
                val delivered = if (nextRepeatCount == existing.repeatCount) existing else existing.copy(
                    repeatCount = nextRepeatCount
                )
                currentTasks[index] = delivered
                if (delivered !== existing) saveTasks(context, currentTasks)
                if (shouldScheduleNextOccurrence(delivered.repeatType, delivered.repeatLimit, delivered.repeatCount)) {
                    scheduleReminder(context, delivered)
                }
            }
        }
    }
}


/** Restores active reminders after reboot and when Android changes scheduling conditions. */
class KaamBootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action ?: return
        val supportedActions = setOf(
            Intent.ACTION_BOOT_COMPLETED,
            "android.intent.action.MY_PACKAGE_REPLACED",
            "android.intent.action.TIME_SET",
            Intent.ACTION_TIMEZONE_CHANGED,
            AlarmManager.ACTION_SCHEDULE_EXACT_ALARM_PERMISSION_STATE_CHANGED
        )
        if (action !in supportedActions) return

        // Android cancels exact alarms when exact-alarm access is revoked. Its
        // grant broadcast is the recovery point; verify access before rebuilding.
        if (action == AlarmManager.ACTION_SCHEDULE_EXACT_ALARM_PERMISSION_STATE_CHANGED &&
            !canScheduleExactAlarms(context)) return

        // Clock/time-zone changes require recomputing clock-based schedules. A
        // duration timer is intentionally left alone by this recovery path.
        val clockChanged = action == "android.intent.action.TIME_SET" ||
            action == Intent.ACTION_TIMEZONE_CHANGED

        // Run scheduling even without exact-alarm access: scheduleReminder can
        // restore persisted snoozes with an inexact alarm and defer other items.
        val pendingResult = goAsync()
        Thread {
            try {
                loadTasks(context).filterNot { it.completed }
                    .filter { !clockChanged || it.whenType != "TIMER" }
                    .forEach { task ->
                        // One bad/unsupported task must not prevent later tasks
                        // from recovering after reboot or a system setting change.
                        try {
                            scheduleReminder(context, task)
                        } catch (error: Exception) {
                            Log.w("KAAM", "Could not restore reminder ${task.id}", error)
                        }
                    }
            } finally {
                pendingResult.finish()
            }
        }.start()
    }
}


/* ============================================================
   MAIN HOME
   ============================================================ */

@Composable
fun KAAMHome() {

    val context = LocalContext.current
    // Read this Compose state during composition so taps received by an
    // already-running singleTop Activity trigger the deep-link effect too.
    val notificationTaskToOpen = (context as? MainActivity)?.notificationTaskToOpen
    val taskStateRefreshToken = (context as? MainActivity)?.taskStateRefreshToken ?: 0

    var showTaskDialog by
    remember {
        mutableStateOf(false)
    }

    var selectedMode by
    remember {
        mutableStateOf("TASK")
    }

    var activeSection by remember { mutableStateOf("HOME") }
    var taskView by remember { mutableStateOf("ACTIVE") }

    var selectedTaskId by
    remember {
        mutableStateOf<Long?>(null)
    }

    var showTaskDetails by
    remember {
        mutableStateOf(false)
    }

    // Deletion is destructive, so both the task-row shortcut and detail screen
    // route through one confirmation instead of deleting on a stray tap.
    var deleteCandidateId by remember { mutableStateOf<Long?>(null) }

    var editingTaskId by
    remember {
        mutableStateOf<Long?>(null)
    }
    var taskPrefill by remember { mutableStateOf("") }

    val tasks =
        remember {
            mutableStateListOf<KaamTask>()
        }
    var tasksLoaded by remember { mutableStateOf(false) }

    LaunchedEffect(taskStateRefreshToken) {
        // Load saved tasks into the UI only. Do not reschedule them on
        // every app launch: doing so resets interval/timer trigger times.
        val saved = loadTasks(context)
        tasks.clear()
        tasks.addAll(saved)
        tasksLoaded = true
    }

    LaunchedEffect(notificationTaskToOpen, tasksLoaded) {
        // Resolve a notification deep-link after the saved task list is loaded.
        // If the task was deleted, safely leave the user on Home.
        if (!tasksLoaded) return@LaunchedEffect
        val requestedTaskId = (context as? MainActivity)?.consumeNotificationTask()
            ?: notificationTaskToOpen
        if (requestedTaskId != null) {
            val requestedTask = tasks.firstOrNull { it.id == requestedTaskId }
            if (requestedTask != null) {
                selectedTaskId = requestedTask.id
                showTaskDetails = true
                activeSection = "HOME"
                taskView = if (requestedTask.completed) "COMPLETED" else "ACTIVE"
            }
        }
    }


    val selectedTask =
        tasks.firstOrNull {
            it.id == selectedTaskId
        }


    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Black)
    ) {

        if (activeSection == "HOME") {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(
                    rememberScrollState()
                )
                .padding(
                    horizontal = 24.dp,
                    vertical = 18.dp
                ),
            horizontalAlignment =
                Alignment.CenterHorizontally
        ) {

            Row(
                modifier =
                    Modifier.fillMaxWidth(),
                verticalAlignment =
                    Alignment.CenterVertically
            ) {

                Column(
                    modifier =
                        Modifier.weight(1f)
                ) {

                    Text(
                        text = "YOUR DAY",
                        color = White,
                        fontSize = 23.sp,
                        fontWeight =
                            FontWeight.Light,
                        letterSpacing = 5.sp
                    )

                    Spacer(
                        modifier =
                            Modifier.height(6.dp)
                    )

                    Text(
                        text =
                            "•  YOUR DAY  ·  YOUR PACE",
                        color = Dim,
                        fontSize = 9.sp,
                        letterSpacing = 1.4.sp
                    )
                }


                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .border(
                            1.dp,
                            Border,
                            RoundedCornerShape(50)
                        ),
                    contentAlignment =
                        Alignment.Center
                ) {

                    Text(
                        text = "·",
                        color = Muted,
                        fontSize = 18.sp
                    )
                }
            }


            Spacer(
                modifier =
                    Modifier.height(34.dp)
            )

            HangingObject()

            Spacer(
                modifier =
                    Modifier.height(76.dp)
            )

            HeroIntentCard {
                selectedMode = "TASK"
                showTaskDialog = true
            }

            Spacer(
                modifier =
                    Modifier.height(28.dp)
            )

            SectionTitle("CREATE")

            Spacer(
                modifier =
                    Modifier.height(10.dp)
            )

            // Four clear creation paths: each has enough room to read and tap.
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                CreateActionCard(
                    symbol = "✓",
                    title = "TASK",
                    subtitle = "A thing to finish",
                    modifier = Modifier.weight(1f)
                ) {
                    selectedMode = "TASK"
                    taskPrefill = ""
                    showTaskDialog = true
                }
                CreateActionCard(
                    symbol = "◷",
                    title = "TIMER",
                    subtitle = "After a duration",
                    modifier = Modifier.weight(1f)
                ) {
                    selectedMode = "TIMER"
                    taskPrefill = ""
                    showTaskDialog = true
                }
            }
            Spacer(Modifier.height(10.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                CreateActionCard(
                    symbol = "◷",
                    title = "ALARM",
                    subtitle = "At a clock time",
                    modifier = Modifier.weight(1f)
                ) {
                    selectedMode = "ALARM"
                    taskPrefill = ""
                    showTaskDialog = true
                }
                CreateActionCard(
                    symbol = "!",
                    title = "ALERT",
                    subtitle = "A strong nudge",
                    modifier = Modifier.weight(1f)
                ) {
                    selectedMode = "ALERT"
                    taskPrefill = ""
                    showTaskDialog = true
                }
            }


            Spacer(
                modifier =
                    Modifier.height(24.dp)
            )

            if (tasks.isEmpty()) {
                PremiumPanel("START WITH ONE THING") {
                    Text(
                        "The next step should be clear. Add one small commitment; you can change its timing later.",
                        color = Muted,
                        fontSize = 12.sp,
                        lineHeight = 18.sp
                    )
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("01", color = Color(0xFF8EB5AE), fontSize = 10.sp)
                        Spacer(Modifier.width(10.dp))
                        Text("Name what matters", color = White, fontSize = 11.sp)
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("02", color = Color(0xFF8EB5AE), fontSize = 10.sp)
                        Spacer(Modifier.width(10.dp))
                        Text("Choose a time—or leave it flexible", color = White, fontSize = 11.sp)
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("03", color = Color(0xFF8EB5AE), fontSize = 10.sp)
                        Spacer(Modifier.width(10.dp))
                        Text("Mark it done when you finish", color = White, fontSize = 11.sp)
                    }
                    SectionAction("CREATE YOUR FIRST TASK", "Keep it small. Build momentum.") {
                        selectedMode = "TASK"
                        taskPrefill = ""
                        showTaskDialog = true
                    }
                }
                Spacer(Modifier.height(24.dp))
            }

            if (tasks.isNotEmpty()) {

                SectionTitle("YOUR TASKS")

                Spacer(Modifier.height(10.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(7.dp)
                ) {
                    val taskViewCounts = mapOf(
                        "ACTIVE" to tasks.count { !it.completed },
                        "COMPLETED" to tasks.count { it.completed },
                        "HISTORY" to tasks.size
                    )
                    listOf("ACTIVE", "COMPLETED", "HISTORY").forEach { view ->
                        Box(
                            modifier = Modifier.weight(1f)
                                .border(1.dp, if (taskView == view) Color(0xFF8EB5AE) else Border, RoundedCornerShape(10.dp))
                                .background(if (taskView == view) Color(0xFF142322) else Panel, RoundedCornerShape(10.dp))
                                .clickable { taskView = view }
                                .padding(vertical = 11.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text("$view  ${taskViewCounts[view] ?: 0}", color = if (taskView == view) White else Muted, fontSize = 10.sp, letterSpacing = 0.3.sp)
                        }
                    }
                }

                Spacer(Modifier.height(8.dp))

                val visibleTasks = when (taskView) {
                    "ACTIVE" -> tasks.filter { !it.completed }.sortedWith(
                        compareBy<KaamTask> {
                            when {
                                isTaskOverdue(it.dueAt, it.completed, System.currentTimeMillis()) -> 0
                                it.dueAt != null -> 1
                                else -> 2
                            }
                        }.thenBy { it.dueAt ?: Long.MAX_VALUE }.thenBy { it.createdAt }
                    )
                    "COMPLETED" -> tasks.filter { it.completed }.sortedByDescending { it.completedAt ?: it.createdAt }
                    else -> tasks.sortedByDescending { it.completedAt ?: it.createdAt }
                }

                if (visibleTasks.isEmpty()) {
                    PremiumPanel(when (taskView) {
                        "ACTIVE" -> "CLEAR SPACE"
                        "COMPLETED" -> "COMPLETED TASKS"
                        else -> "TASK HISTORY"
                    }) {
                        Text(when (taskView) {
                            "ACTIVE" -> "Nothing active right now. A little space can be useful."
                            "COMPLETED" -> "Completed tasks will stay here as your record builds."
                            else -> "Your task timeline will appear here as you use this app."
                        }, color = Muted, fontSize = 12.sp)
                    }
                }

                visibleTasks.forEach { task ->

                    TaskCard(
                        task = task,

                        onOpen = {
                            selectedTaskId =
                                task.id

                            showTaskDetails =
                                true
                        },

                        onToggleComplete = {

                            val index =
                                tasks.indexOfFirst {
                                    it.id == task.id
                                }

                            if (index != -1) {

                                val updated =
                                    tasks[index].copy(
                                        completed = !tasks[index].completed,
                                        completedAt = if (!tasks[index].completed) System.currentTimeMillis() else null,
                                        timerStartedAt = if (tasks[index].completed && tasks[index].whenType == "TIMER") System.currentTimeMillis() else tasks[index].timerStartedAt
                                    )

                                tasks[index] =
                                    updated

                                saveTasks(
                                    context,
                                    tasks
                                )

                                if (
                                    updated.completed
                                ) {
                                    cancelReminder(
                                        context,
                                        updated
                                    )
                                } else {
                                    scheduleReminder(
                                        context,
                                        updated
                                    )
                                }
                            }
                        },

                        onDelete = {
                            deleteCandidateId = task.id
                        }
                    )

                    Spacer(
                        modifier =
                            Modifier.height(8.dp)
                    )
                }

                Spacer(
                    modifier =
                        Modifier.height(18.dp)
                )
            }


            SectionTitle("CONTROL")

            Spacer(
                modifier =
                    Modifier.height(10.dp)
            )

            ControlCard(
                "PROGRESS",
                "today",
                onClick = { activeSection = "PROGRESS" }
            )

            Spacer(
                modifier =
                    Modifier.height(8.dp)
            )

            ControlCard(
                "REPORT",
                "overview",
                onClick = { activeSection = "REPORT" }
            )

            Spacer(
                modifier =
                    Modifier.height(8.dp)
            )

            ControlCard(
                "CALENDAR",
                "schedule",
                onClick = { activeSection = "PLAN" }
            )

            Spacer(
                modifier =
                    Modifier.height(8.dp)
            )

            ControlCard(
                "SOUND",
                "experience",
                onClick = { activeSection = "SOUNDS" }
            )

            Spacer(
                modifier =
                    Modifier.height(8.dp)
            )

            ControlCard(
                "DAILY",
                "update",
                onClick = { activeSection = "CHAT" }
            )

            Spacer(
                modifier =
                    Modifier.height(8.dp)
            )

            ControlCard(
                "SETTINGS",
                "control",
                onClick = { activeSection = "SETTINGS" }
            )

            Spacer(Modifier.height(20.dp))
            SectionTitle("YOUR MODES")
            Spacer(Modifier.height(10.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf("TASKS", "TIMERS").forEach { destination ->
                    ControlCard(destination, "manage", onClick = { activeSection = destination }, modifier = Modifier.weight(1f))
                }
            }
            Spacer(Modifier.height(8.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf("ALARMS", "ALERTS").forEach { destination ->
                    ControlCard(destination, "manage", onClick = { activeSection = destination }, modifier = Modifier.weight(1f))
                }
            }

            Spacer(
                modifier =
                    Modifier.height(30.dp)
            )

            Row(
                verticalAlignment =
                    Alignment.CenterVertically
            ) {

                Box(
                    modifier = Modifier
                        .width(28.dp)
                        .height(1.dp)
                        .background(Border)
                )

                Text(
                    text = "   K   ",
                    color = Dim,
                    fontSize = 6.sp,
                    letterSpacing = 2.sp
                )

                Box(
                    modifier = Modifier
                        .width(28.dp)
                        .height(1.dp)
                        .background(Border)
                )
            }

            Spacer(
                modifier =
                    Modifier.height(4.dp)
            )
        }
        } else {
            KaamSectionScreen(
                section = activeSection,
                tasks = tasks,
                onBack = { activeSection = "HOME" },
                onCreateTask = { mode, prefill ->
                    selectedMode = mode
                    taskPrefill = prefill
                    showTaskDialog = true
                    activeSection = "HOME"
                },
                onOpenTask = { taskId ->
                    selectedTaskId = taskId
                    showTaskDetails = true
                    activeSection = "HOME"
                }
            )
        }
    }


    /* ========================================================
       CREATE / EDIT DIALOG
       ======================================================== */

    if (showTaskDialog) {

        val editingTask =
            tasks.firstOrNull {
                it.id == editingTaskId
            }

        TaskDialog(
            mode = selectedMode,
            existingTask = editingTask,
            initialTitle = taskPrefill,

            onDismiss = {
                showTaskDialog = false
                editingTaskId = null
                taskPrefill = ""
            },

            onSave = {
                    title,
                    details,
                    whenType,
                    whenValue,
                    repeatType,
                    repeatValue,
                    repeatLimit,
                    repeatWindowStart,
                    repeatWindowEnd,
                    dueAt ->

                if (title.isNotBlank()) {

                    if (editingTask != null) {

                        // Editing replaces the regular schedule but should not silently discard a deliberate snooze.
                        cancelReminder(
                            context,
                            editingTask,
                            clearSnooze = false
                        )

                        val index =
                            tasks.indexOfFirst {
                                it.id ==
                                        editingTask.id
                            }

                        if (index != -1) {

                            val updated =
                                editingTask.copy(
                                    title =
                                        title.trim(),
                                    details =
                                        details.trim(),
                                    whenType =
                                        whenType,
                                    whenValue =
                                        whenValue,
                                    repeatType =
                                        repeatType,
                                    repeatValue = if (repeatValue > 0 && repeatValue % 60 == 0) repeatValue / 60 else 0,
                                    repeatIntervalMinutes = repeatValue,
                                    repeatLimit = repeatLimit,
                                    repeatCount = if (editingTask.whenType == whenType && editingTask.whenValue == whenValue && editingTask.repeatType == repeatType && canonicalIntervalMinutes(editingTask.repeatIntervalMinutes, editingTask.repeatValue) == repeatValue && editingTask.repeatLimit == repeatLimit && editingTask.repeatWindowStart == repeatWindowStart && editingTask.repeatWindowEnd == repeatWindowEnd) editingTask.repeatCount else 0,
                                    repeatWindowStart = repeatWindowStart,
                                    repeatWindowEnd = repeatWindowEnd,
                                    dueAt = dueAt,
                                    timerStartedAt = timerStartForEdit(
                                        editingTask.whenType, whenType, editingTask.whenValue, whenValue,
                                        editingTask.timerStartedAt, System.currentTimeMillis()
                                    )
                                )

                            tasks[index] =
                                updated

                            saveTasks(
                                context,
                                tasks
                            )

                            scheduleReminder(
                                context,
                                updated
                            )
                        }

                    } else {

                        val task =
                            KaamTask(
                                id = nextTaskId(context),
                                title =
                                    title.trim(),
                                details =
                                    details.trim(),
                                kind = selectedMode,
                                whenType =
                                    whenType,
                                whenValue =
                                    whenValue,
                                repeatType =
                                    repeatType,
                                repeatValue = if (repeatValue > 0 && repeatValue % 60 == 0) repeatValue / 60 else 0,
                                repeatIntervalMinutes = repeatValue,
                                repeatLimit = repeatLimit,
                                repeatCount = 0,
                                repeatWindowStart = repeatWindowStart,
                                repeatWindowEnd = repeatWindowEnd,
                                dueAt = dueAt,
                                timerStartedAt = if (whenType == "TIMER") System.currentTimeMillis() else null
                            )

                        tasks.add(task)

                        saveTasks(
                            context,
                            tasks
                        )

                        scheduleReminder(
                            context,
                            task
                        )
                    }
                }

                showTaskDialog = false
                editingTaskId = null
                taskPrefill = ""
            }
        )
    }


    /* ========================================================
       TASK DETAILS
       ======================================================== */

    if (
        showTaskDetails &&
        selectedTask != null
    ) {

        TaskDetailsDialog(
            task = selectedTask,

            onDismiss = {
                showTaskDetails = false
                selectedTaskId = null
            },

            onEdit = {

                editingTaskId =
                    selectedTask.id

                selectedMode = selectedTask.kind

                showTaskDetails = false
                showTaskDialog = true
            },

            onToggleComplete = {

                val index =
                    tasks.indexOfFirst {
                        it.id ==
                                selectedTask.id
                    }

                if (index != -1) {

                    val updated =
                        tasks[index].copy(
                            completed = !tasks[index].completed,
                            completedAt = if (!tasks[index].completed) System.currentTimeMillis() else null,
                            timerStartedAt = if (tasks[index].completed && tasks[index].whenType == "TIMER") System.currentTimeMillis() else tasks[index].timerStartedAt
                        )

                    tasks[index] =
                        updated

                    saveTasks(
                        context,
                        tasks
                    )

                    if (updated.completed) {

                        cancelReminder(
                            context,
                            updated
                        )
                        dismissTaskNotification(context, updated.id)

                    } else {

                        scheduleReminder(
                            context,
                            updated
                        )
                    }
                }

                showTaskDetails = false
            },

            onDelete = {
                deleteCandidateId = selectedTask.id
                showTaskDetails = false
            }
        )
    }

    val deleteCandidate = tasks.firstOrNull { it.id == deleteCandidateId }
    if (deleteCandidate != null) {
        AlertDialog(
            onDismissRequest = { deleteCandidateId = null },
            containerColor = Color(0xFF0B1011),
            title = { Text("DELETE THIS ITEM?", color = White, fontSize = 16.sp, letterSpacing = 1.2.sp) },
            text = {
                Text(
                    "\"${deleteCandidate.title}\" will be removed from your history and its reminder will be cancelled. This cannot be undone.",
                    color = Muted,
                    fontSize = 13.sp,
                    lineHeight = 19.sp
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    cancelReminder(context, deleteCandidate)
                    dismissTaskNotification(context, deleteCandidate.id)
                    // Remove per-item notification-copy memory when the item is deleted.
                    // This prevents stale SharedPreferences entries accumulating over time.
                    context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                        .edit()
                        .remove("notification_copy_last_${deleteCandidate.id}")
                        // Clean up the legacy v14 hash key as well for upgraded installs.
                        .remove("notification_copy_hash_${deleteCandidate.id}")
                        .apply()
                    tasks.removeAll { it.id == deleteCandidate.id }
                    saveTasks(context, tasks)
                    if (selectedTaskId == deleteCandidate.id) {
                        selectedTaskId = null
                        showTaskDetails = false
                    }
                    deleteCandidateId = null
                }) {
                    Text("DELETE", color = Color(0xFFE5A39A), letterSpacing = 1.sp)
                }
            },
            dismissButton = {
                TextButton(onClick = { deleteCandidateId = null }) {
                    Text("KEEP ITEM", color = White, letterSpacing = 1.sp)
                }
            }
        )
    }
}


/* ============================================================
   KAAM SECONDARY SCREENS
   ============================================================ */

@Composable
fun KaamSectionScreen(
    section: String,
    tasks: List<KaamTask>,
    onBack: () -> Unit,
    onCreateTask: (String, String) -> Unit,
    onOpenTask: (Long) -> Unit
) {
    val context = LocalContext.current
    val completed = tasks.count { it.completed }
    val active = tasks.count { !it.completed }
    val total = tasks.size
    val accent = Color(0xFF8EB5AE)
    var sound by remember {
        mutableStateOf(
            context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .getString("notification_sound", "FOCUS") ?: "FOCUS"
        )
    }
    var reminderStyle by remember {
        mutableStateOf(
            context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .getString("reminder_style", "BALANCED") ?: "BALANCED"
        )
    }
    var note by remember {
        mutableStateOf(context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).getString("daily_intention", "") ?: "")
    }
    var reportRange by remember { mutableStateOf("WEEK") }
    var savedIntention by remember {
        mutableStateOf(context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).getString("daily_intention", "") ?: "")
    }

    var now by remember(section) { mutableStateOf(System.currentTimeMillis()) }
    LaunchedEffect(section) {
        if (section == "TIMERS") {
            while (true) {
                delay(1_000L)
                now = System.currentTimeMillis()
            }
        }
    }
    val startOfToday = Calendar.getInstance().apply {
        set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
    }.timeInMillis
    val startOfWeek = Calendar.getInstance().apply {
        set(Calendar.DAY_OF_WEEK, firstDayOfWeek)
        set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
    }.timeInMillis
    val startOfMonth = Calendar.getInstance().apply {
        set(Calendar.DAY_OF_MONTH, 1)
        set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
    }.timeInMillis
    val periodStart = when (reportRange) { "TODAY" -> startOfToday; "MONTH" -> startOfMonth; else -> startOfWeek }
    // Reports measure two different events independently: tasks created in the
    // selected period, and tasks completed in that period (including older tasks).
    // This avoids silently omitting an old task completed this week/month.
    val createdInPeriod = tasks.filter { it.createdAt in periodStart..now }
    val completedInPeriod = tasks.filter { (it.completedAt ?: 0L) in periodStart..now }
    val todayTasks = tasks.filter {
        it.createdAt in startOfToday..now || (it.completedAt ?: 0L) in startOfToday..now
    }
    val progressCompletedToday = todayTasks.count { (it.completedAt ?: 0L) in startOfToday..now }
    val periodTotal = if (section == "PROGRESS") todayTasks.size else createdInPeriod.size
    val periodCompleted = if (section == "PROGRESS") progressCompletedToday else completedInPeriod.size
    val periodActiveCreated = createdInPeriod.count { !it.completed }
    val periodPercent = if (periodTotal == 0) 0 else
        ((if (section == "PROGRESS") periodCompleted else createdInPeriod.count { it.completed && (it.completedAt ?: 0L) <= now }) * 100 / periodTotal).coerceIn(0, 100)

    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState())
            .padding(horizontal = 22.dp, vertical = 18.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier.sizeIn(minWidth = 48.dp, minHeight = 48.dp)
                    .semantics { contentDescription = "Back"; role = Role.Button }
                    .clickable(role = Role.Button, onClick = onBack),
                contentAlignment = Alignment.Center
            ) {
                Text("‹", color = accent, fontSize = 32.sp)
            }
            Column {
                Text("YOUR DAY", color = White, fontSize = 10.sp, letterSpacing = 4.sp)
                Text(section, color = White, fontSize = 23.sp, fontWeight = FontWeight.Light,
                    letterSpacing = 2.sp)
            }
        }
        Text(
            when (section) {
                "PROGRESS" -> "CONSISTENCY IS BUILT, NOT FOUND."
                "REPORT" -> "A CLEAR LOOK AT YOUR FOLLOW-THROUGH."
                "PLAN" -> "MAKE SPACE FOR WHAT MATTERS."
                "SOUNDS" -> "MAKE YOUR REMINDERS FEEL DISTINCT."
                "CHAT" -> "A QUIET SPACE TO RESET YOUR INTENTION."
                "SETTINGS" -> "TUNE YOUR EXPERIENCE."
                else -> "YOUR NEXT ACTION STARTS HERE."
            }, color = Dim, fontSize = 9.sp, letterSpacing = 1.5.sp
        )

        when (section) {
            "TASKS", "TIMERS", "ALARMS", "ALERTS" -> {
                val kind = when (section) {
                    "TIMERS" -> "TIMER"
                    "ALARMS" -> "ALARM"
                    "ALERTS" -> "ALERT"
                    else -> "TASK"
                }
                val modeItems = tasks.filter { it.kind == kind }.sortedWith(
                    compareBy<KaamTask> { it.completed }.thenByDescending { it.createdAt }
                )
                val modeActive = modeItems.count { !it.completed }
                val modeCompleted = modeItems.count { it.completed }
                PremiumPanel(when (kind) {
                    "TIMER" -> "DURATION CONTROL"
                    "ALARM" -> "CLOCK-TIME COMMITMENTS"
                    "ALERT" -> "HIGH-ATTENTION NUDGES"
                    else -> "FLEXIBLE COMMITMENTS"
                }) {
                    Text(when (kind) {
                        "TIMER" -> "Timers are duration-first. Each countdown keeps its saved start time; reopening the app should not restart it."
                        "ALARM" -> "Alarms are tied to a clock time. Review what is scheduled and open any item to edit or cancel it."
                        "ALERT" -> "Alerts are for things you do not want to miss. Keep the message short and choose a moment that matters."
                        else -> "Tasks are commitments to finish. Keep them flexible when a strict clock time is not useful."
                    }, color = Muted, fontSize = 12.sp, lineHeight = 18.sp)
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        MetricCard("ACTIVE", modeActive.toString(), Modifier.weight(1f))
                        MetricCard("COMPLETED", modeCompleted.toString(), Modifier.weight(1f))
                        MetricCard("TOTAL", modeItems.size.toString(), Modifier.weight(1f))
                    }
                    SectionAction(when (kind) {
                        "TIMER" -> "START A TIMER"
                        "ALARM" -> "SET AN ALARM"
                        "ALERT" -> "CREATE AN ALERT"
                        else -> "ADD A TASK"
                    }, when (kind) {
                        "TIMER" -> "Choose a duration slot; no clock picker"
                        "ALARM" -> "Choose a clock time with AM / PM"
                        "ALERT" -> "Make an important nudge hard to miss"
                        else -> "Add a flexible action to finish"
                    }) { onCreateTask(kind, "") }
                }
                PremiumPanel(when (kind) {
                    "TIMER" -> "YOUR COUNTDOWNS"
                    "ALARM" -> "YOUR ALARMS"
                    "ALERT" -> "YOUR ALERTS"
                    else -> "YOUR TASKS"
                }) {
                    if (modeItems.isEmpty()) {
                        Text(when (kind) {
                            "TIMER" -> "No timers yet. Start a short focus slot and let the countdown carry the timing."
                            "ALARM" -> "No alarms set. Add a clock-time reminder when something needs to happen at a specific hour."
                            "ALERT" -> "No alerts yet. Reserve this mode for important nudges, not every small task."
                            else -> "No tasks yet. Add one clear commitment and build from there."
                        }, color = Muted, fontSize = 12.sp, lineHeight = 18.sp)
                    } else {
                        modeItems.forEach { item ->
                            val scheduleLabel = when (kind) {
                                "TIMER" -> {
                                    val duration = item.whenValue.toIntOrNull()
                                    val remainingMinutes = duration?.let {
                                        timerRemainingMinutes(timerStartTimestamp(item.timerStartedAt, item.createdAt), it, now)
                                    }
                                    val remainingText = if (item.completed) "COMPLETED" else if (remainingMinutes == null) "DURATION NOT SET" else if (remainingMinutes == 0) "TIME ELAPSED" else {
                                        "${remainingMinutes / 60}h ${remainingMinutes % 60}m remaining"
                                    }
                                    "${item.whenValue.ifBlank { "?" }} min · $remainingText"
                                }
                                "ALARM" -> "${item.whenValue.ifBlank { "TIME NOT SET" }} · ${repeatSummary(item.repeatType, item.repeatValue, item.repeatLimit, item.repeatWindowStart, item.repeatWindowEnd, item.repeatIntervalMinutes)}"
                                "ALERT" -> "${formatWhen(item.whenType, item.whenValue)} · ${repeatSummary(item.repeatType, item.repeatValue, item.repeatLimit, item.repeatWindowStart, item.repeatWindowEnd, item.repeatIntervalMinutes)} · ${if (item.completed) "DONE" else "ACTIVE NUDGE"}"
                                else -> "${formatWhen(item.whenType, item.whenValue)} · ${repeatSummary(item.repeatType, item.repeatValue, item.repeatLimit, item.repeatWindowStart, item.repeatWindowEnd, item.repeatIntervalMinutes)} · ${if (item.completed) "DONE" else "OPEN"}"
                            }
                            val deadlineLabel = item.dueAt?.let { due ->
                                (if (item.completed) "DEADLINE" else if (isTaskOverdue(due, item.completed, now)) "OVERDUE" else "DUE") + " " +
                                    java.text.SimpleDateFormat("dd MMM", Locale.getDefault()).format(java.util.Date(due))
                            }
                            SectionAction(item.title, if (deadlineLabel.isNullOrBlank()) scheduleLabel else "$scheduleLabel · $deadlineLabel") { onOpenTask(item.id) }
                        }
                    }
                }
                Text("Each mode has its own purpose; all share the same dark tactile visual system.", color = Dim, fontSize = 10.sp, lineHeight = 15.sp)
            }
            "PROGRESS", "REPORT" -> {
                if (section == "REPORT") {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf("TODAY", "WEEK", "MONTH").forEach { range ->
                            Box(Modifier.weight(1f).border(1.dp, if (reportRange == range) accent else Border, RoundedCornerShape(10.dp))
                                .background(if (reportRange == range) Color(0xFF142322) else Panel, RoundedCornerShape(10.dp))
                                .clickable { reportRange = range }.padding(vertical = 11.dp), contentAlignment = Alignment.Center) {
                                Text(range, color = if (reportRange == range) White else Muted, fontSize = 9.sp, letterSpacing = 1.sp)
                            }
                        }
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    MetricCard(if (section == "PROGRESS") "TODAY'S TASKS" else "CREATED", periodTotal.toString(), Modifier.weight(1f))
                    MetricCard("COMPLETED", periodCompleted.toString(), Modifier.weight(1f))
                    MetricCard(if (section == "PROGRESS") "STILL OPEN" else "ACTIVE CREATED", (if (section == "PROGRESS") (periodTotal - periodCompleted).coerceAtLeast(0) else periodActiveCreated).toString(), Modifier.weight(1f))
                }
                PremiumPanel(if (section == "PROGRESS") "TODAY'S FOLLOW-THROUGH" else "${reportRange} SUMMARY") {
                    val ratio = periodPercent / 100f
                    Box(Modifier.fillMaxWidth().height(6.dp).background(Border, RoundedCornerShape(20.dp))) {
                        Box(Modifier.fillMaxWidth(ratio.coerceIn(0f, 1f)).fillMaxHeight().background(accent, RoundedCornerShape(20.dp)))
                    }
                    Text(
                        when {
                            section == "PROGRESS" && periodTotal == 0 -> "No tasks recorded today yet. Add a task and your progress will appear here."
                            section == "PROGRESS" -> "$periodPercent% of today's recorded tasks have been completed today."
                            createdInPeriod.isEmpty() && completedInPeriod.isEmpty() -> "No task creation or completion events recorded in this period yet."
                            else -> "Created: ${createdInPeriod.size} · Completed during this period: ${completedInPeriod.size}. Completion counts include tasks created earlier."
                        }, color = Muted, fontSize = 12.sp
                    )
                    if (section == "REPORT") {
                        Text("All-time: $total tasks · $completed completed · $active active", color = Dim, fontSize = 11.sp)
                    }
                }
                PremiumPanel("TASK HISTORY") {
                    if (tasks.isEmpty()) Text("Your history will build as you use this app.", color = Muted, fontSize = 12.sp)
                    tasks.sortedByDescending { it.createdAt }.forEach { task ->
                        Row(Modifier.fillMaxWidth().padding(vertical = 5.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text(if (task.completed) "✓" else "○", color = if (task.completed) accent else Dim, fontSize = 16.sp)
                            Spacer(Modifier.width(10.dp))
                            Column(Modifier.weight(1f)) {
                                Text(task.title, color = White, fontSize = 13.sp)
                                val dateLabel = if (task.completedAt != null) "DONE · " + java.text.SimpleDateFormat("dd MMM, h:mm a", Locale.getDefault()).format(java.util.Date(task.completedAt)) else "ADDED · " + java.text.SimpleDateFormat("dd MMM", Locale.getDefault()).format(java.util.Date(task.createdAt))
                                Text(dateLabel + if (!task.completed) " · ${formatWhen(task.whenType, task.whenValue)}" else "", color = Dim, fontSize = 9.sp)
                            }
                        }
                    }
                }
            }
            "PLAN" -> {
                PremiumPanel("BUILD YOUR DAY") {
                    Text("A plan is a direction, not a cage. Start with the next useful action.", color = Muted, fontSize = 12.sp)
                    SectionAction("ADD A TASK", "Put one priority into motion") { onCreateTask("TASK", "") }
                    SectionAction("SET A TIME", "Choose a clock time with AM / PM") { onCreateTask("ALARM", "") }
                    SectionAction("START A TIMER", "Focus for a chosen duration") { onCreateTask("TIMER", "") }
                }
                PremiumPanel("ACTIVE COMMITMENTS") {
                    if (tasks.none { !it.completed }) Text("Nothing active yet. Keep the plan light and intentional.", color = Muted, fontSize = 12.sp)
                    tasks.filter { !it.completed }.forEach { Text("•  ${it.title}  ·  ${formatWhen(it.whenType, it.whenValue)}", color = White, fontSize = 12.sp) }
                }
            }
            "SOUNDS" -> {
                PremiumPanel("SOUND IDENTITY") {
                    Text("Four short original tones are bundled with the app. Choose a sound separately for each reminder type.", color = Muted, fontSize = 11.sp)
                    val soundChoices = listOf(
                        "FOCUS" to "Focus · soft original chime",
                        "CHIME" to "Chime · bright timer tones",
                        "ALARM" to "Alarm · strong rising pulse",
                        "PULSE" to "Alert · crisp double ping",
                        "SILENT" to "Silent · vibration/visual only"
                    )
                    reminderKinds.forEach { kind ->
                        Spacer(Modifier.height(8.dp))
                        Text(kind, color = White, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 1.5.sp)
                        val currentProfile = selectedSoundProfile(context, kind)
                        soundChoices.forEach { (value, label) ->
                            ChoiceRow(label, currentProfile == value) {
                                context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit()
                                    .putString(soundPreferenceKey(kind), value)
                                    .apply()
                                sound = value
                                createNotificationChannel(context)
                            }
                        }
                        SectionAction("PREVIEW $kind SOUND", "Hear the selected tone") {
                            previewSound(context, selectedSoundProfile(context, kind))
                        }
                    }
                    Text("Android may preserve an existing channel's old sound. If a tone does not change, open system notification settings for this app and update that channel.", color = Muted, fontSize = 11.sp)
                }
            }
            "SETTINGS" -> {
                PremiumPanel("REMINDER VOICE") {
                    Text("Choose how your reminders speak to you. This changes the wording, not your schedule or saved tasks.", color = Muted, fontSize = 11.sp)
                    listOf("GENTLE" to "Gentle nudge", "BALANCED" to "Balanced accountability", "DIRECT" to "Direct and focused").forEach { (value, label) ->
                        ChoiceRow(label, reminderStyle == value) {
                            reminderStyle = value
                            context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit().putString("reminder_style", value).apply()
                        }
                    }
                }
                PremiumPanel("SCHEDULE BEHAVIOR") {
                    Text("Interval reminders count forward inside an optional daily time window; leave the window blank for all-day reminders. Daily reminders use your selected local clock time. Finite limits count regular reminders only; a Snooze 10m follow-up does not use another occurrence.", color = Muted, fontSize = 11.sp, lineHeight = 16.sp)
                    Spacer(Modifier.height(8.dp))
                    Text("A task stays active when its reminder limit is reached, so you can still mark it done or review it in History later.", color = Dim, fontSize = 10.sp, lineHeight = 15.sp)
                }
                PremiumPanel("PERMISSIONS") {
                    Text("Notifications and exact alarms must be allowed by Android for scheduled reminders to arrive on time.", color = Muted, fontSize = 11.sp)
                    SectionAction("OPEN NOTIFICATION SETTINGS", "Allow reminders to appear") { openNotificationSettings(context) }
                    SectionAction("OPEN EXACT ALARM ACCESS", "Allow precise scheduled reminders") { openExactAlarmSettings(context) }
                }
            }
            "CHAT" -> {
                PremiumPanel("DAILY CHECK-IN") {
                    Text("What matters most today? Write it down, then turn it into an action.", color = Muted, fontSize = 12.sp)
                    OutlinedTextField(value = note, onValueChange = {
                        note = it
                        savedIntention = it
                        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit().putString("daily_intention", it).apply()
                    }, modifier = Modifier.fillMaxWidth(), label = { Text("Today's intention") }, minLines = 3)
                    if (savedIntention.isNotBlank() && note.isBlank()) {
                        Text("Saved intention: $savedIntention", color = accent, fontSize = 11.sp)
                    }
                    SectionAction("TURN INTENTION INTO TASK", "Save your intention before opening task creation") {
                        if (note.isNotBlank()) {
                            context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit().putString("daily_intention", note.trim()).apply()
                            onCreateTask("TASK", note.trim())
                        }
                    }
                    Text("No AI service is connected here; this is a simple intention field saved in the app.", color = Dim, fontSize = 10.sp)
                }
                PremiumPanel("PROMPTS") {
                    listOf("What is the one thing I should finish?", "What am I avoiding—and what is the smallest first step?", "What would make today feel complete?").forEach { prompt ->
                        Text(prompt, color = White, fontSize = 12.sp, modifier = Modifier.padding(vertical = 5.dp))
                    }
                }
            }
            else -> {
                PremiumPanel(section) {
                    Text("Keep your attention on the next action, not the entire mountain.", color = Muted, fontSize = 12.sp)
                    SectionAction("CREATE TASK", "A flexible commitment") { onCreateTask("TASK", "") }
                    SectionAction("CREATE ALERT", "A direct attention reminder") { onCreateTask("ALERT", "") }
                    SectionAction("START TIMER", "A duration-based focus slot") { onCreateTask("TIMER", "") }
                }
            }
        }
        Spacer(Modifier.height(18.dp))
        Text("DARK TACTILE / SELECTIVE GLASS", color = Dim, fontSize = 8.sp, letterSpacing = 1.5.sp,
            modifier = Modifier.align(Alignment.CenterHorizontally))
    }
}

@Composable
private fun MetricCard(label: String, value: String, modifier: Modifier = Modifier) {
    Column(modifier.background(Panel, RoundedCornerShape(16.dp)).border(1.dp, Border, RoundedCornerShape(16.dp)).padding(14.dp)) {
        Text(value, color = White, fontSize = 25.sp, fontWeight = FontWeight.Light)
        Text(label, color = Dim, fontSize = 8.sp, letterSpacing = 1.sp)
    }
}

@Composable
private fun PremiumPanel(title: String, content: @Composable ColumnScope.() -> Unit) {
    Column(Modifier.fillMaxWidth().background(Brush.verticalGradient(listOf(Color(0xFF111B1C), Panel)), RoundedCornerShape(20.dp))
        .border(1.dp, Border, RoundedCornerShape(20.dp)).padding(17.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(title, color = White, fontSize = 10.sp, letterSpacing = 1.8.sp)
        content()
    }
}

@Composable
private fun SectionAction(title: String, subtitle: String, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth()
            .heightIn(min = 52.dp)
            .border(1.dp, Border, RoundedCornerShape(12.dp))
            .semantics { role = Role.Button }
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = 13.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, color = White, fontSize = 10.sp, letterSpacing = 1.sp)
            Text(subtitle, color = Dim, fontSize = 10.sp)
        }
        Text("↗", color = Color(0xFF8EB5AE), fontSize = 18.sp)
    }
}

@Composable
private fun ChoiceRow(title: String, selected: Boolean, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth()
            .heightIn(min = 48.dp)
            .semantics {
                role = Role.RadioButton
                this.selected = selected
                stateDescription = if (selected) "Selected" else "Not selected"
            }
            .clickable(role = Role.RadioButton, onClick = onClick)
            .padding(vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(Modifier.size(17.dp).border(1.dp, if (selected) Color(0xFF8EB5AE) else Border, RoundedCornerShape(50)), contentAlignment = Alignment.Center) {
            if (selected) Box(Modifier.size(7.dp).background(Color(0xFF8EB5AE), RoundedCornerShape(50)))
        }
        Spacer(Modifier.width(10.dp))
        Text(title, color = if (selected) White else Muted, fontSize = 12.sp)
    }
}

/* ============================================================
   HERO
   ============================================================ */

@Composable
fun HeroIntentCard(
    onClick: () -> Unit
) {

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .height(150.dp)
            .background(
                Brush.verticalGradient(
                    listOf(
                        WaterMid,
                        WaterDark
                    )
                ),
                RoundedCornerShape(28.dp)
            )
            .border(
                1.dp,
                Border,
                RoundedCornerShape(28.dp)
            )
            .clickable {
                onClick()
            }
            .padding(20.dp),

        horizontalAlignment =
            Alignment.CenterHorizontally,

        verticalArrangement =
            Arrangement.Center
    ) {

        Text(
            text = "WHAT DO YOU WANT?",
            color = White,
            fontSize = 17.sp,
            fontWeight = FontWeight.Light,
            letterSpacing = 2.sp
        )

        Spacer(
            modifier =
                Modifier.height(8.dp)
        )

        Text(
            text = "set an intention",
            color = Dim,
            fontSize = 7.sp,
            letterSpacing = 2.sp
        )

        Spacer(
            modifier =
                Modifier.height(17.dp)
        )

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(1.dp)
                .background(
                    Color(0xFF17282A)
                )
        )
    }
}


/* ============================================================
   HANGING OBJECT
   ============================================================ */

@Composable
fun HangingObject() {

    Column(
        horizontalAlignment =
            Alignment.CenterHorizontally
    ) {

        Row(
            horizontalArrangement =
                Arrangement.spacedBy(14.dp)
        ) {

            Box(
                Modifier
                    .width(1.dp)
                    .height(28.dp)
                    .background(Dim)
            )

            Box(
                Modifier
                    .width(1.dp)
                    .height(36.dp)
                    .background(Muted)
            )

            Box(
                Modifier
                    .width(1.dp)
                    .height(28.dp)
                    .background(Dim)
            )
        }

        Row(
            horizontalArrangement =
                Arrangement.spacedBy(10.dp)
        ) {

            Box(
                Modifier
                    .size(5.dp)
                    .background(
                        Muted,
                        RoundedCornerShape(50)
                    )
            )

            Box(
                Modifier
                    .size(5.dp)
                    .background(
                        White,
                        RoundedCornerShape(50)
                    )
            )

            Box(
                Modifier
                    .size(5.dp)
                    .background(
                        Muted,
                        RoundedCornerShape(50)
                    )
            )
        }
    }
}


/* ============================================================
   SECTION TITLE
   ============================================================ */

@Composable
fun SectionTitle(
    title: String
) {

    Row(
        modifier =
            Modifier.fillMaxWidth(),
        verticalAlignment =
            Alignment.CenterVertically
    ) {

        Text(
            text = title,
            color = Dim,
            fontSize = 9.sp,
            letterSpacing = 1.5.sp
        )

        Spacer(
            modifier =
                Modifier.width(10.dp)
        )

        Box(
            modifier = Modifier
                .weight(1f)
                .height(1.dp)
                .background(Border)
        )
    }
}


/* ============================================================
   PREMIUM CREATE ACTION
   ============================================================ */

@Composable
private fun CreateActionCard(
    symbol: String,
    title: String,
    subtitle: String,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    val accent = Color(0xFF8EB5AE)
    Column(
        modifier = modifier
            .heightIn(min = 104.dp)
            .background(
                Brush.verticalGradient(listOf(Color(0xFF142021), Panel)),
                RoundedCornerShape(18.dp)
            )
            .border(1.dp, Border, RoundedCornerShape(18.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 13.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Box(
            modifier = Modifier
                .size(30.dp)
                .background(Color(0xFF1B302F), RoundedCornerShape(10.dp))
                .border(1.dp, Color(0xFF314B47), RoundedCornerShape(10.dp)),
            contentAlignment = Alignment.Center
        ) {
            Text(symbol, color = accent, fontSize = 17.sp, fontWeight = FontWeight.Light)
        }
        Text(title, color = White, fontSize = 11.sp, letterSpacing = 1.3.sp, fontWeight = FontWeight.Medium)
        Text(subtitle, color = Muted, fontSize = 10.sp, lineHeight = 14.sp)
    }
}

/* ============================================================
   CREATE CARD
   ============================================================ */

@Composable
fun CreateCard(
    title: String,
    modifier: Modifier,
    onClick: () -> Unit
) {

    Column(
        modifier = modifier
            .height(65.dp)
            .background(
                Panel,
                RoundedCornerShape(13.dp)
            )
            .border(
                1.dp,
                Border,
                RoundedCornerShape(13.dp)
            )
            .clickable {
                onClick()
            }
            .padding(10.dp),

        verticalArrangement =
            Arrangement.SpaceBetween
    ) {

        Box(
            modifier = Modifier
                .size(7.dp)
                .background(
                    Dim,
                    RoundedCornerShape(50)
                )
        )

        Text(
            text = title,
            color = Muted,
            fontSize = 6.sp,
            letterSpacing = 1.sp
        )
    }
}


/* ============================================================
   CONTROL CARD
   ============================================================ */

@Composable
fun ControlCard(
    title: String,
    subtitle: String,
    onClick: () -> Unit = {},
    modifier: Modifier = Modifier
) {

    Row(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 64.dp)
            .background(
                Panel,
                RoundedCornerShape(14.dp)
            )
            .border(
                1.dp,
                Border,
                RoundedCornerShape(14.dp)
            )
            .clickable { onClick() }
            .padding(
                horizontal = 14.dp
            ),

        verticalAlignment =
            Alignment.CenterVertically
    ) {

        Box(
            modifier = Modifier
                .size(6.dp)
                .background(
                    Color(0xFF506A68),
                    RoundedCornerShape(50)
                )
        )

        Spacer(
            modifier =
                Modifier.width(12.dp)
        )

        Column {

            Text(
                text = title,
                color = White,
                fontSize = 11.sp,
                letterSpacing = 0.8.sp
            )

            Spacer(
                modifier =
                    Modifier.height(3.dp)
            )

            Text(
                text = subtitle,
                color = Dim,
                fontSize = 9.sp,
                letterSpacing = 0.3.sp
            )
        }
    }
}


/* ============================================================
   TIME PICKER
   ============================================================ */

fun showKaamTimePicker(
    context: Context,
    currentTime: String,
    onTimeSelected: (String) -> Unit
) {

    val calendar = Calendar.getInstance()

    val parsed = parseKaamTime(currentTime)

    val hour24 = parsed?.first ?: calendar.get(Calendar.HOUR_OF_DAY)
    val minute = parsed?.second ?: calendar.get(Calendar.MINUTE)

    val hour12 = when {
        hour24 == 0 -> 12
        hour24 > 12 -> hour24 - 12
        else -> hour24
    }

    val amPm = if (hour24 < 12) "AM" else "PM"

    val container = LinearLayout(context).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(48, 8, 48, 0)
    }

    val hourInput = EditText(context).apply {
        hint = "Hour"
        inputType = InputType.TYPE_CLASS_NUMBER
        setText(hour12.toString())
        setSelectAllOnFocus(true)
        layoutParams = LinearLayout.LayoutParams(
            0,
            LinearLayout.LayoutParams.WRAP_CONTENT,
            1f
        )
    }

    val minuteInput = EditText(context).apply {
        hint = "Minute"
        inputType = InputType.TYPE_CLASS_NUMBER
        setText(String.format(Locale.getDefault(), "%02d", minute))
        setSelectAllOnFocus(true)
        layoutParams = LinearLayout.LayoutParams(
            0,
            LinearLayout.LayoutParams.WRAP_CONTENT,
            1f
        ).apply {
            marginStart = 16
        }
    }

    val timeRow = LinearLayout(context).apply {
        orientation = LinearLayout.HORIZONTAL
        addView(hourInput)
        addView(minuteInput)
    }

    val amButton = RadioButton(context).apply {
        text = "AM"
        isChecked = amPm == "AM"
    }

    val pmButton = RadioButton(context).apply {
        text = "PM"
        isChecked = amPm == "PM"
    }

    val amPmGroup = RadioGroup(context).apply {
        orientation = RadioGroup.HORIZONTAL
        addView(amButton)
        addView(pmButton)
    }

    container.addView(timeRow)
    container.addView(amPmGroup)

    val dialog = android.app.AlertDialog.Builder(context)
        .setTitle("CUSTOM TIME")
        .setView(container)
        .setNegativeButton("CANCEL", null)
        .setPositiveButton("SET", null)
        .create()

    dialog.setOnShowListener {
        dialog.getButton(android.app.AlertDialog.BUTTON_POSITIVE).setOnClickListener {
            val hour = hourInput.text.toString().trim().toIntOrNull()
            val minuteValue = minuteInput.text.toString().trim().toIntOrNull()

            if (hour == null || hour !in 1..12) {
                hourInput.error = "Enter 1–12"
                return@setOnClickListener
            }

            if (minuteValue == null || minuteValue !in 0..59) {
                minuteInput.error = "Enter 0–59"
                return@setOnClickListener
            }

            val selectedAmPm = if (amButton.isChecked) "AM" else "PM"

            onTimeSelected(
                String.format(
                    Locale.getDefault(),
                    "%d:%02d %s",
                    hour,
                    minuteValue,
                    selectedAmPm
                )
            )

            dialog.dismiss()
        }
    }

    dialog.show()
}

/* ============================================================
   PARSE AM / PM TIME
   ============================================================ */

private fun parseKaamTime(
    value: String
): Pair<Int, Int>? = parseKaamClockTime(value)


/* ============================================================
   TASK DIALOG
   ============================================================ */

@Composable
fun TaskDialog(
    mode: String,
    existingTask: KaamTask? = null,
    initialTitle: String = "",
    onDismiss: () -> Unit,
    onSave: (
        String,
        String,
        String,
        String,
        String,
        Int,
        Int,
        String,
        String,
        Long?
    ) -> Unit
) {

    val context =
        LocalContext.current

    var taskName by remember(
        existingTask?.id
    ) {
        mutableStateOf(
            existingTask?.title ?: initialTitle
        )
    }

    var description by remember(
        existingTask?.id
    ) {
        mutableStateOf(
            existingTask?.details ?: ""
        )
    }

    var selectedWhen by remember(
        existingTask?.id
    ) {
        mutableStateOf(
            existingTask?.whenType ?: when (mode) {
                "TIMER" -> "TIMER"
                "ALARM", "ALERT", "NOTIFY", "SPECIAL" -> "CUSTOM"
                else -> "ANYTIME"
            }
        )
    }

    var customTime by remember(
        existingTask?.id
    ) {
        mutableStateOf(
            if (existingTask?.whenType == "CUSTOM") {
                existingTask.whenValue
            } else {
                ""
            }
        )
    }

    var timerMinutes by remember(
        existingTask?.id
    ) {
        mutableStateOf(
            if (existingTask?.whenType == "TIMER") {
                existingTask.whenValue
            } else {
                "30"
            }
        )
    }

    var selectedRepeat by remember(
        existingTask?.id
    ) {
        mutableStateOf(
            existingTask?.repeatType
                ?: "NONE"
        )
    }

    var intervalHours by remember(
        existingTask?.id
    ) {

        mutableStateOf(
            (existingTask?.let { canonicalIntervalMinutes(it.repeatIntervalMinutes, it.repeatValue) } ?: 120).toString()
        )
    }

    var repeatLimit by remember(existingTask?.id) {
        mutableStateOf(existingTask?.repeatLimit ?: 0)
    }

    var repeatWindowStart by remember(existingTask?.id) {
        mutableStateOf(existingTask?.repeatWindowStart ?: "")
    }
    var repeatWindowEnd by remember(existingTask?.id) {
        mutableStateOf(existingTask?.repeatWindowEnd ?: "")
    }

    var deadlineAt by remember(existingTask?.id) {
        mutableStateOf(existingTask?.dueAt)
    }

    var validationMessage by remember(existingTask?.id, mode) {
        mutableStateOf("")
    }

    val whenOptions = when (mode) {
        "TIMER" -> listOf("TIMER" to "count down from a chosen duration")
        "ALARM" -> listOf("CUSTOM" to "choose a clock time")
        "ALERT" -> listOf("CUSTOM" to "choose a time for this alert", "ANYTIME" to "keep it flexible")
        else -> listOf(
            "ANYTIME" to "no fixed time",
            "TIMER" to "remind me after a duration",
            "MORNING" to "09:00",
            "AFTERNOON" to "14:00",
            "EVENING" to "18:00",
            "CUSTOM" to "choose a specific clock time"
        )
    }

    AlertDialog(

        onDismissRequest =
            onDismiss,

        containerColor =
            Color(0xFF0B1011),

        title = {

            Text(
                text =
                    if (
                        existingTask == null
                    ) {
                        mode
                    } else {
                        "EDIT  •  $mode"
                    },

                color = White,
                fontSize = 18.sp,
                fontWeight =
                    FontWeight.Light,
                letterSpacing = 2.sp
            )
        },


        text = {

            Column(
                modifier =
                    Modifier.verticalScroll(
                        rememberScrollState()
                    )
            ) {

                Text(
                    text = when (mode) {
                        "TIMER" -> "CHOOSE A DURATION. WE'LL HANDLE THE NUDGE."
                        "ALARM" -> "PICK A CLOCK TIME THAT WORKS FOR YOU."
                        "ALERT" -> "MAKE THE IMPORTANT THING HARD TO MISS."
                        else -> "MAKE THE NEXT ACTION CLEAR AND DOABLE."
                    },
                    color = Dim,
                    fontSize = 10.sp,
                    lineHeight = 15.sp,
                    letterSpacing = 0.4.sp
                )

                if (validationMessage.isNotBlank()) {
                    Spacer(Modifier.height(8.dp))
                    Text(validationMessage, color = Color(0xFFE5A39A), fontSize = 11.sp)
                }

                Spacer(
                    modifier =
                        Modifier.height(18.dp)
                )

                OutlinedTextField(
                    value = taskName,
                    onValueChange = {
                        taskName = it
                        validationMessage = ""
                    },
                    modifier =
                        Modifier.fillMaxWidth(),
                    singleLine = true,
                    label = {
                        Text(
                            "What do you want?"
                        )
                    }
                )

                Spacer(
                    modifier =
                        Modifier.height(10.dp)
                )

                OutlinedTextField(
                    value = description,
                    onValueChange = {
                        description = it
                    },
                    modifier =
                        Modifier.fillMaxWidth(),
                    minLines = 3,
                    label = {
                        Text("Details")
                    }
                )

                Spacer(
                    modifier =
                        Modifier.height(18.dp)
                )

                Text(
                    text = "WHEN?",
                    color = Muted,
                    fontSize = 8.sp,
                    letterSpacing = 2.sp,
                    fontWeight =
                        FontWeight.Medium
                )

                Spacer(
                    modifier =
                        Modifier.height(8.dp)
                )

                Text(
                    text =
                        "Choose when this task should be due for a reminder.",
                    color = Dim,
                    fontSize = 10.sp,
                    lineHeight = 15.sp
                )

                Spacer(
                    modifier =
                        Modifier.height(12.dp)
                )


                whenOptions.forEachIndexed { index, pair ->

                    WhenOption(

                        title = pair.first,

                        subtitle =
                            if (
                                pair.first ==
                                "CUSTOM" &&
                                customTime.isNotBlank()
                            ) {
                                customTime
                            } else {
                                pair.second
                            },

                        selected =
                            selectedWhen ==
                                    pair.first

                    ) {

                        selectedWhen =
                            pair.first

                        when (pair.first) {

                            "TIMER" -> {
                                customTime = ""
                                selectedRepeat = "NONE"
                            }

                            "CUSTOM" -> {
                                showKaamTimePicker(
                                    context,
                                    customTime
                                ) {
                                    customTime = it
                                }
                            }

                            else -> {
                                customTime = ""
                            }
                        }
                    }


                    if (index < whenOptions.lastIndex) {

                        Spacer(
                            modifier =
                                Modifier.height(7.dp)
                        )
                    }
                }


                if (
                    selectedWhen ==
                    "TIMER"
                ) {

                    Spacer(
                        modifier =
                            Modifier.height(10.dp)
                    )

                    Text(
                        text = "TIMER SLOT",
                        color = Dim,
                        fontSize = 7.sp,
                        letterSpacing = 1.5.sp
                    )

                    Spacer(
                        modifier =
                            Modifier.height(8.dp)
                    )

                    val timerSlots = listOf(
                        5 to "5 MIN",
                        15 to "15 MIN",
                        30 to "30 MIN",
                        45 to "45 MIN",
                        60 to "1 HOUR",
                        90 to "1.5 HOUR",
                        120 to "2 HOURS"
                    )

                    Column(
                        verticalArrangement =
                            Arrangement.spacedBy(7.dp)
                    ) {

                        timerSlots.chunked(2).forEach { row ->

                            Row(
                                modifier =
                                    Modifier.fillMaxWidth(),
                                horizontalArrangement =
                                    Arrangement.spacedBy(7.dp)
                            ) {

                                row.forEach { slot ->

                                    TimerSlot(
                                        label = slot.second,
                                        selected =
                                            timerMinutes.toIntOrNull() == slot.first,
                                        modifier = Modifier.weight(1f)
                                    ) {
                                        timerMinutes = slot.first.toString()
                                        selectedRepeat = "NONE"
                                    }
                                }

                                if (row.size == 1) {
                                    Spacer(
                                        modifier = Modifier.weight(1f)
                                    )
                                }
                            }
                        }
                    }
                }


                if (
                    selectedWhen ==
                    "CUSTOM"
                ) {

                    Spacer(
                        modifier =
                            Modifier.height(10.dp)
                    )

                    OutlinedTextField(

                        value = customTime,

                        onValueChange = {},

                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {

                                showKaamTimePicker(
                                    context,
                                    customTime
                                ) {
                                    customTime = it
                                }
                            },

                        readOnly = true,

                        singleLine = true,

                        label = {
                            Text("Time")
                        },

                        placeholder = {
                            Text(
                                "Select time  •  AM / PM"
                            )
                        }
                    )
                }


                if (mode != "TIMER" && selectedWhen != "TIMER") {
                Spacer(
                    modifier =
                        Modifier.height(18.dp)
                )

                Text(
                    text = "REPEAT",
                    color = Muted,
                    fontSize = 8.sp,
                    letterSpacing = 2.sp,
                    fontWeight =
                        FontWeight.Medium
                )

                Spacer(
                    modifier =
                        Modifier.height(8.dp)
                )


                WhenOption(
                    title = "ONCE / OFF",
                    subtitle =
                        "no automatic reminders",
                    selected =
                        selectedRepeat ==
                                "NONE"
                ) {
                    selectedRepeat =
                        "NONE"
                }


                Spacer(
                    modifier =
                        Modifier.height(7.dp)
                )


                listOf(15 to "EVERY 15 MIN", 30 to "EVERY 30 MIN", 60 to "EVERY HOUR", 120 to "EVERY 2 HOURS", 180 to "EVERY 3 HOURS", 240 to "EVERY 4 HOURS").forEach { (minutes, label) ->
                    WhenOption(
                        title = label,
                        subtitle = "repeat at this interval until completed",
                        selected = selectedRepeat == "INTERVAL" && intervalHours.toIntOrNull() == minutes
                    ) {
                        selectedRepeat = "INTERVAL"
                        intervalHours = minutes.toString()
                    }
                    Spacer(Modifier.height(7.dp))
                }

                if (selectedRepeat == "INTERVAL") {
                    Spacer(Modifier.height(12.dp))
                    Text(
                        text = "DAILY TIME WINDOW · OPTIONAL",
                        color = Muted,
                        fontSize = 8.sp,
                        letterSpacing = 1.6.sp,
                        fontWeight = FontWeight.Medium
                    )
                    Spacer(Modifier.height(5.dp))
                    Text(
                        text = "Keep interval reminders inside a part of your day. Leave both blank for all-day reminders.",
                        color = Dim,
                        fontSize = 10.sp,
                        lineHeight = 15.sp
                    )
                    Spacer(Modifier.height(7.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                        OutlinedTextField(
                            value = repeatWindowStart,
                            onValueChange = {},
                            readOnly = true,
                            singleLine = true,
                            label = { Text("START") },
                            placeholder = { Text("Any time") },
                            modifier = Modifier.weight(1f).clickable {
                                showKaamTimePicker(context, repeatWindowStart.ifBlank { "12:00 PM" }) { repeatWindowStart = it; validationMessage = "" }
                            }
                        )
                        OutlinedTextField(
                            value = repeatWindowEnd,
                            onValueChange = {},
                            readOnly = true,
                            singleLine = true,
                            label = { Text("END") },
                            placeholder = { Text("Any time") },
                            modifier = Modifier.weight(1f).clickable {
                                showKaamTimePicker(context, repeatWindowEnd.ifBlank { "3:00 PM" }) { repeatWindowEnd = it; validationMessage = "" }
                            }
                        )
                    }
                    if (repeatWindowStart.isNotBlank() || repeatWindowEnd.isNotBlank()) {
                        TextButton(onClick = { repeatWindowStart = ""; repeatWindowEnd = ""; validationMessage = "" }) {
                            Text("CLEAR WINDOW", color = Muted, fontSize = 10.sp, letterSpacing = 1.sp)
                        }
                    }
                }

                if (selectedWhen != "ANYTIME") {
                    WhenOption(
                        title = "DAILY",
                        subtitle = "same time every day",
                        selected = selectedRepeat == "DAILY"
                    ) {
                        selectedRepeat = "DAILY"
                    }
                }

                Spacer(Modifier.height(12.dp))
                Text("DEADLINE · OPTIONAL", color = Muted, fontSize = 8.sp, letterSpacing = 1.6.sp, fontWeight = FontWeight.Medium)
                Spacer(Modifier.height(5.dp))
                Text("A date to aim for. This does not change your reminder time.", color = Dim, fontSize = 10.sp, lineHeight = 15.sp)
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = deadlineAt?.let { java.text.SimpleDateFormat("dd MMM yyyy", Locale.getDefault()).format(java.util.Date(it)) } ?: "No deadline",
                        onValueChange = {}, readOnly = true, singleLine = true, label = { Text("DUE DATE") },
                        modifier = Modifier.weight(1f).clickable {
                            val initial = Calendar.getInstance().apply { timeInMillis = deadlineAt ?: System.currentTimeMillis() }
                            DatePickerDialog(context, { _, year, month, day ->
                                deadlineAt = localDeadlineEndOfDay(year, month, day)
                                validationMessage = ""
                            }, initial.get(Calendar.YEAR), initial.get(Calendar.MONTH), initial.get(Calendar.DAY_OF_MONTH)).show()
                        }
                    )
                    if (deadlineAt != null) TextButton(onClick = { deadlineAt = null }) { Text("CLEAR", color = Muted, fontSize = 10.sp) }
                }

                if (selectedRepeat != "NONE") {
                    Spacer(Modifier.height(14.dp))
                    Text(
                        text = "OCCURRENCE LIMIT",
                        color = Muted,
                        fontSize = 8.sp,
                        letterSpacing = 2.sp,
                        fontWeight = FontWeight.Medium
                    )
                    Spacer(Modifier.height(7.dp))
                    Text(
                        text = if (repeatLimit == 0) {
                            "Keeps repeating until you mark it done."
                        } else {
                            val editorIntervalValue = intervalHours.toIntOrNull()?.coerceIn(1, 1440) ?: 120
                            val editorWhenValue = when (selectedWhen) {
                                "CUSTOM" -> customTime
                                "TIMER" -> timerMinutes
                                else -> if (existingTask?.whenType == selectedWhen) existingTask.whenValue else ""
                            }
                            val scheduleUnchanged = existingTask != null &&
                                existingTask.whenType == selectedWhen &&
                                existingTask.whenValue == editorWhenValue &&
                                existingTask.repeatType == selectedRepeat &&
                                (if (selectedRepeat == "INTERVAL") canonicalIntervalMinutes(existingTask.repeatIntervalMinutes, existingTask.repeatValue) else 0) == (if (selectedRepeat == "INTERVAL") editorIntervalValue else 0) &&
                                existingTask.repeatLimit == repeatLimit &&
                                existingTask.repeatWindowStart == repeatWindowStart && existingTask.repeatWindowEnd == repeatWindowEnd
                            val sent = if (scheduleUnchanged) (existingTask?.repeatCount ?: 0).coerceAtMost(repeatLimit) else 0
                            "$sent of $repeatLimit regular reminders sent · ${repeatLimit - sent} remaining. The first reminder counts. Saving schedule changes resets the count."
                        },
                        color = Dim,
                        fontSize = 10.sp,
                        lineHeight = 15.sp
                    )
                    Spacer(Modifier.height(7.dp))
                    Column(verticalArrangement = Arrangement.spacedBy(7.dp)) {
                        listOf(0 to "UNLIMITED", 3 to "3 TIMES", 5 to "5 TIMES", 10 to "10 TIMES").chunked(2).forEach { row ->
                            Row(horizontalArrangement = Arrangement.spacedBy(7.dp), modifier = Modifier.fillMaxWidth()) {
                                row.forEach { (limit, label) ->
                                    TimerSlot(
                                        label = label,
                                        selected = repeatLimit == limit,
                                        modifier = Modifier.weight(1f)
                                    ) { repeatLimit = limit }
                                }
                            }
                        }
                    }
                }
                } // Repeat controls apply to scheduled reminders, not duration timers.
            }
        },


        confirmButton = {

            TextButton(

                onClick = {

                    val validTimerMinutes = timerMinutes.toIntOrNull()
                    when {
                        taskName.isBlank() -> {
                            validationMessage = "Give it a short name so you know what this reminder is for."
                        }
                        selectedWhen == "CUSTOM" && parseKaamTime(customTime) == null -> {
                            validationMessage = "Choose a valid clock time, including AM or PM."
                        }
                        selectedWhen == "TIMER" && (validTimerMinutes == null || validTimerMinutes <= 0) -> {
                            validationMessage = "Choose a timer duration greater than zero."
                        }
                        repeatScheduleValidationMessage(selectedWhen, selectedRepeat) != null -> {
                            validationMessage = repeatScheduleValidationMessage(selectedWhen, selectedRepeat)!!
                        }
                        selectedRepeat == "INTERVAL" && repeatWindowValidationMessage(repeatWindowStart, repeatWindowEnd) != null -> {
                            validationMessage = repeatWindowValidationMessage(repeatWindowStart, repeatWindowEnd)!!
                        }
                        else -> {
                            validationMessage = ""
                            val repeatValue =
                                if (selectedRepeat == "INTERVAL") {
                                    intervalHours.toIntOrNull()?.coerceIn(1, 1440) ?: 120
                                } else {
                                    0
                                }

                            onSave(
                        taskName,
                        description,
                        selectedWhen,
                        if (selectedWhen == "TIMER") {
                            timerMinutes
                        } else {
                            customTime
                        },
                                selectedRepeat,
                                repeatValue,
                                if (selectedRepeat == "NONE") 0 else repeatLimit,
                                if (selectedRepeat == "INTERVAL") repeatWindowStart else "",
                                if (selectedRepeat == "INTERVAL") repeatWindowEnd else "",
                                deadlineAt
                            )
                        }
                    }
                }
            ) {

                Text(
                    text =
                        if (
                            existingTask == null
                        ) {
                            "CREATE"
                        } else {
                            "SAVE"
                        },

                    color = White,
                    letterSpacing = 1.5.sp
                )
            }
        },


        dismissButton = {

            TextButton(
                onClick = onDismiss
            ) {

                Text(
                    text = "CANCEL",
                    color = Dim,
                    letterSpacing = 1.2.sp
                )
            }
        }
    )
}


/* ============================================================
   TASK DETAILS
   ============================================================ */

@Composable
fun TaskDetailsDialog(
    task: KaamTask,
    onDismiss: () -> Unit,
    onEdit: () -> Unit,
    onToggleComplete: () -> Unit,
    onDelete: () -> Unit
) {

    AlertDialog(

        onDismissRequest =
            onDismiss,

        containerColor =
            Color(0xFF0B1011),

        title = {

            Text(
                text = task.title,
                color = White,
                fontSize = 19.sp,
                fontWeight =
                    FontWeight.Light
            )
        },

        text = {

            Column {

                DetailLine("TYPE", task.kind.uppercase(Locale.ROOT))
                Spacer(Modifier.height(10.dp))

                if (
                    task.details.isNotBlank()
                ) {

                    Text(
                        text = task.details,
                        color = Muted,
                        fontSize = 11.sp,
                        lineHeight = 17.sp
                    )

                    Spacer(
                        modifier =
                            Modifier.height(18.dp)
                    )
                }

                DetailLine(
                    "WHEN",
                    formatWhen(
                        task.whenType,
                        task.whenValue
                    )
                )

                Spacer(
                    modifier =
                        Modifier.height(10.dp)
                )

                DetailLine(
                    "REPEAT",
                    formatRepeat(task)
                )

                Spacer(
                    modifier =
                        Modifier.height(10.dp)
                )

                if (task.dueAt != null) {
                    DetailLine(
                        "DEADLINE",
                        java.text.SimpleDateFormat("dd MMM yyyy", Locale.getDefault()).format(java.util.Date(task.dueAt)) +
                            if (isTaskOverdue(task.dueAt, task.completed, System.currentTimeMillis())) " · OVERDUE" else ""
                    )
                    Spacer(Modifier.height(10.dp))
                }

                DetailLine(
                    "STATUS",
                    if (task.completed) "COMPLETED" else "ACTIVE"
                )

                Spacer(Modifier.height(10.dp))
                DetailLine(
                    "CREATED",
                    java.text.SimpleDateFormat("dd MMM yyyy · h:mm a", Locale.getDefault())
                        .format(java.util.Date(task.createdAt))
                )
                if (task.completedAt != null) {
                    Spacer(Modifier.height(10.dp))
                    DetailLine(
                        "COMPLETED",
                        java.text.SimpleDateFormat("dd MMM yyyy · h:mm a", Locale.getDefault())
                            .format(java.util.Date(task.completedAt))
                    )
                }
            }
        },

        confirmButton = {

            TextButton(
                onClick =
                    onToggleComplete
            ) {

                Text(
                    text =
                        if (
                            task.completed
                        ) {
                            "REOPEN"
                        } else {
                            "COMPLETE"
                        },

                    color = White
                )
            }
        },

        dismissButton = {

            Row {

                TextButton(
                    onClick = onEdit
                ) {
                    Text(
                        "EDIT",
                        color = Muted
                    )
                }

                TextButton(
                    onClick = onDelete
                ) {
                    Text(
                        "DELETE",
                        color =
                            Color(0xFFB28B8B)
                    )
                }
            }
        }
    )
}


@Composable
fun DetailLine(
    label: String,
    value: String
) {

    Row(
        modifier =
            Modifier.fillMaxWidth()
    ) {

        Text(
            text = label,
            color = Dim,
            fontSize = 7.sp,
            letterSpacing = 1.5.sp,
            modifier =
                Modifier.width(65.dp)
        )

        Text(
            text = value,
            color = Muted,
            fontSize = 9.sp
        )
    }
}


/* ============================================================
   TIMER SLOT
   ============================================================ */

@Composable
fun TimerSlot(
    label: String,
    selected: Boolean,
    modifier: Modifier,
    onClick: () -> Unit
) {

    Column(
        modifier = modifier
            .height(52.dp)
            .background(
                if (selected) {
                    Color(0xFF172526)
                } else {
                    Panel
                },
                RoundedCornerShape(12.dp)
            )
            .border(
                1.dp,
                if (selected) {
                    Color(0xFF607C78)
                } else {
                    Border
                },
                RoundedCornerShape(12.dp)
            )
            .clickable {
                onClick()
            },
        horizontalAlignment =
            Alignment.CenterHorizontally,
        verticalArrangement =
            Arrangement.Center
    ) {

        Text(
            text = label,
            color = if (selected) White else Muted,
            fontSize = 7.sp,
            letterSpacing = 1.1.sp,
            fontWeight =
                if (selected) FontWeight.Medium else FontWeight.Normal
        )

        Spacer(
            modifier = Modifier.height(4.dp)
        )

        Box(
            modifier = Modifier
                .width(18.dp)
                .height(1.dp)
                .background(
                    if (selected) White else Border
                )
        )
    }
}


/* ============================================================
   WHEN OPTION
   ============================================================ */

@Composable
fun WhenOption(
    title: String,
    subtitle: String,
    selected: Boolean,
    onClick: () -> Unit
) {

    Row(

        modifier = Modifier
            .fillMaxWidth()
            .background(
                if (selected) {
                    Color(0xFF111B1C)
                } else {
                    Panel
                },
                RoundedCornerShape(11.dp)
            )
            .border(
                1.dp,
                if (selected) {
                    Color(0xFF506A68)
                } else {
                    Border
                },
                RoundedCornerShape(11.dp)
            )
            .clickable {
                onClick()
            }
            .padding(
                horizontal = 12.dp,
                vertical = 10.dp
            ),

        verticalAlignment =
            Alignment.CenterVertically
    ) {

        Box(

            modifier = Modifier
                .size(15.dp)
                .background(
                    if (selected) {
                        Color(0xFF304442)
                    } else {
                        Color.Transparent
                    },
                    RoundedCornerShape(50)
                )
                .border(
                    1.dp,
                    if (selected) {
                        Color(0xFF718A86)
                    } else {
                        Border
                    },
                    RoundedCornerShape(50)
                ),

            contentAlignment =
                Alignment.Center
        ) {

            if (selected) {

                Box(
                    modifier = Modifier
                        .size(5.dp)
                        .background(
                            White,
                            RoundedCornerShape(50)
                        )
                )
            }
        }


        Spacer(
            modifier =
                Modifier.width(11.dp)
        )


        Column {

            Text(
                text = title,
                color =
                    if (selected)
                        White
                    else
                        Muted,
                fontSize = 8.sp,
                letterSpacing = 1.2.sp
            )

            Spacer(
                modifier =
                    Modifier.height(2.dp)
            )

            Text(
                text = subtitle,
                color = Dim,
                fontSize = 6.sp,
                letterSpacing = 0.8.sp
            )
        }
    }
}


/* ============================================================
   TASK CARD
   ============================================================ */

@Composable
fun TaskCard(
    task: KaamTask,
    onOpen: () -> Unit,
    onToggleComplete: () -> Unit,
    onDelete: () -> Unit
) {

    Row(

        modifier = Modifier
            .fillMaxWidth()
            .background(
                if (task.completed) {
                    Color(0xFF0A0F10)
                } else {
                    Panel
                },
                RoundedCornerShape(14.dp)
            )
            .border(
                1.dp,
                if (task.completed) {
                    Color(0xFF1B2526)
                } else {
                    Border
                },
                RoundedCornerShape(14.dp)
            )
            .clickable {
                onOpen()
            }
            .padding(14.dp),

        verticalAlignment =
            Alignment.CenterVertically
    ) {

        Box(
            modifier = Modifier
                .sizeIn(minWidth = 48.dp, minHeight = 48.dp)
                .semantics {
                    role = Role.Checkbox
                    contentDescription = if (task.completed) "Mark ${task.title} active" else "Mark ${task.title} complete"
                    stateDescription = if (task.completed) "Completed" else "Active"
                }
                .clickable(role = Role.Checkbox) { onToggleComplete() },
            contentAlignment = Alignment.Center
        ) {
            Box(
                modifier = Modifier
                    .size(24.dp)
                    .background(
                        if (task.completed) Color(0xFF304442) else Color.Transparent,
                        RoundedCornerShape(50)
                    )
                    .border(
                        1.dp,
                        if (task.completed) Color(0xFF5C7773) else Border,
                        RoundedCornerShape(50)
                    ),
                contentAlignment = Alignment.Center
            ) {
                if (task.completed) {
                    Text("✓", color = White, fontSize = 12.sp, fontWeight = FontWeight.Medium)
                }
            }
        }


        Spacer(
            modifier =
                Modifier.width(12.dp)
        )


        Column(
            modifier =
                Modifier.weight(1f)
        ) {

            Text(
                text = task.kind.uppercase(Locale.ROOT),
                color = Color(0xFF8EB5AE),
                fontSize = 10.sp,
                letterSpacing = 1.1.sp
            )
            Spacer(Modifier.height(3.dp))

            Text(
                text = task.title,
                color =
                    if (task.completed)
                        Dim
                    else
                        White,
                fontSize = 15.sp,
                lineHeight = 20.sp
            )


            if (
                task.details.isNotBlank()
            ) {

                Spacer(
                    modifier =
                        Modifier.height(4.dp)
                )

                Text(
                    text = task.details,
                    color = Dim,
                    fontSize = 11.sp,
                    lineHeight = 16.sp,
                    maxLines = 2
                )
            }

            if (task.dueAt != null) {
                Spacer(Modifier.height(5.dp))
                val overdue = isTaskOverdue(task.dueAt, task.completed, System.currentTimeMillis())
                Text(
                    text = (if (task.completed) "DEADLINE · " else if (overdue) "OVERDUE · " else "DUE · ") + java.text.SimpleDateFormat("dd MMM yyyy", Locale.getDefault()).format(java.util.Date(task.dueAt)),
                    color = if (overdue) Color(0xFFE39A86) else Muted,
                    fontSize = 10.sp,
                    letterSpacing = 0.5.sp
                )
            }

            Spacer(
                modifier =
                    Modifier.height(7.dp)
            )


            Text(
                text =
                    formatWhen(
                        task.whenType,
                        task.whenValue
                    ),
                color =
                    if (task.completed)
                        Color(0xFF718A86)
                    else
                        Muted,
                fontSize = 10.sp,
                letterSpacing = 0.5.sp
            )


            Spacer(
                modifier =
                    Modifier.height(4.dp)
            )


            Text(
                text =
                    if (task.completed) {
                        "COMPLETED"
                    } else {
                        formatRepeat(task)
                    },

                color =
                    if (task.completed)
                        Color(0xFF718A86)
                    else
                        Dim,

                fontSize = 9.sp,
                letterSpacing = 0.6.sp
            )
        }


        Spacer(
            modifier =
                Modifier.width(8.dp)
        )


        Text(
            text = "×",
            color = Dim,
            fontSize = 18.sp,
            modifier = Modifier
                .sizeIn(minWidth = 48.dp, minHeight = 48.dp)
                .semantics { contentDescription = "Delete ${task.title}"; role = Role.Button }
                .clickable(role = Role.Button) { onDelete() }
                .padding(10.dp)
        )
    }
}


/* ============================================================
   FORMATTING
   ============================================================ */

fun formatWhen(
    whenType: String,
    whenValue: String
): String {

    return when (whenType) {

        "MORNING" ->
            "MORNING  •  09:00"

        "AFTERNOON" ->
            "AFTERNOON  •  14:00"

        "EVENING" ->
            "EVENING  •  18:00"

        "CUSTOM" ->
            if (whenValue.isNotBlank()) {
                "AT $whenValue"
            } else {
                "CUSTOM TIME"
            }

        "TIMER" -> {
            val minutes =
                whenValue.toIntOrNull()?.coerceAtLeast(1)
                    ?: 30

            when {
                minutes < 60 ->
                    "IN $minutes MIN"
                minutes % 60 == 0 ->
                    "IN ${minutes / 60} HOUR${if (minutes / 60 == 1) "" else "S"}"
                else ->
                    "IN ${minutes / 60}H ${minutes % 60}M"
            }
        }

        else ->
            "ANYTIME"
    }
}


fun formatRepeat(
    task: KaamTask
): String {
    return when (task.repeatType) {
        "INTERVAL", "DAILY" -> repeatSummary(
            task.repeatType,
            task.repeatValue,
            task.repeatLimit,
            task.repeatWindowStart,
            task.repeatWindowEnd,
            task.repeatIntervalMinutes
        ).uppercase(Locale.ROOT)
        "NONE" -> if (task.whenType != "ANYTIME") "ONE-TIME REMINDER" else "NO REMINDER"
        else -> "NO REMINDER"
    }
}
