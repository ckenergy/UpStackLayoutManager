/*
 * Copyright 2013 The Android Open Source Project
 * Copyright 2016 Jake Wharton
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.example.stacklayout.drawer;

import android.graphics.Rect;
import android.os.SystemClock;
import android.util.Log;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.accessibility.AccessibilityEvent;

import androidx.coordinatorlayout.widget.CoordinatorLayout;
import androidx.core.view.GravityCompat;
import androidx.core.view.ViewCompat;
import androidx.customview.widget.ViewDragHelper;

import static android.view.View.INVISIBLE;
import static android.view.View.VISIBLE;

final class BehaviorDelegate extends ViewDragHelper.Callback {

    private static final String TAG = BehaviorDelegate.class.getSimpleName();

    private static final int PEEK_DELAY = 160; // ms
    private static final int MIN_FLING_VELOCITY = 400; // dips per second
    private static final int FLAG_IS_OPENED = 0x1;
    private static final int FLAG_IS_OPENING = 0x2;
    private static final int FLAG_IS_CLOSING = 0x4;
    private static final int DEFAULT_SCRIM_COLOR = 0x99000000;

    private final CoordinatorLayout parent;
    private final View mChild;
    private final boolean isTop;
//    private final ContentScrimDrawer scrimDrawer;
    private final ViewDragHelper dragger;
    private final int minHeight;

    private float initialMotionX;
    private float initialMotionY;
    private boolean childrenCanceledTouch;
    private int openState;
    private boolean isPeeking;
    private float onScreen;
    private int drawerState;

    private AboveDrawerBehavior.OnOffsetChangedListener mOnOffsetChangedListener;

    public void setOnOffsetChangedListener(AboveDrawerBehavior.OnOffsetChangedListener onOffsetChangedListener) {
        mOnOffsetChangedListener = onOffsetChangedListener;
    }

    public float getScale() {
        if (scale < 0) {
            scale = 1.0f*minHeight/this.mChild.getHeight();
        }
        return scale;
    }

    private float scale = -1;

    private int scrimColor = DEFAULT_SCRIM_COLOR;

    private final Runnable peekRunnable = new Runnable() {
        @Override
        public void run() {
            peekDrawer();
        }
    };
    private final Runnable draggerSettle = new Runnable() {
        @Override
        public void run() {
            if (dragger.continueSettling(true)) {
                ViewCompat.postOnAnimation(parent, this);
            }
        }
    };

    BehaviorDelegate(CoordinatorLayout parent, View child, int gravity, int minHeight) {
        this.parent = parent;
        this.mChild = child;
        this.minHeight = minHeight;

        int absGravity =
                GravityCompat.getAbsoluteGravity(gravity, ViewCompat.getLayoutDirection(parent));
        this.isTop = absGravity == Gravity.TOP;

        float density = parent.getResources().getDisplayMetrics().density;
        float minVel = MIN_FLING_VELOCITY * density;

        dragger = ViewDragHelper.create(parent, this);
        dragger.setEdgeTrackingEnabled(isTop ? ViewDragHelper.EDGE_TOP : ViewDragHelper.EDGE_BOTTOM);
        dragger.setMinVelocity(minVel);

//        scrimDrawer = Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR2
//                ? new ContentScrimDrawer.JellyBeanMr2(parent)
//                : new ContentScrimDrawer.Base(parent, mChild);
    }

    BehaviorDelegate(CoordinatorLayout parent, View child, int gravity) {
        this(parent, child, gravity, 0);
    }

    private boolean isContentView(View child) {
        return child != this.mChild;
    }

    private boolean isDrawerView(View child) {
        return child == this.mChild;
    }

    private void removeCallbacks() {
        parent.removeCallbacks(peekRunnable);
    }

    private void peekDrawer() {
        int peekDistance = dragger.getEdgeSize();
        int childTop;
        if (isTop) {
            childTop = -mChild.getHeight() + peekDistance + minHeight;
        } else {
            childTop = parent.getHeight() - peekDistance - minHeight;
        }
        // Only peek if it would mean making the drawer more visible and the drawer isn't locked
        if ((isTop && mChild.getTop() + minHeight < childTop) //
                || (!isTop && mChild.getTop() - minHeight > childTop)) {
            dragger.smoothSlideViewTo(mChild, mChild.getLeft(), childTop);
            ViewCompat.postOnAnimation(parent, draggerSettle);
            isPeeking = true;

            cancelChildViewTouch();
        }
    }

    private void cancelChildViewTouch() {
        // Cancel mChild touches
        if (!childrenCanceledTouch) {
            final long now = SystemClock.uptimeMillis();
            final MotionEvent cancelEvent = MotionEvent.obtain(now, now,
                    MotionEvent.ACTION_CANCEL, 0.0f, 0.0f, 0);
            final int childCount = parent.getChildCount();
            for (int i = 0; i < childCount; i++) {
                parent.getChildAt(i).dispatchTouchEvent(cancelEvent);
            }
            cancelEvent.recycle();
            childrenCanceledTouch = true;
        }
    }

    private float lastY;

    boolean onInterceptTouchEvent(MotionEvent ev) {
        Log.d(TAG, "onInterceptTouchEvent:"+dragger.getViewDragState());

        boolean childCanScroll = false;
        boolean interceptForDrag = dragger.shouldInterceptTouchEvent(ev);
        boolean interceptForTap = false;

        switch (ev.getAction()) {
            case MotionEvent.ACTION_DOWN: {
                Log.d(TAG, "onInterceptTouchEvent ACTION_DOWN onScreen:"+onScreen);
                float x = ev.getX();
                float y = ev.getY();
                initialMotionX = x;
                initialMotionY = y;
                lastY = y;
                View child = dragger.findTopChildUnder((int) x, (int) y);
                if (onScreen > 0 && onScreen < 1) {
                    Log.d(TAG, "mChild:" + child.getClass().getName() + ", ");
                    if (child != null && isContentView(child)) {
                        interceptForTap = true;
                    }
                }
                if (onScreen == 1) {
                    if (child != null && !isContentView(child)) {
                        View touchView = findTouchView(child, ev.getRawX(), ev.getRawY(), -1);
                        if (touchView != null) {
                            childCanScroll = true;
                            Log.d(TAG, "touchView:"+touchView.getClass().getName());
                        }
                    }
                }
                childrenCanceledTouch = false;
                break;
            }

            case MotionEvent.ACTION_MOVE: {
                Log.d(TAG, "onInterceptTouchEvent ACTION_MOVE");
                // If we cross the touch slop, don't perform the delayed peek for an edge touch.
                if (dragger.checkTouchSlop(ViewDragHelper.DIRECTION_ALL)) {
                    removeCallbacks();
                }
                float x = ev.getX();
                float y = ev.getY();
                float dy = lastY - y;
                lastY = y;
                View child = dragger.findTopChildUnder((int) x, (int) y);
                Log.d(TAG, "onInterceptTouchEvent view:"+child.getClass().getSimpleName()+", dy:"+dy);
                Log.d(TAG, "onInterceptTouchEvent isDrawerView:"+isDrawerView(child));
                if (onScreen == 0) {
                    if (child != null && isDrawerView(child) && dy > 0) {
                        interceptForTap = true;
                    }
                }
                if (onScreen == 1) {
                    if (child != null && isDrawerView(child)) {
                        View touchView = findTouchView(child, ev.getRawX(), ev.getRawY(), (int) dy);
                        if (touchView != null) {
                            childCanScroll = true;
                            Log.d(TAG, "touchView:"+touchView.getClass().getName());
                        }
                    }
                }
                break;
            }

            case MotionEvent.ACTION_CANCEL:
            case MotionEvent.ACTION_UP: {
                closeDrawers(true);
                childrenCanceledTouch = false;
                break;
            }
        }
        Log.d(TAG, "onInterceptTouchEvent childCanScroll:" + childCanScroll + ",interceptForDrag:" + interceptForDrag
                + ",interceptForTap:" + interceptForTap + ",isPeeking:" + isPeeking
                + ",childrenCanceledTouch:" + childrenCanceledTouch);

        return !childCanScroll && (interceptForDrag || interceptForTap || isPeeking || childrenCanceledTouch);
    }

    private View findTouchView(View view, float x, float y, int dy) {
        Log.d(TAG, "findTouchView:"+view.getClass().getSimpleName()+",isInViewArea:"+isInViewArea(view, x, y));
        if (!isInViewArea(view, x, y)) {
            return null;
        }
        Log.d(TAG, "findTouchView:"+view.getClass().getSimpleName()
                +", canScrollVertically:"+ view.canScrollVertically(dy));
        if (view.canScrollVertically(dy)) {
            return view;
        }
        if (view instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) view;
            int count = group.getChildCount();
            Log.d(TAG, "group:"+group.getClass().getSimpleName()+", count:"+count);
            if (count > 0) {
                for (int i = count - 1; i >= 0; i--) {
                    View view1 = group.getChildAt(i);
                    View view2 = findTouchView(view1, x, y, dy);
                    if (view2 != null) {
                        return view2;
                    }
                }
            }
        }
        return null;
    }

    private boolean isInViewArea(View view, float x, float y) {
        if (view.getVisibility() != VISIBLE) return false;
        Rect r = new Rect();
        view.getGlobalVisibleRect(r);
//        r.set(view.getLeft(), view.getTop(), view.getRight(), view.getBottom());
        Log.d(TAG, "isInViewArea "+"view:"+view.getClass().getSimpleName()+", rect:"+r.toString()+", x:"+x+", y:"+y);
        if (x > r.left && x < r.right && y > r.top && y < r.bottom) {
            return true;
        }
        return false;
    }

    boolean onTouchEvent(MotionEvent ev) {
        Log.d(TAG, "onTouchEvent");

        dragger.processTouchEvent(ev);

        switch (ev.getAction()) {
            case MotionEvent.ACTION_DOWN: {
                initialMotionX = ev.getX();
                initialMotionY = ev.getY();
                childrenCanceledTouch = false;
                break;
            }

            case MotionEvent.ACTION_UP: {
                float x = ev.getX();
                float y = ev.getY();
                boolean peekingOnly = true;
                View touchedView = dragger.findTopChildUnder((int) x, (int) y);
                if (touchedView != null && isContentView(mChild)) {
                    final float dx = x - initialMotionX;
                    final float dy = y - initialMotionY;
                    final int slop = dragger.getTouchSlop();
                    if (dx * dx + dy * dy < slop * slop) {
                        // Taps close a dimmed open drawer but only if it isn't locked open.
                        if ((openState & FLAG_IS_OPENED) == FLAG_IS_OPENED) { // TODO isDrawerOpen method?
                            peekingOnly = false;
                        }
                    }
                }
                closeDrawers(peekingOnly);
                break;
            }

            case MotionEvent.ACTION_CANCEL: {
                closeDrawers(true);
                childrenCanceledTouch = false;
                break;
            }
        }

        return true;
    }

    public void closeDrawers(boolean peekingOnly) {
        if (peekingOnly && !isPeeking) {
            Log.d(TAG, "do nothing");
            return;
        }

        boolean needsSettle;
        if (isTop) {
            needsSettle = dragger.smoothSlideViewTo(mChild, mChild.getLeft(), -mChild.getHeight() + minHeight);
        } else {
            needsSettle = dragger.smoothSlideViewTo(mChild, mChild.getLeft(), parent.getHeight() - minHeight);
        }
        isPeeking = false;

        removeCallbacks();

        if (needsSettle) {
            ViewCompat.postOnAnimation(parent, draggerSettle);
        }
    }

    @Override
    public void onViewCaptured(View capturedChild, int activePointerId) {
        isPeeking = false;
    }

    @Override
    public void onViewReleased(View releasedChild, float xvel, float yvel) {
        // Offset is how open the drawer is, therefore left/right values
        // are reversed from one another.
        float offset = onScreen;
        int childHeight = releasedChild.getHeight();

        int top;
        if (isTop) {
            top = yvel > 0 || yvel == 0 && offset > 0.5 ? 0 : -childHeight + minHeight ;
        } else {
            int height = parent.getHeight();
            Log.d(TAG, "yvel:"+yvel+", offset:"+offset);
            if (yvel < 0 || yvel == 0 && offset > 0.5) {
                top = height - childHeight;
            }else {
                top =  height - minHeight;
            }
        }

        dragger.settleCapturedViewAt(releasedChild.getLeft(), top);
        ViewCompat.postOnAnimation(parent, draggerSettle);
    }

    @Override
    public void onViewDragStateChanged(int state) {
        updateDrawerState(state, dragger.getCapturedView());
    }

    private void updateDrawerState(int activeState, View activeDrawer) {
        final int state = dragger.getViewDragState();

        if (activeDrawer != null && activeState == ViewDragHelper.STATE_IDLE) {
            if (onScreen == 0) {
                dispatchOnDrawerClosed(activeDrawer);
            } else if (onScreen == 1) {
                dispatchOnDrawerOpened(activeDrawer);
            }
        }

        if (state != drawerState) {
            drawerState = state;
        }
    }

    private void dispatchOnDrawerClosed(View drawerView) {
        if ((openState & FLAG_IS_OPENED) == FLAG_IS_OPENED) {
            openState = 0;

            updateChildrenImportantForAccessibility(drawerView, false);

            // Only send WINDOW_STATE_CHANGE if the host has window focus. This
            // may change if support for multiple foreground windows (e.g. IME)
            // improves.
            if (parent.hasWindowFocus()) {
                final View rootView = parent.getRootView();
                if (rootView != null) {
                    rootView.sendAccessibilityEvent(AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED);
                }
            }
        }
    }

    private void dispatchOnDrawerOpened(View drawerView) {
        if ((openState & FLAG_IS_OPENED) == 0) {
            openState = FLAG_IS_OPENED;

            updateChildrenImportantForAccessibility(drawerView, true);

            // Only send WINDOW_STATE_CHANGE if the host has window focus.
            if (parent.hasWindowFocus()) {
                parent.sendAccessibilityEvent(AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED);
            }

            drawerView.requestFocus();
        }
    }

    private void updateChildrenImportantForAccessibility(View drawerView, boolean isDrawerOpen) {
        final int childCount = parent.getChildCount();
        for (int i = 0; i < childCount; i++) {
            final View child = parent.getChildAt(i);
            if (!isDrawerOpen && child != this.mChild
                    || isDrawerOpen && child == drawerView) {
                // Drawer is closed and this is a content view or this is an
                // open drawer view, so it should be visible.
                ViewCompat.setImportantForAccessibility(child,
                        ViewCompat.IMPORTANT_FOR_ACCESSIBILITY_YES);
            } else {
                ViewCompat.setImportantForAccessibility(child,
                        ViewCompat.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS);
            }
        }
    }

    private int mInitTop = -1;

    @Override
    public void onViewPositionChanged(View changedView, int left, int top, int dx, int dy) {
        int childHeight = changedView.getHeight();

        // This reverses the positioning shown in onLayout.
        float offset;
        if (isTop) {
            int edge = childHeight + top;
            offset = (float) (edge - minHeight) / (childHeight - minHeight);
//            scrimDrawer.setBounds(0, edge, parent.getWidth(), parent.getHeight());
        } else {
            int edge = parent.getHeight() - top - minHeight;
            offset = (float) edge / (childHeight - minHeight);
//            scrimDrawer.setBounds(0, 0, parent.getWidth(), top);
        }

        Log.d(TAG, "mOffset:"+offset+",scale:"+getScale());

        if (mOnOffsetChangedListener != null) {
            int scrollOffset = top - mInitTop;
            int countOffset = Math.abs(mChild.getHeight() - minHeight);
            float scale = Math.abs(1.0f * scrollOffset / countOffset);
            mOnOffsetChangedListener.onOffsetChange(countOffset, scrollOffset, scale);
        }
//        int baseAlpha = (scrimColor & 0xff000000) >>> 24;
//        int imag = (int) (baseAlpha * offset);
//        int color = imag << 24 | (scrimColor & 0xffffff);
//        scrimDrawer.setColor(color);

        setDrawerViewOffset(offset);
        boolean visible = minHeight > 0 || offset == 0;
        changedView.setVisibility(visible ? VISIBLE : INVISIBLE);
        //不显示变色
//        scrimDrawer.setVisible(false);
        parent.invalidate();
    }

    private void setDrawerViewOffset(float slideOffset) {
        if (slideOffset == onScreen) {
            return;
        }
        onScreen = slideOffset;
    }

    @Override
    public void onEdgeTouched(int edgeFlags, int pointerId) {
        parent.postDelayed(peekRunnable, PEEK_DELAY);
    }

    @Override
    public boolean tryCaptureView(View child, int pointerId) {
        Log.d(TAG, ""+isDrawerView(child));
        return isDrawerView(child);
    }

    @Override
    public void onEdgeDragStarted(int edgeFlags, int pointerId) {
        if (((edgeFlags & ViewDragHelper.EDGE_TOP) == ViewDragHelper.EDGE_TOP && isTop)
                || ((edgeFlags & ViewDragHelper.EDGE_BOTTOM) == ViewDragHelper.EDGE_BOTTOM && !isTop)) {
            dragger.captureChildView(mChild, pointerId);
        }
    }

//    @Override
//    public int getViewHorizontalDragRange(View mChild) {
//        return isDrawerView(mChild) ? mChild.getHeight() : 0;
//    }

    @Override
    public int getViewVerticalDragRange(View child) {
        return isDrawerView(child) ? child.getHeight() : 0;
    }

    @Override
    public int clampViewPositionHorizontal(View child, int left, int dx) {
        return child.getLeft();
    }

    @Override
    public int clampViewPositionVertical(View child, int top, int dy) {
        if (isTop) {
            return Math.max(-child.getHeight(), Math.min(top, 0));
        } else {
            int height = parent.getHeight();
            return Math.max(height - child.getHeight(), Math.min(top, height));
        }
    }

    boolean onLayoutChild() {

        Log.d(TAG, "onLayoutChild onScreen:"+onScreen);

        int width = parent.getMeasuredWidth();
        int height = parent.getMeasuredHeight();
        int childWidth = mChild.getMeasuredWidth();
        int childHeight = mChild.getMeasuredHeight();

        int childTop;
        float newOffset;
        if (isTop) {
            childTop = -childHeight + (int)(minHeight + ((childHeight - minHeight)*onScreen));
            newOffset = (float) (childHeight + childTop - minHeight) / (childHeight - minHeight);
        } else {
            childTop = height - (int)(minHeight + ((childHeight - minHeight)*onScreen));
            newOffset = (float) (height - childTop - minHeight) / (childHeight - minHeight);
        }

        boolean changeOffset = newOffset != onScreen;

        CoordinatorLayout.LayoutParams lp = (CoordinatorLayout.LayoutParams) mChild.getLayoutParams();
        int vgrav = lp.gravity & Gravity.VERTICAL_GRAVITY_MASK;

        Log.d(TAG, "height:"+height+",minHeight:"+minHeight);

        Log.d(TAG, "childTop1:"+childTop+",childHeight:"+childHeight);

        if (mInitTop <= 0) {
            mInitTop = childTop;
        }

        switch (vgrav) {
            default:
//            case Gravity.TOP: {
//                mChild.layout(childTop, lp.topMargin, childTop + childWidth,
//                        lp.topMargin + childHeight);
//                break;
//            }
            case GravityCompat.START:
            case Gravity.LEFT:
                mChild.layout(lp.leftMargin, childTop, lp.leftMargin + childWidth,
                        childTop + childHeight);
                break;

//            case Gravity.BOTTOM: {
//                mChild.layout(childTop,
//                        height - lp.bottomMargin - mChild.getMeasuredHeight(),
//                        childTop + childWidth,
//                        height - lp.bottomMargin);
//                break;
//            }
            case GravityCompat.END:
            case Gravity.RIGHT:
                mChild.layout(width - lp.rightMargin - mChild.getMeasuredWidth(),
                        childTop,
                        width - lp.rightMargin,
                        childTop + childHeight);
                break;

//            case Gravity.CENTER_VERTICAL: {
//                int childTop = (height - childHeight) / 2;
//
//                // Offset for margins. If things don't fit right because of
//                // bad measurement before, oh well.
//                if (childTop < lp.topMargin) {
//                    childTop = lp.topMargin;
//                } else if (childTop + childHeight > height - lp.bottomMargin) {
//                    childTop = height - lp.bottomMargin - childHeight;
//                }
//                mChild.layout(childTop, childTop, childTop + childWidth,
//                        childTop + childHeight);
//                break;
//            }

            case Gravity.CENTER_HORIZONTAL:
                int childLeft = (width - childWidth) / 2;

                // Offset for margins. If things don't fit right because of
                // bad measurement before, oh well.
                if (childLeft < lp.leftMargin) {
                    childLeft = lp.leftMargin;
                } else if (childLeft + childWidth > width - lp.rightMargin) {
                    childLeft = width - lp.rightMargin - childWidth;
                }
                mChild.layout(childLeft, childLeft, childLeft + childWidth,
                        childLeft + childHeight);
                break;
        }

        if (changeOffset) {
            setDrawerViewOffset(newOffset);
        }

        int newVisibility = minHeight > 0 ? VISIBLE : onScreen > 0 ? VISIBLE : INVISIBLE;
        if (mChild.getVisibility() != newVisibility) {
            mChild.setVisibility(VISIBLE);
        }
        return true;
    }
}
