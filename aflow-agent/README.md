# AFlow Agent Module

> **Status: Reserved for Future Development**

This module is reserved for future **Agentic AI** features, including:

- LLM-powered workflow generation and optimization
- Intelligent node recommendation and auto-wiring
- Natural language to workflow DSL conversion
- Autonomous workflow debugging and self-healing
- AI-assisted variable mapping and transformation

## Planned Architecture

```
aflow-agent/
├── llm/          # LLM integration adapters (OpenAI, Claude, etc.)
├── planner/      # Workflow planning and generation
├── optimizer/    # Flow optimization suggestions
└── assistant/    # Interactive workflow assistant
```

## Dependencies

Currently depends only on `aflow-common` for shared model types.
Additional dependencies (e.g., LLM SDKs) will be added as features are implemented.
