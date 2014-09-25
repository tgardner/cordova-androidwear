
# Cordova Android Wear Plugin

The plugin has functions that allows your app to have bidirectional communication with Android Wear Smartwatch applications and Cordova applications.

## Usage

1. Add the plugin to your cordova project.

2. You need to install **Google Play Services** from the `Android Extras` section using the Android SDK manager (run `android`).
  You need to add the following line to your `local.properties`
  `android.library.reference.1=PATH_TO_ANDROID_SDK/sdk/extras/google/google_play_services/libproject/google-play-services_lib`

  Alternatively, you can run `cordova plugin add com.google.playservices`

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