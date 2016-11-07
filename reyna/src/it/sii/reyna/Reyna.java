package it.sii.reyna;

import android.content.Context;
import android.os.AsyncTask;
import it.sii.reyna.services.StoreService;
import it.sii.reyna.system.Message;

/**
 * Created by ldematte on 11/4/16.
 */
public class Reyna {

    public interface PostResponse {
        void onPostSuccess(Long messageID);
        void onPostError(String errorMessage);
    }

    public static void sendMessage(final Message message, final Context context, final PostResponse responseHandler) {
        new PostAsyncTask(responseHandler, message, context).execute();
    }

    private static class PostAsyncTask extends AsyncTask<Void, Void, String> {
        private final PostResponse responseHandler;
        private final Message message;
        private final Context context;

        public PostAsyncTask(PostResponse responseHandler, Message message, Context context) {
            this.responseHandler = responseHandler;
            this.message = message;
            this.context = context;
        }

        @Override
        protected String doInBackground(Void... tries) {

            // Can we send it right away?
            if (Dispatcher.canSend(context) == Dispatcher.ResultStatus.OK) {

                Dispatcher.Result result = Dispatcher.sendMessage(context, message);
                // Success or permanent failure: return
                if (result.getStatus() == Dispatcher.ResultStatus.PERMANENT_ERROR)
                    return result.getData();
                else if (result.getStatus() == Dispatcher.ResultStatus.OK) {
                    return null;
                }
            }

            // Cannot send now, or sendMessage gave a temporary failure:
            // Enqueue
            StoreService.start(context, message);
            return null;
        }

        @Override
        protected void onPostExecute(String errorMessage) {
            if (responseHandler != null) {
                if (errorMessage == null) {
                    responseHandler.onPostSuccess(message.getId());
                } else {
                    responseHandler.onPostError(errorMessage);
                }
            }
        }
    }
}
