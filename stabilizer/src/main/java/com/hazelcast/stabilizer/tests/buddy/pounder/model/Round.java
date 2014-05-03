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

public class Round {

    private final long elapsedTime;
    private final int finalSize;
    private final int tps;

    public Round(long elapsedTime, int finalSize, int tps) {
        this.elapsedTime = elapsedTime;
        this.finalSize = finalSize;
        this.tps = tps;
    }

    public long getElapsedTime() {
        return elapsedTime;
    }

    public int getFinalSize() {
        return finalSize;
    }

    public int getTps() {
        return tps;
    }
}
