package com.hazelcast.stabilizer.tests.map;

import com.hazelcast.core.HazelcastInstance;
import com.hazelcast.core.IMap;
import com.hazelcast.core.IdGenerator;
import com.hazelcast.logging.ILogger;
import com.hazelcast.logging.Logger;
import com.hazelcast.stabilizer.tests.AbstractTest;
import com.hazelcast.stabilizer.tests.TestFailureException;
import com.hazelcast.stabilizer.tests.TestRunner;

import java.util.Random;

public class GrowingMapTest extends AbstractTest {

    private final static ILogger log = Logger.getLogger(GrowingMapTest.class);

    private IMap<Long, Long> map;
    private IdGenerator idGenerator;

    //props.
    public int threadCount = 10;
    public int growCount = 10000;
    public boolean usePut = true;
    public boolean useRemove = true;
    public int logFrequency = 10000;
    public boolean removeOnStop = true;
    public boolean readValidation = true;
    public String basename = "map";

    @Override
    public void localSetup() throws Exception {
        HazelcastInstance targetInstance = getTargetInstance();
        idGenerator = targetInstance.getIdGenerator(getTestId() + ":IdGenerator");
        map = targetInstance.getMap(basename + "-" + getTestId());
    }

    @Override
    public void createTestThreads() {
        for (int k = 0; k < threadCount; k++) {
            spawn(new Worker());
        }
    }

    @Override
    public void globalTearDown() throws Exception {
        map.destroy();
    }

    @Override
    public void globalVerify() throws Exception {
        if (removeOnStop && !map.isEmpty()) {
            throw new TestFailureException("Map should be empty, but has size:" + map.size());
        }
    }

    private class Worker implements Runnable {
        @Override
        public void run() {
            long insertIteration = 0;
            long deleteIteration = 0;
            long readIteration = 0;

            long[] keys = new long[growCount];
            long[] values = new long[growCount];

            Random random = new Random();

            while (!stopped()) {
                int keyIndex = -1;
                for (int k = 0; k < growCount; k++) {
                    if (stopped()) {
                        break;
                    }

                    long key = idGenerator.newId();
                    long value = random.nextLong();
                    keyIndex = k;
                    keys[keyIndex] = key;
                    values[keyIndex] = value;

                    if (usePut) {
                        map.put(key, value);
                    } else {
                        map.set(key, value);
                    }

                    insertIteration++;
                    if (insertIteration % logFrequency == 0) {
                        log.info(Thread.currentThread().getName() + " At insert iteration: " + insertIteration);
                    }
                }

                if (readValidation) {
                    for (int k = 0; k <= keyIndex; k++) {
                        if (stopped()) {
                            break;
                        }

                        long key = keys[k];
                        long value = values[k];

                        long found = map.get(key);
                        if (found != value) {
                            throw new RuntimeException("Unexpected value found");
                        }

                        readIteration++;
                        if (readIteration % logFrequency == 0) {
                            log.info(Thread.currentThread().getName() + " At read iteration: " + readIteration);
                        }
                    }
                }

                for (int k = 0; k <= keyIndex; k++) {
                    if (stopped() && !removeOnStop) {
                        break;
                    }

                    long key = keys[k];
                    long value = values[k];

                    if (useRemove) {
                        long found = map.remove(key);
                        if (found != value) {
                            throw new RuntimeException("Unexpected value found");
                        }
                    } else {
                        map.delete(key);
                    }

                    deleteIteration++;
                    if (deleteIteration % logFrequency == 0) {
                        log.info(Thread.currentThread().getName() + " At delete iteration: " + deleteIteration);
                    }
                }
            }
        }
    }

    public static void main(String[] args) throws Exception {
        GrowingMapTest test = new GrowingMapTest();
        new TestRunner().run(test, 20);
    }
}
