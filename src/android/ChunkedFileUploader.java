package com.yourcompany.plugin;

import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.content.Context;
import android.net.Uri;
import android.os.Build;
import android.util.Log;

import androidx.core.app.NotificationCompat;

import org.apache.cordova.CallbackContext;
import org.apache.cordova.CordovaPlugin;
import org.apache.cordova.PluginResult;
import org.json.JSONArray;
import org.json.JSONException;

import java.io.File;
import java.io.FileInputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;

public class ChunkedFileUploader extends CordovaPlugin {

    private static final String TAG = "ChunkedFileUploader";
    private static final int CHUNK_SIZE = 1024 * 1024; // 1 MB chunks
    private NotificationManager notificationManager;
    private NotificationCompat.Builder notificationBuilder;
    private int notificationId = 1;

    @Override
    public boolean execute(String action, JSONArray args, CallbackContext callbackContext) throws JSONException {
        if ("uploadFileInChunks".equals(action)) {
            String fileUri = args.getString(0);
            String sasToken = args.getString(1);
            String containerName = args.getString(2);
            String blobName = args.getString(3);
            startUpload(fileUri, sasToken, containerName, blobName, callbackContext);
            return true;
        }
        return false;
    }

    private void startUpload(String fileUri, String sasToken, String containerName, String blobName, CallbackContext callbackContext) {
        cordova.getThreadPool().execute(() -> {
            try {
                Uri uri = Uri.parse(fileUri);
                File file = new File(uri.getPath());
                long fileSize = file.length();
                FileInputStream fileInputStream = new FileInputStream(file);

                createNotification("Uploading file...");
                int chunkCount = (int) Math.ceil((double) fileSize / CHUNK_SIZE);

                for (int i = 0; i < chunkCount; i++) {
                    int start = i * CHUNK_SIZE;
                    int end = Math.min(start + CHUNK_SIZE, (int) fileSize);
                    byte[] buffer = new byte[end - start];
                    fileInputStream.read(buffer);

                    uploadChunk(buffer, sasToken, containerName, blobName, i, chunkCount);

                    updateNotification("Uploading...", i, chunkCount);
                }

                fileInputStream.close();
                callbackContext.success("File uploaded successfully.");
                cancelNotification();
            } catch (Exception e) {
                Log.e(TAG, "Upload failed", e);
                callbackContext.error("File upload failed: " + e.getMessage());
                cancelNotification();
            }
        });
    }

    private void uploadChunk(byte[] buffer, String sasToken, String containerName, String blobName, int chunkIndex, int chunkCount) throws Exception {
        String urlString = "https://" + containerName + ".blob.core.windows.net/" + containerName + "/" + blobName + "?comp=block&blockid=" + String.format("%05d", chunkIndex) + "&" + sasToken;
        URL url = new URL(urlString);
        HttpURLConnection connection = (HttpURLConnection) url.openConnection();
        connection.setDoOutput(true);
        connection.setRequestMethod("PUT");
        connection.setRequestProperty("x-ms-blob-type", "BlockBlob");
        OutputStream outputStream = connection.getOutputStream();
        outputStream.write(buffer);
        outputStream.close();

        int responseCode = connection.getResponseCode();
        if (responseCode != 201) {
            throw new Exception("Failed to upload chunk: " + responseCode);
        }
    }

    private void createNotification(String title) {
        notificationManager = (NotificationManager) cordova.getActivity().getSystemService(Context.NOTIFICATION_SERVICE);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel("upload_channel", "File Upload", NotificationManager.IMPORTANCE_LOW);
            notificationManager.createNotificationChannel(channel);
        }
        notificationBuilder = new NotificationCompat.Builder(cordova.getActivity(), "upload_channel")
                .setContentTitle(title)
                .setSmallIcon(android.R.drawable.stat_sys_upload)
                .setPriority(NotificationCompat.PRIORITY_LOW);
        notificationManager.notify(notificationId, notificationBuilder.build());
    }

    private void updateNotification(String contentText, int currentChunk, int totalChunks) {
        notificationBuilder.setContentText(contentText + " (" + currentChunk + "/" + totalChunks + ")");
        notificationManager.notify(notificationId, notificationBuilder.build());
    }

    private void cancelNotification() {
        notificationManager.cancel(notificationId);
    }
}
