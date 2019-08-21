package com.google.android.exoplayer2.upstream;

import java.io.IOException;

public interface DataSink {
  void open(DataSpec var1) throws IOException;

  void write(byte[] var1, int var2, int var3) throws IOException;

  void close() throws IOException;

  public interface Factory {
    DataSink createDataSink();
  }
}
