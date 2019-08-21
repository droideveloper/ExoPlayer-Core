package com.google.android.exoplayer2.util;


import com.google.android.exoplayer2.PlaybackParameters;

public interface MediaClock {
  long getPositionUs();

  PlaybackParameters setPlaybackParameters(PlaybackParameters var1);

  PlaybackParameters getPlaybackParameters();
}