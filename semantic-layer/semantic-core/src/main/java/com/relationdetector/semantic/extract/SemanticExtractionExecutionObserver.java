package com.relationdetector.semantic.extract;

import com.fasterxml.jackson.databind.JsonNode;

interface SemanticExtractionExecutionObserver {
    SemanticExtractionExecutionObserver NOOP = new SemanticExtractionExecutionObserver() {
        @Override
        public void shardCompleted(SemanticShardExecution execution) {
        }

        @Override
        public void reconciliationCompleted(
                SemanticExtractionPrompt prompt,
                SemanticExtractionResult result,
                JsonNode patch
        ) {
        }
    };

    void shardCompleted(SemanticShardExecution execution);

    void reconciliationCompleted(
            SemanticExtractionPrompt prompt,
            SemanticExtractionResult result,
            JsonNode patch
    );
}
