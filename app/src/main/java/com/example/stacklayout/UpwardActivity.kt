package com.example.stacklayout

import android.os.Bundle
import android.util.Log
import android.view.View
import android.view.View.OnTouchListener
import androidx.appcompat.app.AppCompatActivity
import androidx.coordinatorlayout.widget.CoordinatorLayout
import androidx.core.view.ViewCompat
import com.example.stacklayout.drawer.BottomSheetHelper
import com.example.stacklayout.stacklayout.UpStackLayoutManager
import com.google.android.material.bottomsheet.BottomSheetBehavior
import kotlinx.android.synthetic.main.activity_upward.*

class UpwardActivity : AppCompatActivity() {

    lateinit var mUpStackLayoutManager: UpStackLayoutManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_upward)

        val size = intent.getIntExtra(PARAM, 0)

        mUpStackLayoutManager =
            UpStackLayoutManager()
        mUpStackLayoutManager.isUseVisibleHeight = true
        mUpStackLayoutManager.diffOffset = UIUtils.dip2px(this, 90)
        recyler_view.layoutManager = mUpStackLayoutManager
        recyler_view.adapter = StackAdapter(this, size)

        setBottomDrawerOffset(bottom_sheet)

        close.setOnClickListener { BottomSheetHelper.closeBottomDrawer(bottom_sheet) }
        close.translationY = -UIUtils.dip2px(this@UpwardActivity, 70).toFloat()
    }

    /**
     * 上滑显示蒙尘的时候不能移动
     */
    private fun setCantMove() {
        overlay.setOnTouchListener(OnTouchListener { v, event -> true })
    }

    private fun hideOnScroll(view: View, height: Int) {
        ViewCompat.animate(view).translationY(-height.toFloat()).setDuration(300).start()
    }

    private fun showOnScroll(view: View) {
        view.visibility = View.VISIBLE
        ViewCompat.animate(view).translationY(0f).setDuration(300).start()
    }

    /**
     * 滑动监听
     */
    protected fun setBottomDrawerOffset(aboveScroll: View) {
        if (aboveScroll.layoutParams is CoordinatorLayout.LayoutParams) {
            val layoutParams: CoordinatorLayout.LayoutParams =
                aboveScroll.layoutParams as CoordinatorLayout.LayoutParams
            if (layoutParams.getBehavior() is BottomSheetBehavior) {
                val behavior = layoutParams.getBehavior() as BottomSheetBehavior
                //初始的高度
                val initHeight = behavior.peekHeight - mUpStackLayoutManager.diffOffset
                mUpStackLayoutManager.visibleHeight = initHeight
                behavior.addBottomSheetCallback(object : BottomSheetBehavior.BottomSheetCallback() {

                    override fun onSlide(bottomSheet: View, slideOffset: Float) {
                        if (!BottomSheetHelper.isBottomExpand(bottomSheet) && slideOffset > 0) {
                            val scrollLength = recyler_view.height - initHeight
                            val currentLength = (slideOffset * scrollLength).toInt()
                            mUpStackLayoutManager.visibleHeight = initHeight + currentLength
                            Log.d(TAG, "onSlide slideOffset:"+slideOffset+", visibleHeight:"+mUpStackLayoutManager.visibleHeight)
                            if(slideOffset > 0.5) {
                                showOnScroll(close)
                            }else{
                                hideOnScroll(close, close.height + UIUtils.dip2px(this@UpwardActivity, 30))
                            }

                            //上滑的蒙尘
                            overlay.setVisibility(if (slideOffset == 0f) View.INVISIBLE else View.VISIBLE)
                            overlay.setAlpha(slideOffset)
                        }
                    }

                    override fun onStateChanged(bottomSheet: View, newState: Int) {
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
