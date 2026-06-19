package com.aflow.components.script;

import com.aflow.common.annotation.FlowNode;
import com.aflow.common.executor.NodeExecutor;
import com.aflow.common.model.FlowContext;
import com.aflow.common.model.NodeConfig;
import com.aflow.common.model.NodeResult;
import groovy.lang.Binding;
import groovy.lang.GroovyShell;
import org.codehaus.groovy.control.CompilerConfiguration;
import org.codehaus.groovy.control.customizers.ImportCustomizer;
import org.codehaus.groovy.control.customizers.SecureASTCustomizer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@FlowNode(type = "script", name = "Groovy Script", description = "Execute Groovy scripts")
@Component
public class ScriptNodeExecutor implements NodeExecutor {

    private static final Logger log = LoggerFactory.getLogger(ScriptNodeExecutor.class);

    /** Pre-configured compiler with security restrictions. */
    private static final CompilerConfiguration SECURE_COMPILER = createSecureCompiler();

    private static CompilerConfiguration createSecureCompiler() {
        SecureASTCustomizer secureCustomizer = new SecureASTCustomizer();
        // Block access to dangerous classes
        secureCustomizer.setDisallowedStaticImports(List.of(
                "java.lang.Runtime", "java.lang.ProcessBuilder",
                "java.lang.System", "java.io.File",
                "java.net.Socket", "java.net.ServerSocket"
        ));
        secureCustomizer.setDisallowedStaticStarImports(List.of(
                "java.lang.Runtime", "java.lang.ProcessBuilder"
        ));
        // Block static method calls on dangerous classes
        secureCustomizer.setDisallowedReceiversClasses(List.of(
                Runtime.class, ProcessBuilder.class, System.class,
                java.io.File.class, java.net.Socket.class
        ));

        ImportCustomizer importCustomizer = new ImportCustomizer();
        // Only allow safe imports
        importCustomizer.addImports("java.util.Map", "java.util.List", "java.util.HashMap", "java.util.ArrayList");

        CompilerConfiguration config = new CompilerConfiguration();
        config.addCompilationCustomizers(secureCustomizer, importCustomizer);
        return config;
    }

    @Override
    @SuppressWarnings("unchecked")
    public NodeResult execute(NodeConfig config, FlowContext context) {
        String script = String.valueOf(config.getConfig().getOrDefault("script", ""));
        if (script.isBlank()) {
            return NodeResult.failed("Script content is empty");
        }

        try {
            Binding binding = new Binding();
            // Expose variables as a read-only copy to prevent script from modifying context directly
            Map<String, Object> readOnlyVars = Collections.unmodifiableMap(
                    new HashMap<>(context.getVariables()));
            readOnlyVars.forEach(binding::setVariable);

            // NOTE: #context is intentionally NOT exposed to prevent
            // scripts from directly manipulating the FlowContext object.

            GroovyShell shell = new GroovyShell(binding, SECURE_COMPILER);
            Object result = shell.evaluate(script);

            Map<String, Object> outputs;
            if (result instanceof Map) {
                outputs = (Map<String, Object>) result;
            } else {
                outputs = new HashMap<>();
                outputs.put("result", result);
            }

            log.info("Groovy script executed successfully");
            return NodeResult.success(outputs);
        } catch (Exception e) {
            log.error("Groovy script execution failed: {}", e.getMessage());
            return NodeResult.failed("Script error: " + e.getMessage());
        }
    }
}
