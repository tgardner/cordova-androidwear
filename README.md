
# Cordova Android Wear Plugin

The plugin has functions that allows your app to have bidirectional communication with Android Wear Smartwatch applications and Cordova applications.

## Usage

1. Add the plugin to your cordova project.
2. Add a [WearableListenerService](https://developer.android.com/reference/com/google/android/gms/wearable/WearableListenerService.html) to your Wearable project
  ```java
  public class WearListenerService extends WearableListenerService {

      private static final String TAG = WearListenerService.class.getSimpleName();
      public static final String MESSAGE_RECEIVED_PATH = "net.trentgardner.cordova.androidwear.NewMessage";
      
      @Override
      public void onMessageReceived(MessageEvent messageEvent) {

          if (messageEvent.getPath().equals(MESSAGE_RECEIVED_PATH)) {
              final String message = new String(messageEvent.getData());
              Log.v(TAG, "Message received on watch is: " + message);
              
              // Custom code goes here
          } else {
              super.onMessageReceived(messageEvent);
          }

      }
  }
  ```

3. Send messages back using the same message path

## Example
  ```javascript
  AndroidWear.onConnect(function(e) {
      alert("Connection Successfully Established - handle: " + e.handle);

      AndroidWear.onDataReceived(e.handle, function(e) {
          alert("Data received - handle: " + e.handle + " data: "+ e.data);
      });

      AndroidWear.sendData(e.handle, "Hello From Cordova!");
  });
  ```