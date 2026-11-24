package com.mrsahed.jimai;

import android.Manifest;
import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Bundle;
import android.os.VibrationEffect;
import android.os.Vibrator;
import android.speech.RecognitionListener;
import android.speech.RecognizerIntent;
import android.speech.SpeechRecognizer;
import android.speech.tts.TextToSpeech;
import android.view.WindowManager;
import android.webkit.JavascriptInterface;
import android.webkit.WebView;

import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Locale;

/** Native features exposed to the web page as window.Native */
public class NativeBridge {

    private final Activity act;
    private final WebView web;
    private SpeechRecognizer recognizer;
    private TextToSpeech tts;
    private boolean ttsReady = false;

    public NativeBridge(Activity activity, WebView webView) {
        this.act = activity;
        this.web = webView;
        tts = new TextToSpeech(act, status -> ttsReady = (status == TextToSpeech.SUCCESS));
    }

    private void js(final String code) {
        act.runOnUiThread(() -> web.evaluateJavascript(code, null));
    }

    @JavascriptInterface
    public void vibrate(int ms) {
        Vibrator v = (Vibrator) act.getSystemService(Context.VIBRATOR_SERVICE);
        if (v != null && v.hasVibrator()) {
            v.vibrate(VibrationEffect.createOneShot(ms, VibrationEffect.DEFAULT_AMPLITUDE));
        }
    }

    @JavascriptInterface
    public void keepScreenOn(final boolean on) {
        act.runOnUiThread(() -> {
            if (on) act.getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
            else act.getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        });
    }

    @JavascriptInterface
    public void speak(final String text, final String lang) {
        if (!ttsReady) return;
        String[] parts = lang.split("-");
        Locale loc = parts.length == 2 ? new Locale(parts[0], parts[1]) : new Locale(lang);
        tts.setLanguage(loc);
        tts.speak(text, TextToSpeech.QUEUE_FLUSH, null, "jim");
    }

    @JavascriptInterface
    public void open(final String url) {
        act.runOnUiThread(() -> {
            try {
                act.startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(url)));
            } catch (Exception ignored) { }
        });
    }

    @JavascriptInterface
    public void call(final String number) {
        act.runOnUiThread(() -> {
            try {
                Uri uri = Uri.parse("tel:" + number);
                if (act.checkSelfPermission(Manifest.permission.CALL_PHONE) == PackageManager.PERMISSION_GRANTED) {
                    act.startActivity(new Intent(Intent.ACTION_CALL, uri));
                } else {
                    act.startActivity(new Intent(Intent.ACTION_DIAL, uri));
                }
            } catch (Exception ignored) { }
        });
    }

    /** Starts one speech recognition. Result is sent to window.onNativeSpeech(text). */
    @JavascriptInterface
    public void listen(final String lang) {
        act.runOnUiThread(() -> {
            if (!SpeechRecognizer.isRecognitionAvailable(act)) {
                js("window.onNativeSpeech && window.onNativeSpeech('')");
                return;
            }
            if (recognizer == null) recognizer = SpeechRecognizer.createSpeechRecognizer(act);
            recognizer.setRecognitionListener(new RecognitionListener() {
                @Override public void onResults(Bundle results) {
                    ArrayList<String> list = results.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION);
                    String text = (list != null && !list.isEmpty()) ? list.get(0) : "";
                    js("window.onNativeSpeech && window.onNativeSpeech(" + JSONObject.quote(text) + ")");
                }
                @Override public void onError(int error) {
                    js("window.onNativeSpeech && window.onNativeSpeech('')");
                }
                @Override public void onReadyForSpeech(Bundle params) { }
                @Override public void onBeginningOfSpeech() { }
                @Override public void onRmsChanged(float rmsdB) { }
                @Override public void onBufferReceived(byte[] buffer) { }
                @Override public void onEndOfSpeech() { }
                @Override public void onPartialResults(Bundle partialResults) { }
                @Override public void onEvent(int eventType, Bundle params) { }
            });
            Intent i = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
            i.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);
            i.putExtra(RecognizerIntent.EXTRA_LANGUAGE, lang);
            i.putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, false);
            recognizer.startListening(i);
        });
    }
}
