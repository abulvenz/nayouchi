 var path = require('path');
 var webpack = require('webpack');

 module.exports = {
     entry: './src/main/js/index.js',
     output: {
         path: path.resolve(__dirname, '.'),
         filename: './src/main/webroot/app.js'
     },
     module: {
         loaders: [
             {
                 test: /\.js$/,
                 loader: 'babel-loader',
                 query: {
                     presets: ['es2015']
                 }
             }
         ]
     },
     stats: {
         colors: true
     },
     devtool: 'source-map'
 };
