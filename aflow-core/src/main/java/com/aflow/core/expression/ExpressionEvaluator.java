package com.aflow.core.expression;

import com.aflow.common.model.FlowContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.expression.EvaluationContext;
import org.springframework.expression.Expression;
import org.springframework.expression.ExpressionParser;
import org.springframework.expression.spel.standard.SpelExpressionParser;
import org.springframework.expression.spel.support.SimpleEvaluationContext;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * SpEL-based expression evaluator for the workflow engine.
 * <p>
 * Supports two modes:
 * <ul>
 *   <li><strong>Expression evaluation</strong> — evaluates a SpEL expression against
 *       the flow context variables (e.g., condition edges: {@code #statusCode == 200})</li>
 *   <li><strong>Template resolution</strong> — resolves {@code ${variable}} placeholders
 *       in strings (e.g., URL templates: {@code https://api.example.com/${endpoint}})</li>
 * </ul>
 *
 * All flow context variables are exposed as SpEL variables (prefixed with {@code #}).
 */
@Service
public class ExpressionEvaluator {

    private static final Logger log = LoggerFactory.getLogger(ExpressionEvaluator.class);

    /** Pattern for ${variable} template placeholders. */
    private static final Pattern TEMPLATE_PATTERN = Pattern.compile("\\$\\{([^}]+)}");

    private final ExpressionParser parser = new SpelExpressionParser();

    /**
     * Evaluate a SpEL expression against the flow context.
     *
     * @param expression the SpEL expression string
     * @param context    the current flow context
     * @param resultType the expected result type
     * @param <T>        result type parameter
     * @return the evaluation result
     */
    public <T> T evaluate(String expression, FlowContext context, Class<T> resultType) {
        try {
            EvaluationContext evalContext = buildEvaluationContext(context);
            Expression expr = parser.parseExpression(expression);
            T result = expr.getValue(evalContext, resultType);
            log.trace("SpEL evaluate: '{}' => {}", expression, result);
            return result;
        } catch (Exception e) {
            log.error("Failed to evaluate SpEL expression '{}': {}", expression, e.getMessage());
            throw new RuntimeException("SpEL evaluation failed for expression: " + expression, e);
        }
    }

    /**
     * Resolve {@code ${variable}} placeholders in a template string.
     * <p>
     * Each placeholder is replaced by evaluating the variable name as a SpEL
     * expression against the flow context. Non-resolvable placeholders are
     * left as-is.
     *
     * @param template the template string with ${...} placeholders
     * @param context  the current flow context
     * @return the resolved string
     */
    public String resolveTemplate(String template, FlowContext context) {
        if (template == null || !template.contains("${")) {
            return template;
        }

        Matcher matcher = TEMPLATE_PATTERN.matcher(template);
        StringBuilder result = new StringBuilder();

        while (matcher.find()) {
            String variableExpr = matcher.group(1).trim();
            try {
                Object value = evaluate(variableExpr, context, Object.class);
                matcher.appendReplacement(result,
                        Matcher.quoteReplacement(value != null ? value.toString() : ""));
            } catch (Exception e) {
                log.warn("Failed to resolve template placeholder '{}': {}", variableExpr, e.getMessage());
                matcher.appendReplacement(result, Matcher.quoteReplacement(matcher.group()));
            }
        }
        matcher.appendTail(result);

        String resolved = result.toString();
        log.trace("Template resolved: '{}' => '{}'", template, resolved);
        return resolved;
    }

    /**
     * Build a restricted SpEL evaluation context with flow variables exposed.
     * <p>
     * Uses {@link SimpleEvaluationContext} to prevent arbitrary type instantiation,
     * constructor calls, and bean references — mitigating SpEL injection risks.
     */
    private EvaluationContext buildEvaluationContext(FlowContext context) {
        // Use read-only data binding: blocks type references, constructors, bean refs
        SimpleEvaluationContext evalContext = SimpleEvaluationContext
                .forReadOnlyDataBinding()
                .build();

        // Expose all flow variables as SpEL variables (accessible via #varName)
        if (context.getVariables() != null) {
            context.getVariables().forEach(evalContext::setVariable);
        }

        // Expose metadata as a special #metadata variable
        if (context.getMetadata() != null) {
            evalContext.setVariable("metadata", context.getMetadata());
        }

        // NOTE: #flowContext is intentionally NOT exposed to prevent
        // arbitrary method calls on the mutable FlowContext object.

        return evalContext;
    }
}
