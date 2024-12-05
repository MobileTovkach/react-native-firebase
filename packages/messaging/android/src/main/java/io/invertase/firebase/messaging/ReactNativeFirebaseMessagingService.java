package io.invertase.firebase.messaging;

import android.content.ComponentName;
import android.content.Intent;
import android.util.Log;

import com.facebook.react.HeadlessJsTaskService;
import com.google.firebase.messaging.FirebaseMessagingService;
import com.google.firebase.messaging.RemoteMessage;

import java.util.HashMap;

import io.invertase.firebase.app.ReactNativeFirebaseApp;
import io.invertase.firebase.common.ReactNativeFirebaseEventEmitter;
import io.invertase.firebase.common.SharedUtils;

public class ReactNativeFirebaseMessagingService extends FirebaseMessagingService {
  static HashMap<String, RemoteMessage> notifications = new HashMap<>();
  @Override
  public void onSendError(String messageId, Exception sendError) {
    ReactNativeFirebaseEventEmitter emitter = ReactNativeFirebaseEventEmitter.getSharedInstance();
    emitter.sendEvent(
      ReactNativeFirebaseMessagingSerializer.messageSendErrorToEvent(messageId, sendError));
  }

  @Override
  public void onDeletedMessages() {
    ReactNativeFirebaseEventEmitter emitter = ReactNativeFirebaseEventEmitter.getSharedInstance();
    emitter.sendEvent(ReactNativeFirebaseMessagingSerializer.messagesDeletedToEvent());
  }

  @Override
  public void onMessageSent(String messageId) {
    ReactNativeFirebaseEventEmitter emitter = ReactNativeFirebaseEventEmitter.getSharedInstance();
    emitter.sendEvent(ReactNativeFirebaseMessagingSerializer.messageSentToEvent(messageId));
  }

  @Override
  public void onNewToken(String token) {
    ReactNativeFirebaseEventEmitter emitter = ReactNativeFirebaseEventEmitter.getSharedInstance();
    emitter.sendEvent(ReactNativeFirebaseMessagingSerializer.newTokenToTokenEvent(token));
  }

  @Override
  public void onMessageReceived(RemoteMessage remoteMessage) {
    // noop - handled in receiver
    Log.d("TAG_firbase", "broadcast received for message");
    if (ReactNativeFirebaseApp.getApplicationContext() == null) {
      ReactNativeFirebaseApp.setApplicationContext(this.getApplicationContext());
    }

    ReactNativeFirebaseEventEmitter emitter = ReactNativeFirebaseEventEmitter.getSharedInstance();

    // Add a RemoteMessage if the message contains a notification payload
    if (remoteMessage.getNotification() != null) {
      notifications.put(remoteMessage.getMessageId(), remoteMessage);
      ReactNativeFirebaseMessagingStoreHelper.getInstance()
        .getMessagingStore()
        .storeFirebaseMessage(remoteMessage);
    }

    //  |-> ---------------------
    //      App in Foreground
    //   ------------------------
    if (SharedUtils.isAppInForeground(this.getApplicationContext())) {
      emitter.sendEvent(
        ReactNativeFirebaseMessagingSerializer.remoteMessageToEvent(remoteMessage, false));
      return;
    }

    //  |-> ---------------------
    //    App in Background/Quit
    //   ------------------------

    try {
      Intent backgroundIntent =
        new Intent(this, ReactNativeFirebaseMessagingHeadlessService.class);
      backgroundIntent.putExtra("message", remoteMessage);
      ComponentName name = this.startService(backgroundIntent);
      if (name != null) {
        HeadlessJsTaskService.acquireWakeLockNow(this);
      }
    } catch (IllegalStateException ex) {
      // By default, data only messages are "default" priority and cannot trigger Headless tasks
      Log.e("TAG_firbase", "Background messages only work if the message priority is set to 'high'", ex);
    }
  }
}
