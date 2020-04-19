package com.example.stacklayout.vagelayout;

import android.graphics.Rect;
import android.util.Log;
import android.util.SparseArray;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewTreeObserver;

import androidx.annotation.NonNull;
import androidx.collection.ArrayMap;
import androidx.coordinatorlayout.widget.CoordinatorLayout;
import androidx.recyclerview.widget.RecyclerView;

import com.example.stacklayout.UIUtils;
import com.example.stacklayout.drawer.AboveDrawerBehavior;


/**
 * Created by xmuSistone on 2017/9/20.
 */
public class StackLayoutManager extends RecyclerView.LayoutManager {

    private static final String TAG = StackLayoutManager.class.getSimpleName();


    private int scroll = 0;

    /**底部用了展示接下来view的高度*/
    private int bottomOffset = 0;
    private int bottomLastOffset = 0;

//    private SparseArray<Rect> locationRects = new SparseArray<>();
    private SparseArray<StateHolder> stateList = new SparseArray<>();
    private ArrayMap<Integer, Integer> viewTypeHeightMap = new ArrayMap<>();

    SparseArray<View> visibleView = new SparseArray<>();

    private boolean needSnap = false;
    private int lastDy = 0;
    private int maxScroll = -1;
    private RecyclerView.Adapter adapter;
    private RecyclerView.Recycler recycler;

    private int mFixHeight;
    private boolean mNeedFixHeight = true;

    public int totalRange = 0;

    private boolean mUseVisibleHeight = false;
    private int mMinScrollHeight = 0;

    private RecyclerView mRecyclerView;

    float h_elevation;
    float m_elevation;
    float l_elevation;

    float a1, a2, b2;

    public StackLayoutManager() {
        setAutoMeasureEnabled(true);
    }

    public boolean isUseVisibleHeight() {
        return mUseVisibleHeight;
    }

    /**
     * useVisibleHeight 设置为true时，建议RecyclerView为 CoordinatorLayout 的直接子view，
     * 不可嵌套多层，如果RecyclerView为WRAP_CONTENT的话，
     * 从不可见到可见时滑动，发生抖动时建议将 useVisibleHeight 设置为false时,
     * 或者去setRecyclerViewHeight方法自己判断各种需要减去的高度
     * @param useVisibleHeight
     */
    public void setUseVisibleHeight(boolean useVisibleHeight) {
        this.mUseVisibleHeight = useVisibleHeight;
    }

    public int getFixHeight() {
        return mFixHeight;
    }

    public void setFixHeight(int fixHeight) {
        mFixHeight = fixHeight;
    }

    @Override
    public RecyclerView.LayoutParams generateDefaultLayoutParams() {
        return new RecyclerView.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
    }

    @Override
    public void onAdapterChanged(RecyclerView.Adapter oldAdapter, RecyclerView.Adapter newAdapter) {
        super.onAdapterChanged(oldAdapter, newAdapter);
        this.adapter = newAdapter;
        mNeedFixHeight = true;
    }

    @Override
    public void onLayoutChildren(RecyclerView.Recycler recycler, RecyclerView.State state) {
        Log.d(TAG, "onLayoutChildren");
        this.recycler = recycler; // 二话不说，先把recycler保存了
        if (state.isPreLayout()) {
            return;
        }
        buildLocationRects();
        layoutItems(true);
    }

    boolean needRebuildRect = true;

    private void buildLocationRects() {
//        if (!needRebuildRect) return;

        Log.d(TAG, "buildLocationRects");
        needRebuildRect = false;
        stateList.clear();

        int tempPosition = getPaddingTop();
        int itemCount = getItemCount();
        int lastBottom = 0;
        for (int i = 0; i < itemCount; i++) {
//        for (int i = itemCount -1; i >= 0; i--) {
            // 1. 先计算出itemWidth和itemHeight
            int viewType = adapter.getItemViewType(i);
            int itemHeight;
            if (viewTypeHeightMap.containsKey(viewType)) {
                itemHeight = viewTypeHeightMap.get(viewType);
            } else {
                // TODO: 2020/4/17 0017  获取view的margin
                View itemView = recycler.getViewForPosition(i);
                addView(itemView);
                measureChildWithMargins(itemView, View.MeasureSpec.UNSPECIFIED, View.MeasureSpec.UNSPECIFIED);
                itemHeight = getDecoratedMeasuredHeight(itemView);
                viewTypeHeightMap.put(viewType, itemHeight);
            }

            StateHolder holder = new StateHolder();
            // 2. 组装Rect并保存
            Rect rect = new Rect();
            rect.left = getPaddingLeft();
            rect.top = tempPosition;
            rect.right = getWidth() - getPaddingRight();
            rect.bottom = rect.top + itemHeight;
            holder.rect = rect;

            if (i == 0) {
                mMinScrollHeight = rect.height() + bottomOffset;
                holder.scrollBottom = lastBottom + rect.height();//初始化bottom
            }else if (i==1){
                holder.scrollBottom = lastBottom + bottomOffset/2;//初始化bottom
            }else if(i == 2) {
                holder.scrollBottom = lastBottom + bottomOffset/2 + bottomLastOffset;//初始化bottom
            }else {
                holder.scrollBottom = lastBottom + bottomLastOffset;//初始化bottom
            }
            lastBottom = holder.scrollBottom;
            stateList.put(i, holder);
            tempPosition = tempPosition + itemHeight;
        }

        totalRange = tempPosition;
        if (mUseVisibleHeight) {
            mRecyclerView.getViewTreeObserver().addOnGlobalLayoutListener(new ViewTreeObserver.OnGlobalLayoutListener() {
                @Override
                public void onGlobalLayout() {
                    setRecyclerViewHeight(mRecyclerView, totalRange);
                    mRecyclerView.getViewTreeObserver().removeOnGlobalLayoutListener(this);
                }
            });
        }

        if (itemCount == 0) {
            maxScroll = 0;
        } else {
            computeMaxScroll();
        }
    }

    protected void setRecyclerViewHeight(RecyclerView recyclerView, int totalRange) {
        ViewGroup.LayoutParams layoutParams = recyclerView.getLayoutParams();
        if (layoutParams.height == ViewGroup.LayoutParams.WRAP_CONTENT
                || layoutParams.height == ViewGroup.LayoutParams.MATCH_PARENT || mNeedFixHeight) {
            mNeedFixHeight = false;
            CoordinatorLayout coordinatorLayout = findCoordinatorLayout(mRecyclerView);
            int maxHeight = -1;//可上升的高度
            int offset = UIUtils.dip2px(mRecyclerView.getContext(), 133);//减去开锁按钮的高度
            if (coordinatorLayout != null) {
                Rect rect = new Rect();
                coordinatorLayout.getGlobalVisibleRect(rect);
                Log.d(TAG, "setRecyclerViewHeight rect:"+rect.toString());
                maxHeight = rect.height() - offset;
                Log.d(TAG, "setRecyclerViewHeight height1:"+maxHeight);
            }
            if (maxHeight <= 0) {
                maxHeight = mRecyclerView.getResources().getDisplayMetrics().heightPixels - offset;
            }
            int height = mRecyclerView.getHeight();
            Log.d(TAG, "setRecyclerViewHeight:"+height+", total:"+totalRange+", maxHeight:"+maxHeight);
            mFixHeight = Math.min(maxHeight, totalRange + getPaddingBottom());

            Log.d(TAG, "buildLocationRects mFixHeight:"+mFixHeight);
            layoutParams.height = mFixHeight;//mFixHeight + getPaddingBottom();
            recyclerView.setLayoutParams(layoutParams);
//            a1 = mFixHeight/1848f - 0.313f;
//            b2 = mFixHeight/528f - 1.345f;
//            a2 = a1 - 2*b2;
            a1 = 1.0f;
            b2 = 0;
            a2 = a1 - 2*b2;
            Log.d(TAG, "setRecyclerViewHeight a1:"+a1+", a2:"+a2+", b2:"+b2);
        }else {
            a1 = 1.0f/6;
            b2 = 1.0f/3;
            a2 = a1 - 2*b2;
        }
        Log.d(TAG, "buildLocationRects height:"+layoutParams.height);

    }

    @Override
    public void onMeasure(@NonNull RecyclerView.Recycler recycler, @NonNull RecyclerView.State state, int widthSpec, int heightSpec) {
        super.onMeasure(recycler, state, widthSpec, heightSpec);
    }

    private boolean isViewHasAboveBehavior(View view) {
        if (view.getLayoutParams() instanceof CoordinatorLayout.LayoutParams) {
            CoordinatorLayout.LayoutParams layoutParams = (CoordinatorLayout.LayoutParams) view.getLayoutParams();
            if (layoutParams.getBehavior() instanceof AboveDrawerBehavior) return true;
        }
        return false;
    }

    private CoordinatorLayout findCoordinatorLayout(View recyclerView) {
        View view = recyclerView;
        if (view == null) return null;
        if (view.getParent() instanceof CoordinatorLayout) {
            return (CoordinatorLayout) view.getParent();
        }else {
            return findCoordinatorLayout((View) view.getParent());
        }
    }

    /**
     * 计算可滑动的最大值
     */
    private void computeMaxScroll() {
        maxScroll = stateList.get(stateList.size() - 1).rect.bottom - getHeight();
        if (maxScroll < 0) {
            maxScroll = 0;
            return;
        }

        int itemCount = getItemCount();
        int screenFilledHeight = 0;
        for (int i = itemCount - 1; i >= 0; i--) {
            Rect rect = stateList.get(i).rect;
            screenFilledHeight = screenFilledHeight + (rect.bottom - rect.top);
            if (screenFilledHeight > getHeight()) {
                int extraSnapHeight = getHeight() - (screenFilledHeight - (rect.bottom - rect.top));
                maxScroll = maxScroll + extraSnapHeight;
                break;
            }
        }
    }

    /**
     * 初始化的时候，layout子View
     */
    private void layoutItems(boolean isSetHeight) {
        removeAndRecycleUnusedViews(recycler);

        int itemCount = getItemCount();
        visibleView.clear();
        for (int i=0; i < itemCount; i++) {
            StateHolder holder = stateList.get(i);
            Rect rect = holder.rect;
            StateHolder beforeHolder = i > 0 ? stateList.get(i-1) : null;
            int lastLength = beforeHolder != null ? beforeHolder.scrollBottom : 0;
            if ((isSetHeight && i < 3) || isViewCanVisible(i)) {
                View childView = findViewForPosition(i, recycler);
                visibleView.put(i, childView);
                computeViewCreate(i, holder, beforeHolder);
            }else if (lastLength < scroll) {
                holder.scrollBottom = lastLength+rect.height();
            }else {
                holder.scrollBottom = lastLength + bottomLastOffset;
            }
        }
        itemCount = visibleView.size();
        for (int i = itemCount -1; i >= 0; i--) {
            int position = visibleView.keyAt(i);
            View childView = visibleView.get(position);
            Rect rect = stateList.get(position).rect;
            StateHolder holder = stateList.get(position);
            bindChild(childView);
            layoutDecorated(childView, rect.left, holder.scrollBottom - rect.height()-holder.scrollOffset,
                    rect.right, holder.scrollBottom-holder.scrollOffset);
        }
        if (!mUseVisibleHeight) {
            mVisibleHeight = getHeight();
        }
    }

    private boolean isViewCanVisible(int position) {
        int visibleLength = mVisibleHeight+scroll+bottomLastOffset;
        StateHolder beforeHolder = position > 0 ? stateList.get(position-1) : null;
        StateHolder holder = stateList.get(position);
        int height = holder.rect.height();
        int lastLength = beforeHolder != null ? beforeHolder.scrollBottom : 0;
        return (lastLength + height > scroll && lastLength + height < visibleLength)
                || (lastLength > scroll && lastLength < visibleLength);
    }

    private void removeAndRecycleUnusedViews(final RecyclerView.Recycler recycler) {
        for (int i = 0, size = getChildCount(); i < size; ++i) {
            final View child = getChildAt(i);
            if (child == null) {
                continue;
            }
            final ViewGroup.LayoutParams lp = child.getLayoutParams();
            if (!(lp instanceof RecyclerView.LayoutParams)) {
                removeAndRecycleView(child, recycler);
                continue;
            }
            final RecyclerView.LayoutParams recyclerViewLp = (RecyclerView.LayoutParams) lp;
            final int adapterPosition = recyclerViewLp.getViewAdapterPosition();
            if (recyclerViewLp.isItemRemoved() || !isViewCanVisible(adapterPosition)) {
                removeAndRecycleView(child, recycler);
            }
        }
    }

    private View bindChild(final View view) {
        if (null == view.getParent()) {
            addView(view);
            measureChildWithMargins(view, 0, 0);
        } else {
            detachView(view);
            attachView(view);
        }
        return view;
    }

    private View findViewForPosition(final int position, final RecyclerView.Recycler recycler) {
        for (int i = 0, size = getChildCount(); i < size; ++i) {
            final View child = getChildAt(i);
            final ViewGroup.LayoutParams lp = child.getLayoutParams();
            if (!(lp instanceof RecyclerView.LayoutParams)) {
                continue;
            }
            final RecyclerView.LayoutParams recyclerLp = (RecyclerView.LayoutParams) lp;
            final int adapterPosition = recyclerLp.getViewAdapterPosition();
            if (adapterPosition == position) {
                if (recyclerLp.isItemChanged()) {
                    recycler.bindViewToPosition(child, position);
                    measureChildWithMargins(child, 0, 0);
                }
                return child;
            }
        }
        return recycler.getViewForPosition(position);
    }

    private int mVisibleHeight;

    public int getVisibleHeight() {
        return mVisibleHeight;
    }

    public void setVisibleHeight(int visibleHeight) {
        Log.d(TAG, "setVisibleHeight:"+getHeight()+", visibleHeight:"+visibleHeight);
        if (!mUseVisibleHeight) return;
        int height = visibleHeight;//Math.min(Math.max(visibleHeight, 0), getHeight());
        if (height > getHeight() || height == mVisibleHeight) return;
        this.mVisibleHeight = height;
        layoutItems(true);
//        requestLayout();
    }

    private void computeViewCreate(int position, StateHolder holder,StateHolder beforeHolder) {
        int itemHeight = holder.rect.height();
        int lastLength = beforeHolder != null ? beforeHolder.scrollBottom : 0;
        int visibleLength = scroll + mVisibleHeight;
        if (mVisibleHeight < mMinScrollHeight) {
            if (position == 0) {
                holder.scrollBottom = itemHeight;
            }else if (position == 1){
                holder.scrollBottom = lastLength + bottomOffset/2;
            }else if (position == 2){
                holder.scrollBottom = lastLength + bottomLastOffset;
            }else {
                holder.scrollBottom = lastLength + bottomLastOffset;
            }
        }else {
            int meddleOffset = bottomOffset*2/3;
            int meddleOffset1 = bottomOffset - meddleOffset;
            if (lastLength == 0) {
                holder.scrollBottom = itemHeight;
                holder.scrollOffset = scroll;
            }else if ((visibleLength - lastLength > bottomOffset + itemHeight)){
                lastLength +=itemHeight;
                holder.scrollBottom = lastLength;
                holder.scrollOffset = scroll;
            }else if (visibleLength - lastLength > bottomOffset) {
                //计算上个view移动与当前view的比例
                float scale = 1.0f*(visibleLength - lastLength - bottomOffset) / itemHeight;
                holder.scrollBottom = visibleLength - meddleOffset1 - (int) (meddleOffset*scale);
                holder.scrollOffset = scroll;
            }else if (visibleLength - lastLength > bottomOffset/2){
                //计算上个view移动与当前view的比例
                float scale = 1.0f*(visibleLength - lastLength - bottomOffset/2) / (bottomOffset/2);
                holder.scrollBottom = visibleLength+bottomLastOffset
                        - (int) ((bottomLastOffset+meddleOffset1)*scale);
                holder.scrollOffset = scroll;
            }else {
                //计算上个view移动与当前view的比例
                holder.scrollBottom = visibleLength+bottomLastOffset;
                holder.scrollOffset = scroll;
            }
        }
        //阻止最后一项往上滑过头
        if (position == getItemCount()-1) {
            holder.scrollBottom = Math.max(visibleLength, holder.scrollBottom);
        }
        Log.d(TAG, "computeViewScroll position:"+position+", bottom:"+holder.scrollBottom);
    }

    @Override
    public boolean canScrollVertically() {
        return true;
    }

    /**
     * 重写 被canScrollVertically调用 ，
     * @param state
     * @return 滑动过得长度
     */
    @Override
    public int computeVerticalScrollOffset(RecyclerView.State state) {
        Log.d(TAG, "computeVerticalScrollOffset:"+scroll);
        return scroll;
    }

    /**
     * 重写 被canScrollVertically调用 ,
     * @param state
     * @return 显示内容的高度
     */
    @Override
    public int computeVerticalScrollExtent(RecyclerView.State state) {
        Log.d(TAG, "computeVerticalScrollExtent:"+getHeight());
        return getHeight();
    }
    /**
     * 重写 被canScrollVertically调用 ，
     * @param state
     * @return 总长度
     */
    @Override
    public int computeVerticalScrollRange(RecyclerView.State state) {
        Log.d(TAG, "computeVerticalScrollRange:"+totalRange);
        return totalRange;
    }

    @Override
    public int scrollVerticallyBy(int dy, RecyclerView.Recycler recycler, RecyclerView.State state) {
        Log.d(TAG, "scrollVerticallyBy dy:"+dy);
        if (getItemCount() == 0 || dy == 0) {
            return 0;
        }
        int travel = dy;
        if (dy + scroll < 0) {
            travel = -scroll;
        } else if (dy + scroll > maxScroll) {
            travel = maxScroll - scroll;
        }
        scroll += travel; //累计偏移量

        int itemCount = getItemCount();
        boolean isFind = false;
        Rect lastRect = stateList.get(itemCount - 1).rect;
        if (lastRect.bottom == scroll + getHeight()){
            isFind = true;
        }else if (lastRect.bottom > scroll + getHeight()) {
            for (int i = 0; i < itemCount; i++) { // TODO: 2018/3/2 0002  数据量大需要优化
                Rect itemRect = stateList.get(i).rect;
                Log.d(TAG, "scrollVerticallyBy1 position:"+i+","+(itemRect.top == scroll));
                if (itemRect.top == scroll) {
                    Log.d(TAG, "scrollVerticallyBy2 position:"+i);
                    isFind = true;
                    break;
                }
                if (itemRect.top > scroll) {
                    break;
                }
            }
        }
        if (isFind) {
            needSnap = false;
        }else {
            needSnap = true;
        }
        lastDy = dy;
        if (!state.isPreLayout() && getChildCount() > 0) {
            layoutItems(false);
        }

        return travel;
    }

    @Override
    public void onAttachedToWindow(RecyclerView view) {
        super.onAttachedToWindow(view);
        new StartSnapHelper().attachToRecyclerView(view);
        view.setOverScrollMode(View.OVER_SCROLL_NEVER);
        mRecyclerView = view;
        mNeedFixHeight = true;
        h_elevation = 2;//mRecyclerView.getResources().getDimension(R.dimen.activity_item_elevation);
        m_elevation = h_elevation/2;
        l_elevation = 0;

        bottomOffset = UIUtils.dip2px(view.getContext(), 50);
        bottomLastOffset = UIUtils.dip2px(view.getContext(), 10);
    }

    @Override
    public void onDetachedFromWindow(RecyclerView view, RecyclerView.Recycler recycler) {
        super.onDetachedFromWindow(view, recycler);
        mRecyclerView = null;
        adapter = null;
        viewTypeHeightMap = null;
    }

    @Override
    public void onScrollStateChanged(int state) {
        Log.d(TAG, "onScrollStateChanged state:"+state);
//        if (state == RecyclerView.SCROLL_STATE_DRAGGING) {
//            needSnap = true;
//        }
        super.onScrollStateChanged(state);
    }

    public int getSnapHeight() {
        Log.d(TAG, "getSnapHeight:"+needSnap);
        if (!needSnap) {
            return 0;
        }
        needSnap = true;

        int itemCount = getItemCount();
        for (int i = 0; i < itemCount; i++) {
            Rect itemRect = stateList.get(i).rect;
            if (isViewCanVisible(i)) {

                Log.d(TAG, "getSnapHeight position:"+i+", lastDy:"+lastDy);
                Rect lastRect = stateList.get(itemCount-1).rect;
                if (scroll+getHeight()-getPaddingBottom() >= lastRect.bottom) {
                    int offset = lastRect.bottom - (scroll+getHeight()-getPaddingBottom());
                    Log.d(TAG, "getSnapHeight offset:"+offset+",bottom:"+getPaddingBottom());
                    return offset;
                }
                if (lastDy > 0) {
                    // scroll变大，属于列表往下走，往下找下一个为snapView

                    if (i <= itemCount - 2) {
                        Rect nextRect = stateList.get(i + 1).rect;
                        int offset = nextRect.top - scroll;
                        Log.d(TAG, "getSnapHeight offset1:"+offset);
                        if (scroll+getHeight()+offset-getPaddingBottom() >= lastRect.bottom) {
                            offset = lastRect.bottom - (scroll+getHeight()-getPaddingBottom());
                            Log.d(TAG, "getSnapHeight offset2:"+offset);
                            return offset;
                        }
                        return offset;
                    }

                }
                return itemRect.top - scroll;
            }
        }
        return 0;
    }

    public View findSnapView() {
        if (getChildCount() > 0) {
            return getChildAt(0);
        }
        return null;
    }

    static class StateHolder {

        int scrollBottom;
        int scrollOffset;

        Rect rect;

    }

}
