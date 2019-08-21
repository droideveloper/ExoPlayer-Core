package com.google.android.exoplayer2.util;

import android.os.Handler;
import java.util.Iterator;
import java.util.concurrent.CopyOnWriteArrayList;

public final class EventDispatcher<T> {
  private final CopyOnWriteArrayList<EventDispatcher.HandlerAndListener<T>> listeners = new CopyOnWriteArrayList<>();

  public EventDispatcher() {
  }

  public void addListener(Handler handler, T eventListener) {
    Assertions.checkArgument(handler != null && eventListener != null);
    this.removeListener(eventListener);
    this.listeners.add(new EventDispatcher.HandlerAndListener<>(handler, eventListener));
  }

  public void removeListener(T eventListener) {
    Iterator var2 = this.listeners.iterator();

    while(var2.hasNext()) {
      EventDispatcher.HandlerAndListener<T> handlerAndListener = (EventDispatcher.HandlerAndListener<T>)var2.next();
      if (handlerAndListener.listener == eventListener) {
        handlerAndListener.release();
        this.listeners.remove(handlerAndListener);
      }
    }

  }

  public void dispatch(EventDispatcher.Event<T> event) {
    Iterator var2 = this.listeners.iterator();

    while(var2.hasNext()) {
      EventDispatcher.HandlerAndListener<T> handlerAndListener = (EventDispatcher.HandlerAndListener<T>)var2.next();
      handlerAndListener.dispatch(event);
    }

  }

  private static final class HandlerAndListener<T> {
    private final Handler handler;
    private final T listener;
    private boolean released;

    public HandlerAndListener(Handler handler, T eventListener) {
      this.handler = handler;
      this.listener = eventListener;
    }

    public void release() {
      this.released = true;
    }

    public void dispatch(EventDispatcher.Event<T> event) {
      this.handler.post(() -> {
        if (!this.released) {
          event.sendTo(this.listener);
        }

      });
    }
  }

  public interface Event<T> {
    void sendTo(T var1);
  }
}
