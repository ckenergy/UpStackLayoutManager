package com.example.stacklayout.drawer;

import android.content.Context;
import android.util.AttributeSet;
import android.util.Log;
import android.view.View;

import androidx.coordinatorlayout.widget.CoordinatorLayout;
import com.google.android.material.bottomsheet.BottomSheetBehavior;
import androidx.core.view.ViewCompat;

/**
 * Created by chengkai on 2018/2/8 0008.
 */

public class AboveDrawerBehavior extends CoordinatorLayout.Behavior<View> {//这里的泛型是child的类型，也就是观察者View

    private static final String TAG = AboveDrawerBehavior.class.getSimpleName();

    boolean hasInit = false;
    int mTop = 0;
    int mInitTop = -1;
    int mCountOffset = 0;

    int mMinHeight = 0;

    View mDependency;

    boolean isClose = false;

    OnOffsetChangedListener mOnOffsetChangedListener;

    public int getMinHeight() {
        return mMinHeight;
    }

    public void setMinHeight(int minHeight) {
        mMinHeight = minHeight;
    }

    public void setOnOffsetChangedListener(OnOffsetChangedListener onOffsetChangedListener) {
        mOnOffsetChangedListener = onOffsetChangedListener;
    }

    public AboveDrawerBehavior(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    /**
     *
     * @param parent
     * @param child
     * @param dependency
     * @return 需要动态的设置，设置过 DrawerBehavior 的View类型, dependency instanceof RelativeLayout
     *
     */
    @Override
    public boolean layoutDependsOn(CoordinatorLayout parent, View child, View dependency) {
        if(dependency.getLayoutParams() instanceof CoordinatorLayout.LayoutParams) {
            CoordinatorLayout.LayoutParams layoutParams = (CoordinatorLayout.LayoutParams) dependency.getLayoutParams();
            if (layoutParams.getBehavior() instanceof BottomSheetBehavior
                    /*|| layoutParams.getBehavior() instanceof MyBottomSheetBehavior*/) {
                mDependency = dependency;
                int minheight = 0;
                boolean found = false;
                if (layoutParams.getBehavior() instanceof BottomSheetBehavior) {
                    BottomSheetBehavior bottomSheetBehavior = (BottomSheetBehavior) layoutParams.getBehavior();
                    minheight = bottomSheetBehavior.getPeekHeight();
                    found = true;
                }
//                if (layoutParams.getBehavior() instanceof MyBottomSheetBehavior) {
//                    MyBottomSheetBehavior bottomSheetBehavior = (MyBottomSheetBehavior) layoutParams.getBehavior();
//                    minheight = bottomSheetBehavior.getPeekHeight();
//                    Log.d(TAG, "layoutDependsOn minheight:"+minheight);
//                    found = true;
//                }

                if (!found) {
                    minheight = mDependency.getMinimumHeight();
                }
                setMinHeight(minheight);
                Log.d(TAG, "layoutDependsOn parentHeight:"+parent.getHeight());
                mInitTop = parent.getHeight() - getMinHeight();//mDependency.getTop();
                return true;
            }
        }
        return false;
    }

    @Override
    public boolean onDependentViewChanged(CoordinatorLayout parent, View child, View dependency) {
        Log.d(TAG, "onDependentViewChanged hasInit:"+hasInit+", state:"+BottomSheetHelper.getBottomState(mDependency));
        if (dependency.getTop() > mInitTop) return true;

        isClose = dependency.getTop() == mInitTop;
        if (!hasInit) {
            hasInit = true;
            Log.d(TAG, "mInitTop:"+mInitTop+", mCountOffset:"+mCountOffset);
        }

        mCountOffset = mDependency.getMeasuredHeight() - getMinHeight();

        mTop = dependency.getTop();
        int mScroll = mTop - mInitTop;

        if (mOnOffsetChangedListener != null) {
            float scale = Math.abs(1.0f * mScroll / mCountOffset);
            mOnOffsetChangedListener.onOffsetChange(mCountOffset, mScroll, scale);
        }
        Log.d(TAG, "mTop:"+ mTop+",mCountOffset:"+mCountOffset+",minHeight:"+dependency.getMinimumHeight()+", mScroll:"+mScroll);
//        child.offsetTopAndBottom(-5);

//        ViewCompat.offsetTopAndBottom(child, -5);
        return true;
    }

    public boolean isClose() {
        return isClose;
    }

    public void reInit() {
        hasInit = false;
    }

    @Override
    public boolean onLayoutChild(CoordinatorLayout parent, View child, int layoutDirection) {
        boolean hasLayout = true;

        Log.d(TAG, ""+mDependency);

        if (mDependency != null) {

            int childWidth = child.getMeasuredWidth();
            int childHeight = child.getMeasuredHeight();

            CoordinatorLayout.LayoutParams lp = (CoordinatorLayout.LayoutParams) child.getLayoutParams();

            Log.d(TAG, "mDependency top:"+mDependency.getTop()+", bottomMargin:"+lp.bottomMargin+",childHeight:"+childHeight);

            child.layout(lp.leftMargin, mDependency.getTop() - childHeight - lp.bottomMargin,
                    childWidth + lp.leftMargin,
                    mDependency.getTop() - lp.bottomMargin);
        }else {
            hasLayout = false;
        }

        return child.getVisibility() == View.GONE //
                || hasLayout;
    }

    public interface OnOffsetChangedListener {

        /**
         *
         * @param countOffset 总的滑动长度
         * @param scrollOffset 当前滑动的长度
         * @param scale 滑动的比例
         */
        public void onOffsetChange(int countOffset, int scrollOffset, float scale);

    }

}
