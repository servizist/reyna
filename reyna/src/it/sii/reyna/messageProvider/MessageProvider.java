package it.sii.reyna.messageProvider;

import android.util.Log;
import it.sii.reyna.system.Header;
import it.sii.reyna.system.Message;
import it.sii.reyna.Repository;

import java.net.URISyntaxException;

public class MessageProvider implements IMessageProvider {

    private static final String TAG = "MessageProvider";

    protected Repository repository;

    public MessageProvider(Repository repository) {
        Log.v(MessageProvider.TAG, "MessageProvider");

        this.repository = repository;
    }

    public Message getNext() throws URISyntaxException {
        Log.v(MessageProvider.TAG, "getNext");

        Message message = this.repository.getNext();

        if (message == null) {
            Log.v(MessageProvider.TAG, "getNext, null message");
            return null;
        }
        return message;
    }

    public void delete(Message message) {
        Log.v(MessageProvider.TAG, "delete");

        this.repository.delete(message);
    }

    @Override
    public void close() {
        Log.v(MessageProvider.TAG, "close");

        this.repository.close();
    }

    @Override
    public boolean canSend() {
        return true;
    }

    private void addReynaSpecificHeaders(Message message) {
        Log.v(MessageProvider.TAG, "addReynaSpecificHeaders");
        Header header = new Header("reyna-id", message.getId().toString());
        message.addHeader(header);
    }
}
