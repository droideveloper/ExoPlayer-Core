package com.google.android.exoplayer2.video;

import com.google.android.exoplayer2.Format;

public interface VideoFrameMetadataListener {
  void onVideoFrameAboutToBeRendered(long var1, long var3, Format var5);
}