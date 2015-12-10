package com.b2msolutions.reyna.services;

import android.content.Context;
import android.content.Intent;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import com.b2msolutions.reyna.*;
import com.b2msolutions.reyna.Dispatcher.Result;
import com.b2msolutions.reyna.system.Message;
import com.b2msolutions.reyna.system.PeriodicBackoutCheck;
import com.b2msolutions.reyna.system.Preferences;
import com.b2msolutions.reyna.system.Thread;
import com.b2msolutions.reyna.messageProvider.BatchProvider;
import com.b2msolutions.reyna.messageProvider.IMessageProvider;
import com.b2msolutions.reyna.messageProvider.MessageProvider;
import junit.framework.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import org.robolectric.shadows.ShadowConnectivityManager;

import java.io.IOException;
import java.net.URISyntaxException;

import static junit.framework.Assert.assertNull;
import static junit.framework.Assert.assertTrue;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.mockito.Mockito.*;
import static test.Assert.assertServiceStartedOrgRobolectric;

@RunWith(RobolectricTestRunner.class)
public class ForwardServiceTest {

    private ForwardService forwardService;

    @Mock Dispatcher dispatcher;

    @Mock
    IMessageProvider messageProvider;

    @Mock Thread thread;

    @Mock
    NetworkInfo networkInfo;

    @Mock
    PeriodicBackoutCheck periodicBackoutCheck;

    private Context context;

    @Before
    public void setup() {
        MockitoAnnotations.initMocks(this);

        this.context = Robolectric.application.getApplicationContext();
        this.forwardService = Robolectric.setupService(ForwardService.class);
        this.forwardService.dispatcher = dispatcher;
        this.forwardService.messageProvider = messageProvider;
        this.forwardService.thread = thread;
        this.forwardService.periodicBackoutCheck = this.periodicBackoutCheck;

        ConnectivityManager connectivityManager = (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);
        ShadowConnectivityManager shadowConnectivityManager = Robolectric.shadowOf_(connectivityManager);
        shadowConnectivityManager.setActiveNetworkInfo(networkInfo);
        when(networkInfo.getType()).thenReturn(ConnectivityManager.TYPE_WIFI);
        when(networkInfo.isConnectedOrConnecting()).thenReturn(true);

        doReturn(true).when(this.messageProvider).canSend();
        doReturn(true).when(this.periodicBackoutCheck).timeElapsed("ForwardService_Backout_Temporary_Error", 300000);
    }

    @Test
    public void sleepTimeoutShouldBeCorrect() {
      assertEquals(1000, ForwardService.SLEEP_MILLISECONDS);
    };

    @Test
    public void temporaryErrorTimeoutShouldBeCorrect() {
        assertEquals(300000, ForwardService.TEMPORARY_ERROR_MILLISECONDS);
    };

    @Test
    public void testConstruction() {
        this.forwardService = new ForwardService();

        assertNotNull(this.forwardService);
        assertNotNull(this.forwardService.periodicBackoutCheck);
        assertNull(this.forwardService.messageProvider);
    }

    @Test
    public void whenBatchModeEnabledMessageProviderShouldBeBatchProvider() {
        new Preferences(this.forwardService).saveBatchUpload(true);
        this.forwardService = new ForwardService();
        this.forwardService.onHandleIntent(new Intent());

        assertNotNull(this.forwardService);
        assertNotNull(this.forwardService.messageProvider);
        assertEquals(BatchProvider.class, this.forwardService.messageProvider.getClass());
    }

    @Test
    public void whenBatchModeDisabledMessageProviderShouldBeMessageProvider() {
        new Preferences(this.forwardService).saveBatchUpload(false);
        this.forwardService = new ForwardService();
        this.forwardService.onHandleIntent(new Intent());

        assertNotNull(this.forwardService);
        assertNotNull(this.forwardService.messageProvider);
        assertEquals(MessageProvider.class, this.forwardService.messageProvider.getClass());
    }

    @Test
    public void whenNotNullIntentShouldNotThrow() {
        this.forwardService.onHandleIntent(null);
    }

    @Test
    public void whenThereAreNoMessagesShouldNotThrow() {
        this.forwardService.onHandleIntent(null);
    }

    @Test
    public void whenThereAreNoMessagesShouldNotSleep() throws InterruptedException {
        this.forwardService.onHandleIntent(new Intent());
        verify(this.thread, never()).sleep(anyLong());
    }

    @Test
    public void whenMoveNextThrowsShouldNotThrow() throws URISyntaxException {
        when(this.messageProvider.getNext()).thenThrow(new URISyntaxException("", ""));
        this.forwardService.onHandleIntent(new Intent());
    }

    @Test
    public void whenSingleMessageAndDispatchReturnsOKShouldDeleteMessage() throws URISyntaxException, InterruptedException {
        Message message = mock(Message.class);
        when(this.messageProvider.getNext()).thenReturn(message).thenReturn(null);
        when(this.dispatcher.sendMessage(this.forwardService, message)).thenReturn(Result.OK);

        this.forwardService.onHandleIntent(new Intent());

        InOrder inorder = inOrder(this.thread, this.dispatcher, this.messageProvider);

        inorder.verify(this.thread).sleep(ForwardService.SLEEP_MILLISECONDS);
        inorder.verify(this.dispatcher).sendMessage(this.forwardService, message);
        inorder.verify(this.messageProvider).delete(message);

        verify(this.periodicBackoutCheck, never()).record("ForwardService_Backout_Temporary_Error");
    }

    @Test
    public void whenCallingOnHandleIntentAndMessageProviderThrowsShouldNotThrow() throws URISyntaxException, InterruptedException {
        doThrow(IOException.class).when(this.messageProvider).getNext();

        this.forwardService.onHandleIntent(new Intent());

        verify(this.messageProvider).close();
    }


    @Test
    public void whenTwoMessagesAndDispatchReturnsOKShouldDeleteMessages() throws URISyntaxException, InterruptedException {
        Message message1 = mock(Message.class);
        Message message2 = mock(Message.class);
        when(this.messageProvider.getNext())
            .thenReturn(message1)
            .thenReturn(message2)
            .thenReturn(null);

        when(this.dispatcher.sendMessage(this.forwardService, message1)).thenReturn(Result.OK);
        when(this.dispatcher.sendMessage(this.forwardService, message2)).thenReturn(Result.OK);

        this.forwardService.onHandleIntent(new Intent());
        InOrder inorder = inOrder(this.thread, this.dispatcher, this.messageProvider);

        inorder.verify(this.thread).sleep(ForwardService.SLEEP_MILLISECONDS);
        inorder.verify(this.dispatcher).sendMessage(this.forwardService, message1);
        inorder.verify(this.messageProvider).delete(message1);
        inorder.verify(this.thread).sleep(ForwardService.SLEEP_MILLISECONDS);
        inorder.verify(this.dispatcher).sendMessage(this.forwardService, message2);
        inorder.verify(this.messageProvider).delete(message2);

        verify(this.periodicBackoutCheck, never()).record("ForwardService_Backout_Temporary_Error");
    }

    @Test
    public void whenSingleMessageAndDispatchReturnsTemporaryErrorShouldNotDeleteMessage() throws URISyntaxException, InterruptedException {
        Message message = mock(Message.class);
        when(this.messageProvider.getNext()).thenReturn(message).thenReturn(null);
        when(this.dispatcher.sendMessage(this.forwardService, message)).thenReturn(Result.TEMPORARY_ERROR);

        this.forwardService.onHandleIntent(new Intent());
        verify(this.dispatcher).sendMessage(this.forwardService, message);
        verify(this.messageProvider, never()).delete(message);

        verify(this.periodicBackoutCheck).record("ForwardService_Backout_Temporary_Error");
    }

    @Test
    public void whenSingleMessageAndDispatchReturnsBlackoutShouldNotDeleteMessage() throws URISyntaxException, InterruptedException {
        Message message = mock(Message.class);
        when(this.messageProvider.getNext()).thenReturn(message).thenReturn(null);
        when(this.dispatcher.sendMessage(this.forwardService, message)).thenReturn(Result.BLACKOUT);

        this.forwardService.onHandleIntent(new Intent());
        verify(this.dispatcher).sendMessage(this.forwardService, message);
        verify(this.messageProvider, never()).delete(message);

        verify(this.periodicBackoutCheck, never()).record("ForwardService_Backout_Temporary_Error");
    }

    @Test
    public void whenSingleMessageAndDispatchReturnsNotConnectedShouldNotDeleteMessage() throws URISyntaxException, InterruptedException {
        Message message = mock(Message.class);
        when(this.messageProvider.getNext()).thenReturn(message).thenReturn(null);
        when(this.dispatcher.sendMessage(this.forwardService, message)).thenReturn(Result.NOTCONNECTED);

        this.forwardService.onHandleIntent(new Intent());
        verify(this.dispatcher).sendMessage(this.forwardService, message);
        verify(this.messageProvider, never()).delete(message);

        verify(this.periodicBackoutCheck, never()).record("ForwardService_Backout_Temporary_Error");
    }

    @Test
    public void whenTwoMessagesAndFirstDispatchReturnsTemporaryErrorShouldNotDeleteMessages() throws URISyntaxException, InterruptedException {
        Message message1 = mock(Message.class);
        Message message2 = mock(Message.class);
        when(this.messageProvider.getNext())
            .thenReturn(message1)
            .thenReturn(message2)
            .thenReturn(null);

        when(this.dispatcher.sendMessage(this.forwardService, message1)).thenReturn(Result.TEMPORARY_ERROR);

        this.forwardService.onHandleIntent(new Intent());
        InOrder inorder = inOrder(this.dispatcher, this.messageProvider);

        inorder.verify(this.dispatcher).sendMessage(this.forwardService, message1);
        inorder.verify(this.messageProvider, never()).delete(message1);
        inorder.verify(this.dispatcher, never()).sendMessage(this.forwardService, message2);
        inorder.verify(this.messageProvider, never()).delete(message2);

        verify(this.periodicBackoutCheck).record("ForwardService_Backout_Temporary_Error");
    }

    @Test
    public void whenTwoMessagesAndFirstDispatchReturnsBlackoutShouldNotDeleteMessages() throws URISyntaxException, InterruptedException {
        Message message1 = mock(Message.class);
        Message message2 = mock(Message.class);
        when(this.messageProvider.getNext())
                .thenReturn(message1)
                .thenReturn(message2)
                .thenReturn(null);

        when(this.dispatcher.sendMessage(this.forwardService, message1)).thenReturn(Result.BLACKOUT);

        this.forwardService.onHandleIntent(new Intent());
        InOrder inorder = inOrder(this.dispatcher, this.messageProvider);

        inorder.verify(this.dispatcher).sendMessage(this.forwardService, message1);
        inorder.verify(this.messageProvider, never()).delete(message1);
        inorder.verify(this.dispatcher, never()).sendMessage(this.forwardService, message2);
        inorder.verify(this.messageProvider, never()).delete(message2);

        verify(this.periodicBackoutCheck, never()).record("ForwardService_Backout_Temporary_Error");
    }

    @Test
    public void whenTwoMessagesAndFirstDispatchReturnsPermanentErrorShouldDeleteMessages() throws URISyntaxException, InterruptedException {
        Message message1 = mock(Message.class);
        Message message2 = mock(Message.class);
        when(this.messageProvider.getNext())
            .thenReturn(message1)
            .thenReturn(message2)
            .thenReturn(null);

        when(this.dispatcher.sendMessage(this.forwardService, message1)).thenReturn(Result.PERMANENT_ERROR);
        when(this.dispatcher.sendMessage(this.forwardService, message2)).thenReturn(Result.OK);

        this.forwardService.onHandleIntent(new Intent());
        InOrder inorder = inOrder(this.dispatcher, this.messageProvider);

        inorder.verify(this.dispatcher).sendMessage(this.forwardService, message1);
        inorder.verify(this.messageProvider).delete(message1);
        inorder.verify(this.dispatcher).sendMessage(this.forwardService, message2);
        inorder.verify(this.messageProvider).delete(message2);

        verify(this.periodicBackoutCheck, never()).record("ForwardService_Backout_Temporary_Error");
    }

    @Test
    public void whenSendMessageShouldCheckForCanSendFirstNotGetNextMessageWhenCannotSend() throws URISyntaxException, InterruptedException {
        when(this.networkInfo.isConnectedOrConnecting()).thenReturn(false);

        this.forwardService.onHandleIntent(new Intent());
        verify(this.messageProvider, never()).getNext();
    }

    @Test
    public void whenCallingStartShouldStartService() {
        ForwardService.start(this.context);

        assertServiceStartedOrgRobolectric(ForwardService.class);
    }

    @Test
    public void whenMessageProviderCannotSendShouldDoNothing() throws URISyntaxException, InterruptedException {
        doReturn(false).when(this.messageProvider).canSend();

        this.forwardService.onHandleIntent(new Intent());

        verify(this.messageProvider, never()).getNext();
        verify(this.messageProvider, never()).delete(any(Message.class));
        verify(this.messageProvider).close();
        verify(this.periodicBackoutCheck, never()).record("ForwardService_Backout_Temporary_Error");
    }

    @Test
    public void whenPreviousMessagesFailedWithTemporaryErrorShouldNotTryToSendAnyMessageWithinFiveMinutes() throws URISyntaxException, InterruptedException {

        doReturn(false).when(this.periodicBackoutCheck).timeElapsed("ForwardService_Backout_Temporary_Error", 300000);
        this.forwardService.onHandleIntent(new Intent());

        verify(this.messageProvider, never()).getNext();
        verify(this.messageProvider, never()).delete(any(Message.class));
        verify(this.messageProvider).close();
        verify(this.periodicBackoutCheck, never()).record("ForwardService_Backout_Temporary_Error");
    }

    @Test
    public void whenCallingStartShouldAcquirePowerLock() {
        ForwardService.start(Robolectric.application.getApplicationContext());
        Intent intent = assertServiceStartedOrgRobolectric(ForwardService.class);
        int lockId = intent.getIntExtra("android.support.content.wakelockid", -1);
        assertTrue(lockId != -1);
    }

}
