package com.example.stacklayout.drawer;

import android.view.View;

import androidx.coordinatorlayout.widget.CoordinatorLayout;

import com.google.android.material.bottomsheet.BottomSheetBehavior;

/**
 * Created by chengkai on 2018/4/17 0017.
 */

public class BottomSheetHelper {

    private static final String TAG = BottomSheetHelper.class.getSimpleName();

    public static int getBottomState(View bottomSheet) {
        BottomSheetBehavior bottomSheetBehavior = getBottomBehavior(bottomSheet);
        if (bottomSheetBehavior != null) return bottomSheetBehavior.getState();
        return -100;
    }

    public static BottomSheetBehavior getBottomBehavior(View bottomSheet) {
        if (bottomSheet.getLayoutParams() instanceof CoordinatorLayout.LayoutParams) {
            CoordinatorLayout.LayoutParams layoutParams = (CoordinatorLayout.LayoutParams) bottomSheet.getLayoutParams();
            if (layoutParams.getBehavior() instanceof BottomSheetBehavior) {
                BottomSheetBehavior bottomSheetBehavior = (BottomSheetBehavior) layoutParams.getBehavior();
                return bottomSheetBehavior;
            }
        }
        return null;
    }

    public static boolean isBottomClose(View bottomSheet) {
        BottomSheetBehavior bottomSheetBehavior = getBottomBehavior(bottomSheet);
//        Log.d(TAG, "isBottomClose:"+bottomSheetBehavior.getState());
        return bottomSheetBehavior != null && bottomSheetBehavior.getState() == BottomSheetBehavior.STATE_COLLAPSED;
    }

    public static void closeBottomDrawer(View bottomSheet) {
        BottomSheetBehavior bottomSheetBehavior = getBottomBehavior(bottomSheet);
        if (bottomSheetBehavior != null) bottomSheetBehavior.setState(BottomSheetBehavior.STATE_COLLAPSED);
    }

    public static void openBottomDrawer(View bottomSheet) {
        BottomSheetBehavior bottomSheetBehavior = getBottomBehavior(bottomSheet);
        if (bottomSheetBehavior != null) bottomSheetBehavior.setState(BottomSheetBehavior.STATE_EXPANDED);
    }

}
