/* Overrides how Google Closure's provide and require functions work, in order for them to work with React Native's packager.
 */
if (typeof global !== 'undefined') {
    global.goog = goog; //Set's up the goog object in global namespace, because React Native runs everything in it's own function, and doesn't expose variables declared by default.
    goog.provide = function(name) {
        if (goog.isProvided_(name)) {
            return; //don't throw an error when called multiple times, because it is going to be called multiple times in from react-native
        }
        //Rest of the code is copied as is from Closure
        delete goog.implicitNamespaces_[name];

        var namespace = name;
        while ((namespace = namespace.substring(0, namespace.lastIndexOf('.')))) {
            if (goog.getObjectByName(namespace)) {
                break;
            }
            goog.implicitNamespaces_[namespace] = true;
        }
        goog.exportPath_(name);
    };
    //Replace goog.require with react-native's implementation, skip errors, because there are going to be some (e.g. missing 'soft' depedencies) and we don't care about them
    goog.require = function(name) {
        try {
            require(name);
        } catch (e) {
            console.log(e);
        }
    };
}
