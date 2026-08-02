/**
 * CN: 责任是校验、快照并隔离不可信adaptor SPI结果；输入为插件能力及返回值，输出为core可提交的不可变
 * 结果或typed contract异常。上游是scan preflight， 下游是采集和parser编排；禁止打开JDBC、推断SQL语义或
 * 部分提交失败结果。
 * EN: Responsibility: validate, snapshot, and detach untrusted adaptor SPI results. Inputs are plugin capabilities
 * and outcomes; outputs are immutable core-owned results or typed contract failures. Upstream is scan preflight and
 * downstream is collection/parser orchestration. JDBC access, SQL inference, and partial commits are forbidden.
 */
package com.relationdetector.core.adaptor;
