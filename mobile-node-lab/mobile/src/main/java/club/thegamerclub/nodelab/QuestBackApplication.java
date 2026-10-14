package club.thegamerclub.nodelab;

import android.app.Activity;
import android.app.Application;
import android.content.Intent;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import android.widget.Button;
import android.widget.FrameLayout;

/**
 * A universal in-app Back control on Quest only.
 *
 * Placed inside each Activity's own decor frame, not in a system overlay or
 * floating service. The activity's existing onBackPressed() implementation
 * owns navigation: Quest Tools returns within its nested page history,
 * Companion goes through its tabs, and detail Activities return to caller.
 * No permissions are requested and the Android phone experience is unchanged.
 */
public final class QuestBackApplication extends Application
        implements Application.ActivityLifecycleCallbacks {
    private static final int BACK_BUTTON_ID=0x7f0fa119;
    private boolean licensePromptInFlight=false;

    @Override public void onCreate(){
        super.onCreate();
        registerActivityLifecycleCallbacks(this);
    }

    @Override public void onActivityResumed(Activity activity){
        if(activity.isFinishing()||activity.isDestroyed())return;
        // Both Quest and Android phone users must explicitly accept the
        // proprietary staff agreement before interacting with app screens.
        // This is an app-local terms screen, never a floating overlay.
        if(activity instanceof CompanionLicenseActivity)return;
        // Exception ONLY for the self-updater, so a broken/outdated agreement
        // cannot prevent installing a repaired signed APK. This Activity is
        // internal (not exported) and performs no staff/VRChat operations.
        // Never interpret opening or completing an update as license consent.
        // When consent already exists, retain normal Quest Back injection.
        if(activity instanceof MobileUpdaterActivity &&
            !CompanionLicense.accepted(activity))return;
        if(!CompanionLicense.accepted(activity)){
            if(!licensePromptInFlight){
                licensePromptInFlight=true;
                try{
                    activity.startActivity(new Intent(activity,
                        CompanionLicenseActivity.class));
                }catch(Exception e){
                    licensePromptInFlight=false;
                    activity.finish();
                }
            }
            return;
        }
        licensePromptInFlight=false;
        if(!QuestDevice.isQuestDevice())return;
        refresh(activity);
    }

    private static int dp(Activity activity,float n){
        return (int)(n*activity.getResources().getDisplayMetrics().density+0.5f);
    }
    /** Synchronize after EVERY in-Activity page transition, not just onResume. */
    public static void refresh(Activity activity){
        if(activity==null||activity.isFinishing()||activity.isDestroyed()||
           !QuestDevice.isQuestDevice())return;
        if(!(activity.getApplication() instanceof QuestBackApplication))return;
        ((QuestBackApplication)activity.getApplication()).update(activity);
    }
    private void update(Activity activity){
        View content=activity.findViewById(android.R.id.content);
        if(!(activity.getWindow().getDecorView() instanceof FrameLayout))return;
        FrameLayout root=(FrameLayout)activity.getWindow().getDecorView();
        boolean show=!(activity instanceof QuestBackHost) ||
            ((QuestBackHost)activity).showQuestBack();
        View existing=root.findViewById(BACK_BUTTON_ID);
        // Restore the real content height at navigation roots, including
        // Companion Home and every top-level Quest Tools tab.
        if(content!=null){
            int top=show?dp(activity,60):0;
            if(content.getPaddingTop()!=top)
                content.setPadding(content.getPaddingLeft(),top,
                    content.getPaddingRight(),content.getPaddingBottom());
            content.setBackgroundColor(CompanionStyle.BG);
        }
        if(!show){
            if(existing!=null)existing.setVisibility(View.GONE);
            return;
        }
        if(existing==null)install(activity);
        else{existing.setVisibility(View.VISIBLE);existing.bringToFront();}
    }
    private void install(Activity activity){
        // DecorView extends FrameLayout. Use a regular Activity view rather
        // than requesting draw-over-other-apps permission (forbidden on Quest).
        View decor=activity.getWindow().getDecorView();
        if(!(decor instanceof FrameLayout))return;
        FrameLayout root=(FrameLayout)decor;
        View previous=root.findViewById(BACK_BUTTON_ID);
        if(previous!=null)return;

        Button back=CompanionStyle.button(activity,"← Back",false);
        back.setId(BACK_BUTTON_ID);
        back.setTextSize(13);
        back.setContentDescription("Back to previous Companion page");
        back.setElevation(dp(activity,9));
        FrameLayout.LayoutParams params=new FrameLayout.LayoutParams(
            dp(activity,104),dp(activity,49),Gravity.TOP|Gravity.START);
        params.leftMargin=dp(activity,12);
        params.topMargin=dp(activity,8);
        root.addView(back,params);
        // Place the button below any Quest system bar/cutout, and keep that
        // position correct if window insets change. This is a normal app view,
        // not a floating system overlay.
        ViewCompat.setOnApplyWindowInsetsListener(back,(v,insets)->{
            Insets safe=insets.getInsets(WindowInsetsCompat.Type.systemBars() |
                WindowInsetsCompat.Type.displayCutout());
            FrameLayout.LayoutParams p=(FrameLayout.LayoutParams)v.getLayoutParams();
            int left=dp(activity,12)+safe.left;
            int top=dp(activity,8)+safe.top;
            if(p.leftMargin!=left || p.topMargin!=top){
                p.leftMargin=left;p.topMargin=top;
                v.setLayoutParams(p);
            }
            return insets;
        });
        ViewCompat.requestApplyInsets(back);
        back.bringToFront();
        back.setOnClickListener(v->{
            if(activity.isFinishing()||activity.isDestroyed())return;
            // Existing per-Activity back handling, including Quest Tools' page
            // stack, is invoked instead of always finishing the activity.
            activity.onBackPressed();
        });
    }

    @Override public void onActivityCreated(Activity activity,Bundle state){}
    @Override public void onActivityStarted(Activity activity){}
    @Override public void onActivityPaused(Activity activity){}
    @Override public void onActivityStopped(Activity activity){}
    @Override public void onActivitySaveInstanceState(Activity activity,Bundle out){}
    @Override public void onActivityDestroyed(Activity activity){
        if(activity instanceof CompanionLicenseActivity)
            licensePromptInFlight=false;
    }
}
