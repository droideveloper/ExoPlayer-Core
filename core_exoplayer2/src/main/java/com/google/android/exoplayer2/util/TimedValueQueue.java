package com.google.android.exoplayer2.util;

import androidx.annotation.Nullable;
import java.util.Arrays;

public final class TimedValueQueue<V> {
  private static final int INITIAL_BUFFER_SIZE = 10;
  private long[] timestamps;
  private V[] values;
  private int first;
  private int size;

  public TimedValueQueue() {
    this(10);
  }

  public TimedValueQueue(int initialBufferSize) {
    this.timestamps = new long[initialBufferSize];
    this.values = newArray(initialBufferSize);
  }

  public synchronized void add(long timestamp, V value) {
    this.clearBufferOnTimeDiscontinuity(timestamp);
    this.doubleCapacityIfFull();
    this.addUnchecked(timestamp, value);
  }

  public synchronized void clear() {
    this.first = 0;
    this.size = 0;
    Arrays.fill(this.values, (Object)null);
  }

  public synchronized int size() {
    return this.size;
  }

  @Nullable
  public synchronized V pollFloor(long timestamp) {
    return this.poll(timestamp, true);
  }

  @Nullable
  public synchronized V poll(long timestamp) {
    return this.poll(timestamp, false);
  }

  @Nullable
  private V poll(long timestamp, boolean onlyOlder) {
    V value = null;

    for(long previousTimeDiff = 9223372036854775807L; this.size > 0; --this.size) {
      long timeDiff = timestamp - this.timestamps[this.first];
      if (timeDiff < 0L && (onlyOlder || -timeDiff >= previousTimeDiff)) {
        break;
      }

      previousTimeDiff = timeDiff;
      value = this.values[this.first];
      this.values[this.first] = null;
      this.first = (this.first + 1) % this.values.length;
    }

    return value;
  }

  private void clearBufferOnTimeDiscontinuity(long timestamp) {
    if (this.size > 0) {
      int last = (this.first + this.size - 1) % this.values.length;
      if (timestamp <= this.timestamps[last]) {
        this.clear();
      }
    }

  }

  private void doubleCapacityIfFull() {
    int capacity = this.values.length;
    if (this.size >= capacity) {
      int newCapacity = capacity * 2;
      long[] newTimestamps = new long[newCapacity];
      V[] newValues = newArray(newCapacity);
      int length = capacity - this.first;
      System.arraycopy(this.timestamps, this.first, newTimestamps, 0, length);
      System.arraycopy(this.values, this.first, newValues, 0, length);
      if (this.first > 0) {
        System.arraycopy(this.timestamps, 0, newTimestamps, length, this.first);
        System.arraycopy(this.values, 0, newValues, length, this.first);
      }

      this.timestamps = newTimestamps;
      this.values = newValues;
      this.first = 0;
    }
  }

  private void addUnchecked(long timestamp, V value) {
    int next = (this.first + this.size) % this.values.length;
    this.timestamps[next] = timestamp;
    this.values[next] = value;
    ++this.size;
  }

  private static <V> V[] newArray(int length) {
    return (V[])new Object[length];
  }
}
