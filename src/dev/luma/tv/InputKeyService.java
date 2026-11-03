package dev.luma.tv;

import android.accessibilityservice.AccessibilityService;
import android.content.*;
import android.provider.Settings;
import android.view.KeyEvent;
import android.view.accessibility.AccessibilityEvent;

/** Optional remote shortcut. Android must explicitly enable this service before it receives keys.
 * No window access or accessibility events are requested. Only TV_INPUT is consumed. */
public class InputKeyService extends AccessibilityService {
    static final String SHOW_INPUTS="dev.luma.tv.action.SHOW_INPUTS";
    private boolean handling;

    static boolean enabled(Context context){
        String services=Settings.Secure.getString(context.getContentResolver(),Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES);
        if(services==null)return false;
        ComponentName self=new ComponentName(context,InputKeyService.class);
        for(String value:services.split(":"))if(self.equals(ComponentName.unflattenFromString(value)))return true;
        return false;
    }
    @Override protected boolean onKeyEvent(KeyEvent event){
        if(event.getKeyCode()!=KeyEvent.KEYCODE_TV_INPUT)return false;
        if(event.getAction()==KeyEvent.ACTION_DOWN&&event.getRepeatCount()==0){
            handling=false;
            try{openInputs();handling=true;}catch(ActivityNotFoundException|SecurityException ignored){}
        }
        boolean consumed=handling;
        if(event.getAction()==KeyEvent.ACTION_UP)handling=false;
        return consumed;
    }
    void openInputs(){startActivity(new Intent(this,MainActivity.class).setAction(SHOW_INPUTS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK|Intent.FLAG_ACTIVITY_SINGLE_TOP));}
    @Override public void onAccessibilityEvent(AccessibilityEvent event){}
    @Override public void onInterrupt(){}
}
