/**
 * CN: 责任是把配置路径、目录和glob解析为稳定输入清单；输入为用户路径与基准目录，输出为已规范化文件列表
 * 或ScanInputException。上游是配置解析，下游是source collector；禁止读取SQL语义、连接数据库或改变source开关。
 * EN: Responsibility: resolve configured paths, directories, and globs into stable input inventories. Inputs are user
 * paths and a base directory; outputs are normalized file lists or ScanInputException. Upstream is configuration and
 * downstream is source collection. SQL interpretation, JDBC access, and source-policy changes are forbidden.
 */
package com.relationdetector.core.input;
