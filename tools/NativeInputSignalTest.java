package dev.luma.tv;

import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;

/** Replays native records, including backlog and forged records, without Android dependencies. */
public final class NativeInputSignalTest {
    static final String INPUT="receive KEYCODE_TV_INPUT start activity";
    static int checks;
    static void require(boolean value,String message){checks++;if(!value)throw new AssertionError(message);}
    static String line(int uid,String tag,String message,int sequence){
        return String.format(Locale.US,"11-08 11:55:09.%03d %5d 3904 11793 D %s: %s",sequence,uid,tag,message);
    }
    static String marker(String token){return "11-08 11:55:09.100 12345 4300 4300 I LumaInputBridge: ready "+token;}
    public static void main(String[] args)throws Exception{
        NativeInputSignal signal=new NativeInputSignal(1000,12345,"fresh");
        require(!signal.accept(line(1000,"GlobalKey",INPUT,1)),"Buffered Input launched before the fresh marker");
        require(!signal.accept(marker("old")),"An old marker armed the reader");
        require(!signal.ready(),"An old marker armed the reader");
        signal.accept(marker("fresh").replace("12345","2000"));require(!signal.ready(),"A shell-forged marker armed the reader");
        signal.accept(marker("fresh"));require(signal.ready(),"The current reader did not arm");
        require(!signal.accept(line(2000,"GlobalKey",INPUT,2)),"A shell-forged Input was accepted");
        require(!signal.accept(line(12345,"GlobalKey",INPUT,3)),"An app-forged Input was accepted");
        require(!signal.accept(line(1000,"AnotherTag",INPUT,4)),"Another tag was accepted");
        require(!signal.accept(line(1000,"GlobalKey",INPUT+" extra",5)),"A partial message match was accepted");
        require(!signal.accept(line(1000,"GlobalKey","receive ACTION_GLOBAL_BUTTON keyCode = 178",6)),"Both edges would open the menu");
        require(!signal.accept(line(1000,"GlobalKey","receive ACTION_GLOBAL_BUTTON keyCode = 24",7)),"Volume was treated as Input");
        require(!signal.accept("malformed"),"Malformed output was accepted");
        String input=line(1000,"GlobalKey",INPUT,8);
        require(signal.accept(input),"The native Input release was missed");
        require(!signal.accept(input),"The same native record launched twice");
        require(signal.accept(line(1000,"GlobalKey",INPUT,9)),"A second real press could not toggle the menu");
        NativeInputSignal restarted=new NativeInputSignal(1000,12345,"restart");
        require(!restarted.accept(input),"Restart replayed an old Input");
        restarted.accept(marker("fresh"));require(!restarted.ready(),"Restart accepted an old readiness marker");
        restarted.accept(marker("restart"));require(restarted.accept(input),"The restarted reader missed a new record");
        if(args.length>0){
            List<String> trace=Files.readAllLines(Paths.get(args[0]),StandardCharsets.UTF_8);
            NativeInputSignal replay=new NativeInputSignal(1000,12345,"trace");
            for(String record:trace)require(!replay.accept(record),"Trace backlog opened the menu");
            replay.accept(marker("trace"));int expected=0,actual=0;
            for(String record:trace){if(record.contains(" D GlobalKey: "+INPUT))expected++;if(replay.accept(record))actual++;}
            require(expected>0,"No actual native Input records in the trace");
            require(actual==expected,"Native trace lost or duplicated Input: "+actual+" / "+expected);
        }
        System.out.println("PASS: "+checks+" native Input signal checks");
    }
}
