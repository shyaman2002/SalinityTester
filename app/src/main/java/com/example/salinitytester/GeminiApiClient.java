package com.example.salinitytester;

import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import okhttp3.*;
import org.json.JSONArray;
import org.json.JSONObject;
import java.io.IOException;

public class GeminiApiClient {
    // REPLACE THIS WITH YOUR KEY
    private static final String API_KEY = "AIzaSyD3-9lMICyB5alsWtNVwoSR659wIGmVa4Y";
    private static final String URL = "https://generativelanguage.googleapis.com/v1beta/models/gemini-2.5-flash-lite:generateContent?key=" + API_KEY;

    public interface GeminiCallback {
        void onSuccess(String result);
        void onError(String error);
    }

    public static void analyzeData(String riverName, String jsonData, GeminiCallback callback) {
        OkHttpClient client = new OkHttpClient();

        String prompt = "Analyze this water quality data for " + riverName + ". " +
                "Data format is [TDS, Height]. Data: " + jsonData + ". " +
                "Is it safe? (Safe < 500 TDS). Keep answer under 50 words.";

        try {
            // Construct JSON securely
            JSONObject part = new JSONObject();
            part.put("text", prompt);

            JSONArray parts = new JSONArray();
            parts.put(part);

            JSONObject content = new JSONObject();
            content.put("parts", parts);

            JSONArray contents = new JSONArray();
            contents.put(content);

            JSONObject jsonBody = new JSONObject();
            jsonBody.put("contents", contents);

            RequestBody body = RequestBody.create(
                    jsonBody.toString(),
                    MediaType.get("application/json; charset=utf-8")
            );

            Request request = new Request.Builder().url(URL).post(body).build();

            client.newCall(request).enqueue(new Callback() {
                @Override
                public void onFailure(Call call, IOException e) {
                    new Handler(Looper.getMainLooper()).post(() -> callback.onError("Network: " + e.getMessage()));
                }

                @Override
                public void onResponse(Call call, Response response) throws IOException {
                    String respStr = response.body().string(); // Read response once

                    if (response.isSuccessful()) {
                        try {
                            JSONObject jsonResponse = new JSONObject(respStr);
                            String text = jsonResponse.getJSONArray("candidates")
                                    .getJSONObject(0).getJSONObject("content")
                                    .getJSONArray("parts").getJSONObject(0).getString("text");
                            new Handler(Looper.getMainLooper()).post(() -> callback.onSuccess(text));
                        } catch (Exception e) {
                            new Handler(Looper.getMainLooper()).post(() -> callback.onError("Parse Error"));
                        }
                    } else {
                        // Log the full error to Android Studio Logcat
                        Log.e("GEMINI_ERROR", "Code: " + response.code() + " Body: " + respStr);

                        // Show the user the code (400) and check Logcat for details
                        new Handler(Looper.getMainLooper()).post(() ->
                                callback.onError("API Error: " + response.code() + " Check Logcat"));
                    }
                }
            });
        } catch (Exception e) {
            callback.onError(e.getMessage());
        }
    }
}