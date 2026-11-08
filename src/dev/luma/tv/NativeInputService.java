package dev.luma.tv;

import android.app.*;
import android.content.*;
import android.content.pm.*;
import android.os.*;
import android.util.Log;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.*;

/** Optional Xiaomi Input bridge. Observes native log records; never filters or synthesizes keys. */
public final class NativeInputService extends Service {
    static final String ENABLE="dev.luma.tv.action.ENABLE_NATIVE_INPUT";
    static final String DISABLE="dev.luma.tv.action.DISABLE_NATIVE_INPUT";
    static final String SETTINGS="dev.luma.tv.action.INPUT_SETTINGS";
    private static final String TAG="LumaInputBridge",CHANNEL="input_shortcut";
    private static final String NATIVE_PACKAGE="com.xiaomi.android.tvsetup.partnercustomizer";
    private static final int NOTIFICATION=178;
    // Android 9 adds the READ_LOGS group when the app process starts, not to an existing process.
    private static final boolean LOG_GROUP=hasLogGroup();
    private final Handler main=new Handler(Looper.getMainLooper());
    private volatile boolean running,ready;
    private volatile java.lang.Process reader;
    private volatile String status="stopped";
    private volatile int signals,launches,restarts;
    private volatile long lastInput;
    private Thread worker;

    static boolean supported(Context context){
        if(Build.VERSION.SDK_INT!=28||!"MiTV-MSSP3".equals(Build.MODEL))return false;
        try{
            ApplicationInfo app=context.getPackageManager().getApplicationInfo(NATIVE_PACKAGE,0);
            return app.uid==android.os.Process.SYSTEM_UID&&(app.flags&ApplicationInfo.FLAG_SYSTEM)!=0;
        }catch(PackageManager.NameNotFoundException missing){return false;}
    }
    private static boolean hasLogGroup(){
        try(BufferedReader status=new BufferedReader(new FileReader("/proc/self/status"))){
            String line;
            while((line=status.readLine())!=null)if(line.startsWith("Groups:")){
                for(String group:line.substring(7).trim().split("\\s+"))if(group.equals("1007"))return true;
                return false;
            }
        }catch(IOException unavailable){}
        return false;
    }
    static boolean requiresRestart(Context context){return context.checkSelfPermission(android.Manifest.permission.READ_LOGS)==PackageManager.PERMISSION_GRANTED&&!LOG_GROUP;}
    static boolean permission(Context context){return context.checkSelfPermission(android.Manifest.permission.READ_LOGS)==PackageManager.PERMISSION_GRANTED&&LOG_GROUP;}
    static String summary(Context context){return requiresRestart(context)?"Restart Luma":enabled(context)&&permission(context)?"Enabled":"Set up";}
    static boolean enabled(Context context){return context.getSharedPreferences("input_shortcut",MODE_PRIVATE).getBoolean("enabled",false);}
    static void restore(Context context){
        if(enabled(context)&&supported(context)&&permission(context)&&!InputKeyService.enabled(context))
            context.startForegroundService(new Intent(context,NativeInputService.class));
    }
    static boolean setEnabled(Context context,boolean enable){
        if(enable&&(!supported(context)||!permission(context)||InputKeyService.enabled(context)))return false;
        Intent intent=new Intent(context,NativeInputService.class).setAction(enable?ENABLE:DISABLE);
        if(enable)context.startForegroundService(intent);else context.startService(intent);
        return true;
    }
    @Override public int onStartCommand(Intent intent,int flags,int startId){
        String action=intent==null?null:intent.getAction();
        if(DISABLE.equals(action)){
            getSharedPreferences("input_shortcut",MODE_PRIVATE).edit().putBoolean("enabled",false).commit();
            stopSelf();return START_NOT_STICKY;
        }
        if(ENABLE.equals(action)&&supported(this)&&permission(this)&&!InputKeyService.enabled(this))
            getSharedPreferences("input_shortcut",MODE_PRIVATE).edit().putBoolean("enabled",true).commit();
        if(!enabled(this)||!supported(this)||!permission(this)||InputKeyService.enabled(this)){stopSelf();return START_NOT_STICKY;}
        foreground();
        if(!running){running=true;worker=new Thread(this::observe,"Luma native Input");worker.start();}
        return START_STICKY;
    }
    private void foreground(){
        NotificationManager manager=getSystemService(NotificationManager.class);
        NotificationChannel channel=new NotificationChannel(CHANNEL,"Remote Input button",NotificationManager.IMPORTANCE_LOW);
        channel.setShowBadge(false);manager.createNotificationChannel(channel);
        PendingIntent settings=PendingIntent.getActivity(this,0,new Intent(this,MainActivity.class).setAction(SETTINGS),PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE);
        PendingIntent disable=PendingIntent.getService(this,0,new Intent(this,NativeInputService.class).setAction(DISABLE),PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE);
        startForeground(NOTIFICATION,new Notification.Builder(this,CHANNEL).setSmallIcon(R.drawable.app_icon)
            .setContentTitle("Luma Input button").setContentText("Remote shortcut is enabled")
            .setContentIntent(settings).setOngoing(true).setOnlyAlertOnce(true).setCategory(Notification.CATEGORY_SERVICE)
            .addAction(new Notification.Action.Builder(null,"Turn off",disable).build()).build());
    }
    private void observe(){
        long delay=1000;
        while(running&&!Thread.currentThread().isInterrupted()){
            java.lang.Process process=null;Runnable watchdog=null;long started=SystemClock.elapsedRealtime();
            ready=false;status="starting";
            try{
                String token=UUID.randomUUID().toString();
                NativeInputSignal signal=new NativeInputSignal(android.os.Process.SYSTEM_UID,android.os.Process.myUid(),token);
                String since=new SimpleDateFormat("MM-dd HH:mm:ss.SSS",Locale.US).format(new Date(System.currentTimeMillis()-2000));
                process=new ProcessBuilder("logcat","-b","main","-v","threadtime","-v","uid","-T",since,"GlobalKey:V",TAG+":I","*:S").redirectErrorStream(true).start();
                reader=process;final java.lang.Process current=process;
                watchdog=()->{if(running&&reader==current&&!ready)current.destroy();};
                main.postDelayed(watchdog,8000);
                Log.i(TAG,"ready "+token);
                try(BufferedReader output=new BufferedReader(new InputStreamReader(process.getInputStream(),StandardCharsets.UTF_8))){
                    String line;
                    while(running&&(line=output.readLine())!=null){
                        boolean input=signal.accept(line);
                        if(signal.ready()){ready=true;status="ready";}
                        if(input){signals++;lastInput=SystemClock.elapsedRealtime();main.post(this::openInputs);}
                    }
                }
            }catch(IOException unavailable){status="reader unavailable";}
            finally{
                if(watchdog!=null)main.removeCallbacks(watchdog);
                ready=false;
                if(process!=null)process.destroy();
                if(reader==process)reader=null;
            }
            if(!running)break;
            status="reconnecting";restarts++;
            if(SystemClock.elapsedRealtime()-started>60000)delay=1000;
            try{Thread.sleep(delay);}catch(InterruptedException stopped){Thread.currentThread().interrupt();break;}
            delay=Math.min(delay*2,60000);
        }
    }
    private void openInputs(){
        if(!running||!enabled(this)||!permission(this)||!getSystemService(PowerManager.class).isInteractive())return;
        try{
            startActivity(new Intent(this,InputsActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK|Intent.FLAG_ACTIVITY_SINGLE_TOP));launches++;
        }catch(ActivityNotFoundException|SecurityException unavailable){status="selector unavailable";}
    }
    @Override public void onDestroy(){
        running=false;ready=false;status="stopped";
        if(reader!=null)reader.destroy();
        if(worker!=null)worker.interrupt();
        main.removeCallbacksAndMessages(null);stopForeground(true);super.onDestroy();
    }
    @Override public IBinder onBind(Intent intent){return null;}
    @Override protected void dump(FileDescriptor fd,PrintWriter out,String[] args){
        out.println("enabled="+enabled(this)+" permission="+permission(this)+" logGroup="+LOG_GROUP+" ready="+ready+" status="+status);
        out.println("nativeSignals="+signals+" selectorLaunches="+launches+" readerRestarts="+restarts);
        out.println("lastInputElapsedMs="+lastInput+" accessibilityFilter="+InputKeyService.enabled(this));
    }
}
