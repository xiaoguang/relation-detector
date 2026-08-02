package com.relationdetector.semantic.cli;

import java.nio.file.Path;

import com.relationdetector.semantic.facade.SemanticExtractionFacade;

/**
 * CN: 为仓库验证脚本提供request-only分片包重建入口；输入是已发布run目录和目标bundle路径，输出canonical
 * SHA-256。它不注册公开CLI命令、不调用模型，也不放宽core的文件摘要、owner或closure校验。
 * EN: Provides repository verification scripts with an internal request-only package reconstruction entry point.
 * It accepts a published run and output bundle and prints the canonical SHA-256 without registering a public CLI
 * command, calling a model, or weakening core file-digest, ownership, or closure validation.
 */
public final class SemanticRequestBundleReconstructorMain {
    private SemanticRequestBundleReconstructorMain() {
    }

    public static void main(String[] args) {
        if (args == null || args.length != 2) {
            throw new IllegalArgumentException(
                    "semantic request bundle run and reconstruction target are required");
        }
        SemanticExtractionFacade.ReconstructionResult result =
                new SemanticExtractionFacade().reconstructRequestBundle(
                        Path.of(args[0]), Path.of(args[1]));
        System.out.println(result.canonicalSha256());
    }
}
