# Sakila MySQL 实战速成营（Spring Boot / Redis 开发者版）

面向人群：只会写 CRUD 的 MySQL 初学者，但已有 Spring Boot 开发经验和 Redis 理论基础。  
达成状态：能用 Sakila 数据库完成查询、优化、Java 访问与缓存一致性，不踩常见坑。

---

## 0. 开箱即用：准备数据库
- 启动 MySQL（示例 root 密码 `pass`）：
  ```bash
  docker run -d --name sakila-mysql -e MYSQL_ROOT_PASSWORD=pass -p 3306:3306 mysql:8
  docker exec -i sakila-mysql mysql -uroot -ppass < mysql-sakila-db/mysql-sakila-schema.sql
  docker exec -i sakila-mysql mysql -uroot -ppass < mysql-sakila-db/mysql-sakila-insert-data.sql
  ```
- 验证：`SELECT COUNT(*) FROM film;` 返回 1000 说明数据就绪。
- 不要动 schema：后续练习仅写查询/索引，避免 `ddl-auto=create` 之类破坏数据。

---

## 基础篇：SQL 基础复习 + Sakila 结构
**目标**：看懂核心表结构，熟练用 SELECT/INSERT/UPDATE/DELETE+分页过滤。  
**核心关系**（记住这几张表就够用）：
- 影片：`film`（标题、时长、租金）、`language`，分类关联表 `film_category` ➜ `category`
- 演员：`actor` 与影片多对多关联表 `film_actor`
- 门店/员工：`store` ↔ `staff`
- 客户/库存/租赁/支付：`customer`、`inventory`（某店的实体盘）、`rental`（租赁记录）、`payment`

**必会动作**
- 基本查询：`SELECT title, rental_rate FROM film WHERE rental_rate BETWEEN 2 AND 4 ORDER BY rental_rate LIMIT 10;`
- 模糊：`... WHERE title LIKE '%PIRATE%'`
- 插入示例：在 `actor` 表插一条（注意主键自增）：`INSERT INTO actor(first_name,last_name) VALUES ('LINUS','TEST');`
- 更新/删除：`UPDATE film SET rental_rate = 4.99 WHERE film_id = 1;` / `DELETE FROM actor WHERE last_name='TEST';`
- 分页：`LIMIT {size} OFFSET {page*size}`

**练习**
1) 列出每种语言的影片数量（group by）。  
2) 找出租金最高的前 10 部影片（order by + limit）。  
3) 查询最近上架（`release_year` 最大）的 20 部影片标题和长度。

---

## 进阶篇：JOIN / 子查询 / 聚合
**目标**：把多表查询写顺手，能用 EXISTS/子查询与聚合回答业务问题。  
**JOIN 模板（Sakila 版）**
- 影片 ➜ 分类：  
  ```sql
  SELECT f.title, c.name AS category
  FROM film f
  JOIN film_category fc ON fc.film_id = f.film_id
  JOIN category c ON c.category_id = fc.category_id
  LIMIT 20;
  ```
- 客户最近租赁：  
  ```sql
  SELECT c.first_name, c.last_name, r.rental_date, f.title
  FROM customer c
  JOIN rental r ON r.customer_id = c.customer_id
  JOIN inventory i ON i.inventory_id = r.inventory_id
  JOIN film f ON f.film_id = i.film_id
  WHERE c.customer_id = 1
  ORDER BY r.rental_date DESC
  LIMIT 5;
  ```

**子查询 / EXISTS**
- 没有任何库存的影片：  
  ```sql
  SELECT f.title
  FROM film f
  WHERE NOT EXISTS (
    SELECT 1 FROM inventory i WHERE i.film_id = f.film_id
  );
  ```

**聚合 & 分组**
- 每个分类的租赁次数：  
  ```sql
  SELECT c.name, COUNT(*) AS rentals
  FROM category c
  JOIN film_category fc ON fc.category_id = c.category_id
  JOIN inventory i ON i.film_id = fc.film_id
  JOIN rental r ON r.inventory_id = i.inventory_id
  GROUP BY c.category_id
  ORDER BY rentals DESC;
  ```
- Top 演员（租赁次数）：类似上面，把 `film_actor`、`actor` 加进来，再 group by 演员。

**练习**
1) 查询从未被租过的影片（NOT EXISTS + rental）。  
2) 统计每家门店的库存量和历史租赁次数。  
3) 找出租金超过 4.0 且时长 > 120 的影片对应的分类列表。

---

## 性能优化篇：索引 / 查询优化 / 事务
**目标**：能判断需要索引的列、读懂执行计划、写出不坑别人的事务。  
**索引套路（先查再建，避免重复）：**
- 查看现有：`SHOW INDEX FROM rental;`
- 常用索引示例（先验证是否已存在）：  
  ```sql
  CREATE INDEX idx_rental_customer_date ON rental(customer_id, rental_date);
  CREATE INDEX idx_inventory_store_film ON inventory(store_id, film_id);
  CREATE INDEX idx_film_title ON film(title);
  ```
- 原则：where/join/group/order 用到的列放前面；不要给低选择性的小表滥建索引。

**查询优化**
- 用 `EXPLAIN` / `EXPLAIN ANALYZE` 看执行计划；避免 `type=ALL` 全表扫。  
- 避免 `SELECT *`；只取需要的列，减小行大小。  
- LIMIT 分页大 offset 时用 “游标式”翻页：`WHERE (rental_date,id) > (?,?) ORDER BY rental_date,id LIMIT 50`

**事务处理**
- 读多写少场景：`SET SESSION TRANSACTION ISOLATION LEVEL READ COMMITTED;`
- 写入示例（保持短事务）：  
  ```sql
  START TRANSACTION;
  INSERT INTO rental(...) VALUES (...);
  INSERT INTO payment(...) VALUES (...);
  COMMIT;
  ```
- 避免：长事务占锁、混用自动提交、在事务里跑大查询。

**练习**
1) 对“客户最近租赁”查询跑 `EXPLAIN`，观察是否命中索引。  
2) 设计一条复合索引，让“按分类统计租赁次数”查询不做全表扫描。  
3) 用两个会话演示不可重复读，再切到 READ COMMITTED 观察变化。

---

## Spring Boot 集成篇：JPA/Hibernate 与 MySQL 最佳实践
**目标**：安全接入数据库，不破坏数据，写出可维护的仓储层。  
**依赖**：`spring-boot-starter-data-jpa`、`mysql-connector-j`、建议加 `testcontainers-mysql` 做集成测试。  
**配置示例（application.yml）**：
```yaml
spring:
  datasource:
    url: jdbc:mysql://localhost:3306/sakila?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=UTC
    username: root
    password: pass
  jpa:
    hibernate:
      ddl-auto: none   # 永远不要在这里创建/删除表
    properties:
      hibernate.format_sql: true
      hibernate.jdbc.time_zone: UTC
    open-in-view: false
```
**实体/仓储示例**
```java
@Entity
@Table(name = "film")
public class Film {
  @Id
  @Column(name = "film_id")
  private Short id;
  private String title;
  private BigDecimal rentalRate;
  private Short length;
  // 只映射用得到的列，保持类精简
}

public interface FilmRepository extends JpaRepository<Film, Short> {
  Page<Film> findByTitleContainingIgnoreCase(String title, Pageable pageable);

  @Query("""
    select f.title as title, c.name as category
    from Film f
    join FilmCategory fc on fc.filmId = f.id
    join Category c on c.id = fc.categoryId
    where c.name = :category
  """)
  List<FilmProjection> findByCategory(@Param("category") String category);
}
```
**服务层要点**
- 查询接口加 `@Transactional(readOnly = true)`；写接口显式 `@Transactional`。  
- 避免 N+1：用 `@EntityGraph` 或 DTO 投影。  
- 分页用 `PageRequest.of(page,size,Sort.by("rentalRate").descending())`，不要自造 offset 语句。

---

## MyBatis-Plus 速成：手把手把 Sakila 跑起来
想用 MyBatis-Plus 操作同一个 `sakila` 数据库？按下面抄即可。

### 1) 依赖
在 `demo/pom.xml` 添加（若已存在 JPA，可同时保留，两者互不冲突）：
```xml
<dependency>
  <groupId>com.baomidou</groupId>
  <artifactId>mybatis-plus-boot-starter</artifactId>
  <version>3.5.7</version>
</dependency>
```

### 2) 数据源配置（application.yaml 示例）
```yaml
spring:
  datasource:
    url: jdbc:mysql://localhost:3306/sakila?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=UTC
    username: root
    password: pass
  # 关闭 MyBatis-Plus banner 可选
mybatis-plus:
  configuration:
    map-underscore-to-camel-case: true  # film_id -> filmId 自动映射
  global-config:
    db-config:
      id-type: assign_id   # 主键策略；Sakila 多为自增，可改为 auto
```

### 3) 基础骨架（Film 为例）
**实体**（保留用得到的列） `demo/src/main/java/.../entity/Film.java`
```java
@TableName("film")
@Data
public class Film {
  @TableId(value = "film_id", type = IdType.AUTO)
  private Short id;
  private String title;
  @TableField("rental_rate")
  private BigDecimal rentalRate;
  private Short length;
}
```

**Mapper 接口** `FilmMapper.java`
```java
public interface FilmMapper extends BaseMapper<Film> {
  // 自定义查询示例：按分类查标题列表
  List<Map<String, Object>> selectTitlesByCategory(@Param("category") String category);
}
```

**Mapper XML** `resources/mapper/FilmMapper.xml`
```xml
<?xml version="1.0" encoding="UTF-8" ?>
<!DOCTYPE mapper PUBLIC "-//mybatis.org//DTD Mapper 3.0//EN"
  "http://mybatis.org/dtd/mybatis-3-mapper.dtd">
<mapper namespace="com.example.demo.mapper.FilmMapper">
  <select id="selectTitlesByCategory" resultType="map">
    select f.title as title, c.name as category
    from film f
    join film_category fc on fc.film_id = f.film_id
    join category c on c.category_id = fc.category_id
    where c.name = #{category}
  </select>
</mapper>
```

**Service 层** `FilmService.java`
```java
@Service
public class FilmService {
  private final FilmMapper filmMapper;

  public FilmService(FilmMapper filmMapper) {
    this.filmMapper = filmMapper;
  }

  @Transactional(readOnly = true)
  public IPage<Film> pageByTitle(String title, int page, int size) {
    Page<Film> p = Page.of(page, size).addOrder(OrderItem.desc("rental_rate"));
    LambdaQueryWrapper<Film> qw = Wrappers.<Film>lambdaQuery()
        .like(StringUtils.hasText(title), Film::getTitle, title);
    return filmMapper.selectPage(p, qw);
  }

  @Transactional(readOnly = true)
  public List<Map<String, Object>> listByCategory(String category) {
    return filmMapper.selectTitlesByCategory(category);
  }

  @Transactional
  public Film updateRate(short filmId, BigDecimal newRate) {
    Film f = filmMapper.selectById(filmId);
    if (f == null) throw new IllegalArgumentException("film not found");
    f.setRentalRate(newRate);
    filmMapper.updateById(f);
    return f;
  }
}
```

**Controller 示例** `FilmController.java`
```java
@RestController
@RequestMapping("/films")
public class FilmController {
  private final FilmService filmService;

  public FilmController(FilmService filmService) { this.filmService = filmService; }

  @GetMapping
  public IPage<Film> list(@RequestParam(defaultValue = "") String title,
                          @RequestParam(defaultValue = "1") int page,
                          @RequestParam(defaultValue = "20") int size) {
    return filmService.pageByTitle(title, page, size);
  }

  @GetMapping("/by-category")
  public List<Map<String, Object>> byCategory(@RequestParam String category) {
    return filmService.listByCategory(category);
  }

  @PostMapping("/{id}/rate")
  public Film updateRate(@PathVariable short id, @RequestParam BigDecimal rate) {
    return filmService.updateRate(id, rate);
  }
}
```

### 4) 启动类与扫描
在 `@SpringBootApplication` 同包或上层加扫描：
```java
@MapperScan("com.example.demo.mapper")
@SpringBootApplication
public class DemoApplication { ... }
```

### 5) 最快自检
1. 启动 Docker MySQL（见开头三条命令）。  
2. 运行 Spring Boot；访问：  
   - `GET http://localhost:8080/films?title=ACE` 返回分页结果。  
   - `GET http://localhost:8080/films/by-category?category=Action` 返回该类标题列表。  
   - `POST http://localhost:8080/films/1/rate?rate=4.99` 更新租金。  
3. 控制台无报错即通。

### 6) 常见坑排查
- 驱动报时区错：URL 加 `serverTimezone=UTC`。  
- 数据为空：确认已执行 schema + insert 脚本。  
- XML 未生效：确认 `mapper-locations: classpath*:mapper/*.xml`（默认值），路径写对。  
- 分页从 1 开始：上面代码用 Page.of(page,size)，习惯从 1 起更友好。  
- 自增主键：Sakila 多表 `AUTO_INCREMENT`，`IdType.AUTO` 更贴合。

### 7) 进阶练习
1) 给 `rental` 表写分页查询，带日期区间筛选，验证生成的 SQL 用 `testcontainers` 跑。  
2) 给 `inventory` 查可用库存：`where film_id = ? and store_id = ? and inventory_id not in (select inventory_id from rental where return_date is null)`，并建组合索引 (`store_id`, `film_id`).  
3) 加 Redis 缓存：可复用上文缓存策略，把 `FilmService.listByCategory` 缓存 10 分钟，并在更新租金时清空该分类缓存。

---

## Redis 整合篇：缓存策略与一致性
**目标**：用缓存加速热点查询，同时保证与 MySQL 一致。采用 cache-aside 模式。  
**配置片段**
```yaml
spring:
  cache:
    type: redis
    redis:
      time-to-live: 10m
      cache-null-values: false
  data:
    redis:
      host: localhost
      port: 6379
```
**代码骨架（缓存影片详情）**
```java
@Service
public class FilmService {
  @Cacheable(cacheNames = "film:detail", key = "#id", unless = "#result == null")
  @Transactional(readOnly = true)
  public FilmDetail findDetail(short id) { ... }

  @Caching(evict = {
    @CacheEvict(cacheNames = "film:detail", key = "#cmd.id"),
    @CacheEvict(cacheNames = "film:by-category", allEntries = true)
  })
  @Transactional
  public FilmDetail updateFilm(UpdateFilmCommand cmd) { ... }
}
```
**策略要点**
- Key 设计：`film:detail:{id}`，列表类缓存加上过滤条件哈希（例：`film:by-category:{name}`）。  
- TTL 加随机抖动，防止雪崩；热点可加互斥锁/单飞机制防止击穿。  
- 一致性：写路径先更新数据库，再删缓存（必要时“双删”：提交后异步再删一次）。  
- 不要缓存事务里的未提交数据；不要把分页结果长期缓存（容易脏）。

**练习**
1) 给“按分类查影片”接口加 `@Cacheable`，验证更新影片时缓存被清理。  
2) 用 JMeter/ab 压测有缓存与无缓存的延迟差异。  
3) 模拟缓存击穿：并发获取同一影片详情，加入锁后对比 QPS。

---

## 练习路线图（按顺序做完）
1) 跑通 0 号步骤，确认数据可查。  
2) 完成基础篇 3 个练习。  
3) 写出“门店库存与租赁次数”查询并用 EXPLAIN 检查索引。  
4) 在 Spring Boot 中实现 `GET /films?title=` 的分页接口，接上 JPA 查询。  
5) 给影片详情接口加缓存与更新时的缓存淘汰，跑一次并发压测验证一致性。
