package org.apache.seata.spring.tcc;

import org.apache.seata.integration.tx.api.util.ProxyUtil;
import org.apache.seata.rm.tcc.api.TwoPhaseBusinessAction;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.mockito.Mockito;

import java.lang.annotation.Annotation;
import java.lang.reflect.Field;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class TccAnnotationProcessorTest {

    private TccAnnotationProcessor processor;

    @BeforeEach
    public void setUp() {
        processor = new TccAnnotationProcessor();
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
    }

    @Test
    public void testAddTccAdviseCreatesProxy() throws Exception {
        TestBean bean = new TestBean();
        Field field = TestBean.class.getField("tccService");

        Object originalValue = field.get(bean);

        try (MockedStatic<ProxyUtil> mockedStatic = Mockito.mockStatic(ProxyUtil.class)) {
            mockedStatic.when(() -> ProxyUtil.createProxy(new MockTccService(), "testBean"))
                    .thenAnswer(invocation -> {
                        Object arg = invocation.getArgument(0);
                        if (arg instanceof MockTccService) {
                            return Mockito.spy((MockTccService) arg);
                        }
                        return arg;
                    });

            processor.addTccAdvise(bean, "testBean", field, MockTccService.class);
        }

        Object newValue = field.get(bean);

        assertNotEquals(originalValue, newValue, "The proxy object should replace the original field object");
    }

    @Test
    public void testProcessFieldWithAnnotation() throws Exception {
        Field annotationsField = TccAnnotationProcessor.class.getDeclaredField("ANNOTATIONS");
        annotationsField.setAccessible(true);
        @SuppressWarnings("unchecked")
        java.util.List<Class<? extends Annotation>> annotations =
                (java.util.List<Class<? extends Annotation>>) annotationsField.get(null);
        annotations.add(MockReference.class);

        TestBean bean = new TestBean();
        processor.postProcessBeforeInitialization(bean, "testBean");

        Field proxiedField = TccAnnotationProcessor.class.getDeclaredField("PROXIED_SET");
        proxiedField.setAccessible(true);
        Set<String> proxied = (Set<String>) proxiedField.get(null);

        assertTrue(proxied.contains("testBean"));
    }

}
