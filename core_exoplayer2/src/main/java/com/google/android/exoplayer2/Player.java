package com.google.android.exoplayer2;

mport android.os.Looper;
import android.os.Looper;
import android.view.Surface;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.TextureView;
import androidx.annotation.Nullable;
import com.google.android.exoplayer2.audio.AudioAttributes;
import com.google.android.exoplayer2.audio.AudioListener;
import com.google.android.exoplayer2.audio.AuxEffectInfo;
import com.google.android.exoplayer2.source.TrackGroupArray;
import com.google.android.exoplayer2.text.TextOutput;
import com.google.android.exoplayer2.trackselection.TrackSelectionArray;
import com.google.android.exoplayer2.video.VideoFrameMetadataListener;
import com.google.android.exoplayer2.video.VideoListener;
import com.google.android.exoplayer2.video.spherical.CameraMotionListener;
import java.lang.annotation.Documented;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

public interface Player {
  int STATE_IDLE = 1;
  int STATE_BUFFERING = 2;
  int STATE_READY = 3;
  int STATE_ENDED = 4;
  int REPEAT_MODE_OFF = 0;
  int REPEAT_MODE_ONE = 1;
  int REPEAT_MODE_ALL = 2;
  int DISCONTINUITY_REASON_PERIOD_TRANSITION = 0;
  int DISCONTINUITY_REASON_SEEK = 1;
  int DISCONTINUITY_REASON_SEEK_ADJUSTMENT = 2;
  int DISCONTINUITY_REASON_AD_INSERTION = 3;
  int DISCONTINUITY_REASON_INTERNAL = 4;
  int TIMELINE_CHANGE_REASON_PREPARED = 0;
  int TIMELINE_CHANGE_REASON_RESET = 1;
  int TIMELINE_CHANGE_REASON_DYNAMIC = 2;

  @Nullable
  Player.AudioComponent getAudioComponent();

  @Nullable
  Player.VideoComponent getVideoComponent();

  @Nullable
  Player.TextComponent getTextComponent();

  Looper getApplicationLooper();

  void addListener(Player.EventListener var1);

  void removeListener(Player.EventListener var1);

  int getPlaybackState();

  @Nullable
  ExoPlaybackException getPlaybackError();

  void setPlayWhenReady(boolean var1);

  boolean getPlayWhenReady();

  void setRepeatMode(int var1);

  int getRepeatMode();

  void setShuffleModeEnabled(boolean var1);

  boolean getShuffleModeEnabled();

  boolean isLoading();

  void seekToDefaultPosition();

  void seekToDefaultPosition(int var1);

  void seekTo(long var1);

  void seekTo(int var1, long var2);

  boolean hasPrevious();

  void previous();

  boolean hasNext();

  void next();

  void setPlaybackParameters(@Nullable PlaybackParameters var1);

  PlaybackParameters getPlaybackParameters();

  void stop();

  void stop(boolean var1);

  void release();

  int getRendererCount();

  int getRendererType(int var1);

  TrackGroupArray getCurrentTrackGroups();

  TrackSelectionArray getCurrentTrackSelections();

  @Nullable
  Object getCurrentManifest();

  Timeline getCurrentTimeline();

  int getCurrentPeriodIndex();

  int getCurrentWindowIndex();

  int getNextWindowIndex();

  int getPreviousWindowIndex();

  @Nullable
  Object getCurrentTag();

  long getDuration();

  long getCurrentPosition();

  long getBufferedPosition();

  int getBufferedPercentage();

  long getTotalBufferedDuration();

  boolean isCurrentWindowDynamic();

  boolean isCurrentWindowSeekable();

  boolean isPlayingAd();

  int getCurrentAdGroupIndex();

  int getCurrentAdIndexInAdGroup();

  long getContentDuration();

  long getContentPosition();

  long getContentBufferedPosition();

  @Documented
  @Retention(RetentionPolicy.SOURCE)
  public @interface TimelineChangeReason {
  }

  @Documented
  @Retention(RetentionPolicy.SOURCE)
  public @interface DiscontinuityReason {
  }

  @Documented
  @Retention(RetentionPolicy.SOURCE)
  public @interface RepeatMode {
  }

  /** @deprecated */
  @Deprecated
  public abstract static class DefaultEventListener implements Player.EventListener {
    public DefaultEventListener() {
    }

    public void onTimelineChanged(Timeline timeline, @Nullable Object manifest, int reason) {
      this.onTimelineChanged(timeline, manifest);
    }

    /** @deprecated */
    @Deprecated
    public void onTimelineChanged(Timeline timeline, @Nullable Object manifest) {
    }
  }

  public interface EventListener {
    default void onTimelineChanged(Timeline timeline, @Nullable Object manifest, int reason) {
    }

    default void onTracksChanged(TrackGroupArray trackGroups, TrackSelectionArray trackSelections) {
    }

    default void onLoadingChanged(boolean isLoading) {
    }

    default void onPlayerStateChanged(boolean playWhenReady, int playbackState) {
    }

    default void onRepeatModeChanged(int repeatMode) {
    }

    default void onShuffleModeEnabledChanged(boolean shuffleModeEnabled) {
    }

    default void onPlayerError(ExoPlaybackException error) {
    }

    default void onPositionDiscontinuity(int reason) {
    }

    default void onPlaybackParametersChanged(PlaybackParameters playbackParameters) {
    }

    default void onSeekProcessed() {
    }
  }

  public interface TextComponent {
    void addTextOutput(TextOutput var1);

    void removeTextOutput(TextOutput var1);
  }

  public interface VideoComponent {
    void setVideoScalingMode(int var1);

    int getVideoScalingMode();

    void addVideoListener(VideoListener var1);

    void removeVideoListener(VideoListener var1);

    void setVideoFrameMetadataListener(VideoFrameMetadataListener var1);

    void clearVideoFrameMetadataListener(VideoFrameMetadataListener var1);

    void setCameraMotionListener(CameraMotionListener var1);

    void clearCameraMotionListener(CameraMotionListener var1);

    void clearVideoSurface();

    void clearVideoSurface(Surface var1);

    void setVideoSurface(@Nullable Surface var1);

    void setVideoSurfaceHolder(SurfaceHolder var1);

    void clearVideoSurfaceHolder(SurfaceHolder var1);

    void setVideoSurfaceView(SurfaceView var1);

    void clearVideoSurfaceView(SurfaceView var1);

    void setVideoTextureView(TextureView var1);

    void clearVideoTextureView(TextureView var1);
  }

  public interface AudioComponent {
    void addAudioListener(AudioListener var1);

    void removeAudioListener(AudioListener var1);

    /** @deprecated */
    @Deprecated
    void setAudioAttributes(AudioAttributes var1);

    void setAudioAttributes(AudioAttributes var1, boolean var2);

    AudioAttributes getAudioAttributes();

    int getAudioSessionId();

    void setAuxEffectInfo(AuxEffectInfo var1);

    void clearAuxEffectInfo();

    void setVolume(float var1);

    float getVolume();
  }
}

