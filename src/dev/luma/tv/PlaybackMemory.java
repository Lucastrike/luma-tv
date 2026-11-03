package dev.luma.tv;

import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.media.MediaMetadata;
import android.media.session.MediaController;
import android.media.session.PlaybackState;
import android.os.SystemClock;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/** One local resume card, independent of Home's lifetime. Historical metadata is never a live player. */
final class PlaybackMemory {
    static final class Item {
        String pkg,title,artist,uri;
        Bitmap bitmap;
        long duration,position,at;
        float speed;
        long positionNow(){return Math.max(0,Math.min(duration>0?duration:Long.MAX_VALUE,position+(long)(Math.max(0,SystemClock.elapsedRealtime()-at)*speed)));}
    }
    private static Item latest;
    private static boolean loaded;
    private static final CopyOnWriteArrayList<Runnable> observers=new CopyOnWriteArrayList<>();
    static void addObserver(Runnable observer){observers.addIfAbsent(observer);}
    static void removeObserver(Runnable observer){observers.remove(observer);}
    private static void notifyChanged(){for(Runnable observer:observers)observer.run();}
    private static SharedPreferences prefs(Context context){return context.getSharedPreferences("last_playback",Context.MODE_PRIVATE);}
    static Item get(Context context){
        if(!loaded){
            loaded=true;SharedPreferences p=prefs(context);
            if(!p.getString("title","").isEmpty()){
                latest=new Item();latest.pkg=p.getString("package","");latest.title=p.getString("title","");latest.artist=p.getString("artist","");latest.uri=p.getString("art_uri","");
                latest.duration=p.getLong("duration",0);latest.position=p.getLong("position",0);latest.at=SystemClock.elapsedRealtime();
            }
        }
        return latest;
    }
    static Item read(MediaController controller){
        if(controller==null)return null;
        try{
            MediaMetadata metadata=controller.getMetadata();if(metadata==null)return null;
            Item item=new Item();item.pkg=controller.getPackageName();
            item.title=text(metadata,MediaMetadata.METADATA_KEY_DISPLAY_TITLE,MediaMetadata.METADATA_KEY_TITLE);if(item.title.isEmpty())return null;
            item.artist=text(metadata,MediaMetadata.METADATA_KEY_DISPLAY_SUBTITLE,MediaMetadata.METADATA_KEY_ARTIST,MediaMetadata.METADATA_KEY_ALBUM);
            item.uri=text(metadata,MediaMetadata.METADATA_KEY_ART_URI,MediaMetadata.METADATA_KEY_ALBUM_ART_URI,MediaMetadata.METADATA_KEY_DISPLAY_ICON_URI);
            for(String key:new String[]{MediaMetadata.METADATA_KEY_ART,MediaMetadata.METADATA_KEY_ALBUM_ART,MediaMetadata.METADATA_KEY_DISPLAY_ICON})if(item.bitmap==null)item.bitmap=metadata.getBitmap(key);
            item.duration=metadata.getLong(MediaMetadata.METADATA_KEY_DURATION);item.at=SystemClock.elapsedRealtime();
            PlaybackState state=controller.getPlaybackState();
            if(state!=null){
                item.position=Math.max(0,state.getPosition());
                if(state.getState()==PlaybackState.STATE_PLAYING){
                    item.speed=state.getPlaybackSpeed();
                    if(state.getLastPositionUpdateTime()>0)item.position+=(long)(Math.max(0,item.at-state.getLastPositionUpdateTime())*item.speed);
                }
            }
            item.position=Math.min(item.duration>0?item.duration:Long.MAX_VALUE,item.position);
            return item;
        }catch(RuntimeException ignored){return null;}
    }
    static void remember(Context context,MediaController controller){
        Item item=read(controller);if(item==null)return;
        Item previous=get(context);
        if(previous!=null&&previous.pkg.equals(item.pkg)&&previous.title.equals(item.title)){
            if(item.bitmap==null)item.bitmap=previous.bitmap;
            if(item.uri.isEmpty())item.uri=previous.uri;
        }
        latest=item;save(context);
    }
    static void freeze(Context context,String pkg){
        Item item=get(context);if(item==null||!item.pkg.equals(pkg)||item.speed==0)return;
        item.position=item.positionNow();item.at=SystemClock.elapsedRealtime();item.speed=0;save(context);
    }
    /** Called by the media listener even while Home is off screen. Empty lists keep the last card. */
    static void observe(Context context,List<MediaController> controllers){
        Item previous=get(context);MediaController chosen=null;boolean previousPresent=false;
        for(MediaController controller:controllers){
            Item item=read(controller);if(item==null)continue;
            if(previous!=null&&item.pkg.equals(previous.pkg)){previousPresent=true;if(chosen==null)chosen=controller;}
            PlaybackState state=controller.getPlaybackState();
            if(state!=null&&(state.getState()==PlaybackState.STATE_PLAYING||state.getState()==PlaybackState.STATE_BUFFERING)){chosen=controller;break;}
            if(previous==null&&chosen==null)chosen=controller;
        }
        if(chosen!=null)remember(context,chosen);
        else if(previous!=null&&!previousPresent)freeze(context,previous.pkg);
        notifyChanged();
    }
    private static void save(Context context){
        Item item=latest;if(item==null)return;
        prefs(context).edit().putString("package",item.pkg).putString("title",item.title).putString("artist",item.artist).putString("art_uri",item.uri)
            .putLong("duration",item.duration).putLong("position",item.position).apply();
    }
    private static String text(MediaMetadata metadata,String... keys){for(String key:keys){CharSequence value=metadata.getText(key);if(value!=null&&value.length()>0)return value.toString();}return "";}
}
