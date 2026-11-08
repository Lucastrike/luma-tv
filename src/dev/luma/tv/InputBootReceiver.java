package dev.luma.tv;

import android.content.*;

/** Restores only the user's explicitly enabled native shortcut after boot or an app update. */
public final class InputBootReceiver extends BroadcastReceiver {
    @Override public void onReceive(Context context,Intent intent){
        if(Intent.ACTION_BOOT_COMPLETED.equals(intent.getAction())||Intent.ACTION_MY_PACKAGE_REPLACED.equals(intent.getAction()))
            NativeInputService.restore(context);
    }
}
