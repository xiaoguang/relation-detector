/**
 * CN: semantic-core 的稳定用例入口。输入是CLI已完成结构校验的路径与运行配置，输出是原子发布的KG、
 * extraction或normalization artifact；上游是semantic CLI，下游是ingest/evidence/extraction/KG内部包。
 * 禁止在此包解析命令行、创建物理事实或向调用方暴露磁盘session和store实现。
 *
 * <p>EN: Stable semantic-core use-case entry points. Inputs are paths and run configuration already structurally
 * validated by CLI; outputs are atomically published KG, extraction, or normalization artifacts. Semantic CLI is
 * upstream and the ingest/evidence/extraction/KG packages are downstream. This package must not parse command lines,
 * create physical facts, or expose disk-session/store implementations to callers.
 */
package com.relationdetector.semantic.facade;
