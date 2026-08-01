/**
 * CN: internal.io包负责semantic临时workspace的有界文件系统原语。输入来自reader和extract生命周期，
 * 输出是严格或尽力完成的文件事务；上游是磁盘store/session，下游是JDK文件系统。禁止解释业务artifact、
 * 跟随符号链接或把完整路径树加载到内存。
 * EN: The internal.io package owns bounded filesystem primitives for semantic temporary workspaces. It consumes
 * reader and extraction lifecycle requests and emits strict or best-effort file transactions between disk stores
 * and the JDK filesystem; it must not interpret business artifacts, follow symlinks, or load complete path trees.
 */
package com.relationdetector.semantic.internal.io;
