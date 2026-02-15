# Sakila 实战对照：JPA vs MyBatis-Plus（按当前项目）

这份文档不是模板教程，而是基于你现在 `D:\Github\sakila` 里的真实代码来讲。

目标只有一个：你读完后，能明确知道 JPA 和 MyBatis-Plus 在这个项目里到底差在哪、怎么选。

---

## 0) 先看当前项目状态（非常关键）

你现在的 `demo` 项目已经是 **MyBatis-Plus 主线**：

- 已保留依赖：`mybatis-plus-spring-boot3-starter`（`demo/pom.xml`）
- 已移除依赖：`spring-boot-starter-data-jpa`（`demo/pom.xml`）
- 已有 MP 配置：`demo/src/main/java/com/example/demo/sakila/config/MybatisPlusConfig.java`
- 已有 Mapper：`demo/src/main/java/com/example/demo/sakila/mapper/FilmMapper.java`
- Service 已改为调用 Mapper：`demo/src/main/java/com/example/demo/sakila/service/FilmService.java`
- Controller 对外 API 没变：`demo/src/main/java/com/example/demo/sakila/web/FilmController.java`
- 原 JPA Repository 已删除：`demo/src/main/java/com/example/demo/sakila/repository/FilmRepository.java`

一句话总结当前结构：

`Controller -> Service -> MyBatis-Plus Mapper -> MySQL`

---

## 1) 用同一个需求看差异（最容易理解）

我们用同一组 API 做对照：

- `GET /api/films?title=ACE&page=0&size=5`
- `GET /api/films/by-category?category=Action`
- `POST /api/films/{id}/rental-rate?rate=2.99`

### 1.1 分页模糊查询：JPA vs MP

JPA 常见写法（你之前项目里就是这个思路）：

```java
PageRequest pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "rentalRate"));
return filmRepository.findByTitleContainingIgnoreCase(title, pageable);
```

MyBatis-Plus 当前写法（你现在真实代码）：

```java
LambdaQueryWrapper<Film> wrapper = new LambdaQueryWrapper<>();
if (StringUtils.hasText(title)) {
    wrapper.like(Film::getTitle, title);
}
wrapper.orderByDesc(Film::getRentalRate);

IPage<Film> filmPage = filmMapper.selectPage(new Page<>(safePage + 1L, safeSize), wrapper);
```

差异点：

- JPA：更像“声明意图”，SQL 细节交给框架推导。
- MP：更像“自己搭 SQL 条件”，控制感更强。
- 注意页码：Spring Data 常用 0-based；MP 的 `Page` 是 1-based，所以代码里做了 `+1`。

### 1.2 多表查询：JPA vs MP

JPA 常见写法：

- `@Query(nativeQuery = true)` + projection interface。

MP 当前写法：

- 直接在 `FilmMapper` 里 `@Select` 写 SQL：

```java
@Select("""
        select f.title as title, c.name as category
        from film f
        join film_category fc on fc.film_id = f.film_id
        join category c on c.category_id = fc.category_id
        where c.name = #{category}
        """)
List<Map<String, Object>> selectByCategory(@Param("category") String category);
```

差异点：

- JPA：投影接口很优雅，但复杂 SQL 改动时要来回看实体/投影。
- MP：SQL 就在 Mapper，调优时非常直接。

### 1.3 更新字段：JPA vs MP

JPA 典型是：

1. `findById`
2. `setXxx`
3. `save`（依赖持久化上下文脏检查）

MP 当前是：

1. `selectById`
2. `setXxx`
3. `updateById`

差异点：

- JPA 更偏“对象状态驱动”。
- MP 更偏“显式 SQL 操作”。

---

## 2) 在这个项目里，JPA 和 MP 的本质区别

| 维度 | JPA（你之前那版） | MyBatis-Plus（你现在这版） |
|---|---|---|
| 思维模型 | 面向对象映射（Entity 状态） | 面向表和 SQL |
| 简单 CRUD | 很省代码 | 也省代码（`BaseMapper`） |
| 动态条件 | `Specification/Criteria` 写起来偏重 | `LambdaQueryWrapper` 更直观 |
| 复杂联表 | 常转 native SQL | 直接写 SQL，非常自然 |
| SQL 可控性 | 中等（框架生成） | 高（你自己定义） |
| 学习曲线 | ORM 概念更多 | SQL 能力要求更高 |
| 调优体验 | 先看 ORM 生成 SQL | 直接盯 SQL 本身 |

---

## 3) 为什么你这个项目切 MP 后更容易“看懂 SQL 性能”

你项目里已经有：

- SQL 耗时日志：`demo/src/main/java/com/example/demo/sakila/config/SqlSlowQueryListener.java`
- DataSource 代理：`demo/src/main/java/com/example/demo/sakila/config/DataSourceProxyConfig.java`
- Service 耗时 AOP：`demo/src/main/java/com/example/demo/sakila/aop/ServiceCostAspect.java`

当你使用 MP 时，Mapper 里 SQL 更直观，看到慢日志后，通常可以直接定位到具体 SQL 片段，而不用先还原 ORM 生成链路。

---

## 4) 最小实操：你现在就能跑的验证

在 `demo` 目录启动：

```bash
.\mvnw.cmd spring-boot:run
```

然后请求：

```bash
curl "http://localhost:8080/api/films?title=ACE&page=0&size=5"
curl "http://localhost:8080/api/films/by-category?category=Action"
curl -X POST "http://localhost:8080/api/films/1/rental-rate?rate=2.99"
curl "http://localhost:8080/api/films/debug/slow-sql"
```

你会看到：

- 正常 SQL 在控制台输出；
- 超过阈值的 SQL 被打到 `SLOW_SQL`；
- Service 方法耗时打到 `SERVICE_COST`。

---

## 5) 如果你要“再对照一次 JPA 版本”怎么做

你现在工作区是 MP 版本。想回看 JPA 写法，可以用 git 对比历史实现（建议新分支看）：

```bash
git switch -c review-jpa 5d61007
```

回到当前 MP：

```bash
git switch main
```

也可以直接看某个文件演进：

```bash
git diff 5d61007 -- demo/src/main/java/com/example/demo/sakila/service/FilmService.java
```

---

## 6) 面试可直接说的 20 秒版本

在这个 Sakila 项目里，我们把 `Film` 链路从 JPA 改成了 MyBatis-Plus。  
JPA 优势是对象映射一致性好、简单 CRUD 很顺；MyBatis-Plus 优势是 SQL 可控、复杂查询和调优更直接。  
如果业务复杂联表多、强调 SQL 可解释性，我会优先 MP；如果领域建模复杂、对象关系重，我会优先 JPA。

---

## 7) 你接下来该怎么学（按这个项目）

1. 先只盯 `FilmController -> FilmService -> FilmMapper` 这一条链路跑通。  
2. 每改一个查询条件，就看一次慢 SQL 日志和 `EXPLAIN`。  
3. 把 `by-category` 从 `Map` 返回改成 DTO 映射，练一次“可维护性优化”。  
4. 再决定要不要补 `Customer` 的 MP 全套（Entity/Mapper/Service/Controller）。  
