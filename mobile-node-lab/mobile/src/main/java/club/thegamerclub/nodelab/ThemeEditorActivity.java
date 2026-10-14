package club.thegamerclub.nodelab;

import android.app.AlertDialog;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.DialogInterface;
import android.os.Bundle;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.text.Editable;
import android.text.InputFilter;
import android.text.InputType;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;

/** Admin+ local theme editor. Hex fields and VRChat palette order are editable. */
public final class ThemeEditorActivity extends StaffProtectedActivity {
    private LinearLayout page;
    private ScrollView scroll;
    private boolean access(){return ThemePreferences.allowed(this);}
    private int dp(int n){return CompanionStyle.dp(this,n);}
    private TextView t(String label,int n,int color){
        return CompanionStyle.text(this,label,n,color,n>=17);
    }
    private Button button(LinearLayout parent,String label,Runnable callback){
        Button b=CompanionStyle.button(this,label,false);
        parent.addView(b,CompanionStyle.buttonParams(this));
        b.setOnClickListener(v->callback.run());return b;
    }
    private void gap(LinearLayout parent,int px){
        parent.addView(new View(this),new LinearLayout.LayoutParams(1,dp(px)));
    }
    private EditText hexInput(String hint,String initial){
        EditText input=CompanionStyle.input(this,hint);
        input.setInputType(InputType.TYPE_CLASS_TEXT|InputType.TYPE_TEXT_FLAG_CAP_CHARACTERS);
        input.setFilters(new InputFilter[]{new InputFilter.LengthFilter(7)});
        input.setText(initial);input.setSingleLine(true);input.setSelectAllOnFocus(true);
        return input;
    }
    @Override public void onCreate(Bundle state){super.onCreate(state);render(false);}
    @Override public void onResume(){
        super.onResume();
        if(!access()){finish();return;}
        ThemePreferences.apply(this);
    }
    private void render(boolean preserveScroll){
        if(!access()){finish();return;}
        int previous=preserveScroll&&scroll!=null?scroll.getScrollY():0;
        ThemePreferences.apply(this);
        scroll=new ScrollView(this);
        scroll.setFillViewport(true);
        scroll.setBackgroundColor(CompanionStyle.BG);
        page=new LinearLayout(this);page.setOrientation(LinearLayout.VERTICAL);
        page.setPadding(dp(16),dp(12),dp(16),dp(28));
        scroll.addView(page);setContentView(scroll);CompanionStyle.safeArea(this,scroll);
        if(preserveScroll)scroll.post(()->scroll.scrollTo(0,previous));

        button(page,"← Back",this::finish);
        page.addView(t("ADMIN+ · PERSONAL COLORS",12,CompanionStyle.ACCENT));
        page.addView(t("Theme Studio",26,CompanionStyle.TEXT));
        page.addView(t("Your choices are private to this device. "+
            "Royalty identity badges and gold trim stay recognizable to other staff.",
            13,CompanionStyle.MUTED));
        gap(page,10);
        LinearLayout sample=CompanionStyle.card(this);
        page.addView(sample,CompanionStyle.cardParams(this));
        sample.addView(t("Live theme preview",19,CompanionStyle.TEXT));
        sample.addView(t("Cards, text, icon colors and buttons reflect your selected palette.",
            13,CompanionStyle.MUTED));
        sample.addView(t("◆  Icon preview",15,CompanionStyle.ICON));
        Button primary=CompanionStyle.button(this,"Sample primary button",true);
        sample.addView(primary,CompanionStyle.buttonParams(this));primary.setEnabled(false);

        page.addView(CompanionStyle.section(this,"VRCHAT PALETTE"));
        LinearLayout vrc=CompanionStyle.card(this);
        page.addView(vrc,CompanionStyle.cardParams(this));
        vrc.addView(t("Paste a complete VRChat UI code palette",16,CompanionStyle.TEXT));
        vrc.addView(t("Six colors, in this exact order: Highlight, Icons, Buttons, "+
            "Background, Fonts, Subtext. Accepts comma-separated codes or one per line. "+
            "This applies only to your own theme; it does not create a shared preset.",
            12,CompanionStyle.MUTED));
        gap(vrc,8);
        button(vrc,"Import 6-color VRChat palette",this::showImport);
        button(vrc,"Copy my colors as VRChat UI codes",this::copyPalette);

        page.addView(CompanionStyle.section(this,"QUICK THEMES"));
        for(int i=0;i<ThemePreferences.NAMES.length;i++){
            final int idx=i;
            button(page,ThemePreferences.NAMES[i],()->{
                ThemePreferences.preset(this,idx);
                render(true);
            });
        }

        page.addView(CompanionStyle.section(this,"CUSTOM COLORS · HEX OR RGB"));
        page.addView(t("Tap a color to enter #RRGGBB directly or fine-tune it with "+
            "the red, green and blue sliders. Both inputs stay synchronized.",
            12,CompanionStyle.MUTED));
        gap(page,8);
        int[] colors=ThemePreferences.colors(this);
        for(int i=0;i<ThemePreferences.COLOR_NAMES.length;i++){
            final int index=i,initial=colors[i];
            final String label=ThemePreferences.COLOR_NAMES[i];
            LinearLayout row=new LinearLayout(this);
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setGravity(Gravity.CENTER_VERTICAL);
            row.setPadding(dp(12),dp(9),dp(10),dp(9));
            row.setBackground(CompanionStyle.shape(this,
                CompanionStyle.SURFACE,13,CompanionStyle.BORDER));
            LinearLayout.LayoutParams outer=new LinearLayout.LayoutParams(-1,-2);
            outer.bottomMargin=dp(8);
            page.addView(row,outer);
            View swatch=new View(this);
            swatch.setBackground(CompanionStyle.shape(this,initial,10,
                CompanionStyle.readableOn(initial)==0xFFFFFFFF?
                    0xFFCBD5E1:0xFF35394F));
            LinearLayout.LayoutParams swatchSize=
                new LinearLayout.LayoutParams(dp(38),dp(38));
            swatchSize.rightMargin=dp(12);row.addView(swatch,swatchSize);
            LinearLayout labels=new LinearLayout(this);
            labels.setOrientation(LinearLayout.VERTICAL);
            row.addView(labels,new LinearLayout.LayoutParams(0,-2,1));
            labels.addView(t(label,14,CompanionStyle.TEXT));
            labels.addView(t(ThemePreferences.hex(initial),13,CompanionStyle.MUTED));
            TextView edit=t("Edit ›",13,CompanionStyle.ACCENT);
            row.addView(edit);
            row.setOnClickListener(v->editColor(index,label,initial));
            row.setContentDescription("Edit "+label+" hex color, currently "+
                ThemePreferences.hex(initial));
            row.setFocusable(true);row.setClickable(true);
        }
        button(page,"Reset all colors to TGC Default",()->{
            ThemePreferences.reset(this);render(true);
        });
    }
    private void copyPalette(){
        if(!access()){finish();return;}
        ClipboardManager clip=(ClipboardManager)getSystemService(Context.CLIPBOARD_SERVICE);
        if(clip==null){Toast.makeText(this,"Clipboard unavailable",Toast.LENGTH_SHORT).show();return;}
        clip.setPrimaryClip(ClipData.newPlainText("VRChat UI palette",
            ThemePreferences.exportVrchatPalette(this)));
        Toast.makeText(this,"Copied six VRChat UI colors",Toast.LENGTH_SHORT).show();
    }
    private void showImport(){
        if(!access()){finish();return;}
        LinearLayout frame=new LinearLayout(this);
        frame.setPadding(dp(18),dp(8),dp(18),dp(10));
        frame.setOrientation(LinearLayout.VERTICAL);
        frame.addView(t("Highlight → Icons → Buttons → Background → Fonts → Subtext",
            13,CompanionStyle.TEXT));
        gap(frame,9);
        EditText input=CompanionStyle.input(this,"#123456,#234567,#345678,#456789,#56789A,#6789AB");
        input.setSingleLine(false);
        input.setMinLines(3);input.setMaxLines(6);
        input.setInputType(InputType.TYPE_CLASS_TEXT|InputType.TYPE_TEXT_FLAG_MULTI_LINE);
        input.setText(ThemePreferences.exportVrchatPalette(this));
        input.setSelectAllOnFocus(true);
        frame.addView(input,new LinearLayout.LayoutParams(-1,-2));
        gap(frame,7);
        frame.addView(t("All six values are validated before any change is saved. "+
            "The background generates matching card/border shades.",12,CompanionStyle.MUTED));
        AlertDialog dialog=new AlertDialog.Builder(this)
            .setTitle("Import VRChat UI colors").setView(frame)
            .setNegativeButton("Cancel",null)
            .setPositiveButton("Import palette",null).create();
        dialog.setOnShowListener(v->dialog.getButton(DialogInterface.BUTTON_POSITIVE)
            .setOnClickListener(click->{
                if(!access()){dialog.dismiss();finish();return;}
                try{
                    ThemePreferences.importVrchatPalette(this,input.getText().toString());
                    dialog.dismiss();
                    Toast.makeText(this,"Your VRChat palette is now applied",Toast.LENGTH_SHORT).show();
                    render(true);
                }catch(IllegalArgumentException ex){input.setError(ex.getMessage());}
                 catch(Exception ex){input.setError("Could not apply palette: "+ex.getMessage());}
            }));
        dialog.show();
    }
    private void editColor(int index,String name,int initial){
        if(!access()){finish();return;}
        LinearLayout frame=new LinearLayout(this);
        frame.setOrientation(LinearLayout.VERTICAL);
        frame.setPadding(dp(20),dp(6),dp(20),dp(10));
        frame.addView(t("HEX COLOR · #RRGGBB",12,CompanionStyle.MUTED));
        EditText hex=hexInput("#RRGGBB",ThemePreferences.hex(initial));
        frame.addView(hex,new LinearLayout.LayoutParams(-1,-2));
        gap(frame,9);
        TextView preview=t("Preview "+ThemePreferences.hex(initial),14,CompanionStyle.TEXT);
        frame.addView(preview);
        View swatch=new View(this);
        swatch.setBackground(CompanionStyle.shape(this,initial,11,CompanionStyle.BORDER));
        frame.addView(swatch,new LinearLayout.LayoutParams(-1,dp(35)));
        gap(frame,12);
        int[] rgb={Color.red(initial),Color.green(initial),Color.blue(initial)};
        final boolean[] syncing={false};
        final SeekBar[] channels=new SeekBar[3];
        for(int i=0;i<3;i++){
            final int channel=i;
            frame.addView(t(new String[]{"Red","Green","Blue"}[i],13,CompanionStyle.MUTED));
            SeekBar bar=new SeekBar(this);
            bar.setMax(255);bar.setProgress(rgb[i]);
            channels[i]=bar;frame.addView(bar);
            bar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener(){
                public void onProgressChanged(SeekBar seek,int value,boolean fromUser){
                    if(syncing[0])return;
                    rgb[channel]=value;
                    int color=Color.rgb(rgb[0],rgb[1],rgb[2]);
                    syncing[0]=true;
                    hex.setText(ThemePreferences.hex(color));
                    hex.setSelection(hex.getText().length());
                    syncing[0]=false;
                    updatePreview(swatch,preview,color);
                }
                public void onStartTrackingTouch(SeekBar seek){}
                public void onStopTrackingTouch(SeekBar seek){}
            });
        }
        hex.addTextChangedListener(new TextWatcher(){
            public void beforeTextChanged(CharSequence s,int start,int count,int after){}
            public void onTextChanged(CharSequence s,int start,int before,int count){
                if(syncing[0])return;
                try{
                    int parsed=ThemePreferences.parseHex(s.toString());
                    syncing[0]=true;
                    channels[0].setProgress(Color.red(parsed));
                    channels[1].setProgress(Color.green(parsed));
                    channels[2].setProgress(Color.blue(parsed));
                    syncing[0]=false;
                    updatePreview(swatch,preview,parsed);
                    hex.setError(null);
                }catch(IllegalArgumentException ignored){ /* Invalid partial typing is not applied. */ }
            }
            public void afterTextChanged(Editable s){}
        });
        AlertDialog dialog=new AlertDialog.Builder(this)
            .setTitle("Edit "+name)
            .setView(frame)
            .setNegativeButton("Cancel",null)
            .setPositiveButton("Apply hex color",null)
            .create();
        dialog.setOnShowListener(v->dialog.getButton(DialogInterface.BUTTON_POSITIVE)
            .setOnClickListener(click->{
                if(!access()){dialog.dismiss();finish();return;}
                try{
                    ThemePreferences.color(this,index,
                        ThemePreferences.parseHex(hex.getText().toString()));
                    dialog.dismiss();render(true);
                }catch(IllegalArgumentException ex){hex.setError(ex.getMessage());}
            }));
        dialog.show();
    }
    private void updatePreview(View swatch,TextView description,int color){
        swatch.setBackground(CompanionStyle.shape(this,color,11,CompanionStyle.BORDER));
        description.setText("Preview "+ThemePreferences.hex(color));
    }
}
