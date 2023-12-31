package com.example.projetamio.notifications;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;

import androidx.annotation.NonNull;
import androidx.core.app.ActivityCompat;
import androidx.core.app.NotificationCompat;

import com.example.projetamio.R;
import com.example.projetamio.fetchedData.FetchedData;
import com.example.projetamio.fetchedData.FetchedDataRequest;
import com.example.projetamio.fetchedData.SensorInformation;
import com.example.projetamio.utils.Constants;

import java.io.IOException;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Timer;
import java.util.TimerTask;

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.Response;

public class NotificationService extends Service {

    private final Timer timer = new Timer(true);
    int notification_id = 1;

    public NotificationService() {}

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public void onCreate() {
        // Create the used notification channel.
        createNotificationChannel();

        final Handler handler = new Handler();
        TimerTask task = new TimerTask() {
            public void run() {
                handler.post(() -> {
                    if (LocalTime.now().isAfter(LocalTime.of(19, 0)) &&
                            LocalTime.now().isBefore(LocalTime.of(23, 0))) {
                        if (FetchedData.getInstance().data != null) {
                            verifyIfAnySensorChangedState();
                        }
                    }
                });
            }
        };
        sendNotification(1, "Demonstration", "Just a message to demonstrate that notifications work.");
        timer.schedule(task, 0, Constants.SENSOR_UPDATE_INTERVAL_IN_MINUTES * 60 * 1000);
    }

    private void verifyIfAnySensorChangedState() {
        Call call = FetchedDataRequest.getSensorInformation();

        call.enqueue(new Callback() {
            @Override
            public void onFailure(@NonNull Call call, @NonNull IOException e) {
                // Not connected to VPN, should do nothing.
            }

            @Override
            public void onResponse(@NonNull Call call, @NonNull Response response) throws IOException {
                List<Boolean> oldSensorState = new ArrayList<>();

                // Treat the new response and save it.
                FetchedDataRequest.treatJSONResponseAndUpdateUI(response);

                // Add true if sensor is on, false otherwise.
                for (int i = 0; i < FetchedData.getInstance().data.size(); i++) {
                    float oldSensorValue = FetchedData.getInstance().data.get(i).value;

                    oldSensorState.add(oldSensorValue >= Constants.SENSOR_THRESHOLD);
                }

                // Verify if all the sensors that were off are still off.
                for (int i = 0; i < FetchedData.getInstance().data.size(); i++) {
                    SensorInformation currentNewSensor =
                            FetchedData.getInstance().data.get(i);

                    // In case the sensor is now on, should send a notification.
                    if (!oldSensorState.get(i) && currentNewSensor.value >= Constants.SENSOR_THRESHOLD) {
                        String title = currentNewSensor.mote + " is now on!";
                        String description = "The new value is " + currentNewSensor.value;
                        sendNotification(notification_id++, title, description);
                    }
                }
            }
        });
    }

    private void sendNotification(int id, String title, String description) {
        NotificationCompat.Builder builder = new NotificationCompat.Builder(this, Constants.NOTIFICATION_CHANNEL_NAME)
                .setContentTitle(title)
                .setContentText(description)
                .setSmallIcon(R.drawable.ic_launcher_background)
                .setAutoCancel(true)
                .setPriority(NotificationCompat.PRIORITY_DEFAULT);

        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
                == PackageManager.PERMISSION_GRANTED) {
            NotificationManager notificationManager =
                    (NotificationManager) getSystemService(Service.NOTIFICATION_SERVICE);
            notificationManager.notify(id, builder.build());
        }
    }

    @SuppressLint("ObsoleteSdkInt")
    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            int importance = NotificationManager.IMPORTANCE_DEFAULT;
            NotificationChannel channel = new NotificationChannel(Constants.NOTIFICATION_CHANNEL_NAME,
                    Constants.NOTIFICATION_CHANNEL_NAME, importance);

            NotificationManager notificationManager = getSystemService(NotificationManager.class);
            notificationManager.createNotificationChannel(channel);
        }
    }
}
