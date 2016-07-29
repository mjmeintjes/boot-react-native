# notes

* "Reload websocket error". Solution: `adb reverse tcp:8079 tcp:8079`. This
  needs to be run every time you start the simulator.

* to work around changes in the RN packager: after `npm install`, run

```
patch -d app/node_modules/react-native -p1 < rn-goog-require.patch
```

* note about moving from having the packager run from inside boot towards
  running it in a separate terminal window (taming the packager is an uphill battle)

* for unknown reasons building for iOS requires adding `react-dom` to
  `package.json` (in addtion to `react` and with the same version number as `react`)

* source-maps are disabled for now, to be added back when things stabilize

* remove print-ios-log in favor of `react-native log-ios`
