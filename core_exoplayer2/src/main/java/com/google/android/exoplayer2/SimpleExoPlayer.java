package com.google.android.exoplayer2;

import android.annotation.TargetApi;
import android.content.Context;
import android.graphics.Rect;
import android.graphics.SurfaceTexture;
import android.media.PlaybackParams;
import android.os.Handler;
import android.os.Looper;
import android.view.Surface;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.TextureView;
import android.view.SurfaceHolder.Callback;
import android.view.TextureView.SurfaceTextureListener;
import androidx.annotation.Nullable;
import com.google.android.exoplayer2.ExoPlayer.ExoPlayerMessage;
import com.google.android.exoplayer2.Player.AudioComponent;
import com.google.android.exoplayer2.Player.EventListener;
import com.google.android.exoplayer2.Player.TextComponent;
import com.google.android.exoplayer2.Player.VideoComponent;
import com.google.android.exoplayer2.PlayerMessage.Target;
import com.google.android.exoplayer2.analytics.AnalyticsCollector;
import com.google.android.exoplayer2.analytics.AnalyticsListener;
import com.google.android.exoplayer2.analytics.AnalyticsCollector.Factory;
import com.google.android.exoplayer2.audio.AudioAttributes;
import com.google.android.exoplayer2.audio.AudioFocusManager;
import com.google.android.exoplayer2.audio.AudioListener;
import com.google.android.exoplayer2.audio.AudioRendererEventListener;
import com.google.android.exoplayer2.audio.AuxEffectInfo;
import com.google.android.exoplayer2.audio.AudioAttributes.Builder;
import com.google.android.exoplayer2.audio.AudioFocusManager.PlayerControl;
import com.google.android.exoplayer2.decoder.DecoderCounters;
import com.google.android.exoplayer2.drm.DefaultDrmSessionManager;
import com.google.android.exoplayer2.drm.DrmSessionManager;
import com.google.android.exoplayer2.drm.FrameworkMediaCrypto;
import com.google.android.exoplayer2.metadata.Metadata;
import com.google.android.exoplayer2.metadata.MetadataOutput;
import com.google.android.exoplayer2.source.MediaSource;
import com.google.android.exoplayer2.source.TrackGroupArray;
import com.google.android.exoplayer2.text.Cue;
import com.google.android.exoplayer2.text.TextOutput;
import com.google.android.exoplayer2.trackselection.TrackSelectionArray;
import com.google.android.exoplayer2.trackselection.TrackSelector;
import com.google.android.exoplayer2.upstream.BandwidthMeter;
import com.google.android.exoplayer2.util.Clock;
import com.google.android.exoplayer2.util.Log;
import com.google.android.exoplayer2.util.Util;
import com.google.android.exoplayer2.video.VideoFrameMetadataListener;
import com.google.android.exoplayer2.video.VideoRendererEventListener;
import com.google.android.exoplayer2.video.spherical.CameraMotionListener;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.CopyOnWriteArraySet;

@TargetApi(16)
public class SimpleExoPlayer extends BasePlayer implements ExoPlayer, AudioComponent, VideoComponent, TextComponent {
  private static final String TAG = "SimpleExoPlayer";
  protected final Renderer[] renderers;
  private final ExoPlayerImpl player;
  private final Handler eventHandler;
  private final SimpleExoPlayer.ComponentListener componentListener;
  private final CopyOnWriteArraySet<com.google.android.exoplayer2.video.VideoListener> videoListeners;
  private final CopyOnWriteArraySet<AudioListener> audioListeners;
  private final CopyOnWriteArraySet<TextOutput> textOutputs;
  private final CopyOnWriteArraySet<MetadataOutput> metadataOutputs;
  private final CopyOnWriteArraySet<VideoRendererEventListener> videoDebugListeners;
  private final CopyOnWriteArraySet<AudioRendererEventListener> audioDebugListeners;
  private final BandwidthMeter bandwidthMeter;
  private final AnalyticsCollector analyticsCollector;
  private final AudioFocusManager audioFocusManager;
  private Format videoFormat;
  private Format audioFormat;
  private Surface surface;
  private boolean ownsSurface;
  private int videoScalingMode;
  private SurfaceHolder surfaceHolder;
  private TextureView textureView;
  private int surfaceWidth;
  private int surfaceHeight;
  private DecoderCounters videoDecoderCounters;
  private DecoderCounters audioDecoderCounters;
  private int audioSessionId;
  private AudioAttributes audioAttributes;
  private float audioVolume;
  private MediaSource mediaSource;
  private List<Cue> currentCues;
  private VideoFrameMetadataListener videoFrameMetadataListener;
  private CameraMotionListener cameraMotionListener;
  private boolean hasNotifiedFullWrongThreadWarning;

  protected SimpleExoPlayer(Context context, RenderersFactory renderersFactory, TrackSelector trackSelector, LoadControl loadControl, BandwidthMeter bandwidthMeter, @Nullable DrmSessionManager<FrameworkMediaCrypto> drmSessionManager, Looper looper) {
    this(context, renderersFactory, trackSelector, loadControl, drmSessionManager, bandwidthMeter, new Factory(), looper);
  }

  protected SimpleExoPlayer(Context context, RenderersFactory renderersFactory, TrackSelector trackSelector, LoadControl loadControl, @Nullable DrmSessionManager<FrameworkMediaCrypto> drmSessionManager, BandwidthMeter bandwidthMeter, Factory analyticsCollectorFactory, Looper looper) {
    this(context, renderersFactory, trackSelector, loadControl, drmSessionManager, bandwidthMeter, analyticsCollectorFactory, Clock.DEFAULT, looper);
  }

  protected SimpleExoPlayer(Context context, RenderersFactory renderersFactory, TrackSelector trackSelector, LoadControl loadControl, @Nullable DrmSessionManager<FrameworkMediaCrypto> drmSessionManager, BandwidthMeter bandwidthMeter, Factory analyticsCollectorFactory, Clock clock, Looper looper) {
    this.bandwidthMeter = bandwidthMeter;
    this.componentListener = new SimpleExoPlayer.ComponentListener();
    this.videoListeners = new CopyOnWriteArraySet();
    this.audioListeners = new CopyOnWriteArraySet();
    this.textOutputs = new CopyOnWriteArraySet();
    this.metadataOutputs = new CopyOnWriteArraySet();
    this.videoDebugListeners = new CopyOnWriteArraySet();
    this.audioDebugListeners = new CopyOnWriteArraySet();
    this.eventHandler = new Handler(looper);
    this.renderers = renderersFactory.createRenderers(this.eventHandler, this.componentListener, this.componentListener, this.componentListener, this.componentListener, drmSessionManager);
    this.audioVolume = 1.0F;
    this.audioSessionId = 0;
    this.audioAttributes = AudioAttributes.DEFAULT;
    this.videoScalingMode = 1;
    this.currentCues = Collections.emptyList();
    this.player = new ExoPlayerImpl(this.renderers, trackSelector, loadControl, bandwidthMeter, clock, looper);
    this.analyticsCollector = analyticsCollectorFactory.createAnalyticsCollector(this.player, clock);
    this.addListener(this.analyticsCollector);
    this.videoDebugListeners.add(this.analyticsCollector);
    this.videoListeners.add(this.analyticsCollector);
    this.audioDebugListeners.add(this.analyticsCollector);
    this.audioListeners.add(this.analyticsCollector);
    this.addMetadataOutput(this.analyticsCollector);
    bandwidthMeter.addEventListener(this.eventHandler, this.analyticsCollector);
    if (drmSessionManager instanceof DefaultDrmSessionManager) {
      ((DefaultDrmSessionManager)drmSessionManager).addListener(this.eventHandler, this.analyticsCollector);
    }

    this.audioFocusManager = new AudioFocusManager(context, this.componentListener);
  }

  public AudioComponent getAudioComponent() {
    return this;
  }

  public VideoComponent getVideoComponent() {
    return this;
  }

  public TextComponent getTextComponent() {
    return this;
  }

  public void setVideoScalingMode(int videoScalingMode) {
    this.verifyApplicationThread();
    this.videoScalingMode = videoScalingMode;
    Renderer[] var2 = this.renderers;
    int var3 = var2.length;

    for(int var4 = 0; var4 < var3; ++var4) {
      Renderer renderer = var2[var4];
      if (renderer.getTrackType() == 2) {
        this.player.createMessage(renderer).setType(4).setPayload(videoScalingMode).send();
      }
    }

  }

  public int getVideoScalingMode() {
    return this.videoScalingMode;
  }

  public void clearVideoSurface() {
    this.verifyApplicationThread();
    this.setVideoSurface((Surface)null);
  }

  public void clearVideoSurface(Surface surface) {
    this.verifyApplicationThread();
    if (surface != null && surface == this.surface) {
      this.setVideoSurface((Surface)null);
    }

  }

  public void setVideoSurface(@Nullable Surface surface) {
    this.verifyApplicationThread();
    this.removeSurfaceCallbacks();
    this.setVideoSurfaceInternal(surface, false);
    int newSurfaceSize = surface == null ? 0 : -1;
    this.maybeNotifySurfaceSizeChanged(newSurfaceSize, newSurfaceSize);
  }

  public void setVideoSurfaceHolder(SurfaceHolder surfaceHolder) {
    this.verifyApplicationThread();
    this.removeSurfaceCallbacks();
    this.surfaceHolder = surfaceHolder;
    if (surfaceHolder == null) {
      this.setVideoSurfaceInternal((Surface)null, false);
      this.maybeNotifySurfaceSizeChanged(0, 0);
    } else {
      surfaceHolder.addCallback(this.componentListener);
      Surface surface = surfaceHolder.getSurface();
      if (surface != null && surface.isValid()) {
        this.setVideoSurfaceInternal(surface, false);
        Rect surfaceSize = surfaceHolder.getSurfaceFrame();
        this.maybeNotifySurfaceSizeChanged(surfaceSize.width(), surfaceSize.height());
      } else {
        this.setVideoSurfaceInternal((Surface)null, false);
        this.maybeNotifySurfaceSizeChanged(0, 0);
      }
    }

  }

  public void clearVideoSurfaceHolder(SurfaceHolder surfaceHolder) {
    this.verifyApplicationThread();
    if (surfaceHolder != null && surfaceHolder == this.surfaceHolder) {
      this.setVideoSurfaceHolder((SurfaceHolder)null);
    }

  }

  public void setVideoSurfaceView(SurfaceView surfaceView) {
    this.setVideoSurfaceHolder(surfaceView == null ? null : surfaceView.getHolder());
  }

  public void clearVideoSurfaceView(SurfaceView surfaceView) {
    this.clearVideoSurfaceHolder(surfaceView == null ? null : surfaceView.getHolder());
  }

  public void setVideoTextureView(TextureView textureView) {
    this.verifyApplicationThread();
    this.removeSurfaceCallbacks();
    this.textureView = textureView;
    if (textureView == null) {
      this.setVideoSurfaceInternal((Surface)null, true);
      this.maybeNotifySurfaceSizeChanged(0, 0);
    } else {
      if (textureView.getSurfaceTextureListener() != null) {
        Log.w("SimpleExoPlayer", "Replacing existing SurfaceTextureListener.");
      }

      textureView.setSurfaceTextureListener(this.componentListener);
      SurfaceTexture surfaceTexture = textureView.isAvailable() ? textureView.getSurfaceTexture() : null;
      if (surfaceTexture == null) {
        this.setVideoSurfaceInternal((Surface)null, true);
        this.maybeNotifySurfaceSizeChanged(0, 0);
      } else {
        this.setVideoSurfaceInternal(new Surface(surfaceTexture), true);
        this.maybeNotifySurfaceSizeChanged(textureView.getWidth(), textureView.getHeight());
      }
    }

  }

  public void clearVideoTextureView(TextureView textureView) {
    this.verifyApplicationThread();
    if (textureView != null && textureView == this.textureView) {
      this.setVideoTextureView((TextureView)null);
    }

  }

  public void addAudioListener(AudioListener listener) {
    this.audioListeners.add(listener);
  }

  public void removeAudioListener(AudioListener listener) {
    this.audioListeners.remove(listener);
  }

  public void setAudioAttributes(AudioAttributes audioAttributes) {
    this.setAudioAttributes(audioAttributes, false);
  }

  public void setAudioAttributes(AudioAttributes audioAttributes, boolean handleAudioFocus) {
    this.verifyApplicationThread();
    if (!Util.areEqual(this.audioAttributes, audioAttributes)) {
      this.audioAttributes = audioAttributes;
      Renderer[] var3 = this.renderers;
      int var4 = var3.length;

      for(int var5 = 0; var5 < var4; ++var5) {
        Renderer renderer = var3[var5];
        if (renderer.getTrackType() == 1) {
          this.player.createMessage(renderer).setType(3).setPayload(audioAttributes).send();
        }
      }

      Iterator var7 = this.audioListeners.iterator();

      while(var7.hasNext()) {
        AudioListener audioListener = (AudioListener)var7.next();
        audioListener.onAudioAttributesChanged(audioAttributes);
      }
    }

    int playerCommand = this.audioFocusManager.setAudioAttributes(handleAudioFocus ? audioAttributes : null, this.getPlayWhenReady(), this.getPlaybackState());
    this.updatePlayWhenReady(this.getPlayWhenReady(), playerCommand);
  }

  public AudioAttributes getAudioAttributes() {
    return this.audioAttributes;
  }

  public int getAudioSessionId() {
    return this.audioSessionId;
  }

  public void setAuxEffectInfo(AuxEffectInfo auxEffectInfo) {
    this.verifyApplicationThread();
    Renderer[] var2 = this.renderers;
    int var3 = var2.length;

    for(int var4 = 0; var4 < var3; ++var4) {
      Renderer renderer = var2[var4];
      if (renderer.getTrackType() == 1) {
        this.player.createMessage(renderer).setType(5).setPayload(auxEffectInfo).send();
      }
    }

  }

  public void clearAuxEffectInfo() {
    this.setAuxEffectInfo(new AuxEffectInfo(0, 0.0F));
  }

  public void setVolume(float audioVolume) {
    this.verifyApplicationThread();
    audioVolume = Util.constrainValue(audioVolume, 0.0F, 1.0F);
    if (this.audioVolume != audioVolume) {
      this.audioVolume = audioVolume;
      this.sendVolumeToRenderers();
      Iterator var2 = this.audioListeners.iterator();

      while(var2.hasNext()) {
        AudioListener audioListener = (AudioListener)var2.next();
        audioListener.onVolumeChanged(audioVolume);
      }

    }
  }

  public float getVolume() {
    return this.audioVolume;
  }

  /** @deprecated */
  @Deprecated
  public void setAudioStreamType(int streamType) {
    int usage = Util.getAudioUsageForStreamType(streamType);
    int contentType = Util.getAudioContentTypeForStreamType(streamType);
    AudioAttributes audioAttributes = (new Builder()).setUsage(usage).setContentType(contentType).build();
    this.setAudioAttributes(audioAttributes);
  }

  /** @deprecated */
  @Deprecated
  public int getAudioStreamType() {
    return Util.getStreamTypeForAudioUsage(this.audioAttributes.usage);
  }

  public AnalyticsCollector getAnalyticsCollector() {
    return this.analyticsCollector;
  }

  public void addAnalyticsListener(AnalyticsListener listener) {
    this.verifyApplicationThread();
    this.analyticsCollector.addListener(listener);
  }

  public void removeAnalyticsListener(AnalyticsListener listener) {
    this.verifyApplicationThread();
    this.analyticsCollector.removeListener(listener);
  }

  /** @deprecated */
  @Deprecated
  @TargetApi(23)
  public void setPlaybackParams(@Nullable PlaybackParams params) {
    PlaybackParameters playbackParameters;
    if (params != null) {
      params.allowDefaults();
      playbackParameters = new PlaybackParameters(params.getSpeed(), params.getPitch());
    } else {
      playbackParameters = null;
    }

    this.setPlaybackParameters(playbackParameters);
  }

  public Format getVideoFormat() {
    return this.videoFormat;
  }

  public Format getAudioFormat() {
    return this.audioFormat;
  }

  public DecoderCounters getVideoDecoderCounters() {
    return this.videoDecoderCounters;
  }

  public DecoderCounters getAudioDecoderCounters() {
    return this.audioDecoderCounters;
  }

  public void addVideoListener(com.google.android.exoplayer2.video.VideoListener listener) {
    this.videoListeners.add(listener);
  }

  public void removeVideoListener(com.google.android.exoplayer2.video.VideoListener listener) {
    this.videoListeners.remove(listener);
  }

  public void setVideoFrameMetadataListener(VideoFrameMetadataListener listener) {
    this.verifyApplicationThread();
    this.videoFrameMetadataListener = listener;
    Renderer[] var2 = this.renderers;
    int var3 = var2.length;

    for(int var4 = 0; var4 < var3; ++var4) {
      Renderer renderer = var2[var4];
      if (renderer.getTrackType() == 2) {
        this.player.createMessage(renderer).setType(6).setPayload(listener).send();
      }
    }

  }

  public void clearVideoFrameMetadataListener(VideoFrameMetadataListener listener) {
    this.verifyApplicationThread();
    if (this.videoFrameMetadataListener == listener) {
      Renderer[] var2 = this.renderers;
      int var3 = var2.length;

      for(int var4 = 0; var4 < var3; ++var4) {
        Renderer renderer = var2[var4];
        if (renderer.getTrackType() == 2) {
          this.player.createMessage(renderer).setType(6).setPayload((Object)null).send();
        }
      }

    }
  }

  public void setCameraMotionListener(CameraMotionListener listener) {
    this.verifyApplicationThread();
    this.cameraMotionListener = listener;
    Renderer[] var2 = this.renderers;
    int var3 = var2.length;

    for(int var4 = 0; var4 < var3; ++var4) {
      Renderer renderer = var2[var4];
      if (renderer.getTrackType() == 5) {
        this.player.createMessage(renderer).setType(7).setPayload(listener).send();
      }
    }

  }

  public void clearCameraMotionListener(CameraMotionListener listener) {
    this.verifyApplicationThread();
    if (this.cameraMotionListener == listener) {
      Renderer[] var2 = this.renderers;
      int var3 = var2.length;

      for(int var4 = 0; var4 < var3; ++var4) {
        Renderer renderer = var2[var4];
        if (renderer.getTrackType() == 5) {
          this.player.createMessage(renderer).setType(7).setPayload((Object)null).send();
        }
      }

    }
  }

  /** @deprecated */
  @Deprecated
  public void setVideoListener(SimpleExoPlayer.VideoListener listener) {
    this.videoListeners.clear();
    if (listener != null) {
      this.addVideoListener(listener);
    }

  }

  /** @deprecated */
  @Deprecated
  public void clearVideoListener(SimpleExoPlayer.VideoListener listener) {
    this.removeVideoListener(listener);
  }

  public void addTextOutput(TextOutput listener) {
    if (!this.currentCues.isEmpty()) {
      listener.onCues(this.currentCues);
    }

    this.textOutputs.add(listener);
  }

  public void removeTextOutput(TextOutput listener) {
    this.textOutputs.remove(listener);
  }

  /** @deprecated */
  @Deprecated
  public void setTextOutput(TextOutput output) {
    this.textOutputs.clear();
    if (output != null) {
      this.addTextOutput(output);
    }

  }

  /** @deprecated */
  @Deprecated
  public void clearTextOutput(TextOutput output) {
    this.removeTextOutput(output);
  }

  public void addMetadataOutput(MetadataOutput listener) {
    this.metadataOutputs.add(listener);
  }

  public void removeMetadataOutput(MetadataOutput listener) {
    this.metadataOutputs.remove(listener);
  }

  /** @deprecated */
  @Deprecated
  public void setMetadataOutput(MetadataOutput output) {
    this.metadataOutputs.retainAll(Collections.singleton(this.analyticsCollector));
    if (output != null) {
      this.addMetadataOutput(output);
    }

  }

  /** @deprecated */
  @Deprecated
  public void clearMetadataOutput(MetadataOutput output) {
    this.removeMetadataOutput(output);
  }

  /** @deprecated */
  @Deprecated
  public void setVideoDebugListener(VideoRendererEventListener listener) {
    this.videoDebugListeners.retainAll(Collections.singleton(this.analyticsCollector));
    if (listener != null) {
      this.addVideoDebugListener(listener);
    }

  }

  /** @deprecated */
  @Deprecated
  public void addVideoDebugListener(VideoRendererEventListener listener) {
    this.videoDebugListeners.add(listener);
  }

  /** @deprecated */
  @Deprecated
  public void removeVideoDebugListener(VideoRendererEventListener listener) {
    this.videoDebugListeners.remove(listener);
  }

  /** @deprecated */
  @Deprecated
  public void setAudioDebugListener(AudioRendererEventListener listener) {
    this.audioDebugListeners.retainAll(Collections.singleton(this.analyticsCollector));
    if (listener != null) {
      this.addAudioDebugListener(listener);
    }

  }

  /** @deprecated */
  @Deprecated
  public void addAudioDebugListener(AudioRendererEventListener listener) {
    this.audioDebugListeners.add(listener);
  }

  /** @deprecated */
  @Deprecated
  public void removeAudioDebugListener(AudioRendererEventListener listener) {
    this.audioDebugListeners.remove(listener);
  }

  public Looper getPlaybackLooper() {
    return this.player.getPlaybackLooper();
  }

  public Looper getApplicationLooper() {
    return this.player.getApplicationLooper();
  }

  public void addListener(EventListener listener) {
    this.verifyApplicationThread();
    this.player.addListener(listener);
  }

  public void removeListener(EventListener listener) {
    this.verifyApplicationThread();
    this.player.removeListener(listener);
  }

  public int getPlaybackState() {
    this.verifyApplicationThread();
    return this.player.getPlaybackState();
  }

  @Nullable
  public ExoPlaybackException getPlaybackError() {
    this.verifyApplicationThread();
    return this.player.getPlaybackError();
  }

  public void retry() {
    this.verifyApplicationThread();
    if (this.mediaSource != null && (this.getPlaybackError() != null || this.getPlaybackState() == 1)) {
      this.prepare(this.mediaSource, false, false);
    }

  }

  public void prepare(MediaSource mediaSource) {
    this.prepare(mediaSource, true, true);
  }

  public void prepare(MediaSource mediaSource, boolean resetPosition, boolean resetState) {
    this.verifyApplicationThread();
    if (this.mediaSource != null) {
      this.mediaSource.removeEventListener(this.analyticsCollector);
      this.analyticsCollector.resetForNewMediaSource();
    }

    this.mediaSource = mediaSource;
    mediaSource.addEventListener(this.eventHandler, this.analyticsCollector);
    int playerCommand = this.audioFocusManager.handlePrepare(this.getPlayWhenReady());
    this.updatePlayWhenReady(this.getPlayWhenReady(), playerCommand);
    this.player.prepare(mediaSource, resetPosition, resetState);
  }

  public void setPlayWhenReady(boolean playWhenReady) {
    this.verifyApplicationThread();
    int playerCommand = this.audioFocusManager.handleSetPlayWhenReady(playWhenReady, this.getPlaybackState());
    this.updatePlayWhenReady(playWhenReady, playerCommand);
  }

  public boolean getPlayWhenReady() {
    this.verifyApplicationThread();
    return this.player.getPlayWhenReady();
  }

  public int getRepeatMode() {
    this.verifyApplicationThread();
    return this.player.getRepeatMode();
  }

  public void setRepeatMode(int repeatMode) {
    this.verifyApplicationThread();
    this.player.setRepeatMode(repeatMode);
  }

  public void setShuffleModeEnabled(boolean shuffleModeEnabled) {
    this.verifyApplicationThread();
    this.player.setShuffleModeEnabled(shuffleModeEnabled);
  }

  public boolean getShuffleModeEnabled() {
    this.verifyApplicationThread();
    return this.player.getShuffleModeEnabled();
  }

  public boolean isLoading() {
    this.verifyApplicationThread();
    return this.player.isLoading();
  }

  public void seekTo(int windowIndex, long positionMs) {
    this.verifyApplicationThread();
    this.analyticsCollector.notifySeekStarted();
    this.player.seekTo(windowIndex, positionMs);
  }

  public void setPlaybackParameters(@Nullable PlaybackParameters playbackParameters) {
    this.verifyApplicationThread();
    this.player.setPlaybackParameters(playbackParameters);
  }

  public PlaybackParameters getPlaybackParameters() {
    this.verifyApplicationThread();
    return this.player.getPlaybackParameters();
  }

  public void setSeekParameters(@Nullable SeekParameters seekParameters) {
    this.verifyApplicationThread();
    this.player.setSeekParameters(seekParameters);
  }

  public SeekParameters getSeekParameters() {
    this.verifyApplicationThread();
    return this.player.getSeekParameters();
  }

  public void stop(boolean reset) {
    this.verifyApplicationThread();
    this.player.stop(reset);
    if (this.mediaSource != null) {
      this.mediaSource.removeEventListener(this.analyticsCollector);
      this.analyticsCollector.resetForNewMediaSource();
      if (reset) {
        this.mediaSource = null;
      }
    }

    this.audioFocusManager.handleStop();
    this.currentCues = Collections.emptyList();
  }

  public void release() {
    this.audioFocusManager.handleStop();
    this.player.release();
    this.removeSurfaceCallbacks();
    if (this.surface != null) {
      if (this.ownsSurface) {
        this.surface.release();
      }

      this.surface = null;
    }

    if (this.mediaSource != null) {
      this.mediaSource.removeEventListener(this.analyticsCollector);
      this.mediaSource = null;
    }

    this.bandwidthMeter.removeEventListener(this.analyticsCollector);
    this.currentCues = Collections.emptyList();
  }

  /** @deprecated */
  @Deprecated
  public void sendMessages(ExoPlayerMessage... messages) {
    this.player.sendMessages(messages);
  }

  public PlayerMessage createMessage(Target target) {
    this.verifyApplicationThread();
    return this.player.createMessage(target);
  }

  /** @deprecated */
  @Deprecated
  public void blockingSendMessages(ExoPlayerMessage... messages) {
    this.player.blockingSendMessages(messages);
  }

  public int getRendererCount() {
    this.verifyApplicationThread();
    return this.player.getRendererCount();
  }

  public int getRendererType(int index) {
    this.verifyApplicationThread();
    return this.player.getRendererType(index);
  }

  public TrackGroupArray getCurrentTrackGroups() {
    this.verifyApplicationThread();
    return this.player.getCurrentTrackGroups();
  }

  public TrackSelectionArray getCurrentTrackSelections() {
    this.verifyApplicationThread();
    return this.player.getCurrentTrackSelections();
  }

  public Timeline getCurrentTimeline() {
    this.verifyApplicationThread();
    return this.player.getCurrentTimeline();
  }

  @Nullable
  public Object getCurrentManifest() {
    this.verifyApplicationThread();
    return this.player.getCurrentManifest();
  }

  public int getCurrentPeriodIndex() {
    this.verifyApplicationThread();
    return this.player.getCurrentPeriodIndex();
  }

  public int getCurrentWindowIndex() {
    this.verifyApplicationThread();
    return this.player.getCurrentWindowIndex();
  }

  public long getDuration() {
    this.verifyApplicationThread();
    return this.player.getDuration();
  }

  public long getCurrentPosition() {
    this.verifyApplicationThread();
    return this.player.getCurrentPosition();
  }

  public long getBufferedPosition() {
    this.verifyApplicationThread();
    return this.player.getBufferedPosition();
  }

  public long getTotalBufferedDuration() {
    this.verifyApplicationThread();
    return this.player.getTotalBufferedDuration();
  }

  public boolean isPlayingAd() {
    this.verifyApplicationThread();
    return this.player.isPlayingAd();
  }

  public int getCurrentAdGroupIndex() {
    this.verifyApplicationThread();
    return this.player.getCurrentAdGroupIndex();
  }

  public int getCurrentAdIndexInAdGroup() {
    this.verifyApplicationThread();
    return this.player.getCurrentAdIndexInAdGroup();
  }

  public long getContentPosition() {
    this.verifyApplicationThread();
    return this.player.getContentPosition();
  }

  public long getContentBufferedPosition() {
    this.verifyApplicationThread();
    return this.player.getContentBufferedPosition();
  }

  private void removeSurfaceCallbacks() {
    if (this.textureView != null) {
      if (this.textureView.getSurfaceTextureListener() != this.componentListener) {
        Log.w("SimpleExoPlayer", "SurfaceTextureListener already unset or replaced.");
      } else {
        this.textureView.setSurfaceTextureListener((SurfaceTextureListener)null);
      }

      this.textureView = null;
    }

    if (this.surfaceHolder != null) {
      this.surfaceHolder.removeCallback(this.componentListener);
      this.surfaceHolder = null;
    }

  }

  private void setVideoSurfaceInternal(@Nullable Surface surface, boolean ownsSurface) {
    List<PlayerMessage> messages = new ArrayList();
    Renderer[] var4 = this.renderers;
    int var5 = var4.length;

    for(int var6 = 0; var6 < var5; ++var6) {
      Renderer renderer = var4[var6];
      if (renderer.getTrackType() == 2) {
        messages.add(this.player.createMessage(renderer).setType(1).setPayload(surface).send());
      }
    }

    if (this.surface != null && this.surface != surface) {
      try {
        Iterator var9 = messages.iterator();

        while(var9.hasNext()) {
          PlayerMessage message = (PlayerMessage)var9.next();
          message.blockUntilDelivered();
        }
      } catch (InterruptedException var8) {
        Thread.currentThread().interrupt();
      }

      if (this.ownsSurface) {
        this.surface.release();
      }
    }

    this.surface = surface;
    this.ownsSurface = ownsSurface;
  }

  private void maybeNotifySurfaceSizeChanged(int width, int height) {
    if (width != this.surfaceWidth || height != this.surfaceHeight) {
      this.surfaceWidth = width;
      this.surfaceHeight = height;
      Iterator var3 = this.videoListeners.iterator();

      while(var3.hasNext()) {
        com.google.android.exoplayer2.video.VideoListener videoListener = (com.google.android.exoplayer2.video.VideoListener)var3.next();
        videoListener.onSurfaceSizeChanged(width, height);
      }
    }

  }

  private void sendVolumeToRenderers() {
    float scaledVolume = this.audioVolume * this.audioFocusManager.getVolumeMultiplier();
    Renderer[] var2 = this.renderers;
    int var3 = var2.length;

    for(int var4 = 0; var4 < var3; ++var4) {
      Renderer renderer = var2[var4];
      if (renderer.getTrackType() == 1) {
        this.player.createMessage(renderer).setType(2).setPayload(scaledVolume).send();
      }
    }

  }

  private void updatePlayWhenReady(boolean playWhenReady, int playerCommand) {
    this.player.setPlayWhenReady(playWhenReady && playerCommand != -1, playerCommand != 1);
  }

  private void verifyApplicationThread() {
    if (Looper.myLooper() != this.getApplicationLooper()) {
    }

  }

  private final class ComponentListener implements VideoRendererEventListener, AudioRendererEventListener, TextOutput, MetadataOutput, Callback, SurfaceTextureListener, PlayerControl {
    private ComponentListener() {
    }

    public void onVideoEnabled(DecoderCounters counters) {
      SimpleExoPlayer.this.videoDecoderCounters = counters;
      Iterator var2 = SimpleExoPlayer.this.videoDebugListeners.iterator();

      while(var2.hasNext()) {
        VideoRendererEventListener videoDebugListener = (VideoRendererEventListener)var2.next();
        videoDebugListener.onVideoEnabled(counters);
      }

    }

    public void onVideoDecoderInitialized(String decoderName, long initializedTimestampMs, long initializationDurationMs) {
      Iterator var6 = SimpleExoPlayer.this.videoDebugListeners.iterator();

      while(var6.hasNext()) {
        VideoRendererEventListener videoDebugListener = (VideoRendererEventListener)var6.next();
        videoDebugListener.onVideoDecoderInitialized(decoderName, initializedTimestampMs, initializationDurationMs);
      }

    }

    public void onVideoInputFormatChanged(Format format) {
      SimpleExoPlayer.this.videoFormat = format;
      Iterator var2 = SimpleExoPlayer.this.videoDebugListeners.iterator();

      while(var2.hasNext()) {
        VideoRendererEventListener videoDebugListener = (VideoRendererEventListener)var2.next();
        videoDebugListener.onVideoInputFormatChanged(format);
      }

    }

    public void onDroppedFrames(int count, long elapsed) {
      Iterator var4 = SimpleExoPlayer.this.videoDebugListeners.iterator();

      while(var4.hasNext()) {
        VideoRendererEventListener videoDebugListener = (VideoRendererEventListener)var4.next();
        videoDebugListener.onDroppedFrames(count, elapsed);
      }

    }

    public void onVideoSizeChanged(int width, int height, int unappliedRotationDegrees, float pixelWidthHeightRatio) {
      Iterator var5 = SimpleExoPlayer.this.videoListeners.iterator();

      while(var5.hasNext()) {
        com.google.android.exoplayer2.video.VideoListener videoListener = (com.google.android.exoplayer2.video.VideoListener)var5.next();
        if (!SimpleExoPlayer.this.videoDebugListeners.contains(videoListener)) {
          videoListener.onVideoSizeChanged(width, height, unappliedRotationDegrees, pixelWidthHeightRatio);
        }
      }

      var5 = SimpleExoPlayer.this.videoDebugListeners.iterator();

      while(var5.hasNext()) {
        VideoRendererEventListener videoDebugListener = (VideoRendererEventListener)var5.next();
        videoDebugListener.onVideoSizeChanged(width, height, unappliedRotationDegrees, pixelWidthHeightRatio);
      }

    }

    public void onRenderedFirstFrame(Surface surface) {
      Iterator var2;
      if (SimpleExoPlayer.this.surface == surface) {
        var2 = SimpleExoPlayer.this.videoListeners.iterator();

        while(var2.hasNext()) {
          com.google.android.exoplayer2.video.VideoListener videoListener = (com.google.android.exoplayer2.video.VideoListener)var2.next();
          videoListener.onRenderedFirstFrame();
        }
      }

      var2 = SimpleExoPlayer.this.videoDebugListeners.iterator();

      while(var2.hasNext()) {
        VideoRendererEventListener videoDebugListener = (VideoRendererEventListener)var2.next();
        videoDebugListener.onRenderedFirstFrame(surface);
      }

    }

    public void onVideoDisabled(DecoderCounters counters) {
      Iterator var2 = SimpleExoPlayer.this.videoDebugListeners.iterator();

      while(var2.hasNext()) {
        VideoRendererEventListener videoDebugListener = (VideoRendererEventListener)var2.next();
        videoDebugListener.onVideoDisabled(counters);
      }

      SimpleExoPlayer.this.videoFormat = null;
      SimpleExoPlayer.this.videoDecoderCounters = null;
    }

    public void onAudioEnabled(DecoderCounters counters) {
      SimpleExoPlayer.this.audioDecoderCounters = counters;
      Iterator var2 = SimpleExoPlayer.this.audioDebugListeners.iterator();

      while(var2.hasNext()) {
        AudioRendererEventListener audioDebugListener = (AudioRendererEventListener)var2.next();
        audioDebugListener.onAudioEnabled(counters);
      }

    }

    public void onAudioSessionId(int sessionId) {
      if (SimpleExoPlayer.this.audioSessionId != sessionId) {
        SimpleExoPlayer.this.audioSessionId = sessionId;
        Iterator var2 = SimpleExoPlayer.this.audioListeners.iterator();

        while(var2.hasNext()) {
          AudioListener audioListener = (AudioListener)var2.next();
          if (!SimpleExoPlayer.this.audioDebugListeners.contains(audioListener)) {
            audioListener.onAudioSessionId(sessionId);
          }
        }

        var2 = SimpleExoPlayer.this.audioDebugListeners.iterator();

        while(var2.hasNext()) {
          AudioRendererEventListener audioDebugListener = (AudioRendererEventListener)var2.next();
          audioDebugListener.onAudioSessionId(sessionId);
        }

      }
    }

    public void onAudioDecoderInitialized(String decoderName, long initializedTimestampMs, long initializationDurationMs) {
      Iterator var6 = SimpleExoPlayer.this.audioDebugListeners.iterator();

      while(var6.hasNext()) {
        AudioRendererEventListener audioDebugListener = (AudioRendererEventListener)var6.next();
        audioDebugListener.onAudioDecoderInitialized(decoderName, initializedTimestampMs, initializationDurationMs);
      }

    }

    public void onAudioInputFormatChanged(Format format) {
      SimpleExoPlayer.this.audioFormat = format;
      Iterator var2 = SimpleExoPlayer.this.audioDebugListeners.iterator();

      while(var2.hasNext()) {
        AudioRendererEventListener audioDebugListener = (AudioRendererEventListener)var2.next();
        audioDebugListener.onAudioInputFormatChanged(format);
      }

    }

    public void onAudioSinkUnderrun(int bufferSize, long bufferSizeMs, long elapsedSinceLastFeedMs) {
      Iterator var6 = SimpleExoPlayer.this.audioDebugListeners.iterator();

      while(var6.hasNext()) {
        AudioRendererEventListener audioDebugListener = (AudioRendererEventListener)var6.next();
        audioDebugListener.onAudioSinkUnderrun(bufferSize, bufferSizeMs, elapsedSinceLastFeedMs);
      }

    }

    public void onAudioDisabled(DecoderCounters counters) {
      Iterator var2 = SimpleExoPlayer.this.audioDebugListeners.iterator();

      while(var2.hasNext()) {
        AudioRendererEventListener audioDebugListener = (AudioRendererEventListener)var2.next();
        audioDebugListener.onAudioDisabled(counters);
      }

      SimpleExoPlayer.this.audioFormat = null;
      SimpleExoPlayer.this.audioDecoderCounters = null;
      SimpleExoPlayer.this.audioSessionId = 0;
    }

    public void onCues(List<Cue> cues) {
      SimpleExoPlayer.this.currentCues = cues;
      Iterator var2 = SimpleExoPlayer.this.textOutputs.iterator();

      while(var2.hasNext()) {
        TextOutput textOutput = (TextOutput)var2.next();
        textOutput.onCues(cues);
      }

    }

    public void onMetadata(Metadata metadata) {
      Iterator var2 = SimpleExoPlayer.this.metadataOutputs.iterator();

      while(var2.hasNext()) {
        MetadataOutput metadataOutput = (MetadataOutput)var2.next();
        metadataOutput.onMetadata(metadata);
      }

    }

    public void surfaceCreated(SurfaceHolder holder) {
      SimpleExoPlayer.this.setVideoSurfaceInternal(holder.getSurface(), false);
    }

    public void surfaceChanged(SurfaceHolder holder, int format, int width, int height) {
      SimpleExoPlayer.this.maybeNotifySurfaceSizeChanged(width, height);
    }

    public void surfaceDestroyed(SurfaceHolder holder) {
      SimpleExoPlayer.this.setVideoSurfaceInternal((Surface)null, false);
      SimpleExoPlayer.this.maybeNotifySurfaceSizeChanged(0, 0);
    }

    public void onSurfaceTextureAvailable(SurfaceTexture surfaceTexture, int width, int height) {
      SimpleExoPlayer.this.setVideoSurfaceInternal(new Surface(surfaceTexture), true);
      SimpleExoPlayer.this.maybeNotifySurfaceSizeChanged(width, height);
    }

    public void onSurfaceTextureSizeChanged(SurfaceTexture surfaceTexture, int width, int height) {
      SimpleExoPlayer.this.maybeNotifySurfaceSizeChanged(width, height);
    }

    public boolean onSurfaceTextureDestroyed(SurfaceTexture surfaceTexture) {
      SimpleExoPlayer.this.setVideoSurfaceInternal((Surface)null, true);
      SimpleExoPlayer.this.maybeNotifySurfaceSizeChanged(0, 0);
      return true;
    }

    public void onSurfaceTextureUpdated(SurfaceTexture surfaceTexture) {
    }

    public void setVolumeMultiplier(float volumeMultiplier) {
      SimpleExoPlayer.this.sendVolumeToRenderers();
    }

    public void executePlayerCommand(int playerCommand) {
      SimpleExoPlayer.this.updatePlayWhenReady(SimpleExoPlayer.this.getPlayWhenReady(), playerCommand);
    }
  }

  /** @deprecated */
  @Deprecated
  public interface VideoListener extends com.google.android.exoplayer2.video.VideoListener {
  }
}
