package com.google.android.exoplayer2.video;

import android.os.Handler;
import android.view.Surface;
import androidx.annotation.Nullable;
import com.google.android.exoplayer2.Format;
import com.google.android.exoplayer2.decoder.DecoderCounters;
import com.google.android.exoplayer2.util.Assertions;

public interface VideoRendererEventListener {
  void onVideoEnabled(DecoderCounters var1);

  void onVideoDecoderInitialized(String var1, long var2, long var4);

  void onVideoInputFormatChanged(Format var1);

  void onDroppedFrames(int var1, long var2);

  void onVideoSizeChanged(int var1, int var2, int var3, float var4);

  void onRenderedFirstFrame(@Nullable Surface var1);

  void onVideoDisabled(DecoderCounters var1);

  public static final class EventDispatcher {
    @Nullable
    private final Handler handler;
    @Nullable
    private final VideoRendererEventListener listener;

    public EventDispatcher(@Nullable Handler handler, @Nullable VideoRendererEventListener listener) {
      this.handler = listener != null ? (Handler)Assertions.checkNotNull(handler) : null;
      this.listener = listener;
    }

    public void enabled(DecoderCounters decoderCounters) {
      if (this.listener != null) {
        this.handler.post(() -> {
          this.listener.onVideoEnabled(decoderCounters);
        });
      }

    }

    public void decoderInitialized(String decoderName, long initializedTimestampMs, long initializationDurationMs) {
      if (this.listener != null) {
        this.handler.post(() -> {
          this.listener.onVideoDecoderInitialized(decoderName, initializedTimestampMs, initializationDurationMs);
        });
      }

    }

    public void inputFormatChanged(Format format) {
      if (this.listener != null) {
        this.handler.post(() -> {
          this.listener.onVideoInputFormatChanged(format);
        });
      }

    }

    public void droppedFrames(int droppedFrameCount, long elapsedMs) {
      if (this.listener != null) {
        this.handler.post(() -> {
          this.listener.onDroppedFrames(droppedFrameCount, elapsedMs);
        });
      }

    }

    public void videoSizeChanged(int width, int height, int unappliedRotationDegrees, float pixelWidthHeightRatio) {
      if (this.listener != null) {
        this.handler.post(() -> {
          this.listener.onVideoSizeChanged(width, height, unappliedRotationDegrees, pixelWidthHeightRatio);
        });
      }

    }

    public void renderedFirstFrame(@Nullable Surface surface) {
      if (this.listener != null) {
        this.handler.post(() -> {
          this.listener.onRenderedFirstFrame(surface);
        });
      }

    }

    public void disabled(DecoderCounters counters) {
      if (this.listener != null) {
        this.handler.post(() -> {
          counters.ensureUpdated();
          this.listener.onVideoDisabled(counters);
        });
      }

    }
  }
}