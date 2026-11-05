package dev.luma.tv;

import android.app.Activity;
import android.os.Bundle;
import android.widget.TextView;
import android.widget.Button;
import android.widget.LinearLayout;

/** QA-only foreground activity for real Android pause/resume and Home intent delivery. */
public final class ReturnFixture extends Activity {
    boolean resumed,stopped;
    Button first,second;
    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        LinearLayout content=new LinearLayout(this);content.setOrientation(LinearLayout.VERTICAL);
        TextView label=new TextView(this);label.setText("Luma navigation regression");content.addView(label);
        first=new Button(this);first.setText("First control");content.addView(first);
        second=new Button(this);second.setText("Second control");content.addView(second);
        setContentView(content);first.requestFocus();
    }
    @Override protected void onStart(){super.onStart();stopped=false;}
    @Override protected void onResume(){super.onResume();resumed=true;}
    @Override protected void onPause(){resumed=false;super.onPause();}
    @Override protected void onStop(){stopped=true;super.onStop();}
}
