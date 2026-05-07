package com.ws.dyfire;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.content.Context;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;

import java.lang.ref.WeakReference;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

import de.robv.android.xposed.IXposedHookLoadPackage;
import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;
import de.robv.android.xposed.callbacks.XC_LoadPackage;

public class MainHook implements IXposedHookLoadPackage {

    private static final String TARGET_PKG    = "com.ss.android.ugc.aweme";
    private static final String SENDER_CLASS  = "com.bytedance.ies.im.core.send.MessageSenderImpl";
    private static final String MESSAGE_CLASS = "com.bytedance.im.core.model.Message";
    private static final String SDK_MGR_CLASS = "com.bytedance.ies.im.core.sdk.SDKManager";

    private static Context appContext;
    private static final Map<String, WeakReference<Object>> templates = new HashMap<>();

    @Override
    public void handleLoadPackage(XC_LoadPackage.LoadPackageParam lpparam) throws Throwable {
        if (!TARGET_PKG.equals(lpparam.packageName)) return;

        // ★ 只在主进程工作，过滤掉 minigame、push 等子进程
        String pn = lpparam.processName;
        if (pn != null && !pn.equals(TARGET_PKG)) {
            XposedBridge.log("[DyFire] 跳过子进程: " + pn);
            return;
        }

        XposedBridge.log("[DyFire] v3 已注入抖音主进程");

        XposedHelpers.findAndHookMethod("android.app.Application", lpparam.classLoader,
            "onCreate", new XC_MethodHook() {
                @Override
                protected void afterHookedMethod(MethodHookParam param) {
                    appContext = (Context) param.thisObject;
                    XposedBridge.log("[DyFire] Context 已获取，5秒后检查");
                    new Handler(Looper.getMainLooper()).postDelayed(
                        () -> checkAndSendToday(lpparam.classLoader), 5000);
                }
            });

        hookSender(lpparam.classLoader);
    }

    private void hookSender(ClassLoader cl) {
        try {
            Class<?> cls = cl.loadClass(SENDER_CLASS);
            int n = 0;
            for (Method m : cls.getDeclaredMethods()) {
                Class<?>[] pts = m.getParameterTypes();
                if (pts.length < 1 || !MESSAGE_CLASS.equals(pts[0].getName())) continue;
                XposedBridge.hookMethod(m, new XC_MethodHook() {
                    @Override
                    protected void beforeHookedMethod(MethodHookParam param) {
                        onMessageSend(param.args[0]);
                    }
                });
                n++;
            }
            XposedBridge.log("[DyFire] Hook 了 " + n + " 个发送方法");
        } catch (Exception e) {
            XposedBridge.log("[DyFire] hookSender 失败: " + e);
        }
    }

    private void onMessageSend(Object msg) {
        if (msg == null || appContext == null) return;
        try {
            String cid = (String) XposedHelpers.callMethod(msg, "getConversationId");
            if (cid == null || cid.isEmpty()) return;
            templates.put(cid, new WeakReference<>(msg));

            List<String> friends = Config.getFriends();
            if (!friends.contains(cid)) {
                Config.addFriend(cid);
                int total = Config.getFriends().size();
                showNotif("DyFire ✅ 好友已记录", "已记录 " + total + " 位好友");
                XposedBridge.log("[DyFire] 新好友已记录 cid=" + cid + " 共" + total + "位");
            }
        } catch (Exception e) {
            XposedBridge.log("[DyFire] onMessageSend 异常: " + e);
        }
    }

    private void checkAndSendToday(ClassLoader cl) {
        if (appContext == null) return;

        // 检查手动触发（1分钟内有效）
        long triggerTime = Config.getTriggerTime();
        boolean manual = triggerTime > 0 &&
            (System.currentTimeMillis() - triggerTime) < 60_000;

        if (!manual) {
            int sendHour = Config.getSendHour();
            int curHour  = java.util.Calendar.getInstance().get(java.util.Calendar.HOUR_OF_DAY);
            if (curHour < sendHour) {
                XposedBridge.log("[DyFire] 未到时间（当前" + curHour + "点，设定" + sendHour + "点）");
                return;
            }
            String today = new SimpleDateFormat("yyyyMMdd", Locale.getDefault()).format(new Date());
            if (today.equals(Config.getLastSent())) {
                XposedBridge.log("[DyFire] 今日已发送，跳过");
                return;
            }
        } else {
            XposedBridge.log("[DyFire] 手动触发");
            Config.clearTrigger();
        }

        List<String> friends = Config.getFriends();
        if (friends.isEmpty()) {
            showNotif("DyFire 提示", "还没有记录好友，请先手动给朋友发一条消息");
            return;
        }

        String sendText = Config.getSendMessage();
        XposedBridge.log("[DyFire] 开始发送，共 " + friends.size() + " 位，内容：" + sendText);

        new Thread(() -> {
            int ok = 0, fail = 0;
            for (String cid : friends) {
                try {
                    if (sendMessage(cl, cid, sendText)) ok++;
                    else fail++;
                    Thread.sleep(2500);
                } catch (Exception e) {
                    fail++;
                    XposedBridge.log("[DyFire] 发送失败 cid=" + cid + ": " + e);
                }
            }
            final int fo = ok, ff = fail;
            new Handler(Looper.getMainLooper()).post(() -> {
                String today = new SimpleDateFormat("yyyyMMdd", Locale.getDefault()).format(new Date());
                Config.setLastSent(today);
                showNotif("DyFire 🔥 完成", "成功 " + fo + " 位" + (ff > 0 ? "，失败 " + ff + " 位" : ""));
            });
        }).start();
    }

    private boolean sendMessage(ClassLoader cl, String cid, String text) {
        WeakReference<Object> ref = templates.get(cid);
        Object tmpl = ref != null ? ref.get() : null;
        if (tmpl != null) {
            try { return sendViaTemplate(cl, tmpl, text); } catch (Exception ignored) {}
        }
        try { return sendViaConstruct(cl, cid, text); } catch (Exception e) {
            XposedBridge.log("[DyFire] 构造失败: " + e); return false;
        }
    }

    private boolean sendViaTemplate(ClassLoader cl, Object tmpl, String text) throws Exception {
        Class<?> mc = cl.loadClass(MESSAGE_CLASS);
        try { XposedHelpers.callMethod(tmpl, "setContent", text); } catch (Throwable t) {}
        try { XposedHelpers.callMethod(tmpl, "setUuid", UUID.randomUUID().toString()); } catch (Throwable t) {}
        for (Field f : getAllFields(mc)) {
            f.setAccessible(true);
            String fn = f.getName().toLowerCase();
            if (f.getType() == String.class) {
                if (fn.contains("content") && !fn.contains("obj")) f.set(tmpl, text);
                if (fn.equals("uuid") || fn.contains("msgid")) f.set(tmpl, UUID.randomUUID().toString());
            }
        }
        callSDKSend(cl, tmpl);
        return true;
    }

    private boolean sendViaConstruct(ClassLoader cl, String cid, String text) throws Exception {
        Class<?> mc = cl.loadClass(MESSAGE_CLASS);
        Object msg = XposedHelpers.newInstance(mc);
        try { XposedHelpers.callMethod(msg, "setConversationId", cid); } catch (Throwable t) {}
        try { XposedHelpers.callMethod(msg, "setContent", text); } catch (Throwable t) {}
        try { XposedHelpers.callMethod(msg, "setMsgType", 1); } catch (Throwable t) {}
        try { XposedHelpers.callMethod(msg, "setUuid", UUID.randomUUID().toString()); } catch (Throwable t) {}
        for (Field f : getAllFields(mc)) {
            f.setAccessible(true);
            String fn = f.getName().toLowerCase();
            if (f.getType() == String.class) {
                if (fn.equals("conversationid") || fn.equals("cid")) f.set(msg, cid);
                if (fn.contains("content") && !fn.contains("obj")) f.set(msg, text);
                if (fn.equals("uuid") || fn.contains("msgid")) f.set(msg, UUID.randomUUID().toString());
            }
            if (f.getType() == int.class && (fn.contains("msgtype") || fn.equals("type"))) f.set(msg, 1);
        }
        callSDKSend(cl, msg);
        return true;
    }

    private void callSDKSend(ClassLoader cl, Object msg) throws Exception {
        Class<?> sdkCls = cl.loadClass(SDK_MGR_CLASS);
        Object mgr    = XposedHelpers.getStaticObjectField(sdkCls, "INSTANCE");
        Object client = XposedHelpers.callMethod(mgr, "getImSdkClient");
        Object util   = XposedHelpers.callMethod(client, "getIIMUtilService");
        Object mu     = XposedHelpers.callMethod(util, "getMessageUtils");
        XposedHelpers.callMethod(mu, "sendMessage", msg, null, null);
    }

    private List<Field> getAllFields(Class<?> cls) {
        List<Field> list = new ArrayList<>();
        while (cls != null && cls != Object.class) {
            for (Field f : cls.getDeclaredFields()) list.add(f);
            cls = cls.getSuperclass();
        }
        return list;
    }

    private void showNotif(String title, String text) {
        if (appContext == null) return;
        try {
            NotificationManager nm = (NotificationManager)
                appContext.getSystemService(Context.NOTIFICATION_SERVICE);
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
                nm.createNotificationChannel(new NotificationChannel(
                    "dyfire", "DyFire", NotificationManager.IMPORTANCE_DEFAULT));
            Notification n = new Notification.Builder(appContext, "dyfire")
                .setSmallIcon(android.R.drawable.ic_dialog_info)
                .setContentTitle(title).setContentText(text).setAutoCancel(true).build();
            nm.notify((int)(System.currentTimeMillis()/1000), n);
        } catch (Exception e) {
            XposedBridge.log("[DyFire] 通知失败: " + e);
        }
    }
}
