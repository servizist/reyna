package it.sii.reyna.services;

import android.content.Context;
import android.content.Intent;
import android.support.v4.content.WakefulBroadcastReceiver;
import android.util.Log;
import it.sii.reyna.Dispatcher;
import it.sii.reyna.Dispatcher.ResultStatus;

import it.sii.reyna.messageProvider.IMessageProvider;
import it.sii.reyna.messageProvider.MessageProvider;

import it.sii.reyna.Repository;
import it.sii.reyna.system.*;

public class ForwardService extends WakefulService {

    private static String TAG = "ForwardService";

    private static String PERIODIC_BACKOUT_TEMPORARY_ERROR = "ForwardService_Backout_Temporary_Error";

    protected static final long SLEEP_MILLISECONDS = 1000; // 1 second

    protected static final long TEMPORARY_ERROR_MILLISECONDS = 1 * 60 * 1000; // 1 minute

    protected PeriodicBackoutCheck periodicBackoutCheck;

    protected Repository repository = null;

    public ForwardService() {
        super(ForwardService.class.getName());

        Log.v(TAG, "ForwardService()");

        this.periodicBackoutCheck = new PeriodicBackoutCheck(this);
        this.repository = new Repository(this);
    }

    public static void start(Context context) {
        Log.v(ForwardService.TAG, "start");

        Intent serviceIntent = new Intent();
        serviceIntent.setClass(context, ForwardService.class);
        context.startService(serviceIntent);
        WakefulBroadcastReceiver.startWakefulService(context, serviceIntent);
    }

    @Override
    protected void processIntent(Intent intent) {
        Log.v(TAG, "onHandleIntent");

        IMessageProvider messageProvider = this.getMessageProvider();

        try {

            if(!this.periodicBackoutCheck.timeElapsed(ForwardService.PERIODIC_BACKOUT_TEMPORARY_ERROR, ForwardService.TEMPORARY_ERROR_MILLISECONDS)) {
                Log.i(TAG, "ForwardService: temporary error, backing off...");
                return;
            }

            ResultStatus canSend = Dispatcher.canSend(this);
            if (canSend != ResultStatus.OK) {
                Log.v(TAG, "ForwardService: cannot send " + canSend);
                return;
            }

            if (!messageProvider.canSend()) {
                Log.v(TAG, "ForwardService: messageProvider cannot send");
                return;
            }

            Message message = messageProvider.getNext();
            while(message != null) {
                Thread.sleep(SLEEP_MILLISECONDS);

                Log.v(TAG, "ForwardService: processing message " + message.getId());

                ResultStatus resultStatus = Dispatcher.sendMessage(this, message).getStatus();

                Log.i(TAG, "ForwardService: send message result: " + resultStatus.toString());

                if(resultStatus == ResultStatus.TEMPORARY_ERROR) {
                    Log.i(TAG, "ForwardService: temporary error, backing off...");
                    messageProvider.recordTemporaryError(message);
                    this.periodicBackoutCheck.record(ForwardService.PERIODIC_BACKOUT_TEMPORARY_ERROR);
                    return;
                }

                if(resultStatus == ResultStatus.BLACKOUT || resultStatus == ResultStatus.NOTCONNECTED) {
                    return;
                }

                // OK or Permanent Error
                messageProvider.delete(message);
                message = messageProvider.getNext();
            }

        } catch(Exception e) {
            Log.e(TAG, "onHandleIntent", e);
        } finally {
            messageProvider.close();
        }
    }

    protected IMessageProvider getMessageProvider() {
        Log.v(TAG, "getMessageProvider MessageProvider");
        return new MessageProvider(repository);
    }
}
