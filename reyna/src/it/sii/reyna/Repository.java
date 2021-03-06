package it.sii.reyna;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import android.util.Log;
import it.sii.reyna.system.Header;
import it.sii.reyna.system.Message;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

public class Repository extends SQLiteOpenHelper {

    private static final String DATABASE_NAME = "reyna.db";

    private static final int DATABASE_VERSION = 3;

    private static final String TAG = "Repository";

    private static final Lock lock = new ReentrantLock();

    private static final int SIZE_DIFFERENCE_TO_START_CLEANING = 307200; //300Kb in bytes

    public Repository(Context context) {
        super(context, DATABASE_NAME, null, DATABASE_VERSION);
    }

    @Override
    public void onCreate(SQLiteDatabase db) {
        Log.v(TAG, "onCreate");
        db.execSQL("CREATE TABLE Message (" +
                   "  id INTEGER PRIMARY KEY AUTOINCREMENT, " +
                   "  url TEXT, " +
                   "  body TEXT, " +
                   "  username TEXT DEFAULT NULL, " +
                   "  password TEXT DEFAULT NULL, " +
                   "  tries_left DEFAULT 100);");
        db.execSQL("CREATE TABLE Header (id INTEGER PRIMARY KEY AUTOINCREMENT, messageid INTEGER, key TEXT, value TEXT, FOREIGN KEY(messageid) REFERENCES message(id));");
    }

    @Override
    protected void finalize() throws Throwable {
        this.close();
        super.finalize();
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        Log.v(TAG, "onUpgrade");
        db.execSQL("DROP TABLE Message");
        db.execSQL("DROP TABLE Header");

        onCreate(db);
    }

    public void insert(Message message) {
        Log.v(TAG, "insert");

        if (message == null) {
            Log.v(TAG, "insert, null message");
            return;
        }

        lock.lock();
        SQLiteDatabase db = this.getWritableDatabase();
        try {
            this.insertMessage(db, message);
        } finally {
            db.close();
            lock.unlock();
        }
    }

    public void insert(Message message, long dbSizeLimit) {
        Log.v(TAG, "insert with limit");

        if (message == null) {
            Log.v(TAG, "insert, null message");
            return;
        }

        lock.lock();
        SQLiteDatabase db = this.getWritableDatabase();
        try {
            long dbSize = this.getDbSize(db);
            Log.v(TAG, String.format("insert with limit. dbSize: %d, dbSizeLimit: %d", dbSize, dbSizeLimit));
            if (this.dbSizeApproachesLimit(dbSize, dbSizeLimit)) {
                Log.v(TAG, "insert with limit, dbSizeApproachesLimit");
                this.clearOldRecords(db, message);
            }

            this.insertMessage(db, message);
        } finally {
            db.close();
            lock.unlock();
        }
    }

    public Message getNext() throws URISyntaxException {
        Log.v(TAG, "getNext");
        return getNextMessageAfter(null);
    }

    public Message getNextMessageAfter(Long messageId) throws URISyntaxException {
        Log.v(TAG, "getNextMessageAfter");
        Cursor messageCursor = null;
        Cursor headersCursor = null;
        SQLiteDatabase db = this.getReadableDatabase();
        try {
            String selection = null;
            if (messageId != null) {
                selection = "id > " + messageId;
            }

            messageCursor = db.query("Message", new String[]{"id", "url",
                    "body", "username", "password", "tries_left" }, selection, null, null, null, "id", "1");
            if (!messageCursor.moveToFirst())
                return null;

            long id = messageCursor.getLong(0);
            String url = messageCursor.getString(1);
            String body = messageCursor.getString(2);
            String username = messageCursor.getString(3);
            String password = messageCursor.getString(4);
            Integer triesLeft = messageCursor.getInt(5);

            headersCursor = db.query("Header", new String[]{"id", "key",
                            "value"}, "messageid = " + id, null, null, null,
                    null);

            ArrayList<Header> headers = new ArrayList<Header>();
            while (headersCursor.moveToNext()) {
                headers.add(new Header(headersCursor.getLong(0), headersCursor
                        .getString(1), headersCursor.getString(2)));
            }

            Message message = new Message(messageId, new URI(url), body, username, password, headers);
            message.setNumberOfTries(triesLeft);
            return message;
        }
        finally {
            if (messageCursor != null)
                messageCursor.close();
            if (headersCursor != null)
                headersCursor.close();
            db.close();
        }
    }

    public void shrinkDb(long limit) {
        Log.v(TAG, "shrinkDb");
        lock.lock();

        SQLiteDatabase db = this.getWritableDatabase();
        try {
            limit -= SIZE_DIFFERENCE_TO_START_CLEANING;
            long dbSize = this.getDbSize(db);

            Log.v(TAG, "shrinkDb, dbSize: " + dbSize);
            Log.v(TAG, "shrinkDb, limit: " + limit);
            if (dbSize <= limit) {
                Log.v(TAG, "shrinkDb, dbSize <= limit, no SHRINK");
                return;
            }

            do {
                Log.v(TAG, String.format("shrinkDb, dbSize > limit, SHRINK needed, dbSize: %d, limit: %d", dbSize, limit));
                this.shrink(db, limit, dbSize);
                dbSize = this.getDbSize(db);
            }
            while (dbSize > limit);

            Log.v(TAG, String.format("shrinkDb, dbSize: %d, limit: %d", dbSize, limit));
            this.vacuum(db);
        } finally {
            db.close();
            lock.unlock();
        }

    }

    public void delete(Message message) {
        Log.v(TAG, "delete");
        if (message == null)
            return;
        if (message.getId() == null)
            return;

        SQLiteDatabase db = this.getWritableDatabase();
        try {
            if (!this.doesMessageExist(db, message))
                return;

            this.deleteExistingMessage(db, message.getId());
        } finally {
            db.close();
        }
    }

    public void deleteMessagesFrom(long messageId) {
        Log.v(TAG, "deleteMessagesFrom");

        SQLiteDatabase db = this.getWritableDatabase();
        db.beginTransaction();
        try {
            String[] args = new String[]{String.valueOf(messageId)};
            db.delete("Header", "messageid <= ?", args);
            db.delete("Message", "id <= ?", args);
            db.setTransactionSuccessful();
        } finally {
            db.endTransaction();
            db.close();
        }
    }

    public long getAvailableMessagesCount() {
        Log.v(TAG, "getAvailableMessagesCount");

        SQLiteDatabase db = this.getReadableDatabase();
        long numberOfMessages = this.getNumberOfMessages(db);
        db.close();
        return numberOfMessages;
    }

    private void insertMessage(SQLiteDatabase db, Message message) {
        Log.v(TAG, "insertMessage");
        try {
            db.beginTransaction();
            ContentValues values = new ContentValues();
            values.put("url", message.getUrl());
            values.put("body", message.getBody());
            values.put("username", message.getUsername());
            values.put("password", message.getPassword());
            values.put("tries_left", message.getNumberOfTries());

            long messageId = db.insert("Message", null, values);
            this.addHeaders(db, messageId, message.getHeaders());
            db.setTransactionSuccessful();

            Log.v("reyna", "Repository: inserted message " + messageId);
        } finally {
            if (db != null && db.inTransaction()) {
                db.endTransaction();
            }
        }
    }

    private void clearOldRecords(SQLiteDatabase db, Message message) {
        Log.v(TAG, "clearOldRecords");
        Long oldestMessageId = findOldestMessageIdWithType(db, message.getUrl());

        Log.v(TAG, "clearOldRecords, message.getUrl(): " + message.getUrl());
        Log.v(TAG, "clearOldRecords, oldestMessageId: " + oldestMessageId);
        if (oldestMessageId == null) {
            return;
        }

        this.deleteExistingMessage(db, oldestMessageId);
    }

    private Long findOldestMessageIdWithType(SQLiteDatabase db, String type) {
        Log.v(TAG, "findOldestMessageIdWithType");
        Cursor cursor = null;
        try {
            cursor = db.query("Message", new String[]{"min(id)"}, "url=?", new String[]{type}, null, null, null);
            if (cursor.moveToNext()) {
                long result = cursor.getLong(0);
                Log.v(TAG, "findOldestMessageIdWithType, oldest messageid: " + result);
                return result;
            }

            Log.v(TAG, "findOldestMessageIdWithType, NO Message found");
            return null;
        } finally {
            if (cursor != null) {
                cursor.close();
            }
        }
    }

    private boolean dbSizeApproachesLimit(long dbSize, long limit) {
        Log.v(TAG, String.format("dbSizeApproachesLimit, dbSize: %d, limit %d, SIZE_DIFFERENCE_TO_START_CLEANING: %d", dbSize, limit, SIZE_DIFFERENCE_TO_START_CLEANING));
        boolean result = (limit > dbSize) && (limit - dbSize) < SIZE_DIFFERENCE_TO_START_CLEANING;
        Log.v(TAG, "dbSizeApproachesLimit, result: " + result);
        return result;
    }


    private long getNumberOfMessages(SQLiteDatabase db) {
        Log.v(TAG, "getNumberOfMessages");

        Cursor cursor = null;
        try {
            cursor = db.rawQuery("select count(*) from Message", null);
            if (cursor.moveToFirst()) {
                long numberOfMessages = cursor.getLong(0);
                Log.v(TAG, "getNumberOfMessages, numberOfMessages: " + numberOfMessages);
                return numberOfMessages;
            }

            return 0;
        } finally {
            if (cursor != null) {
                cursor.close();
            }
        }
    }

    private void deleteExistingMessage(SQLiteDatabase db, long messageId) {
        Log.v(TAG, "deleteExistingMessage");
        db.beginTransaction();
        try {
            String[] args = new String[]{String.valueOf(messageId)};
            db.delete("Header", "messageid = ?", args);
            db.delete("Message", "id = ?", args);
            db.setTransactionSuccessful();
        } finally {
            db.endTransaction();
        }
    }

    private boolean doesMessageExist(SQLiteDatabase db, Message message) {
        Log.v(TAG, "doesMessageExist");

        Cursor cursor = null;
        try {
            cursor = db.query("Message", new String[]{"id"}, "id = ?",
                    new String[]{message.getId().toString()}, null, null,
                    null);
            return cursor.moveToFirst();
        } finally {
            if (cursor != null)
                cursor.close();
        }
    }

    private void addHeaders(SQLiteDatabase db, long messageid, List<Header> headers) {
        Log.v(TAG, "addHeaders");

        for (Header header : headers) {
            ContentValues headerValues = new ContentValues();
            headerValues.put("messageid", messageid);
            headerValues.put("key", header.getKey());
            headerValues.put("value", header.getValue());

            db.insert("Header", null, headerValues);
        }
    }

    private void shrink(SQLiteDatabase db, long limit, long dbSize) {
        double limitPercentage = 1 - (double) limit / dbSize;
        long numberOfMessages = this.getNumberOfMessages(db);
        long numberOfMessagesToRemove = Math.round(numberOfMessages * limitPercentage);
        numberOfMessagesToRemove = numberOfMessagesToRemove == 0 ? 1 : numberOfMessagesToRemove;

        try {
            db.beginTransaction();

            long thresholdId = this.getMessageIdToWhichShrink(db, numberOfMessagesToRemove);

            db.execSQL("delete from Message where id < " + thresholdId);
            db.execSQL("delete from Header where messageid < " + thresholdId);

            db.setTransactionSuccessful();
        } finally {
            if (db.inTransaction()) {
                db.endTransaction();
            }
        }
    }

    private void vacuum(SQLiteDatabase db) {
        Log.v(TAG, "vacuum");
        db.execSQL("vacuum");
    }

    private long getMessageIdToWhichShrink(SQLiteDatabase db, long numberOfMessagesToRemove) {
        Log.v(TAG, "getMessageIdToWhichShrink");

        Cursor cursor = null;
        try {
            cursor = db.rawQuery("select id from Message limit 1 offset " + numberOfMessagesToRemove, null);
            if (cursor.moveToFirst()) {
                long id = cursor.getLong(0);
                Log.v(TAG, "getMessageIdToWhichShrink, id: " + id);
                return id;
            }

            return 0;
        } finally {
            if (cursor != null) {
                cursor.close();
            }
        }
    }

    private long getDbSize(SQLiteDatabase db) {
        Log.v(TAG, "getDbSize");

        Cursor cursor = null;

        try {
            cursor = db.rawQuery("pragma page_count", null);

            if (cursor.moveToFirst()) {
                long pageCount = cursor.getLong(0);
                return pageCount * db.getPageSize();
            }

            return 0;
        } finally {
            if (cursor != null) {
                cursor.close();
            }
        }
    }

    public void decrementMessageTries(Message message) {
        Log.v(TAG, "decrementTries");
        SQLiteDatabase db = this.getWritableDatabase();
        try {
            db.beginTransaction();
            int numberOfTriesLeft = message.getNumberOfTries() - 1;
            message.setNumberOfTries(numberOfTriesLeft);
            ContentValues values = new ContentValues();
            values.put("tries_left", numberOfTriesLeft);

            long messageId = db.update("Message", values, "id = ?", new String[] { message.getId().toString() });
            db.setTransactionSuccessful();

            Log.v("reyna", "Repository: decremented tries for message " + messageId);
        }
        finally {
            if (db != null) {
                if (db.inTransaction())
                    db.endTransaction();
                db.close();
            }
        }
    }
}