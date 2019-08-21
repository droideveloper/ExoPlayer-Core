package com.google.android.exoplayer2;

mport android.os.Looper;
import android.os.Looper;
import androidx.annotation.Nullable;
import com.google.android.exoplayer2.PlayerMessage.Target;
import com.google.android.exoplayer2.source.MediaSource;

public interface ExoPlayer extends Player {
  /** @deprecated */
  @Deprecated
  int STATE_IDLE = 1;
  /** @deprecated */
  @Deprecated
  int STATE_BUFFERING = 2;
  /** @deprecated */
  @Deprecated
  int STATE_READY = 3;
  /** @deprecated */
  @Deprecated
  int STATE_ENDED = 4;
  /** @deprecated */
  @Deprecated
  int REPEAT_MODE_OFF = 0;
  /** @deprecated */
  @Deprecated
  int REPEAT_MODE_ONE = 1;
  /** @deprecated */
  @Deprecated
  int REPEAT_MODE_ALL = 2;

  Looper getPlaybackLooper();

  void retry();

  void prepare(MediaSource var1);

  void prepare(MediaSource var1, boolean var2, boolean var3);

  PlayerMessage createMessage(Target var1);

  /** @deprecated */
  @Deprecated
  void sendMessages(ExoPlayer.ExoPlayerMessage... var1);

  /** @deprecated */
  @Deprecated
  void blockingSendMessages(ExoPlayer.ExoPlayerMessage... var1);

  void setSeekParameters(@Nullable SeekParameters var1);

  SeekParameters getSeekParameters();

  /** @deprecated */
  @Deprecated
  public static final class ExoPlayerMessage {
    public final Target target;
    public final int messageType;
    public final Object message;

    /** @deprecated */
    @Deprecated
    public ExoPlayerMessage(Target target, int messageType, Object message) {
      this.target = target;
      this.messageType = messageType;
      this.message = message;
    }
  }

  /** @deprecated */
  @Deprecated
  public interface ExoPlayerComponent extends Target {
  }

  /** @deprecated */
  @Deprecated
  public interface EventListener extends com.google.android.exoplayer2.Player.EventListener {
  }
}