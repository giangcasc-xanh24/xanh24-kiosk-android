package vn.xanh24.kiosk;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

/** Mở kiosk khi thiết bị khởi động xong (bổ sung cho cách đặt ứng dụng làm màn hình chính / Device Owner). */
public class BootReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context context, Intent intent) {
        if (intent == null) return;
        String a = intent.getAction();
        if (Intent.ACTION_BOOT_COMPLETED.equals(a) || "android.intent.action.LOCKED_BOOT_COMPLETED".equals(a) || Intent.ACTION_MY_PACKAGE_REPLACED.equals(a)) {
            Intent i = new Intent(context, MainActivity.class);
            i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            try { context.startActivity(i); } catch (Exception ignored) { }
        }
    }
}
