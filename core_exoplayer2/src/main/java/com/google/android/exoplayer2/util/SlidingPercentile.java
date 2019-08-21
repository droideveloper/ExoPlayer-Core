package com.google.android.exoplayer2.util;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;

public class SlidingPercentile {
  private static final Comparator<SlidingPercentile.Sample> INDEX_COMPARATOR = (a, b) -> a.index - b.index;
  private static final Comparator<SlidingPercentile.Sample> VALUE_COMPARATOR = (a, b) -> Float.compare(a.value, b.value);
  private static final int SORT_ORDER_NONE = -1;
  private static final int SORT_ORDER_BY_VALUE = 0;
  private static final int SORT_ORDER_BY_INDEX = 1;
  private static final int MAX_RECYCLED_SAMPLES = 5;
  private final int maxWeight;
  private final ArrayList<SlidingPercentile.Sample> samples;
  private final SlidingPercentile.Sample[] recycledSamples;
  private int currentSortOrder;
  private int nextSampleIndex;
  private int totalWeight;
  private int recycledSampleCount;

  public SlidingPercentile(int maxWeight) {
    this.maxWeight = maxWeight;
    this.recycledSamples = new SlidingPercentile.Sample[5];
    this.samples = new ArrayList<>();
    this.currentSortOrder = -1;
  }

  public void addSample(int weight, float value) {
    this.ensureSortedByIndex();
    SlidingPercentile.Sample newSample = this.recycledSampleCount > 0 ? this.recycledSamples[--this.recycledSampleCount] : new SlidingPercentile.Sample();
    newSample.index = this.nextSampleIndex++;
    newSample.weight = weight;
    newSample.value = value;
    this.samples.add(newSample);
    this.totalWeight += weight;

    while(this.totalWeight > this.maxWeight) {
      int excessWeight = this.totalWeight - this.maxWeight;
      SlidingPercentile.Sample oldestSample = (SlidingPercentile.Sample)this.samples.get(0);
      if (oldestSample.weight <= excessWeight) {
        this.totalWeight -= oldestSample.weight;
        this.samples.remove(0);
        if (this.recycledSampleCount < 5) {
          this.recycledSamples[this.recycledSampleCount++] = oldestSample;
        }
      } else {
        oldestSample.weight -= excessWeight;
        this.totalWeight -= excessWeight;
      }
    }

  }

  public float getPercentile(float percentile) {
    this.ensureSortedByValue();
    float desiredWeight = percentile * (float)this.totalWeight;
    int accumulatedWeight = 0;

    for(int i = 0; i < this.samples.size(); ++i) {
      SlidingPercentile.Sample currentSample = (SlidingPercentile.Sample)this.samples.get(i);
      accumulatedWeight += currentSample.weight;
      if ((float)accumulatedWeight >= desiredWeight) {
        return currentSample.value;
      }
    }

    return this.samples.isEmpty() ? 0.0F / 0.0F : ((SlidingPercentile.Sample)this.samples.get(this.samples.size() - 1)).value;
  }

  private void ensureSortedByIndex() {
    if (this.currentSortOrder != 1) {
      Collections.sort(this.samples, INDEX_COMPARATOR);
      this.currentSortOrder = 1;
    }

  }

  private void ensureSortedByValue() {
    if (this.currentSortOrder != 0) {
      Collections.sort(this.samples, VALUE_COMPARATOR);
      this.currentSortOrder = 0;
    }

  }

  private static class Sample {
    public int index;
    public int weight;
    public float value;

    private Sample() {
    }
  }
}

