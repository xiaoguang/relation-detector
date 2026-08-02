/**
 * CN: 责任是提供外排排序、磁盘字典、union-find和record store等存储无关原语；输入输出均为内部typed记录或
 * UTF-8 key文件。上游是ingest/evidence/sharding，下游是文件系统；禁止包含业务语义、模型协议或CLI策略。
 * EN: Responsibility: provide storage-neutral external sorting, disk dictionaries, union-find, and record-store
 * primitives. Inputs and outputs are internal typed records or UTF-8 key files. Upstream is ingest/evidence/sharding
 * and downstream is the file system. Domain semantics, model protocols, and CLI policy are forbidden.
 */
package com.relationdetector.semantic.internal.store;
