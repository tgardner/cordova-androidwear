
# Cordova Android Wear Plugin

The plugin has functions that allows your app to have bidirectional communication with Android Wear Smartwatch applications and Cordova applications.

## Installation
`cordova plugin add net.trentgardner.cordova.androidwear`

## Usage

1. Add the plugin to your cordova project.

2. Bundle your watch apk in your project by following the instructions in the [documentation](https://developer.android.com/training/wearables/apps/packaging.html#PackageManually).

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

A slightly more complete example is available at https://github.com/tgardner/cordova-androidwear-example
