/**
 Overrides Google Closure's jsloader functionality in order for us to
 support hot reloading.
 Now, instead of injecting <script> tags when asked to load js files,
 Google Closure will fetch and eval the js files. This approach works
 for both simulator/real phone, as well as when running in Debug mode
 in Chrome.
**/
(function () {
    var config = {
        basePath: '{{ asset-path }}',
        server: '{{ server-url }}'
    };

    var globalEval = eval;
    var importScripts = myImportScripts;
    var scriptQueue = [];

    function customEval(url, javascript, success, error) {
        if (scriptQueue.length > 0){
            if (scriptQueue[0] === url) {
                try {
                    globalEval(javascript);

                    scriptQueue.shift();
                    //in case we reload jsloader
                    if(url.indexOf('jsloader') > -1){
                        shimJsLoader();
                    }
                    success();
                } catch (e) {
                    console.error('Evaluation error in: ' + url + " - " + e.message);
                    console.error(e);
                    error();
                }
            } else {
                setTimeout(customEval, 5, url, javascript, success, error);
            }
        } else {
            console.error('Something bad happened...');
            error();
        }
    }

    function myImportScripts(path, success, error) {
        if(typeof success !== 'function') { success = function(){}; }
        if(typeof error !== 'function') { error = function(){}; }

        var url = config.server + '/' + path;

        scriptQueue.push(url);
        fetch(url)
            .then(function(response) { return response.text(); })
            .then(function(responseText) {
                var js = responseText;
                customEval(url, js, success, error);
            })
            .catch(function(error) {
                console.error('Error loading script, please check your config setup.');
                console.error(error);
                error();
            });
    }
    // Uninstall watchman???
    function importJs(src, success, error){
        if(typeof success !== 'function') { success = function(){}; }
        if(typeof error !== 'function') { error = function(){}; }

        if (!src.startsWith("/")) {
            src = "/" + src; 
        }
        if (src.startsWith("/goog")){
            src = "/main.out" + src;
        }
        var jsUrl = config.basePath + src;
        console.log('(Reload Bridge) Importing: ' + jsUrl);

        try {
            myImportScripts(jsUrl, success, error);
        } catch(e) {
            console.warn('Could not load: ' + config.basePath + src);
            console.error('Import error: ' + e);
            error();
        }
    }

    function shimBaseGoog(){
        console.info('Shimming goog functions.');
        goog.basePath = 'goog/';
        goog.writeScriptSrcNode = importJs;
        goog.writeScriptTag_ = function(src, optSourceText) {
            importJs(src);
            return true;
        };
        goog.inHtmlDocument_ = function() { return true; };
    }

    // Used by boot-reload - uses importScript to load JS rather than <script>'s
    function shimJsLoader(){
        goog.net.jsloader.load = function(uri, options) {
            if (typeof uri === 'string' || uri instanceof String) {
                //already a string
            }
            else {
                uri = uri.getPath();
            }

            if (uri.startsWith('/node_modules')) {
                //Don't reload node_modules files because they have been generated by us
                //TODO: is this a good assumption?
                return goog.async.Deferred.succeed(null); //return success in order for success callback to fire
            }


            var deferred = new goog.async.Deferred();

            setTimeout(function(){
                importJs(uri,
                         function (res) {
                             deferred.callback(res);
                             console.log("SUCCESSFULLY loaded " + uri);
                         },
                         function (err) {
                             console.warn("ERROR while loading " + uri);
                             console.error(err);
                             deferred.errback(err);
                         });
            }, 1);

            return deferred;
        };
    }
    console.log('Starting shim setup');
    shimBaseGoog();
    shimJsLoader();
    console.log('Done shimming');
})();
