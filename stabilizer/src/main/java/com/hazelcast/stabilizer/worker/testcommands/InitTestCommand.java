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
package com.hazelcast.stabilizer.worker.testcommands;

import com.hazelcast.stabilizer.TestCase;

public class InitTestCommand extends TestCommand {

    public static final long serialVersionUID = 0l;

    public TestCase testCase;

    public InitTestCommand(TestCase testCase) {
        this.testCase = testCase;
    }

    @Override
    public String toString() {
        return "InitTestCommand{" +
                "testRecipe=" + testCase +
                '}';
    }
}

