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
    String checkFilter="";
    interface Check { void run() throws Exception; }
    @Override public void onCreate(Bundle args) { super.onCreate(args);if(args!=null)checkFilter=args.getString("check","");start(); }
    @Override public void onStart() {
        try {
            if(!getTargetContext().getPackageName().endsWith(".qa"))throw new IllegalStateException("Use a .qa package for regression tests");
            activity=(MainActivity)startActivitySync(new Intent().setClassName(getTargetContext(),MainActivity.class.getName()).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
            ui(()->activity.getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON));
            for(int i=0;i<100&&!((Boolean)field(activity,"loaded"));i++){SystemClock.sleep(100);waitForIdleSync();}
            test("Input shortcut opens over content without launching Home",()->{
                Intent[] opened={null};
                InputKeyService shortcut=new InputKeyService(){@Override public void startActivity(Intent intent){opened[0]=intent;}};
                Method attach=ContextWrapper.class.getDeclaredMethod("attachBaseContext",Context.class);attach.setAccessible(true);attach.invoke(shortcut,activity);
                ui(()->{
                    require(shortcut.onKeyEvent(new KeyEvent(KeyEvent.ACTION_DOWN,KeyEvent.KEYCODE_TV_INPUT)),"Input down was not handled");
                    require(shortcut.onKeyEvent(new KeyEvent(KeyEvent.ACTION_UP,KeyEvent.KEYCODE_TV_INPUT)),"Input up was not handled");
                });
                require(opened[0]!=null,"Input did not open a selector");
                require(new ComponentName(activity.getPackageName(),"dev.luma.tv.InputsActivity").equals(opened[0].getComponent()),"Input opened Home: "+opened[0].getComponent());
                require((opened[0].getFlags()&Intent.FLAG_ACTIVITY_NEW_TASK)!=0,"Service launch requires its own task");
            });
            test("Input menu: background stays visible and arrows work before and after Back",()->inputMenu("back"));
            test("Input menu: repeated Input closes to the same app",()->inputMenu("toggle"));
            test("Input menu: OK opens the selected input and leaves no menu behind",()->inputMenu("select"));
            test("Input menu: Home leaves no menu behind",()->inputMenu("home"));
            test("Return focus: Back keeps the selected Home app",()->returnFocus("home",false,false));
            test("Return focus: Home keeps the selected Home app",()->returnFocus("home",true,false));
            test("Return focus: Home keeps the selected Apps row",()->returnFocus("apps",true,false));
            test("Return focus: Home keeps the selected Settings component",()->returnFocus("settings",true,false));
            test("Return focus: Home keeps the selected input widget and page",()->returnFocus("home",true,true));
            test("Return focus: Back keeps the selected Apps row",()->returnFocus("apps",false,false));
            test("Return focus: Back keeps the selected Settings component",()->returnFocus("settings",false,false));
            test("Return focus: Back keeps the selected input widget and page",()->returnFocus("home",false,true));
            test("Recreated launcher restores the selected app and Apps page",()->recreatedFocus(false));
            test("Recreated launcher restores the selected input and widget page",()->recreatedFocus(true));
            test("Home while already in Luma still opens Home",()->{
                ui(()->invoke(activity,"showPage",new Class[]{String.class,boolean.class},"apps",true));waitForIdleSync();
                ui(()->activity.startActivity(new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME).setClass(getTargetContext(),MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)));
                waitForIdleSync();require("home".equals(field(activity,"page")),"Home inside Luma did not open Home");
            });
            test("Preferred inputs follow TV, Series X, HDMI 1, AV, soundbar",()->{
                ArrayList<String> names=new ArrayList<>();
                for(InputsWidget.Input input:InputsWidget.list(activity))names.add(input.name);
                require(names.equals(Arrays.asList("TV","SERIES X","HDMI 1","AV","Barra de sonido")),"Input order: "+names);
            });
            test("Focus animation frame timings",()->profileFocus());
            test("Tabs switch content with arrows and Down enters the selected page",()->{
                ui(()->{invoke(activity,"showPage",new Class[]{String.class,boolean.class},"home",false);((View)((List<?>)read(activity,"tabs")).get(0)).requestFocus();});
                ui(()->key(KeyEvent.KEYCODE_DPAD_RIGHT));waitForIdleSync();
                require(field(activity,"page").equals("apps"),"Focusing Apps did not switch content: page="+field(activity,"page")+", tab="+invoke(activity,"focusedTab",new Class[]{}));
                require(((View)((List<?>)field(activity,"tabs")).get(1)).hasFocus(),"Switching tabs stole focus");
                ui(()->key(KeyEvent.KEYCODE_DPAD_RIGHT));waitForIdleSync();
                require(field(activity,"page").equals("settings"),"Focusing Settings did not switch content");
                ui(()->key(KeyEvent.KEYCODE_DPAD_DOWN));waitForIdleSync();
                require(!((View)field(activity,"tabBar")).hasFocus(),"Down did not enter Settings");
                ui(()->((View)((List<?>)read(activity,"tabs")).get(0)).requestFocus());waitForIdleSync();
                require(field(activity,"page").equals("home"),"Focusing Home did not switch content");
            });
            test("Focused artwork covers the card without a bottom stripe",()->{
                ui(()->invoke(activity,"showPage",new Class[]{String.class,boolean.class},"apps",true));waitForIdleSync();
                ui(()->((View)((List<?>)read(activity,"tiles")).get(1)).requestFocus());SystemClock.sleep(650);waitForIdleSync();
                ui(()->{
                    Object tile=((List<?>)read(activity,"tiles")).get(1);
                    View face=(View)read(tile,"face"),art=(View)read(tile,"art");
                    require(art.getY()+art.getHeight()>=face.getHeight()-.1f,"Artwork leaves the bottom of the focused card uncovered");
                    require(art.getX()<=.1f&&art.getX()+art.getWidth()>=face.getWidth()-.1f,"Artwork leaves a side of the focused card uncovered");
                });
            });
            test("Now Playing keeps the last video when its session disappears and Home rebuilds",()->{
                android.media.session.MediaSession session=new android.media.session.MediaSession(activity,"Luma media regression");
                try{
                    session.setMetadata(new android.media.MediaMetadata.Builder().putString(android.media.MediaMetadata.METADATA_KEY_TITLE,"Regression video").putLong(android.media.MediaMetadata.METADATA_KEY_DURATION,120000).build());
                    session.setPlaybackState(new android.media.session.PlaybackState.Builder().setState(android.media.session.PlaybackState.STATE_PAUSED,30000,0).build());
                    WidgetBoard.Host host=(WidgetBoard.Host)field(activity,"boardHost");
                    NowPlayingWidget[] widget=new NowPlayingWidget[1];
                    ui(()->{widget[0]=new NowPlayingWidget(activity,host);invoke(widget[0],"use",new Class[]{android.media.session.MediaController.class},session.getController());});
                    require(widget[0].title.getText().toString().equals("Regression video"),"Fixture media metadata was not shown");
                    ui(()->invoke(widget[0],"pick",new Class[]{List.class},Collections.emptyList()));
                    require(widget[0].title.getText().toString().equals("Regression video"),"Video disappeared when the app removed its active session");
                    require(!((Boolean)invoke(widget[0],"playing",new Class[]{})),"Cached video is incorrectly shown as playing");
                    ui(()->{widget[0]=new NowPlayingWidget(activity,host);invoke(widget[0],"pick",new Class[]{List.class},Collections.emptyList());});
                    require(widget[0].title.getText().toString().equals("Regression video"),"Video disappeared when Home rebuilt");
                }finally{session.release();}
            });
            test("Now Playing retains the last watched minute when a session stops at zero",()->{
                android.media.session.MediaSession session=new android.media.session.MediaSession(activity,"Luma stopped progress regression");
                try{
                    session.setMetadata(new android.media.MediaMetadata.Builder().putString(android.media.MediaMetadata.METADATA_KEY_TITLE,"Stopped video").putLong(android.media.MediaMetadata.METADATA_KEY_DURATION,120000).build());
                    session.setPlaybackState(new android.media.session.PlaybackState.Builder().setState(android.media.session.PlaybackState.STATE_PAUSED,45000,0).build());
                    ui(()->PlaybackMemory.observe(activity,Collections.singletonList(session.getController())));
                    session.setPlaybackState(new android.media.session.PlaybackState.Builder().setState(android.media.session.PlaybackState.STATE_STOPPED,0,0).build());
                    ui(()->PlaybackMemory.observe(activity,Collections.singletonList(session.getController())));
                    ui(()->PlaybackMemory.observe(activity,Collections.emptyList()));
                    resetPlaybackMemory();
                    ui(()->{
                        NowPlayingWidget widget=new NowPlayingWidget(activity,(WidgetBoard.Host)read(activity,"boardHost"));
                        require(widget.elapsed.getText().toString().equals("0:45"),"Last watched minute was replaced by "+widget.elapsed.getText());
                    });
                }finally{session.release();}
            });
            test("Now Playing retains a bitmap preview after the launcher process restarts",()->{
                android.media.session.MediaSession session=new android.media.session.MediaSession(activity,"Luma persisted preview regression");
                android.graphics.Bitmap preview=android.graphics.Bitmap.createBitmap(1024,512,android.graphics.Bitmap.Config.ARGB_8888);preview.eraseColor(android.graphics.Color.MAGENTA);
                try{
                    session.setMetadata(new android.media.MediaMetadata.Builder().putString(android.media.MediaMetadata.METADATA_KEY_TITLE,"Bitmap video").putBitmap(android.media.MediaMetadata.METADATA_KEY_ART,preview).putLong(android.media.MediaMetadata.METADATA_KEY_DURATION,120000).build());
                    session.setPlaybackState(new android.media.session.PlaybackState.Builder().setState(android.media.session.PlaybackState.STATE_PAUSED,45000,0).build());
                    ui(()->PlaybackMemory.observe(activity,Collections.singletonList(session.getController())));
                    ui(()->PlaybackMemory.observe(activity,Collections.emptyList()));
                    resetPlaybackMemory();
                    require(PlaybackMemory.get(activity).bitmap!=null,"Preview disappeared after reloading the saved video");
                    require(PlaybackMemory.get(activity).bitmap.getWidth()<=512,"Persisted preview was not bounded");
                    ui(()->{
                        NowPlayingWidget widget=new NowPlayingWidget(activity,(WidgetBoard.Host)read(activity,"boardHost"));
                        require(widget.artImage.getDrawable()!=null,"Preview disappeared after reloading the saved video");
                        require(widget.elapsed.getText().toString().equals("0:45"),"Saved preview lost its playback position");
                    });
                }finally{session.release();}
            });
            test("Now Playing keeps preview and minute during partial live metadata updates",()->{
                android.media.session.MediaSession session=new android.media.session.MediaSession(activity,"Luma partial metadata regression");
                android.graphics.Bitmap preview=android.graphics.Bitmap.createBitmap(24,24,android.graphics.Bitmap.Config.ARGB_8888);
                try{
                    session.setMetadata(new android.media.MediaMetadata.Builder().putString(android.media.MediaMetadata.METADATA_KEY_TITLE,"Partial video").putBitmap(android.media.MediaMetadata.METADATA_KEY_ART,preview).putLong(android.media.MediaMetadata.METADATA_KEY_DURATION,120000).build());
                    session.setPlaybackState(new android.media.session.PlaybackState.Builder().setState(android.media.session.PlaybackState.STATE_PAUSED,45000,0).build());
                    ui(()->PlaybackMemory.observe(activity,Collections.singletonList(session.getController())));
                    session.setMetadata(new android.media.MediaMetadata.Builder().putString(android.media.MediaMetadata.METADATA_KEY_TITLE,"Partial video").build());
                    session.setPlaybackState(new android.media.session.PlaybackState.Builder().setState(android.media.session.PlaybackState.STATE_PAUSED,android.media.session.PlaybackState.PLAYBACK_POSITION_UNKNOWN,0).build());
                    ui(()->{
                        NowPlayingWidget widget=new NowPlayingWidget(activity,(WidgetBoard.Host)read(activity,"boardHost"));
                        invoke(widget,"use",new Class[]{android.media.session.MediaController.class},session.getController());
                        require(widget.artImage.getDrawable()!=null,"Partial live metadata erased the preview");
                        require(widget.elapsed.getText().toString().equals("0:45"),"Partial live metadata erased the watched minute");
                        require(widget.track.getVisibility()==View.VISIBLE,"Partial live metadata erased the known duration");
                        invoke(widget,"use",new Class[]{android.media.session.MediaController.class},(Object)null);
                    });
                }finally{session.release();}
            });
            test("Now Playing accepts a real seek to zero and does not reuse another video's preview",()->{
                android.media.session.MediaSession session=new android.media.session.MediaSession(activity,"Luma seek regression");
                try{
                    session.setMetadata(new android.media.MediaMetadata.Builder().putString(android.media.MediaMetadata.METADATA_KEY_MEDIA_ID,"video-one").putString(android.media.MediaMetadata.METADATA_KEY_TITLE,"Same title").putBitmap(android.media.MediaMetadata.METADATA_KEY_ART,android.graphics.Bitmap.createBitmap(24,24,android.graphics.Bitmap.Config.ARGB_8888)).putLong(android.media.MediaMetadata.METADATA_KEY_DURATION,120000).build());
                    session.setPlaybackState(new android.media.session.PlaybackState.Builder().setState(android.media.session.PlaybackState.STATE_PAUSED,45000,0).build());
                    ui(()->PlaybackMemory.observe(activity,Collections.singletonList(session.getController())));
                    session.setPlaybackState(new android.media.session.PlaybackState.Builder().setState(android.media.session.PlaybackState.STATE_PAUSED,0,0).build());
                    ui(()->PlaybackMemory.observe(activity,Collections.singletonList(session.getController())));
                    require(PlaybackMemory.get(activity).position==0,"A deliberate seek to the start was ignored");
                    session.setMetadata(new android.media.MediaMetadata.Builder().putString(android.media.MediaMetadata.METADATA_KEY_MEDIA_ID,"video-two").putString(android.media.MediaMetadata.METADATA_KEY_TITLE,"Same title").build());
                    session.setPlaybackState(new android.media.session.PlaybackState.Builder().setState(android.media.session.PlaybackState.STATE_NONE,android.media.session.PlaybackState.PLAYBACK_POSITION_UNKNOWN,0).build());
                    ui(()->PlaybackMemory.observe(activity,Collections.singletonList(session.getController())));
                    resetPlaybackMemory();
                    ui(()->{
                        NowPlayingWidget widget=new NowPlayingWidget(activity,(WidgetBoard.Host)read(activity,"boardHost"));
                        require(widget.artImage.getDrawable()==null,"A different video inherited the previous preview");
                        require(widget.elapsed.getText().length()==0,"An unknown position was presented as 0:00");
                    });
                }finally{session.release();}
            });
            test("The antenna tuner is named TV",()->{
                boolean found=false;for(InputsWidget.Input input:InputsWidget.list(activity))if(input.tuner){found=true;require(input.name.equals("TV"),"Tuner is called "+input.name);}
                require(found,"No tuner available for this regression");
            });
            test("Media listener remembers off-screen metadata and offers app resume",()->{
                android.media.session.MediaSession session=new android.media.session.MediaSession(activity,"Luma offscreen regression");
                MediaListener listener=new MediaListener();
                Method attach=ContextWrapper.class.getDeclaredMethod("attachBaseContext",Context.class);attach.setAccessible(true);attach.invoke(listener,activity);
                try{
                    session.setMetadata(new android.media.MediaMetadata.Builder().putString(android.media.MediaMetadata.METADATA_KEY_TITLE,"Offscreen video A").putLong(android.media.MediaMetadata.METADATA_KEY_DURATION,120000).build());
                    session.setPlaybackState(new android.media.session.PlaybackState.Builder().setState(android.media.session.PlaybackState.STATE_PAUSED,45000,0).build());
                    ui(()->invoke(listener,"watch",new Class[]{List.class},Collections.singletonList(session.getController())));
                    session.setMetadata(new android.media.MediaMetadata.Builder().putString(android.media.MediaMetadata.METADATA_KEY_TITLE,"Offscreen video B").putLong(android.media.MediaMetadata.METADATA_KEY_DURATION,120000).build());
                    boolean[] received={false};
                    for(int i=0;i<100;i++){ui(()->received[0]=PlaybackMemory.get(activity).title.equals("Offscreen video B"));if(received[0])break;SystemClock.sleep(20);}
                    ui(()->require(PlaybackMemory.get(activity).title.equals("Offscreen video B"),"Metadata changed offscreen but was not remembered: saved="+PlaybackMemory.get(activity).title+", session="+session.getController().getMetadata().getString(android.media.MediaMetadata.METADATA_KEY_TITLE)));
                    ui(()->invoke(listener,"watch",new Class[]{List.class},Collections.emptyList()));
                    String[] launched={null};WidgetBoard.Host delegate=(WidgetBoard.Host)field(activity,"boardHost");
                    WidgetBoard.Host host=(WidgetBoard.Host)Proxy.newProxyInstance(WidgetBoard.Host.class.getClassLoader(),new Class[]{WidgetBoard.Host.class},(proxy,method,args)->{
                        if(method.getName().equals("launch")){launched[0]=(String)args[0];return null;}return method.invoke(delegate,args);
                    });
                    ui(()->{
                        NowPlayingWidget widget=new NowPlayingWidget(activity,host);
                        require(widget.title.getText().toString().equals("Offscreen video B"),"Home missed the last offscreen video");
                        require(widget.prev.getVisibility()==View.GONE&&widget.next.getVisibility()==View.GONE,"Historical video offers unavailable skip controls");
                        widget.play.performClick();
                    });
                    require(activity.getPackageName().equals(launched[0]),"Resume did not open the remembered app");
                    Field latest=PlaybackMemory.class.getDeclaredField("latest");latest.setAccessible(true);latest.set(null,null);
                    Field loaded=PlaybackMemory.class.getDeclaredField("loaded");loaded.setAccessible(true);loaded.setBoolean(null,false);
                    require(PlaybackMemory.get(activity).title.equals("Offscreen video B"),"Last video was not persisted locally");
                    require(PlaybackMemory.get(activity).position==45000,"Paused position was not preserved");
                }finally{ui(()->invoke(listener,"disconnect",new Class[]{}));session.release();}
            });
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
                        View allow=find(w,"Setup help");require(allow!=null,"Missing setup help on TV without notification settings");allow.performClick();w.setActive(false);
                    } catch(Exception e){throw new RuntimeException(e);}
                });
                waitForIdleSync();
                require(find(root(),"Now Playing setup")!=null,"No help for missing notification settings");
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
            test("Moving cards leaves every other app image aligned",()->{
                ui(()->invoke(activity,"showPage",new Class[]{String.class,boolean.class},"apps",true));waitForIdleSync();
                ui(()->((View)((List<?>)read(activity,"tiles")).get(0)).requestFocus());
                SystemClock.sleep(600);
                ui(()->key(KeyEvent.KEYCODE_DPAD_RIGHT));SystemClock.sleep(600);
                View tile=(View)((List<?>)field(activity,"tiles")).get(1);
                ui(()->tile.performLongClick());waitForIdleSync();
                View move=findShown(root(),"Move");require(move!=null,"Move unavailable");
                ui(()->move.performClick());waitForIdleSync();
                ui(()->{key(KeyEvent.KEYCODE_DPAD_RIGHT);key(KeyEvent.KEYCODE_DPAD_DOWN);key(KeyEvent.KEYCODE_DPAD_LEFT);});
                SystemClock.sleep(700);waitForIdleSync();
                ui(()->assertAligned());
                ui(()->key(KeyEvent.KEYCODE_DPAD_CENTER));
                SystemClock.sleep(700);waitForIdleSync();
                ui(()->assertAligned());
            });
            test("YouTube uses the artwork supplied by its installed app",()->{
                ui(()->{
                    WidgetBoard.Host host=(WidgetBoard.Host)read(activity,"boardHost");
                    MainActivity.App youtube=host.app("com.google.android.youtube.tv");
                    require(youtube!=null,"YouTube is not installed");
                    MainActivity.App reference=new MainActivity.App();reference.pkg="qa.native.art";reference.icon=youtube.icon;reference.banner=youtube.banner;reference.color=youtube.color;
                    try {
                        java.lang.reflect.Constructor<?> ctor=host.art(youtube.pkg).getClass().getDeclaredConstructor(MainActivity.class,MainActivity.App.class);ctor.setAccessible(true);
                        android.graphics.Bitmap expected=draw((View)ctor.newInstance(activity,reference));
                        android.graphics.Bitmap actual=draw(host.art(youtube.pkg));
                        require(actual.sameAs(expected),"YouTube artwork differs from the installed banner/icon");
                    }catch(ReflectiveOperationException e){throw new RuntimeException(e);}
                });
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
            test("Remote shortcut handles only Input and consumes a complete key pair",()->{
                int[] opens={0};InputKeyService shortcut=new InputKeyService(){@Override void openInputs(){opens[0]++;}};
                for(int code=0;code<=KeyEvent.getMaxKeyCode();code++)if(code!=KeyEvent.KEYCODE_TV_INPUT){
                    require(!shortcut.onKeyEvent(new KeyEvent(KeyEvent.ACTION_DOWN,code)),"Consumed another key down: "+code);
                    require(!shortcut.onKeyEvent(new KeyEvent(KeyEvent.ACTION_UP,code)),"Consumed another key up: "+code);
                }
                require(opens[0]==0,"Another key opened Inputs");
                require(shortcut.onKeyEvent(new KeyEvent(KeyEvent.ACTION_DOWN,KeyEvent.KEYCODE_TV_INPUT)),"Input down not handled");
                require(shortcut.onKeyEvent(new KeyEvent(1,2,KeyEvent.ACTION_DOWN,KeyEvent.KEYCODE_TV_INPUT,1)),"Input repeat not consumed");
                require(opens[0]==1,"Holding Input opened more than one selector");
                require(shortcut.onKeyEvent(new KeyEvent(KeyEvent.ACTION_UP,KeyEvent.KEYCODE_TV_INPUT)),"Input up not handled");
                require(!shortcut.onKeyEvent(new KeyEvent(KeyEvent.ACTION_UP,KeyEvent.KEYCODE_TV_INPUT)),"Unpaired Input up consumed");
                InputKeyService missing=new InputKeyService(){@Override void openInputs(){throw new ActivityNotFoundException();}};
                require(!missing.onKeyEvent(new KeyEvent(KeyEvent.ACTION_DOWN,KeyEvent.KEYCODE_TV_INPUT)),"Failed open swallowed Input down");
                require(!missing.onKeyEvent(new KeyEvent(KeyEvent.ACTION_UP,KeyEvent.KEYCODE_TV_INPUT)),"Failed open swallowed Input up");
            });
            test("Remote shortcut requests key filtering without reading screens",()->{
                android.content.pm.ServiceInfo service=activity.getPackageManager().getServiceInfo(new ComponentName(activity,InputKeyService.class),android.content.pm.PackageManager.GET_META_DATA);
                require("android.permission.BIND_ACCESSIBILITY_SERVICE".equals(service.permission),"Service is not protected by Android");
                try(android.content.res.XmlResourceParser xml=service.loadXmlMetaData(activity.getPackageManager(),"android.accessibilityservice")){
                    while(xml.next()!=org.xmlpull.v1.XmlPullParser.START_TAG){}
                    String ns="http://schemas.android.com/apk/res/android";
                    require(xml.getAttributeBooleanValue(ns,"canRequestFilterKeyEvents",false),"Missing key-filter capability");
                    require(!xml.getAttributeBooleanValue(ns,"canRetrieveWindowContent",true),"Requests screen contents");
                    require(xml.getAttributeValue(ns,"accessibilityEventTypes")==null,"Requests accessibility event contents");
                }
            });
            test("Remote shortcut action opens the input selector in a running launcher",()->{
                ui(()->activity.onNewIntent(new Intent(InputKeyService.SHOW_INPUTS)));waitForIdleSync();
                require(findShown(root(),"HDMI 1")!=null,"Shortcut action did not open Inputs");
                ui(()->activity.onBackPressed());
            });
            test("Remote setup avoids the TV settings stub that cannot open",()->{
                Intent setup=(Intent)invoke(activity,"inputSetupIntent",new Class[]{});
                ComponentName target=setup.resolveActivity(activity.getPackageManager());
                require(target!=null,"No setup screen available");
                require(!target.getPackageName().equals("com.google.android.tv.frameworkpackagestubs"),"Setup opens unavailable settings stub");
            });
            test("Remote shortcut action opens the input selector on cold launch",()->{
                ui(()->activity.finish());waitForIdleSync();
                activity=(MainActivity)startActivitySync(new Intent(getTargetContext(),MainActivity.class).setAction(InputKeyService.SHOW_INPUTS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
                waitForIdleSync();require(findShown(root(),"HDMI 1")!=null,"Cold shortcut action did not open Inputs");
                ui(()->activity.onBackPressed());
            });
        } catch(Throwable e){failures++;log("ERROR "+e);}
        Bundle result=new Bundle();result.putString("stream","\nLuma regression failures: "+failures+"\n");finish(failures==0?Activity.RESULT_OK:Activity.RESULT_CANCELED,result);
    }
    void test(String name,Check check){if(!checkFilter.isEmpty()&&!name.contains(checkFilter))return;try{check.run();log("PASS "+name);}catch(Throwable e){failures++;log("FAIL "+name+": "+e);}}
    void resetPlaybackMemory()throws Exception{
        ((java.util.concurrent.ExecutorService)fieldStatic(PlaybackMemory.class,"ART_IO")).submit(()->{}).get();
        Field latest=PlaybackMemory.class.getDeclaredField("latest");latest.setAccessible(true);latest.set(null,null);
        Field loaded=PlaybackMemory.class.getDeclaredField("loaded");loaded.setAccessible(true);loaded.setBoolean(null,false);
    }
    void log(String message){Bundle b=new Bundle();b.putString("stream",message+"\n");sendStatus(0,b);}
    void require(boolean value,String message){if(!value)throw new AssertionError(message);}
    void inputMenu(String exit)throws Exception{
        ReturnFixture background=(ReturnFixture)startActivitySync(new Intent(getTargetContext(),ReturnFixture.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
        Activity menu=null;
        Intent[] selected={null};
        ActivityMonitor monitor=new ActivityMonitor(){@Override public ActivityResult onStartActivity(Intent intent){
            if(Intent.ACTION_VIEW.equals(intent.getAction())){selected[0]=intent;return new ActivityResult(Activity.RESULT_CANCELED,null);}return null;
        }};
        try{
            waitForIdleSync();SystemClock.sleep(400);
            menu=startActivitySync(new Intent().setClassName(getTargetContext(),"dev.luma.tv.InputsActivity").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
            final Activity shown=menu;waitForIdleSync();SystemClock.sleep(400);waitForIdleSync();
            ui(()->{
                require(!background.stopped&&background.getWindow().getDecorView().isShown(),"Opening Inputs hid the original content");
                require(!((Boolean)read(activity,"started")),"Opening Inputs resumed Home");
                require(shown.getTaskId()!=activity.getTaskId(),"Inputs reused the launcher task");
                require(shown.getCurrentFocus() instanceof TextView&&"TV".contentEquals(((TextView)shown.getCurrentFocus()).getText()),"Inputs did not focus TV");
            });
            String[] labels={"TV","SERIES X","HDMI 1","AV","Barra de sonido"};
            for(int i=1;i<labels.length;i++){
                sendKeyDownUpSync(KeyEvent.KEYCODE_DPAD_DOWN);waitForIdleSync();final String expected=labels[i];
                ui(()->require(shown.getCurrentFocus() instanceof TextView&&expected.contentEquals(((TextView)shown.getCurrentFocus()).getText()),"Down did not focus "+expected));
            }
            sendKeyDownUpSync(KeyEvent.KEYCODE_DPAD_UP);waitForIdleSync();
            ui(()->require("AV".contentEquals(((TextView)shown.getCurrentFocus()).getText()),"Up did not focus AV"));
            if(exit.equals("toggle"))ui(()->shown.startActivity(new Intent().setClassName(getTargetContext(),"dev.luma.tv.InputsActivity").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK|Intent.FLAG_ACTIVITY_SINGLE_TOP)));
            else if(exit.equals("select")){
                addMonitor(monitor);sendKeyDownUpSync(KeyEvent.KEYCODE_DPAD_UP);waitForIdleSync();sendKeyDownUpSync(KeyEvent.KEYCODE_DPAD_CENTER);
                require(selected[0]!=null,"OK did not start the selected input");
                InputsWidget.Input hdmi=null;for(InputsWidget.Input input:InputsWidget.list(activity))if(input.name.equals("HDMI 1"))hdmi=input;
                require(hdmi!=null&&InputsWidget.intent(hdmi).getData().equals(selected[0].getData()),"OK started another input: "+selected[0]);
            }
            else if(exit.equals("home"))ui(()->shown.startActivity(new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)));
            else sendKeyDownUpSync(KeyEvent.KEYCODE_BACK);
            waitForIdleSync();SystemClock.sleep(400);waitForIdleSync();
            for(int i=0;i<20&&!shown.isFinishing()&&!shown.isDestroyed();i++){SystemClock.sleep(100);waitForIdleSync();}
            ui(()->{
                require(shown.isFinishing()||shown.isDestroyed(),"Inputs did not close");
                if(!exit.equals("home")){require(background.resumed&&!background.isFinishing(),"Closing Inputs did not restore the original app");background.first.requestFocus();}
            });
            if(!exit.equals("home")){
                sendKeyDownUpSync(KeyEvent.KEYCODE_DPAD_DOWN);waitForIdleSync();
                ui(()->require(background.second.hasFocus(),"Arrow keys did not reach the original app after closing Inputs"));
            }
        }finally{
            if(exit.equals("select"))removeMonitor(monitor);
            if(menu!=null){final Activity shown=menu;ui(shown::finish);}
            ui(background::finish);waitForIdleSync();
            ui(()->activity.startActivity(new Intent(getTargetContext(),MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)));waitForIdleSync();
        }
    }
    void returnFocus(String page,boolean home,boolean widget)throws Exception{
        ui(()->invoke(activity,"showPage",new Class[]{String.class,boolean.class},page,true));waitForIdleSync();
        View[] target={null};int[] widgetPage={-1};
        ui(()->{
            if(widget){
                WidgetBoard board=(WidgetBoard)read(activity,"board");
                for(int p=0;p<board.widgets.size();p++)for(Widget w:board.widgets.get(p))if(w instanceof InputsWidget){
                    board.showPage(p);widgetPage[0]=p;target[0]=((InputsWidget)w).tiles.get(1);
                }
            }else if(page.equals("settings"))target[0]=(View)((List<?>)((View)read(activity,"content")).getTag()).get(2);
            else target[0]=(View)((List<?>)read(activity,"tiles")).get(page.equals("apps")?7:2);
            require(target[0]!=null&&target[0].requestFocus(),"Could not focus the return target");
        });
        waitForIdleSync();
        ReturnFixture other=(ReturnFixture)startActivitySync(new Intent(getTargetContext(),ReturnFixture.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
        try{
            waitForIdleSync();SystemClock.sleep(400);waitForIdleSync();
            require(!((Boolean)field(activity,"started")),"Fixture did not pause Luma");
            if(home)ui(()->other.startActivity(new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME).setClass(getTargetContext(),MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)));
            else ui(other::finish);
            waitForIdleSync();SystemClock.sleep(300);waitForIdleSync();
            ui(()->{
                require(page.equals(read(activity,"page")),"Returned to "+read(activity,"page")+" instead of "+page);
                require(target[0].hasFocus(),"Lost the selected component when returning with "+(home?"Home":"Back"));
                if(widget)require(((WidgetBoard)read(activity,"board")).index==widgetPage[0],"Lost the widget page");
            });
        }finally{ui(other::finish);waitForIdleSync();}
    }
    void recreatedFocus(boolean widget)throws Exception{
        String page=widget?"home":"apps";
        ui(()->invoke(activity,"showPage",new Class[]{String.class,boolean.class},page,true));waitForIdleSync();
        String[] key={null};int[] shown={-1};
        ui(()->{
            if(widget){
                WidgetBoard board=(WidgetBoard)read(activity,"board");
                for(int p=0;p<board.widgets.size();p++)for(Widget w:board.widgets.get(p))if(w instanceof InputsWidget){board.showPage(p);shown[0]=p;Widget.Tile tile=((InputsWidget)w).tiles.get(1);tile.requestFocus();key[0]=tile.key;}
            }else{View tile=(View)((List<?>)read(activity,"tiles")).get(7);tile.requestFocus();key[0]=((MainActivity.App)read(tile,"app")).pkg;}
        });waitForIdleSync();
        ActivityMonitor monitor=addMonitor(MainActivity.class.getName(),null,false);
        try{
            ui(activity::recreate);Activity replacement=waitForMonitorWithTimeout(monitor,5000);require(replacement!=null,"Recreation did not create an activity");activity=(MainActivity)replacement;
            for(int i=0;i<100&&!((Boolean)field(activity,"loaded"));i++){SystemClock.sleep(50);waitForIdleSync();}
            waitForIdleSync();SystemClock.sleep(300);waitForIdleSync();
            ui(()->{
                require(page.equals(read(activity,"page")),"Recreation lost the page");View focus=activity.getCurrentFocus();require(focus!=null,"Recreation lost focus");
                if(widget){require(focus instanceof Widget.Tile&&key[0].equals(((Widget.Tile)focus).key),"Recreation lost the input");require(((WidgetBoard)read(activity,"board")).index==shown[0],"Recreation lost the widget page");}
                else require(key[0].equals(((MainActivity.App)read(focus,"app")).pkg),"Recreation lost the selected app");
            });
        }finally{removeMonitor(monitor);}
    }
    void profileFocus()throws Exception{
        ui(()->invoke(activity,"showPage",new Class[]{String.class,boolean.class},"apps",true));SystemClock.sleep(700);
        require(field(activity,"page").equals("apps")&&((List<?>)field(activity,"tiles")).size()>6,"Animation benchmark must run on the full Apps grid");
        ArrayList<Long> samples=new ArrayList<>();long[] phase=new long[5];HandlerThread thread=new HandlerThread("Luma frame probe");thread.start();
        Window.OnFrameMetricsAvailableListener probe=(window,metrics,dropped)->{long duration=metrics.getMetric(FrameMetrics.TOTAL_DURATION);if(duration>0)synchronized(samples){samples.add(duration);phase[0]+=metrics.getMetric(FrameMetrics.UNKNOWN_DELAY_DURATION);phase[1]+=metrics.getMetric(FrameMetrics.DRAW_DURATION);phase[2]+=metrics.getMetric(FrameMetrics.SYNC_DURATION);phase[3]+=metrics.getMetric(FrameMetrics.COMMAND_ISSUE_DURATION);phase[4]+=metrics.getMetric(FrameMetrics.SWAP_BUFFERS_DURATION);}};
        ui(()->activity.getWindow().addOnFrameMetricsAvailableListener(probe,new Handler(thread.getLooper())));
        try{for(int i=0;i<48;i++){final int index=i%6;ui(()->((View)((List<?>)read(activity,"tiles")).get(index)).requestFocus());SystemClock.sleep(90);}SystemClock.sleep(500);}
        finally{ui(()->activity.getWindow().removeOnFrameMetricsAvailableListener(probe));thread.quitSafely();thread.join();}
        Collections.sort(samples);require(!samples.isEmpty(),"No frame timings collected");
        int missed=0;for(long duration:samples)if(duration>16666667L)missed++;
        log(String.format(java.util.Locale.US,"LUMA_PERF frames=%d p50_ms=%.2f p95_ms=%.2f over_16_67_ms=%d",samples.size(),samples.get(samples.size()/2)/1e6,samples.get((samples.size()-1)*95/100)/1e6,missed));
        log(String.format(java.util.Locale.US,"LUMA_PHASE_MEAN unknown=%.2f draw=%.2f sync=%.2f issue=%.2f swap=%.2f",phase[0]/1e6/samples.size(),phase[1]/1e6/samples.size(),phase[2]/1e6/samples.size(),phase[3]/1e6/samples.size(),phase[4]/1e6/samples.size()));
    }
    void assertAligned(){
        for(Object tile:(List<?>)read(activity,"tiles")){
            if(((View)tile).hasFocus())continue;
            MainActivity.App app=(MainActivity.App)read(tile,"app");
            View face=(View)read(tile,"face"),art=(View)read(tile,"art");
            require(Math.abs(face.getScaleX()-1)<.001f&&Math.abs(face.getScaleY()-1)<.001f,app.label+" retains focus scale: "+face.getScaleX());
            require(Math.abs(face.getTranslationY())<.01f&&Math.abs(face.getRotationX())<.01f&&Math.abs(face.getRotationY())<.01f,app.label+" retains focus position/rotation");
            require(Math.abs(art.getTranslationX())<.01f&&Math.abs(art.getTranslationY())<.01f,app.label+" artwork remains offset: "+art.getTranslationX()+", "+art.getTranslationY());
        }
    }
    static Object read(Object target,String name){try{return field(target,name);}catch(Exception e){throw new RuntimeException(e);}}
    static android.graphics.Bitmap draw(View view){view.layout(0,0,264,152);android.graphics.Bitmap bitmap=android.graphics.Bitmap.createBitmap(264,152,android.graphics.Bitmap.Config.ARGB_8888);view.draw(new android.graphics.Canvas(bitmap));return bitmap;}
    void ui(Runnable action){Throwable[] error=new Throwable[1];runOnMainSync(()->{try{action.run();}catch(Throwable e){error[0]=e;}});if(error[0] instanceof AssertionError)throw (AssertionError)error[0];if(error[0]!=null)throw new RuntimeException(error[0]);}
    View root(){return activity.getWindow().getDecorView();}
    void key(int code){activity.dispatchKeyEvent(new KeyEvent(KeyEvent.ACTION_DOWN,code));activity.dispatchKeyEvent(new KeyEvent(KeyEvent.ACTION_UP,code));}
    static View find(View v,String text){if(v instanceof TextView&&((TextView)v).getText().toString().equals(text))return v;if(v instanceof ViewGroup)for(int i=0;i<((ViewGroup)v).getChildCount();i++){View match=find(((ViewGroup)v).getChildAt(i),text);if(match!=null)return match;}return null;}
    static View findShown(View v,String text){if(!v.isShown())return null;if(v instanceof TextView&&((TextView)v).getText().toString().equals(text))return v;if(v instanceof ViewGroup)for(int i=0;i<((ViewGroup)v).getChildCount();i++){View match=findShown(((ViewGroup)v).getChildAt(i),text);if(match!=null)return match;}return null;}
    static Object field(Object target,String name)throws Exception{Field f=target.getClass().getDeclaredField(name);f.setAccessible(true);return f.get(target);}
    static Object fieldStatic(Class<?> target,String name)throws Exception{Field f=target.getDeclaredField(name);f.setAccessible(true);return f.get(null);}
    static Object invoke(Object target,String name,Class<?>[] types,Object... args){try{Method m=target.getClass().getDeclaredMethod(name,types);m.setAccessible(true);return m.invoke(target,args);}catch(Exception e){throw new RuntimeException(e);}}
    static Object invokeStatic(Class<?> target,String name,Class<?>[] types,Object... args)throws Exception{Method m=target.getDeclaredMethod(name,types);m.setAccessible(true);return m.invoke(null,args);}
}
