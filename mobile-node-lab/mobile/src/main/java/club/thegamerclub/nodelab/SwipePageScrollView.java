package club.thegamerclub.nodelab;

import android.content.Context;
import android.view.MotionEvent;
import android.widget.ScrollView;

/**
 * Vertical content scroll with deliberate horizontal page navigation.
 * A horizontal gesture must beat vertical movement by 1.5x; ordinary
 * scrolling, taps and clickable cards retain their existing behavior.
 */
public final class SwipePageScrollView extends ScrollView {
    public interface Listener {
        /** -1 moves to the previous page, +1 to the next page. */
        void onSwipe(int direction);
    }

    private final Listener listener;
    private float startX,startY;
    private boolean horizontal=false,dispatched=false;
    private final int startThreshold,finishThreshold;

    public SwipePageScrollView(Context context,Listener listener){
        super(context);
        this.listener=listener;
        startThreshold=CompanionStyle.dp(context,42);
        finishThreshold=CompanionStyle.dp(context,78);
        setFillViewport(true);
    }

    @Override public boolean onInterceptTouchEvent(MotionEvent e){
        switch(e.getActionMasked()){
            case MotionEvent.ACTION_DOWN:
                startX=e.getX();
                startY=e.getY();
                horizontal=false;dispatched=false;
                break;
            case MotionEvent.ACTION_MOVE:
                float dx=e.getX()-startX,dy=e.getY()-startY;
                if(!horizontal && Math.abs(dx)>startThreshold &&
                    Math.abs(dx)>Math.abs(dy)*1.5f){
                    horizontal=true;
                    return true;
                }
                break;
            case MotionEvent.ACTION_CANCEL:
                horizontal=false;
                break;
            default:
                break;
        }
        return super.onInterceptTouchEvent(e);
    }

    @Override public boolean onTouchEvent(MotionEvent e){
        if(horizontal){
            switch(e.getActionMasked()){
                case MotionEvent.ACTION_UP:
                    float dx=e.getX()-startX,dy=e.getY()-startY;
                    if(!dispatched && Math.abs(dx)>=finishThreshold &&
                        Math.abs(dx)>Math.abs(dy)*1.5f){
                        dispatched=true;
                        if(listener!=null)listener.onSwipe(dx<0f?1:-1);
                    }
                    horizontal=false;
                    return true;
                case MotionEvent.ACTION_CANCEL:
                    horizontal=false;
                    return true;
                default:return true;
            }
        }
        return super.onTouchEvent(e);
    }
}
