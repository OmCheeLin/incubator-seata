/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.seata.common;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.fail;

public class FlakyTest {

    @Test
    public void testSimulateFlaky() {
        String flag = System.getenv("I_RUN_ATTEMPT");
        System.out.println("I_RUN_ATTEMPT env var value: " + flag);

        if ("1".equals(flag)) {
            System.out.println("Simulating flaky test failure due to I_RUN_ATTEMPT=1");
            fail("This test is intentionally failed on first run.");
        } else {
            System.out.println("I_RUN_ATTEMPT not 1, test will pass.");
        }
    }
}
