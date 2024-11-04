var exec = require('cordova/exec');

exports.uploadFileInChunks = function(fileUri, sasToken, containerName, blobName, successCallback, errorCallback) {
    exec(successCallback, errorCallback, 'ChunkedFileUploader', 'uploadFileInChunks', [fileUri, sasToken, containerName, blobName]);
};
