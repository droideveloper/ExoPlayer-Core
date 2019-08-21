package com.google.android.exoplayer2.video;

public interface VideoListener {
  default void onVideoSizeChanged(int width, int height, int unappliedRotationDegrees, float pixelWidthHeightRatio) {
  }

  default void onSurfaceSizeChanged(int width, int height) {
  }

  default void onRenderedFirstFrame() {
  }
}
