package dev.luma.tv;

import android.content.*;
import android.graphics.*;
import android.graphics.drawable.GradientDrawable;
import android.media.MediaMetadata;
import android.media.session.MediaController;
import android.media.session.MediaSessionManager;
import android.media.session.PlaybackState;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.view.*;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.*;

/** Now Playing: the media session of the app playing on this TV (Spotify, YouTube Music, SoundCloud…) with its
 *  artwork, progress and previous / play-pause / next. Android hands media sessions only to a notification
 *  listener, so Luma declares one (MediaListener) that reads no notifications; the user allows it once. */
final class NowPlayingWidget extends Widget {
    static final String[] MUSIC={"com.spotify.tv.android","com.google.android.youtube.tvmusic","com.soundcloud.android","com.apple.android.music","deezer.android.tv","com.amazon.music.tv","com.aspiro.tidal","ru.yandex.music","com.google.android.youtube.tv"};
    static final float PAD=18,ART=104,TX=136,CTRL_Y=130;
    final Handler handler=new Handler(Looper.getMainLooper());
    final MediaSessionManager sessions;
    final ComponentName listener;
    final boolean settingsAvailable;
    final FrameLayout art;
    final ImageView artImage;
    final Glyph artGlyph,playGlyph;
    final TextView source,title,artist,elapsed,remaining;
    final View track,fill;
    final FrameLayout prev,play,next;
    final TextView openPill;
    final LinearLayout shortcuts;
    MediaController controller;
    boolean access=true;
    String artKey="";
    final MediaSessionManager.OnActiveSessionsChangedListener changed=list->pick(list);
    final Runnable remembered=this::repick;
    final MediaController.Callback callback=new MediaController.Callback(){
        @Override public void onPlaybackStateChanged(PlaybackState state){PlaybackMemory.remember(getContext(),controller);render();}
        @Override public void onMetadataChanged(MediaMetadata metadata){PlaybackMemory.remember(getContext(),controller);render();}
        @Override public void onSessionDestroyed(){if(controller!=null)controller.unregisterCallback(this);controller=null;repick();}
    };
    final Runnable tick=new Runnable(){public void run(){progress();if(active&&playing())handler.postDelayed(this,1000);}};

    NowPlayingWidget(Context context,WidgetBoard.Host host){
        super(context,host,WidgetBoard.width("music"));
        sessions=(MediaSessionManager)context.getSystemService(Context.MEDIA_SESSION_SERVICE);
        listener=new ComponentName(context,MediaListener.class);
        settingsAvailable=new Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS).resolveActivity(context.getPackageManager())!=null;
        art=new FrameLayout(context);art.setClipToOutline(true);art.setBackground(round(light()?0x14000000:0x1AFFFFFF,14));art.setElevation(px(3));addView(art,pos(ART,ART,PAD,PAD));
        artGlyph=new Glyph(context).kind("note").color(ink(0x80));art.addView(artGlyph,pos(40,40,(ART-40)/2,(ART-40)/2));
        artImage=new ImageView(context);artImage.setScaleType(ImageView.ScaleType.CENTER_CROP);art.addView(artImage,new LayoutParams(-1,-1));
        float tw=w-TX-PAD;
        source=text("",10.5f,ink(0x8C),"sans-serif-medium");source.setLetterSpacing(.08f);addView(source,pos(tw,14,TX,22));
        title=text("",17,ink(0xFF),"sans-serif-medium");addView(title,pos(tw,22,TX,40));
        artist=text("",13,ink(0xB3),"sans-serif");addView(artist,pos(tw,17,TX,66));
        track=new View(context);track.setBackground(round(ink(0x26),1.5f));addView(track,pos(tw,3,TX,96));
        fill=new View(context);fill.setBackground(round(ink(0xD9),1.5f));fill.setPivotX(0);addView(fill,pos(tw,3,TX,96));
        elapsed=text("",10.5f,ink(0x80),"sans-serif");addView(elapsed,pos(60,13,TX,104));
        remaining=text("",10.5f,ink(0x80),"sans-serif");remaining.setGravity(Gravity.RIGHT);addView(remaining,pos(60,13,w-PAD-60,104));
        prev=button("previous",34,TX-4,CTRL_Y+4,0,()->{if(controller!=null)controller.getTransportControls().skipToPrevious();});
        play=button("play",42,TX+38,CTRL_Y,1,()->{if(Promo.on){Promo.toggle();render();return;}if(!live()){resume();return;}if(playing())controller.getTransportControls().pause();else controller.getTransportControls().play();});
        next=button("next",34,TX+88,CTRL_Y+4,2,()->{if(controller!=null)controller.getTransportControls().skipToNext();});
        playGlyph=(Glyph)play.getChildAt(1);
        openPill=pill("Open",3,this::resume);
        // Right-aligned by layout, not by translation: focus search goes by layout position.
        LayoutParams op=new LayoutParams(-2,px(32));op.gravity=Gravity.TOP|Gravity.RIGHT;op.rightMargin=px(PAD);op.topMargin=px(CTRL_Y+5);addView(openPill,op);
        shortcuts=new LinearLayout(context);shortcuts.setClipChildren(false);shortcuts.setClipToPadding(false);addView(shortcuts,pos(tw+10,34,TX-4,CTRL_Y+4));
        render();
    }
    private FrameLayout button(String kind,float size,float x,float y,int index,Runnable action){
        FrameLayout b=new FrameLayout(getContext());View disc=new View(getContext());b.addView(disc,new LayoutParams(-1,-1));
        Glyph g=new Glyph(getContext()).kind(kind);float gs=size*.46f;b.addView(g,pos(gs,gs,(size-gs)/2,(size-gs)/2));
        b.setPivotX(px(size/2));b.setPivotY(px(size/2));
        control(b,index,1.14f,f->{disc.setBackground(oval(f?focusFill():restFill()));g.color(f?focusInk():ink(0xF2));},()->hero(),action);
        addView(b,pos(size,size,x,y));return b;
    }
    private TextView pill(String label,int index,Runnable action){return pill(label,index,null,action);}
    /** A rounded text button; hero overrides the Home title while it is focused (app shortcuts). */
    private TextView pill(String label,int index,String[] hero,Runnable action){
        TextView p=text(label,12.5f,ink(0xF2),"sans-serif-medium");p.setGravity(Gravity.CENTER);p.setPadding(px(16),0,px(16),0);
        control(p,index,1.08f,f->{p.setBackground(round(f?focusFill():restFill(),16));p.setTextColor(f?focusInk():ink(0xF2));},()->hero!=null?hero:hero(),action);
        return p;
    }
    @Override void setActive(boolean value){
        if(value==active)return;super.setActive(value);
        if(value){
            PlaybackMemory.addObserver(remembered);
            try{sessions.addOnActiveSessionsChangedListener(changed,listener,handler);access=true;pick(sessions.getActiveSessions(listener));}
            catch(SecurityException e){access=false;use(null);}
        } else {
            PlaybackMemory.removeObserver(remembered);
            try{sessions.removeOnActiveSessionsChangedListener(changed);}catch(RuntimeException ignored){}
            use(null);handler.removeCallbacks(tick);
        }
    }
    @Override void refresh(){if(active)repick();}
    private void repick(){try{pick(sessions.getActiveSessions(listener));}catch(SecurityException e){access=false;use(null);}}
    /** The session to show: one that is playing, else the most recent one with a title. */
    private void pick(List<MediaController> list){
        MediaController best=null;PlaybackMemory.Item last=PlaybackMemory.get(getContext());
        if(list!=null){for(MediaController c:list){if(c.getPackageName().equals(getContext().getPackageName())||titleOf(c).isEmpty())continue;if(c.getPlaybackState()!=null&&c.getPlaybackState().getState()==PlaybackState.STATE_PLAYING){best=c;break;}}
            if(best==null)for(MediaController c:list)if(!c.getPackageName().equals(getContext().getPackageName())&&!titleOf(c).isEmpty()&&(last==null||last.pkg.equals(c.getPackageName()))){best=c;break;}}
        if(best==null&&last!=null)PlaybackMemory.freeze(getContext(),last.pkg);
        use(best);
    }
    private void use(MediaController c){
        if(c!=null)PlaybackMemory.remember(getContext(),c);
        if(controller!=null&&c!=null&&controller.getSessionToken().equals(c.getSessionToken())){render();return;}
        if(controller!=null)controller.unregisterCallback(callback);
        controller=c;if(c!=null)c.registerCallback(callback,handler);
        render();
    }
    private static String titleOf(MediaController c){MediaMetadata m=c.getMetadata();if(m==null)return "";CharSequence t=m.getText(MediaMetadata.METADATA_KEY_DISPLAY_TITLE);if(t==null)t=m.getText(MediaMetadata.METADATA_KEY_TITLE);return t==null?"":t.toString();}
    private boolean live(){return controller!=null&&!titleOf(controller).isEmpty();}
    private boolean playing(){if(Promo.on)return Promo.playing;PlaybackState s=live()?controller.getPlaybackState():null;return s!=null&&(s.getState()==PlaybackState.STATE_PLAYING||s.getState()==PlaybackState.STATE_BUFFERING);}
    private String appName(String pkg){MainActivity.App a=host.app(pkg);return a==null?"":a.label;}
    private PlaybackMemory.Item displayed(){PlaybackMemory.Item live=PlaybackMemory.current(getContext(),controller);return live!=null?live:access?PlaybackMemory.get(getContext()):null;}
    private void resume(){PlaybackMemory.Item item=displayed();if(item!=null)host.launch(item.pkg);}

    /** Updates the widget in place; focus moves to a visible control if its control disappears. */
    private void render(){
        if(Promo.on){promo();return;}
        View focused=findFocus();
        PlaybackMemory.Item item=displayed();boolean has=item!=null;
        int media=has?VISIBLE:GONE;
        for(View v:new View[]{prev,play,next,track,fill,elapsed,remaining,openPill})v.setVisibility(media);
        if(!live()){prev.setVisibility(GONE);next.setVisibility(GONE);}
        shortcuts.setVisibility(has?GONE:VISIBLE);
        if(has){
            String pkg=item.pkg;boolean live=live();
            source.setText(appName(pkg).toUpperCase(Locale.ENGLISH)+(live?"":" · RESUME"));title.setText(item.title);
            artist.setText(item.artist);
            long actions=!live||controller.getPlaybackState()==null?0:controller.getPlaybackState().getActions();
            prev.setAlpha((actions&PlaybackState.ACTION_SKIP_TO_PREVIOUS)!=0?1:.4f);next.setAlpha((actions&PlaybackState.ACTION_SKIP_TO_NEXT)!=0?1:.4f);
            playGlyph.kind(playing()?"pause":"play");
            openPill.setText((live?"Open ":"Resume ")+appName(pkg));
            artwork(item);progress();
            handler.removeCallbacks(tick);if(active&&playing())handler.postDelayed(tick,1000);
        } else {
            artKey="";artImage.setImageDrawable(null);source.setText("NOW PLAYING");
            title.setText(access?"Nothing playing":"See what's playing");
            artist.setText(access?"Music you play on this TV appears here.":settingsAvailable?"Allow notification access for Luma.":"Set up once from a connected computer.");
            fillShortcuts();
        }
        if(focused!=null&&(focused.getVisibility()!=VISIBLE||focused.getParent()==null||!focused.isShown())){View first=firstControl();if(first!=null)first.requestFocus();}
    }
    /** The promo demo's song (Promo): the same layout as a real session, on a made-up clock. */
    private void promo(){
        for(View v:new View[]{prev,play,next,track,fill,elapsed,remaining,openPill})v.setVisibility(VISIBLE);
        shortcuts.setVisibility(GONE);prev.setAlpha(1);next.setAlpha(1);
        source.setText(Promo.SOURCE.toUpperCase(Locale.ENGLISH));title.setText(Promo.SONG);artist.setText(Promo.ARTIST);
        playGlyph.kind(Promo.playing?"pause":"play");
        openPill.setText("Open "+Promo.SOURCE);
        if(!artKey.equals("promo:"+Promo.SONG)){artKey="promo:"+Promo.SONG;fade(Promo.artwork(px(ART)*2));}
        progress();
        handler.removeCallbacks(tick);if(active&&Promo.playing)handler.postDelayed(tick,1000);
    }
    private View firstControl(){if(play.getVisibility()==VISIBLE)return play;return shortcuts.getChildCount()>0?shortcuts.getChildAt(0):null;}
    private String shortcutKey="";
    private void fillShortcuts(){
        ArrayList<String> apps=new ArrayList<>();
        if(!access)apps.add("@allow");
        else for(String pkg:MUSIC)if(host.app(pkg)!=null&&apps.size()<2)apps.add(pkg);
        String key=String.join(",",apps);if(key.equals(shortcutKey))return;shortcutKey=key;
        boolean hadFocus=shortcuts.hasFocus();shortcuts.removeAllViews();
        for(int i=0;i<apps.size();i++){final String pkg=apps.get(i);
            String[] hero=pkg.equals("@allow")?null:new String[]{"music:open:"+pkg,appName(pkg),"Open "+appName(pkg)+" and start something. It will show up here, with play, pause and skip."};
            TextView p=pill(pkg.equals("@allow")?(settingsAvailable?"Allow access":"Setup help"):appName(pkg),i,hero,()->{
                if(pkg.equals("@allow"))allowAccess();else host.launch(pkg);});
            LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-2,px(32));lp.rightMargin=px(8);lp.topMargin=px(1);shortcuts.addView(p,lp);}
        if(hadFocus&&shortcuts.getChildCount()>0)shortcuts.getChildAt(0).requestFocus();
    }
    private void allowAccess(){
        Intent settings=new Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS);
        if(settings.resolveActivity(getContext().getPackageManager())!=null){
            try{host.activity().startActivity(settings);return;}catch(ActivityNotFoundException|SecurityException ignored){}
        }
        host.notice("Now Playing setup","This TV has no notification-access settings screen. A connected computer must enable access once. Android grants notification access so Luma can display and control media. You can hide this widget in Settings → Home widgets.");
    }
    private void progress(){
        if(Promo.on){long d=Promo.SONG_LENGTH,p=Promo.position();track.setVisibility(VISIBLE);fill.setVisibility(VISIBLE);fill.setScaleX(p/(float)d);elapsed.setText(time(p));remaining.setText("−"+time(d-p));return;}
        PlaybackMemory.Item item=displayed();if(item==null)return;
        long duration=item.duration,position=item.positionNow();
        boolean measured=item.positionKnown&&duration>0;
        track.setVisibility(measured?VISIBLE:INVISIBLE);fill.setVisibility(measured?VISIBLE:INVISIBLE);fill.setScaleX(measured?position/(float)duration:0);
        elapsed.setText(item.positionKnown?time(position):"");remaining.setText(measured?"−"+time(Math.max(0,duration-position)):"");
    }
    private static String time(long ms){long s=ms/1000;return s>=3600?String.format(Locale.US,"%d:%02d:%02d",s/3600,s/60%60,s%60):String.format(Locale.US,"%d:%02d",s/60,s%60);}
    private void artwork(PlaybackMemory.Item item){
        Bitmap b=item.bitmap;String link=item.uri.isEmpty()?null:item.uri;
        String key=b!=null?"bitmap:"+b.getGenerationId()+":"+b.getWidth()+item.title:link==null?"":link;
        if(key.equals(artKey))return;artKey=key;
        if(b!=null){fade(b);return;}
        artImage.setImageDrawable(null);if(link==null)return;
        final String url=link;final Context context=getContext().getApplicationContext();
        WidgetBoard.IO.execute(()->{Bitmap loaded=null;
            try{InputStream in;if(url.startsWith("http")){HttpURLConnection c=(HttpURLConnection)new URL(url).openConnection();c.setConnectTimeout(6000);c.setReadTimeout(8000);in=c.getInputStream();}else in=context.getContentResolver().openInputStream(Uri.parse(url));
                try(InputStream s=in){BitmapFactory.Options o=new BitmapFactory.Options();o.inPreferredConfig=Bitmap.Config.RGB_565;loaded=BitmapFactory.decodeStream(s,null,o);}}catch(Exception|OutOfMemoryError ignored){}
            final Bitmap result=loaded;if(result!=null)post(()->{if(url.equals(artKey)){PlaybackMemory.artworkLoaded(context,item,result);fade(result);}});});
    }
    private void fade(Bitmap b){artImage.setImageBitmap(b);if(motion()){artImage.setAlpha(0f);artImage.animate().alpha(1).setDuration(300).start();}}
    private String[] hero(){
        if(Promo.on)return new String[]{"music:promo",Promo.SONG,Promo.ARTIST+" · "+Promo.SOURCE};
        PlaybackMemory.Item item=displayed();
        if(item!=null)return new String[]{"music:"+item.title,item.title,!live()?"Resume in "+appName(item.pkg):UpNextWidget.join(artist.getText().toString(),appName(item.pkg))};
        return access?new String[]{"music:none","Now Playing","Start music in an app and control it from Home."}:new String[]{"music:allow","Now Playing","Allow notification access so Luma can show and control the music playing on this TV. Luma does not read your notifications."};
    }
    @Override protected void onDetachedFromWindow(){setActive(false);handler.removeCallbacksAndMessages(null);super.onDetachedFromWindow();}
}
