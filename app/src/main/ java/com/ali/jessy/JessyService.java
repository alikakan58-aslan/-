package com.ali.jessy;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.Intent;
import android.content.pm.ServiceInfo;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.speech.RecognitionListener;
import android.speech.RecognizerIntent;
import android.speech.SpeechRecognizer;
import android.speech.tts.TextToSpeech;
import android.telephony.SmsManager;
import java.util.ArrayList;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class JessyService extends Service implements RecognitionListener {

    private static final String STOP_KEY = "کلید خاموشی";

    private SpeechRecognizer recognizer;
    private TextToSpeech tts;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private boolean running = true;

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        NotificationManager nm = getSystemService(NotificationManager.class);
        nm.createNotificationChannel(new NotificationChannel("jessy", "Jessy", NotificationManager.IMPORTANCE_LOW));
        Notification n = new Notification.Builder(this, "jessy")
                .setContentTitle("Jessy")
                .setContentText("Listening...")
                .setSmallIcon(android.R.drawable.ic_btn_speak_now)
                .build();
        if (Build.VERSION.SDK_INT >= 29) {
            startForeground(1, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE);
        } else {
            startForeground(1, n);
        }

        if (tts == null) {
            tts = new TextToSpeech(this, status -> {
                if (status == TextToSpeech.SUCCESS) {
                    tts.setLanguage(new Locale("fa", "IR"));
                    speak("جسی آماده است");
                    startListening();
                }
            });
        }
        return START_STICKY;
    }

    private void speak(String text) {
        if (tts != null) {
            tts.speak(text, TextToSpeech.QUEUE_FLUSH, null, "jessy");
        }
    }

    private void startListening() {
        handler.post(() -> {
            if (!running) return;
            if (recognizer == null) {
                recognizer = SpeechRecognizer.createSpeechRecognizer(this);
                recognizer.setRecognitionListener(this);
            }
            Intent i = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
            i.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);
            i.putExtra(RecognizerIntent.EXTRA_LANGUAGE, "fa-IR");
            recognizer.startListening(i);
        });
    }

    private String fixDigits(String s) {
        StringBuilder sb = new StringBuilder();
        for (char c : s.toCharArray()) {
            if (c >= '\u06F0' && c <= '\u06F9') sb.append((char) ('0' + (c - '\u06F0')));
            else if (c >= '\u0660' && c <= '\u0669') sb.append((char) ('0' + (c - '\u0660')));
            else sb.append(c);
        }
        return sb.toString();
    }

    private void handle(String raw) {
        String t = fixDigits(raw);
        String low = t.toLowerCase();
        if (!(t.contains("جسی") || t.contains("جیسی") || low.contains("jessy") || low.contains("jessie"))) {
            return;
        }

        if (t.contains(STOP_KEY)) {
            speak("خاموش شدم");
            running = false;
            handler.postDelayed(this::stopSelf, 2500);
            return;
        }

        Matcher m = Pattern.compile("\\d{7,}").matcher(t);
        boolean hasNumber = m.find();
        String number = hasNumber ? m.group() : "";

        try {
            if (t.contains("پیامک") || t.contains("اس ام اس")) {
                if (!hasNumber) {
                    speak("شماره را بگو");
                    return;
                }
                String msg = t.substring(m.end()).trim();
                if (msg.isEmpty()) {
                    speak("متن پیام را بگو");
                    return;
                }
                SmsManager.getDefault().sendTextMessage(number, null, msg, null, null);
                speak("پیام ارسال شد");
            } else if (t.contains("زنگ") || t.contains("تماس")) {
                if (!hasNumber) {
                    speak("شماره را بگو");
                    return;
                }
                Intent call = new Intent(Intent.ACTION_CALL, Uri.parse("tel:" + number));
                call.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                startActivity(call);
                speak("در حال تماس");
            } else if (t.contains("شماره گیر") || t.contains("شماره‌گیر")) {
                Intent dial = new Intent(Intent.ACTION_DIAL);
                dial.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                startActivity(dial);
                speak("شماره‌گیر باز شد");
            } else {
                speak("دستور را نفهمیدم");
            }
        } catch (Exception e) {
            speak("خطا در انجام دستور");
        }
    }

    @Override
    public void onResults(Bundle results) {
        ArrayList<String> list = results.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION);
        if (list != null && !list.isEmpty()) {
            handle(list.get(0));
        }
        handler.postDelayed(this::startListening, 800);
    }

    @Override
    public void onError(int error) {
        handler.postDelayed(this::startListening, 1000);
    }

    @Override public void onReadyForSpeech(Bundle params) {}
    @Override public void onBeginningOfSpeech() {}
    @Override public void onRmsChanged(float rmsdB) {}
    @Override public void onBufferReceived(byte[] buffer) {}
    @Override public void onEndOfSpeech() {}
    @Override public void onPartialResults(Bundle partialResults) {}
    @Override public void onEvent(int eventType, Bundle params) {}

    @Override
    public void onDestroy() {
        running = false;
        if (recognizer != null) recognizer.destroy();
        if (tts != null) tts.shutdown();
        super.onDestroy();
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
}
