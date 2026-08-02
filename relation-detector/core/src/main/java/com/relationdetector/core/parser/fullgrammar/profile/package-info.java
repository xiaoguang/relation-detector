/**
 * CN: 责任是定义full-grammar版本profile和方言能力选择；输入为数据库版本与profile声明，输出为确定的parser
 * profile。上游是parser bundle选择，下游是版本化adaptor；禁止解析SQL、跨版本delegate或修改runtime事实。
 * EN: Responsibility: define full-grammar version profiles and dialect capability selection. Inputs are database
 * versions and profile declarations; output is a deterministic parser profile. Upstream is bundle selection and
 * downstream is versioned adaptors. SQL parsing, cross-version delegation, and fact mutation are forbidden.
 */
package com.relationdetector.core.parser.fullgrammar.profile;
