package ru.seva.finder;

import android.app.Notification;
import android.app.NotificationManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.support.v4.app.NotificationCompat;
import android.support.v4.content.LocalBroadcastManager;
import android.telephony.SmsMessage;
import java.util.regex.Matcher;
import java.util.regex.Pattern;


public class SmsReceiver extends BroadcastReceiver {
    private static final String SMS_RECEIVED = "android.provider.Telephony.SMS_RECEIVED";
    StringBuilder text = new StringBuilder("");

    @Override
    public void onReceive(Context context, Intent intent) {

        SharedPreferences sPref;
        sPref = PreferenceManager.getDefaultSharedPreferences(context);

        /* блок выключения звука
        AudioManager aMan = (AudioManager) context.getSystemService(context.AUDIO_SERVICE);
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                aMan.adjustStreamVolume(AudioManager.STREAM_NOTIFICATION, AudioManager.ADJUST_MUTE, 0);
                Toast.makeText(context, "213", Toast.LENGTH_SHORT).show();
            } else {
                aMan.setStreamMute(AudioManager.STREAM_NOTIFICATION, true);
                Toast.makeText(context, "000", Toast.LENGTH_SHORT).show();
            }
        } catch (NullPointerException e) {
            //хз когда это сработает
        }
        */

        String phone = "";  //на случай отсутствия в sms-ке
        Bundle intentExtras = intent.getExtras();
        String action = intent.getAction();
        if ((intentExtras != null) && (action.equals(SMS_RECEIVED))) {
            /* древний метод для совместимости с 17м API */
            Object[] sms = (Object[]) intentExtras.get("pdus");

            for (int i = 0; i < sms.length; ++i) {
                /* Parse Each Message */
                SmsMessage smsMessage = SmsMessage.createFromPdu((byte[]) sms[i]);
                phone = smsMessage.getOriginatingAddress();
                String message = smsMessage.getMessageBody();
                text.append(message);
            }

            //проверка на возможный ответ
            Intent stop_bar = new Intent("disable_bar");
            String message = text.toString();
            if (checkWifiSms(message)) {
                Intent new_message_intent = new Intent(context, NewGoogleGeo.class);  //intent-сервис с запросом к google-api
                new_message_intent.putExtra("phone", phone);
                new_message_intent.putExtra("message", message);
                context.startService(new_message_intent);
                LocalBroadcastManager.getInstance(context).sendBroadcast(stop_bar);
            } else if (checkGpsSms(message)) {
                Intent gps_intent = new Intent(context, GpsCoordsReceived.class);
                gps_intent.putExtra("phone", phone);
                gps_intent.putExtra("message", message);
                LocalBroadcastManager.getInstance(context).sendBroadcast(stop_bar);
                context.startService(gps_intent);
            } else if (message.equals("gps not enabled")) {
                NotificationCompat.Builder builder = new NotificationCompat.Builder(context)
                        .setSmallIcon(R.mipmap.ic_launcher)
                        .setContentTitle(phone)
                        .setContentText(context.getString(R.string.gps_not_enabled))
                        .setAutoCancel(true);  //подумать над channel id  и ИКОНКОЙ!
                Notification notification = builder.build();
                NotificationManager nManage = (NotificationManager) context.getSystemService(context.NOTIFICATION_SERVICE);
                int id = sPref.getInt("notification_id", 0);
                nManage.notify(id, notification);
                sPref.edit().putInt("notification_id", id+1).apply();
                LocalBroadcastManager.getInstance(context).sendBroadcast(stop_bar);
            } else if (message.equals("unable get location") || message.equals("net info unavailable")) {
                NotificationCompat.Builder builder = new NotificationCompat.Builder(context)
                        .setSmallIcon(R.mipmap.ic_launcher)
                        .setContentTitle(phone)
                        .setContentText(context.getString(R.string.no_coord_bad_signal))
                        .setAutoCancel(true);  //подумать над channel id  и ИКОНКОЙ!
                Notification notification = builder.build();
                NotificationManager nManage = (NotificationManager) context.getSystemService(context.NOTIFICATION_SERVICE);
                int id = sPref.getInt("notification_id", 0);
                nManage.notify(id, notification);
                sPref.edit().putInt("notification_id", id+1).apply();
                LocalBroadcastManager.getInstance(context).sendBroadcast(stop_bar);
            }


            //проверка на возможный запрос наших координат
            //в SMS пришла команда на wifi при этом в настройках указанно отвечать
            if (message.equals(sPref.getString("wifi", context.getString(R.string.wifi_default_command))) && sPref.getBoolean("answer", false)) {
                Intent wifi_intent = new Intent(context, WifiSearch.class);
                wifi_intent.putExtra("phone_number", phone);
                context.startService(wifi_intent);
            }

            //в SMS пришла команда на GPS при этом в настройках указанно отвечать
            if (message.equals(sPref.getString("gps", context.getString(R.string.wifi_default_command))) && sPref.getBoolean("answer", false)) {
                Intent gps_intent = new Intent(context, GpsSearch.class);
                gps_intent.putExtra("phone_number", phone);
                context.startService(gps_intent);
            }

            //в SMS пришла команда на remote_add при этом опция включена
            if (message.equals(sPref.getString("remote", "NO_DEFAULT_VALUE")) && sPref.getBoolean("remote_active", false)) {
                Intent remote_intent = new Intent(context, RemoteAdding.class);
                remote_intent.putExtra("phone_number", phone);
                context.startService(remote_intent);
            }

            text = new StringBuilder("");  //иначе новый текст складывается с предудущим
        }
    }

    public static boolean checkWifiSms(String textMessage) {
        Pattern pat1 = Pattern.compile("^(gsm|wcdma|lte|cdma)\nMCC(\\d+)\nMNC(\\d+)\nLAC(\\d+)\nCID(\\d+)");
        Matcher m1 = pat1.matcher(textMessage);
        Pattern pat2 = Pattern.compile("([0-9a-f]{12})\n(-?\\d+)");  //mac или сети могут отсутствовать
        Matcher m2 = pat2.matcher(textMessage);
        return ((m1.find() || m2.find()));
    }

    public static boolean checkGpsSms(String message) {
        Pattern gps_pat = Pattern.compile("^lat:-?\\d+\\.\\d+ lon:-?\\d+\\.\\d+");
        Matcher m = gps_pat.matcher(message);
        return m.find();
    }


}