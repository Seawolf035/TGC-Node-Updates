package club.thegamerclub.nodelab;

import android.app.AlertDialog;
import android.content.Intent;
import android.os.Bundle;
import android.graphics.Color;
import android.view.Gravity;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.SeekBar;
import android.widget.TextView;

/** Personal UI theme editor for Relay-verified Admin+ users, never a global theme. */
public final class ThemeEditorActivity extends StaffProtectedActivity {
    private LinearLayout page;
    private boolean access(){return ThemePreferences.allowed(this);}
    private int dp(int n){return CompanionStyle.dp(this,n);}
    private TextView t(String s,int n,int color){return CompanionStyle.text(this,s,n,color,n>=17);}
    private void button(LinearLayout p,String label,Runnable action){
        Button b=CompanionStyle.button(this,label,false);
        p.addView(b,CompanionStyle.buttonParams(this));b.setOnClickListener(v->action.run());
    }
    @Override public void onCreate(Bundle state){super.onCreate(state);render();}
    @Override public void onResume(){
        super.onResume();
        if(!access()){finish();return;}
        ThemePreferences.apply(this);
    }
    private void render(){
        if(!access()){finish();return;}
        ThemePreferences.apply(this);
        ScrollView scroll=new ScrollView(this);
        scroll.setBackgroundColor(CompanionStyle.BG);
        page=new LinearLayout(this);page.setOrientation(LinearLayout.VERTICAL);
        page.setPadding(dp(16),dp(10),dp(16),dp(25));
        scroll.addView(page);setContentView(scroll);CompanionStyle.safeArea(this,scroll);
        button(page,"← Back",this::finish);
        page.addView(t("ADMIN+ · PERSONAL COLORS",12,CompanionStyle.ACCENT));
        page.addView(t("Theme Studio",26,CompanionStyle.TEXT));
        page.addView(t("Colors change this device only. Other staff keep their own appearance. "+
            "Royalty's recognizable gold badges and borders remain separate from personal colors.",
            13,CompanionStyle.MUTED));
        LinearLayout sample=CompanionStyle.card(this);
        page.addView(sample,CompanionStyle.cardParams(this));
        sample.addView(t("Live preview",19,CompanionStyle.TEXT));
        sample.addView(t("Sample card · section and buttons follow your current palette.",13,CompanionStyle.MUTED));
        Button preview=CompanionStyle.button(this,"Sample primary button",true);
        sample.addView(preview,CompanionStyle.buttonParams(this));preview.setEnabled(false);
        page.addView(CompanionStyle.section(this,"QUICK THEMES"));
        for(int i=0;i<ThemePreferences.NAMES.length;i++){
            final int index=i;
            button(page,ThemePreferences.NAMES[i],()->{
                ThemePreferences.preset(this,index);render();
            });
        }
        page.addView(CompanionStyle.section(this,"CUSTOM COLOR MIXER"));
        String[] names={"Background","Card surface","Raised buttons & panels",
            "Input fields","Accent","Borders"};
        int[] palette=ThemePreferences.colors(this);
        for(int i=0;i<names.length;i++){
            final int index=i;
            final int value=palette[i];
            button(page,names[i]+"  ·  #"+String.format(java.util.Locale.US,"%06X",value&0xFFFFFF),
                ()->editColor(index,names[index],value));
        }
        button(page,"Reset all colors to TGC Default",()->{
            ThemePreferences.reset(this);render();
        });
    }
    private void editColor(int index,String name,int initial){
        if(!access()){finish();return;}
        LinearLayout frame=new LinearLayout(this);frame.setOrientation(LinearLayout.VERTICAL);
        frame.setPadding(dp(22),dp(6),dp(22),dp(6));
        SeekBar[] channels=new SeekBar[3];
        TextView preview=t("",14,CompanionStyle.TEXT);
        frame.addView(preview);
        int[] rgb={Color.red(initial),Color.green(initial),Color.blue(initial)};
        for(int i=0;i<3;i++){
            final int idx=i;
            TextView label=t(new String[]{"Red","Green","Blue"}[i],13,CompanionStyle.TEXT);
            frame.addView(label);
            SeekBar bar=new SeekBar(this);bar.setMax(255);bar.setProgress(rgb[i]);channels[i]=bar;
            frame.addView(bar);
            bar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener(){
                public void onProgressChanged(SeekBar seek,int value,boolean fromUser){
                    rgb[idx]=value;
                    int c=Color.rgb(rgb[0],rgb[1],rgb[2]);
                    preview.setText("Preview #"+String.format(java.util.Locale.US,"%06X",c&0xFFFFFF));
                    preview.setTextColor(index==0||index==1||index==2||index==3?
                        CompanionStyle.TEXT:c);
                }
                public void onStartTrackingTouch(SeekBar seek){}
                public void onStopTrackingTouch(SeekBar seek){}
            });
        }
        preview.setText("Preview #"+String.format(java.util.Locale.US,"%06X",initial&0xFFFFFF));
        new AlertDialog.Builder(this).setTitle(name).setView(frame)
            .setNegativeButton("Cancel",null)
            .setPositiveButton("Apply",(dialog,which)->{
                if(!access())return;
                ThemePreferences.color(this,index,Color.rgb(rgb[0],rgb[1],rgb[2]));
                render();
            }).show();
    }
}
