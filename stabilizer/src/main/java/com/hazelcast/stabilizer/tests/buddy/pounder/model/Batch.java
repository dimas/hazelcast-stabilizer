/*
 * Copyright (c) 2008-2013, Hazelcast, Inc. All Rights Reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.hazelcast.stabilizer.tests.buddy.pounder.model;

public class Batch {

    private final int taskId;
    private final int round;
    private final boolean warmup;
    private final int length;
    private final int readCount;
    private final int writeCount;
    private final int currentSize;
    private final long batchTimeMillis;
    private final long maxBatchTimeMillis;

    public Batch(int taskId, int round, boolean warmup, int length, int readCount, int writeCount, int currentSize,
                 long batchTimeMillis, long maxBatchTimeMillis) {

        this.taskId = taskId;
        this.round = round;
        this.warmup = warmup;
        this.length = length;
        this.readCount = readCount;
        this.writeCount = writeCount;
        this.currentSize = currentSize;
        this.batchTimeMillis = batchTimeMillis;
        this.maxBatchTimeMillis = maxBatchTimeMillis;
    }

    public int getTaskId() {
        return taskId;
    }

    public int getRound() {
        return round;
    }

    public boolean isWarmup() {
        return warmup;
    }

    public int getLength() {
        return length;
    }

    public int getReadCount() {
        return readCount;
    }

    public int getWriteCount() {
        return writeCount;
    }

    public int getCurrentSize() {
        return currentSize;
    }

    public long getBatchTimeMillis() {
        return batchTimeMillis;
    }

    public long getMaxBatchTimeMillis() {
        return maxBatchTimeMillis;
    }
}
