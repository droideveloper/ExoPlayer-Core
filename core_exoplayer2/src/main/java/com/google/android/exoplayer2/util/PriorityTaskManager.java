package com.google.android.exoplayer2.util;

import java.io.IOException;
import java.util.Collections;
import java.util.PriorityQueue;

public final class PriorityTaskManager {
  private final Object lock = new Object();
  private final PriorityQueue<Integer> queue = new PriorityQueue(10, Collections.reverseOrder());
  private int highestPriority = -2147483648;

  public PriorityTaskManager() {
  }

  public void add(int priority) {
    synchronized(this.lock) {
      this.queue.add(priority);
      this.highestPriority = Math.max(this.highestPriority, priority);
    }
  }

  public void proceed(int priority) throws InterruptedException {
    synchronized(this.lock) {
      while(this.highestPriority != priority) {
        this.lock.wait();
      }

    }
  }

  public boolean proceedNonBlocking(int priority) {
    synchronized(this.lock) {
      return this.highestPriority == priority;
    }
  }

  public void proceedOrThrow(int priority) throws PriorityTaskManager.PriorityTooLowException {
    synchronized(this.lock) {
      if (this.highestPriority != priority) {
        throw new PriorityTaskManager.PriorityTooLowException(priority, this.highestPriority);
      }
    }
  }

  public void remove(int priority) {
    synchronized(this.lock) {
      this.queue.remove(priority);
      this.highestPriority = this.queue.isEmpty() ? -2147483648 : (Integer)Util.castNonNull(this.queue.peek());
      this.lock.notifyAll();
    }
  }

  public static class PriorityTooLowException extends IOException {
    public PriorityTooLowException(int priority, int highestPriority) {
      super("Priority too low [priority=" + priority + ", highest=" + highestPriority + "]");
    }
  }
}