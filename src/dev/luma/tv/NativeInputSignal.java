package dev.luma.tv;

import java.util.regex.*;

/** Recognizes authenticated native releases only after this reader's fresh readiness marker. */
final class NativeInputSignal {
    private static final Pattern RECORD=Pattern.compile("^\\d{2}-\\d{2}\\s+\\d{2}:\\d{2}:\\d{2}\\.\\d{3,6}\\s+(\\d+)\\s+\\d+\\s+\\d+\\s+([VDIWEFAS])\\s+([^:]+):\\s?(.*)$");
    private final String nativeUid,ownUid,marker;
    private boolean ready;
    private String lastInput;
    NativeInputSignal(int nativeUid,int ownUid,String token){
        this.nativeUid=Integer.toString(nativeUid);this.ownUid=Integer.toString(ownUid);marker="ready "+token;
    }
    boolean ready(){return ready;}
    boolean accept(String line){
        if(line.length()>8192)return false;
        Matcher record=RECORD.matcher(line);if(!record.matches())return false;
        String uid=record.group(1),level=record.group(2),tag=record.group(3).trim(),message=record.group(4);
        if(uid.equals(ownUid)&&level.equals("I")&&tag.equals("LumaInputBridge")&&message.equals(marker)){ready=true;return false;}
        if(!ready||!uid.equals(nativeUid)||!level.equals("D")||!tag.equals("GlobalKey")||!message.equals("receive KEYCODE_TV_INPUT start activity"))return false;
        if(line.equals(lastInput))return false;
        lastInput=line;return true;
    }
}
