package it.sii.reyna.services;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.support.v4.content.WakefulBroadcastReceiver;
import android.util.Log;
import it.sii.reyna.blackout.TimeRange;
import it.sii.reyna.system.Message;
import it.sii.reyna.system.Preferences;
import it.sii.reyna.system.WakefulService;
import it.sii.reyna.Repository;

import java.net.URI;

public class StoreService extends WakefulService {
    private static String TAG = "StoreService";

    private static final long MINIMUM_STORAGE_LIMIT = 1867776; // 1Mb 800Kb

    private static final String MESSAGE = "MESSAGE";

    private Preferences preferences = null;

    protected Repository repository;

    public StoreService() {
        super(StoreService.class.getName());

        Log.v(TAG, "StoreService()");
        this.repository = new Repository(this);
        this.preferences = new Preferences(this);
    }

    public static void start(Context context, Message message) {
        Log.v(TAG, "start");

        Intent service = new Intent(context, StoreService.class);
        service.putExtra(StoreService.MESSAGE, message);
        ComponentName componentName = WakefulBroadcastReceiver.startWakefulService(context, service);
        if (componentName == null) {
            context.startService(service);
        }
    }

    public static void setCellularDataBlackout(Context context, TimeRange range) {
        Log.v(TAG, "setCellularDataBlackout: " + range);
        new Preferences(context).saveCellularDataBlackout(range);
    }

    public static void resetCellularDataBlackout(Context context) {
        Log.v(TAG, "resetCellularDataBlackout");

        new Preferences(context).resetCellularDataBlackout();
    }

    public static void setWlanBlackout(Context context, String range) {
        Log.v(TAG, "setWlanBlackout: " + range);
        new Preferences(context).saveWlanBlackout(range);
    }

    public static void setWwanBlackout(Context context, String range) {
        Log.v(TAG, "setWwanBlackout: " + range);
        new Preferences(context).saveWwanBlackout(range);
    }

    public static void setWwanRoamingBlackout(Context context, boolean value) {
        Log.v(TAG, "setWwanRoamingBlackout: " + value);
        new Preferences(context).saveWwanRoamingBlackout(value);
    }

    public static void setOnChargeBlackout(Context context, boolean value) {
        Log.v(TAG, "setOnChargeBlackout: " + value);
        new Preferences(context).saveOnChargeBlackout(value);
    }

    public static void setOffChargeBlackout(Context context, boolean value) {
        Log.v(TAG, "setOffChargeBlackout: " + value);
        new Preferences(context).saveOffChargeBlackout(value);
    }

    public static void setStorageSizeLimit(Context context, long limit) {
        Log.v(TAG, "setStorageSizeLimit, limit: " + limit);
        limit = limit < MINIMUM_STORAGE_LIMIT ? MINIMUM_STORAGE_LIMIT : limit;

        Log.v(TAG, "setStorageSizeLimit,  limit: " + limit);
        Preferences preferences = new Preferences(context);
        preferences.saveStorageSize(limit);

        Repository repo = new Repository(context);
        repo.shrinkDb(limit);
    }

    public static void setNonRecurringWwanBlackoutStartTime(Context context, long startTimeUtc) {
        Log.v(TAG, "setNonRecurringWwanBlackoutStartTime: " + startTimeUtc);
        new Preferences(context).saveNonRecurringWwanBlackoutStartTime(startTimeUtc);
    }

    public static void setNonRecurringWwanBlackoutEndTime(Context context, long endTimeUtc) {
        Log.v(TAG, "setNonRecurringWwanBlackoutEndTime: " + endTimeUtc);
        new Preferences(context).saveNonRecurringWwanBlackoutEndTime(endTimeUtc);
    }

    public static void resetNonRecurringWwanBlackout(Context context) {
        Log.v(TAG, "resetNonRecurringWwanBlackout");
        new Preferences(context).resetNonRecurringWwanBlackout();
    }

    public static long getStorageSizeLimit(Context context) {
        Log.v(TAG, "getStorageSizeLimit");
        Preferences preferences = new Preferences(context);
        long result =  preferences.getStorageSize();
        Log.v(TAG, "getStorageSizeLimit, size: " + result);
        return result;
    }

    public static void resetStorageSizeLimit(Context context) {
        Log.v(TAG, "resetStorageSizeLimit");
        Preferences preferences = new Preferences(context);
        preferences.resetStorageSize();
    }

    public static void setBatchUploadConfiguration(Context context, boolean value, URI url, long checkInterval) {
        Log.v(TAG, "setBatchUploadConfiguration: " + value);
        Preferences preferences = new Preferences(context);

        preferences.saveBatchUpload(value);
        preferences.saveBatchUploadUrl(url);
        preferences.saveBatchUploadCheckInterval(checkInterval);
    }

    @Override
    protected void processIntent(Intent intent) {
        Log.v(TAG, "onHandleIntent");

        Message message = (Message)intent.getSerializableExtra(MESSAGE);
        if(message != null) {
            this.insert(message);
        }
    }

    private void insert(Message message) {
        Log.v(TAG, "insert");

        long limit = getStorageSizeLimit(this);
        Log.v(TAG, "insert, getStorageSizeLimit: " + limit);

        try {
            if (limit == -1) {
                this.repository.insert(message);
            }
            else {
                this.repository.insert(message, getStorageSizeLimit(this));
            }

            ForwardService.start(this);
        } finally {
            this.repository.close();
        }
    }
}
