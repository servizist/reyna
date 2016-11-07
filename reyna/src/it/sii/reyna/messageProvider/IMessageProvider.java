package it.sii.reyna.messageProvider;

import it.sii.reyna.system.Message;

import java.net.URISyntaxException;

public interface IMessageProvider {
    Message getNext() throws URISyntaxException;

    void delete(Message message);

    void close();

    boolean canSend();

    void recordTemporaryError(Message message);
}
