package dev.luma.tv;

import android.service.notification.NotificationListenerService;
import android.content.ComponentName;
import android.content.Context;
import android.media.MediaMetadata;
import android.media.session.MediaController;
import android.media.session.MediaSession;
import android.media.session.MediaSessionManager;
import android.media.session.PlaybackState;
import android.os.Handler;
import android.os.Looper;
import java.util.*;

/** Observes media sessions while apps play in the foreground. Does not process notification payloads. */
public final class MediaListener extends NotificationListenerService {
    private final Handler handler=new Handler(Looper.getMainLooper());
    private MediaSessionManager sessions;
    private final Map<MediaSession.Token,MediaController> watched=new LinkedHashMap<>();
    private final Map<MediaSession.Token,MediaController.Callback> callbacks=new HashMap<>();
    private final MediaSessionManager.OnActiveSessionsChangedListener changed=this::watch;
    @Override public void onListenerConnected(){
        super.onListenerConnected();disconnect();
        sessions=(MediaSessionManager)getSystemService(Context.MEDIA_SESSION_SERVICE);
        ComponentName listener=new ComponentName(this,MediaListener.class);
        try{sessions.addOnActiveSessionsChangedListener(changed,listener,handler);watch(sessions.getActiveSessions(listener));}catch(SecurityException ignored){}
    }
    private void watch(List<MediaController> controllers){
        if(controllers==null)controllers=Collections.emptyList();
        HashSet<MediaSession.Token> present=new HashSet<>();for(MediaController controller:controllers)present.add(controller.getSessionToken());
        for(MediaSession.Token token:new ArrayList<>(watched.keySet()))if(!present.contains(token)){
            MediaController controller=watched.remove(token);controller.unregisterCallback(callbacks.remove(token));PlaybackMemory.freeze(this,controller.getPackageName());
        }
        for(MediaController controller:controllers){
            final MediaSession.Token token=controller.getSessionToken();if(watched.containsKey(token))continue;
            MediaController.Callback callback=new MediaController.Callback(){
                @Override public void onPlaybackStateChanged(PlaybackState state){snapshot();}
                @Override public void onMetadataChanged(MediaMetadata metadata){snapshot();}
                @Override public void onSessionDestroyed(){
                    PlaybackMemory.freeze(MediaListener.this,controller.getPackageName());
                    controller.unregisterCallback(this);watched.remove(token);callbacks.remove(token);snapshot();
                }
            };
            watched.put(token,controller);callbacks.put(token,callback);controller.registerCallback(callback,handler);
        }
        PlaybackMemory.observe(this,controllers);
    }
    private void snapshot(){PlaybackMemory.observe(this,new ArrayList<>(watched.values()));}
    private void disconnect(){
        if(sessions!=null)try{sessions.removeOnActiveSessionsChangedListener(changed);}catch(RuntimeException ignored){}
        for(Map.Entry<MediaSession.Token,MediaController> entry:watched.entrySet())entry.getValue().unregisterCallback(callbacks.get(entry.getKey()));
        watched.clear();callbacks.clear();sessions=null;
    }
    @Override public void onListenerDisconnected(){disconnect();super.onListenerDisconnected();}
    @Override public void onDestroy(){disconnect();super.onDestroy();}
}
