package com.google.android.exoplayer2;

import com.google.android.exoplayer2.util.Assertions;
import java.io.IOException;
import java.lang.annotation.Documented;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

public final class ExoPlaybackException extends Exception {
  public static final int TYPE_SOURCE = 0;
  public static final int TYPE_RENDERER = 1;
  public static final int TYPE_UNEXPECTED = 2;
  public final int type;
  public final int rendererIndex;
  private final Throwable cause;

  public static ExoPlaybackException createForSource(IOException cause) {
    return new ExoPlaybackException(0, cause, -1);
  }

  public static ExoPlaybackException createForRenderer(Exception cause, int rendererIndex) {
    return new ExoPlaybackException(1, cause, rendererIndex);
  }

  static ExoPlaybackException createForUnexpected(RuntimeException cause) {
    return new ExoPlaybackException(2, cause, -1);
  }

  private ExoPlaybackException(int type, Throwable cause, int rendererIndex) {
    super(cause);
    this.type = type;
    this.cause = cause;
    this.rendererIndex = rendererIndex;
  }

  public IOException getSourceException() {
    Assertions.checkState(this.type == 0);
    return (IOException)this.cause;
  }

  public Exception getRendererException() {
    Assertions.checkState(this.type == 1);
    return (Exception)this.cause;
  }

  public RuntimeException getUnexpectedException() {
    Assertions.checkState(this.type == 2);
    return (RuntimeException)this.cause;
  }

  @Documented
  @Retention(RetentionPolicy.SOURCE)
  public @interface Type {
  }