package com.example.stacklayout.stacklayout;

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
public class UpStackLayoutManager extends RecyclerView.LayoutManager {

    private static final String TAG = UpStackLayoutManager.class.getSimpleName();


    private int scroll = 0;

    /**底部用了展示接下来view的高度*/
    private int bottomOffset = 0;
    private int bottomLastOffset = 0;

    private SparseArray<StateHolder> stateList;
    private ArrayMap<Integer, Integer> viewHeightMap;

    private SparseArray<View> visibleView;

    private boolean needSnap = false;
    private int lastDy = 0;
    private int maxScroll = -1;
    private RecyclerView.Adapter adapter;
    private RecyclerView.Recycler recycler;

    private int mFixHeight;
    private boolean mNeedFixHeight = true;

    public int totalRange = 0;

    /**需要减去的高度，一般为一些按钮的高度，*/
    private int diffOffset = 0;

    private boolean mUseVisibleHeight = false;
    private int mMinScrollHeight = 0;

    private RecyclerView mRecyclerView;

    public UpStackLayoutManager() {
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

    public int getDiffOffset() {
        return diffOffset;
    }

    public void setDiffOffset(int diffOffset) {
        if (this.diffOffset != diffOffset) {
            this.diffOffset = diffOffset;
            mNeedFixHeight = true;
        }
    }

    @Override
    public void onLayoutChildren(RecyclerView.Recycler recycler, RecyclerView.State state) {
        Log.d(TAG, "onLayoutChildren");
        this.recycler = recycler; // 二话不说，先把recycler保存了
        if (state.isPreLayout()) {
            return;
        }
        buildLocationRects();
        layoutItems();
    }

    boolean needRebuildRect = true;

    private void buildLocationRects() {

        Log.d(TAG, "buildLocationRects");
        needRebuildRect = false;
        stateList.clear();

        int tempPosition = getPaddingTop();
        int itemCount = getItemCount();
        for (int i = 0; i < itemCount; i++) {
//        for (int i = itemCount -1; i >= 0; i--) {
            // 1. 先计算出itemWidth和itemHeight
            int viewType = adapter.getItemViewType(i);
            int itemHeight;
            if (viewHeightMap.containsKey(viewType)) {
                itemHeight = viewHeightMap.get(viewType);
            } else {
                View itemView = recycler.getViewForPosition(i);
                addView(itemView);
                measureChildWithMargins(itemView, View.MeasureSpec.UNSPECIFIED, View.MeasureSpec.UNSPECIFIED);
                RecyclerView.LayoutParams lp = (RecyclerView.LayoutParams) itemView.getLayoutParams();
                itemHeight = getDecoratedMeasuredHeight(itemView)+lp.topMargin + lp.bottomMargin;
                Log.d(TAG, "top:"+lp.topMargin+", bottom:"+lp.bottomMargin);
                viewHeightMap.put(viewType, itemHeight);
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
            }
            stateList.put(i, holder);
            tempPosition = tempPosition + rect.height();
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
        }else {
            mVisibleHeight = getHeight();
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
            int offset = diffOffset;//减去按钮的高度
            if (coordinatorLayout != null) {
                Rect rect = new Rect();
                coordinatorLayout.getGlobalVisibleRect(rect);
                maxHeight = rect.height() - offset;

                if (maxHeight <= 0) {
                    maxHeight = mRecyclerView.getResources().getDisplayMetrics().heightPixels - offset;
                }
                mFixHeight = Math.min(maxHeight, totalRange + getPaddingBottom()+getPaddingTop());
                layoutParams.height = mFixHeight;//mFixHeight + getPaddingBottom();
                recyclerView.setLayoutParams(layoutParams);
            }
        }
        Log.d(TAG, "buildLocationRects height:"+layoutParams.height);

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

    }

    /**
     * 初始化的时候，layout子View
     */
    private void layoutItems() {
        removeAndRecycleUnusedViews(recycler);

        int itemCount = getItemCount();
        visibleView.clear();
        for (int i=0; i < itemCount; i++) {
            StateHolder holder = stateList.get(i);
            Rect rect = holder.rect;
            StateHolder beforeHolder = i > 0 ? stateList.get(i-1) : null;
            int lastLength = beforeHolder != null ? beforeHolder.scrollBottom : 0;
            //还没有完全展开，前3个必须初始化
            if ((mVisibleHeight <= getHeight() && scroll == 0 && i < 3) || isViewCanVisible(i)) {
                Log.d(TAG, "layoutItems i:"+i);
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
            layoutDecoratedWithMargins(childView, rect.left, holder.scrollBottom - rect.height()-holder.scrollOffset,
                    rect.right, holder.scrollBottom-holder.scrollOffset);
        }
    }

    private boolean isViewCanVisible(int position) {
        int visibleLength = mVisibleHeight+scroll+bottomLastOffset;
        StateHolder beforeHolder = position > 0 ? stateList.get(position-1) : null;
        StateHolder holder = stateList.get(position);
        int height = holder.rect.height();
        int lastLength = beforeHolder != null ? beforeHolder.scrollBottom : 0;
        int first = findFirstVisibleItemPosition();
        if (mVisibleHeight <= mMinScrollHeight && position >= first && position <= first+3) {
            return true;
        }
        return (lastLength + height > scroll && lastLength + height < visibleLength)
                || (lastLength > scroll && lastLength < visibleLength) || (lastLength < scroll && lastLength + height > scroll);
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
            final int adapterPosition = getPosition(child);
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
            final int adapterPosition = getPosition(child);
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
        int height = Math.min(Math.max(visibleHeight, 0), getHeight());
        if (height == mVisibleHeight) return;
        this.mVisibleHeight = height;
        layoutItems();
//        requestLayout();
    }

    public int findFirstVisibleItemPosition() {
        final View child = getChildAt(getChildCount()-1);
        return child == null ? RecyclerView.NO_POSITION : getPosition(child);
    }

    private void computeViewCreate(int position, StateHolder holder,StateHolder beforeHolder) {
        int itemHeight = holder.rect.height();
        int lastLength = beforeHolder != null ? beforeHolder.scrollBottom : 0;
        int visibleLength = scroll + mVisibleHeight;
        int meddleOffset = bottomOffset*2/3;
        int meddleOffset1 = bottomOffset - meddleOffset;
        int offsetPosition = findFirstVisibleItemPosition();//visibleView.keyAt(0);
        Log.d(TAG, "computeViewCreate offsetPosition:"+offsetPosition+", mVisibleHeight:"+mVisibleHeight);
        if (mVisibleHeight < mMinScrollHeight) {
            if (scroll == 0) {
                offsetPosition = 0;
            }
            if (position == offsetPosition) {
                holder.scrollBottom = lastLength+itemHeight;
                mMinScrollHeight = itemHeight + bottomOffset;
            }else if (position == 1+offsetPosition){
                holder.scrollBottom = lastLength + meddleOffset;
            }else if (position == 2+offsetPosition){
                holder.scrollBottom = lastLength + meddleOffset1 + bottomLastOffset;
            }else {
                holder.scrollBottom = lastLength + bottomLastOffset;
            }
        }else {
            if (lastLength == 0) {
                holder.scrollBottom = itemHeight;
            }else if ((visibleLength - lastLength > itemHeight + bottomOffset)){
                holder.scrollBottom = lastLength + itemHeight;
            }else if (visibleLength - lastLength > bottomOffset) {
                mMinScrollHeight = itemHeight + bottomOffset;
                //计算上个view移动与当前view的比例
                float scale = 1.0f*(visibleLength - lastLength - bottomOffset) / itemHeight;
                holder.scrollBottom = visibleLength - meddleOffset1 - (int) (meddleOffset*scale);
            }else if (visibleLength - lastLength > meddleOffset1){
                //计算上个view移动与当前view的比例
                float scale = 1.0f*(visibleLength - lastLength - meddleOffset1) / (meddleOffset);
                holder.scrollBottom = visibleLength+bottomLastOffset
                        - (int) ((bottomLastOffset+meddleOffset1)*scale);
            }else {
                //计算上个view移动与当前view的比例
                holder.scrollBottom = visibleLength+bottomLastOffset;
            }
        }

        //特殊处理,当上滑到完全展开，最上面一个view没有对齐的时候，需要下滑对齐，然后下面的view才开始叠加
//        if(totalRange == getHeight() + scroll && mVisibleHeight < getHeight() && scroll > 0){
//            if (offsetPosition == position) {
//                if (lastLength + itemHeight > scroll + (getHeight() - mVisibleHeight) && lastLength < scroll) {
//                    holder.scrollBottom  = lastLength + itemHeight - (getHeight() - mVisibleHeight);
//                }else {
//                    holder.scrollBottom  = scroll;
//                }
//            }else if(mVisibleHeight < minScrollHeight) {
//                if (position == 1+offsetPosition) {
//                    holder.scrollBottom = lastLength+itemHeight;
//                }else if (position == 2+offsetPosition){
//                    holder.scrollBottom = lastLength + meddleOffset;
//                }else if (position == 3+offsetPosition){
//                    holder.scrollBottom = lastLength + meddleOffset1 + bottomLastOffset;
//                }else {
//                    holder.scrollBottom = lastLength + bottomLastOffset;
//                }
//            }
//
//        }

        holder.scrollOffset = scroll;
        //阻止最后一项往上滑过头
        if (position == getItemCount()-1) {
            holder.scrollBottom = Math.max(visibleLength, holder.scrollBottom);
        }
        Log.d(TAG, "computeViewScroll position:"+position+", visibleLength:"+visibleLength
                +", bottom:"+holder.scrollBottom);
        Log.d(TAG, "computeViewScroll position:"+position+", height:"+totalRange
                +", h:"+(maxScroll+getHeight()));
    }

    @Override
    public boolean canScrollVertically() {
        // TODO: 2020/4/20 0020 避免CoordinatorLayout没上滑完，就可以向下滑动
//        return mVisibleHeight >= getHeight();
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
            layoutItems();
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
        stateList = new SparseArray<>();
        viewHeightMap = new ArrayMap<>();
        visibleView = new SparseArray<>();

        bottomOffset = UIUtils.dip2px(view.getContext(), 50);
        bottomLastOffset = UIUtils.dip2px(view.getContext(), 10);
    }

    @Override
    public void onDetachedFromWindow(RecyclerView view, RecyclerView.Recycler recycler) {
        super.onDetachedFromWindow(view, recycler);
        mRecyclerView = null;
        adapter = null;
        stateList = null;
        viewHeightMap = null;
        visibleView = null;
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
