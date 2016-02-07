/**
 * Copyright (c) 2015-present, Facebook, Inc.
 * All rights reserved.
 *
 * This source code is licensed under the BSD-style license found in the
 * LICENSE file in the root directory of this source tree. An additional grant
 * of patent rights can be found in the PATENTS file in the same directory.
 *
 * Note: This is a fork of the fb-specific transform.js
 */
'use strict';

// TODO: this should probably not be hardcoded
const fs = require('fs');
const transformer = require('react-native/packager/transformer');

const util = require('util');
function getSourceMapModuleName(code) {
    //Extract the source map module name from source code
    var sourceMapRegex = /sourceMappingURL=(.*)\.js\.map/;
    var match = sourceMapRegex.exec(code);
    var moduleName = "";
    if (match != null) {
	      moduleName = match[1] + ".cljs";
    }
    return moduleName;
}
function transform(code, filename, callback) {
    fs.readFile(filename + '.map', function (err, map) {
        console.log("Generating sourcemap for " + filename);
        var shouldGenerateSourceMap =
                !err && filename.indexOf("/cljs.") == -1 && filename.indexOf("/clojure.") == -1;

        if (shouldGenerateSourceMap) {
            var sourceMap = JSON.parse(map.toString());
            sourceMap.sources = [getSourceMapModuleName(code)];
            sourceMap.sourceRoot = "/build/node_modules/";
            fs.readFile(filename.replace(".js", ".cljs"), function (err, cljs) {
                if (!err) {
                    sourceMap.sourcesContent = [cljs.toString()];
                }
                sourceMap.file = "bundle.js";
                callback(null, {
                    code: code.replace("# sourceMappingURL=", ""),
                    map: sourceMap
                });
            });
        } else {
            sourceMap = getBasicSourceMap(filename, code);
            callback(null, {
                code: code,
                map: sourceMap
            });
        }
    });
}

function getBasicMappings(code) {
    //TODO: This is copied from https://github.com/facebook/react-native/blob/528e30987aba8848f8c8815f00c42ecb2ce0919a/packager/react-packager/src/Bundler/Bundle.js#L265
    //but unfortunately I know next to nothing about setting up source maps.
    //Therefore it is not working. But, this is only run for js files, and Chrome falls
    //back to displaying the Javascript from the bundled file anyway when it can't understand
    //the source mappings from here.
    //TLDR - I don't think this does anything, and it probably needs to be fixed, but not critical.
    var mappings = "";
    const line = 'AACA';
    let lastCharNewLine  = false;
    var moduleLines = 0;
    for (let t = 0; t < code.length; t++) {
        if (t === 0) {
            mappings += 'AC';
            mappings += 'A';
        } else if (lastCharNewLine) {
            moduleLines++;
            mappings += line;
        }
        lastCharNewLine = code[t] === '\n';
        if (lastCharNewLine) {
            mappings += ';';
        }
    }
    mappings += ';';
    return mappings;
}
function getBasicSourceMap(filename, code) {
    //This is supposed to just provide a fallback sourcemap
    //If we don't provide a source map for every module, then Google Chrome
    //starts throwing errors ('sources' could not be found on undefined)
    //Chrome expects every section (and there is a section for each module) to
    //have a 'map' property with an array of 'sources'.

    var mappings = getBasicMappings(code);
    const map = {
        file: "bundle.js",
        sources: [filename],
        version: 3,
        names: [],
        mappings: "",
        sourcesContent: [""]
    };
    return map;
}

module.exports = function (data, callback) {
    if (data.sourceCode && data.sourceCode.indexOf('Compiled by ClojureScript') > -1) {
        transform(data.sourceCode, data.filename, callback);
    } else {
        var cb = function(err, mod) {
            mod.map = getBasicSourceMap(data.filename, mod.code);
            return callback(err,  mod);
        };
        transformer(data, cb);
    }
};
