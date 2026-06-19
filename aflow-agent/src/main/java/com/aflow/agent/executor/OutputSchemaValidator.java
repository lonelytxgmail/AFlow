package com.aflow.agent.executor;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.networknt.schema.JsonSchema;
import com.networknt.schema.JsonSchemaFactory;
import com.networknt.schema.SpecVersion;
import com.networknt.schema.ValidationMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Agent 输出 JSON Schema 验证器。
 * <p>
 * 用于在 Agent 产出最终答案后，验证其输出是否符合用户指定的 JSON Schema。
 *
 * @author AFlow Team
 * @since 1.1.0
 */
public class OutputSchemaValidator {

    private static final Logger log = LoggerFactory.getLogger(OutputSchemaValidator.class);
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final JsonSchema schema;

    public OutputSchemaValidator(String schemaJson) {
        JsonSchemaFactory factory = JsonSchemaFactory.getInstance(SpecVersion.VersionFlag.V7);
        this.schema = factory.getSchema(schemaJson);
    }

    /**
     * 验证 Agent 输出是否符合 schema。
     *
     * @param output Agent 的最终输出文本
     * @return 验证结果
     */
    public ValidationResult validate(String output) {
        if (output == null || output.isBlank()) {
            return ValidationResult.failed(List.of("Output is null or empty"));
        }

        try {
            JsonNode jsonNode = MAPPER.readTree(output);
            Set<ValidationMessage> errors = schema.validate(jsonNode);

            if (errors.isEmpty()) {
                return ValidationResult.success();
            }

            List<String> errorMessages = errors.stream()
                    .map(ValidationMessage::getMessage)
                    .collect(Collectors.toList());
            return ValidationResult.failed(errorMessages);

        } catch (Exception e) {
            // 输出不是有效 JSON
            log.debug("Output is not valid JSON: {}", e.getMessage());
            return ValidationResult.failed(List.of("Output is not valid JSON: " + e.getMessage()));
        }
    }

    /**
     * 验证结果。
     */
    public record ValidationResult(boolean valid, List<String> errors) {

        public static ValidationResult success() {
            return new ValidationResult(true, Collections.emptyList());
        }

        public static ValidationResult failed(List<String> errors) {
            return new ValidationResult(false, errors);
        }
    }
}
