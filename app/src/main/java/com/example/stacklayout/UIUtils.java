package com.example.stacklayout;

import android.content.Context;
import android.util.DisplayMetrics;

/**
 * Created by chengkai on 2020/4/16 0016.
 */
public class UIUtils {

    public static int dip2px(Context context, int dip) {
        // dip ---> px

        // 公式 ： px = dp * (dpi / 160)
        // dp = 160 * px / dpi
        // Density = px / dp
        // px = dp * density

        DisplayMetrics metrics = context.getResources().getDisplayMetrics();
        float density = metrics.density;
        return (int) (dip * density + 0.5f);
    }

}
