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


import com.hazelcast.stabilizer.Utils;
import com.hazelcast.stabilizer.tests.buddy.pounder.PounderTest;
import org.apache.log4j.Logger;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

public class Result {
    private static final Logger LOGGER = Logger.getLogger(Result.class);

    private final List<Round> rounds = new CopyOnWriteArrayList<Round>();
    private final List<Batch> batches = new CopyOnWriteArrayList<Batch>();

    private final StorageType storageType;

    private long maxGetTimeMillis;

    public Result(StorageType storageType) {
        this.storageType = storageType;
    }

    public void addRound(long elapsedTime, int finalSize, int tps) {
        rounds.add(new Round(elapsedTime, finalSize, tps));
    }

    public void addBatch(int taskId, int round, boolean warmup, int length, int readCount, //
                         int writeCount, int currentSize, long batchTimeMillis, long maxBatchTimeMillis) {

        batches.add(new Batch(taskId, round, warmup, length, readCount, writeCount, currentSize, batchTimeMillis,
                maxBatchTimeMillis));
    }

    public void setMaxGetTimeMillis(long maxGetTimeMillis) {
        this.maxGetTimeMillis = maxGetTimeMillis;
    }

    public void writeToDisk() {
        FileWriter roundsWriter = null;
        FileWriter batchesWriter = null;

        try {
            roundsWriter = new FileWriter(new File("rounds.csv"));
            batchesWriter = new FileWriter(new File("batches.csv"));

            long sumTps = 0;
            long sumElapsedTime = 0;

            int roundNb = 0;
            for (Round round : rounds) {
                int finalSize = round.getFinalSize();
                long maxGetTime = round.getElapsedTime();
                int tps = round.getTps();

                String entry = join(roundNb++, finalSize, maxGetTime, tps);
                roundsWriter.write(entry);

                if (roundNb > 0) {
                    sumTps += tps;
                    sumElapsedTime += maxGetTime;
                }
            }

            float avgTps = ((float) sumTps) / (rounds.size() - 1);
            PounderTest.log("Total: storageType: %s, totalTime: %s, avg tps: %s, maxGetTime: %s", //
                    storageType, sumElapsedTime, avgTps, maxGetTimeMillis);

            for (Batch batch : batches) {
                int taskId = batch.getTaskId();
                int round = batch.getRound();
                int length = batch.getLength();
                boolean warmup = batch.isWarmup();
                int currentSize = batch.getCurrentSize();
                long batchTimeMillis = batch.getBatchTimeMillis();
                long maxBatchTimeMillis = batch.getMaxBatchTimeMillis();
                int readCount = batch.getReadCount();
                int writeCount = batch.getWriteCount();

                String entry = join(taskId, round, length, warmup, currentSize, batchTimeMillis, //
                        maxBatchTimeMillis, readCount, writeCount);

                batchesWriter.write(entry);
            }
        } catch (IOException e) {
            LOGGER.warn(e);
        } finally {
            Utils.closeQuietly(roundsWriter);
            Utils.closeQuietly(batchesWriter);
        }
    }

    private String join(Object... values) {
        StringBuilder sb = new StringBuilder();
        for (Object value : values) {
            sb.append(value).append(',');
        }
        return sb.deleteCharAt(sb.length() - 1).toString();
    }
}
