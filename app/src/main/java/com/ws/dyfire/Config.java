package com.ws.dyfire;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.util.ArrayList;
import java.util.List;

/**
 * 用 /sdcard/dyfire/config.json 做跨进程共享存储
 * 主进程（抖音）和设置 App 都读写同一个文件
 */
public class Config {

    private static final String DIR  = "/sdcard/dyfire/";
    private static final String PATH = DIR + "config.json";

    // JSON key
    private static final String K_HOUR      = "send_hour";
    private static final String K_MESSAGE   = "send_message";
    private static final String K_FRIENDS   = "friends";
    private static final String K_LAST_SENT = "last_sent";
    private static final String K_TRIGGER   = "manual_trigger";

    // ── 读 ────────────────────────────────────────
    public static JSONObject read() {
        try {
            File f = new File(PATH);
            if (!f.exists()) return defaultJson();
            FileReader fr = new FileReader(f);
            StringBuilder sb = new StringBuilder();
            char[] buf = new char[4096];
            int n;
            while ((n = fr.read(buf)) != -1) sb.append(buf, 0, n);
            fr.close();
            return new JSONObject(sb.toString());
        } catch (Exception e) {
            return defaultJson();
        }
    }

    // ── 写 ────────────────────────────────────────
    public static void write(JSONObject json) {
        try {
            new File(DIR).mkdirs();
            FileWriter fw = new FileWriter(PATH);
            fw.write(json.toString(2));
            fw.close();
        } catch (Exception ignored) {}
    }

    // ── 快捷方法 ──────────────────────────────────
    public static int getSendHour()      { return read().optInt(K_HOUR, 9); }
    public static String getSendMessage(){ return read().optString(K_MESSAGE, "🔥"); }
    public static String getLastSent()   { return read().optString(K_LAST_SENT, ""); }
    public static long getTriggerTime()  { return read().optLong(K_TRIGGER, 0); }

    public static List<String> getFriends() {
        List<String> list = new ArrayList<>();
        try {
            JSONArray arr = read().optJSONArray(K_FRIENDS);
            if (arr != null)
                for (int i = 0; i < arr.length(); i++) list.add(arr.getString(i));
        } catch (Exception ignored) {}
        return list;
    }

    public static void setSendHour(int hour) {
        JSONObject j = read(); try { j.put(K_HOUR, hour); } catch (Exception ignored) {}
        write(j);
    }

    public static void setSendMessage(String msg) {
        JSONObject j = read(); try { j.put(K_MESSAGE, msg); } catch (Exception ignored) {}
        write(j);
    }

    public static void setLastSent(String date) {
        JSONObject j = read(); try { j.put(K_LAST_SENT, date); } catch (Exception ignored) {}
        write(j);
    }

    public static void setTriggerNow() {
        JSONObject j = read();
        try { j.put(K_TRIGGER, System.currentTimeMillis()); } catch (Exception ignored) {}
        write(j);
    }

    public static void clearTrigger() {
        JSONObject j = read(); try { j.put(K_TRIGGER, 0); } catch (Exception ignored) {}
        write(j);
    }

    public static void addFriend(String cid) {
        JSONObject j = read();
        try {
            JSONArray arr = j.optJSONArray(K_FRIENDS);
            if (arr == null) arr = new JSONArray();
            for (int i = 0; i < arr.length(); i++)
                if (cid.equals(arr.getString(i))) return; // 已存在
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
            JSONArray newArr = new JSONArray();
            for (int i = 0; i < arr.length(); i++)
                if (!cid.equals(arr.getString(i))) newArr.put(arr.getString(i));
            j.put(K_FRIENDS, newArr);
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
            j.put(K_HOUR, 9);
            j.put(K_MESSAGE, "🔥");
            j.put(K_FRIENDS, new JSONArray());
            j.put(K_LAST_SENT, "");
            j.put(K_TRIGGER, 0);
            return j;
        } catch (Exception e) { return new JSONObject(); }
    }
}
