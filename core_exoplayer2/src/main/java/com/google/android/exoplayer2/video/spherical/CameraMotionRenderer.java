package com.google.android.exoplayer2.video.spherical;

import androidx.annotation.Nullable;
import com.google.android.exoplayer2.BaseRenderer;
import com.google.android.exoplayer2.ExoPlaybackException;
import com.google.android.exoplayer2.Format;
import com.google.android.exoplayer2.FormatHolder;
import com.google.android.exoplayer2.decoder.DecoderInputBuffer;
import com.google.android.exoplayer2.util.ParsableByteArray;
import com.google.android.exoplayer2.util.Util;
import java.nio.ByteBuffer;

public class CameraMotionRenderer extends BaseRenderer {
  private static final int SAMPLE_WINDOW_DURATION_US = 100000;
  private final FormatHolder formatHolder = new FormatHolder();
  private final DecoderInputBuffer buffer = new DecoderInputBuffer(1);
  private final ParsableByteArray scratch = new ParsableByteArray();
  private long offsetUs;
  @Nullable
  private CameraMotionListener listener;
  private long lastTimestampUs;

  public CameraMotionRenderer() {
    super(5);
  }

  public int supportsFormat(Format format) {
    return "application/x-camera-motion".equals(format.sampleMimeType) ? 4 : 0;
  }

  public void handleMessage(int messageType, @Nullable Object message) throws ExoPlaybackException {
    if (messageType == 7) {
      this.listener = (CameraMotionListener)message;
    } else {
      super.handleMessage(messageType, message);
    }

  }

  protected void onStreamChanged(Format[] formats, long offsetUs) throws ExoPlaybackException {
    this.offsetUs = offsetUs;
  }

  protected void onPositionReset(long positionUs, boolean joining) throws ExoPlaybackException {
    this.reset();
  }

  protected void onDisabled() {
    this.reset();
  }

  public void render(long positionUs, long elapsedRealtimeUs) throws ExoPlaybackException {
    while(true) {
      if (!this.hasReadStreamToEnd() && this.lastTimestampUs < positionUs + 100000L) {
        this.buffer.clear();
        int result = this.readSource(this.formatHolder, this.buffer, false);
        if (result == -4 && !this.buffer.isEndOfStream()) {
          this.buffer.flip();
          this.lastTimestampUs = this.buffer.timeUs;
          if (this.listener == null) {
            continue;
          }

          float[] rotation = this.parseMetadata(this.buffer.data);
          if (rotation != null) {
            ((CameraMotionListener)Util.castNonNull(this.listener)).onCameraMotion(this.lastTimestampUs - this.offsetUs, rotation);
          }
          continue;
        }

        return;
      }

      return;
    }
  }

  public boolean isEnded() {
    return this.hasReadStreamToEnd();
  }

  public boolean isReady() {
    return true;
  }

  @Nullable
  private float[] parseMetadata(ByteBuffer data) {
    if (data.remaining() != 16) {
      return null;
    } else {
      this.scratch.reset(data.array(), data.limit());
      this.scratch.setPosition(data.arrayOffset() + 4);
      float[] result = new float[3];

      for(int i = 0; i < 3; ++i) {
        result[i] = Float.intBitsToFloat(this.scratch.readLittleEndianInt());
      }

      return result;
    }
  }

  private void reset() {
    this.lastTimestampUs = 0L;
    if (this.listener != null) {
      this.listener.onCameraMotionReset();
    }

  }
}