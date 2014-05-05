package com.hazelcast.stabilizer.tests.buddy;


import com.hazelcast.cache.HazelcastCacheManager;
import com.hazelcast.cache.HazelcastCachingProvider;
import com.hazelcast.cache.ICache;
import com.hazelcast.core.HazelcastInstance;
import com.hazelcast.elastic_test.ByteArrayValueFactory;
import com.hazelcast.elastic_test.MemoryStatsUtil;
import com.hazelcast.elastic_test.SampleValueFactory;
import com.hazelcast.elastic_test.ValueFactory;
import com.hazelcast.logging.ILogger;
import com.hazelcast.logging.Logger;
import com.hazelcast.memory.MemoryStats;
import com.hazelcast.memory.error.OffHeapOutOfMemoryError;
import com.hazelcast.stabilizer.tests.AbstractTest;
import com.hazelcast.stabilizer.tests.TestRunner;
import com.hazelcast.util.Counter;
import org.HdrHistogram.AbstractHistogram;
import org.HdrHistogram.AtomicHistogram;
import org.HdrHistogram.Histogram;
import org.HdrHistogram.HistogramData;

import javax.cache.CacheManager;
import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.io.UnsupportedEncodingException;
import java.util.Random;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;

import static com.hazelcast.stabilizer.Utils.sleepSeconds;

public class SimpleCacheTest extends AbstractTest {
    private final static ILogger log = Logger.getLogger(SimpleCacheTest.class);

    private static final String NAMESPACE = "default";

    private MemoryStats memoryStats;

    private HazelcastInstance instance;
    private HazelcastCacheManager cacheManager;
    private Stats[] allStats;
    private String value;

    //properties.
    public int statsSeconds = 60;
    public int threadCount = 40;
    public int entryCount = 10 * 1000;
    public int getPercentage = 40;
    public int putPercentage = 40;
    public int valueSize = 1024;
    public int logFrequency = 10000;
    public int performanceUpdateFrequency = 10000;

    @Override
    public void localSetup() throws Exception {
        instance = getTargetInstance();

        this.allStats = new Stats[threadCount];
        for (int i = 0; i < threadCount; i++) {
            allStats[i] = new Stats();
        }

        memoryStats = MemoryStatsUtil.getMemoryStats(instance);
        CacheManager cm = new HazelcastCachingProvider(instance).getCacheManager();
        cacheManager = cm.unwrap(HazelcastCacheManager.class);

        byte[] data = new byte[valueSize];
        new Random().nextBytes(data);
        this.value = new String(data);
    }

    @Override
    public void createTestThreads() {
        final ICache<Integer, Object> cache = cacheManager.getCache(NAMESPACE);
        for (int k = 0; k < threadCount; k++) {
            spawn(new Worker(k, cache));
        }

        new PrintStatsThread().start();
    }

    private static class Stats {
        final AbstractHistogram histogram = new AtomicHistogram(1, TimeUnit.MINUTES.toNanos(1), 3);
        final Counter gets = new Counter();
        final Counter puts = new Counter();
        final Counter removes = new Counter();
    }

    private class Worker implements Runnable {
        private final int tid;
        private final ICache<Integer, Object> cache;
        private final Random rand;

        public Worker(int tid, ICache<Integer, Object> cache) {
            this.tid = tid;
            this.cache = cache;
            this.rand = ThreadLocalRandom.current();
        }

        @Override
        public void run() {
            Stats stats = allStats[tid];
            long iteration = 0;
            while (!stopped()) {
                int key = rand.nextInt();
                int operation = rand.nextInt(100);
                long start = System.nanoTime();
                if (operation < getPercentage) {
                    cache.get(key);
                    stats.gets.increment();
                } else if (operation < getPercentage + putPercentage) {
                    try {
                        cache.put(key, value);
                    } catch (OffHeapOutOfMemoryError e) {
//                                        int size = cache.size();
//                                        cache.clear();
//                                        System.out.println("SIZE SIZE SIZE = " + size);
                    }
                    stats.puts.increment();
                } else {
                    cache.remove(key);
                    stats.removes.increment();
                }
                long end = System.nanoTime();

                try {
                    stats.histogram.recordValue(end - start);
                } catch (IndexOutOfBoundsException ignored) {
                }

                iteration++;

                if (iteration % logFrequency == 0) {
                    log.info(Thread.currentThread().getName() + " At iteration: " + iteration);
                }
            }
        }
    }

    public static void main(String[] args) throws Exception {
        SimpleCacheTest test = new SimpleCacheTest();
        new TestRunner().run(test, 60);
        System.exit(0);
    }

    private class PrintStatsThread extends Thread {
        final AbstractHistogram totalHistogram = new Histogram(1, TimeUnit.MINUTES.toNanos(1), 3);

        public PrintStatsThread() {
            setDaemon(true);
            setName("PrintStats." + instance.getName());
        }

        public void run() {

            while (!stopped()) {
                long start = System.currentTimeMillis();
                sleepSeconds(statsSeconds);
                long end = System.currentTimeMillis();
                long interval = end - start;

                totalHistogram.reset();
                long getsNow = 0;
                long putsNow = 0;
                long removesNow = 0;

                for (int i = 0; i < threadCount; i++) {
                    Stats stats = allStats[i];
                    getsNow += stats.gets.get();
                    stats.gets.set(0);
                    putsNow += stats.puts.get();
                    stats.puts.set(0);
                    removesNow += stats.removes.get();
                    stats.removes.set(0);

                    totalHistogram.add(stats.histogram);
                    stats.histogram.reset();
                }

                printHistogram();

                long totalOps = getsNow + putsNow + removesNow;
                log.info(
                        "total-ops= " + (totalOps * 1000 / interval) + ", gets:" + (getsNow * 1000 / interval) + ", puts:"
                                + (putsNow * 1000 / interval) + ", removes:" + (removesNow * 1000 / interval)
                );

                if (memoryStats != null) {
                    System.out.println(memoryStats);
                }
            }
        }

        private void printHistogram() {
            HistogramData data = totalHistogram.getHistogramData();
            totalHistogram.reestablishTotalCount();

            ByteArrayOutputStream os = new ByteArrayOutputStream();
            PrintStream ps = new PrintStream(os);
            data.outputPercentileDistribution(ps, 1, 1000d);
            try {
                String output = os.toString("UTF8");
                log.info(output);
            } catch (UnsupportedEncodingException e) {
                log.severe(e);
            }
        }
    }
}