package com.example.nearbymemories.util;

import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import org.json.JSONObject;

public class WeatherClient {
    private static final OkHttpClient client = new OkHttpClient();

    public static String getWeatherSummary(double lat, double lng) {
        String url = "https://api.open-meteo.com/v1/forecast?latitude=" + lat +
                "&longitude=" + lng + "&current=temperature_2m,weather_code";
        Request req = new Request.Builder().url(url).build();
        try (Response resp = client.newCall(req).execute()) {
            if (!resp.isSuccessful()) return null;
            String body = resp.body() != null ? resp.body().string() : null;
            if (body == null) return null;
            JSONObject json = new JSONObject(body);
            JSONObject cur = json.optJSONObject("current");
            if (cur == null) return null;
            double temp = cur.optDouble("temperature_2m", Double.NaN);
            int code = cur.optInt("weather_code", -1);
            String text = codeToText(code);
            if (!Double.isNaN(temp)) return String.format("%.1f°C, %s", temp, text);
            return text;
        } catch (Exception e) {
            return null;
        }
    }

    private static String codeToText(int code) {
        switch (code) {
            case 0: return "Clear sky";
            case 1:
            case 2: return "Mostly clear";
            case 3: return "Cloudy";
            case 45:
            case 48: return "Fog";
            case 51:
            case 53:
            case 55: return "Drizzle";
            case 61:
            case 63:
            case 65: return "Rain";
            case 71:
            case 73:
            case 75: return "Snow";
            case 95: return "Thunderstorm";
            default: return "Weather code " + code;
        }
    }
}
