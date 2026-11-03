package dev.luma.tv;

import android.app.*;
import android.content.*;
import android.os.*;
import android.view.*;
import android.widget.*;
import java.lang.reflect.*;
import java.util.*;

/** On-device checks against the real views, TV inputs and provider. Included only with LUMA_TEST=1.
 * Use an isolated LUMA_PACKAGE so tests never edit the installed launcher's preferences. */
public final class LumaRegression extends Instrumentation {
    MainActivity activity;
    int failures;
    interface Check { void run() throws Exception; }
    @Override public void onCreate(Bundle args) { super.onCreate(args);start(); }
    @Override public void onStart() {
        try {
            if(!getTargetContext().getPackageName().endsWith(".qa"))throw new IllegalStateException("Use a .qa package for regression tests");
            activity=(MainActivity)startActivitySync(new Intent().setClassName(getTargetContext(),MainActivity.class.getName()).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
            for(int i=0;i<100&&!((Boolean)field(activity,"loaded"));i++){SystemClock.sleep(100);waitForIdleSync();}
            test("Input handler opens the input selector when the event reaches Luma",()->{
                ui(()->{key(KeyEvent.KEYCODE_TV_INPUT);});
                waitForIdleSync();
                View hdmi=findShown(root(),"HDMI 1");
                require(hdmi!=null&&hdmi.isShown(),"Input did not open visible HDMI choices");
                ui(()->activity.onBackPressed());
            });
            test("Tuner entries do not have duplicate labels",()->{
                HashSet<String> labels=new HashSet<>();
                for(InputsWidget.Input in:InputsWidget.list(activity)){
                    log(in.id+" => "+in.name);
                    if(in.tuner)require(labels.add(in.name),"Duplicate tuner: "+in.name);
                }
            });
            test("Unavailable notification settings offer an actionable fallback",()->{
                ui(()->{
                    try {
                        WidgetBoard.Host host=(WidgetBoard.Host)field(activity,"boardHost");
                        NowPlayingWidget w=new NowPlayingWidget(activity,host);w.setActive(true);
                        View allow=find(w,"Allow access");require(allow!=null,"Expected ungranted listener in QA package");allow.performClick();w.setActive(false);
                    } catch(Exception e){throw new RuntimeException(e);}
                });
                waitForIdleSync();
                require(find(root(),"Enable with ADB")!=null,"No help for missing notification settings");
                ui(()->activity.onBackPressed());
            });
            test("Move stays active across directions and OK saves",()->{
                ui(()->invoke(activity,"showPage",new Class[]{String.class,boolean.class},"apps",true));waitForIdleSync();
                ArrayList<?> tiles=(ArrayList<?>)field(activity,"tiles");
                require(tiles.size()>7,"Need eight installed apps for cross-row move");
                View tile=(View)tiles.get(0);String pkg=((MainActivity.App)field(tile,"app")).pkg;
                ui(()->tile.performLongClick());waitForIdleSync();
                View move=find(root(),"Move");require(move!=null,"App menu has no continuous Move action");
                ui(()->move.performClick());waitForIdleSync();
                ui(()->{key(KeyEvent.KEYCODE_DPAD_RIGHT);key(KeyEvent.KEYCODE_DPAD_DOWN);key(KeyEvent.KEYCODE_DPAD_LEFT);});waitForIdleSync();
                require(field(activity,"movingApp")!=null,"Move mode ended after a direction");
                ui(()->key(KeyEvent.KEYCODE_DPAD_CENTER));waitForIdleSync();
                require(field(activity,"movingApp")==null,"OK did not finish moving");
                List<?> ordered=(List<?>)invoke(activity,"visibleApps",new Class[]{boolean.class},true);
                require(((MainActivity.App)ordered.get(6)).pkg.equals(pkg),"Wrong destination after right/down/left");
                require(activity.getSharedPreferences("luma",0).getString("order","").contains(pkg),"Order was not saved");
                ui(()->invoke(activity,"showPage",new Class[]{String.class,boolean.class},"apps",true));waitForIdleSync();
                List<?> rebuilt=(List<?>)field(activity,"tiles");
                require(((MainActivity.App)field(rebuilt.get(6),"app")).pkg.equals(pkg),"Saved destination was lost on rebuild");
            });
            test("Back cancels moving and keeps the saved order",()->{
                String saved=activity.getSharedPreferences("luma",0).getString("order","");
                View tile=(View)((List<?>)field(activity,"tiles")).get(0);
                ui(()->tile.performLongClick());waitForIdleSync();View move=findShown(root(),"Move");
                require(move!=null,"Move action unavailable");ui(()->move.performClick());waitForIdleSync();
                ui(()->{key(KeyEvent.KEYCODE_DPAD_RIGHT);key(KeyEvent.KEYCODE_DPAD_RIGHT);key(KeyEvent.KEYCODE_BACK);});waitForIdleSync();
                require(field(activity,"movingApp")==null,"Back left moving active");
                require(activity.getSharedPreferences("luma",0).getString("order","").equals(saved),"Back changed saved order");
            });
            test("Live TV and HDMI use resolvable channel intents",()->{
                for(InputsWidget.Input in:InputsWidget.list(activity)){
                    Intent intent=InputsWidget.intent(in);
                    require(intent.resolveActivity(activity.getPackageManager())!=null,"No handler for "+in.name);
                    require(!in.tuner||!intent.getDataString().contains("passthrough"),"Tuner uses passthrough URI");
                }
            });
            test("Moving from Home can go beyond six favourites and returns Home",()->{
                ui(()->invoke(activity,"showPage",new Class[]{String.class,boolean.class},"home",true));waitForIdleSync();
                View tile=(View)((List<?>)field(activity,"tiles")).get(0);
                String pkg=((MainActivity.App)field(tile,"app")).pkg;
                ui(()->tile.performLongClick());waitForIdleSync();View move=findShown(root(),"Move");
                require(move!=null,"Home Move unavailable");ui(()->move.performClick());waitForIdleSync();
                require(((List<?>)field(activity,"tiles")).size()>6,"Home movement is limited to six apps");
                ui(()->{key(KeyEvent.KEYCODE_DPAD_DOWN);key(KeyEvent.KEYCODE_DPAD_CENTER);});waitForIdleSync();
                require(field(activity,"page").equals("home"),"Move did not return to Home");
                List<?> ordered=(List<?>)invoke(activity,"visibleApps",new Class[]{boolean.class},true);
                require(((MainActivity.App)ordered.get(6)).pkg.equals(pkg),"Home app did not move past six favourites");
            });
            test("Up Next reset hides old provider rows and old recent apps",()->{
                android.content.SharedPreferences prefs=activity.getSharedPreferences("luma",0);
                prefs.edit().remove("upnext_reset_at").putString("recent","com.netflix.ninja:1").commit();
                WidgetBoard.Host host=(WidgetBoard.Host)field(activity,"boardHost");
                UpNextWidget[] w=new UpNextWidget[1];ui(()->w[0]=new UpNextWidget(activity,host));
                List<?> before=(List<?>)invoke(w[0],"load",new Class[]{Context.class},activity);
                require(!before.isEmpty(),"Reset test has no old content");
                Class<?>[] types={android.content.SharedPreferences.class,long.class};
                long cutoff=System.currentTimeMillis()+60000;
                invokeStatic(UpNextWidget.class,"reset",types,prefs,cutoff);
                List<?> items=(List<?>)invoke(w[0],"load",new Class[]{Context.class},activity);
                for(Object item:items)require((Boolean)field(item,"allow"),"Old content returned after reset");
                require(prefs.getString("recent","").isEmpty(),"Local recent apps remain");
                prefs.edit().putString("recent","com.netflix.ninja:"+(cutoff+1)).commit();
                List<?> fresh=(List<?>)invoke(w[0],"load",new Class[]{Context.class},activity);
                require(fresh.size()==1&&field(fresh.get(0),"pkg").equals("com.netflix.ninja"),"New activity did not return after reset");
            });
        } catch(Throwable e){failures++;log("ERROR "+e);}
        Bundle result=new Bundle();result.putString("stream","\nLuma regression failures: "+failures+"\n");finish(failures==0?Activity.RESULT_OK:Activity.RESULT_CANCELED,result);
    }
    void test(String name,Check check){try{check.run();log("PASS "+name);}catch(Throwable e){failures++;log("FAIL "+name+": "+e);}}
    void log(String message){Bundle b=new Bundle();b.putString("stream",message+"\n");sendStatus(0,b);}
    void require(boolean value,String message){if(!value)throw new AssertionError(message);}
    void ui(Runnable action){runOnMainSync(action);}
    View root(){return activity.getWindow().getDecorView();}
    void key(int code){activity.dispatchKeyEvent(new KeyEvent(KeyEvent.ACTION_DOWN,code));activity.dispatchKeyEvent(new KeyEvent(KeyEvent.ACTION_UP,code));}
    static View find(View v,String text){if(v instanceof TextView&&((TextView)v).getText().toString().equals(text))return v;if(v instanceof ViewGroup)for(int i=0;i<((ViewGroup)v).getChildCount();i++){View match=find(((ViewGroup)v).getChildAt(i),text);if(match!=null)return match;}return null;}
    static View findShown(View v,String text){if(!v.isShown())return null;if(v instanceof TextView&&((TextView)v).getText().toString().equals(text))return v;if(v instanceof ViewGroup)for(int i=0;i<((ViewGroup)v).getChildCount();i++){View match=findShown(((ViewGroup)v).getChildAt(i),text);if(match!=null)return match;}return null;}
    static Object field(Object target,String name)throws Exception{Field f=target.getClass().getDeclaredField(name);f.setAccessible(true);return f.get(target);}
    static Object invoke(Object target,String name,Class<?>[] types,Object... args){try{Method m=target.getClass().getDeclaredMethod(name,types);m.setAccessible(true);return m.invoke(target,args);}catch(Exception e){throw new RuntimeException(e);}}
    static Object invokeStatic(Class<?> target,String name,Class<?>[] types,Object... args)throws Exception{Method m=target.getDeclaredMethod(name,types);m.setAccessible(true);return m.invoke(null,args);}
}
