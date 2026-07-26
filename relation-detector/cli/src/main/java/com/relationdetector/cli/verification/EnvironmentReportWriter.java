package com.relationdetector.cli.verification;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;

import com.fasterxml.jackson.databind.node.ObjectNode;

/**
 * CN: 采集当前提交、分支、JDK、Maven 和平台的小型发布环境快照并写入 JSON；
 * 上游是发布脚本，下游是 verification manifest，禁止读取凭据、连接信息或业务数据。
 * EN: Captures a compact release environment snapshot containing revision, branch, JDK, Maven, and platform data.
 * Release scripts are upstream and the verification manifest is downstream; credentials and business data are out.
 */
final class EnvironmentReportWriter {
    void write(Path output, String commit, String branch, String originMain, String mavenBinary) {
        ObjectNode report = ReleaseVerificationJson.MAPPER.createObjectNode();
        report.put("commit", commit);
        report.put("branch", branch);
        report.put("originMain", originMain);
        report.put("maven", command(mavenBinary, "-version"));
        report.put("java", command("java", "-version"));
        report.put("platform", System.getProperty("os.name") + " "
                + System.getProperty("os.version") + " " + System.getProperty("os.arch"));
        ReleaseVerificationJson.write(output, report);
    }

    private String command(String... command) {
        try {
            Process process = new ProcessBuilder(command)
                    .redirectErrorStream(true)
                    .start();
            String output = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8).strip();
            if (process.waitFor() != 0) {
                throw new ReleaseVerificationException(
                        "failed to collect verification environment");
            }
            return output;
        } catch (IOException error) {
            throw new ReleaseVerificationException("failed to collect verification environment", error);
        } catch (InterruptedException error) {
            Thread.currentThread().interrupt();
            throw new ReleaseVerificationException("environment collection was interrupted", error);
        }
    }
}
