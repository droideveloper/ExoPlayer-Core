package com.google.android.exoplayer2.upstream;


public interface Allocator {
  Allocation allocate();

  void release(Allocation var1);

  void release(Allocation[] var1);

  void trim();

  int getTotalBytesAllocated();

  int getIndividualAllocationLength();
}