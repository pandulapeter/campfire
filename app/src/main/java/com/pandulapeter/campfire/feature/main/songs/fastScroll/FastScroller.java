package com.pandulapeter.campfire.feature.main.songs.fastScroll;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ObjectAnimator;
import android.content.Context;
import android.content.res.Resources;
import android.content.res.TypedArray;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Point;
import android.graphics.Rect;
import android.support.annotation.IntDef;
import android.support.v4.view.animation.FastOutLinearInInterpolator;
import android.support.v4.view.animation.LinearOutSlowInInterpolator;
import android.support.v7.widget.RecyclerView;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.ViewConfiguration;

import com.pandulapeter.campfire.R;

import java.lang.annotation.Retention;

import static java.lang.annotation.RetentionPolicy.SOURCE;

public class FastScroller {
    private FastScrollRecyclerView recyclerView;
    private FastScrollPopup popup;
    private int thumbHeight;
    private int width;
    private Paint thumb;
    private Paint track;
    private Rect tempRect = new Rect();
    private Rect invalidateRect = new Rect();
    private Rect invalidateTmpRect = new Rect();
    // The inset is the buffer around which a point will still register as a click on the scrollbar
    private int touchInset;
    // This is the offset from the top of the scrollbar when the user first starts touching.  To
    // prevent jumping, this offset is applied as the user scrolls.
    private int touchOffset;
    private Point thumbPosition = new Point(-1, -1);
    private Point offset = new Point(0, 0);
    private boolean isDragging;
    private Animator autoHideAnimator;
    private boolean animatingShow;
    private boolean autoHideEnabled;
    private final Runnable hideRunnable;
    private int thumbActiveColor = 0x79000000;
    private int thumbInactiveColor = 0x79000000;
    private boolean thumbInactiveState = true;

    @Retention(SOURCE)
    @IntDef({FastScroller.FastScrollerPopupPosition.ADJACENT, FastScroller.FastScrollerPopupPosition.CENTER})
    public @interface FastScrollerPopupPosition {
        int ADJACENT = 0;
        int CENTER = 1;
    }

    FastScroller(Context context, FastScrollRecyclerView recyclerView, AttributeSet attrs) {
        Resources resources = context.getResources();
        this.recyclerView = recyclerView;
        popup = new FastScrollPopup(resources, recyclerView);
        thumbHeight = Utils.toPixels(resources, 48);
        width = Utils.toPixels(resources, 8);
        touchInset = Utils.toPixels(resources, -24);
        thumb = new Paint(Paint.ANTI_ALIAS_FLAG);
        track = new Paint(Paint.ANTI_ALIAS_FLAG);
        TypedArray typedArray = context.getTheme().obtainStyledAttributes(attrs, R.styleable.FastScrollRecyclerView, 0, 0);
        try {
            autoHideEnabled = typedArray.getBoolean(R.styleable.FastScrollRecyclerView_fastScrollAutoHide, true);
            int popupBgColor = typedArray.getColor(R.styleable.FastScrollRecyclerView_fastScrollPopupBgColor, 0xff000000);
            int popupTextColor = typedArray.getColor(R.styleable.FastScrollRecyclerView_fastScrollPopupTextColor, 0xffffffff);
            int popupTextSize = typedArray.getDimensionPixelSize(R.styleable.FastScrollRecyclerView_fastScrollPopupTextSize, Utils.toScreenPixels(resources, 44));
            int popupBackgroundSize = typedArray.getDimensionPixelSize(R.styleable.FastScrollRecyclerView_fastScrollPopupBackgroundSize, Utils.toPixels(resources, 88));
            track.setColor(0x28000000);
            thumb.setColor(thumbInactiveState ? thumbInactiveColor : thumbActiveColor);
            popup.setBgColor(popupBgColor);
            popup.setTextColor(popupTextColor);
            popup.setTextSize(popupTextSize);
            popup.setBackgroundSize(popupBackgroundSize);
            popup.setPopupPosition(FastScroller.FastScrollerPopupPosition.ADJACENT);
        } finally {
            typedArray.recycle();
        }

        hideRunnable = new Runnable() {
            @Override
            public void run() {
                if (!isDragging) {
                    if (autoHideAnimator != null) {
                        autoHideAnimator.cancel();
                    }
                    autoHideAnimator = ObjectAnimator.ofInt(FastScroller.this, "offsetX", (Utils.isRtl(FastScroller.this.recyclerView.getResources()) ? -1 : 1) * width);
                    autoHideAnimator.setInterpolator(new FastOutLinearInInterpolator());
                    autoHideAnimator.setDuration(200);
                    autoHideAnimator.start();
                }
            }
        };

        this.recyclerView.addOnScrollListener(new RecyclerView.OnScrollListener() {
            @Override
            public void onScrolled(RecyclerView recyclerView, int dx, int dy) {
                super.onScrolled(recyclerView, dx, dy);

                if (!FastScroller.this.recyclerView.isInEditMode()) {
                    show();
                }
            }
        });

        if (autoHideEnabled) {
            postAutoHideDelayed();
        }
    }

    int getThumbHeight() {
        return thumbHeight;
    }

    public int getWidth() {
        return width;
    }

    boolean isDragging() {
        return isDragging;
    }

    /**
     * Handles the touch event and determines whether to show the fast scroller (or updates it if
     * it is already showing).
     */
    void handleTouchEvent(MotionEvent ev, int downX, int downY, int lastY,
                          OnFastScrollStateChangeListener stateChangeListener) {
        ViewConfiguration config = ViewConfiguration.get(recyclerView.getContext());

        int action = ev.getAction();
        int y = (int) ev.getY();
        switch (action) {
            case MotionEvent.ACTION_DOWN:
                if (isNearPoint(downX, downY)) {
                    touchOffset = downY - thumbPosition.y;
                }
                break;
            case MotionEvent.ACTION_MOVE:
                // Check if we should start scrolling
                if (!isDragging && isNearPoint(downX, downY) &&
                        Math.abs(y - downY) > config.getScaledTouchSlop()) {
                    recyclerView.getParent().requestDisallowInterceptTouchEvent(true);
                    isDragging = true;
                    touchOffset += (lastY - downY);
                    popup.animateVisibility(true);
                    if (stateChangeListener != null) {
                        stateChangeListener.onFastScrollStart();
                    }
                    if (thumbInactiveState) {
                        thumb.setColor(thumbActiveColor);
                    }
                }
                if (isDragging) {
                    // Update the fastscroller section name at this touch position
                    int top = 0;
                    int bottom = recyclerView.getHeight() - thumbHeight;
                    float boundedY = (float) Math.max(top, Math.min(bottom, y - touchOffset));
                    String sectionName = recyclerView.scrollToPositionAtProgress((boundedY - top) / (bottom - top));
                    popup.setSectionName(sectionName);
                    popup.animateVisibility(!sectionName.isEmpty());
                    recyclerView.invalidate(popup.updateFastScrollerBounds(recyclerView, thumbPosition.y));
                }
                break;
            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL:
                touchOffset = 0;
                if (isDragging) {
                    isDragging = false;
                    popup.animateVisibility(false);
                    if (stateChangeListener != null) {
                        stateChangeListener.onFastScrollStop();
                    }
                }
                if (thumbInactiveState) {
                    thumb.setColor(thumbInactiveColor);
                }
                break;
        }
    }

    void draw(Canvas canvas) {

        if (thumbPosition.x < 0 || thumbPosition.y < 0) {
            return;
        }

        //Background
        canvas.drawRect(thumbPosition.x + offset.x, offset.y, thumbPosition.x + offset.x + width, recyclerView.getHeight() + offset.y, track);

        //Handle
        canvas.drawRect(thumbPosition.x + offset.x, thumbPosition.y + offset.y, thumbPosition.x + offset.x + width, thumbPosition.y + offset.y + thumbHeight, thumb);

        //Popup
        popup.draw(canvas);
    }

    /**
     * Returns whether the specified points are near the scroll bar bounds.
     */
    private boolean isNearPoint(int x, int y) {
        tempRect.set(thumbPosition.x, thumbPosition.y, thumbPosition.x + width,
                thumbPosition.y + thumbHeight);
        tempRect.inset(touchInset, touchInset);
        return tempRect.contains(x, y);
    }

    void setThumbPosition(int x, int y) {
        if (thumbPosition.x == x && thumbPosition.y == y) {
            return;
        }
        // do not create new objects here, this is called quite often
        invalidateRect.set(thumbPosition.x + offset.x, offset.y, thumbPosition.x + offset.x + width, recyclerView.getHeight() + offset.y);
        thumbPosition.set(x, y);
        invalidateTmpRect.set(thumbPosition.x + offset.x, offset.y, thumbPosition.x + offset.x + width, recyclerView.getHeight() + offset.y);
        invalidateRect.union(invalidateTmpRect);
        recyclerView.invalidate(invalidateRect);
    }

    public void show() {
        if (!animatingShow) {
            if (autoHideAnimator != null) {
                autoHideAnimator.cancel();
            }
            autoHideAnimator = ObjectAnimator.ofInt(this, "offsetX", 0);
            autoHideAnimator.setInterpolator(new LinearOutSlowInInterpolator());
            autoHideAnimator.setDuration(150);
            autoHideAnimator.addListener(new AnimatorListenerAdapter() {
                @Override
                public void onAnimationCancel(Animator animation) {
                    super.onAnimationCancel(animation);
                    animatingShow = false;
                }

                @Override
                public void onAnimationEnd(Animator animation) {
                    super.onAnimationEnd(animation);
                    animatingShow = false;
                }
            });
            animatingShow = true;
            autoHideAnimator.start();
        }
        if (autoHideEnabled) {
            postAutoHideDelayed();
        } else {
            cancelAutoHide();
        }
    }

    private void postAutoHideDelayed() {
        if (recyclerView != null) {
            cancelAutoHide();
            int autoHideDelay = 1500;
            recyclerView.postDelayed(hideRunnable, autoHideDelay);
        }
    }

    private void cancelAutoHide() {
        if (recyclerView != null) {
            recyclerView.removeCallbacks(hideRunnable);
        }
    }

    void enableThumbInactiveColor(boolean enableInactiveColor) {
        thumbInactiveState = enableInactiveColor;
        thumb.setColor(thumbInactiveState ? thumbInactiveColor : thumbActiveColor);
    }
}