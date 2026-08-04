package com.relationdetector.semantic.extraction.runtime;

import com.relationdetector.semantic.extraction.prompt.SemanticExtractionPrompt;

/**
 * CN: 抽象一次 evidence-grounded model 请求；输入绑定 prompt/bundle，输出成功响应，供 shard service 测试和 provider 适配，禁止持有物理事实状态。
 * EN: Abstracts one evidence-grounded model request. It consumes a prompt-bound bundle and returns a successful response without owning physical-fact state.
 */
@FunctionalInterface
public interface SemanticModelClient {
    SemanticModelCallResult extract(
            SemanticExtractionPrompt prompt,
            SemanticModelCallContext context
    );
}
