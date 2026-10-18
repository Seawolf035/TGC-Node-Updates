package club.thegamerclub.nodelab;

import android.content.Context;
import android.text.InputFilter;
import android.text.InputType;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;
import java.time.LocalDate;

/** Self-contained, offline age-range utility for phone and Quest floating tools. */
public final class AgeCheckTool {
    private AgeCheckTool(){}

    /**
     * The optional keyboard callbacks are needed by the Android phone overlay,
     * whose window must temporarily become focusable for numeric entry.
     */
    public static void attach(Context context,LinearLayout parent,
            Runnable enableKeyboard,Runnable dismissKeyboard){
        parent.addView(CompanionStyle.text(context,"AGE CHECK TOOL",15,
            CompanionStyle.ACCENT,true));
        parent.addView(CompanionStyle.text(context,
            "Enter the age someone claims to be. See exactly which birth months "+
            "and dates could match that age today.",12,CompanionStyle.MUTED,false));
        EditText age=CompanionStyle.input(context,"Claimed age (e.g. 15)");
        age.setInputType(InputType.TYPE_CLASS_NUMBER);
        age.setFilters(new InputFilter[]{new InputFilter.LengthFilter(3)});
        age.setContentDescription("Claimed age in whole years");
        LinearLayout.LayoutParams field=new LinearLayout.LayoutParams(-1,-2);
        field.topMargin=CompanionStyle.dp(context,9);
        field.bottomMargin=CompanionStyle.dp(context,9);
        parent.addView(age,field);
        if(enableKeyboard!=null){
            age.setOnClickListener(v->{enableKeyboard.run();age.requestFocus();});
            age.setOnFocusChangeListener((v,hasFocus)->{
                if(hasFocus)enableKeyboard.run();
            });
        }
        TextView result=CompanionStyle.text(context,
            "Enter an age, then tap Calculate.",12,CompanionStyle.MUTED,false);
        result.setTextIsSelectable(true);
        Button calculate=CompanionStyle.button(context,"Calculate possible birthdays",true);
        parent.addView(calculate,CompanionStyle.buttonParams(context));
        parent.addView(result);
        calculate.setOnClickListener(v->{
            if(dismissKeyboard!=null)dismissKeyboard.run();
            String value=age.getText().toString().trim();
            try{
                if(value.isEmpty())throw new IllegalArgumentException("Enter an age first.");
                int years=Integer.parseInt(value);
                AgeCheckMath.Range range=AgeCheckMath.range(years,LocalDate.now());
                result.setText(range.summary());
                result.setTextColor(CompanionStyle.TEXT);
            }catch(IllegalArgumentException ex){
                result.setText(ex.getMessage()==null?"Enter an age from 0 to 120.":ex.getMessage());
                result.setTextColor(CompanionStyle.GOLD);
            }
        });
        parent.addView(CompanionStyle.text(context,
            "Offline only · No birthdate history is stored or sent. "+
            "Use as a conversation consistency check, not proof of identity or age.",
            11,CompanionStyle.MUTED,false));
    }
}
