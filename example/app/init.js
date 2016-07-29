/**
 * Sample React Native App
 * https://github.com/facebook/react-native
 */

'use strict';

// global.window = {};

// hack: get reagent to find ReactNative.render as ReactDOM.render
global.ReactDOM = require('react-native');

require('./build/main.js');
mattsum.simple_example.core.main();
