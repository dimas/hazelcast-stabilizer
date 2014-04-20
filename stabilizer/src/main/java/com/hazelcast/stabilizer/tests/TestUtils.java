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
package com.hazelcast.stabilizer.tests;

import java.io.File;
import java.util.concurrent.atomic.AtomicLong;

import static com.hazelcast.stabilizer.Utils.writeObject;

public class TestUtils {
    public final static AtomicLong FAILURE_ID = new AtomicLong(1);

    public static String getWorkerId() {
        return System.getProperty("workerId");
    }

    public static void signalFailure(Throwable cause) {
        final File file = new File(getWorkerId() + "." + FAILURE_ID.incrementAndGet() + ".exception");
        writeObject(cause, file);
    }

    private TestUtils() {
    }
}