package com.google.android.exoplayer2.upstream.cache;

public interface ContentMetadata {
  String INTERNAL_METADATA_NAME_PREFIX = "exo_";

  byte[] get(String var1, byte[] var2);

  String get(String var1, String var2);

  long get(String var1, long var2);

  boolean contains(String var1);
}
