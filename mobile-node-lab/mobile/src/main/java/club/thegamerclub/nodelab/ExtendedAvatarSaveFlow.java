package club.thegamerclub.nodelab;

import android.app.Activity;
import android.app.AlertDialog;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import org.json.JSONObject;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.concurrent.ExecutorService;

/**
 * Shared no-ID workflow for saving a VRChat avatar into the account-scoped
 * on-device Extended Favorites. Neither Shizuku nor the Relay is involved.
 * Existing local database names and VRChat's own favorite slots are unchanged.
 */
public final class ExtendedAvatarSaveFlow {
    private ExtendedAvatarSaveFlow(){}

    private static int dp(Activity activity,int points){
        return CompanionStyle.dp(activity,points);
    }
    private static void inform(Activity host,String message){
        if(!host.isFinishing()&&!host.isDestroyed())
            Toast.makeText(host,message,Toast.LENGTH_LONG).show();
    }

    /** Calls completion only after a confirmed successful save or category move. */
    public static void open(Activity host,JSONObject avatar,
        ExecutorService executor,Runnable completion){
        if(!StaffGate.unlocked(host)){
            inform(host,"Staff access is required.");return;
        }
        String id=avatar==null?"":avatar.optString("id","");
        if(!ExtendedAvatarStore.validAvatar(id)){
            inform(host,"That avatar is unavailable.");return;
        }
        final String label=avatar.optString("name","Avatar");
        inform(host,"Checking VRC+ and your saved categories…");
        executor.execute(()->{
            String account="",existing=null,error=null;
            List<String> categories=new ArrayList<>();
            try{
                account=VRChatApi.requireActiveVRCPlus(host);
                try(ExtendedAvatarStore db=new ExtendedAvatarStore(host)){
                    existing=db.categoryFor(account,id);
                    categories=db.categories(account);
                }
            }catch(Exception e){
                error=e.getMessage()==null?"Unable to verify your VRC+ subscription":
                    e.getMessage();
            }
            final String user=account,previous=existing,issue=error;
            final List<String> available=categories;
            host.runOnUiThread(()->{
                if(host.isFinishing()||host.isDestroyed()||!StaffGate.unlocked(host))return;
                if(issue!=null){inform(host,"Can't open Extended Favorites: "+issue);return;}
                showCategoryDialog(host,executor,completion,user,id,label,previous,available);
            });
        });
    }

    private static void showCategoryDialog(Activity host,ExecutorService executor,
        Runnable completion,String account,String id,String label,
        String existing,List<String> categories){

        LinkedHashSet<String> values=new LinkedHashSet<>();
        values.add("General");
        if(existing!=null&&!existing.isEmpty())values.add(existing);
        for(String item:categories)if(item!=null&&!item.isEmpty())values.add(item);
        List<String> names=new ArrayList<>(values);
        names.add("+ New category…");
        final int customPosition=names.size()-1;

        LinearLayout form=new LinearLayout(host);
        form.setOrientation(LinearLayout.VERTICAL);
        form.setPadding(dp(host,18),dp(host,11),dp(host,18),dp(host,4));
        TextView info=CompanionStyle.text(host,
            existing==null?
                "Save “"+label+"” to a local Extended Favorites category. This doesn't use a VRChat favorite slot.":
                "“"+label+"” is already saved in “"+existing+"”. Select another category to move it; no duplicate will be created.",
            13,CompanionStyle.MUTED,false);
        form.addView(info);
        Spinner spinner=new Spinner(host);
        ArrayAdapter<String> adapter=new ArrayAdapter<>(host,
            android.R.layout.simple_spinner_item,names);
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinner.setAdapter(adapter);
        LinearLayout.LayoutParams sp=new LinearLayout.LayoutParams(-1,dp(host,52));
        sp.topMargin=dp(host,11);
        form.addView(spinner,sp);
        int initial=existing==null?0:names.indexOf(existing);
        spinner.setSelection(Math.max(0,initial));
        EditText custom=CompanionStyle.input(host,"New category name");
        LinearLayout.LayoutParams customParams=new LinearLayout.LayoutParams(-1,dp(host,54));
        customParams.topMargin=dp(host,9);
        form.addView(custom,customParams);
        custom.setVisibility(View.GONE);
        spinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener(){
            @Override public void onItemSelected(AdapterView<?> parent,View v,int pos,long key){
                custom.setVisibility(pos==customPosition?View.VISIBLE:View.GONE);
            }
            @Override public void onNothingSelected(AdapterView<?> parent){}
        });
        AlertDialog dialog=new AlertDialog.Builder(host)
            .setTitle(existing==null?"Save to Extended Favorites":"Move saved avatar")
            .setView(form)
            .setNegativeButton("Cancel",null)
            .setPositiveButton(existing==null?"Save":"Move",null)
            .create();
        dialog.setOnShowListener(d->dialog.getButton(AlertDialog.BUTTON_POSITIVE)
            .setOnClickListener(v->{
                int position=spinner.getSelectedItemPosition();
                if(position<0)return;
                String category=position==customPosition?
                    custom.getText().toString().trim():names.get(position);
                if(position==customPosition&&category.isEmpty()){
                    custom.setError("Enter a new category name.");return;
                }
                try{category=ExtendedAvatarStore.category(category);}
                catch(Exception e){custom.setError(e.getMessage());return;}
                final String selected=category;
                if(existing!=null&&existing.equals(selected)){
                    inform(host,"Already saved in "+selected+".");
                    return;
                }
                dialog.getButton(AlertDialog.BUTTON_POSITIVE).setEnabled(false);
                executor.execute(()->{
                    String error=null;
                    boolean moved=false,same=false;
                    try{
                        String verified=VRChatApi.requireActiveVRCPlus(host);
                        if(!account.equals(verified))
                            throw new SecurityException("Your VRChat account changed. Try again.");
                        try(ExtendedAvatarStore db=new ExtendedAvatarStore(host)){
                            String current=db.categoryFor(account,id);
                            if(selected.equals(current))same=true;
                            else if(current!=null){
                                db.move(account,id,selected);
                                moved=true;
                            }else{
                                JSONObject detail=VRChatApi.avatar(host,id);
                                if(!id.equals(detail.optString("id","")))
                                    throw new SecurityException("VRChat returned an unexpected avatar.");
                                db.add(account,detail,selected);
                            }
                        }
                    }catch(Exception e){
                        error=e.getMessage()==null?e.getClass().getSimpleName():e.getMessage();
                    }
                    final String issue=error;
                    final boolean wasMove=moved,wasSame=same;
                    host.runOnUiThread(()->{
                        if(host.isFinishing()||host.isDestroyed())return;
                        if(issue!=null){
                            inform(host,"Unable to save: "+issue);
                            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setEnabled(true);
                            return;
                        }
                        dialog.dismiss();
                        inform(host,wasSame?"Already saved in "+selected:
                            wasMove?"Moved to "+selected:"Saved to "+selected);
                        if(completion!=null && StaffGate.unlocked(host))completion.run();
                    });
                });
            }));
        dialog.show();
    }
}
