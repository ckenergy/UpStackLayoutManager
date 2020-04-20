package com.example.stacklayout

import android.os.Bundle
import android.util.Log
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import androidx.coordinatorlayout.widget.CoordinatorLayout
import com.example.stacklayout.drawer.AboveDrawerBehavior
import com.example.stacklayout.stacklayout.StackLayoutManager
import kotlinx.android.synthetic.main.activity_upward.*

class UpwardActivity : AppCompatActivity() {

    lateinit var mStackLayoutManager: StackLayoutManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_upward)

        val size = intent.getIntExtra(PARAM, 0)

        mStackLayoutManager = StackLayoutManager()
        mStackLayoutManager.isUseVisibleHeight = true
        mStackLayoutManager.diffOffset = UIUtils.dip2px(this, 78)
        recyler_view.layoutManager = mStackLayoutManager
        recyler_view.adapter = StackAdapter(this, size)

        setBottomDrawerOffset(above_scroll)
    }

    /**
     * 滑动监听
     */
    protected fun setBottomDrawerOffset(mAboveScroll: View) {
        if (mAboveScroll.layoutParams is CoordinatorLayout.LayoutParams) {
            val layoutParams: CoordinatorLayout.LayoutParams =
                mAboveScroll.layoutParams as CoordinatorLayout.LayoutParams
            if (layoutParams.getBehavior() is AboveDrawerBehavior) {
                val behavior: AboveDrawerBehavior =
                    layoutParams.getBehavior() as AboveDrawerBehavior
                behavior.setOnOffsetChangedListener(object :
                    AboveDrawerBehavior.OnOffsetChangedListener {

                    var listOffset = UIUtils.dip2px(this@UpwardActivity, 50)

                    override fun onOffsetChange(
                        countOffset: Int,
                        scrollOffset: Int,
                        scale: Float
                    ) {
                        Log.d(
                            TAG,
                            "onOffsetChange count:$countOffset,scroll:$scrollOffset,scale:$scale"
                        )
                        Log.d(
                            TAG,
                            "onOffsetChange isBottom:" + behavior.isClose()
                        )
                        //                        Log.d(TAG, "onOffsetChange mRecyclerView:" + mRecyclerView.getHeight());
                        //滑动事件传递

                        //设置LayoutManager的高度来展示叠加效果
                        val initHeight: Int = behavior.minHeight - listOffset
                        mStackLayoutManager.visibleHeight = initHeight + Math.abs(scrollOffset)
                    }
                })
            }
        }
    }

    companion object {
        val TAG = UpwardActivity::class.java.getSimpleName()
        val PARAM = "UpwardActivity_PARAM"
    }

}
