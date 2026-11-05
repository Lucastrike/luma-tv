package dev.luma.tv;

import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.media.MediaMetadata;
import android.media.session.MediaController;
import android.media.session.PlaybackState;
import android.os.SystemClock;
import android.util.AtomicFile;
import java.io.*;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** One local resume card, independent of Home's lifetime. Historical metadata is never a live player. */
final class PlaybackMemory {
    static final class Item {
        String pkg,title,artist,uri,id="";
        Bitmap bitmap;
        long duration,position,at;
        float speed;
        boolean positionKnown,resetPosition;
        long positionNow(){return Math.max(0,Math.min(duration>0?duration:Long.MAX_VALUE,position+(long)(Math.max(0,SystemClock.elapsedRealtime()-at)*speed)));}
    }
    private static Item latest;
    private static boolean loaded;
    private static final ExecutorService ART_IO=Executors.newSingleThreadExecutor(r->{Thread t=new Thread(r,"Luma playback artwork");t.setDaemon(true);return t;});
    private static Bitmap queuedBitmap;
    private static String queuedArtKey="";
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
                latest.id=p.getString("media_id","");latest.duration=p.getLong("duration",0);latest.position=p.getLong("position",0);latest.positionKnown=p.getBoolean("position_known",p.contains("position"));latest.at=SystemClock.elapsedRealtime();
                try(DataInputStream in=new DataInputStream(artFile(context).openRead())){if(in.readUTF().equals(artKey(latest)))latest.bitmap=BitmapFactory.decodeStream(in);}catch(IOException|RuntimeException|OutOfMemoryError ignored){}
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
            item.id=text(metadata,MediaMetadata.METADATA_KEY_MEDIA_ID);
            item.artist=text(metadata,MediaMetadata.METADATA_KEY_DISPLAY_SUBTITLE,MediaMetadata.METADATA_KEY_ARTIST,MediaMetadata.METADATA_KEY_ALBUM);
            item.uri=text(metadata,MediaMetadata.METADATA_KEY_ART_URI,MediaMetadata.METADATA_KEY_ALBUM_ART_URI,MediaMetadata.METADATA_KEY_DISPLAY_ICON_URI);
            for(String key:new String[]{MediaMetadata.METADATA_KEY_ART,MediaMetadata.METADATA_KEY_ALBUM_ART,MediaMetadata.METADATA_KEY_DISPLAY_ICON})if(item.bitmap==null)item.bitmap=metadata.getBitmap(key);
            item.duration=metadata.getLong(MediaMetadata.METADATA_KEY_DURATION);item.at=SystemClock.elapsedRealtime();
            PlaybackState state=controller.getPlaybackState();
            if(state!=null){
                item.positionKnown=state.getPosition()>=0;
                item.position=Math.max(0,state.getPosition());
                item.resetPosition=item.position==0&&(state.getState()==PlaybackState.STATE_STOPPED||state.getState()==PlaybackState.STATE_NONE||state.getState()==PlaybackState.STATE_ERROR);
                if(state.getState()==PlaybackState.STATE_PLAYING){
                    item.speed=state.getPlaybackSpeed();
                    if(state.getLastPositionUpdateTime()>0)item.position+=(long)(Math.max(0,item.at-state.getLastPositionUpdateTime())*item.speed);
                }
            }
            item.position=Math.min(item.duration>0?item.duration:Long.MAX_VALUE,item.position);
            return item;
        }catch(RuntimeException ignored){return null;}
    }
    private static boolean sameMedia(Item a,Item b){return a!=null&&b!=null&&a.pkg.equals(b.pkg)&&a.title.equals(b.title)&&(a.id.isEmpty()||b.id.isEmpty()||a.id.equals(b.id));}
    /** Partial/closing session updates must not erase the last usable snapshot of this video. */
    static Item current(Context context,MediaController controller){
        Item item=read(controller),previous=get(context);if(item==null)return null;
        if(sameMedia(previous,item)){
            if(item.bitmap==null&&(item.uri.isEmpty()||item.uri.equals(previous.uri)))item.bitmap=previous.bitmap;
            if(item.uri.isEmpty())item.uri=previous.uri;
            if(item.id.isEmpty())item.id=previous.id;
            if(item.artist.isEmpty())item.artist=previous.artist;
            if(item.duration<=0)item.duration=previous.duration;
            if((!item.positionKnown||item.resetPosition)&&previous.positionKnown){item.position=previous.positionNow();item.positionKnown=true;}
        }
        item.position=Math.min(item.duration>0?item.duration:Long.MAX_VALUE,item.position);
        return item;
    }
    static void remember(Context context,MediaController controller){
        Item item=current(context,controller);if(item==null)return;
        latest=item;save(context);
    }
    /** Cache URI artwork too: a provider may revoke access when its app leaves the foreground. */
    static void artworkLoaded(Context context,Item item,Bitmap bitmap){
        Item current=get(context);if(bitmap==null||!sameMedia(current,item)||!current.uri.equals(item.uri))return;
        current.bitmap=bitmap;saveArtwork(context,current);
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
            .putString("media_id",item.id).putLong("duration",item.duration).putLong("position",item.position).putBoolean("position_known",item.positionKnown).apply();
        saveArtwork(context,item);
    }
    private static AtomicFile artFile(Context context){return new AtomicFile(new File(context.getFilesDir(),"last-playback-preview"));}
    private static String artKey(Item item){return item.pkg+"\n"+item.id+"\n"+item.title+"\n"+item.uri;}
    private static void saveArtwork(Context context,Item item){
        final Bitmap bitmap=item.bitmap;final String key=artKey(item);
        if(bitmap==queuedBitmap&&key.equals(queuedArtKey))return;
        queuedBitmap=bitmap;queuedArtKey=key;final AtomicFile file=artFile(context);
        ART_IO.execute(()->{
            if(bitmap==null){file.delete();return;}
            FileOutputStream out=null;Bitmap scaled=null;
            try{
                float scale=Math.min(1,512f/Math.max(bitmap.getWidth(),bitmap.getHeight()));
                scaled=scale<1?Bitmap.createScaledBitmap(bitmap,Math.max(1,Math.round(bitmap.getWidth()*scale)),Math.max(1,Math.round(bitmap.getHeight()*scale)),true):bitmap;
                out=file.startWrite();DataOutputStream data=new DataOutputStream(out);data.writeUTF(key);
                if(!scaled.compress(Bitmap.CompressFormat.PNG,100,data))throw new IOException("Artwork compression failed");
                data.flush();file.finishWrite(out);out=null;
            }catch(IOException|RuntimeException|OutOfMemoryError ignored){if(out!=null)file.failWrite(out);}
            finally{if(scaled!=null&&scaled!=bitmap)scaled.recycle();}
        });
    }
    private static String text(MediaMetadata metadata,String... keys){for(String key:keys){CharSequence value=metadata.getText(key);if(value!=null&&value.length()>0)return value.toString();}return "";}
}
