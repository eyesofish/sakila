package com.example.demo.sakila.config;

import javax.sql.DataSource;
import net.ttddyy.dsproxy.listener.logging.SLF4JLogLevel;
import net.ttddyy.dsproxy.support.ProxyDataSource;
import net.ttddyy.dsproxy.support.ProxyDataSourceBuilder;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class DataSourceProxyConfig {
    @Bean
    public BeanPostProcessor dataSourceProxyBeanPostProcessor(SqlSlowQueryListener slowQueryListener) {
        return new BeanPostProcessor() {
            @Override
            public Object postProcessAfterInitialization(Object bean, String beanName)
                    throws BeansException {
                if (!(bean instanceof DataSource dataSource)) {
                    return bean;
                }
                if (!"dataSource".equals(beanName) || bean instanceof ProxyDataSource) {
                    return bean;
                }
                return ProxyDataSourceBuilder.create(dataSource)
                        .name("sakila-proxy")
                        .multiline()
                        .logQueryBySlf4j(SLF4JLogLevel.INFO) // 常规 SQL 日志：SQL + 参数 + 耗时
                        .listener(slowQueryListener) // 慢查询单独 WARN
                        .build();

            }
        };
    }
}
