package dev.luma.tv;

import android.app.Activity;
import android.content.*;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.view.*;
import android.widget.*;
import java.util.ArrayList;

/** A short-lived translucent task. Android owns its input focus and restores the app underneath on exit. */
public final class InputsActivity extends Activity {
    private float unit;
    private SonicFeedback sonic;
    private boolean sound;
    private final ArrayList<TextView> choices=new ArrayList<>();

    @Override public void onCreate(Bundle state){
        super.onCreate(state);
        unit=getResources().getDisplayMetrics().widthPixels/960f;
        sound=getSharedPreferences("luma",MODE_PRIVATE).getBoolean("sound",true);
        if(sound)sonic=new SonicFeedback(this);
        getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_FULLSCREEN|View.SYSTEM_UI_FLAG_HIDE_NAVIGATION|View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY);
        ArrayList<InputsWidget.Input> inputs=InputsWidget.list(this);
        FrameLayout root=new FrameLayout(this);root.setBackgroundColor(0xA6000000);root.setOnClickListener(v->finish());root.setFocusable(false);
        LinearLayout panel=new LinearLayout(this);panel.setOrientation(LinearLayout.VERTICAL);panel.setPadding(px(14),px(24),px(14),px(14));
        panel.setBackground(shape(0xF51C1C1E,24));panel.setElevation(px(24));panel.setOnClickListener(v->{});panel.setFocusable(false);
        root.addView(panel,new FrameLayout.LayoutParams(px(400),-2,Gravity.CENTER));
        TextView title=text("Inputs",20);title.setPadding(px(10),0,px(10),px(20));panel.addView(title);
        ScrollView scroll=new ScrollView(this);scroll.setVerticalScrollBarEnabled(false);scroll.setFocusable(false);scroll.setClipToPadding(false);
        LinearLayout rows=new LinearLayout(this);rows.setOrientation(LinearLayout.VERTICAL);rows.setPadding(px(4),px(4),px(4),px(4));scroll.addView(rows);
        panel.addView(scroll,new LinearLayout.LayoutParams(-1,px(Math.min(Math.max(1,inputs.size()),7)*46+8)));
        for(int i=0;i<inputs.size();i++){
            InputsWidget.Input input=inputs.get(i);final int index=i;
            addChoice(rows,input.name,index,()->open(input));
        }
        if(inputs.isEmpty()){title.setText("No TV inputs available");addChoice(rows,"Close",0,this::finish);}
        for(int i=0;i<choices.size();i++){
            TextView row=choices.get(i);
            row.setNextFocusUpId(choices.get(Math.max(0,i-1)).getId());row.setNextFocusDownId(choices.get(Math.min(choices.size()-1,i+1)).getId());
            row.setNextFocusLeftId(row.getId());row.setNextFocusRightId(row.getId());
        }
        setContentView(root);choices.get(0).requestFocus();
    }
    private void addChoice(LinearLayout rows,String label,int index,Runnable action){
        TextView row=text(label,15);row.setId(View.generateViewId());row.setSingleLine(true);row.setEllipsize(android.text.TextUtils.TruncateAt.END);
        row.setGravity(Gravity.CENTER_VERTICAL);row.setPadding(px(18),0,px(18),0);row.setFocusable(true);row.setFocusableInTouchMode(true);row.setSoundEffectsEnabled(false);
        row.setOnFocusChangeListener((v,focused)->{row.setBackground(focused?shape(0xF7FFFFFF,12):null);row.setTextColor(focused?0xFF111827:Color.WHITE);if(focused&&sonic!=null)sonic.step((index%6)/5f,1);});
        row.setOnClickListener(v->{if(sonic!=null)sonic.open();action.run();});
        LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-1,px(42));lp.bottomMargin=px(4);rows.addView(row,lp);choices.add(row);
    }
    private void open(InputsWidget.Input input){
        try{startActivity(InputsWidget.intent(input).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));finish();overridePendingTransition(0,0);}
        catch(ActivityNotFoundException|SecurityException error){Toast.makeText(this,"This TV could not open that input.",Toast.LENGTH_SHORT).show();}
    }
    @Override protected void onNewIntent(Intent intent){super.onNewIntent(intent);finish();}
    @Override public boolean dispatchKeyEvent(KeyEvent event){
        if(event.getKeyCode()==KeyEvent.KEYCODE_TV_INPUT){if(event.getAction()==KeyEvent.ACTION_UP)finish();return true;}
        return super.dispatchKeyEvent(event);
    }
    @Override public void finish(){super.finish();overridePendingTransition(0,0);}
    @Override protected void onDestroy(){if(sonic!=null)sonic.release();super.onDestroy();}
    private int px(float value){return Math.round(value*unit);}
    private GradientDrawable shape(int color,float radius){GradientDrawable bg=new GradientDrawable();bg.setColor(color);bg.setCornerRadius(px(radius));return bg;}
    private TextView text(String value,float size){
        TextView view=new TextView(this);view.setText(value);view.setTextColor(Color.WHITE);
        view.setTextSize(android.util.TypedValue.COMPLEX_UNIT_PX,px(size));view.setTypeface(Typeface.create("sans-serif-medium",Typeface.NORMAL));view.setIncludeFontPadding(false);return view;
    }
}
