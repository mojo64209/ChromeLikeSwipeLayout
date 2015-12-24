package com.asha;

import android.content.Context;
import android.content.res.Resources;
import android.content.res.TypedArray;
import android.graphics.PointF;
import android.support.annotation.ColorInt;
import android.support.annotation.DrawableRes;
import android.support.v4.view.ScrollingView;
import android.support.v4.view.ViewCompat;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.ViewGroup;
import android.view.animation.Animation;
import android.view.animation.DecelerateInterpolator;
import android.view.animation.Transformation;
import android.widget.AbsListView;
import android.widget.ScrollView;

import com.asha.library.R;

import java.util.LinkedList;
import java.util.List;

/**
 * Created by hzqiujiadi on 15/11/20.
 * hzqiujiadi ashqalcn@gmail.com
 */
public class ChromeLikeSwipeLayout extends ViewGroup implements TouchManager.ITouchCallback {

    private static final String TAG = "ChromeLikeSwipeLayout";
    private View mTarget; // the target of the gesture
    private ChromeLikeLayout mChromeLikeLayout;
    private int mCollapseDuration = 300;
    private boolean mAnimationStarted;
    private StatusManager mStatusManager = new StatusManager();
    private TouchManager mTouchManager = new TouchManager(this);
    private IOnItemSelectedListener mOnItemSelectedListener;
    private LinkedList<IOnExpandViewListener> mExpandListeners = new LinkedList<>();

    public ChromeLikeSwipeLayout(Context context) {
        this(context, null);
    }

    public ChromeLikeSwipeLayout(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public ChromeLikeSwipeLayout(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init();

        Config config = makeConfig();
        TypedArray ta = context.getTheme().obtainStyledAttributes(attrs, R.styleable.ChromeLikeSwipeLayout,defStyleAttr,0);
        if ( ta != null ){
            if (ta.hasValue(R.styleable.ChromeLikeSwipeLayout_circleColor))
                config.circleColor(ta.getColor(R.styleable.ChromeLikeSwipeLayout_circleColor,Config.DEFAULT));
            if ( ta.hasValue(R.styleable.ChromeLikeSwipeLayout_gap))
                config.gap(ta.getDimensionPixelOffset(R.styleable.ChromeLikeSwipeLayout_gap,Config.DEFAULT));
            if ( ta.hasValue(R.styleable.ChromeLikeSwipeLayout_radius))
                config.radius(ta.getDimensionPixelOffset(R.styleable.ChromeLikeSwipeLayout_radius,Config.DEFAULT));
            if ( ta.hasValue(R.styleable.ChromeLikeSwipeLayout_collapseDuration))
                config.collapseDuration(ta.getInt(R.styleable.ChromeLikeSwipeLayout_collapseDuration,Config.DEFAULT));
            if ( ta.hasValue(R.styleable.ChromeLikeSwipeLayout_rippleDuration))
                config.rippleDuration(ta.getInt(R.styleable.ChromeLikeSwipeLayout_rippleDuration,Config.DEFAULT));
            if ( ta.hasValue(R.styleable.ChromeLikeSwipeLayout_gummyDuration))
                config.gummyDuration(ta.getInt(R.styleable.ChromeLikeSwipeLayout_gummyDuration,Config.DEFAULT));
            ta.recycle();
        }
        config.setTo(this);
    }

    private void init() {
        final ViewConfiguration configuration = ViewConfiguration.get(getContext());
        mTouchManager.setTouchSlop(configuration.getScaledTouchSlop());

        mChromeLikeLayout = new ChromeLikeLayout(getContext());
        mChromeLikeLayout.setRippleListener(new ChromeLikeLayout.IOnRippleListener() {
            @Override
            public void onRippleAnimFinished(int index) {
                mStatusManager.toLoading();
                if ( mOnItemSelectedListener != null )
                    mOnItemSelectedListener.onItemSelected(index);
            }
        });
        addOnExpandViewListener(mChromeLikeLayout);
        addView(mChromeLikeLayout);
    }

    @Override
    public boolean onInterceptTouchEvent(MotionEvent event) {
        if ( mAnimationStarted ) return false;
        if ( canChildDragDown(mTouchManager.event2Point(event)) ) return false;
        return mTouchManager.onFeedInterceptEvent(event);
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        return mTouchManager.onFeedTouchEvent(event);
    }

    private void childOffsetTopAndBottom(int target){
        mTarget.offsetTopAndBottom( target );
        mChromeLikeLayout.offsetTopAndBottom( target );
        requestLayout();
    }

    // only execute on ACTION_UP
    private void executeAction(boolean isExpanded) {
        if ( isExpanded ){
            mStatusManager.toBusy();
        } else {
            if ( mStatusManager.isBusying() || mStatusManager.isLoading() ) return;
            if ( mAnimationStarted ) return;
            launchResetAnim();
            mTouchManager.endDrag();
        }
    }

    private void launchResetAnim() {
        boolean isFromCancel = !mStatusManager.isRecovering();
        launchResetAnim(isFromCancel);
    }

    private void launchResetAnim( final boolean isFromCancel ){
        ensureTarget();

        final int from = mTarget.getTop();
        final int to = 0;
        Animation animation = new Animation() {
            @Override
            protected void applyTransformation(float interpolatedTime, Transformation t) {
                float step = (to - from) * interpolatedTime + from;
                int top =  mTarget.getTop();
                notifyOnExpandListeners( mTouchManager.calExpandProgress(top) ,isFromCancel);
                childOffsetTopAndBottom( mTouchManager.calTargetTopOffset(top, Math.round(step)) );
            }
        };
        animation.setDuration(mCollapseDuration);
        animation.setInterpolator(new DecelerateInterpolator());
        animation.setAnimationListener(new AnimationListenerAdapter() {
            @Override
            public void onAnimationEnd(Animation animation) {
                mAnimationStarted = false;
                mStatusManager.toIdle();
            }
        });
        this.clearAnimation();
        this.startAnimation(animation);
        mAnimationStarted = true;
    }

    @Override
    public void addView(View child, int index, LayoutParams params) {

        boolean touchAlwaysTrue = child instanceof ScrollView
                || child instanceof AbsListView
                || child instanceof ScrollingView
                || child instanceof TouchAlwaysTrueLayout
                || child instanceof ChromeLikeLayout;

        if ( !touchAlwaysTrue ) child = TouchAlwaysTrueLayout.wrap(child);
        super.addView(child,index,params);
    }

    @Override
    protected void onLayout(boolean changed, int l, int t, int r, int b) {
        final int width = getMeasuredWidth();
        final int height = getMeasuredHeight();
        if (getChildCount() == 0) {
            return;
        }
        if (mTarget == null) {
            ensureTarget();
        }
        if (mTarget == null) {
            return;
        }
        View child = mTarget;
        int childLeft = getPaddingLeft();
        int childTop = child.getTop();
        int childWidth = width - getPaddingLeft() - getPaddingRight();
        int childHeight = height - getPaddingBottom();
        child.layout(childLeft, childTop, childLeft + childWidth, childTop + childHeight);

        child = mChromeLikeLayout;
        childLeft = getPaddingLeft();
        childTop = mTarget.getTop() - child.getMeasuredHeight();
        childWidth = width - getPaddingLeft() - getPaddingRight();
        childHeight = child.getMeasuredHeight();
        child.layout(childLeft, childTop, childLeft + childWidth, childTop + childHeight);
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        super.onMeasure(widthMeasureSpec, heightMeasureSpec);
        if (mTarget == null) {
            ensureTarget();
        }
        if (mTarget == null) {
            return;
        }
        final int width = getMeasuredWidth() - getPaddingLeft() - getPaddingRight();
        final int height = getMeasuredHeight() - getPaddingTop() - getPaddingBottom();
        mTarget.measure(MeasureSpec.makeMeasureSpec(width, MeasureSpec.EXACTLY),
                MeasureSpec.makeMeasureSpec(height, MeasureSpec.EXACTLY));
        mChromeLikeLayout.measure(MeasureSpec.makeMeasureSpec(width, MeasureSpec.EXACTLY),
                MeasureSpec.makeMeasureSpec(mTarget.getTop(), MeasureSpec.EXACTLY));
    }

    @Override
    public void requestDisallowInterceptTouchEvent(boolean b) {
        // Nope.
        //super.requestDisallowInterceptTouchEvent(b);
    }

    private boolean canChildDragDown(PointF pointF){
        ensureTarget();
        if ( mTarget instanceof TouchAlwaysTrueLayout )
            return ((TouchAlwaysTrueLayout) mTarget).canChildDragDown(pointF);
        else return ViewCompat.canScrollVertically(mTarget,-1);

    }

    private void ensureTarget() {
        // Don't bother getting the parent height if the parent hasn't been laid
        // out yet.
        if (mTarget == null) {
            for (int i = 0; i < getChildCount(); i++) {
                View child = getChildAt(i);
                if (!child.equals(mChromeLikeLayout)) {
                    mTarget = child;
                    mChromeLikeLayout.bringToFront();
                    break;
                }
            }
        }
    }

    private void setCollapseDuration(int collapseDuration) {
        this.mCollapseDuration = collapseDuration;
    }

    private void setConfig(Config config){
        if ( config.mIcons != null )
            mChromeLikeLayout.setIcons(config.mIcons);
        if ( config.mBackgroundResId != Config.DEFAULT )
            mChromeLikeLayout.setBackgroundResource(config.mBackgroundResId);
        if ( config.mBackgroundColor != Config.DEFAULT )
            mChromeLikeLayout.setBackgroundColor(config.mBackgroundColor);
        if ( config.mCircleColor != Config.DEFAULT )
            mChromeLikeLayout.setCircleColor(config.mCircleColor);
        if ( config.mRadius != Config.DEFAULT )
            mChromeLikeLayout.setRadius(config.mRadius);
        if ( config.mRadius != Config.DEFAULT )
            mChromeLikeLayout.setGap(config.mGap);
        if ( config.mRippleDuration != Config.DEFAULT )
            mChromeLikeLayout.setRippleDuration(config.mRippleDuration);
        if ( config.mGummyDuration != Config.DEFAULT )
            mChromeLikeLayout.setGummyDuration(config.mGummyDuration);
        if ( config.mCollapseDuration != Config.DEFAULT )
            setCollapseDuration(config.mCollapseDuration);

        mOnItemSelectedListener = config.mOnItemSelectedListener;
    }

    public void notifyOnExpandListeners(float fraction, boolean isFromCancel){
        fraction = fraction < 1 ? fraction : 1;
        for ( IOnExpandViewListener listener : mExpandListeners )
            listener.onExpandView(fraction,isFromCancel);
    }

    public void addOnExpandViewListener(IOnExpandViewListener listener){
        mExpandListeners.add(listener);
    }

    public void removeOnExpandViewListener(IOnExpandViewListener listener){
        mExpandListeners.remove(listener);
    }

    public void removeAllOnExpandViewListener(){
        mExpandListeners.clear();
    }

    public void complete(){
        if ( mStatusManager.isLoading() ){
            mStatusManager.toRecovering();
            if ( !mAnimationStarted ) launchResetAnim();
            mTouchManager.endDrag();
        }
    }

    @Override
    public void onActionDown() {
        if ( !mStatusManager.isLoading() )
            mChromeLikeLayout.onActionDown();
    }

    @Override
    public void onActionUp(boolean isExpanded) {
        if ( !mStatusManager.isLoading() ){
            executeAction(isExpanded);
            mChromeLikeLayout.onActionUpOrCancel(isExpanded);
        }
    }

    @Override
    public void onActionCancel(boolean isExpanded) {
        if ( !mStatusManager.isLoading() )
            mChromeLikeLayout.onActionUpOrCancel(isExpanded);
    }

    @Override
    public void onActionMove(boolean isExpanded, TouchManager touchManager) {
        if ( !mStatusManager.isLoading() )
            mChromeLikeLayout.onActionMove(isExpanded, touchManager);
        ensureTarget();
        View child = mTarget;
        int currentTop = child.getTop();
        if ( mTouchManager.isBeginDragging() ) {
            if ( !isExpanded && !mStatusManager.isLoading() )
                notifyOnExpandListeners(mTouchManager.calExpandProgress(currentTop), true);
            childOffsetTopAndBottom(mTouchManager.calTargetTopOffset(currentTop));
        }
    }

    @Override
    public void onBeginDragging() {
        mStatusManager.toChanged();
    }

    public interface IOnExpandViewListener {
        void onExpandView(float fraction, boolean isFromCancel);
    }

    public static Config makeConfig(){
        return new Config();
    }

    /***
     *
     * Builder
     *
     * */
    public static class Config{
        private List<Integer> mIcons;
        private IOnItemSelectedListener mOnItemSelectedListener;
        private int mCircleColor = DEFAULT;
        private int mBackgroundResId = DEFAULT;
        private int mBackgroundColor = DEFAULT;
        private int mRadius = DEFAULT;
        private int mGap = DEFAULT;
        private int mCollapseDuration = DEFAULT;
        private int mRippleDuration = DEFAULT;
        private int mGummyDuration = DEFAULT;
        private static final int DEFAULT = -1;

        private Config(){

        }

        public Config addIcon(@DrawableRes int drawableResId){
            if ( mIcons == null ) mIcons = new LinkedList<>();
            mIcons.add(drawableResId);
            return this;
        }

        public Config background(@DrawableRes int backgroundResId){
            this.mBackgroundResId = backgroundResId;
            return this;
        }

        public Config backgroundColor(@ColorInt int color){
            this.mBackgroundColor = color;
            return this;
        }

        public Config circleColor(@ColorInt int color){
            this.mCircleColor = color;
            return this;
        }

        public Config listenItemSelected(IOnItemSelectedListener listener){
            this.mOnItemSelectedListener = listener;
            return this;
        }

        public Config radius(int radius){
            this.mRadius = radius;
            return this;
        }

        public Config gap(int gap){
            this.mGap = gap;
            return this;
        }

        public Config collapseDuration(int duration){
            this.mCollapseDuration = duration;
            return this;
        }

        public Config rippleDuration(int duration){
            this.mRippleDuration = duration;
            return this;
        }

        public Config gummyDuration(int duration){
            this.mGummyDuration = duration;
            return this;
        }

        public void setTo(ChromeLikeSwipeLayout chromeLikeSwipeLayout){
            chromeLikeSwipeLayout.setConfig(this);
        }
    }

    public static int dp2px(float valueInDp) {
        final float scale = Resources.getSystem().getDisplayMetrics().density;
        return (int) (valueInDp * scale + 0.5f);
    }

    public interface IOnItemSelectedListener{
        void onItemSelected(int index);
    }

    /****
     *
     * Response for managing dropdown status
     *
     * */
    public static class StatusManager {
        private byte mStatus = STATUS_IDLE;
        private static final byte STATUS_IDLE       = 0;
        private static final byte STATUS_CHANGED    = 1;
        private static final byte STATUS_BUSY       = 2;
        private static final byte STATUS_LOADING    = 3;
        private static final byte STATUS_RECOVERING = 4;


        public void toIdle(){
            mStatus = STATUS_IDLE;
        }

        public void toBusy(){
            mStatus = STATUS_BUSY;
        }

        public void toLoading(){
            mStatus = STATUS_LOADING;
        }

        public void toChanged(){
            mStatus = STATUS_CHANGED;
        }

        public boolean isChanged(){
            return  mStatus == STATUS_CHANGED;
        }

        public boolean isBusying(){
            return mStatus == STATUS_BUSY;
        }

        public void toRecovering(){
            mStatus = STATUS_RECOVERING;
        }

        public boolean isIdle(){
            return mStatus == STATUS_IDLE;
        }

        public boolean isLoading() {
            return mStatus == STATUS_LOADING;
        }

        public boolean isRecovering() {
            return mStatus == STATUS_RECOVERING;
        }
    }
}
