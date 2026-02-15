# Sakila 实战对照：JPA 视角（与 MP 文档一一对应）

这份文档和 `my-batis-plus_demo.md` 是配套的。  
它讲同一套需求，但从 JPA 视角解释为什么这样写、什么时候更合适。

目标只有一个：你读完后，能把 JPA 和 MyBatis-Plus 的差异说清楚，而不是只会背概念。

---

## 0) 先看当前项目状态（非常关键）

你现在工作区的 `demo` 项目是 **MyBatis-Plus 主线**，不是 JPA 主线：

- 当前依赖里保留了 `mybatis-plus-spring-boot3-starter`：`demo/pom.xml`
- `spring-boot-starter-data-jpa` 已移除：`demo/pom.xml`
- 现在运行链路是 `Controller -> Service -> Mapper`

所以这份文档里出现的 JPA 代码，主要用于“对照理解”。  
如果你要亲手跑 JPA 版本，建议切到历史提交（后面第 4 节给命令）。

---

## 1) 用同一个需求看差异（最容易理解）

同一组 API：

- `GET /api/films?title=ACE&page=0&size=5`
- `GET /api/films/by-category?category=Action`
- `POST /api/films/{id}/rental-rate?rate=2.99`

### 1.1 分页模糊查询：JPA vs MP

JPA 常见写法：

```java
PageRequest pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "rentalRate"));
return filmRepository.findByTitleContainingIgnoreCase(safeTitle, pageable).map(FilmService::toDto);
```

MP 当前写法：

```java
LambdaQueryWrapper<Film> wrapper = new LambdaQueryWrapper<>();
if (StringUtils.hasText(title)) {
    wrapper.like(Film::getTitle, title);
}
wrapper.orderByDesc(Film::getRentalRate);

IPage<Film> filmPage = filmMapper.selectPage(new Page<>(safePage + 1L, safeSize), wrapper);
```

差异点：

- JPA：接口命名 + 分页对象就能表达查询意图，业务层更“面向对象”。
- MP：条件构造更灵活，SQL 形状更可控。
- 页码细节：JPA 常用 0-based；MP 常见 1-based。

### 1.2 多表查询：JPA vs MP

JPA 常见写法：

- Repository 中用 `@Query(nativeQuery = true)`。
- 配 projection interface 接返回字段。

示例：

```java
@Query(value = """
        select f.title as title, c.name as category
        from film f
        join film_category fc on fc.film_id = f.film_id
        join category c on c.category_id = fc.category_id
        where c.name = :category
        """, nativeQuery = true)
List<FilmTitleCategoryView> findByCategory(@Param("category") String category);
```

MP 常见写法：

- 在 Mapper 中直接 `@Select` 写 SQL。

差异点：

- JPA：结果映射抽象更统一，仓储接口风格一致。
- MP：SQL 调优时路径更短，定位更直接。

### 1.3 更新字段：JPA vs MP

JPA 典型写法：

1. `findById`
2. 修改实体字段
3. `save`

示例：

```java
Film film = filmRepository.findById(filmId)
        .orElseThrow(() -> new IllegalArgumentException("film not found :" + filmId));
film.setRentalRate(newRate);
return toDto(filmRepository.save(film));
```

MP 典型写法：

1. `selectById`
2. 修改实体字段
3. `updateById`

差异点：

- JPA 更强调实体状态和持久化上下文。
- MP 更强调“这次要执行哪条 SQL”。

---

## 2) 在这个项目里，JPA 和 MP 的本质区别

| 维度 | JPA 视角 | MyBatis-Plus 视角 |
|---|---|---|
| 思维模型 | 以实体关系和对象生命周期为中心 | 以 SQL 和条件构造为中心 |
| 简单 CRUD | 非常顺滑，样板少 | 也很少样板（`BaseMapper`） |
| 动态条件 | `Specification/Criteria` 可做但偏重 | `LambdaQueryWrapper` 更直观 |
| 复杂联表 | 常转 native SQL | 直接写 SQL 很自然 |
| SQL 可控性 | 中等 | 高 |
| 调优入口 | 先看 ORM 最终 SQL | 直接看 Mapper SQL |
| 代码风格 | 更偏领域建模 | 更偏数据访问工程化 |

---

## 3) 为什么有些团队仍优先 JPA

在“领域对象关系复杂、业务规则多”的系统里，JPA 的优势通常更明显：

- 实体关系建模统一（聚合、关联、生命周期）。
- Repository 风格一致，业务开发同学门槛更低。
- 同一事务里实体状态流转更自然。

在这个 Sakila 练习项目里，联表和 SQL 调优练习很多，所以 MP 体感更直接。  
但如果你做的是强领域模型业务，JPA 往往更省心。

---

## 4) 最小实操：把当前项目切到 JPA 版本再跑一遍

在仓库根目录执行：

```bash
git switch -c review-jpa 5d61007
cd demo
.\mvnw.cmd spring-boot:run
```

测试同一组接口：

```bash
curl "http://localhost:8080/api/films?title=ACE&page=0&size=5"
curl "http://localhost:8080/api/films/by-category?category=Action"
curl -X POST "http://localhost:8080/api/films/1/rental-rate?rate=2.99"
curl "http://localhost:8080/api/films/debug/slow-sql"
```

你应该关注两件事：

- 相同 API 在 JPA/MP 下，数据返回结构是否一致。
- 慢 SQL 和方法耗时日志是否都能正常观测。

---

## 5) 如何回到当前 MP 版本

```bash
git switch main
```

如果你只想看关键差异，不切分支也可以直接对比：

```bash
git diff 5d61007 -- demo/src/main/java/com/example/demo/sakila/service/FilmService.java
```

---

## 6) 面试可直接说的 20 秒版本

这个项目我把同一条 `Film` 业务链路分别用 JPA 和 MyBatis-Plus 做了对照。  
JPA 的优势是面向对象建模一致、CRUD 和事务语义自然；MyBatis-Plus 的优势是 SQL 可控、联表和调优更直接。  
业务偏领域模型时我优先 JPA，业务偏数据查询和性能可解释性时我优先 MP。

---

## 7) 你接下来该怎么学（按这两份文档对照）

1. 先按本文件切到 JPA 版本跑通同一组接口。  
2. 再切回 `main` 跑 MP 版本，观察同一请求的 SQL 和耗时差异。  
3. 专门对比 `FilmService` 在两种实现里的“分页、联表、更新”写法。  
4. 最后自己写一条新需求（比如按 `rentalRate` 区间筛选）各做一版。  
