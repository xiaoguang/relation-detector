package com.relationdetector.semantic.extraction.artifact;

import com.relationdetector.semantic.extraction.prompt.SemanticExtractionPrompt;

import java.nio.file.Path;

/**
 * CN: 将一个有界 prompt 渲染到调用方指定的固定 request path，并返回含大小和摘要的文件引用；实现不得改写其他路径。
 * EN: Renders one bounded prompt to the caller-designated fixed request path and returns a size-and-digest file
 * reference; implementations must not write any other path.
 */
@FunctionalInterface
public interface SemanticRequestRenderer {
    SemanticArtifactRef render(SemanticExtractionPrompt prompt, Path target);
}
