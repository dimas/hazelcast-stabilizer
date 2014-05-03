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

package com.hazelcast.stabilizer.tests.buddy.pounder;

import com.hazelcast.cache.HazelcastCacheManager;
import com.hazelcast.cache.HazelcastCachingProvider;
import com.hazelcast.cache.ICache;
import com.hazelcast.config.CacheConfig;
import com.hazelcast.config.Config;
import com.hazelcast.config.EvictionPolicy;
import com.hazelcast.config.InMemoryFormat;
import com.hazelcast.config.NearCacheConfig;
import com.hazelcast.config.OffHeapMemoryConfig;
import com.hazelcast.config.SerializationConfig;
import com.hazelcast.core.Hazelcast;
import com.hazelcast.core.HazelcastInstance;
import com.hazelcast.logging.ILogger;
import com.hazelcast.logging.Logger;
import com.hazelcast.memory.MemorySize;
import com.hazelcast.stabilizer.tests.AbstractTest;
import com.hazelcast.stabilizer.tests.TestRunner;
import com.hazelcast.stabilizer.tests.buddy.pounder.model.Result;
import com.hazelcast.stabilizer.tests.buddy.pounder.model.StorageType;
import com.hazelcast.stabilizer.worker.ExceptionReporter;

import javax.cache.CacheManager;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

public class PounderTest extends AbstractTest {

    private final static ILogger log = Logger.getLogger(PounderTest.class);

    private final AtomicBoolean warmup = new AtomicBoolean(true);
    private final AtomicLong maxBatchTimeMillis = new AtomicLong(0);
    private final AtomicLong maxGetTimeMillis = new AtomicLong(0);

    private ExecutorService executorService;
    private HazelcastInstance hazelcastInstance;
    private int entryCountPerThread;
    private Result result;
    private ICache<String, byte[]> cache;

    //properties
    public StorageType storageType = StorageType.OFFHEAP;
    public int threadCount = 20;
    public int entryCount = 100000;
    public String offheapSize = "2G";
    public int maxNearCacheCount = 10;
    public int batchCount = 100000;
    public long maxValueSize = 10000;
    public long minValueSize = 1000;
    public int hotSetPercentage = 10;
    public int updatePercentage = 40;
    public int logFrequency = 10000;

    @Override
    public void localSetup() throws Exception {
        hazelcastInstance = getTargetInstance();
        CacheManager cm = new HazelcastCachingProvider(hazelcastInstance).getCacheManager();
        HazelcastCacheManager cacheManager = cm.unwrap(HazelcastCacheManager.class);
        this.cache = cacheManager.getCache("default");
        this.result = new Result(storageType);
        this.entryCountPerThread = entryCount / threadCount;
        this.executorService = Executors.newFixedThreadPool(threadCount);
    }

    @Override
    public void localTearDown() throws Exception {
        result.setMaxGetTimeMillis(maxGetTimeMillis.get());
        result.writeToDisk();
    }

    @Override
    public void createTestThreads() {
        spawn(new Worker());
    }

    public class Worker implements Runnable {

        @Override
        public void run() {
            log("Start => threadcount: %s, entrycount: %s, maxValueSize: %s", threadCount,
                    entryCount, maxValueSize);

            try {
                int iteration = 0;
                while (!stopped()) {
                    long elapsedTime = performOperation(iteration, warmup.get());
                    int tps = (int) (entryCount / (elapsedTime / 1000d));
                    writeRoundData(iteration, cache.size(), elapsedTime, tps);

                    if (warmup.compareAndSet(true, false)) {
                        maxBatchTimeMillis.set(0);
                    }

                    if (iteration % logFrequency == 0) {
                        log.info(Thread.currentThread().getName() + " At iteration: " + iteration);
                    }
                    iteration++;
                }
            } catch (RuntimeException e) {
                throw e;
            } catch (Exception e) {
                throw new RuntimeException(e);
            } finally {
                executorService.shutdown();
                hazelcastInstance.shutdown();
            }
        }
    }

    ICache<String, byte[]> getCache() {
        return cache;
    }

    void writeBatchData(int taskId, int round, boolean warmup, int length, int readCount, //
                        int writeCount, int currentSize, long batchTimeMillis, long maxBatchTimeMillis) {

        updateMaxBatchTimeMillis(maxBatchTimeMillis);

        String temp = warmup ? "warmup" : String.valueOf(this.maxBatchTimeMillis.get());
        log("Batch (%s-%s) => size: %s, batch time: %s, max batch time: %s, value length: %s, read: %s, write: %s", //
                taskId, round, currentSize, batchTimeMillis, temp, length, readCount, writeCount);

        result.addBatch(taskId, round, warmup, length, readCount, writeCount, currentSize, batchTimeMillis, maxBatchTimeMillis);
    }

    private long performOperation(int round, boolean warmup) throws Exception {

        long startTime = System.nanoTime();

        List<Callable<long[]>> tasks = new ArrayList<>();
        for (int i = 0; i < threadCount; i++) {
            int startIndex = entryCountPerThread * i;
            int endIndex = entryCountPerThread * (i + 1);
            tasks.add(new PounterLoadTask(round, warmup, i, startIndex, endIndex, this));
        }

        List<Future<long[]>> futures = executorService.invokeAll(tasks);
        for (Future<long[]> future : futures) {
            long[] timings = future.get();
            updateMaxBatchTimeMillis(timings[0]);
            updateMaxGetTimeMillis(timings[1]);
        }

        long endTime = System.nanoTime();
        return TimeUnit.NANOSECONDS.toMillis(endTime - startTime);
    }

    private void updateMaxBatchTimeMillis(long maxBatchTimeMillis) {
        for (; ; ) {
            long temp = this.maxBatchTimeMillis.get();
            if (maxBatchTimeMillis > temp) {
                if (this.maxBatchTimeMillis.compareAndSet(temp, maxBatchTimeMillis)) {
                    break;
                }
            } else {
                break;
            }
        }
    }

    private void updateMaxGetTimeMillis(long maxGetTimeMillis) {
        for (; ; ) {
            long temp = this.maxGetTimeMillis.get();
            if (maxGetTimeMillis > temp) {
                if (this.maxGetTimeMillis.compareAndSet(temp, maxGetTimeMillis)) {
                    break;
                }
            } else {
                break;
            }
        }
    }

    private void writeRoundData(int i, int size, long elapsedTime, int tps) {
        result.addRound(elapsedTime, size, tps);
        log("Round #%s => size: %s, elapsedTime: %s, tps: %s", i, size, elapsedTime, tps);
    }

    public static void log(String message, Object... params) {
        String msg = String.format(message, params);
        log.info(msg);
    }

    public class PounterLoadTask implements Callable<long[]> {

        private final Random random = new Random();

        private final PounderTest pounderTest;
        private final ICache<String, byte[]> cache;

        private final int round;
        private final boolean warmup;
        private final int taskId;
        private final long startIndex;
        private final long endIndex;

        private long maxBatchTimeNanos = 0;
        private long maxGetTimeNanos = 0;

        byte[] value;
        int readCount = 0;
        int writeCount = 0;
        int currentSize = 1;

        public PounterLoadTask(int round, boolean warmup, int taskId, long startIndex, //
                               long endIndex, PounderTest pounderTest) {

            this.round = round;
            this.warmup = warmup;
            this.taskId = taskId;
            this.startIndex = startIndex;
            this.endIndex = endIndex;
            this.cache = pounderTest.getCache();
            this.pounderTest = pounderTest;
        }

        @Override
        public long[] call()
                throws Exception {

            try {

                value = buildValue();
                long startTime = System.nanoTime();

                for (long i = startIndex; i < endIndex; i++) {
                    if ((i + 1) % batchCount == 0) {
                        logAndResetValues(startTime);
                        startTime = System.nanoTime();
                    }

                    boolean write = isWrite();
                    if (warmup || write) {
                        String key = createKeyFromCount(i);
                        cache.put(key, value.clone());
                        writeCount++;
                    }

                    if (!warmup && !write) {
                        long getTime = 0;
                        if (readCount % threadCount == 0) {
                            getTime = System.nanoTime();
                        }

                        long randomIndex = pickNextReadNumber(i, currentSize);
                        readEntry(createKeyFromCount(randomIndex));
                        if (getTime > 0) {
                            long temp = System.nanoTime() - getTime;
                            if (temp > maxGetTimeNanos) {
                                maxGetTimeNanos = temp;
                            }
                        }
                        readCount++;
                    }
                }

                long maxBatchTimeMillis = TimeUnit.NANOSECONDS.toMillis(maxBatchTimeNanos);
                long maxGetTimeMillis = TimeUnit.NANOSECONDS.toMillis(maxGetTimeNanos);
                return new long[]{maxBatchTimeMillis, maxGetTimeMillis};
            } catch (Throwable t) {
                ExceptionReporter.report(t);
                throw t;
            }
        }

        private long pickNextReadNumber(long i, int currentSize) {
            if ((i % 100) < hotSetPercentage) {
                return random.nextInt(maxNearCacheCount);
            }
            return random.nextInt(currentSize);
        }

        private boolean isWrite() {
            return random.nextInt(100) < updatePercentage;
        }

        private String createKeyFromCount(long i) {
            return "Key-" + i;
        }

        private void readEntry(String key) {
            byte[] value = cache.get(key);
            validateValue(value);
        }

        private void validateValue(byte[] value) {
            //log("taskId: %s, length: %s", taskId, value.length);
            for (int i = 0; i < 5; i++) {
                if (i != value[i]) {
                    log("First expected: %s but got %s", i, value[i]);
                    throw new RuntimeException("Invalid value");
                }
            }
            for (int i = 1; i < 5; i++) {
                if (i != value[value.length - i]) {
                    log("Last expected: %s but got %s", i, value[value.length - i]);
                    throw new RuntimeException("Invalid value");
                }
            }
        }

        private void logAndResetValues(long startTime) {
            long batchTimeNanos = System.nanoTime() - startTime;
            if (batchTimeNanos > maxBatchTimeNanos) {
                maxBatchTimeNanos = batchTimeNanos;
            }

            currentSize = cache.size();

            long maxBatchTimeMillis = TimeUnit.NANOSECONDS.toMillis(maxBatchTimeNanos);
            long batchTimeMillis = TimeUnit.NANOSECONDS.toMillis(batchTimeNanos);
            pounderTest.writeBatchData(taskId, round, warmup, value.length, //
                    readCount, writeCount, currentSize, batchTimeMillis, maxBatchTimeMillis);

            value = buildValue();
            readCount = 0;
            writeCount = 0;
        }

        private byte[] buildValue() {
            int randomizer = (int) (maxValueSize - minValueSize);
            int size = (int) (random.nextInt(randomizer) + minValueSize) + 10;
            byte[] value = new byte[size];
            for (int i = 0; i < size; i++) {
                if (i < 5) {
                    value[i] = (byte) i;
                } else if ((value.length - i) < 5) {
                    value[i] = (byte) (value.length - i);
                } else {
                    value[i] = (byte) random.nextInt(255);
                }
            }
            return value;
        }
    }

    private static HazelcastInstance buildHazelcastInstance(PounderTest configuration) {
        Config config = new Config();

        SerializationConfig serializationConfig = config.getSerializationConfig();
        serializationConfig.setAllowUnsafe(true).setUseNativeByteOrder(true);

        CacheConfig cacheConfig = new CacheConfig("default");
        cacheConfig.setBackupCount(0).setAsyncBackupCount(1);
        cacheConfig.setEvictionPercentage(10).setEvictionThresholdPercentage(95).setEvictionPolicy(EvictionPolicy.LRU);

        config.addCacheConfig(cacheConfig);

        if (configuration.maxNearCacheCount > 0) {
            NearCacheConfig nearCacheConfig = new NearCacheConfig();
            nearCacheConfig.setInMemoryFormat(InMemoryFormat.BINARY);
            nearCacheConfig.setCacheLocalEntries(true);
            nearCacheConfig.setMaxSize(configuration.maxNearCacheCount);
            nearCacheConfig.setName("default");
            cacheConfig.setNearCacheConfig(nearCacheConfig);
        }

        MemorySize memorySize = MemorySize.parse(configuration.offheapSize);
        OffHeapMemoryConfig offheapConfig = new OffHeapMemoryConfig().setSize(memorySize).setEnabled(true);
        offheapConfig.setAllocatorType(OffHeapMemoryConfig.MemoryAllocatorType.POOLED);
        config.setOffHeapMemoryConfig(offheapConfig);

        return Hazelcast.newHazelcastInstance(config);
    }

    public static void main(String[] args) throws Exception {
        PounderTest test = new PounderTest();
        HazelcastInstance hz = buildHazelcastInstance(test);
        TestRunner testRunner = new TestRunner();
        testRunner.setHazelcastInstance(hz);
        testRunner.run(test, 600);
    }
}
