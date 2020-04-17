package com.example.stacklayout.drawer;

import android.content.Context;
import android.util.AttributeSet;
import android.widget.RelativeLayout;

import androidx.annotation.AttrRes;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

/**
 * Created by chengkai on 2018/2/9 0009.
 */

public class BottomDrawerLayout extends RelativeLayout implements AboveDrawerBehavior.OnOffsetChangedListener{

    public AboveDrawerBehavior.OnOffsetChangedListener getOnOffsetChangedListener() {
        return mOnOffsetChangedListener;
    }

    public void setOnOffsetChangedListener(AboveDrawerBehavior.OnOffsetChangedListener onOffsetChangedListener) {
        mOnOffsetChangedListener = onOffsetChangedListener;
    }

    AboveDrawerBehavior.OnOffsetChangedListener mOnOffsetChangedListener;

    public BottomDrawerLayout(@NonNull Context context) {
        this(context, null);
    }

    public BottomDrawerLayout(@NonNull Context context, @Nullable AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public BottomDrawerLayout(@NonNull Context context, @Nullable AttributeSet attrs, @AttrRes int defStyleAttr) {
        super(context, attrs, defStyleAttr);
    }

    @Override
    public void onOffsetChange(int countOffset, int scrollOffset, float scale) {
        if (mOnOffsetChangedListener != null) {
            mOnOffsetChangedListener.onOffsetChange(countOffset, scrollOffset, scale);
        }
    }

}
