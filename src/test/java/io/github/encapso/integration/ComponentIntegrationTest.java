package io.github.encapso.integration;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;

class ComponentIntegrationTest {

    @Test
    void testCompiledComponentFacadeInjectionAndExecution() {
        // 1. Manually instantiate our internal implementation logic
        MyTarget rawTarget = new MyTarget();

        // 2. Instantiate the generated JavaPoet facade using Constructor Injection!
        // (This class is built natively by `javac` dynamically during the `test-compile` phase!)
        MyFacade facadeBridge = new MyFacadeImpl(rawTarget);

        // 3. Execute the public Component interface method
        int result = facadeBridge.execute(10);

        // 4. Assert that the underlying Delegate logic explicitly ran (10 * 3 = 30)
        assertEquals(30, result);
    }
}
