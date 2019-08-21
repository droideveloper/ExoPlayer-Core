package com.google.android.exoplayer2.util;

import java.io.BufferedOutputStream;
import java.io.IOException;
import java.io.OutputStream;

public final class ReusableBufferedOutputStream extends BufferedOutputStream {
  private boolean closed;

  public ReusableBufferedOutputStream(OutputStream out) {
    super(out);
  }

  public ReusableBufferedOutputStream(OutputStream out, int size) {
    super(out, size);
  }

  public void close() throws IOException {
    this.closed = true;
    Throwable thrown = null;

    try {
      this.flush();
    } catch (Throwable var3) {
      thrown = var3;
    }

    try {
      this.out.close();
    } catch (Throwable var4) {
      if (thrown == null) {
        thrown = var4;
      }
    }

    if (thrown != null) {
      Util.sneakyThrow(thrown);
    }

  }

  public void reset(OutputStream out) {
    Assertions.checkState(this.closed);
    this.out = out;
    this.count = 0;
    this.closed = false;
  }
}
