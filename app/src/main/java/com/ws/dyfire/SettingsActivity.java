package com.ws.dyfire;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.os.Bundle;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import java.util.List;

public class SettingsActivity extends Activity {

    private LinearLayout llFriends;
    private TextView tvFriendCount, tvStatus;
    private EditText etHour, etMessage;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_settings);

        etHour        = findViewById(R.id.etHour);
        etMessage     = findViewById(R.id.etMessage);
        llFriends     = findViewById(R.id.llFriends);
        tvFriendCount = findViewById(R.id.tvFriendCount);
        tvStatus      = findViewById(R.id.tvStatus);

        etHour.setText(String.valueOf(Config.getSendHour()));
        etMessage.setText(Config.getSendMessage());

        loadFriendList();

        findViewById(R.id.btnSave).setOnClickListener(v -> saveSettings());
        findViewById(R.id.btnClear).setOnClickListener(v -> confirmClear());
        findViewById(R.id.btnTrigger).setOnClickListener(v -> manualTrigger());
    }

    private void saveSettings() {
        String hourStr = etHour.getText().toString().trim();
        String msg     = etMessage.getText().toString().trim();
        if (hourStr.isEmpty()) { toast("请填写发送时间"); return; }
        int hour = Integer.parseInt(hourStr);
        if (hour < 0 || hour > 23) { toast("时间请填 0-23 之间"); return; }
        if (msg.isEmpty()) { toast("请填写发送内容"); return; }
        Config.setSendHour(hour);
        Config.setSendMessage(msg);
        tvStatus.setText("✅ 已保存！每天 " + hour + " 点后打开抖音自动续火花");
        toast("保存成功");
    }

    private void loadFriendList() {
        List<String> friends = Config.getFriends();
        llFriends.removeAllViews();
        tvFriendCount.setText("共 " + friends.size() + " 位好友");

        String lastSent = Config.getLastSent();

        for (String cid : friends) {
            LinearLayout row = new LinearLayout(this);
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setPadding(16, 12, 16, 12);
            row.setBackgroundColor(0xFFFFFFFF);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
            lp.setMargins(0, 0, 0, 8);
            row.setLayoutParams(lp);

            TextView tv = new TextView(this);
            String shortCid = cid.length() > 24 ? cid.substring(0, 24) + "…" : cid;
            tv.setText("🔥 " + shortCid);
            tv.setTextSize(13);
            tv.setTextColor(0xFF333333);
            tv.setLayoutParams(new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
            row.addView(tv);

            Button del = new Button(this);
            del.setText("删除");
            del.setTextSize(12);
            del.setTextColor(0xFFFFFFFF);
            del.getBackground().setTint(0xFFCC0000);
            del.setPadding(16, 4, 16, 4);
            del.setOnClickListener(v -> {
                Config.removeFriend(cid);
                loadFriendList();
                toast("已删除");
            });
            row.addView(del);
            llFriends.addView(row);
        }

        TextView tvLast = new TextView(this);
        tvLast.setText("上次发送日期：" + (lastSent.isEmpty() ? "未发送过" : lastSent));
        tvLast.setTextSize(12);
        tvLast.setTextColor(0xFF999999);
        tvLast.setPadding(0, 8, 0, 0);
        llFriends.addView(tvLast);
    }

    private void confirmClear() {
        new AlertDialog.Builder(this)
            .setTitle("确认清空")
            .setMessage("清空后需要重新手动发消息才能记录，确定吗？")
            .setPositiveButton("确定", (d, w) -> {
                Config.clearFriends();
                loadFriendList();
                toast("已清空");
            })
            .setNegativeButton("取消", null)
            .show();
    }

    private void manualTrigger() {
        Config.setLastSent("");
        Config.setTriggerNow();
        tvStatus.setText("✅ 已触发！请立即打开抖音，稍等片刻会收到通知");
        toast("触发成功，请打开抖音");
    }

    private void toast(String msg) {
        Toast.makeText(this, msg, Toast.LENGTH_SHORT).show();
    }

    @Override
    protected void onResume() {
        super.onResume();
        loadFriendList();
        etHour.setText(String.valueOf(Config.getSendHour()));
        etMessage.setText(Config.getSendMessage());
    }
}
