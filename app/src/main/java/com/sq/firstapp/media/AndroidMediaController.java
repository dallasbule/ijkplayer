package com.sq.firstapp.media;

import android.annotation.SuppressLint;
import android.annotation.TargetApi;
import android.content.Context;
import android.content.res.Resources;
import android.graphics.PixelFormat;
import android.media.AudioManager;
import android.os.Build;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.v7.app.ActionBar;
import android.util.AttributeSet;
import android.util.Log;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.widget.FrameLayout;
import android.widget.ImageButton;
import android.widget.ProgressBar;
import android.widget.SeekBar;
import android.widget.SeekBar.OnSeekBarChangeListener;
import android.widget.TextView;
import android.widget.Toast;


import com.sq.firstapp.video.R;

import java.lang.reflect.Constructor;
import java.util.ArrayList;
import java.util.Formatter;
import java.util.Locale;


/**
 * 复制mediaControl系统源码，进行改写利用,便于自定义修改
 * 通过Window的方式来显示MediaController，MediaController是一个填充屏幕的布局，但是背景是透明的
 */


@TargetApi(Build.VERSION_CODES.HONEYCOMB)
public class AndroidMediaController extends FrameLayout implements IMediaController {

    private final Context mContext;
    private View mAnchor;// VideoView中调用setAnchorView()设置进来的View，MediaController显示的时候会根据该AnchorView的位置进行显示
    private View mRoot;// MediaController最外层的根布局
    private WindowManager mWindowManager;
    private Window mWindow;//整个contronl的window（窗口)
    private View mDecor;//整个View的最顶层布局，可理解用为这个view控制一些监听和事件，而mAnchor在被定位在这个view中
    private WindowManager.LayoutParams mDecorLayoutParams;//当前整个control控件的布局
    private ProgressBar mProgress;
    private TextView mEndTime, mCurrentTime;
    private boolean mShowing;
    private boolean mDragging;//是否是拖拽
    private static final int sDefaultTimeout = 100000;// 默认自动消失的时间
    private final boolean mUseFastForward;
    private boolean mFromXml;
    StringBuilder mFormatBuilder;
    Formatter mFormatter;
    private ImageButton mPauseButton;//暂停/播放按钮
    private ImageButton mFfwdButton;//快进按钮
    private ImageButton mRewButton;//快退按钮
    private CharSequence mPlayDescription;//播放状态时按钮对应文字
    private CharSequence mPauseDescription;//暂停状态时按钮对应文字
    private ActionBar mActionBar;
    private MediaPlayerControl mPlayer; //播放控制
    private int LastTime = 0;//记录播放的时间

    public AndroidMediaController(Context context, AttributeSet attrs) {
        super(context, attrs);
        mRoot = this;
        mContext = context;
        mUseFastForward = true;
        mFromXml = true;
    }

    @Override
    public void onFinishInflate() {
        super.onFinishInflate();
        if (mRoot != null)
            initControllerView(mRoot);
    }

    public AndroidMediaController(Context context, boolean useFastForward) {
        super(context);
        mContext = context;
        mUseFastForward = useFastForward;
        //创建该control的布局
        initFloatingWindowLayout();
        initFloatingWindow();
    }

    public AndroidMediaController(Context context) {
        this(context, true);
    }

    @SuppressLint("PrivateApi")
    private void initFloatingWindow() {
        mWindowManager = (WindowManager) mContext.getSystemService(Context.WINDOW_SERVICE);
        try {
            //利用反射获取PhoneWindow对象
            Class windowClass = null;
            windowClass = Class.forName("com.android.internal.policy.PhoneWindow");
            Constructor<?> localConstructor = null;
            localConstructor = windowClass.getConstructor(Context.class);
            mWindow = (Window) localConstructor.newInstance(this.getContext());
        } catch (Exception e) {
            e.printStackTrace();
        }
        mWindow.setWindowManager(mWindowManager, null, null);
        mWindow.requestFeature(Window.FEATURE_NO_TITLE);
        // 通过WindowManager去add该Decor以及remove来实现MediaController的显示与隐藏
        mDecor = mWindow.getDecorView();
        mDecor.setOnTouchListener(mTouchListener);
        mWindow.setContentView(this);
        //给该窗口设置透明模式
        mWindow.setBackgroundDrawableResource(android.R.color.transparent);

        // 控制音量
        mWindow.setVolumeControlStream(AudioManager.STREAM_MUSIC);

        setFocusable(true);
        setFocusableInTouchMode(true);
        //控制childView获取焦点的能力，该设置指先分发给ChildView进行处理
        setDescendantFocusability(ViewGroup.FOCUS_AFTER_DESCENDANTS);
        //使得该控件获取焦点
        requestFocus();
    }


    /**
     * 可以理解为对control布局的父布局的重新测量及定位
     */
    private void initFloatingWindowLayout() {
        mDecorLayoutParams = new WindowManager.LayoutParams();
        WindowManager.LayoutParams p = mDecorLayoutParams;
        p.gravity = Gravity.TOP | Gravity.LEFT;
        p.height = LayoutParams.WRAP_CONTENT;
        p.x = 0;
        // 用来设置弹出框（即这个window）的透明背景遮罩，若不设置则为黑色，此处为透明
        p.format = PixelFormat.TRANSLUCENT;
        //设置形成窗口的类型，此处为面板窗口，显示于宿主窗口的上层
        p.type = WindowManager.LayoutParams.TYPE_APPLICATION_PANEL;
        //设置窗口的属性，即是否有聚焦、可点击等等
        p.flags |= WindowManager.LayoutParams.FLAG_ALT_FOCUSABLE_IM
                | WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL
                | WindowManager.LayoutParams.FLAG_SPLIT_TOUCH;
        //获取当前Activity中的View中的TOken,来依附Activity
        p.token = null;
        //动画属性，此处不设置，也可设置为android.R.style.DropDownAnimationDown等
        p.windowAnimations = 0;
    }

    /**
     * 更新布局，其中anchor不可以为null,主要是对控制条在父布局的位置设置
     */
    private void updateFloatingWindowLayout() {
        int[] anchorPos = new int[2];
        //获取在整个屏幕内的绝对坐标
        mAnchor.getLocationOnScreen(anchorPos);


        //定位控制条的布局
        mDecor.measure(MeasureSpec.makeMeasureSpec(mAnchor.getWidth(), MeasureSpec.AT_MOST),
                MeasureSpec.makeMeasureSpec(mAnchor.getHeight(), MeasureSpec.AT_MOST));

        WindowManager.LayoutParams p = mDecorLayoutParams;
        p.width = mAnchor.getWidth();
        p.x = anchorPos[0] + (mAnchor.getWidth() - p.width) / 2;
        p.y = anchorPos[1] + mAnchor.getHeight() - mDecor.getMeasuredHeight();
    }

    /**
     * 用于更新布局的监听
     */
    private final OnLayoutChangeListener mLayoutChangeListener =
            new OnLayoutChangeListener() {
                @Override
                public void onLayoutChange(View v, int left, int top, int right,
                                           int bottom, int oldLeft, int oldTop, int oldRight,
                                           int oldBottom) {
                    updateFloatingWindowLayout();
                    if (mShowing) {
                        mWindowManager.updateViewLayout(mDecor, mDecorLayoutParams);
                    }
                }
            };


    /**
     * 此监听用来控制视频的控制条的显示与消失，
     * 可理解为点击屏幕若显示状态则消失，若消失状态则显示
     */
    private final OnTouchListener mTouchListener = new OnTouchListener() {
        @Override
        public boolean onTouch(View v, MotionEvent event) {
            if (event.getAction() == MotionEvent.ACTION_DOWN) {
                if (mShowing) {
                    hide();
                }
            }
            return false;
        }
    };


    /**
     * 控制条的布局设置
     *
     * @param view
     */
    public void setAnchorView(View view) {
        if (mAnchor != null) {
            mAnchor.removeOnLayoutChangeListener(mLayoutChangeListener);
        }
        mAnchor = view;
        if (mAnchor != null) {
            mAnchor.addOnLayoutChangeListener(mLayoutChangeListener);
        }

        FrameLayout.LayoutParams frameParams = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
        );

        removeAllViews();
        View v = makeControllerView();
        addView(v, frameParams);
    }

    /**
     * 创建控制条的布局
     */
    protected View makeControllerView() {
        LayoutInflater inflate = (LayoutInflater) mContext.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
        mRoot = inflate.inflate(R.layout.table_media_controller, null);

        initControllerView(mRoot);

        return mRoot;
    }

    /**
     * 对控制条的布局进行初始化设置，如按钮监听等
     *
     * @param v
     */
    private void initControllerView(View v) {
        Resources res = mContext.getResources();
        mPlayDescription = res
                .getText(R.string.lockscreen_transport_play_description);
        mPauseDescription = res
                .getText(R.string.lockscreen_transport_pause_description);
        mPauseButton = v.findViewById(R.id.pause);
        if (mPauseButton != null) {
            mPauseButton.requestFocus();
            mPauseButton.setOnClickListener(mPauseListener);
        }

        mFfwdButton = v.findViewById(R.id.ffwd);
        if (mFfwdButton != null) {
            mFfwdButton.setOnClickListener(mFfwdListener);
            if (!mFromXml) {
                mFfwdButton.setVisibility(mUseFastForward ? View.VISIBLE : View.GONE);
            }
        }

        mRewButton = v.findViewById(R.id.rew);
        if (mRewButton != null) {
            mRewButton.setOnClickListener(mRewListener);
            if (!mFromXml) {
                mRewButton.setVisibility(mUseFastForward ? View.VISIBLE : View.GONE);
            }
        }

        mProgress = v.findViewById(R.id.mediacontroller_progress);
        if (mProgress != null) {
            if (mProgress instanceof SeekBar) {
                SeekBar seeker = (SeekBar) mProgress;
                seeker.setOnSeekBarChangeListener(mSeekListener);
            }
            mProgress.setMax(1000);
        }

        mEndTime = v.findViewById(R.id.time);
        mCurrentTime = v.findViewById(R.id.time_current);
        mFormatBuilder = new StringBuilder();
        mFormatter = new Formatter(mFormatBuilder, Locale.getDefault());
    }

    /**
     * 如果视频是不能暂停或者快进快退的，则对这些按钮进行不可点击操作，如直播
     */
    private void disableUnsupportedButtons() {
        try {
            if (mPauseButton != null && !mPlayer.canPause()) {
                mPauseButton.setEnabled(false);
            }
            if (mRewButton != null && !mPlayer.canSeekBackward()) {
                mRewButton.setEnabled(false);
            }
            if (mFfwdButton != null && !mPlayer.canSeekForward()) {
                mFfwdButton.setEnabled(false);
            }
            if (mProgress != null && !mPlayer.canSeekBackward() && !mPlayer.canSeekForward()) {
                mProgress.setEnabled(false);
            }
        } catch (IncompatibleClassChangeError ex) {

        }
    }

    public void show() {
        this.show(sDefaultTimeout);
    }

    /**
     * 控制条显示
     *
     * @param timeout 超时时间，多少秒后会隐藏
     */
    public void show(int timeout) {
        if (!mShowing && mAnchor != null) {
            //当进度条显示的时候记录下进度
            if (LastTime <= mPlayer.getCurrentPosition()) {
                LastTime = mPlayer.getCurrentPosition();
            }
            setProgress(mPlayer.getCurrentPosition());
            if (mPauseButton != null) {
                mPauseButton.requestFocus();
            }
            disableUnsupportedButtons();
            updateFloatingWindowLayout();
            mWindowManager.addView(mDecor, mDecorLayoutParams);
            mShowing = true;
        }
        updatePausePlay();
        //当控制条显示时，实时对控制条进行ui更新
        post(mShowProgress);

        if (timeout != 0) {
            //清楚上一个创建的mFadeOut，同时开启下一个并延迟执行达到默认时间控制条消失的目的
            removeCallbacks(mFadeOut);
            postDelayed(mFadeOut, timeout);
        }
        if (mActionBar != null)
            mActionBar.show();
    }

    public boolean isShowing() {
        return mShowing;
    }

    /**
     * Remove the controller from the screen.
     */
    public void hide() {
        if (mAnchor == null)
            return;

        if (mShowing) {
            try {
                removeCallbacks(mShowProgress);
                mWindowManager.removeView(mDecor);
            } catch (IllegalArgumentException ex) {
                Log.w("MediaController", "already removed");
            }
            mShowing = false;
        }
        if (mActionBar != null)
            mActionBar.hide();
        for (View view : mShowOnceArray)
            view.setVisibility(View.GONE);
        mShowOnceArray.clear();
    }

    //隐藏控制条
    private final Runnable mFadeOut = new Runnable() {
        @Override
        public void run() {
            hide();
        }
    };

    //用于在显示的时候更新进度条(实时更新）
    private final Runnable mShowProgress = new Runnable() {
        @SuppressLint("SetTextI18n")
        @Override
        public void run() {
            int position = mPlayer.getCurrentPosition();
            int duration = mPlayer.getDuration();
            if (mDragging) {
                setProgress(position);
                position = setProgress(position);
            } else {
                if (position > LastTime) {
                    LastTime = position;
                }
            }
            if (mProgress != null) {
                if (duration > 0) {
                    // use long to avoid overflow
                    long pos = 1000L * position / duration;
                    mProgress.setProgress((int) pos);
                }
                int percent = mPlayer.getBufferPercentage();
                mProgress.setSecondaryProgress(percent * 10);
            }
            if (mEndTime != null)
                mEndTime.setText(stringForTime(duration));
            if (mCurrentTime != null)
                mCurrentTime.setText(stringForTime(position) + "(" + stringForTime(LastTime) + ")");
            if (mShowing && mPlayer.isPlaying()) {
                postDelayed(mShowProgress, 1000 - (position % 1000));
            }
        }
    };


    /**
     * 将时间转化为字符串
     *
     * @param timeMs 时间
     * @return
     */
    private String stringForTime(int timeMs) {
        int totalSeconds = timeMs / 1000;

        int seconds = totalSeconds % 60;
        int minutes = (totalSeconds / 60) % 60;
        int hours = totalSeconds / 3600;

        mFormatBuilder.setLength(0);
        if (hours > 0) {
            return mFormatter.format("%d:%02d:%02d", hours, minutes, seconds).toString();
        } else {
            return mFormatter.format("%02d:%02d", minutes, seconds).toString();
        }
    }

    /**
     * 更新进度条（主要用于刚刚显示进度条时候的定位，非实时更新)
     *
     * @param position 进度条所处位置
     * @return
     */
    private int setProgress(int position) {
        if (mPlayer == null) {
            return 0;
        }
        if (position < 0) {
            position = 0;
        }
        int duration = mPlayer.getDuration();
        if (mProgress != null) {
            if (duration > 0) {
                long pos = 1000L * position / duration;
                //判断是否快进超出播放位置，若是回退到播放的位置
                if (position > LastTime) {
                    mProgress.setProgress((LastTime * 1000) / duration);
                    mPlayer.seekTo(LastTime);
                    mCurrentTime.setText(stringForTime(LastTime));
                    Toast.makeText(mContext, "您还没有看完前面，无法快进", Toast.LENGTH_SHORT).show();
                } else {
                    mProgress.setProgress((int) pos);
                    mPlayer.seekTo(position);
                    mCurrentTime.setText(stringForTime(position));
                }

            }
            int percent = mPlayer.getBufferPercentage();
            mProgress.setSecondaryProgress(percent * 10);
        }

        if (mEndTime != null)
            mEndTime.setText(stringForTime(duration));

        return position > LastTime ? LastTime : position;
    }


    /**
     * 点击事件分发,主要是对控制条的显示与否的控制
     *
     * @param event
     * @return
     */
    @Override
    public boolean onTouchEvent(MotionEvent event) {
        switch (event.getAction()) {
            case MotionEvent.ACTION_DOWN:
                show(0); //为0时候该方法有个判断不执行控制条消失，也就是按下就会一直显示
                break;
            case MotionEvent.ACTION_UP:
                show(sDefaultTimeout); //松开则执行默认时间后控制条消失
                break;
            case MotionEvent.ACTION_CANCEL:
                hide();
                break;
            default:
                break;
        }
        return true;
    }

    @Override
    public boolean onTrackballEvent(MotionEvent ev) {
        show(sDefaultTimeout);
        return false;
    }

    @Override
    public boolean dispatchKeyEvent(KeyEvent event) {
        int keyCode = event.getKeyCode();
        final boolean uniqueDown = event.getRepeatCount() == 0
                && event.getAction() == KeyEvent.ACTION_DOWN;
        if (keyCode == KeyEvent.KEYCODE_HEADSETHOOK
                || keyCode == KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE
                || keyCode == KeyEvent.KEYCODE_SPACE) {
            if (uniqueDown) {
                doPauseResume();
                show(sDefaultTimeout);
                if (mPauseButton != null) {
                    mPauseButton.requestFocus();
                }
            }
            return true;
        } else if (keyCode == KeyEvent.KEYCODE_MEDIA_PLAY) {
            if (uniqueDown && !mPlayer.isPlaying()) {
                mPlayer.start();
                updatePausePlay();
                show(sDefaultTimeout);
            }
            return true;
        } else if (keyCode == KeyEvent.KEYCODE_MEDIA_STOP
                || keyCode == KeyEvent.KEYCODE_MEDIA_PAUSE) {
            if (uniqueDown && mPlayer.isPlaying()) {
                mPlayer.pause();
                updatePausePlay();
                show(sDefaultTimeout);
            }
            return true;
        } else if (keyCode == KeyEvent.KEYCODE_VOLUME_DOWN
                || keyCode == KeyEvent.KEYCODE_VOLUME_UP
                || keyCode == KeyEvent.KEYCODE_VOLUME_MUTE
                || keyCode == KeyEvent.KEYCODE_CAMERA) {
            // don't show the controls for volume adjustment
            return super.dispatchKeyEvent(event);
        } else if (keyCode == KeyEvent.KEYCODE_BACK || keyCode == KeyEvent.KEYCODE_MENU) {
            if (uniqueDown) {
                hide();
            }
            return true;
        }

        show(sDefaultTimeout);
        return super.dispatchKeyEvent(event);
    }

    private final View.OnClickListener mPauseListener = new View.OnClickListener() {
        @Override
        public void onClick(View v) {
            doPauseResume();
            show(sDefaultTimeout);
        }
    };

    /**
     * 显示暂停或者播放的按钮并修改对应文字
     */
    private void updatePausePlay() {
        if (mRoot == null || mPauseButton == null)
            return;

        if (mPlayer.isPlaying()) {
            mPauseButton.setImageResource(R.drawable.ic_media_pause);
            mPauseButton.setContentDescription(mPauseDescription);
        } else {
            mPauseButton.setImageResource(R.drawable.ic_media_play);
            mPauseButton.setContentDescription(mPlayDescription);
        }
    }

    /**
     *更改播放器状态（播放/停止）
     */
    private void doPauseResume() {
        if (mPlayer.isPlaying()) {
            mPlayer.pause();
        } else {
            mPlayer.start();
        }
        updatePausePlay();
    }


    /**
     * 进度条的监听，用于拖动进度条的一系列ui变化及事件操作
     */
    private final OnSeekBarChangeListener mSeekListener = new OnSeekBarChangeListener() {
        @Override
        public void onStartTrackingTouch(SeekBar bar) {
            show(3600000);
            mDragging = true;
            removeCallbacks(mShowProgress);
        }

        @Override
        public void onProgressChanged(SeekBar bar, int progress, boolean fromuser) {
            if (!fromuser) {
                return;
            }

            long duration = mPlayer.getDuration();
            long newposition = (duration * progress) / 1000L;
            if (newposition < LastTime) {
                mPlayer.seekTo((int) newposition);
            }
            mFfwdButton.setEnabled(false);
            mRewButton.setEnabled(false);
            mPlayer.pause();
            if (mCurrentTime != null)
                mCurrentTime.setText(stringForTime((int) newposition) + "(" + stringForTime(LastTime) + ")");
        }

        @Override
        public void onStopTrackingTouch(SeekBar bar) {
            updatePausePlay();
            mFfwdButton.setEnabled(true);
            mRewButton.setEnabled(true);
            mPlayer.start();
            mDragging = false;
            show(sDefaultTimeout);
            post(mShowProgress);
            updatePausePlay();
        }
    };

    @Override
    public void setEnabled(boolean enabled) {
        if (mPauseButton != null) {
            mPauseButton.setEnabled(enabled);
        }
        if (mFfwdButton != null) {
            mFfwdButton.setEnabled(enabled);
        }
        if (mRewButton != null) {
            mRewButton.setEnabled(enabled);
        }
        if (mProgress != null) {
            mProgress.setEnabled(enabled);
        }
        disableUnsupportedButtons();
        super.setEnabled(enabled);
    }

    @Override
    public void setMediaPlayer(MediaPlayerControl player) {
        mPlayer = player;
        updatePausePlay();
    }

    /**
     * 快退监听
     */
    private final View.OnClickListener mRewListener = new View.OnClickListener() {
        @Override
        public void onClick(View v) {
            int pos = mPlayer.getCurrentPosition();
            pos -= 5000; // milliseconds
            mPlayer.seekTo(pos);
            setProgress(pos);
            show(sDefaultTimeout);
        }
    };

    /**
     * 快进监听
     */
    private final View.OnClickListener mFfwdListener = new View.OnClickListener() {
        @Override
        public void onClick(View v) {
            int pos = mPlayer.getCurrentPosition();
            pos += 15000; // milliseconds
            setProgress(pos);
            show(sDefaultTimeout);
        }
    };

    public interface MediaPlayerControl {
        void start();

        void pause();

        int getDuration();

        int getCurrentPosition();

        void seekTo(int pos);

        boolean isPlaying();

        int getBufferPercentage();

        boolean canPause();

        boolean canSeekBackward();

        boolean canSeekForward();

        /**
         * Get the audio session id for the player used by this VideoView. This can be used to
         * apply audio effects to the audio track of a video.
         *
         * @return The audio session, or 0 if there was an error.
         */
        int getAudioSessionId();
    }

    /**
     * 自定义的一些方法,标题栏的显示与隐藏
     */
    public void setSupportActionBar(@Nullable ActionBar actionBar) {
        mActionBar = actionBar;
        if (isShowing()) {
            actionBar.show();
        } else {
            actionBar.hide();
        }
    }

    //----------
    // Extends
    //----------
    private ArrayList<View> mShowOnceArray = new ArrayList<View>();

    public void showOnce(@NonNull View view) {
        mShowOnceArray.add(view);
        view.setVisibility(View.VISIBLE);
        show(sDefaultTimeout);
    }
}
