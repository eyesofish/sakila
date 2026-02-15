# Sakila + Spring Boot JPA 傻瓜教程（一步一步跟着做）

目标：你已经有一个 Docker 里的 MySQL（容器名 `sakila-mysql`，库名 `sakila`），现在要在 `D:\Github\sakila\demo` 用 **Spring Data JPA** 访问它，并且顺便把“大厂常见 JPA 用法”（分页、排序、投影、事务、避免 N+1、测试）过一遍。

> 你只需要照着每一步做；每一步我都写了「知识点」和「检查点」，能跑通再做下一步。

---

## 0) 你最后会得到什么

跑起来后你会有这些能力（也是后面验收标准）：

- 能启动 Spring Boot 并连上 `sakila`。
- 能分页模糊查电影：`GET /api/films?title=ACE&page=0&size=5`
- 能按分类查电影标题：`GET /api/films/by-category?category=Action`
- 能更新某个电影的租金：`POST /api/films/1/rental-rate?rate=3.99`

---

## 1) 确认数据库容器真的可用

### 你要做什么

在任意终端执行：

```bash
docker ps --filter "name=sakila-mysql"
docker logs sakila-mysql --tail 30
docker exec -i sakila-mysql mysqladmin -uroot -ppass ping
```

### 知识点

- `docker ps` 看容器是否在跑。
- `docker logs` 看 MySQL 有没有 “ready for connections”。
- `mysqladmin ping` 最直接：能 ping 通说明账号密码能用、MySQL 服务真起来了。

### 检查点

你应该看到：`mysqld is alive`

---

## 2) 导入 Sakila（只做一次）

> 如果你已经导入过并且能查到 `film` 有 1000 条，可直接跳到第 3 步。

### 你要做什么

Sakila 的 SQL 文件在仓库：`D:\Github\sakila\mysql-sakila-db\`

#### 推荐做法（不管你是 PowerShell / Git Bash，都最稳）

把 SQL 先拷进容器，再在容器里执行（避开 Windows Shell 的重定向坑）：

```bash
docker cp mysql-sakila-db/mysql-sakila-schema.sql sakila-mysql:/schema.sql
docker cp mysql-sakila-db/mysql-sakila-insert-data.sql sakila-mysql:/data.sql

docker exec -i sakila-mysql sh -c "MYSQL_PWD=pass mysql -uroot < /schema.sql"
docker exec -i sakila-mysql sh -c "MYSQL_PWD=pass mysql -uroot < /data.sql"
```

#### 验证是否导入成功

```bash
docker exec -i sakila-mysql mysql -uroot -ppass -e "USE sakila; SELECT COUNT(*) AS films FROM film;"
```

### 知识点

- Sakila 分两步：`schema`（建库建表）+ `insert-data`（灌数据）。
- 为什么不用 `ddl-auto=create/update`：因为 Sakila 是现成 schema，你要练的是“对已有库写业务查询”，而不是让 Hibernate 去改表。

### 检查点

`films` 应该等于 `1000`

---

## 3) Spring Boot 项目里具体写在哪（目录约定）

你的项目路径是：`D:\Github\sakila\demo`

你后面写的 Java 代码，全部放这里：

- 代码：`demo/src/main/java/com/example/demo/`
- 配置：`demo/src/main/resources/application.yaml`
- 测试：`demo/src/test/java/com/example/demo/`

建议按“分层”建包（你照这个建，不会乱）：

```
com.example.demo
  sakila
    entity          # JPA 实体（表映射）
    repository      # JPA 仓储（数据访问）
    service         # 业务逻辑 + 事务边界
    web             # Controller 对外接口
```

### 知识点

- 大厂里最常见的分层就是：Controller（接口层）→ Service（业务层）→ Repository（数据层）。
- 事务（`@Transactional`）通常放 Service 层，而不是 Controller 层。

---

## 4) 依赖（pom.xml 要有这些）

打开 `demo/pom.xml`，确保至少有：

- `spring-boot-starter-data-jpa`（JPA 必需）
- `mysql-connector-j`（MySQL 驱动）
- `spring-boot-starter-web`（你要写 REST 接口就要它）

> 你当前的 `demo/pom.xml` 已经加过 MySQL、JPA、MyBatis-Plus；为了学 JPA，MyBatis-Plus 可以先不管。

### 知识点

- **JPA = 规范**；**Hibernate = 默认实现**；**Spring Data JPA = 帮你生成 Repository 的那层**。
- 没有 `starter-web` 就没有 `@RestController` 那套 Web 能力。

---

## 5) 配置数据源（application.yaml）

打开 `demo/src/main/resources/application.yaml`，按这个配（你现在基本已经是这个了）：

```yaml
spring:
  datasource:
    url: jdbc:mysql://localhost:3306/sakila?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=UTC
    username: root
    password: pass
  jpa:
    hibernate:
      ddl-auto: none # 关键：不要让 Hibernate 改表
    open-in-view: false # 关键：避免“事务外懒加载”把问题藏起来
    properties:
      hibernate.format_sql: true
      hibernate.jdbc.time_zone: UTC
```

### 知识点

- `open-in-view=false`：大厂常规默认关闭。你必须在 Service 的事务里把需要的数据查完，避免线上 N+1 和延迟加载“暗雷”。
- `allowPublicKeyRetrieval=true`：MySQL 8/9 常见认证相关参数，不加容易连不上。

### 检查点

先别写任何代码，直接启动一下应用看能不能连上库（下一步会给启动命令）。

---

## 6) 第一个实体：Film（只映射你用得到的列）

新建文件：`demo/src/main/java/com/example/demo/sakila/entity/Film.java`

```java
package com.example.demo.sakila.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;

@Entity
@Table(name = "film")
public class Film {
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  @Column(name = "film_id")
  private Long id;

  @Column(nullable = false)
  private String title;

  @Column(name = "rental_rate", nullable = false)
  private BigDecimal rentalRate;

  // sakila 里是 SMALLINT UNSIGNED，Java 用 Integer 足够（且允许为 null）
  private Integer length;

  protected Film() {}

  public Long getId() { return id; }
  public String getTitle() { return title; }
  public BigDecimal getRentalRate() { return rentalRate; }
  public Integer getLength() { return length; }

  public void setTitle(String title) { this.title = title; }
  public void setRentalRate(BigDecimal rentalRate) { this.rentalRate = rentalRate; }
  public void setLength(Integer length) { this.length = length; }
}
```

//2.2 晚上看到这里

### 知识点

- `@Entity`：告诉 Hibernate 这是一个“可持久化对象”。
- `@Id`：主键；Sakila 的 `film_id` 是 `INT UNSIGNED AUTO_INCREMENT`，所以用 `Long/Integer` 更合适，不要用 `Short`。
- **只映射你用得到的列**：这是一种大厂里常见的“让实体保持精简”的做法；复杂场景用 DTO/投影补齐。

---

## 7) Repository：分页模糊查 + 多表查询投影

### 7.1 投影接口（只返回你要的字段）

新建：`demo/src/main/java/com/example/demo/sakila/repository/projection/FilmTitleCategoryView.java`

```java
package com.example.demo.sakila.repository.projection;

public interface FilmTitleCategoryView {
  String getTitle();
  String getCategory();
}
```

### 7.2 Repository 接口

新建：`demo/src/main/java/com/example/demo/sakila/repository/FilmRepository.java`

```java
package com.example.demo.sakila.repository;

import com.example.demo.sakila.entity.Film;
import com.example.demo.sakila.repository.projection.FilmTitleCategoryView;
import java.util.List;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface FilmRepository extends JpaRepository<Film, Long> {
  // 1) 方法名派生查询：title like %xx%（忽略大小写） + 分页
  Page<Film> findByTitleContainingIgnoreCase(String title, Pageable pageable);

  // 2) 多表 join：不想建很多实体关系时，用 nativeQuery + projection 很好用
  @Query(
    value = """
      select f.title as title, c.name as category
      from film f
      join film_category fc on fc.film_id = f.film_id
      join category c on c.category_id = fc.category_id
      where c.name = :category
      """,
    nativeQuery = true
  )
  List<FilmTitleCategoryView> findByCategory(@Param("category") String category);
}
```

### 知识点

- **派生查询**：`findByXxxContainingIgnoreCase` 这种就是 Spring Data JPA 的“按方法名生成 SQL”。
- **分页**：永远用 `Pageable` / `Page`，别手写 `limit/offset` 拼字符串。
- **投影（Projection）**：只取需要的列，避免把整行整表都加载进实体（省内存、省网络、也更快）。
- **多表查询两条路**：
  1. 建实体关系（`@ManyToMany` 等）→ 用 JPQL/FETCH JOIN
  2. 先别建关系 → 用 native SQL + Projection（上面用的就是这条）

---

## 8) Service：把事务放在这里（大厂默认姿势）

新建：`demo/src/main/java/com/example/demo/sakila/service/FilmService.java`

```java
package com.example.demo.sakila.service;

import com.example.demo.sakila.entity.Film;
import com.example.demo.sakila.repository.FilmRepository;
import com.example.demo.sakila.repository.projection.FilmTitleCategoryView;
import java.math.BigDecimal;
import java.util.List;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
public class FilmService {
  private final FilmRepository filmRepository;

  public FilmService(FilmRepository filmRepository) {
    this.filmRepository = filmRepository;
  }

  // 读接口：readOnly=true（语义清晰，且某些场景能优化）
  @Transactional(readOnly = true)
  public Page<Film> pageByTitle(String title, int page, int size) {
    String safeTitle = StringUtils.hasText(title) ? title : "";
    PageRequest pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "rentalRate"));
    return filmRepository.findByTitleContainingIgnoreCase(safeTitle, pageable);
  }

  @Transactional(readOnly = true)
  public List<FilmTitleCategoryView> listByCategory(String category) {
    return filmRepository.findByCategory(category);
  }

  // 写接口：显式事务（默认 readOnly=false）
  @Transactional
  public Film updateRentalRate(long filmId, BigDecimal newRate) {
    Film film = filmRepository.findById(filmId)
      .orElseThrow(() -> new IllegalArgumentException("film not found: " + filmId));
    film.setRentalRate(newRate);
    return filmRepository.save(film);
  }
}
```

### 知识点

- 为什么事务放 Service：Controller 只负责参数/返回；Service 才是“业务动作”的边界。
- `@Transactional(readOnly = true)`：不是为了玄学提速，而是为了语义+避免误写；写接口必须显式事务保证一致性。

---

## 9) Controller：给你一个能直接测的接口

新建：`demo/src/main/java/com/example/demo/sakila/web/FilmController.java`

```java
package com.example.demo.sakila.web;

import com.example.demo.sakila.entity.Film;
import com.example.demo.sakila.repository.projection.FilmTitleCategoryView;
import com.example.demo.sakila.service.FilmService;
import java.math.BigDecimal;
import java.util.List;
import org.springframework.data.domain.Page;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/films")
public class FilmController {
  private final FilmService filmService;

  public FilmController(FilmService filmService) {
    this.filmService = filmService;
  }

  // 示例：/api/films?title=ACE&page=0&size=5
  @GetMapping
  public Page<Film> page(
    @RequestParam(defaultValue = "") String title,
    @RequestParam(defaultValue = "0") int page,
    @RequestParam(defaultValue = "20") int size
  ) {
    return filmService.pageByTitle(title, page, size);
  }

  // 示例：/api/films/by-category?category=Action
  @GetMapping("/by-category")
  public List<FilmTitleCategoryView> byCategory(@RequestParam String category) {
    return filmService.listByCategory(category);
  }

  // 示例：POST /api/films/1/rental-rate?rate=3.99
  @PostMapping("/{id}/rental-rate")
  public Film updateRentalRate(@PathVariable long id, @RequestParam BigDecimal rate) {
    return filmService.updateRentalRate(id, rate);
  }
}
```

### 知识点

- 这里直接返回实体只是为了学习更快；真实业务通常返回 DTO（避免实体泄漏、避免循环引用、避免懒加载坑）。
- 分页参数统一从 Controller 收口，再交给 Service。

---

## 10) 运行与验证（能跑通就算入门成功）

### 10.1 启动项目

在 `D:\Github\sakila\demo` 运行（按你用的终端选一种）：

```bash
cd demo

# Git Bash
./mvnw spring-boot:run

# CMD
mvnw.cmd spring-boot:run

# PowerShell
.\mvnw.cmd spring-boot:run
```

### 10.2 调接口验证

浏览器直接访问也行，或用 curl：

```bash
curl "http://localhost:8080/api/films?title=ACE&page=0&size=5"
curl "http://localhost:8080/api/films/by-category?category=Action"
curl -X POST "http://localhost:8080/api/films/1/rental-rate?rate=3.99"
```

---

## 11) 加 SQL 耗时日志 + 慢查询识别（datasource-proxy）

目标：每条 SQL 都打印出来（SQL + 参数 + 耗时），并且超过阈值（例如 200ms）单独打 `WARN`。

> 这里用 `datasource-proxy`，好处是你能在代码里完全掌控日志格式，方便做实验。

### 11.1 先加依赖（pom.xml）

打开 `demo/pom.xml`，补这两个依赖：

```xml
<!-- SQL 代理：打印 SQL、参数、耗时 -->
<dependency>
  <groupId>net.ttddyy</groupId>
  <artifactId>datasource-proxy</artifactId>
  <version>1.11.0</version>
</dependency>

<!-- 第 12 步 AOP 要用 -->
<dependency>
  <groupId>org.springframework.boot</groupId>
  <artifactId>spring-boot-starter-aop</artifactId>
</dependency>
```

### 11.2 新建慢 SQL 监听器

新建：`demo/src/main/java/com/example/demo/sakila/config/SqlSlowQueryListener.java`

```java
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
    // no-op
  }

  @Override
  public void afterQuery(ExecutionInfo execInfo, List<QueryInfo> queryInfoList) {
    long costMs = execInfo.getElapsedTime(); // datasource-proxy 默认就是毫秒
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
      params
    );
  }
}
```

### 11.3 包装 DataSource，让每条 SQL 自动打日志

新建：`demo/src/main/java/com/example/demo/sakila/config/DataSourceProxyConfig.java`

```java
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
      public Object postProcessAfterInitialization(Object bean, String beanName) throws BeansException {
        if (!(bean instanceof DataSource dataSource)) {
          return bean;
        }
        // 只包装 Spring Boot 的主数据源，避免重复代理
        if (!"dataSource".equals(beanName) || bean instanceof ProxyDataSource) {
          return bean;
        }

        return ProxyDataSourceBuilder.create(dataSource)
          .name("sakila-proxy")
          .multiline()
          .logQueryBySlf4j(SLF4JLogLevel.INFO) // 常规 SQL 日志：SQL + 参数 + 耗时
          .listener(slowQueryListener)          // 慢查询单独 WARN
          .build();
      }
    };
  }
}
```

### 11.4 application.yaml 增加配置

打开 `demo/src/main/resources/application.yaml`，在你原有配置后追加：

```yaml
app:
  sql:
    slow-threshold-ms: 200
  aop:
    service-slow-threshold-ms: 300

logging:
  level:
    net.ttddyy.dsproxy.listener: INFO
    SLOW_SQL: WARN
    SERVICE_COST: INFO
```

### 11.5 你会看到什么日志（示例）

正常 SQL（每条都打，方便你观察）：

```text
Name:sakila-proxy, Time:14, Success:True
Type:Prepared, Batch:False, QuerySize:1, BatchSize:0
Query:["select f.film_id,f.title from film f where f.title like ? order by f.rental_rate desc limit ?"]
Params:[(ACE%,5)]
```

慢 SQL（超过 200ms 单独 `WARN`）：

```text
WARN  SLOW_SQL - [SLOW_SQL] cost=287ms, success=true, type=STATEMENT, batch=false,
sql=select sleep(0.287), params=[[]]
```

### 11.6（可选）造一个慢 SQL 做验证

在 `demo/src/main/java/com/example/demo/sakila/repository/FilmRepository.java` 增加：

```java
@Query(value = "select sleep(0.25)", nativeQuery = true)
Double debugSleep250ms();
```

在 `demo/src/main/java/com/example/demo/sakila/service/FilmService.java` 增加：

```java
@Transactional(readOnly = true)
public Double debugSlowSql() {
  return filmRepository.debugSleep250ms();
}
```

在 `demo/src/main/java/com/example/demo/sakila/web/FilmController.java` 增加：

```java
@GetMapping("/debug/slow-sql")
public Double debugSlowSql() {
  return filmService.debugSlowSql();
}
```

然后访问：`GET http://localhost:8080/api/films/debug/slow-sql`

你应该马上看到 `SLOW_SQL` 的 `WARN`。

---

## 12) Service 层方法耗时统计（Spring AOP）

//2.5 日下午看到这里，然后去搓 AOP 了

//2.13 回来继续看

目标：不入侵业务代码，不写 `System.currentTimeMillis()`，统一统计 Service 方法耗时；超过 300ms 打 `WARN`。

新建：`demo/src/main/java/com/example/demo/sakila/aop/ServiceCostAspect.java`

```java
package com.example.demo.sakila.aop;

import java.util.concurrent.TimeUnit;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Aspect
@Component
public class ServiceCostAspect {

  private static final Logger SERVICE_COST_LOG = LoggerFactory.getLogger("SERVICE_COST");

  private final long slowThresholdMs;

  public ServiceCostAspect(@Value("${app.aop.service-slow-threshold-ms:300}") long slowThresholdMs) {
    this.slowThresholdMs = slowThresholdMs;
  }

  // 你的项目当前 service 包是 com.example.demo.sakila.service
  @Around("execution(* com.example.demo.sakila.service..*(..))")
  public Object aroundService(ProceedingJoinPoint joinPoint) throws Throwable {
    long startNs = System.nanoTime();
    try {
      return joinPoint.proceed();
    } finally {
      long costMs = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startNs);
      String method = joinPoint.getSignature().toShortString();
      if (costMs >= slowThresholdMs) {
        SERVICE_COST_LOG.warn("[SLOW_SERVICE] method={}, cost={}ms", method, costMs);
      } else {
        SERVICE_COST_LOG.info("[SERVICE] method={}, cost={}ms", method, costMs);
      }
    }
  }
}
```

示例日志：

```text
INFO  SERVICE_COST - [SERVICE] method=FilmService.pageByTitle(..), cost=24ms
WARN  SERVICE_COST - [SLOW_SERVICE] method=FilmService.debugSlowSql(..), cost=255ms
```

---

## 13) Sakila 实战：一条慢 SQL vs 一条优化 SQL

> 你要的是“方便自己改 SQL 再观察”，这里给你一组很适合做实验的样例。

### 13.1 慢 SQL 示例（故意写差）

```sql
SELECT f.film_id, f.title, COUNT(r.rental_id) AS rental_cnt
FROM film f
JOIN inventory i ON i.film_id = f.film_id
JOIN rental r ON r.inventory_id = i.inventory_id
JOIN film_category fc ON fc.film_id = f.film_id
JOIN category c ON c.category_id = fc.category_id
WHERE DATE(r.rental_date) = '2005-06-14'
  AND LOWER(c.name) LIKE '%act%'
  AND LOWER(f.title) LIKE '%ace%'
GROUP BY f.film_id, f.title
ORDER BY rental_cnt DESC
LIMIT 20;
```

为什么它容易慢：

- `DATE(r.rental_date)` 对列做函数，索引利用率会变差。
- `LIKE '%xxx%'` 前导 `%`，B-Tree 索引通常用不上。
- `LOWER(...)` 也会让优化器更难使用普通索引。

### 13.2 优化后 SQL（同需求，写法更可优化）

```sql
SELECT f.film_id, f.title, COUNT(r.rental_id) AS rental_cnt
FROM rental r
JOIN inventory i ON i.inventory_id = r.inventory_id
JOIN film f ON f.film_id = i.film_id
JOIN film_category fc ON fc.film_id = f.film_id
JOIN category c ON c.category_id = fc.category_id
WHERE r.rental_date >= '2005-06-14 00:00:00'
  AND r.rental_date <  '2005-06-15 00:00:00'
  AND c.name = 'Action'
  AND f.title LIKE 'ACE%'
GROUP BY f.film_id, f.title
ORDER BY rental_cnt DESC
LIMIT 20;
```

为什么它通常更快：

- 时间条件改成“范围过滤”，更容易走 `rental_date` 索引。
- 分类改成等值匹配（`c.name = 'Action'`），选择性更高。
- 标题改成前缀匹配（`'ACE%'`），有机会利用标题索引。

### 13.3 如何用 EXPLAIN 分析

先看执行计划：

```sql
EXPLAIN FORMAT=TRADITIONAL
SELECT ... ; -- 把上面的 SQL 粘过来
```

再看真实执行耗时（MySQL 8+）：

```sql
EXPLAIN ANALYZE
SELECT ... ;
```

重点看这几列：

- `type`：是否从 `ALL`（全表扫）改善到 `range/ref/eq_ref`。
- `key`：是否从 `NULL` 变成使用具体索引名。
- `rows`：预估扫描行数是否明显下降。
- `Extra`：慢 SQL 常见 `Using temporary`、`Using filesort`。

---

## 14) 一次完整实验流程（照着跑）

1. 启动应用：`.\mvnw.cmd spring-boot:run`
2. 访问：`/api/films?title=ACE&page=0&size=5`，观察常规 SQL 日志。
3. 访问：`/api/films/debug/slow-sql`，确认 `SLOW_SQL` 打 `WARN`。
4. 把 `app.sql.slow-threshold-ms` 改成 `50`，再测一遍，感受阈值变化。
5. 在 MySQL 里分别执行“慢 SQL / 优化 SQL”的 `EXPLAIN ANALYZE`，对比 `type/key/rows`。
6. 自己改 where 条件（比如把 `'ACE%'` 改回 `'%ACE%'`），再观察日志和执行计划变化。

到这里，你就有了一个可以反复做实验的“性能分析练习场”：

- 代码层实时看到 SQL + 参数 + 耗时
- 慢查询自动告警
- Service 方法耗时也可见
- 能立刻切到 EXPLAIN 做定位
