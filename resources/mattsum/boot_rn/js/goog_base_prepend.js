if (typeof global !== 'undefined') {
    goog = goog || {};
    global.goog = goog; //Set's up the goog object in global namespace, because React Native runs everything in it's own function, and doesn't expose variables declared by default.
}
