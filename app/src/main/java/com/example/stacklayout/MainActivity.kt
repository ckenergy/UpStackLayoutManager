package com.example.stacklayout

import android.content.Context
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import androidx.appcompat.app.AppCompatActivity
import androidx.coordinatorlayout.widget.CoordinatorLayout
import androidx.core.view.ViewCompat
import androidx.recyclerview.widget.RecyclerView
import com.example.stacklayout.drawer.AboveDrawerBehavior
import com.example.stacklayout.vagelayout.VegaLayoutManager
import kotlinx.android.synthetic.main.activity_main.*
import java.security.AccessControlContext

class MainActivity : AppCompatActivity() {

    lateinit var mVegaLayoutManager: VegaLayoutManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        mVegaLayoutManager = VegaLayoutManager()
        mVegaLayoutManager.isUseVisibleHeight = true
        recyler_view.layoutManager = mVegaLayoutManager
        recyler_view.adapter = Adapter(this)

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

                    var listOffset = UIUtils.dip2px(this@MainActivity, 50)

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
                        mVegaLayoutManager.visibleHeight = initHeight + Math.abs(scrollOffset)
                    }
                })
            }
        }
    }

    companion object {
        val TAG = MainActivity::class.java.getSimpleName()
    }

    class Adapter(private val context: Context) : RecyclerView.Adapter<Adapter.Holder>() {

        var list:List<Int> = listOf(R.mipmap.riding1,R.mipmap.riding2,R.mipmap.riding3,R.mipmap.riding4)

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): Adapter.Holder {
            val view = LayoutInflater.from(context)
                .inflate(R.layout.item_image, parent, false)
            return Holder(view)
        }

        override fun getItemCount(): Int {
            return list.size
        }

        override fun onBindViewHolder(holder: Adapter.Holder, position: Int) {
            holder.image.setImageResource(list.get(position))
            ViewCompat.setElevation(holder.image, (5-position).toFloat())
        }

        class Holder(itemView: View) : RecyclerView.ViewHolder(itemView) {
            var image:ImageView = itemView.findViewById(R.id.item_iv)
        }

    }

}
