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
package org.apache.seata.spring.tcc;

import org.apache.seata.integration.tx.api.util.ProxyUtil;
import org.apache.seata.rm.tcc.api.TwoPhaseBusinessAction;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.mockito.Mockito;

import java.lang.annotation.Annotation;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import static org.junit.jupiter.api.Assertions.*;

public class TccAnnotationProcessorTest {

    private TccAnnotationProcessor processor;

    @BeforeEach
    public void setUp() {
        processor = new TccAnnotationProcessor();
        clearStaticFields();
    }

    private void clearStaticFields() {
        try {
            Field proxiedField = TccAnnotationProcessor.class.getDeclaredField("PROXIED_SET");
            proxiedField.setAccessible(true);
            ((Set<String>) proxiedField.get(null)).clear();

            Field annotationsField = TccAnnotationProcessor.class.getDeclaredField("ANNOTATIONS");
            annotationsField.setAccessible(true);
            ((java.util.List<Class<? extends Annotation>>) annotationsField.get(null)).clear();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @interface MockReference {
    }

    static class MockTccService {
        @TwoPhaseBusinessAction(name = "testAction")
        public void tryMethod() {
        }
    }

    static class TestBean {
        @MockReference
        public MockTccService tccService = new MockTccService();

        @MockReference
        public MockTccService nullService = null;
    }

    @Test
    public void testAddTccAdviseCreatesProxy() throws Exception {
        TestBean bean = new TestBean();
        Field field = TestBean.class.getField("tccService");

        Object originalValue = field.get(bean);

        try (MockedStatic<ProxyUtil> mockedStatic = Mockito.mockStatic(ProxyUtil.class)) {
            mockedStatic.when(() -> ProxyUtil.createProxy(bean, "testBean"))
                    .thenAnswer(invocation -> {
                        Object arg = invocation.getArgument(0);
                        if (arg instanceof TestBean) {
                            return Mockito.spy(new MockTccService());
                        }
                        return arg;
                    });

            processor.addTccAdvise(bean, "testBean", field, MockTccService.class);
        }

        Object newValue = field.get(bean);
        assertNotEquals(originalValue, newValue, "Proxy should replace original field");
    }

    @Test
    public void testAddTccAdviseFieldValueNull() throws Exception {
        TestBean bean = new TestBean();
        Field nullField = TestBean.class.getField("nullService");
        // 应该安全返回，不抛异常
        processor.addTccAdvise(bean, "testBean", nullField, MockTccService.class);
        assertNull(nullField.get(bean));
    }

    @Test
    public void testProcessWithNullAnnotation() throws Exception {
        processor.process(new TestBean(), "testBean", null);
        Field proxiedField = TccAnnotationProcessor.class.getDeclaredField("PROXIED_SET");
        proxiedField.setAccessible(true);
        Set<String> proxied = (Set<String>) proxiedField.get(null);
        assertTrue(proxied.isEmpty());
    }

    @Test
    public void testProcessWhenAlreadyProxied() throws Exception {
        Field proxiedField = TccAnnotationProcessor.class.getDeclaredField("PROXIED_SET");
        proxiedField.setAccessible(true);
        Set<String> proxied = (Set<String>) proxiedField.get(null);
        proxied.add("testBean");

        processor.process(new TestBean(), "testBean", MockReference.class);
        assertTrue(proxied.contains("testBean"));
    }

    @Test
    public void testProcessFieldWithAnnotation() throws Exception {
        Field annotationsField = TccAnnotationProcessor.class.getDeclaredField("ANNOTATIONS");
        annotationsField.setAccessible(true);
        ((java.util.List<Class<? extends Annotation>>) annotationsField.get(null)).add(MockReference.class);

        TestBean bean = new TestBean();

        try (MockedStatic<ProxyUtil> mockedStatic = Mockito.mockStatic(ProxyUtil.class)) {
            mockedStatic.when(() -> ProxyUtil.createProxy(bean, "testBean"))
                    .thenReturn(Mockito.spy(new MockTccService()));

            processor.postProcessBeforeInitialization(bean, "testBean");
        }

        Field proxiedField = TccAnnotationProcessor.class.getDeclaredField("PROXIED_SET");
        proxiedField.setAccessible(true);
        Set<String> proxied = (Set<String>) proxiedField.get(null);

        assertTrue(proxied.contains("testBean"));
    }

    @Test
    public void testLoadAnnotationWithReflection() throws Exception {
        Method loadAnnotationMethod = TccAnnotationProcessor.class.getDeclaredMethod("loadAnnotation", String.class);
        loadAnnotationMethod.setAccessible(true);

        Class<?> annotationClass = (Class<?>) loadAnnotationMethod.invoke(null, "java.lang.Override");
        assertNotNull(annotationClass);

        Object nullClass = loadAnnotationMethod.invoke(null, "non.existing.AnnotationClass");
        assertNull(nullClass);
    }


    @Test
    public void testPostProcessAfterInitializationReturnsBean() {
        TestBean bean = new TestBean();
        Object result = processor.postProcessAfterInitialization(bean, "testBean");
        assertSame(bean, result);
    }
}
