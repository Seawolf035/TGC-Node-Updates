package club.thegamerclub.nodelab;

import android.app.Activity;
import android.app.Application;
import android.content.Intent;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
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
        install(activity);
    }

    private static int dp(Activity activity,float n){
        return (int)(n*activity.getResources().getDisplayMetrics().density+0.5f);
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
            dp(activity,104),dp(activity,49),Gravity.END|Gravity.BOTTOM);
        params.rightMargin=dp(activity,12);
        // Keep clear of the Android nav bar and Companion bottom tab bar.
        params.bottomMargin=dp(activity,85);
        root.addView(back,params);
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
