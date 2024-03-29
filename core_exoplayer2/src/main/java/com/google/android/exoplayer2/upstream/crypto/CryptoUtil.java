package com.google.android.exoplayer2.upstream.crypto;

final class CryptoUtil {
  private CryptoUtil() {
  }

  public static long getFNV64Hash(String input) {
    if (input == null) {
      return 0L;
    } else {
      long hash = 0L;

      for(int i = 0; i < input.length(); ++i) {
        hash ^= (long)input.charAt(i);
        hash += (hash << 1) + (hash << 4) + (hash << 5) + (hash << 7) + (hash << 8) + (hash << 40);
      }

      return hash;
    }
  }
}
