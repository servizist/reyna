package it.sii.reyna;

import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.text.TextUtils;
import android.util.Log;
import it.sad.sii.network.RestClient;
import it.sii.reyna.blackout.TimeRange;
import it.sii.reyna.blackout.BlackoutTime;
import it.sii.reyna.system.Message;
import it.sii.reyna.system.Preferences;

import java.text.ParseException;
import java.util.GregorianCalendar;
import java.util.Locale;

public class Dispatcher {

    private static final String TAG = "Dispatcher";

    public enum Result {
        OK, PERMANENT_ERROR, TEMPORARY_ERROR, BLACKOUT, NOTCONNECTED
    }

    public Result sendMessage(Context context, Message message) {
        Log.v(TAG, "sendMessage");

        Result result = Dispatcher.canSend(context);
        if(result != Result.OK) {
            return result;
        }

        RestClient client;
        try {
            client = new RestClient(message.getUrl(), message.getUsername(),
                                    message.getPassword(), 2000, null);

        } catch (Exception e) {
            Log.e(TAG, "parseHttpPost", e);
            return Result.PERMANENT_ERROR;
        }

        try {
            // TODO: add headers
            int resultCode = client.post("", message.getBody());
            return Dispatcher.getResult(resultCode);
        }
        catch (Exception e) {
            Log.d(TAG, "tryToExecute", e);
            Log.i(TAG, "tryToExecute: temporary error");
            return Result.TEMPORARY_ERROR;
        }
    }

    public static Result canSend(Context context) {
        return canSend(context, new GregorianCalendar());
    }

    protected static Result canSend(Context context, GregorianCalendar now) {
        Log.v(TAG, "canSend start");
        ConnectivityManager connectivityManager = (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);
        NetworkInfo info = connectivityManager.getActiveNetworkInfo();
        if (info == null || !info.isConnectedOrConnecting()) {
            Log.v(TAG, "not connected");
            return Result.NOTCONNECTED;
        }

        Preferences preferences = new Preferences(context);
        BlackoutTime blackoutTime = new BlackoutTime();

        String startTime = preferences.getNonRecurringWwanBlackoutStartTimeAsString();
        String endTime = preferences.getNonRecurringWwanBlackoutEndTimeAsString();
        if (isMobile(info) && isNonRecurringBlackout(startTime, endTime, now.getTimeInMillis())) {
            Log.v(TAG, "blackout because mobile and current time is within non recurring WWAN blackout period");
            return Result.BLACKOUT;
        }

        if (TextUtils.isEmpty(preferences.getWwanBlackout())) {
            Log.v(TAG, "save cellular data backward compatibility");
            saveCellularDataAsWwanForBackwardCompatibility(preferences);
        }

        if (Dispatcher.isBatteryCharging(context) && !preferences.canSendOnCharge()) {
            Log.v(TAG, "blackout because charging and cant send on charge");
            return Result.BLACKOUT;
        }
        if (!Dispatcher.isBatteryCharging(context) && !preferences.canSendOffCharge()) {
            Log.v(TAG, "blackout because not charging and cant send off charge");
            return Result.BLACKOUT;
        }
        if (isRoaming(info) && !preferences.canSendOnRoaming()) {
            Log.v(TAG, "blackout because roaming and cant send on roaming");
            return Result.BLACKOUT;
        }
        try {
            if (isWifi(info) && !canSendNow(blackoutTime, preferences.getWlanBlackout(), now)) {
                Log.v(TAG, "blackout because wifi and cant send at " + preferences.getWlanBlackout());
                return Result.BLACKOUT;
            }
            if (isMobile(info) && !canSendNow(blackoutTime, preferences.getWwanBlackout(), now)) {
                Log.v(TAG, "blackout because mobile and cant send at " + preferences.getWwanBlackout());
                return Result.BLACKOUT;
            }
        } catch (ParseException e) {
            Log.w(TAG, "canSend", e);
            return Result.OK;
        }

        Log.v(TAG, "canSend ok");
        return Result.OK;
    }

    private static boolean canSendNow(BlackoutTime blackoutTime, String window, GregorianCalendar now) throws ParseException {
        return blackoutTime.canSendAtTime(now, window);
    }

    private static boolean isNonRecurringBlackout(String startUtc, String endUtc, long nowUtc) {
        if(startUtc == null || startUtc.isEmpty() || endUtc == null || endUtc.isEmpty()) {
            return false;
        }

        return nowUtc >= Long.parseLong(startUtc) && nowUtc < Long.parseLong(endUtc);
    }

    private static void saveCellularDataAsWwanForBackwardCompatibility(Preferences preferences) {
        TimeRange timeRange = preferences.getCellularDataBlackout();
        if(timeRange != null) {

            int hourFrom = (int) Math.floor(timeRange.getFrom().getMinuteOfDay() / 60);
            int minuteFrom = timeRange.getFrom().getMinuteOfDay() % 60;
            String blackoutFrom = zeroPad(hourFrom) + ":" + zeroPad(minuteFrom);

            int hourTo = (int) Math.floor(timeRange.getTo().getMinuteOfDay() / 60);
            int minuteTo = timeRange.getTo().getMinuteOfDay() % 60;

            String blackoutTo = zeroPad(hourTo) + ":" + zeroPad(minuteTo);
            preferences.saveWwanBlackout(blackoutFrom + "-" + blackoutTo);
        }
    }

    private static String zeroPad(int toBePadded) {
        return String.format(Locale.US,"%02d", toBePadded);
    }

    private static boolean isRoaming(NetworkInfo info) {
        return info.isRoaming();
    }

    private static boolean isWifi(NetworkInfo info) {
        return info.getType() == ConnectivityManager.TYPE_WIFI;
    }

    private static boolean isMobile(NetworkInfo info) {
        int type = info.getType();
        return type == ConnectivityManager.TYPE_MOBILE ||
            type == ConnectivityManager.TYPE_MOBILE_DUN ||
            type == ConnectivityManager.TYPE_MOBILE_HIPRI ||
            type == ConnectivityManager.TYPE_MOBILE_MMS ||
            type == ConnectivityManager.TYPE_MOBILE_SUPL ||
            type == ConnectivityManager.TYPE_WIMAX;
    }

    protected static Result getResult(int statusCode) {
        Log.v(TAG, "getResult: " + statusCode);

        if (statusCode >= 200 && statusCode < 300)
            return Result.OK;
        if (statusCode >= 300 && statusCode < 500)
            return Result.PERMANENT_ERROR;
        if (statusCode >= 500 && statusCode < 600)
            return Result.TEMPORARY_ERROR;

        return Result.PERMANENT_ERROR;
    }

    public static boolean isBatteryCharging(Context context) {
        Intent batteryStatus = context.registerReceiver(null, new IntentFilter(Intent.ACTION_BATTERY_CHANGED));
        if(batteryStatus == null) return false;
        Integer plugged = batteryStatus.getIntExtra(android.os.BatteryManager.EXTRA_PLUGGED, -1);
        return plugged == android.os.BatteryManager.BATTERY_PLUGGED_AC ||
                plugged == android.os.BatteryManager.BATTERY_PLUGGED_USB ||
                // wireless!
                plugged == 4 ||
                // unknown
                plugged == 3;
    }
}
