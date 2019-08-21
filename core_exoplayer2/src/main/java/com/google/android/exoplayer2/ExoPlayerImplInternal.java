package com.google.android.exoplayer2;

import android.os.Handler;
import android.os.HandlerThread;
import android.os.Looper;
import android.os.Message;
import android.os.SystemClock;
import android.os.Handler.Callback;
import android.util.Pair;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import com.google.android.exoplayer2.DefaultMediaClock.PlaybackParameterListener;
import com.google.android.exoplayer2.PlayerMessage.Sender;
import com.google.android.exoplayer2.Timeline.Period;
import com.google.android.exoplayer2.Timeline.Window;
import com.google.android.exoplayer2.source.MediaPeriod;
import com.google.android.exoplayer2.source.MediaSource;
import com.google.android.exoplayer2.source.SampleStream;
import com.google.android.exoplayer2.source.TrackGroupArray;
import com.google.android.exoplayer2.source.MediaSource.MediaPeriodId;
import com.google.android.exoplayer2.source.MediaSource.SourceInfoRefreshListener;
import com.google.android.exoplayer2.trackselection.TrackSelection;
import com.google.android.exoplayer2.trackselection.TrackSelector;
import com.google.android.exoplayer2.trackselection.TrackSelectorResult;
import com.google.android.exoplayer2.trackselection.TrackSelector.InvalidationListener;
import com.google.android.exoplayer2.upstream.BandwidthMeter;
import com.google.android.exoplayer2.util.Assertions;
import com.google.android.exoplayer2.util.Clock;
import com.google.android.exoplayer2.util.HandlerWrapper;
import com.google.android.exoplayer2.util.Log;
import com.google.android.exoplayer2.util.TraceUtil;
import com.google.android.exoplayer2.util.Util;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;

final class ExoPlayerImplInternal implements Callback, com.google.android.exoplayer2.source.MediaPeriod.Callback, InvalidationListener, SourceInfoRefreshListener, PlaybackParameterListener, Sender {
  private static final String TAG = "ExoPlayerImplInternal";
  public static final int MSG_PLAYBACK_INFO_CHANGED = 0;
  public static final int MSG_PLAYBACK_PARAMETERS_CHANGED = 1;
  public static final int MSG_ERROR = 2;
  private static final int MSG_PREPARE = 0;
  private static final int MSG_SET_PLAY_WHEN_READY = 1;
  private static final int MSG_DO_SOME_WORK = 2;
  private static final int MSG_SEEK_TO = 3;
  private static final int MSG_SET_PLAYBACK_PARAMETERS = 4;
  private static final int MSG_SET_SEEK_PARAMETERS = 5;
  private static final int MSG_STOP = 6;
  private static final int MSG_RELEASE = 7;
  private static final int MSG_REFRESH_SOURCE_INFO = 8;
  private static final int MSG_PERIOD_PREPARED = 9;
  private static final int MSG_SOURCE_CONTINUE_LOADING_REQUESTED = 10;
  private static final int MSG_TRACK_SELECTION_INVALIDATED = 11;
  private static final int MSG_SET_REPEAT_MODE = 12;
  private static final int MSG_SET_SHUFFLE_ENABLED = 13;
  private static final int MSG_SEND_MESSAGE = 14;
  private static final int MSG_SEND_MESSAGE_TO_TARGET_THREAD = 15;
  private static final int MSG_PLAYBACK_PARAMETERS_CHANGED_INTERNAL = 16;
  private static final int PREPARING_SOURCE_INTERVAL_MS = 10;
  private static final int RENDERING_INTERVAL_MS = 10;
  private static final int IDLE_INTERVAL_MS = 1000;
  private final Renderer[] renderers;
  private final RendererCapabilities[] rendererCapabilities;
  private final TrackSelector trackSelector;
  private final TrackSelectorResult emptyTrackSelectorResult;
  private final LoadControl loadControl;
  private final BandwidthMeter bandwidthMeter;
  private final HandlerWrapper handler;
  private final HandlerThread internalPlaybackThread;
  private final Handler eventHandler;
  private final ExoPlayer player;
  private final Window window;
  private final Period period;
  private final long backBufferDurationUs;
  private final boolean retainBackBufferFromKeyframe;
  private final DefaultMediaClock mediaClock;
  private final ExoPlayerImplInternal.PlaybackInfoUpdate playbackInfoUpdate;
  private final ArrayList<ExoPlayerImplInternal.PendingMessageInfo> pendingMessages;
  private final Clock clock;
  private final MediaPeriodQueue queue;
  private SeekParameters seekParameters;
  private PlaybackInfo playbackInfo;
  private MediaSource mediaSource;
  private Renderer[] enabledRenderers;
  private boolean released;
  private boolean playWhenReady;
  private boolean rebuffering;
  private int repeatMode;
  private boolean shuffleModeEnabled;
  private int pendingPrepareCount;
  private ExoPlayerImplInternal.SeekPosition pendingInitialSeekPosition;
  private long rendererPositionUs;
  private int nextPendingMessageIndex;

  public ExoPlayerImplInternal(Renderer[] renderers, TrackSelector trackSelector, TrackSelectorResult emptyTrackSelectorResult, LoadControl loadControl, BandwidthMeter bandwidthMeter, boolean playWhenReady, int repeatMode, boolean shuffleModeEnabled, Handler eventHandler, ExoPlayer player, Clock clock) {
    this.renderers = renderers;
    this.trackSelector = trackSelector;
    this.emptyTrackSelectorResult = emptyTrackSelectorResult;
    this.loadControl = loadControl;
    this.bandwidthMeter = bandwidthMeter;
    this.playWhenReady = playWhenReady;
    this.repeatMode = repeatMode;
    this.shuffleModeEnabled = shuffleModeEnabled;
    this.eventHandler = eventHandler;
    this.player = player;
    this.clock = clock;
    this.queue = new MediaPeriodQueue();
    this.backBufferDurationUs = loadControl.getBackBufferDurationUs();
    this.retainBackBufferFromKeyframe = loadControl.retainBackBufferFromKeyframe();
    this.seekParameters = SeekParameters.DEFAULT;
    this.playbackInfo = PlaybackInfo.createDummy(-9223372036854775807L, emptyTrackSelectorResult);
    this.playbackInfoUpdate = new ExoPlayerImplInternal.PlaybackInfoUpdate();
    this.rendererCapabilities = new RendererCapabilities[renderers.length];

    for(int i = 0; i < renderers.length; ++i) {
      renderers[i].setIndex(i);
      this.rendererCapabilities[i] = renderers[i].getCapabilities();
    }

    this.mediaClock = new DefaultMediaClock(this, clock);
    this.pendingMessages = new ArrayList();
    this.enabledRenderers = new Renderer[0];
    this.window = new Window();
    this.period = new Period();
    trackSelector.init(this, bandwidthMeter);
    this.internalPlaybackThread = new HandlerThread("ExoPlayerImplInternal:Handler", -16);
    this.internalPlaybackThread.start();
    this.handler = clock.createHandler(this.internalPlaybackThread.getLooper(), this);
  }

  public void prepare(MediaSource mediaSource, boolean resetPosition, boolean resetState) {
    this.handler.obtainMessage(0, resetPosition ? 1 : 0, resetState ? 1 : 0, mediaSource).sendToTarget();
  }

  public void setPlayWhenReady(boolean playWhenReady) {
    this.handler.obtainMessage(1, playWhenReady ? 1 : 0, 0).sendToTarget();
  }

  public void setRepeatMode(int repeatMode) {
    this.handler.obtainMessage(12, repeatMode, 0).sendToTarget();
  }

  public void setShuffleModeEnabled(boolean shuffleModeEnabled) {
    this.handler.obtainMessage(13, shuffleModeEnabled ? 1 : 0, 0).sendToTarget();
  }

  public void seekTo(Timeline timeline, int windowIndex, long positionUs) {
    this.handler.obtainMessage(3, new ExoPlayerImplInternal.SeekPosition(timeline, windowIndex, positionUs)).sendToTarget();
  }

  public void setPlaybackParameters(PlaybackParameters playbackParameters) {
    this.handler.obtainMessage(4, playbackParameters).sendToTarget();
  }

  public void setSeekParameters(SeekParameters seekParameters) {
    this.handler.obtainMessage(5, seekParameters).sendToTarget();
  }

  public void stop(boolean reset) {
    this.handler.obtainMessage(6, reset ? 1 : 0, 0).sendToTarget();
  }

  public synchronized void sendMessage(PlayerMessage message) {
    if (this.released) {
      Log.w("ExoPlayerImplInternal", "Ignoring messages sent after release.");
      message.markAsProcessed(false);
    } else {
      this.handler.obtainMessage(14, message).sendToTarget();
    }
  }

  public synchronized void release() {
    if (!this.released) {
      this.handler.sendEmptyMessage(7);
      boolean wasInterrupted = false;

      while(!this.released) {
        try {
          this.wait();
        } catch (InterruptedException var3) {
          wasInterrupted = true;
        }
      }

      if (wasInterrupted) {
        Thread.currentThread().interrupt();
      }

    }
  }

  public Looper getPlaybackLooper() {
    return this.internalPlaybackThread.getLooper();
  }

  public void onSourceInfoRefreshed(MediaSource source, Timeline timeline, Object manifest) {
    this.handler.obtainMessage(8, new ExoPlayerImplInternal.MediaSourceRefreshInfo(source, timeline, manifest)).sendToTarget();
  }

  public void onPrepared(MediaPeriod source) {
    this.handler.obtainMessage(9, source).sendToTarget();
  }

  public void onContinueLoadingRequested(MediaPeriod source) {
    this.handler.obtainMessage(10, source).sendToTarget();
  }

  public void onTrackSelectionsInvalidated() {
    this.handler.sendEmptyMessage(11);
  }

  public void onPlaybackParametersChanged(PlaybackParameters playbackParameters) {
    this.handler.obtainMessage(16, playbackParameters).sendToTarget();
  }

  public boolean handleMessage(Message msg) {
    try {
      switch(msg.what) {
        case 0:
          this.prepareInternal((MediaSource)msg.obj, msg.arg1 != 0, msg.arg2 != 0);
          break;
        case 1:
          this.setPlayWhenReadyInternal(msg.arg1 != 0);
          break;
        case 2:
          this.doSomeWork();
          break;
        case 3:
          this.seekToInternal((ExoPlayerImplInternal.SeekPosition)msg.obj);
          break;
        case 4:
          this.setPlaybackParametersInternal((PlaybackParameters)msg.obj);
          break;
        case 5:
          this.setSeekParametersInternal((SeekParameters)msg.obj);
          break;
        case 6:
          this.stopInternal(msg.arg1 != 0, true);
          break;
        case 7:
          this.releaseInternal();
          return true;
        case 8:
          this.handleSourceInfoRefreshed((ExoPlayerImplInternal.MediaSourceRefreshInfo)msg.obj);
          break;
        case 9:
          this.handlePeriodPrepared((MediaPeriod)msg.obj);
          break;
        case 10:
          this.handleContinueLoadingRequested((MediaPeriod)msg.obj);
          break;
        case 11:
          this.reselectTracksInternal();
          break;
        case 12:
          this.setRepeatModeInternal(msg.arg1);
          break;
        case 13:
          this.setShuffleModeEnabledInternal(msg.arg1 != 0);
          break;
        case 14:
          this.sendMessageInternal((PlayerMessage)msg.obj);
          break;
        case 15:
          this.sendMessageToTargetThread((PlayerMessage)msg.obj);
          break;
        case 16:
          this.handlePlaybackParameters((PlaybackParameters)msg.obj);
          break;
        default:
          return false;
      }

      this.maybeNotifyPlaybackInfoChanged();
    } catch (ExoPlaybackException var3) {
      Log.e("ExoPlayerImplInternal", "Playback error.", var3);
      this.stopInternal(false, false);
      this.eventHandler.obtainMessage(2, var3).sendToTarget();
      this.maybeNotifyPlaybackInfoChanged();
    } catch (IOException var4) {
      Log.e("ExoPlayerImplInternal", "Source error.", var4);
      this.stopInternal(false, false);
      this.eventHandler.obtainMessage(2, ExoPlaybackException.createForSource(var4)).sendToTarget();
      this.maybeNotifyPlaybackInfoChanged();
    } catch (RuntimeException var5) {
      Log.e("ExoPlayerImplInternal", "Internal runtime error.", var5);
      this.stopInternal(false, false);
      this.eventHandler.obtainMessage(2, ExoPlaybackException.createForUnexpected(var5)).sendToTarget();
      this.maybeNotifyPlaybackInfoChanged();
    }

    return true;
  }

  private void setState(int state) {
    if (this.playbackInfo.playbackState != state) {
      this.playbackInfo = this.playbackInfo.copyWithPlaybackState(state);
    }

  }

  private void setIsLoading(boolean isLoading) {
    if (this.playbackInfo.isLoading != isLoading) {
      this.playbackInfo = this.playbackInfo.copyWithIsLoading(isLoading);
    }

  }

  private void maybeNotifyPlaybackInfoChanged() {
    if (this.playbackInfoUpdate.hasPendingUpdate(this.playbackInfo)) {
      this.eventHandler.obtainMessage(0, this.playbackInfoUpdate.operationAcks, this.playbackInfoUpdate.positionDiscontinuity ? this.playbackInfoUpdate.discontinuityReason : -1, this.playbackInfo).sendToTarget();
      this.playbackInfoUpdate.reset(this.playbackInfo);
    }

  }

  private void prepareInternal(MediaSource mediaSource, boolean resetPosition, boolean resetState) {
    ++this.pendingPrepareCount;
    this.resetInternal(true, resetPosition, resetState);
    this.loadControl.onPrepared();
    this.mediaSource = mediaSource;
    this.setState(2);
    mediaSource.prepareSource(this.player, true, this, this.bandwidthMeter.getTransferListener());
    this.handler.sendEmptyMessage(2);
  }

  private void setPlayWhenReadyInternal(boolean playWhenReady) throws ExoPlaybackException {
    this.rebuffering = false;
    this.playWhenReady = playWhenReady;
    if (!playWhenReady) {
      this.stopRenderers();
      this.updatePlaybackPositions();
    } else if (this.playbackInfo.playbackState == 3) {
      this.startRenderers();
      this.handler.sendEmptyMessage(2);
    } else if (this.playbackInfo.playbackState == 2) {
      this.handler.sendEmptyMessage(2);
    }

  }

  private void setRepeatModeInternal(int repeatMode) throws ExoPlaybackException {
    this.repeatMode = repeatMode;
    if (!this.queue.updateRepeatMode(repeatMode)) {
      this.seekToCurrentPosition(true);
    }

    this.handleLoadingMediaPeriodChanged(false);
  }

  private void setShuffleModeEnabledInternal(boolean shuffleModeEnabled) throws ExoPlaybackException {
    this.shuffleModeEnabled = shuffleModeEnabled;
    if (!this.queue.updateShuffleModeEnabled(shuffleModeEnabled)) {
      this.seekToCurrentPosition(true);
    }

    this.handleLoadingMediaPeriodChanged(false);
  }

  private void seekToCurrentPosition(boolean sendDiscontinuity) throws ExoPlaybackException {
    MediaPeriodId periodId = this.queue.getPlayingPeriod().info.id;
    long newPositionUs = this.seekToPeriodPosition(periodId, this.playbackInfo.positionUs, true);
    if (newPositionUs != this.playbackInfo.positionUs) {
      this.playbackInfo = this.playbackInfo.copyWithNewPosition(periodId, newPositionUs, this.playbackInfo.contentPositionUs, this.getTotalBufferedDurationUs());
      if (sendDiscontinuity) {
        this.playbackInfoUpdate.setPositionDiscontinuity(4);
      }
    }

  }

  private void startRenderers() throws ExoPlaybackException {
    this.rebuffering = false;
    this.mediaClock.start();
    Renderer[] var1 = this.enabledRenderers;
    int var2 = var1.length;

    for(int var3 = 0; var3 < var2; ++var3) {
      Renderer renderer = var1[var3];
      renderer.start();
    }

  }

  private void stopRenderers() throws ExoPlaybackException {
    this.mediaClock.stop();
    Renderer[] var1 = this.enabledRenderers;
    int var2 = var1.length;

    for(int var3 = 0; var3 < var2; ++var3) {
      Renderer renderer = var1[var3];
      this.ensureStopped(renderer);
    }

  }

  private void updatePlaybackPositions() throws ExoPlaybackException {
    if (this.queue.hasPlayingPeriod()) {
      MediaPeriodHolder playingPeriodHolder = this.queue.getPlayingPeriod();
      long periodPositionUs = playingPeriodHolder.mediaPeriod.readDiscontinuity();
      if (periodPositionUs != -9223372036854775807L) {
        this.resetRendererPosition(periodPositionUs);
        if (periodPositionUs != this.playbackInfo.positionUs) {
          this.playbackInfo = this.playbackInfo.copyWithNewPosition(this.playbackInfo.periodId, periodPositionUs, this.playbackInfo.contentPositionUs, this.getTotalBufferedDurationUs());
          this.playbackInfoUpdate.setPositionDiscontinuity(4);
        }
      } else {
        this.rendererPositionUs = this.mediaClock.syncAndGetPositionUs();
        periodPositionUs = playingPeriodHolder.toPeriodTime(this.rendererPositionUs);
        this.maybeTriggerPendingMessages(this.playbackInfo.positionUs, periodPositionUs);
        this.playbackInfo.positionUs = periodPositionUs;
      }

      MediaPeriodHolder loadingPeriod = this.queue.getLoadingPeriod();
      this.playbackInfo.bufferedPositionUs = loadingPeriod.getBufferedPositionUs();
      this.playbackInfo.totalBufferedDurationUs = this.getTotalBufferedDurationUs();
    }
  }

  private void doSomeWork() throws ExoPlaybackException, IOException {
    long operationStartTimeMs = this.clock.uptimeMillis();
    this.updatePeriods();
    if (!this.queue.hasPlayingPeriod()) {
      this.maybeThrowPeriodPrepareError();
      this.scheduleNextWork(operationStartTimeMs, 10L);
    } else {
      MediaPeriodHolder playingPeriodHolder = this.queue.getPlayingPeriod();
      TraceUtil.beginSection("doSomeWork");
      this.updatePlaybackPositions();
      long rendererPositionElapsedRealtimeUs = SystemClock.elapsedRealtime() * 1000L;
      playingPeriodHolder.mediaPeriod.discardBuffer(this.playbackInfo.positionUs - this.backBufferDurationUs, this.retainBackBufferFromKeyframe);
      boolean renderersEnded = true;
      boolean renderersReadyOrEnded = true;
      Renderer[] var8 = this.enabledRenderers;
      int var9 = var8.length;

      for(int var10 = 0; var10 < var9; ++var10) {
        Renderer renderer = var8[var10];
        renderer.render(this.rendererPositionUs, rendererPositionElapsedRealtimeUs);
        renderersEnded = renderersEnded && renderer.isEnded();
        boolean rendererReadyOrEnded = renderer.isReady() || renderer.isEnded() || this.rendererWaitingForNextStream(renderer);
        if (!rendererReadyOrEnded) {
          renderer.maybeThrowStreamError();
        }

        renderersReadyOrEnded = renderersReadyOrEnded && rendererReadyOrEnded;
      }

      if (!renderersReadyOrEnded) {
        this.maybeThrowPeriodPrepareError();
      }

      long playingPeriodDurationUs = playingPeriodHolder.info.durationUs;
      if (renderersEnded && (playingPeriodDurationUs == -9223372036854775807L || playingPeriodDurationUs <= this.playbackInfo.positionUs) && playingPeriodHolder.info.isFinal) {
        this.setState(4);
        this.stopRenderers();
      } else if (this.playbackInfo.playbackState == 2 && this.shouldTransitionToReadyState(renderersReadyOrEnded)) {
        this.setState(3);
        if (this.playWhenReady) {
          this.startRenderers();
        }
      } else if (this.playbackInfo.playbackState == 3) {
        label82: {
          if (this.enabledRenderers.length == 0) {
            if (this.isTimelineReady()) {
              break label82;
            }
          } else if (renderersReadyOrEnded) {
            break label82;
          }

          this.rebuffering = this.playWhenReady;
          this.setState(2);
          this.stopRenderers();
        }
      }

      if (this.playbackInfo.playbackState == 2) {
        Renderer[] var15 = this.enabledRenderers;
        int var16 = var15.length;

        for(int var17 = 0; var17 < var16; ++var17) {
          Renderer renderer = var15[var17];
          renderer.maybeThrowStreamError();
        }
      }

      if ((!this.playWhenReady || this.playbackInfo.playbackState != 3) && this.playbackInfo.playbackState != 2) {
        if (this.enabledRenderers.length != 0 && this.playbackInfo.playbackState != 4) {
          this.scheduleNextWork(operationStartTimeMs, 1000L);
        } else {
          this.handler.removeMessages(2);
        }
      } else {
        this.scheduleNextWork(operationStartTimeMs, 10L);
      }

      TraceUtil.endSection();
    }
  }

  private void scheduleNextWork(long thisOperationStartTimeMs, long intervalMs) {
    this.handler.removeMessages(2);
    this.handler.sendEmptyMessageAtTime(2, thisOperationStartTimeMs + intervalMs);
  }

  private void seekToInternal(ExoPlayerImplInternal.SeekPosition seekPosition) throws ExoPlaybackException {
    this.playbackInfoUpdate.incrementPendingOperationAcks(1);
    Pair<Object, Long> resolvedSeekPosition = this.resolveSeekPosition(seekPosition, true);
    MediaPeriodId periodId;
    long periodPositionUs;
    long contentPositionUs;
    boolean seekPositionAdjusted;
    if (resolvedSeekPosition == null) {
      periodId = this.playbackInfo.getDummyFirstMediaPeriodId(this.shuffleModeEnabled, this.window);
      periodPositionUs = -9223372036854775807L;
      contentPositionUs = -9223372036854775807L;
      seekPositionAdjusted = true;
    } else {
      Object periodUid = resolvedSeekPosition.first;
      contentPositionUs = (Long)resolvedSeekPosition.second;
      periodId = this.queue.resolveMediaPeriodIdForAds(periodUid, contentPositionUs);
      if (periodId.isAd()) {
        periodPositionUs = 0L;
        seekPositionAdjusted = true;
      } else {
        periodPositionUs = (Long)resolvedSeekPosition.second;
        seekPositionAdjusted = seekPosition.windowPositionUs == -9223372036854775807L;
      }
    }

    try {
      if (this.mediaSource == null || this.pendingPrepareCount > 0) {
        this.pendingInitialSeekPosition = seekPosition;
      } else if (periodPositionUs == -9223372036854775807L) {
        this.setState(4);
        this.resetInternal(false, true, false);
      } else {
        long newPeriodPositionUs = periodPositionUs;
        if (periodId.equals(this.playbackInfo.periodId)) {
          MediaPeriodHolder playingPeriodHolder = this.queue.getPlayingPeriod();
          if (playingPeriodHolder != null && periodPositionUs != 0L) {
            newPeriodPositionUs = playingPeriodHolder.mediaPeriod.getAdjustedSeekPositionUs(periodPositionUs, this.seekParameters);
          }

          if (C.usToMs(newPeriodPositionUs) == C.usToMs(this.playbackInfo.positionUs)) {
            periodPositionUs = this.playbackInfo.positionUs;
            return;
          }
        }

        newPeriodPositionUs = this.seekToPeriodPosition(periodId, newPeriodPositionUs);
        seekPositionAdjusted |= periodPositionUs != newPeriodPositionUs;
        periodPositionUs = newPeriodPositionUs;
      }
    } finally {
      this.playbackInfo = this.playbackInfo.copyWithNewPosition(periodId, periodPositionUs, contentPositionUs, this.getTotalBufferedDurationUs());
      if (seekPositionAdjusted) {
        this.playbackInfoUpdate.setPositionDiscontinuity(2);
      }

    }
  }

  private long seekToPeriodPosition(MediaPeriodId periodId, long periodPositionUs) throws ExoPlaybackException {
    return this.seekToPeriodPosition(periodId, periodPositionUs, this.queue.getPlayingPeriod() != this.queue.getReadingPeriod());
  }

  private long seekToPeriodPosition(MediaPeriodId periodId, long periodPositionUs, boolean forceDisableRenderers) throws ExoPlaybackException {
    this.stopRenderers();
    this.rebuffering = false;
    this.setState(2);
    MediaPeriodHolder oldPlayingPeriodHolder = this.queue.getPlayingPeriod();

    MediaPeriodHolder newPlayingPeriodHolder;
    for(newPlayingPeriodHolder = oldPlayingPeriodHolder; newPlayingPeriodHolder != null; newPlayingPeriodHolder = this.queue.advancePlayingPeriod()) {
      if (periodId.equals(newPlayingPeriodHolder.info.id) && newPlayingPeriodHolder.prepared) {
        this.queue.removeAfter(newPlayingPeriodHolder);
        break;
      }
    }

    if (oldPlayingPeriodHolder != newPlayingPeriodHolder || forceDisableRenderers) {
      Renderer[] var7 = this.enabledRenderers;
      int var8 = var7.length;

      for(int var9 = 0; var9 < var8; ++var9) {
        Renderer renderer = var7[var9];
        this.disableRenderer(renderer);
      }

      this.enabledRenderers = new Renderer[0];
      oldPlayingPeriodHolder = null;
    }

    if (newPlayingPeriodHolder != null) {
      this.updatePlayingPeriodRenderers(oldPlayingPeriodHolder);
      if (newPlayingPeriodHolder.hasEnabledTracks) {
        periodPositionUs = newPlayingPeriodHolder.mediaPeriod.seekToUs(periodPositionUs);
        newPlayingPeriodHolder.mediaPeriod.discardBuffer(periodPositionUs - this.backBufferDurationUs, this.retainBackBufferFromKeyframe);
      }

      this.resetRendererPosition(periodPositionUs);
      this.maybeContinueLoading();
    } else {
      this.queue.clear(true);
      this.playbackInfo = this.playbackInfo.copyWithTrackInfo(TrackGroupArray.EMPTY, this.emptyTrackSelectorResult);
      this.resetRendererPosition(periodPositionUs);
    }

    this.handleLoadingMediaPeriodChanged(false);
    this.handler.sendEmptyMessage(2);
    return periodPositionUs;
  }

  private void resetRendererPosition(long periodPositionUs) throws ExoPlaybackException {
    this.rendererPositionUs = !this.queue.hasPlayingPeriod() ? periodPositionUs : this.queue.getPlayingPeriod().toRendererTime(periodPositionUs);
    this.mediaClock.resetPosition(this.rendererPositionUs);
    Renderer[] var3 = this.enabledRenderers;
    int var4 = var3.length;

    for(int var5 = 0; var5 < var4; ++var5) {
      Renderer renderer = var3[var5];
      renderer.resetPosition(this.rendererPositionUs);
    }

  }

  private void setPlaybackParametersInternal(PlaybackParameters playbackParameters) {
    this.mediaClock.setPlaybackParameters(playbackParameters);
  }

  private void setSeekParametersInternal(SeekParameters seekParameters) {
    this.seekParameters = seekParameters;
  }

  private void stopInternal(boolean reset, boolean acknowledgeStop) {
    this.resetInternal(true, reset, reset);
    this.playbackInfoUpdate.incrementPendingOperationAcks(this.pendingPrepareCount + (acknowledgeStop ? 1 : 0));
    this.pendingPrepareCount = 0;
    this.loadControl.onStopped();
    this.setState(1);
  }

  private void releaseInternal() {
    this.resetInternal(true, true, true);
    this.loadControl.onReleased();
    this.setState(1);
    this.internalPlaybackThread.quit();
    synchronized(this) {
      this.released = true;
      this.notifyAll();
    }
  }

  private void resetInternal(boolean releaseMediaSource, boolean resetPosition, boolean resetState) {
    this.handler.removeMessages(2);
    this.rebuffering = false;
    this.mediaClock.stop();
    this.rendererPositionUs = 0L;
    Renderer[] var4 = this.enabledRenderers;
    int var5 = var4.length;

    for(int var6 = 0; var6 < var5; ++var6) {
      Renderer renderer = var4[var6];

      try {
        this.disableRenderer(renderer);
      } catch (RuntimeException | ExoPlaybackException var9) {
        Log.e("ExoPlayerImplInternal", "Stop failed.", var9);
      }
    }

    this.enabledRenderers = new Renderer[0];
    this.queue.clear(!resetPosition);
    this.setIsLoading(false);
    if (resetPosition) {
      this.pendingInitialSeekPosition = null;
    }

    if (resetState) {
      this.queue.setTimeline(Timeline.EMPTY);
      Iterator var10 = this.pendingMessages.iterator();

      while(var10.hasNext()) {
        ExoPlayerImplInternal.PendingMessageInfo pendingMessageInfo = (ExoPlayerImplInternal.PendingMessageInfo)var10.next();
        pendingMessageInfo.message.markAsProcessed(false);
      }

      this.pendingMessages.clear();
      this.nextPendingMessageIndex = 0;
    }

    MediaPeriodId mediaPeriodId = resetPosition ? this.playbackInfo.getDummyFirstMediaPeriodId(this.shuffleModeEnabled, this.window) : this.playbackInfo.periodId;
    long startPositionUs = resetPosition ? -9223372036854775807L : this.playbackInfo.positionUs;
    long contentPositionUs = resetPosition ? -9223372036854775807L : this.playbackInfo.contentPositionUs;
    this.playbackInfo = new PlaybackInfo(resetState ? Timeline.EMPTY : this.playbackInfo.timeline, resetState ? null : this.playbackInfo.manifest, mediaPeriodId, startPositionUs, contentPositionUs, this.playbackInfo.playbackState, false, resetState ? TrackGroupArray.EMPTY : this.playbackInfo.trackGroups, resetState ? this.emptyTrackSelectorResult : this.playbackInfo.trackSelectorResult, mediaPeriodId, startPositionUs, 0L, startPositionUs);
    if (releaseMediaSource && this.mediaSource != null) {
      this.mediaSource.releaseSource(this);
      this.mediaSource = null;
    }

  }

  private void sendMessageInternal(PlayerMessage message) throws ExoPlaybackException {
    if (message.getPositionMs() == -9223372036854775807L) {
      this.sendMessageToTarget(message);
    } else if (this.mediaSource != null && this.pendingPrepareCount <= 0) {
      ExoPlayerImplInternal.PendingMessageInfo pendingMessageInfo = new ExoPlayerImplInternal.PendingMessageInfo(message);
      if (this.resolvePendingMessagePosition(pendingMessageInfo)) {
        this.pendingMessages.add(pendingMessageInfo);
        Collections.sort(this.pendingMessages);
      } else {
        message.markAsProcessed(false);
      }
    } else {
      this.pendingMessages.add(new ExoPlayerImplInternal.PendingMessageInfo(message));
    }

  }

  private void sendMessageToTarget(PlayerMessage message) throws ExoPlaybackException {
    if (message.getHandler().getLooper() == this.handler.getLooper()) {
      this.deliverMessage(message);
      if (this.playbackInfo.playbackState == 3 || this.playbackInfo.playbackState == 2) {
        this.handler.sendEmptyMessage(2);
      }
    } else {
      this.handler.obtainMessage(15, message).sendToTarget();
    }

  }

  private void sendMessageToTargetThread(PlayerMessage message) {
    Handler handler = message.getHandler();
    handler.post(() -> {
      try {
        this.deliverMessage(message);
      } catch (ExoPlaybackException var3) {
        Log.e("ExoPlayerImplInternal", "Unexpected error delivering message on external thread.", var3);
        throw new RuntimeException(var3);
      }
    });
  }

  private void deliverMessage(PlayerMessage message) throws ExoPlaybackException {
    if (!message.isCanceled()) {
      try {
        message.getTarget().handleMessage(message.getType(), message.getPayload());
      } finally {
        message.markAsProcessed(true);
      }

    }
  }

  private void resolvePendingMessagePositions() {
    for(int i = this.pendingMessages.size() - 1; i >= 0; --i) {
      if (!this.resolvePendingMessagePosition((ExoPlayerImplInternal.PendingMessageInfo)this.pendingMessages.get(i))) {
        ((ExoPlayerImplInternal.PendingMessageInfo)this.pendingMessages.get(i)).message.markAsProcessed(false);
        this.pendingMessages.remove(i);
      }
    }

    Collections.sort(this.pendingMessages);
  }

  private boolean resolvePendingMessagePosition(ExoPlayerImplInternal.PendingMessageInfo pendingMessageInfo) {
    if (pendingMessageInfo.resolvedPeriodUid == null) {
      Pair<Object, Long> periodPosition = this.resolveSeekPosition(new ExoPlayerImplInternal.SeekPosition(pendingMessageInfo.message.getTimeline(), pendingMessageInfo.message.getWindowIndex(), C.msToUs(pendingMessageInfo.message.getPositionMs())), false);
      if (periodPosition == null) {
        return false;
      }

      pendingMessageInfo.setResolvedPosition(this.playbackInfo.timeline.getIndexOfPeriod(periodPosition.first), (Long)periodPosition.second, periodPosition.first);
    } else {
      int index = this.playbackInfo.timeline.getIndexOfPeriod(pendingMessageInfo.resolvedPeriodUid);
      if (index == -1) {
        return false;
      }

      pendingMessageInfo.resolvedPeriodIndex = index;
    }

    return true;
  }

  private void maybeTriggerPendingMessages(long oldPeriodPositionUs, long newPeriodPositionUs) throws ExoPlaybackException {
    if (!this.pendingMessages.isEmpty() && !this.playbackInfo.periodId.isAd()) {
      if (this.playbackInfo.startPositionUs == oldPeriodPositionUs) {
        --oldPeriodPositionUs;
      }

      int currentPeriodIndex = this.playbackInfo.timeline.getIndexOfPeriod(this.playbackInfo.periodId.periodUid);

      for(ExoPlayerImplInternal.PendingMessageInfo previousInfo = this.nextPendingMessageIndex > 0 ? (ExoPlayerImplInternal.PendingMessageInfo)this.pendingMessages.get(this.nextPendingMessageIndex - 1) : null; previousInfo != null && (previousInfo.resolvedPeriodIndex > currentPeriodIndex || previousInfo.resolvedPeriodIndex == currentPeriodIndex && previousInfo.resolvedPeriodTimeUs > oldPeriodPositionUs); previousInfo = this.nextPendingMessageIndex > 0 ? (ExoPlayerImplInternal.PendingMessageInfo)this.pendingMessages.get(this.nextPendingMessageIndex - 1) : null) {
        --this.nextPendingMessageIndex;
      }

      ExoPlayerImplInternal.PendingMessageInfo nextInfo;
      for(nextInfo = this.nextPendingMessageIndex < this.pendingMessages.size() ? (ExoPlayerImplInternal.PendingMessageInfo)this.pendingMessages.get(this.nextPendingMessageIndex) : null; nextInfo != null && nextInfo.resolvedPeriodUid != null && (nextInfo.resolvedPeriodIndex < currentPeriodIndex || nextInfo.resolvedPeriodIndex == currentPeriodIndex && nextInfo.resolvedPeriodTimeUs <= oldPeriodPositionUs); nextInfo = this.nextPendingMessageIndex < this.pendingMessages.size() ? (ExoPlayerImplInternal.PendingMessageInfo)this.pendingMessages.get(this.nextPendingMessageIndex) : null) {
        ++this.nextPendingMessageIndex;
      }

      for(; nextInfo != null && nextInfo.resolvedPeriodUid != null && nextInfo.resolvedPeriodIndex == currentPeriodIndex && nextInfo.resolvedPeriodTimeUs > oldPeriodPositionUs && nextInfo.resolvedPeriodTimeUs <= newPeriodPositionUs; nextInfo = this.nextPendingMessageIndex < this.pendingMessages.size() ? (ExoPlayerImplInternal.PendingMessageInfo)this.pendingMessages.get(this.nextPendingMessageIndex) : null) {
        this.sendMessageToTarget(nextInfo.message);
        if (!nextInfo.message.getDeleteAfterDelivery() && !nextInfo.message.isCanceled()) {
          ++this.nextPendingMessageIndex;
        } else {
          this.pendingMessages.remove(this.nextPendingMessageIndex);
        }
      }

    }
  }

  private void ensureStopped(Renderer renderer) throws ExoPlaybackException {
    if (renderer.getState() == 2) {
      renderer.stop();
    }

  }

  private void disableRenderer(Renderer renderer) throws ExoPlaybackException {
    this.mediaClock.onRendererDisabled(renderer);
    this.ensureStopped(renderer);
    renderer.disable();
  }

  private void reselectTracksInternal() throws ExoPlaybackException {
    if (this.queue.hasPlayingPeriod()) {
      float playbackSpeed = this.mediaClock.getPlaybackParameters().speed;
      MediaPeriodHolder periodHolder = this.queue.getPlayingPeriod();
      MediaPeriodHolder readingPeriodHolder = this.queue.getReadingPeriod();

      for(boolean selectionsChangedForReadPeriod = true; periodHolder != null && periodHolder.prepared; periodHolder = periodHolder.next) {
        if (periodHolder.selectTracks(playbackSpeed)) {
          if (selectionsChangedForReadPeriod) {
            MediaPeriodHolder playingPeriodHolder = this.queue.getPlayingPeriod();
            boolean recreateStreams = this.queue.removeAfter(playingPeriodHolder);
            boolean[] streamResetFlags = new boolean[this.renderers.length];
            long periodPositionUs = playingPeriodHolder.applyTrackSelection(this.playbackInfo.positionUs, recreateStreams, streamResetFlags);
            if (this.playbackInfo.playbackState != 4 && periodPositionUs != this.playbackInfo.positionUs) {
              this.playbackInfo = this.playbackInfo.copyWithNewPosition(this.playbackInfo.periodId, periodPositionUs, this.playbackInfo.contentPositionUs, this.getTotalBufferedDurationUs());
              this.playbackInfoUpdate.setPositionDiscontinuity(4);
              this.resetRendererPosition(periodPositionUs);
            }

            int enabledRendererCount = 0;
            boolean[] rendererWasEnabledFlags = new boolean[this.renderers.length];

            for(int i = 0; i < this.renderers.length; ++i) {
              Renderer renderer = this.renderers[i];
              rendererWasEnabledFlags[i] = renderer.getState() != 0;
              SampleStream sampleStream = playingPeriodHolder.sampleStreams[i];
              if (sampleStream != null) {
                ++enabledRendererCount;
              }

              if (rendererWasEnabledFlags[i]) {
                if (sampleStream != renderer.getStream()) {
                  this.disableRenderer(renderer);
                } else if (streamResetFlags[i]) {
                  renderer.resetPosition(this.rendererPositionUs);
                }
              }
            }

            this.playbackInfo = this.playbackInfo.copyWithTrackInfo(playingPeriodHolder.trackGroups, playingPeriodHolder.trackSelectorResult);
            this.enableRenderers(rendererWasEnabledFlags, enabledRendererCount);
          } else {
            this.queue.removeAfter(periodHolder);
            if (periodHolder.prepared) {
              long loadingPeriodPositionUs = Math.max(periodHolder.info.startPositionUs, periodHolder.toPeriodTime(this.rendererPositionUs));
              periodHolder.applyTrackSelection(loadingPeriodPositionUs, false);
            }
          }

          this.handleLoadingMediaPeriodChanged(true);
          if (this.playbackInfo.playbackState != 4) {
            this.maybeContinueLoading();
            this.updatePlaybackPositions();
            this.handler.sendEmptyMessage(2);
          }

          return;
        }

        if (periodHolder == readingPeriodHolder) {
          selectionsChangedForReadPeriod = false;
        }
      }

    }
  }

  private void updateTrackSelectionPlaybackSpeed(float playbackSpeed) {
    for(MediaPeriodHolder periodHolder = this.queue.getFrontPeriod(); periodHolder != null; periodHolder = periodHolder.next) {
      if (periodHolder.trackSelectorResult != null) {
        TrackSelection[] trackSelections = periodHolder.trackSelectorResult.selections.getAll();
        TrackSelection[] var4 = trackSelections;
        int var5 = trackSelections.length;

        for(int var6 = 0; var6 < var5; ++var6) {
          TrackSelection trackSelection = var4[var6];
          if (trackSelection != null) {
            trackSelection.onPlaybackSpeed(playbackSpeed);
          }
        }
      }
    }

  }

  private boolean shouldTransitionToReadyState(boolean renderersReadyOrEnded) {
    if (this.enabledRenderers.length == 0) {
      return this.isTimelineReady();
    } else if (!renderersReadyOrEnded) {
      return false;
    } else if (!this.playbackInfo.isLoading) {
      return true;
    } else {
      MediaPeriodHolder loadingHolder = this.queue.getLoadingPeriod();
      boolean bufferedToEnd = loadingHolder.isFullyBuffered() && loadingHolder.info.isFinal;
      return bufferedToEnd || this.loadControl.shouldStartPlayback(this.getTotalBufferedDurationUs(), this.mediaClock.getPlaybackParameters().speed, this.rebuffering);
    }
  }

  private boolean isTimelineReady() {
    MediaPeriodHolder playingPeriodHolder = this.queue.getPlayingPeriod();
    long playingPeriodDurationUs = playingPeriodHolder.info.durationUs;
    return playingPeriodDurationUs == -9223372036854775807L || this.playbackInfo.positionUs < playingPeriodDurationUs || playingPeriodHolder.next != null && (playingPeriodHolder.next.prepared || playingPeriodHolder.next.info.id.isAd());
  }

  private void maybeThrowSourceInfoRefreshError() throws IOException {
    MediaPeriodHolder loadingPeriodHolder = this.queue.getLoadingPeriod();
    if (loadingPeriodHolder != null) {
      Renderer[] var2 = this.enabledRenderers;
      int var3 = var2.length;

      for(int var4 = 0; var4 < var3; ++var4) {
        Renderer renderer = var2[var4];
        if (!renderer.hasReadStreamToEnd()) {
          return;
        }
      }
    }

    this.mediaSource.maybeThrowSourceInfoRefreshError();
  }

  private void maybeThrowPeriodPrepareError() throws IOException {
    MediaPeriodHolder loadingPeriodHolder = this.queue.getLoadingPeriod();
    MediaPeriodHolder readingPeriodHolder = this.queue.getReadingPeriod();
    if (loadingPeriodHolder != null && !loadingPeriodHolder.prepared && (readingPeriodHolder == null || readingPeriodHolder.next == loadingPeriodHolder)) {
      Renderer[] var3 = this.enabledRenderers;
      int var4 = var3.length;

      for(int var5 = 0; var5 < var4; ++var5) {
        Renderer renderer = var3[var5];
        if (!renderer.hasReadStreamToEnd()) {
          return;
        }
      }

      loadingPeriodHolder.mediaPeriod.maybeThrowPrepareError();
    }

  }

  private void handleSourceInfoRefreshed(ExoPlayerImplInternal.MediaSourceRefreshInfo sourceRefreshInfo) throws ExoPlaybackException {
    if (sourceRefreshInfo.source == this.mediaSource) {
      Timeline oldTimeline = this.playbackInfo.timeline;
      Timeline timeline = sourceRefreshInfo.timeline;
      Object manifest = sourceRefreshInfo.manifest;
      this.queue.setTimeline(timeline);
      this.playbackInfo = this.playbackInfo.copyWithTimeline(timeline, manifest);
      this.resolvePendingMessagePositions();
      long positionUs;
      Pair periodPosition;
      Object periodUid;
      MediaPeriodId periodId;
      if (this.pendingPrepareCount > 0) {
        this.playbackInfoUpdate.incrementPendingOperationAcks(this.pendingPrepareCount);
        this.pendingPrepareCount = 0;
        if (this.pendingInitialSeekPosition != null) {
          try {
            periodPosition = this.resolveSeekPosition(this.pendingInitialSeekPosition, true);
          } catch (IllegalSeekPositionException var15) {
            MediaPeriodId firstMediaPeriodId = this.playbackInfo.getDummyFirstMediaPeriodId(this.shuffleModeEnabled, this.window);
            this.playbackInfo = this.playbackInfo.resetToNewPosition(firstMediaPeriodId, -9223372036854775807L, -9223372036854775807L);
            throw var15;
          }

          this.pendingInitialSeekPosition = null;
          if (periodPosition == null) {
            this.handleSourceInfoRefreshEndedPlayback();
          } else {
            periodUid = periodPosition.first;
            positionUs = (Long)periodPosition.second;
            periodId = this.queue.resolveMediaPeriodIdForAds(periodUid, positionUs);
            this.playbackInfo = this.playbackInfo.resetToNewPosition(periodId, periodId.isAd() ? 0L : positionUs, positionUs);
          }
        } else if (this.playbackInfo.startPositionUs == -9223372036854775807L) {
          if (timeline.isEmpty()) {
            this.handleSourceInfoRefreshEndedPlayback();
          } else {
            periodPosition = this.getPeriodPosition(timeline, timeline.getFirstWindowIndex(this.shuffleModeEnabled), -9223372036854775807L);
            periodUid = periodPosition.first;
            positionUs = (Long)periodPosition.second;
            periodId = this.queue.resolveMediaPeriodIdForAds(periodUid, positionUs);
            this.playbackInfo = this.playbackInfo.resetToNewPosition(periodId, periodId.isAd() ? 0L : positionUs, positionUs);
          }
        }

      } else if (oldTimeline.isEmpty()) {
        if (!timeline.isEmpty()) {
          periodPosition = this.getPeriodPosition(timeline, timeline.getFirstWindowIndex(this.shuffleModeEnabled), -9223372036854775807L);
          periodUid = periodPosition.first;
          positionUs = (Long)periodPosition.second;
          periodId = this.queue.resolveMediaPeriodIdForAds(periodUid, positionUs);
          this.playbackInfo = this.playbackInfo.resetToNewPosition(periodId, periodId.isAd() ? 0L : positionUs, positionUs);
        }

      } else {
        MediaPeriodHolder periodHolder = this.queue.getFrontPeriod();
        long contentPositionUs = this.playbackInfo.contentPositionUs;
        Object playingPeriodUid = periodHolder == null ? this.playbackInfo.periodId.periodUid : periodHolder.uid;
        int periodIndex = timeline.getIndexOfPeriod(playingPeriodUid);
        if (periodIndex == -1) {
          Object newPeriodUid = this.resolveSubsequentPeriod(playingPeriodUid, oldTimeline, timeline);
          if (newPeriodUid == null) {
            this.handleSourceInfoRefreshEndedPlayback();
          } else {
            Pair<Object, Long> defaultPosition = this.getPeriodPosition(timeline, timeline.getPeriodByUid(newPeriodUid, this.period).windowIndex, -9223372036854775807L);
            newPeriodUid = defaultPosition.first;
            contentPositionUs = (Long)defaultPosition.second;
            MediaPeriodId periodId = this.queue.resolveMediaPeriodIdForAds(newPeriodUid, contentPositionUs);
            if (periodHolder != null) {
              while(periodHolder.next != null) {
                periodHolder = periodHolder.next;
                if (periodHolder.info.id.equals(periodId)) {
                  periodHolder.info = this.queue.getUpdatedMediaPeriodInfo(periodHolder.info);
                }
              }
            }

            long seekPositionUs = this.seekToPeriodPosition(periodId, periodId.isAd() ? 0L : contentPositionUs);
            this.playbackInfo = this.playbackInfo.copyWithNewPosition(periodId, seekPositionUs, contentPositionUs, this.getTotalBufferedDurationUs());
          }
        } else {
          MediaPeriodId playingPeriodId = this.playbackInfo.periodId;
          if (playingPeriodId.isAd()) {
            MediaPeriodId periodId = this.queue.resolveMediaPeriodIdForAds(playingPeriodUid, contentPositionUs);
            if (!periodId.equals(playingPeriodId)) {
              long seekPositionUs = this.seekToPeriodPosition(periodId, periodId.isAd() ? 0L : contentPositionUs);
              this.playbackInfo = this.playbackInfo.copyWithNewPosition(periodId, seekPositionUs, contentPositionUs, this.getTotalBufferedDurationUs());
              return;
            }
          }

          if (!this.queue.updateQueuedPeriods(playingPeriodId, this.rendererPositionUs)) {
            this.seekToCurrentPosition(false);
          }

          this.handleLoadingMediaPeriodChanged(false);
        }
      }
    }
  }

  private void handleSourceInfoRefreshEndedPlayback() {
    this.setState(4);
    this.resetInternal(false, true, false);
  }

  @Nullable
  private Object resolveSubsequentPeriod(Object oldPeriodUid, Timeline oldTimeline, Timeline newTimeline) {
    int oldPeriodIndex = oldTimeline.getIndexOfPeriod(oldPeriodUid);
    int newPeriodIndex = -1;
    int maxIterations = oldTimeline.getPeriodCount();

    for(int i = 0; i < maxIterations && newPeriodIndex == -1; ++i) {
      oldPeriodIndex = oldTimeline.getNextPeriodIndex(oldPeriodIndex, this.period, this.window, this.repeatMode, this.shuffleModeEnabled);
      if (oldPeriodIndex == -1) {
        break;
      }

      newPeriodIndex = newTimeline.getIndexOfPeriod(oldTimeline.getUidOfPeriod(oldPeriodIndex));
    }

    return newPeriodIndex == -1 ? null : newTimeline.getUidOfPeriod(newPeriodIndex);
  }

  private Pair<Object, Long> resolveSeekPosition(ExoPlayerImplInternal.SeekPosition seekPosition, boolean trySubsequentPeriods) {
    Timeline timeline = this.playbackInfo.timeline;
    Timeline seekTimeline = seekPosition.timeline;
    if (timeline.isEmpty()) {
      return null;
    } else {
      if (seekTimeline.isEmpty()) {
        seekTimeline = timeline;
      }

      Pair periodPosition;
      try {
        periodPosition = seekTimeline.getPeriodPosition(this.window, this.period, seekPosition.windowIndex, seekPosition.windowPositionUs);
      } catch (IndexOutOfBoundsException var8) {
        throw new IllegalSeekPositionException(timeline, seekPosition.windowIndex, seekPosition.windowPositionUs);
      }

      if (timeline == seekTimeline) {
        return periodPosition;
      } else {
        int periodIndex = timeline.getIndexOfPeriod(periodPosition.first);
        if (periodIndex != -1) {
          return periodPosition;
        } else {
          if (trySubsequentPeriods) {
            Object periodUid = this.resolveSubsequentPeriod(periodPosition.first, seekTimeline, timeline);
            if (periodUid != null) {
              return this.getPeriodPosition(timeline, timeline.getPeriod(periodIndex, this.period).windowIndex, -9223372036854775807L);
            }
          }

          return null;
        }
      }
    }
  }

  private Pair<Object, Long> getPeriodPosition(Timeline timeline, int windowIndex, long windowPositionUs) {
    return timeline.getPeriodPosition(this.window, this.period, windowIndex, windowPositionUs);
  }

  private void updatePeriods() throws ExoPlaybackException, IOException {
    if (this.mediaSource != null) {
      if (this.pendingPrepareCount > 0) {
        this.mediaSource.maybeThrowSourceInfoRefreshError();
      } else {
        this.maybeUpdateLoadingPeriod();
        MediaPeriodHolder loadingPeriodHolder = this.queue.getLoadingPeriod();
        if (loadingPeriodHolder != null && !loadingPeriodHolder.isFullyBuffered()) {
          if (!this.playbackInfo.isLoading) {
            this.maybeContinueLoading();
          }
        } else {
          this.setIsLoading(false);
        }

        if (this.queue.hasPlayingPeriod()) {
          MediaPeriodHolder playingPeriodHolder = this.queue.getPlayingPeriod();
          MediaPeriodHolder readingPeriodHolder = this.queue.getReadingPeriod();

          int i;
          for(boolean advancedPlayingPeriod = false; this.playWhenReady && playingPeriodHolder != readingPeriodHolder && this.rendererPositionUs >= playingPeriodHolder.next.getStartPositionRendererTime(); advancedPlayingPeriod = true) {
            if (advancedPlayingPeriod) {
              this.maybeNotifyPlaybackInfoChanged();
            }

            i = playingPeriodHolder.info.isLastInTimelinePeriod ? 0 : 3;
            MediaPeriodHolder oldPlayingPeriodHolder = playingPeriodHolder;
            playingPeriodHolder = this.queue.advancePlayingPeriod();
            this.updatePlayingPeriodRenderers(oldPlayingPeriodHolder);
            this.playbackInfo = this.playbackInfo.copyWithNewPosition(playingPeriodHolder.info.id, playingPeriodHolder.info.startPositionUs, playingPeriodHolder.info.contentPositionUs, this.getTotalBufferedDurationUs());
            this.playbackInfoUpdate.setPositionDiscontinuity(i);
            this.updatePlaybackPositions();
          }

          SampleStream sampleStream;
          Renderer renderer;
          if (readingPeriodHolder.info.isFinal) {
            for(i = 0; i < this.renderers.length; ++i) {
              renderer = this.renderers[i];
              sampleStream = readingPeriodHolder.sampleStreams[i];
              if (sampleStream != null && renderer.getStream() == sampleStream && renderer.hasReadStreamToEnd()) {
                renderer.setCurrentStreamFinal();
              }
            }

          } else if (readingPeriodHolder.next != null) {
            for(i = 0; i < this.renderers.length; ++i) {
              renderer = this.renderers[i];
              sampleStream = readingPeriodHolder.sampleStreams[i];
              if (renderer.getStream() != sampleStream || sampleStream != null && !renderer.hasReadStreamToEnd()) {
                return;
              }
            }

            if (!readingPeriodHolder.next.prepared) {
              this.maybeThrowPeriodPrepareError();
            } else {
              TrackSelectorResult oldTrackSelectorResult = readingPeriodHolder.trackSelectorResult;
              readingPeriodHolder = this.queue.advanceReadingPeriod();
              TrackSelectorResult newTrackSelectorResult = readingPeriodHolder.trackSelectorResult;
              boolean initialDiscontinuity = readingPeriodHolder.mediaPeriod.readDiscontinuity() != -9223372036854775807L;

              for(int i = 0; i < this.renderers.length; ++i) {
                Renderer renderer = this.renderers[i];
                boolean rendererWasEnabled = oldTrackSelectorResult.isRendererEnabled(i);
                if (rendererWasEnabled) {
                  if (initialDiscontinuity) {
                    renderer.setCurrentStreamFinal();
                  } else if (!renderer.isCurrentStreamFinal()) {
                    TrackSelection newSelection = newTrackSelectorResult.selections.get(i);
                    boolean newRendererEnabled = newTrackSelectorResult.isRendererEnabled(i);
                    boolean isNoSampleRenderer = this.rendererCapabilities[i].getTrackType() == 6;
                    RendererConfiguration oldConfig = oldTrackSelectorResult.rendererConfigurations[i];
                    RendererConfiguration newConfig = newTrackSelectorResult.rendererConfigurations[i];
                    if (newRendererEnabled && newConfig.equals(oldConfig) && !isNoSampleRenderer) {
                      Format[] formats = getFormats(newSelection);
                      renderer.replaceStream(formats, readingPeriodHolder.sampleStreams[i], readingPeriodHolder.getRendererOffset());
                    } else {
                      renderer.setCurrentStreamFinal();
                    }
                  }
                }
              }

            }
          }
        }
      }
    }
  }

  private void maybeUpdateLoadingPeriod() throws IOException {
    this.queue.reevaluateBuffer(this.rendererPositionUs);
    if (this.queue.shouldLoadNextMediaPeriod()) {
      MediaPeriodInfo info = this.queue.getNextMediaPeriodInfo(this.rendererPositionUs, this.playbackInfo);
      if (info == null) {
        this.maybeThrowSourceInfoRefreshError();
      } else {
        MediaPeriod mediaPeriod = this.queue.enqueueNextMediaPeriod(this.rendererCapabilities, this.trackSelector, this.loadControl.getAllocator(), this.mediaSource, info);
        mediaPeriod.prepare(this, info.startPositionUs);
        this.setIsLoading(true);
        this.handleLoadingMediaPeriodChanged(false);
      }
    }

  }

  private void handlePeriodPrepared(MediaPeriod mediaPeriod) throws ExoPlaybackException {
    if (this.queue.isLoading(mediaPeriod)) {
      MediaPeriodHolder loadingPeriodHolder = this.queue.getLoadingPeriod();
      loadingPeriodHolder.handlePrepared(this.mediaClock.getPlaybackParameters().speed);
      this.updateLoadControlTrackSelection(loadingPeriodHolder.trackGroups, loadingPeriodHolder.trackSelectorResult);
      if (!this.queue.hasPlayingPeriod()) {
        MediaPeriodHolder playingPeriodHolder = this.queue.advancePlayingPeriod();
        this.resetRendererPosition(playingPeriodHolder.info.startPositionUs);
        this.updatePlayingPeriodRenderers((MediaPeriodHolder)null);
      }

      this.maybeContinueLoading();
    }
  }

  private void handleContinueLoadingRequested(MediaPeriod mediaPeriod) {
    if (this.queue.isLoading(mediaPeriod)) {
      this.queue.reevaluateBuffer(this.rendererPositionUs);
      this.maybeContinueLoading();
    }
  }

  private void handlePlaybackParameters(PlaybackParameters playbackParameters) throws ExoPlaybackException {
    this.eventHandler.obtainMessage(1, playbackParameters).sendToTarget();
    this.updateTrackSelectionPlaybackSpeed(playbackParameters.speed);
    Renderer[] var2 = this.renderers;
    int var3 = var2.length;

    for(int var4 = 0; var4 < var3; ++var4) {
      Renderer renderer = var2[var4];
      if (renderer != null) {
        renderer.setOperatingRate(playbackParameters.speed);
      }
    }

  }

  private void maybeContinueLoading() {
    MediaPeriodHolder loadingPeriodHolder = this.queue.getLoadingPeriod();
    long nextLoadPositionUs = loadingPeriodHolder.getNextLoadPositionUs();
    if (nextLoadPositionUs == -9223372036854775808L) {
      this.setIsLoading(false);
    } else {
      long bufferedDurationUs = this.getTotalBufferedDurationUs(nextLoadPositionUs);
      boolean continueLoading = this.loadControl.shouldContinueLoading(bufferedDurationUs, this.mediaClock.getPlaybackParameters().speed);
      this.setIsLoading(continueLoading);
      if (continueLoading) {
        loadingPeriodHolder.continueLoading(this.rendererPositionUs);
      }

    }
  }

  private void updatePlayingPeriodRenderers(@Nullable MediaPeriodHolder oldPlayingPeriodHolder) throws ExoPlaybackException {
    MediaPeriodHolder newPlayingPeriodHolder = this.queue.getPlayingPeriod();
    if (newPlayingPeriodHolder != null && oldPlayingPeriodHolder != newPlayingPeriodHolder) {
      int enabledRendererCount = 0;
      boolean[] rendererWasEnabledFlags = new boolean[this.renderers.length];

      for(int i = 0; i < this.renderers.length; ++i) {
        Renderer renderer = this.renderers[i];
        rendererWasEnabledFlags[i] = renderer.getState() != 0;
        if (newPlayingPeriodHolder.trackSelectorResult.isRendererEnabled(i)) {
          ++enabledRendererCount;
        }

        if (rendererWasEnabledFlags[i] && (!newPlayingPeriodHolder.trackSelectorResult.isRendererEnabled(i) || renderer.isCurrentStreamFinal() && renderer.getStream() == oldPlayingPeriodHolder.sampleStreams[i])) {
          this.disableRenderer(renderer);
        }
      }

      this.playbackInfo = this.playbackInfo.copyWithTrackInfo(newPlayingPeriodHolder.trackGroups, newPlayingPeriodHolder.trackSelectorResult);
      this.enableRenderers(rendererWasEnabledFlags, enabledRendererCount);
    }
  }

  private void enableRenderers(boolean[] rendererWasEnabledFlags, int totalEnabledRendererCount) throws ExoPlaybackException {
    this.enabledRenderers = new Renderer[totalEnabledRendererCount];
    int enabledRendererCount = 0;
    MediaPeriodHolder playingPeriodHolder = this.queue.getPlayingPeriod();

    for(int i = 0; i < this.renderers.length; ++i) {
      if (playingPeriodHolder.trackSelectorResult.isRendererEnabled(i)) {
        this.enableRenderer(i, rendererWasEnabledFlags[i], enabledRendererCount++);
      }
    }

  }

  private void enableRenderer(int rendererIndex, boolean wasRendererEnabled, int enabledRendererIndex) throws ExoPlaybackException {
    MediaPeriodHolder playingPeriodHolder = this.queue.getPlayingPeriod();
    Renderer renderer = this.renderers[rendererIndex];
    this.enabledRenderers[enabledRendererIndex] = renderer;
    if (renderer.getState() == 0) {
      RendererConfiguration rendererConfiguration = playingPeriodHolder.trackSelectorResult.rendererConfigurations[rendererIndex];
      TrackSelection newSelection = playingPeriodHolder.trackSelectorResult.selections.get(rendererIndex);
      Format[] formats = getFormats(newSelection);
      boolean playing = this.playWhenReady && this.playbackInfo.playbackState == 3;
      boolean joining = !wasRendererEnabled && playing;
      renderer.enable(rendererConfiguration, formats, playingPeriodHolder.sampleStreams[rendererIndex], this.rendererPositionUs, joining, playingPeriodHolder.getRendererOffset());
      this.mediaClock.onRendererEnabled(renderer);
      if (playing) {
        renderer.start();
      }
    }

  }

  private boolean rendererWaitingForNextStream(Renderer renderer) {
    MediaPeriodHolder readingPeriodHolder = this.queue.getReadingPeriod();
    return readingPeriodHolder.next != null && readingPeriodHolder.next.prepared && renderer.hasReadStreamToEnd();
  }

  private void handleLoadingMediaPeriodChanged(boolean loadingTrackSelectionChanged) {
    MediaPeriodHolder loadingMediaPeriodHolder = this.queue.getLoadingPeriod();
    MediaPeriodId loadingMediaPeriodId = loadingMediaPeriodHolder == null ? this.playbackInfo.periodId : loadingMediaPeriodHolder.info.id;
    boolean loadingMediaPeriodChanged = !this.playbackInfo.loadingMediaPeriodId.equals(loadingMediaPeriodId);
    if (loadingMediaPeriodChanged) {
      this.playbackInfo = this.playbackInfo.copyWithLoadingMediaPeriodId(loadingMediaPeriodId);
    }

    this.playbackInfo.bufferedPositionUs = loadingMediaPeriodHolder == null ? this.playbackInfo.positionUs : loadingMediaPeriodHolder.getBufferedPositionUs();
    this.playbackInfo.totalBufferedDurationUs = this.getTotalBufferedDurationUs();
    if ((loadingMediaPeriodChanged || loadingTrackSelectionChanged) && loadingMediaPeriodHolder != null && loadingMediaPeriodHolder.prepared) {
      this.updateLoadControlTrackSelection(loadingMediaPeriodHolder.trackGroups, loadingMediaPeriodHolder.trackSelectorResult);
    }

  }

  private long getTotalBufferedDurationUs() {
    return this.getTotalBufferedDurationUs(this.playbackInfo.bufferedPositionUs);
  }

  private long getTotalBufferedDurationUs(long bufferedPositionInLoadingPeriodUs) {
    MediaPeriodHolder loadingPeriodHolder = this.queue.getLoadingPeriod();
    return loadingPeriodHolder == null ? 0L : bufferedPositionInLoadingPeriodUs - loadingPeriodHolder.toPeriodTime(this.rendererPositionUs);
  }

  private void updateLoadControlTrackSelection(TrackGroupArray trackGroups, TrackSelectorResult trackSelectorResult) {
    this.loadControl.onTracksSelected(this.renderers, trackGroups, trackSelectorResult.selections);
  }

  private static Format[] getFormats(TrackSelection newSelection) {
    int length = newSelection != null ? newSelection.length() : 0;
    Format[] formats = new Format[length];

    for(int i = 0; i < length; ++i) {
      formats[i] = newSelection.getFormat(i);
    }

    return formats;
  }

  private static final class PlaybackInfoUpdate {
    private PlaybackInfo lastPlaybackInfo;
    private int operationAcks;
    private boolean positionDiscontinuity;
    private int discontinuityReason;

    private PlaybackInfoUpdate() {
    }

    public boolean hasPendingUpdate(PlaybackInfo playbackInfo) {
      return playbackInfo != this.lastPlaybackInfo || this.operationAcks > 0 || this.positionDiscontinuity;
    }

    public void reset(PlaybackInfo playbackInfo) {
      this.lastPlaybackInfo = playbackInfo;
      this.operationAcks = 0;
      this.positionDiscontinuity = false;
    }

    public void incrementPendingOperationAcks(int operationAcks) {
      this.operationAcks += operationAcks;
    }

    public void setPositionDiscontinuity(int discontinuityReason) {
      if (this.positionDiscontinuity && this.discontinuityReason != 4) {
        Assertions.checkArgument(discontinuityReason == 4);
      } else {
        this.positionDiscontinuity = true;
        this.discontinuityReason = discontinuityReason;
      }
    }
  }

  private static final class MediaSourceRefreshInfo {
    public final MediaSource source;
    public final Timeline timeline;
    public final Object manifest;

    public MediaSourceRefreshInfo(MediaSource source, Timeline timeline, Object manifest) {
      this.source = source;
      this.timeline = timeline;
      this.manifest = manifest;
    }
  }

  private static final class PendingMessageInfo implements Comparable<ExoPlayerImplInternal.PendingMessageInfo> {
    public final PlayerMessage message;
    public int resolvedPeriodIndex;
    public long resolvedPeriodTimeUs;
    @Nullable
    public Object resolvedPeriodUid;

    public PendingMessageInfo(PlayerMessage message) {
      this.message = message;
    }

    public void setResolvedPosition(int periodIndex, long periodTimeUs, Object periodUid) {
      this.resolvedPeriodIndex = periodIndex;
      this.resolvedPeriodTimeUs = periodTimeUs;
      this.resolvedPeriodUid = periodUid;
    }

    public int compareTo(@NonNull ExoPlayerImplInternal.PendingMessageInfo other) {
      if (this.resolvedPeriodUid == null != (other.resolvedPeriodUid == null)) {
        return this.resolvedPeriodUid != null ? -1 : 1;
      } else if (this.resolvedPeriodUid == null) {
        return 0;
      } else {
        int comparePeriodIndex = this.resolvedPeriodIndex - other.resolvedPeriodIndex;
        return comparePeriodIndex != 0 ? comparePeriodIndex : Util.compareLong(this.resolvedPeriodTimeUs, other.resolvedPeriodTimeUs);
      }
    }
  }

  private static final class SeekPosition {
    public final Timeline timeline;
    public final int windowIndex;
    public final long windowPositionUs;

    public SeekPosition(Timeline timeline, int windowIndex, long windowPositionUs) {
      this.timeline = timeline;
      this.windowIndex = windowIndex;
      this.windowPositionUs = windowPositionUs;
    }
  }
}
