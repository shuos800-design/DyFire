package com.ws.dyfire;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.List;

public class Config {

    private static final String PATH = "/sdcard/dyfire/config.json";
    private static final String DIR  = "/sdcard/dyfire";

    private static final String K_HOUR      = "send_hour";
    private static final String K_MESSAGE   = "send_message";
    private static final String K_FRIENDS   = "friends";
    private static final String K_LAST_SENT = "last_sent";
    private static final String K_TRIGGER   = "manual_trigger";

    // 直接用 FileReader 读（抖音进程有 /sdcard 读权限）
    private static String readRaw() {
        try {
            File f = new File(PATH);
            if (!f.exists()) return "";
            BufferedReader br = new BufferedReader(new FileReader(f));
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = br.readLine()) != null) sb.append(line);
            br.close();
            return sb.toString();
        } catch (Exception e) { return ""; }
    }

    // 写文件：抖音进程用 FileWriter，设置 App 用 su
    private static void writeRaw(String json) {
        // 先尝试直接写
        try {
            new File(DIR).mkdirs();
            FileWriter fw = new FileWriter(PATH);
            fw.write(json);
            fw.close();
            return;
        } catch (Exception ignored) {}
        // 直接写失败则用 su
        try {
            String escaped = json.replace("'", "'\\''");
            String cmd = "mkdir -p " + DIR + " && printf '%s' '" + escaped + "' > " + PATH + " && chmod 666 " + PATH;
            Process p = Runtime.getRuntime().exec(new String[]{"su", "-c", cmd});
            p.waitFor();
        } catch (Exception ignored) {}
    }

    public static JSONObject read() {
        try {
            String raw = readRaw();
            if (raw.isEmpty()) return defaultJson();
            return new JSONObject(raw);
        } catch (Exception e) { return defaultJson(); }
    }

    public static void write(JSONObject json) {
        try { writeRaw(json.toString(2)); } catch (Exception ignored) {}
    }

    public static int getSendHour()       { return read().optInt(K_HOUR, 9); }
    public static String getSendMessage() { return read().optString(K_MESSAGE, "🔥"); }
    public static String getLastSent()    { return read().optString(K_LAST_SENT, ""); }
    public static long getTriggerTime()   { return read().optLong(K_TRIGGER, 0); }

    public static List<String> getFriends() {
        List<String> list = new ArrayList<>();
        try {
            JSONArray arr = read().optJSONArray(K_FRIENDS);
            if (arr != null)
                for (int i = 0; i < arr.length(); i++) list.add(arr.getString(i));
        } catch (Exception ignored) {}
        return list;
    }

    public static void setSendHour(int h) {
        JSONObject j = read(); try { j.put(K_HOUR, h); } catch (Exception ignored) {} write(j);
    }
    public static void setSendMessage(String m) {
        JSONObject j = read(); try { j.put(K_MESSAGE, m); } catch (Exception ignored) {} write(j);
    }
    public static void setLastSent(String d) {
        JSONObject j = read(); try { j.put(K_LAST_SENT, d); } catch (Exception ignored) {} write(j);
    }
    public static void setTriggerNow() {
        JSONObject j = read();
        try { j.put(K_TRIGGER, System.currentTimeMillis()); } catch (Exception ignored) {}
        write(j);
    }
    public static void clearTrigger() {
        JSONObject j = read(); try { j.put(K_TRIGGER, 0); } catch (Exception ignored) {} write(j);
    }
    public static void addFriend(String cid) {
        JSONObject j = read();
        try {
            JSONArray arr = j.optJSONArray(K_FRIENDS);
            if (arr == null) arr = new JSONArray();
            for (int i = 0; i < arr.length(); i++)
                if (cid.equals(arr.getString(i))) return;
            arr.put(cid);
            j.put(K_FRIENDS, arr);
            write(j);
        } catch (Exception ignored) {}
    }
    public static void removeFriend(String cid) {
        JSONObject j = read();
        try {
            JSONArray arr = j.optJSONArray(K_FRIENDS);
            if (arr == null) return;
            JSONArray n = new JSONArray();
            for (int i = 0; i < arr.length(); i++)
                if (!cid.equals(arr.getString(i))) n.put(arr.getString(i));
            j.put(K_FRIENDS, n);
            write(j);
        } catch (Exception ignored) {}
    }
    public static void clearFriends() {
        JSONObject j = read();
        try { j.put(K_FRIENDS, new JSONArray()); j.put(K_LAST_SENT, ""); } catch (Exception ignored) {}
        write(j);
    }

    private static JSONObject defaultJson() {
        try {
            JSONObject j = new JSONObject();
            j.put(K_HOUR, 9); j.put(K_MESSAGE, "🔥");
            j.put(K_FRIENDS, new JSONArray());
            j.put(K_LAST_SENT, ""); j.put(K_TRIGGER, 0);
            return j;
        } catch (Exception e) { return new JSONObject(); }
    }
}
