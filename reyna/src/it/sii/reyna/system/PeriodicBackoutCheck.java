package it.sii.reyna.system;

import android.content.Context;
import android.util.Log;

public class PeriodicBackoutCheck {
    private static String TAG = "PeriodicBackoutCheck";

    protected Preferences preferences;

    public PeriodicBackoutCheck(Context context) {
        Log.v(TAG, "PeriodicBackoutCheck");

        this.preferences = new Preferences(context);
    }

    public void record(String task) {
        Log.v(TAG, String.format("record, task %s", task));

        this.preferences.putLong(task, System.currentTimeMillis());
    }

    public boolean timeElapsed(String task, long interval) {
        Log.v(TAG, "timeElapsed");

        long lastRun = this.preferences.getLong(task, -1);

        if (lastRun > System.currentTimeMillis()) {
            Log.w(TAG, String.format("lastRun in future, %d, current %d", lastRun, System.currentTimeMillis()));

            this.record(task);
            return true;
        }

        return System.currentTimeMillis() - lastRun >= interval;
    }

    public long getLastRecordedTime(String task) {
        Log.v(TAG, String.format("getLastRecorded, task %s", task));

        return this.preferences.getLong(task, -1);
    }
}
