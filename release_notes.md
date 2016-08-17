# Release Notes

## 0.3

This release brings compatibility with current React Native versions.

* Added compatibility with recent react-native (>= 0.23.0)
* Update included SimpleExampleApp to react-native 0.30.0. Please refer to the
  `example/` folder for how to set up `boot-react-native`.
* BREAKING: due to changes to the react-native packager, a patch needs to be
  applied to the react-native npm module. To do this manually for the example app,

  ```
  patch -d app/node_modules/react-native -p1 < rn-goog-require.patch
  ```

  You can also use the new `patch-rn` task, which automatically patches
  the npm module.
* BREAKING: disabled sourcemap support temporarily (see
  https://github.com/mjmeintjes/boot-react-native/issues/58) The feature will be
  added back in.
* BREAKING: if you see "Reload websocket error" on Android, the reason is most
  likely that reverse port mapping is not enabled in your emulator.

  To enable "boot reload"-based reloading, enable `adb reverse tcp:8079 tcp:8079`. This
  needs to be run every time you start the simulator.

  Note that this is not the same as "Live Reload" or "Hot Reload" features

* React Native's "Live Reload" or "Hot Reload" features must be disabled for
  boot-reload based reloading to work correctly

* BREAKING: instead of using the `print-ios-log` or `print-android-log`
  features, please use react native's `react-native ios-log` and `react-native android-log` features

* BREAKING: the packager is not run inside the boot anomore. Instead, rely on
  the standard React Native packager terminal window that pops up when running
  `react-native run-ios` or `react-native run-android`

* BREAKING: for unknown reasons building for iOS requires adding `react-dom` to
  `package.json` (in addtion to `react` and with the same version number as
  `react`).

* Add audible notification for example application

## 0.2-SNAPSHOT

* Sourcemap support added (thanks @scttnlsn)
* Tested and working on iOS (@pesterhazy, @jellea)
* Build and run app in iOS simulator directly - without xcode (@pesterhazy)
* Added tasks to print Android and iOS log messages - `print-android-log` and `print-ios-log` - @pesterhazy and @AdamFrey
* Some general cleanup and removal of warnings to example app
* nREPL supported, tested and documentation added (@mjmeintjes and @hugoduncan)
 
 
