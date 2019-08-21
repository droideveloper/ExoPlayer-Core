package com.google.android.exoplayer2.util;

import android.os.SystemClock;

public final class ConditionVariable {
  private boolean isOpen;

  public ConditionVariable() {
  }

  public synchronized boolean open() {
    if (this.isOpen) {
      return false;
    } else {
      this.isOpen = true;
      this.notifyAll();
      return true;
    }
  }

  public synchronized boolean close() {
    boolean wasOpen = this.isOpen;
    this.isOpen = false;
    return wasOpen;
  }

  public synchronized void block() throws InterruptedException {
    while(!this.isOpen) {
      this.wait();
    }

  }

  public synchronized boolean block(long timeout) throws InterruptedException {
    long now = SystemClock.elapsedRealtime();

    for(long end = now + timeout; !this.isOpen && now < end; now = SystemClock.elapsedRealtime()) {
      this.wait(end - now);
    }

    return this.isOpen;
  }
}
