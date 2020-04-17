package com.example.stacklayout.vagelayout;

import android.graphics.Rect;
import android.util.Log;
import android.util.SparseArray;
import android.util.SparseBooleanArray;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewTreeObserver;

import androidx.collection.ArrayMap;
import androidx.coordinatorlayout.widget.CoordinatorLayout;
import androidx.recyclerview.widget.RecyclerView;

import com.example.stacklayout.UIUtils;
import com.example.stacklayout.drawer.AboveDrawerBehavior;


/**
 * Created by xmuSistone on 2017/9/20.
 */
public class VegaLayoutManager extends RecyclerView.LayoutManager {

    private static final String TAG = VegaLayoutManager.class.getSimpleName();

    public static int OFFSET_ITEM = 0;

    private int scroll = 0;

    /**底部用了展示接下来view的高度*/
    private int bottomOffset = 0;
    private int bottomLastOffset = 0;

    private SparseArray<Rect> locationRects = new SparseArray<>();
    private SparseArray<StateHolder> stateList = new SparseArray<>();
    private SparseBooleanArray attachedItems = new SparseBooleanArray();
    private ArrayMap<Integer, Integer> viewTypeHeightMap = new ArrayMap<>();

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

    public VegaLayoutManager() {
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

        // 先回收放到缓存，后面会再次统一layout
        detachAndScrapAttachedViews(recycler);
        layoutItemsOnCreate(recycler);
    }

    boolean needRebuildRect = true;

    private void buildLocationRects() {
//        if (!needRebuildRect) return;

        Log.d(TAG, "buildLocationRects");
        needRebuildRect = false;
        locationRects.clear();
        stateList.clear();
        attachedItems.clear();

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
                OFFSET_ITEM = Math.max(itemHeight, OFFSET_ITEM);
                Log.d(TAG, "OFFSET_ITEM:"+OFFSET_ITEM);
                viewTypeHeightMap.put(viewType, itemHeight);
            }

            // 2. 组装Rect并保存
            Rect rect = new Rect();
            rect.left = getPaddingLeft();
            rect.top = tempPosition;
            rect.right = getWidth() - getPaddingRight();
            rect.bottom = rect.top + itemHeight;
//            locationRects.put(itemCount - 1 - i, rect);
//            attachedItems.put(itemCount - 1 - i, false);
            locationRects.put(i, rect);
            attachedItems.put(i, false);

            StateHolder holder = new StateHolder();
            if (i == 0) {
                mMinScrollHeight = rect.height() + bottomOffset + bottomLastOffset;
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
     * 对外提供接口，找到第一个可视view的index
     */
    public int findFirstVisibleItemPosition() {
        int count = locationRects.size();
        Rect displayRect = getDisplayRect();
        for (int i = 0; i < count; i++) {
            if (Rect.intersects(displayRect, locationRects.get(i)) &&
                    attachedItems.get(i)) {
                return i;
            }
        }
        return 0;
    }

    Rect mDisplayRect = new Rect();

    private Rect getDisplayRect() {
        mDisplayRect.set(0, scroll , getWidth(), getHeight() + scroll + OFFSET_ITEM);
        return mDisplayRect;
    }

    /**
     * 计算可滑动的最大值
     */
    private void computeMaxScroll() {
        maxScroll = locationRects.get(locationRects.size() - 1).bottom - getHeight();
        if (maxScroll < 0) {
            maxScroll = 0;
            return;
        }

        int itemCount = getItemCount();
        int screenFilledHeight = 0;
        for (int i = itemCount - 1; i >= 0; i--) {
            Rect rect = locationRects.get(i);
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
    private void layoutItemsOnCreate(RecyclerView.Recycler recycler) {
        int itemCount = getItemCount();
        Rect displayRect = getDisplayRect();
//        for (int i = 0; i < itemCount; i++) {
        for (int i = itemCount -1; i >= 0; i--) {
            Rect thisRect = locationRects.get(i);
            if (Rect.intersects(displayRect, thisRect)) {
                Log.d(TAG, "layoutItemsOnCreate position:"+i);
                View childView = recycler.getViewForPosition(i);
                addView(childView);
                measureChildWithMargins(childView, View.MeasureSpec.UNSPECIFIED, View.MeasureSpec.UNSPECIFIED);
                StateHolder beforeHolder = i > 0 ? stateList.get(i-1) : null;
                int nextHeight = i < itemCount - 2 ? locationRects.get(i+1).height() : 0;
                layoutItemOnScrollVisible(childView, stateList.get(i), beforeHolder, nextHeight, locationRects.get(i), i);
                attachedItems.put(i, true);
            }
        }
        if (!mUseVisibleHeight) {
            mVisibleHeight = getHeight();
        }
    }

    /**
     * 初始化的时候，layout子View
     */
    private void layoutItemsOnScroll() {
        int childCount = getChildCount();
        // 1. 已经在屏幕上显示的child
        int itemCount = getItemCount();
        Rect displayRect = getDisplayRect();
        int firstVisiblePosition = -1;
        int lastVisiblePosition = -1;
        for (int i = childCount - 1; i >= 0; i--) {
//        for (int i = 0; i < childCount; i++) {
            View child = getChildAt(i);
            if (child == null) {
                continue;
            }
            int position = getPosition(child);
            if (!Rect.intersects(displayRect, locationRects.get(position))) {
                // 回收滑出屏幕的View
                removeAndRecycleView(child, recycler);
                Log.d(TAG, "removeAndRecycleView:"+position+", scroll:"+scroll);
                Log.d(TAG, "displayRect:"+displayRect.toString()+", Rects:"+locationRects.get(position).toString());
                attachedItems.put(position, false);
            } else {
                // Item还在显示区域内，更新滑动后Item的位置
                if (lastVisiblePosition < 0) {
                    lastVisiblePosition = position;
                }/*else {
                    lastVisiblePosition = Math.max(lastVisiblePosition, position);
                }*/

                if (firstVisiblePosition < 0) {
                    firstVisiblePosition = position;
                } else {
                    firstVisiblePosition = Math.min(firstVisiblePosition, position);
                }

                Log.d(TAG, "layoutItem position:"+position);
                layoutItem(child, locationRects.get(position)); //更新Item位置
            }
        }

        // 2. 复用View处理
        if (firstVisiblePosition > 0) {
            // 往前搜索复用
            for (int i = firstVisiblePosition - 1; i >= 0; i--) {
//            for (int i = 0; i <firstVisiblePosition ; i++) {
                if (Rect.intersects(displayRect, locationRects.get(i)) &&
                        !attachedItems.get(i)) {
                    reuseItemOnSroll(i, true);
                } /*else {
                    break;
                }*/
            }
        }
        // 往后搜索复用
//        for (int i = lastVisiblePosition + 1; i < itemCount; i++) {
        for (int i = itemCount - 1; i >= lastVisiblePosition + 1; i--) {
//            Log.d(TAG, "position:"+i+", in rect:"+Rect.intersects(displayRect, locationRects.get(i))
//                    +", attached:"+attachedItems.get(i));
            if (Rect.intersects(displayRect, locationRects.get(i)) &&
                    !attachedItems.get(i)) {
                reuseItemOnSroll(i, false);
            } /*else {
                break;
            }*/
        }
    }

    /**
     * 复用position对应的View
     */
    private void reuseItemOnSroll(int position, boolean addViewFromTop) {
        Log.d(TAG, "reuseItemOnSroll:"+position);
        View scrap = recycler.getViewForPosition(position);
        measureChildWithMargins(scrap, 0, 0);

        if (addViewFromTop) {
            addView(scrap);
        } else {
            addView(scrap, 0);
        }
        // 将这个Item布局出来
        layoutItem(scrap, locationRects.get(position));
        attachedItems.put(position, true);
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
        requestLayout();
    }

    private void layoutItemOnScrollVisible(View child, StateHolder holder, StateHolder beforeHolder,
                                           int nextHeight, Rect rect, int position) {
        Log.d(TAG, "layoutItemOnScrollVisible: scroll:"+scroll+", mVisibleHeight:"+mVisibleHeight);
        int topDistance = scroll + mVisibleHeight - rect.top;
        int layoutTop, layoutBottom;
        int itemHeight = rect.height();
        int offset = 0;
        if (mVisibleHeight < mMinScrollHeight) {
            if (beforeHolder == null) {
                holder.state = StateHolder.VISIBLE_TOP;
                holder.scrollBottom = scroll + mVisibleHeight - itemHeight;
            }else if (beforeHolder.state == StateHolder.VISIBLE_BOTTOM){
                holder.state = StateHolder.VISIBLE_BOTTOM;
                holder.scrollBottom = beforeHolder.scrollBottom + bottomLastOffset;
            }else {
                switch (beforeHolder.state) {
                    case 1:
                        holder.scrollBottom = beforeHolder.scrollBottom + bottomOffset/2;
                        break;
                    case 2://StateHolder.VISIBLE_MEDDLE:
                        holder.scrollBottom = beforeHolder.scrollBottom + bottomOffset/2+bottomLastOffset;
                        break;
                }

            }
        }else {
            if (beforeHolder == null) {
                holder.state = StateHolder.VISIBLE_TOP;
                holder.scrollBottom = itemHeight;
            }else if(scroll + mVisibleHeight - (holder.scrollBottom) > nextHeight + bottomOffset + bottomLastOffset){
                holder.state = StateHolder.VISIBLE_STOP;
                holder.scrollBottom = beforeHolder.scrollBottom + itemHeight;
            }else if (beforeHolder.state == StateHolder.VISIBLE_BOTTOM){
                holder.state = StateHolder.VISIBLE_BOTTOM;
                holder.scrollBottom = beforeHolder.scrollBottom + bottomLastOffset;
            }else {
                switch (beforeHolder.state) {
                    case 1:
                        holder.scrollBottom = beforeHolder.scrollBottom + bottomOffset/2;
                        break;
                    case 2://StateHolder.VISIBLE_MEDDLE:
                        holder.scrollBottom = beforeHolder.scrollBottom + bottomOffset/2+bottomLastOffset;
                        break;
                }
                holder.state = beforeHolder.state - 1;
            }
        }
        if (rect.top == getPaddingTop()) {

        } else if (topDistance < itemHeight && topDistance > 0) { //底部第一项
//            Log.d(TAG, "layoutItem rect.bottom:"+rect.bottom+", big:"+(rect.bottom > getHeight()));
            float rate1 = 1.0f*topDistance / itemHeight;
            float changePosition = 0.5f;//(0.5-m/0.8-m)

            if (rate1 > changePosition) {
//                offset = (int) ((1 - rate1)*itemHeight*a1);
                offset = (int) ((1 - rate1)*itemHeight);
            }else {
//                offset = (int) ((rate1 * a2  + b2)*itemHeight);
                offset = (int) ((rate1)*itemHeight);
            }
//            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
//                child.setElevation(m_elevation);
//            }
        } else if (topDistance <= 0 && topDistance > -OFFSET_ITEM) { //底部第二项
            float rate1 = Math.abs(1.0f*topDistance / OFFSET_ITEM);
            offset = (int) ((rate1 * a2  + b2)*itemHeight);
//            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
//                child.setElevation(l_elevation);
//            }
        }
        offset = 0;
//        Log.d(TAG, "layoutItem topDistance:"+topDistance+", offset:"+offset);
//        Log.d(TAG, "layoutItem is top:" + (rect.top == getPaddingTop()) + ", offset:" + offset);
//        Log.d(TAG, "layoutItem position:"+position+", offset:"+(offset)+",topDistance:"+topDistance);
        layoutTop = rect.top - scroll - offset;
        layoutBottom = rect.bottom - scroll - offset;

        Log.d(TAG, "layoutItem position:"+position+", layoutTop:"+layoutTop+", offset:"+offset+",topDistance:"+topDistance);
        layoutDecorated(child, rect.left, layoutTop, rect.right, layoutBottom);
    }

    private void layoutItem(View child, Rect rect) {
        int topDistance = scroll + mVisibleHeight - rect.top;
        int layoutTop, layoutBottom;
        int itemHeight = rect.bottom - rect.top;
        int offset = 0;
//        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
//            child.setElevation(h_elevation);
//        }
        if (rect.top == getPaddingTop()) {

        } else if (topDistance < itemHeight && topDistance > 0) { //底部第一项
            float rate1 = 1.0f*topDistance / itemHeight;
            if (rate1 > 0.5) {
                offset = (int) ((1 - rate1)*itemHeight*a1);
            }else {
                offset = (int) ((rate1 * a2  + b2)*itemHeight);
            }
//            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
//                child.setElevation(m_elevation);
//            }
        } else if (topDistance <= 0 && topDistance > -OFFSET_ITEM) { //底部第二项
            float rate1 = Math.abs(1.0f*topDistance / OFFSET_ITEM);
            offset = (int) ((rate1 * a2  + b2)*itemHeight);
//            if (rect.bottom > getHeight()) {
//                offset = (itemHeight - topDistance) * 4 / 5;
//            }else {
//                offset = (itemHeight - topDistance) / 3;
//            }
//            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
//                child.setElevation(l_elevation);
//            }
        }
        Log.d(TAG, "layoutItem offset:"+offset);
        layoutTop = rect.top - scroll - offset;
        layoutBottom = rect.bottom - scroll - offset;
        layoutDecorated(child, rect.left, layoutTop, rect.right, layoutBottom);
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
        int countHeight = 0;
        boolean isFind = false;
        Rect lastRect = locationRects.get(itemCount - 1);
        if (lastRect.bottom == scroll + getHeight()){
            isFind = true;
        }else if (lastRect.bottom > scroll + getHeight()) {
            for (int i = 0; i < itemCount; i++) { // TODO: 2018/3/2 0002  数据量大需要优化
                Rect itemRect = locationRects.get(i);
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
            layoutItemsOnScroll();
        }

        getPaddingTop();
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
        locationRects = null;
        attachedItems = null;
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

        Rect displayRect = getDisplayRect();
        int itemCount = getItemCount();
        for (int i = 0; i < itemCount; i++) {
            Rect itemRect = locationRects.get(i);
            if (displayRect.intersect(itemRect)) {

                Log.d(TAG, "getSnapHeight position:"+i+", lastDy:"+lastDy);
                Rect lastRect = locationRects.get(itemCount-1);
                if (scroll+getHeight()-getPaddingBottom() >= lastRect.bottom) {
                    int offset = lastRect.bottom - (scroll+getHeight()-getPaddingBottom());
                    Log.d(TAG, "getSnapHeight offset:"+offset+",bottom:"+getPaddingBottom());
                    return offset;
                }
                if (lastDy > 0) {
                    // scroll变大，属于列表往下走，往下找下一个为snapView

                    if (i <= itemCount - 2) {
                        Rect nextRect = locationRects.get(i + 1);
                        int offset = nextRect.top - displayRect.top;
                        Log.d(TAG, "getSnapHeight offset1:"+offset);
                        if (scroll+getHeight()+offset-getPaddingBottom() >= lastRect.bottom) {
                            offset = lastRect.bottom - (scroll+getHeight()-getPaddingBottom());
                            Log.d(TAG, "getSnapHeight offset2:"+offset);
                            return offset;
                        }
                        return offset;
                    }

                }
                return itemRect.top - displayRect.top;
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

        static int INVISIBLE = 0;
        static int VISIBLE_BOTTOM = 1;
        static int VISIBLE_MEDDLE = 2;
        static int VISIBLE_TOP = 3;
        static int VISIBLE_STOP = 4;

        int state = INVISIBLE;

        int scrollBottom;

    }

}
