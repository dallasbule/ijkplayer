/**
 * 此文件为利用源码MediaControl来实现，为官方实现方式
 */

package com.sq.firstapp.widget.media;

import android.content.Context;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.v7.app.ActionBar;
import android.util.AttributeSet;
import android.view.View;
import android.widget.MediaController;

import java.util.ArrayList;


public class AndroidMediaController2 extends MediaController implements IMediaController {
    private ActionBar mActionBar;

    public AndroidMediaController2(Context context, AttributeSet attrs) {
        super(context, attrs);
        initView(context);
    }

    public AndroidMediaController2(Context context, boolean useFastForward) {
        super(context, useFastForward);
        initView(context);
    }

    public AndroidMediaController2(Context context) {
        super(context);
        initView(context);
    }

    private void initView(Context context) {
    }

    public void setSupportActionBar(@Nullable ActionBar actionBar) {
        mActionBar = actionBar;
        if (isShowing()) {
            actionBar.show();
        } else {
            actionBar.hide();
        }
    }

    @Override
    public void show() {
        super.show();
        if (mActionBar != null)
            mActionBar.show();
    }

    @Override
    public void hide() {
        super.hide();
        if (mActionBar != null)
            mActionBar.hide();
        for (View view : mShowOnceArray)
            view.setVisibility(View.GONE);
        mShowOnceArray.clear();
    }

    @Override
    public void setMediaPlayer(AndroidMediaController.MediaPlayerControl player) {

    }

    //----------
    // Extends
    //----------
    private ArrayList<View> mShowOnceArray = new ArrayList<View>();

    public void showOnce(@NonNull View view) {
        mShowOnceArray.add(view);
        view.setVisibility(View.VISIBLE);
        show();
    }
}
