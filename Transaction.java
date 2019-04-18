package com.tablet.autosms_2.library;

//
// Source code recreated from a .class file by IntelliJ IDEA
// (powered by Fernflower decompiler)
//


import android.app.PendingIntent;
import android.app.PendingIntent.CanceledException;
import android.content.BroadcastReceiver;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.net.Uri;
import android.net.Uri.Builder;
import android.os.Bundle;
import android.os.Looper;
import android.os.Parcelable;
import android.os.Build.VERSION;
import android.telephony.SmsManager;
import android.telephony.SmsMessage;
import android.text.TextUtils;

import com.android.mms.MmsConfig;
import com.android.mms.dom.smil.parser.SmilXmlSerializer;
import com.android.mms.service_alt.MmsNetworkManager;
import com.android.mms.service_alt.MmsRequestManager;
import com.android.mms.service_alt.SendRequest;
import com.android.mms.transaction.MmsMessageSender;
import com.android.mms.util.DownloadManager;
import com.android.mms.util.RateController;
import com.google.android.mms.InvalidHeaderValueException;
import com.google.android.mms.MMSPart;
import com.google.android.mms.MmsException;
import com.google.android.mms.pdu_alt.EncodedStringValue;
import com.google.android.mms.pdu_alt.PduBody;
import com.google.android.mms.pdu_alt.PduComposer;
import com.google.android.mms.pdu_alt.PduPart;
import com.google.android.mms.pdu_alt.PduPersister;
import com.google.android.mms.pdu_alt.SendReq;
import com.google.android.mms.smil.SmilHelper;
import com.klinker.android.logger.Log;
import com.klinker.android.send_message.BroadcastUtils;
import com.klinker.android.send_message.Message;
import com.klinker.android.send_message.Message.Part;
import com.klinker.android.send_message.Settings;
import com.klinker.android.send_message.SmsManagerFactory;
import com.klinker.android.send_message.StripAccents;
import com.klinker.android.send_message.Utils;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Random;
import java.util.Set;

public class Transaction {
    private static final String TAG = "Transaction";
    public static Settings settings;
    private Context context;
    private Intent explicitSentSmsReceiver;
    private Intent explicitSentMmsReceiver;
    private Intent explicitDeliveredSmsReceiver;
    private boolean saveMessage;
    public String SMS_SENT;
    public String SMS_DELIVERED;
    public static final String SENT_SMS_BUNDLE = "com.klinker.android.send_message.SENT_SMS_BUNDLE";
    public static final String DELIVERED_SMS_BUNDLE = "com.klinker.android.send_message.DELIVERED_SMS_BUNDLE";
    public static String NOTIFY_SMS_FAILURE = ".NOTIFY_SMS_FAILURE";
    public static final String MMS_ERROR = "com.klinker.android.send_message.MMS_ERROR";
    public static final String REFRESH = "com.klinker.android.send_message.REFRESH";
    public static final String MMS_PROGRESS = "com.klinker.android.send_message.MMS_PROGRESS";
    public static final String NOTIFY_OF_DELIVERY = "com.klinker.android.send_message.NOTIFY_DELIVERY";
    public static final String NOTIFY_OF_MMS = "com.klinker.android.messaging.NEW_MMS_DOWNLOADED";
    public static final long NO_THREAD_ID = 0L;
    public static final long DEFAULT_EXPIRY_TIME = 604800L;
    public static final int DEFAULT_PRIORITY = 129;

    public Transaction(Context context) {
        this(context, new Settings());
    }

    public Transaction(Context context, Settings settings) {
        this.saveMessage = true;
        this.SMS_SENT = ".SMS_SENT";
        this.SMS_DELIVERED = ".SMS_DELIVERED";
        this.settings = settings;
        this.context = context;
        this.SMS_SENT = context.getPackageName() + this.SMS_SENT;
        this.SMS_DELIVERED = context.getPackageName() + this.SMS_DELIVERED;
        if (NOTIFY_SMS_FAILURE.equals(".NOTIFY_SMS_FAILURE")) {
            NOTIFY_SMS_FAILURE = context.getPackageName() + NOTIFY_SMS_FAILURE;
        }

    }

    public void sendNewMessage(Message message, long threadId, Parcelable sentMessageParcelable, Parcelable deliveredParcelable) {
        this.saveMessage = message.getSave();
        if (this.checkMMS(message)) {
            try {
                Looper.prepare();
            } catch (Exception var7) {

            }

            RateController.init(this.context);
            DownloadManager.init(this.context);
            this.sendMmsMessage(message.getText(), message.getAddresses(), message.getImages(), message.getImageNames(), message.getParts(), message.getSubject());
        } else {
            this.sendSmsMessage(message.getText(), message.getAddresses(), threadId, message.getDelay(), sentMessageParcelable, deliveredParcelable);
        }

    }

    public void sendNewMessage(Message message, long threadId) {
        this.sendNewMessage(message, threadId, new Bundle(), new Bundle());
    }

    public Transaction setExplicitBroadcastForSentSms(Intent intent) {
        this.explicitSentSmsReceiver = intent;
        return this;
    }

    public Transaction setExplicitBroadcastForSentMms(Intent intent) {
        this.explicitSentMmsReceiver = intent;
        return this;
    }

    public Transaction setExplicitBroadcastForDeliveredSms(Intent intent) {
        this.explicitDeliveredSmsReceiver = intent;
        return this;
    }

    private void sendSmsMessage(String text, String[] addresses, long threadId, int delay, Parcelable sentMessageParcelable, Parcelable deliveredParcelable) {
        Log.v("send_transaction", "message text: " + text);
        Uri messageUri = null;
        int messageId = 0;
        if (this.saveMessage) {
            Log.v("send_transaction", "saving message");
            if (!settings.getSignature().equals("")) {
                text = text + "\n" + settings.getSignature();
            }

            for(int i = 0; i < addresses.length; ++i) {
                Calendar cal = Calendar.getInstance();
                ContentValues values = new ContentValues();
                values.put("address", addresses[i]);
                values.put("body", settings.getStripUnicode() ? StripAccents.stripAccents(text) : text);
                values.put("date", cal.getTimeInMillis() + "");
                values.put("read", 1);
                values.put("type", 4);
                if (threadId == 0L || addresses.length > 1) {
                    threadId = Utils.getOrCreateThreadId(this.context, addresses[i]);
                }

                Log.v("send_transaction", "saving message with thread id: " + threadId);
                values.put("thread_id", threadId);
                messageUri = this.context.getContentResolver().insert(Uri.parse("content://sms/"), values);
                Log.v("send_transaction", "inserted to uri: " + messageUri);
                Cursor query = this.context.getContentResolver().query(messageUri, new String[]{"_id"}, (String)null, (String[])null, (String)null);
                if (query != null && query.moveToFirst()) {
                    messageId = query.getInt(0);
                    query.close();
                }

                Log.v("send_transaction", "message id: " + messageId);
                Intent sentIntent;
                if (this.explicitSentSmsReceiver == null) {
                    sentIntent = new Intent(this.SMS_SENT);
                    BroadcastUtils.addClassName(this.context, sentIntent, this.SMS_SENT);
                } else {
                    sentIntent = this.explicitSentSmsReceiver;
                }

                sentIntent.putExtra("message_uri", messageUri == null ? "" : messageUri.toString());
                sentIntent.putExtra("com.klinker.android.send_message.SENT_SMS_BUNDLE", sentMessageParcelable);
                PendingIntent sentPI = PendingIntent.getBroadcast(this.context, messageId, sentIntent, PendingIntent.FLAG_ONE_SHOT);
                Intent deliveredIntent;
                if (this.explicitDeliveredSmsReceiver == null) {
                    deliveredIntent = new Intent(this.SMS_DELIVERED);
                    BroadcastUtils.addClassName(this.context, deliveredIntent, this.SMS_DELIVERED);
                } else {
                    deliveredIntent = this.explicitDeliveredSmsReceiver;
                }

                deliveredIntent.putExtra("message_uri", messageUri == null ? "" : messageUri.toString());
                deliveredIntent.putExtra("com.klinker.android.send_message.DELIVERED_SMS_BUNDLE", deliveredParcelable);
                PendingIntent deliveredPI = PendingIntent.getBroadcast(this.context, messageId, deliveredIntent, PendingIntent.FLAG_ONE_SHOT);
                ArrayList<PendingIntent> sPI = new ArrayList();
                ArrayList<PendingIntent> dPI = new ArrayList();
                String body = text;
                if (settings.getStripUnicode()) {
                    body = StripAccents.stripAccents(text);
                }

                if (!settings.getPreText().equals("")) {
                    body = settings.getPreText() + " " + body;
                }

                SmsManager smsManager = SmsManagerFactory.createSmsManager(settings);
                Log.v("send_transaction", "found sms manager");
                int j;
                if (settings.getSplit()) {
                    Log.v("send_transaction", "splitting message");
                    int[] splitData = SmsMessage.calculateLength(body, false);
                    j = (body.length() + splitData[2]) / splitData[0];
                    Log.v("send_transaction", "length: " + j);
                    boolean counter = false;
                    if (settings.getSplitCounter() && body.length() > j) {
                        counter = true;
                        j -= 6;
                    }

                    String[] textToSend = this.splitByLength(body, j, counter);

                    for(int p = 0; p < textToSend.length; ++p) {
                        ArrayList<String> parts = smsManager.divideMessage(textToSend[p]);

                        for(int k = 0; k < parts.size(); ++k) {
                            sPI.add(this.saveMessage ? sentPI : null);
                            dPI.add(settings.getDeliveryReports() && this.saveMessage ? deliveredPI : null);
                        }

                        Log.v("send_transaction", "sending split message");
                        this.sendDelayedSms(smsManager, addresses[i], parts, sPI, dPI, delay, messageUri);
                    }
                } else {
                    Log.v("send_transaction", "sending without splitting");
                    ArrayList<String> parts = smsManager.divideMessage(body);

                    for(j = 0; j < parts.size(); ++j) {
                        sPI.add(this.saveMessage ? sentPI : null);
                        dPI.add(settings.getDeliveryReports() && this.saveMessage ? deliveredPI : null);
                    }

                    if (Utils.isDefaultSmsApp(this.context)) {
                        try {
                            Log.v("send_transaction", "sent message");
                            this.sendDelayedSms(smsManager, addresses[i], parts, sPI, dPI, delay, messageUri);
                        } catch (Exception var30) {
                            Log.v("send_transaction", "error sending message");
                            Log.e("Transaction", "exception thrown", var30);

                            try {
//                                ((Activity)this.context).getWindow().getDecorView().findViewById(16908290).post(new Runnable() {
//                                    public void run() {
//                                        Toast.makeText(Transaction.this.context, "Message could not be sent", Toast.LENGTH_SHORT).show();
//                                    }
//                                });
                            } catch (Exception var29) {
                                ;
                            }
                        }
                    } else {
                        smsManager.sendMultipartTextMessage(addresses[i], (String)null, parts, sPI, dPI);
                    }
                }
            }
        }

    }

    private void sendDelayedSms(final SmsManager smsManager, final String address, final ArrayList<String> parts, final ArrayList<PendingIntent> sPI, final ArrayList<PendingIntent> dPI, final int delay, final Uri messageUri) {
        (new Thread(new Runnable() {
            public void run() {
                try {
                    Thread.sleep((long)delay);
                } catch (Exception var3) {
                    ;
                }

                if (Transaction.this.checkIfMessageExistsAfterDelay(messageUri)) {
                    Log.v("send_transaction", "message sent after delay");

                    try {
                        smsManager.sendMultipartTextMessage(address, (String)null, parts, sPI, dPI);
                    } catch (Exception var2) {
                        Log.e("Transaction", "exception thrown", var2);
                    }
                } else {
                    Log.v("send_transaction", "message not sent after delay, no longer exists");
                }

            }
        })).start();
    }

    private boolean checkIfMessageExistsAfterDelay(Uri messageUti) {
        Cursor query = this.context.getContentResolver().query(messageUti, new String[]{"_id"}, (String)null, (String[])null, (String)null);
        if (query != null && query.moveToFirst()) {
            query.close();
            return true;
        } else {
            return false;
        }
    }

    private void sendMmsMessage(String text, String[] addresses, Bitmap[] image, String[] imageNames, List<Part> parts, String subject) {
        String address = "";

        for(int i = 0; i < addresses.length; ++i) {
            address = address + addresses[i] + " ";
        }

        address = address.trim();
        ArrayList<MMSPart> data = new ArrayList();

        MMSPart part;
        for(int i = 0; i < image.length; ++i) {
            byte[] imageBytes = Message.bitmapToByteArray(image[i]);
            part = new MMSPart();
            part.MimeType = "image/jpeg";
            part.Name = imageNames != null ? imageNames[i] : "image_" + System.currentTimeMillis();
            part.Data = imageBytes;
            data.add(part);
        }

        Iterator var16;
        if (parts != null) {
            var16 = parts.iterator();

            while(var16.hasNext()) {
                Part p = (Part)var16.next();
                part = new MMSPart();
                if (p.getName() != null) {
                    part.Name = p.getName();
                } else {
                    part.Name = p.getContentType().split("/")[0];
                }

                part.MimeType = p.getContentType();
                part.Data = p.getMedia();
                data.add(part);
            }
        }

        if (text != null && !text.equals("")) {
            MMSPart mPart = new MMSPart();
            mPart.Name = "text";
            mPart.MimeType = "text/plain";
            mPart.Data = text.getBytes();
            data.add(mPart);
        }

        Transaction.MessageInfo info;
        if (VERSION.SDK_INT <= 19) {
            var16 = null;

            try {
                info = getBytes(this.context, this.saveMessage, address.split(" "), (MMSPart[])data.toArray(new MMSPart[data.size()]), subject);
                MmsMessageSender sender = new MmsMessageSender(this.context, info.location, (long)info.bytes.length);
                sender.sendMessage(info.token);
                IntentFilter filter = new IntentFilter();
                filter.addAction("com.android.mms.PROGRESS_STATUS");
                BroadcastReceiver receiver = new BroadcastReceiver() {
                    public void onReceive(Context context, Intent intent) {
                        int progress = intent.getIntExtra("progress", -3);
                        Log.v("sending_mms_library", "progress: " + progress);
                        Intent progressIntent = new Intent("com.klinker.android.send_message.MMS_PROGRESS");
                        progressIntent.putExtra("progress", progress);
                        BroadcastUtils.sendExplicitBroadcast(context, progressIntent, "com.klinker.android.send_message.MMS_PROGRESS");
                        if (progress == 100) {
                            BroadcastUtils.sendExplicitBroadcast(context, new Intent(), "com.klinker.android.send_message.REFRESH");

                            try {
                                context.unregisterReceiver(this);
                            } catch (Exception var6) {

                            }
                        } else if (progress == -2) {
                            Log.v("sending_mms_library", "sending aborted for some reason...");
                        }

                    }
                };
                this.context.registerReceiver(receiver, filter);
            } catch (Throwable var14) {
                Log.e("Transaction", "exception thrown", var14);
            }
        } else {
            Log.v("Transaction", "using lollipop method for sending sms");
            if (settings.getUseSystemSending()) {
                Log.v("Transaction", "using system method for sending");
                sendMmsThroughSystem(this.context, subject, data, addresses, this.explicitSentMmsReceiver);
            } else {
                try {
                    info = getBytes(this.context, this.saveMessage, address.split(" "), (MMSPart[])data.toArray(new MMSPart[data.size()]), subject);
                    MmsRequestManager requestManager = new MmsRequestManager(this.context, info.bytes);
                    SendRequest request = new SendRequest(requestManager, Utils.getDefaultSubscriptionId(), info.location, (String)null, (PendingIntent)null, (String)null, (Bundle)null);
                    MmsNetworkManager manager = new MmsNetworkManager(this.context, Utils.getDefaultSubscriptionId());
                    request.execute(this.context, manager);
                } catch (Exception var13) {
                    Log.e("Transaction", "error sending mms", var13);
                }
            }
        }

    }

    public static Transaction.MessageInfo getBytes(Context context, boolean saveMessage, String[] recipients, MMSPart[] parts, String subject) throws MmsException {
        SendReq sendRequest = new SendReq();

        for(int i = 0; i < recipients.length; ++i) {
            EncodedStringValue[] phoneNumbers = EncodedStringValue.extract(recipients[i]);
            if (phoneNumbers != null && phoneNumbers.length > 0) {
                sendRequest.addTo(phoneNumbers[0]);
            }
        }

        if (subject != null) {
            sendRequest.setSubject(new EncodedStringValue(subject));
        }

        sendRequest.setDate(Calendar.getInstance().getTimeInMillis() / 1000L);

        try {
            sendRequest.setFrom(new EncodedStringValue(Utils.getMyPhoneNumber(context)));
        } catch (Exception var18) {
            Log.e("Transaction", "error getting from address", var18);
        }

        PduBody pduBody = new PduBody();
        long size = 0L;
        if (parts != null) {
            for(int i = 0; i < parts.length; ++i) {
                MMSPart part = parts[i];
                if (part != null) {
                    try {
                        PduPart partPdu = new PduPart();
                        partPdu.setName(part.Name.getBytes());
                        partPdu.setContentType(part.MimeType.getBytes());
                        if (part.MimeType.startsWith("text")) {
                            partPdu.setCharset(106);
                        }

                        partPdu.setContentLocation(part.Name.getBytes());
                        int index = part.Name.lastIndexOf(".");
                        String contentId = index == -1 ? part.Name : part.Name.substring(0, index);
                        partPdu.setContentId(contentId.getBytes());
                        partPdu.setData(part.Data);
                        pduBody.addPart(partPdu);
                        size += (long)(2 * part.Name.getBytes().length + part.MimeType.getBytes().length + part.Data.length + contentId.getBytes().length);
                    } catch (Exception var17) {
                        ;
                    }
                }
            }
        }

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        SmilXmlSerializer.serialize(SmilHelper.createSmilDocument(pduBody), out);
        PduPart smilPart = new PduPart();
        smilPart.setContentId("smil".getBytes());
        smilPart.setContentLocation("smil.xml".getBytes());
        smilPart.setContentType("application/smil".getBytes());
        smilPart.setData(out.toByteArray());
        pduBody.addPart(0, smilPart);
        sendRequest.setBody(pduBody);
        Log.v("Transaction", "setting message size to " + size + " bytes");
        sendRequest.setMessageSize(size);
        sendRequest.setPriority(129);
        sendRequest.setDeliveryReport(129);
        sendRequest.setExpiry(604800000L);
        sendRequest.setMessageClass("personal".getBytes());
        sendRequest.setReadReport(129);
        PduComposer composer = new PduComposer(context, sendRequest);

        byte[] bytesToSend;
        try {
            bytesToSend = composer.make();
        } catch (OutOfMemoryError var16) {
            throw new MmsException("Out of memory!");
        }

        Transaction.MessageInfo info = new Transaction.MessageInfo();
        info.bytes = bytesToSend;
        if (saveMessage) {
            try {
                PduPersister persister = PduPersister.getPduPersister(context);
                //info.location = persister.persist(sendRequest, Uri.parse("content://mms/outbox"), true, settings.getGroup(), (HashMap)null);
            } catch (Exception var15) {
                Log.v("sending_mms_library", "error saving mms message");
                Log.e("Transaction", "exception thrown", var15);
                insert(context, recipients, parts, subject);
            }
        }

        try {
            Cursor query = context.getContentResolver().query(info.location, new String[]{"thread_id"}, (String)null, (String[])null, (String)null);
            if (query != null && query.moveToFirst()) {
                info.token = query.getLong(query.getColumnIndex("thread_id"));
                query.close();
            } else {
                info.token = 4444L;
            }
        } catch (Exception var19) {
            Log.e("Transaction", "exception thrown", var19);
            info.token = 4444L;
        }

        return info;
    }

    private static void sendMmsThroughSystem(Context context, String subject, List<MMSPart> parts, String[] addresses, Intent explicitSentMmsReceiver) {
        try {
            String fileName = "send." + String.valueOf(Math.abs((new Random()).nextLong())) + ".dat";
            File mSendFile = new File(context.getCacheDir(), fileName);
            SendReq sendReq = buildPdu(context, addresses, subject, parts);
            PduPersister persister = PduPersister.getPduPersister(context);
            //Uri messageUri = persister.persist(sendReq, Uri.parse("content://mms/outbox"), true, settings.getGroup(), (HashMap)null);
            Intent intent;
            if (explicitSentMmsReceiver == null) {
                intent = new Intent("com.klinker.android.messaging.MMS_SENT");
                BroadcastUtils.addClassName(context, intent, "com.klinker.android.messaging.MMS_SENT");
            } else {
                intent = explicitSentMmsReceiver;
            }

            //intent.putExtra("content_uri", messageUri.toString());
            intent.putExtra("file_path", mSendFile.getPath());
            PendingIntent pendingIntent = PendingIntent.getBroadcast(context, 0, intent, PendingIntent.FLAG_ONE_SHOT);
            Uri writerUri = (new Builder()).authority(context.getPackageName() + ".MmsFileProvider").path(fileName).scheme("content").build();
            FileOutputStream writer = null;
            Uri contentUri = null;

            try {
                writer = new FileOutputStream(mSendFile);
                writer.write((new PduComposer(context, sendReq)).make());
                contentUri = writerUri;
            } catch (IOException var27) {
                Log.e("Transaction", "Error writing send file", var27);
            } finally {
                if (writer != null) {
                    try {
                        writer.close();
                    } catch (IOException var25) {
                        ;
                    }
                }

            }

            Bundle configOverrides = new Bundle();
            configOverrides.putBoolean("enableGroupMms", settings.getGroup());
            String httpParams = MmsConfig.getHttpParams();
            if (!TextUtils.isEmpty(httpParams)) {
                configOverrides.putString("httpParams", httpParams);
            }

            configOverrides.putInt("maxMessageSize", MmsConfig.getMaxMessageSize());
            if (contentUri != null) {
                SmsManagerFactory.createSmsManager(settings).sendMultimediaMessage(context, contentUri, (String)null, configOverrides, pendingIntent);
            } else {
                Log.e("Transaction", "Error writing sending Mms");

                try {
                    pendingIntent.send(5);
                } catch (CanceledException var26) {
                    Log.e("Transaction", "Mms pending intent cancelled?", var26);
                }
            }
        } catch (Exception var29) {
            Log.e("Transaction", "error using system sending method", var29);
        }

    }

    private static SendReq buildPdu(Context context, String[] recipients, String subject, List<MMSPart> parts) {
        SendReq req = new SendReq();
        String lineNumber = Utils.getMyPhoneNumber(context);
        if (!TextUtils.isEmpty(lineNumber)) {
            req.setFrom(new EncodedStringValue(lineNumber));
        }

        String[] var6 = recipients;
        int size = recipients.length;

        int i;
        for(i = 0; i < size; ++i) {
            String recipient = var6[i];
            req.addTo(new EncodedStringValue(recipient));
        }

        if (!TextUtils.isEmpty(subject)) {
            req.setSubject(new EncodedStringValue(subject));
        }

        req.setDate(System.currentTimeMillis() / 1000L);
        PduBody body = new PduBody();
        size = 0;

        for(i = 0; i < parts.size(); ++i) {
            MMSPart part = (MMSPart)parts.get(i);
            size += addTextPart(body, part, i);
        }

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        SmilXmlSerializer.serialize(SmilHelper.createSmilDocument(body), out);
        PduPart smilPart = new PduPart();
        smilPart.setContentId("smil".getBytes());
        smilPart.setContentLocation("smil.xml".getBytes());
        smilPart.setContentType("application/smil".getBytes());
        smilPart.setData(out.toByteArray());
        body.addPart(0, smilPart);
        req.setBody(body);
        req.setMessageSize((long)size);
        req.setMessageClass("personal".getBytes());
        req.setExpiry(604800L);

        try {
            req.setPriority(129);
            req.setDeliveryReport(129);
            req.setReadReport(129);
        } catch (InvalidHeaderValueException var11) {
            ;
        }

        return req;
    }

    private static int addTextPart(PduBody pb, MMSPart p, int id) {
        String filename = p.Name;
        PduPart part = new PduPart();
        if (p.MimeType.startsWith("text")) {
            part.setCharset(106);
        }

        part.setContentType(p.MimeType.getBytes());
        part.setContentLocation(filename.getBytes());
        int index = filename.lastIndexOf(".");
        String contentId = index == -1 ? filename : filename.substring(0, index);
        part.setContentId(contentId.getBytes());
        part.setData(p.Data);
        pb.addPart(part);
        return part.getData().length;
    }

    private String[] splitByLength(String s, int chunkSize, boolean counter) {
        int arraySize = (int)Math.ceil((double)s.length() / (double)chunkSize);
        String[] returnArray = new String[arraySize];
        int index = 0;

        int i;
        for(i = 0; i < s.length(); i += chunkSize) {
            if (s.length() - i < chunkSize) {
                returnArray[index++] = s.substring(i);
            } else {
                returnArray[index++] = s.substring(i, i + chunkSize);
            }
        }

        if (counter && returnArray.length > 1) {
            for(i = 0; i < returnArray.length; ++i) {
                returnArray[i] = "(" + (i + 1) + "/" + returnArray.length + ") " + returnArray[i];
            }
        }

        return returnArray;
    }

    private static Uri insert(Context context, String[] to, MMSPart[] parts, String subject) {
        try {
            Uri destUri = Uri.parse("content://mms");
            Set<String> recipients = new HashSet();
            recipients.addAll(Arrays.asList(to));
            long thread_id = Utils.getOrCreateThreadId(context, recipients);
            ContentValues dummyValues = new ContentValues();
            dummyValues.put("thread_id", thread_id);
            dummyValues.put("body", " ");
            Uri dummySms = context.getContentResolver().insert(Uri.parse("content://sms/sent"), dummyValues);
            long now = System.currentTimeMillis();
            ContentValues mmsValues = new ContentValues();
            mmsValues.put("thread_id", thread_id);
            mmsValues.put("date", now / 1000L);
            mmsValues.put("msg_box", 4);
            mmsValues.put("read", true);
            mmsValues.put("sub", subject != null ? subject : "");
            mmsValues.put("sub_cs", 106);
            mmsValues.put("ct_t", "application/vnd.wap.multipart.related");
            long imageBytes = 0L;
            MMSPart[] var15 = parts;
            int var16 = parts.length;

            for(int var17 = 0; var17 < var16; ++var17) {
                MMSPart part = var15[var17];
                imageBytes += (long)part.Data.length;
            }

            mmsValues.put("exp", imageBytes);
            mmsValues.put("m_cls", "personal");
            mmsValues.put("m_type", 128);
            mmsValues.put("v", 19);
            mmsValues.put("pri", 129);
            mmsValues.put("tr_id", "T" + Long.toHexString(now));
            mmsValues.put("resp_st", 128);
            Uri res = context.getContentResolver().insert(destUri, mmsValues);
            String messageId = res.getLastPathSegment().trim();
            MMSPart[] var24 = parts;
            int var26 = parts.length;

            int var19;
            for(var19 = 0; var19 < var26; ++var19) {
                MMSPart part = var24[var19];
                if (part.MimeType.startsWith("image")) {
                    createPartImage(context, messageId, part.Data, part.MimeType);
                } else if (part.MimeType.startsWith("text")) {
                    createPartText(context, messageId, new String(part.Data, "UTF-8"));
                }
            }

            String[] var25 = to;
            var26 = to.length;

            for(var19 = 0; var19 < var26; ++var19) {
                String addr = var25[var19];
                createAddr(context, messageId, addr);
            }

            context.getContentResolver().delete(dummySms, (String)null, (String[])null);
            return res;
        } catch (Exception var21) {
            Log.v("sending_mms_library", "still an error saving... :(");
            Log.e("Transaction", "exception thrown", var21);
            return null;
        }
    }

    private static Uri createPartImage(Context context, String id, byte[] imageBytes, String mimeType) throws Exception {
        ContentValues mmsPartValue = new ContentValues();
        mmsPartValue.put("mid", id);
        mmsPartValue.put("ct", mimeType);
        mmsPartValue.put("cid", "<" + System.currentTimeMillis() + ">");
        Uri partUri = Uri.parse("content://mms/" + id + "/part");
        Uri res = context.getContentResolver().insert(partUri, mmsPartValue);
        OutputStream os = context.getContentResolver().openOutputStream(res);
        ByteArrayInputStream is = new ByteArrayInputStream(imageBytes);
        byte[] buffer = new byte[256];
        boolean var10 = false;

        int len;
        while((len = is.read(buffer)) != -1) {
            os.write(buffer, 0, len);
        }

        os.close();
        is.close();
        return res;
    }

    private static Uri createPartText(Context context, String id, String text) throws Exception {
        ContentValues mmsPartValue = new ContentValues();
        mmsPartValue.put("mid", id);
        mmsPartValue.put("ct", "text/plain");
        mmsPartValue.put("cid", "<" + System.currentTimeMillis() + ">");
        mmsPartValue.put("text", text);
        Uri partUri = Uri.parse("content://mms/" + id + "/part");
        Uri res = context.getContentResolver().insert(partUri, mmsPartValue);
        return res;
    }

    private static Uri createAddr(Context context, String id, String addr) throws Exception {
        ContentValues addrValues = new ContentValues();
        addrValues.put("address", addr);
        addrValues.put("charset", "106");
        addrValues.put("type", 151);
        Uri addrUri = Uri.parse("content://mms/" + id + "/addr");
        Uri res = context.getContentResolver().insert(addrUri, addrValues);
        return res;
    }

    public boolean checkMMS(Message message) {
        return message.getImages().length != 0 || message.getParts().size() != 0 || settings.getSendLongAsMms() && Utils.getNumPages(settings, message.getText()) > settings.getSendLongAsMmsAfter() || message.getAddresses().length > 1 && settings.getGroup() || message.getSubject() != null;
    }

    public static class MessageInfo {
        public long token;
        public Uri location;
        public byte[] bytes;

        public MessageInfo() {
        }
    }
}

