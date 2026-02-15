package com.example.demo.sakila.config;

import java.util.List;
import java.util.stream.Collectors;
import net.ttddyy.dsproxy.ExecutionInfo;
import net.ttddyy.dsproxy.QueryInfo;
import net.ttddyy.dsproxy.listener.QueryExecutionListener;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class SqlSlowQueryListener implements QueryExecutionListener {
    private static final Logger SLOW_SQL_LOG = LoggerFactory.getLogger("SLOW_SQL");
    private final long slowThresholdMs;

    public SqlSlowQueryListener(@Value("${app.sql.slow-threshold-ms:200}") long slowThresholdMs) {
        this.slowThresholdMs = slowThresholdMs;
    }

    @Override
    public void beforeQuery(ExecutionInfo execInfo, List<QueryInfo> queryInfoList) {

    }

    @Override
    public void afterQuery(ExecutionInfo execInfo, List<QueryInfo> queryInfoList) {
        long costMs = execInfo.getElapsedTime();
        if (costMs < slowThresholdMs) {
            return;
        }
        String sql = queryInfoList.stream()
                .map(QueryInfo::getQuery)
                .collect(Collectors.joining(" ; "));
        String params = queryInfoList.stream()
                .map(q -> String.valueOf(q.getParametersList()))
                .collect(Collectors.joining(" ; "));

        SLOW_SQL_LOG.warn(
                "[SLOW_SQL] cost={}ms, success={}, type={}, batch={}, sql={}, params={}",
                costMs,
                execInfo.isSuccess(),
                execInfo.getStatementType(),
                execInfo.isBatch(),
                sql,
                params);

    }
}
