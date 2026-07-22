package com.relationdetector.core.parser;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import com.relationdetector.contracts.model.WarningMessage;
import com.relationdetector.contracts.parse.SqlStatementRecord;
import com.relationdetector.contracts.parse.StructuredParseResult;
import com.relationdetector.contracts.spi.AdaptorContext;
import com.relationdetector.contracts.spi.Collectors.StructuredSqlParser;
import com.relationdetector.core.provenance.StructuredParseProvenanceNormalizer;
import com.relationdetector.core.scan.AdaptorContractException;
import com.relationdetector.core.scan.AdaptorParseResultContractValidator;
import com.relationdetector.core.scan.AdaptorResultDetachmentSupport;

/**
 * CN: 统一执行一个不可信的structured SQL parser，先隔离context、原子校验typed result与provenance，
 * 再提交warning并把可信结果交给relationship/lineage消费者。输入来自parser SPI，输出供runner、direct
 * statement服务和relationship facade复用；本类不选择parser、不执行fallback，也不抽取事实。
 *
 * <p>EN: Executes one untrusted structured SQL parser behind a detached context, atomically validates the typed
 * result and provenance, and commits warnings only after validation. Runners, direct statement execution, and the
 * relationship facade consume its trusted result; it neither selects parsers, performs fallback, nor extracts facts.
 */
public final class StructuredSqlParseExecutor {
    private final AdaptorParseResultContractValidator resultValidator =
            new AdaptorParseResultContractValidator();
    private final AdaptorResultDetachmentSupport detachment = new AdaptorResultDetachmentSupport();
    private final StructuredParseProvenanceNormalizer provenanceNormalizer =
            new StructuredParseProvenanceNormalizer();

    public StructuredParseResult parse(
            StructuredSqlParser parser,
            SqlStatementRecord statement,
            AdaptorContext context
    ) {
        if (parser == null) {
            throw new AdaptorContractException(
                    "adaptor parse-result contract violation: structured SQL parser is null");
        }
        List<WarningMessage> callbackWarnings = new ArrayList<>();
        AdaptorContext detached = detachedContext(context, callbackWarnings);
        StructuredParseResult validated = resultValidator.validateSql(
                statement, parser.parseSql(statement, detached), callbackWarnings);
        StructuredParseResult normalized = provenanceNormalizer.normalize(statement, validated);
        if (context != null) {
            normalized.warnings().forEach(context::warn);
        }
        return normalized;
    }

    private AdaptorContext detachedContext(
            AdaptorContext context,
            List<WarningMessage> warnings
    ) {
        return context == null
                ? new AdaptorContext(null, Map.of(), warnings::add)
                : new AdaptorContext(context.scope(),
                        detachment.attributes(context.options(), "structured parser context options"),
                        warnings::add);
    }

}
