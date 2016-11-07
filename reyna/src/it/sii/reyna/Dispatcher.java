package it.sii.reyna;

import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.text.TextUtils;
import android.util.Log;
import it.sad.sii.network.RestClient;
import it.sad.sii.network.RestResponse;
import it.sii.reyna.blackout.TimeRange;
import it.sii.reyna.blackout.BlackoutTime;
import it.sii.reyna.system.Header;
import it.sii.reyna.system.Message;
import it.sii.reyna.system.Preferences;

import java.text.ParseException;
import java.util.*;

public class Dispatcher {

    private static final String TAG = "Dispatcher";

    public enum ResultStatus {
        OK, PERMANENT_ERROR, TEMPORARY_ERROR, BLACKOUT, NOTCONNECTED
    }

    public static class Result {
        private final ResultStatus status;
        private final String data;

        public Result(ResultStatus status, String data) {
            this.status = status;
            this.data = data;
        }

        public Result(ResultStatus status) {
            this.status = status;
            this.data = null;
        }

        public ResultStatus getStatus() {
            return status;
        }

        public String getData() {
            return data;
        }
    }

    public static Result sendMessage(Context context, Message message) {
        Log.v(TAG, "sendMessage");

        ResultStatus resultStatus = Dispatcher.canSend(context);
        if(resultStatus != ResultStatus.OK) {
            return new Result(resultStatus);
        }

        RestClient client;
        try {
            client = new RestClient(message.getUrl(), message.getUsername(),
                                    message.getPassword(), 5000, null);

        } catch (Exception e) {
            Log.e(TAG, "Cannot create RestClient", e);
            return new Result(resultStatus, e.getMessage());
        }

        try {
            //message.getHeaders().stream().collect(Collectors.toMap(Header::getKey, Header::getValue))
            Map<String, String> headers = new HashMap<>();
            for (Header header: message.getHeaders()) {
                headers.put(header.getKey(), header.getValue());
            }

            RestResponse response = client.postResponse("", Collections.<String, String>emptyMap(),
                                                        message.getBody(),
                                                        headers);
            if (response.isOk())
                return new Result(ResultStatus.OK);
            else if (response.isTransientFailure())
                return new Result(ResultStatus.TEMPORARY_ERROR, response.getData());
            else
                return new Result(ResultStatus.PERMANENT_ERROR, response.getData());
        }
        catch (Exception e) {
            Log.d(TAG, "Exception in Dispatcher.sendMessage", e);
            if (RestResponse.isTransientException(e))
                return new Result(ResultStatus.TEMPORARY_ERROR, e.getMessage());
            else
                return new Result(ResultStatus.PERMANENT_ERROR, e.getMessage());
        }
    }

    public static ResultStatus canSend(Context context) {
        return canSend(context, new GregorianCalendar());
    }

    protected static ResultStatus canSend(Context context, GregorianCalendar now) {
        Log.v(TAG, "canSend start");
        ConnectivityManager connectivityManager = (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);
        NetworkInfo info = connectivityManager.getActiveNetworkInfo();
        if (info == null || !info.isConnectedOrConnecting()) {
            Log.v(TAG, "not connected");
            return ResultStatus.NOTCONNECTED;
        }

        Preferences preferences = new Preferences(context);
        BlackoutTime blackoutTime = new BlackoutTime();

        String startTime = preferences.getNonRecurringWwanBlackoutStartTimeAsString();
        String endTime = preferences.getNonRecurringWwanBlackoutEndTimeAsString();
        if (isMobile(info) && isNonRecurringBlackout(startTime, endTime, now.getTimeInMillis())) {
            Log.v(TAG, "blackout because mobile and current time is within non recurring WWAN blackout period");
            return ResultStatus.BLACKOUT;
        }

        if (TextUtils.isEmpty(preferences.getWwanBlackout())) {
            Log.v(TAG, "save cellular data backward compatibility");
            saveCellularDataAsWwanForBackwardCompatibility(preferences);
        }

        if (Dispatcher.isBatteryCharging(context) && !preferences.canSendOnCharge()) {
            Log.v(TAG, "blackout because charging and cant send on charge");
            return ResultStatus.BLACKOUT;
        }
        if (!Dispatcher.isBatteryCharging(context) && !preferences.canSendOffCharge()) {
            Log.v(TAG, "blackout because not charging and cant send off charge");
            return ResultStatus.BLACKOUT;
        }
        if (isRoaming(info) && !preferences.canSendOnRoaming()) {
            Log.v(TAG, "blackout because roaming and cant send on roaming");
            return ResultStatus.BLACKOUT;
        }
        try {
            if (isWifi(info) && !canSendNow(blackoutTime, preferences.getWlanBlackout(), now)) {
                Log.v(TAG, "blackout because wifi and cant send at " + preferences.getWlanBlackout());
                return ResultStatus.BLACKOUT;
            }
            if (isMobile(info) && !canSendNow(blackoutTime, preferences.getWwanBlackout(), now)) {
                Log.v(TAG, "blackout because mobile and cant send at " + preferences.getWwanBlackout());
                return ResultStatus.BLACKOUT;
            }
        } catch (ParseException e) {
            Log.w(TAG, "canSend", e);
            return ResultStatus.OK;
        }

        Log.v(TAG, "canSend ok");
        return ResultStatus.OK;
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
