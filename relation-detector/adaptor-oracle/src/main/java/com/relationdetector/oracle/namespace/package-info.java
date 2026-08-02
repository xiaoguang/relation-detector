/**
 * CN: 责任是从显式schema、connection schema或username证明Oracle owner；输入为scope/connection，输出为非空
 * owner。上游是Oracle collectors，下游是dictionary查询；禁止OWNER为空查询或在失败消息泄漏连接信息。
 * EN: Responsibility: prove a non-empty Oracle owner from explicit schema, connection schema, or username. Inputs are
 * scope and connection; output is the verified owner. Upstream is Oracle collectors and downstream is dictionary
 * queries. Empty-owner queries and connection-detail leakage are forbidden.
 */
package com.relationdetector.oracle.namespace;
