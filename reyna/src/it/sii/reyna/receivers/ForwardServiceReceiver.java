package it.sii.reyna.receivers;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.util.Log;

import it.sii.reyna.services.ForwardService;

public class ForwardServiceReceiver extends BroadcastReceiver {
    private static final String TAG = "ForwardServiceReceiver";

    @Override
    public void onReceive(Context context, Intent intent) {
        Log.v(TAG, "onReceive");

        context.startService(new Intent(context, ForwardService.class));
    }
}
