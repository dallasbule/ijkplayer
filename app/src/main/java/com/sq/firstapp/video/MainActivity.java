package com.sq.firstapp.video;

import android.net.Uri;
import android.os.Environment;
import android.support.v7.app.ActionBar;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.support.v7.widget.Toolbar;
import android.util.Log;

import com.sq.firstapp.media.AndroidMediaController;
import com.sq.firstapp.media.IjkVideoView;

import java.nio.file.Path;

import tv.danmaku.ijk.media.player.IMediaPlayer;
import tv.danmaku.ijk.media.player.IjkMediaPlayer;

public class MainActivity extends AppCompatActivity implements IMediaPlayer.OnPreparedListener {

    private IjkVideoView videoView;
    //控制播放进度
    Toolbar toolbar;
    private AndroidMediaController mMediaController;
    private String path = "http://hc.yinyuetai.com/uploads/videos/common/2B40015FD4683805AAD2D7D35A80F606.mp4?sc=364e86c8a7f42de3&br=783&rd=Android";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        boolean sdCardExist = Environment.getExternalStorageState()
                .equals(android.os.Environment.MEDIA_MOUNTED);
        if (sdCardExist) {
            path = "/storage/emulated/0/BaiduNetdisk/RunningMan.mkv";//获取根目录
        }
        init();
    }

    private void init() {

        toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);

        ActionBar actionBar = getSupportActionBar();
        mMediaController = new AndroidMediaController(this, true);
        mMediaController.setSupportActionBar(actionBar);

        videoView = findViewById(R.id.video_view);
        videoView.setMediaController(mMediaController);

        IjkMediaPlayer.loadLibrariesOnce(null);
        IjkMediaPlayer.native_profileBegin("libijkplayer.so");

        videoView.setOnPreparedListener(this);
        videoView.setVideoPath(path);
        videoView.start();
    }

    @Override
    public void onPrepared(IMediaPlayer iMediaPlayer) {

    }
}
