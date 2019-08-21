package com.google.android.exoplayer2;

import com.google.android.exoplayer2.PlayerMessage.Target;
import com.google.android.exoplayer2.source.SampleStream;
import com.google.android.exoplayer2.util.MediaClock;
import java.io.IOException;
import java.lang.annotation.Documented;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.text.Format;

public interface Renderer extends Target {
  int STATE_DISABLED = 0;
  int STATE_ENABLED = 1;
  int STATE_STARTED = 2;

  int getTrackType();

  RendererCapabilities getCapabilities();

  void setIndex(int var1);

  MediaClock getMediaClock();

  int getState();

  void enable(RendererConfiguration var1, Format[] var2, SampleStream var3, long var4, boolean var6, long var7) throws ExoPlaybackException;

  void start() throws ExoPlaybackException;

  void replaceStream(Format[] var1, SampleStream var2, long var3) throws ExoPlaybackException;

  SampleStream getStream();

  boolean hasReadStreamToEnd();

  void setCurrentStreamFinal();

  boolean isCurrentStreamFinal();

  void maybeThrowStreamError() throws IOException;

  void resetPosition(long var1) throws ExoPlaybackException;

  default void setOperatingRate(float operatingRate) throws ExoPlaybackException {
  }

  void render(long var1, long var3) throws ExoPlaybackException;

  boolean isReady();

  boolean isEnded();

  void stop() throws ExoPlaybackException;

  void disable();

  @Documented
  @Retention(RetentionPolicy.SOURCE)
  public @interface State {
  }
}

