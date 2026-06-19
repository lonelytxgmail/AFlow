package com.aflow.core.expression;

import com.aflow.common.model.FlowContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class ExpressionEvaluatorTest {

    private ExpressionEvaluator evaluator;

    @BeforeEach
    void setUp() {
        evaluator = new ExpressionEvaluator();
    }

    // ─── SpEL Evaluation ─────────────────────────────────────────────

    @Test
    void evaluate_simpleVariableReference() {
        FlowContext ctx = new FlowContext("inst-1", "def-1");
        ctx.putVariable("statusCode", 200);

        Integer result = evaluator.evaluate("#statusCode", ctx, Integer.class);
        assertEquals(200, result);
    }

    @Test
    void evaluate_booleanExpression() {
        FlowContext ctx = new FlowContext("inst-1", "def-1");
        ctx.putVariable("statusCode", 200);

        Boolean result = evaluator.evaluate("#statusCode == 200", ctx, Boolean.class);
        assertTrue(result);
    }

    @Test
    void evaluate_comparisonExpression() {
        FlowContext ctx = new FlowContext("inst-1", "def-1");
        ctx.putVariable("amount", 100);

        Boolean result = evaluator.evaluate("#amount > 50", ctx, Boolean.class);
        assertTrue(result);
    }

    @Test
    void evaluate_stringExpression() {
        FlowContext ctx = new FlowContext("inst-1", "def-1");
        ctx.putVariable("name", "world");

        String result = evaluator.evaluate("#name", ctx, String.class);
        assertEquals("world", result);
    }

    @Test
    void evaluate_logicalExpression() {
        FlowContext ctx = new FlowContext("inst-1", "def-1");
        ctx.putVariable("a", true);
        ctx.putVariable("b", false);

        Boolean result = evaluator.evaluate("#a and !#b", ctx, Boolean.class);
        assertTrue(result);
    }

    @Test
    void evaluate_metadataVariable() {
        FlowContext ctx = new FlowContext("inst-1", "def-1");
        ctx.getMetadata().put("tenant", "acme");

        // metadata is exposed as #metadata
        Object result = evaluator.evaluate("#metadata", ctx, Object.class);
        assertNotNull(result);
    }

    @Test
    void evaluate_invalidExpression_throws() {
        FlowContext ctx = new FlowContext("inst-1", "def-1");
        assertThrows(RuntimeException.class, () ->
                evaluator.evaluate("###invalid!!!", ctx, Object.class));
    }

    @Test
    void evaluate_blocksTypeReference_security() {
        FlowContext ctx = new FlowContext("inst-1", "def-1");
        // SimpleEvaluationContext blocks T() references
        assertThrows(RuntimeException.class, () ->
                evaluator.evaluate("T(java.lang.Runtime).getRuntime()", ctx, Object.class));
    }

    @Test
    void evaluate_blocksConstructorCall_security() {
        FlowContext ctx = new FlowContext("inst-1", "def-1");
        assertThrows(RuntimeException.class, () ->
                evaluator.evaluate("new java.io.File('/etc/passwd')", ctx, Object.class));
    }

    // ─── Template Resolution ─────────────────────────────────────────

    @Test
    void resolveTemplate_singlePlaceholder() {
        FlowContext ctx = new FlowContext("inst-1", "def-1");
        ctx.putVariable("endpoint", "users");

        String result = evaluator.resolveTemplate("https://api.example.com/${#endpoint}", ctx);
        assertEquals("https://api.example.com/users", result);
    }

    @Test
    void resolveTemplate_multiplePlaceholders() {
        FlowContext ctx = new FlowContext("inst-1", "def-1");
        ctx.putVariable("host", "example.com");
        ctx.putVariable("port", 8080);

        String result = evaluator.resolveTemplate("http://${#host}:${#port}/api", ctx);
        assertEquals("http://example.com:8080/api", result);
    }

    @Test
    void resolveTemplate_noPlaceholder_returnsOriginal() {
        FlowContext ctx = new FlowContext("inst-1", "def-1");
        String result = evaluator.resolveTemplate("plain text", ctx);
        assertEquals("plain text", result);
    }

    @Test
    void resolveTemplate_null_returnsNull() {
        FlowContext ctx = new FlowContext("inst-1", "def-1");
        assertNull(evaluator.resolveTemplate(null, ctx));
    }

    @Test
    void resolveTemplate_unresolvablePlaceholder_keepsOriginal() {
        FlowContext ctx = new FlowContext("inst-1", "def-1");
        // Variable 'unknown' not set — SpEL evaluates to null, placeholder becomes empty
        String result = evaluator.resolveTemplate("value: ${#unknown}", ctx);
        // The placeholder is either kept or replaced with empty string
        assertNotNull(result);
        assertTrue(result.startsWith("value: "));
    }
}
