package io.invertase.firebase.messaging;

import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.util.Log;

import androidx.core.app.JobIntentService;
import androidx.localbroadcastmanager.content.LocalBroadcastManager;

import android.os.Bundle;

import com.facebook.react.HeadlessJsTaskService;
import com.google.firebase.messaging.RemoteMessage;

import java.util.HashMap;

import com.quantumgraph.sdk.NotificationJobIntentService;
import com.quantumgraph.sdk.QG;

import java.util.Map;

import io.invertase.firebase.app.ReactNativeFirebaseApp;
import io.invertase.firebase.common.ReactNativeFirebaseEventEmitter;
import io.invertase.firebase.common.SharedUtils;

public class ReactNativeFirebaseMessagingReceiver extends BroadcastReceiver {
  private static final String TAG = "RNFirebaseMsgReceiver";
  static HashMap<String, RemoteMessage> notifications = new HashMap<>();


  @Override
  public void onReceive(Context context, Intent intent) {
    Log.d(TAG, "broadcast received for message");
    if (ReactNativeFirebaseApp.getApplicationContext() == null) {
      ReactNativeFirebaseApp.setApplicationContext(context.getApplicationContext());
    }
    RemoteMessage remoteMessage = new RemoteMessage(intent.getExtras());
    ReactNativeFirebaseEventEmitter emitter = ReactNativeFirebaseEventEmitter.getSharedInstance();


    String from = remoteMessage.getFrom();
    Map data = remoteMessage.getData();

    if (data.containsKey("message") && QG.isQGMessage(data.get("message").toString())) {
      Bundle qgData = new Bundle();
      qgData.putString("message", data.get("message").toString());
      Context context = getApplicationContext();
      if (from == null || context == null) {
        return;
      }
      Intent intent = new Intent(context, NotificationJobIntentService.class);
      intent.setAction("QG");
      intent.putExtras(qgData);
      JobIntentService.enqueueWork(context, NotificationJobIntentService.class, 1000, intent);

      // aiqua notification data가 js handler로 들어오게끔 처리
      Intent notificationEvent = new Intent(REMOTE_NOTIFICATION_EVENT);
      notificationEvent.putExtra("notification", message);
      LocalBroadcastManagerl
        .getInstance(this)
        .sendBroadcast(notificationEvent);

      notifications.put(remoteMessage.getMessageId(), remoteMessage);
      ReactNativeFirebaseMessagingStoreHelper.getInstance().getMessagingStore().storeFirebaseMessage(remoteMessage);
      return;
    } else {
      // Add a RemoteMessage if the message contains a notification payload
      if (remoteMessage.getNotification() != null) {
        notifications.put(remoteMessage.getMessageId(), remoteMessage);
        ReactNativeFirebaseMessagingStoreHelper.getInstance().getMessagingStore().storeFirebaseMessage(remoteMessage);
      }

      //  |-> ---------------------
      //      App in Foreground
      //   ------------------------
      if (SharedUtils.isAppInForeground(context)) {
        emitter.sendEvent(ReactNativeFirebaseMessagingSerializer.remoteMessageToEvent(remoteMessage, false));
        return;
      }


      //  |-> ---------------------
      //    App in Background/Quit
      //   ------------------------

      try {
        Intent backgroundIntent = new Intent(context, ReactNativeFirebaseMessagingHeadlessService.class);
        backgroundIntent.putExtra("message", remoteMessage);
        ComponentName name = context.startService(backgroundIntent);
        if (name != null) {
          HeadlessJsTaskService.acquireWakeLockNow(context);
        }
      } catch (IllegalStateException ex) {
        // By default, data only messages are "default" priority and cannot trigger Headless tasks
        Log.e(
          TAG,
          "Background messages only work if the message priority is set to 'high'",
          ex
        );
      }

    }
  }
}
