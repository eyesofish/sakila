# MyBatis-Plus 操作 Sakila 数据库完整傻瓜教程

## 目录
1. [前置准备](#前置准备)
2. [项目初始化](#项目初始化)
3. [MyBatis-Plus 配置](#mybatis-plus-配置)
4. [实体类创建](#实体类创建)
5. [Mapper 接口开发](#mapper-接口开发)
6. [Service 层开发](#service-层开发)
7. [Controller 层开发](#controller-层开发)
8. [实战案例](#实战案例)
9. [高级特性](#高级特性)

## 前置准备

### 知识点：MyBatis-Plus 简介
MyBatis-Plus (简称 MP) 是一个 MyBatis 的增强工具，在 MyBatis 的基础上只做增强不做改变，为简化开发、提高效率而生。

**核心特性：**
- 无侵入：只做增强不修改，引入即可用
- 损耗小：启动即会自动注入基本 CRUD，性能基本无损耗
- 强大的 CRUD 操作：内置通用 Mapper、通用 Service
- 支持 Lambda 形式调用：利用 Java8 的 Lambda 表达式
- 支持主键自动生成策略

### 准备工作
1. 确保你的 Docker 已经启动了 MySQL 数据库，并且 sakila 数据库已准备好
2. 确认 Sakila 数据库已经导入到 MySQL 中
3. 确保你有 Maven、Java 21 环境

## 项目初始化

### 步骤 1：检查现有项目结构
当前项目位于 `D:\Github\sakila\demo`，我们将在该 Spring Boot 项目基础上进行开发。

### 步骤 2：验证 pom.xml 依赖
确认 [pom.xml](file:///d:/Github/sakila/demo/pom.xml) 中已包含必要的依赖：
- `mybatis-plus-boot-starter`
- `mysql-connector-j`
- `spring-boot-starter-data-jpa`

如果缺少 MyBatis-Plus 依赖，添加以下依赖：
```xml
<dependency>
    <groupId>com.baomidou</groupId>
    <artifactId>mybatis-plus-boot-starter</artifactId>
    <version>3.5.7</version>
</dependency>
```

### 步骤 3：更新数据库连接配置
编辑 [application.yaml](file:///d:/Github/sakila/demo/src/main/resources/application.yaml) 文件，确保配置如下：
```yaml
spring:
  datasource:
    driver-class-name: com.mysql.cj.jdbc.Driver
    url: jdbc:mysql://localhost:3306/sakila?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=UTC&nullCatalogMeansCurrent=true
    username: root
    password: pass
    type: com.zaxxer.hikari.HikariDataSource

# MyBatis-Plus 配置
mybatis-plus:
  mapper-locations: classpath*:/mapper/**/*Mapper.xml
  type-aliases-package: com.example.demo.entity
  configuration:
    map-underscore-to-camel-case: true
    cache-enabled: false
    lazy-loading-enabled: false
    aggressive-lazy-loading: true
    log-impl: org.apache.ibatis.logging.stdout.StdOutImpl
  global-config:
    db-config:
      logic-delete-field: deleted  # 逻辑删除字段名
      logic-delete-value: 1        # 逻辑删除值
      logic-not-delete-value: 0    # 逻辑未删除值
      id-type: auto                # ID 类型：自增
```

## MyBatis-Plus 配置

### 步骤 4：创建 MyBatis-Plus 配置类
在 `com.example.demo.config` 包下创建 [MybatisPlusConfig.java](file:///d:/Github/sakila/demo/src/main/java/com/example/demo/config/MybatisPlusConfig.java)：

```java
package com.example.demo.config;

import com.baomidou.mybatisplus.annotation.DbType;
import com.baomidou.mybatisplus.extension.plugins.MybatisPlusInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.PaginationInnerInterceptor;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@MapperScan("com.example.demo.mapper")  // 配置 Mapper 扫描
public class MybatisPlusConfig {

    /**
     * 分页插件配置
     * 知识点：PaginationInnerInterceptor 是 MyBatis-Plus 内置的分页插件
     */
    @Bean
    public MybatisPlusInterceptor mybatisPlusInterceptor() {
        MybatisPlusInterceptor interceptor = new MybatisPlusInterceptor();
        // 添加分页拦截器
        interceptor.addInnerInterceptor(new PaginationInnerInterceptor(DbType.MYSQL));
        return interceptor;
    }
}
```

## 实体类创建

### 步骤 5：创建实体类包结构
在 `com.example.demo.entity` 包下创建以下实体类，我们以 Sakila 数据库中最常用的 [customer](file:///d:/Github/sakila/postgres-sakila-db/postgres-sakila-schema.sql#L614-L625) 表为例：

```java
package com.example.demo.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.Accessors;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * <p>
 * 客户实体类
 * </p>
 *
 * @author lingma
 * @since 2026-02-02
 */
@Data
@EqualsAndHashCode(callSuper = false)
@Accessors(chain = true)
@TableName("customer")
public class Customer implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 知识点：TableId 注解用于标识主键
     * IdType.AUTO 表示自增长
     */
    @TableId(value = "customer_id", type = IdType.AUTO)
    private Integer customerId;

    private Integer storeId;

    private String firstName;

    private String lastName;

    private String email;

    private Integer addressId;

    private Boolean activebool;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime create_date;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime lastUpdate;

    private Boolean active;
}
```

### 步骤 6：创建其他实体类
类似地，为 Sakila 数据库中常用表创建实体类，例如 [film](file:///d:/Github/sakila/postgres-sakila-db/postgres-sakila-schema.sql#L575-L589) 表：

```java
package com.example.demo.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.Accessors;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * <p>
 * 电影实体类
 * </p>
 *
 * @author lingma
 * @since 2026-02-02
 */
@Data
@EqualsAndHashCode(callSuper = false)
@Accessors(chain = true)
@TableName("film")
public class Film implements Serializable {

    private static final long serialVersionUID = 1L;

    @TableId(value = "film_id", type = IdType.AUTO)
    private Integer filmId;

    private String title;

    private String description;

    private String releaseYear;

    private Integer languageId;

    private Integer originalLanguageId;

    private Integer rentalDuration;

    private BigDecimal rentalRate;

    private Integer length;

    private BigDecimal replacementCost;

    private String rating;

    private String specialFeatures;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime lastUpdate;
}
```

> 知识点：@TableName 注解指定映射的数据库表名；@TableId 指定主键；Lombok 注解减少样板代码。

## Mapper 接口开发

### 步骤 7：创建 Mapper 接口
在 `com.example.demo.mapper` 包下创建 [CustomerMapper.java](file:///d:/Github/sakila/demo/src/main/java/com/example/demo/mapper/CustomerMapper.java)：

```java
package com.example.demo.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.example.demo.entity.Customer;
import org.apache.ibatis.annotations.Mapper;

/**
 * <p>
 * 客户表 Mapper 接口
 * </p>
 *
 * @author lingma
 * @since 2026-02-02
 */
@Mapper
public interface CustomerMapper extends BaseMapper<Customer> {

}
```

### 步骤 8：创建自定义查询方法
在 CustomerMapper 中添加自定义查询方法：

```java
package com.example.demo.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.example.demo.entity.Customer;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * <p>
 * 客户表 Mapper 接口
 * </p>
 *
 * @author lingma
 * @since 2026-02-02
 */
@Mapper
public interface CustomerMapper extends BaseMapper<Customer> {
    
    /**
     * 根据姓氏查询客户
     * 知识点：MyBatis-Plus 支持自定义 SQL 查询
     */
    List<Customer> selectByLastName(@Param("lastName") String lastName);
    
    /**
     * 分页查询活跃客户
     * 知识点：使用 IPage 实现分页查询
     */
    IPage<Customer> selectActiveCustomers(Page<Customer> page, @Param("active") Boolean active);
}
```

### 步骤 9：创建对应的 XML 映射文件
在 `resources/mapper` 目录下创建 CustomerMapper.xml 文件：

```xml
<?xml version="1.0" encoding="UTF-8"?>
<!DOCTYPE mapper PUBLIC "-//mybatis.org//DTD Mapper 3.0//EN" "http://mybatis.org/dtd/mybatis-3-mapper.dtd">
<mapper namespace="com.example.demo.mapper.CustomerMapper">

    <!-- 根据姓氏查询客户 -->
    <select id="selectByLastName" resultType="com.example.demo.entity.Customer">
        SELECT * FROM customer WHERE last_name = #{lastName}
    </select>
    
    <!-- 分页查询活跃客户 -->
    <select id="selectActiveCustomers" resultType="com.example.demo.entity.Customer">
        SELECT * FROM customer WHERE active = #{active}
    </select>
</mapper>
```

## Service 层开发

### 步骤 10：创建 Service 接口
在 `com.example.demo.service` 包下创建 [ICustomerService.java](file:///d:/Github/sakila/demo/src/main/java/com/example/demo/service/ICustomerService.java)：

```java
package com.example.demo.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.IService;
import com.example.demo.entity.Customer;

/**
 * <p>
 * 客户表 服务类
 * </p>
 *
 * @author lingma
 * @since 2026-02-02
 */
public interface ICustomerService extends IService<Customer> {
    
    /**
     * 获取活跃客户分页列表
     * 知识点：IService 已经提供了基础的 CRUD 方法
     */
    IPage<Customer> getActiveCustomersWithPagination(int pageNum, int pageSize, Boolean active);
    
    /**
     * 根据姓氏查找客户
     */
    java.util.List<Customer> findByLastName(String lastName);
}
```

### 步骤 11：创建 Service 实现类
在 `com.example.demo.service.impl` 包下创建 [CustomerServiceImpl.java](file:///d:/Github/sakila/demo/src/main/java/com/example/demo/service/impl/CustomerServiceImpl.java)：

```java
package com.example.demo.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.example.demo.entity.Customer;
import com.example.demo.mapper.CustomerMapper;
import com.example.demo.service.ICustomerService;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * <p>
 * 客户表 服务实现类
 * </p>
 *
 * @author lingma
 * @since 2026-02-02
 */
@Service
public class CustomerServiceImpl extends ServiceImpl<CustomerMapper, Customer> implements ICustomerService {

    @Override
    public IPage<Customer> getActiveCustomersWithPagination(int pageNum, int pageSize, Boolean active) {
        // 创建分页对象
        Page<Customer> page = new Page<>(pageNum, pageSize);
        
        // 构造查询条件
        QueryWrapper<Customer> wrapper = new QueryWrapper<>();
        wrapper.eq("active", active);
        
        // 执行分页查询
        return this.page(page, wrapper);
    }

    @Override
    public List<Customer> findByLastName(String lastName) {
        // 使用 QueryWrapper 构造查询条件
        QueryWrapper<Customer> wrapper = new QueryWrapper<>();
        wrapper.eq("last_name", lastName);
        return this.list(wrapper);
    }
}
```

> 知识点：QueryWrapper 是 MyBatis-Plus 提供的条件构造器，可以链式调用构建复杂查询条件。

## Controller 层开发

### 步骤 12：创建 Controller 类
在 `com.example.demo.controller` 包下创建 [CustomerController.java](file:///d:/Github/sakila/demo/src/main/java/com/example/demo/controller/CustomerController.java)：

```java
package com.example.demo.controller;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.example.demo.entity.Customer;
import com.example.demo.service.ICustomerService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * <p>
 * 客户前端控制器
 * </p>
 *
 * @author lingma
 * @since 2026-02-02
 */
@RestController
@RequestMapping("/customer")
public class CustomerController {

    @Autowired
    private ICustomerService customerService;

    /**
     * 查询所有客户
     * 知识点：IService 的 list() 方法无需实现，直接可用
     */
    @GetMapping("/all")
    public List<Customer> getAllCustomers() {
        return customerService.list();
    }

    /**
     * 分页查询客户
     */
    @GetMapping("/page/{current}/{size}")
    public IPage<Customer> getCustomersByPage(
            @PathVariable int current,
            @PathVariable int size) {
        return customerService.page(new com.baomidou.mybatisplus.extension.plugins.pagination.Page<>(current, size));
    }

    /**
     * 根据ID获取客户详情
     */
    @GetMapping("/{id}")
    public Customer getCustomerById(@PathVariable Integer id) {
        return customerService.getById(id);
    }

    /**
     * 创建新客户
     */
    @PostMapping
    public boolean createCustomer(@RequestBody Customer customer) {
        return customerService.save(customer);
    }

    /**
     * 更新客户信息
     */
    @PutMapping
    public boolean updateCustomer(@RequestBody Customer customer) {
        return customerService.updateById(customer);
    }

    /**
     * 删除客户
     */
    @DeleteMapping("/{id}")
    public boolean deleteCustomer(@PathVariable Integer id) {
        return customerService.removeById(id);
    }

    /**
     * 分页查询活跃客户
     */
    @GetMapping("/active/{current}/{size}")
    public IPage<Customer> getActiveCustomers(
            @PathVariable int current,
            @PathVariable int size,
            @RequestParam(defaultValue = "true") Boolean active) {
        return customerService.getActiveCustomersWithPagination(current, size, active);
    }

    /**
     * 根据姓氏查找客户
     */
    @GetMapping("/lastname/{lastName}")
    public List<Customer> getCustomersByLastName(@PathVariable String lastName) {
        return customerService.findByLastName(lastName);
    }
}
```

## 实战案例

### 步骤 13：运行并测试 API
1. 启动应用程序：`mvn spring-boot:run`
2. 访问以下 URL 测试功能：
   - 获取所有客户：`GET http://localhost:8080/customer/all`
   - 分页获取客户：`GET http://localhost:8080/customer/page/1/10`
   - 根据姓氏查找：`GET http://localhost:8080/customer/lastname/Smith`

### 步骤 14：创建更多业务逻辑
让我们创建一个涉及多个表关联查询的复杂案例，例如查询客户的租赁历史：

```java
// 在 Rental 实体类中
package com.example.demo.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.Accessors;

import java.io.Serializable;
import java.time.LocalDateTime;

@Data
@EqualsAndHashCode(callSuper = false)
@Accessors(chain = true)
@TableName("rental")
public class Rental implements Serializable {

    private static final long serialVersionUID = 1L;

    @TableId(value = "rental_id", type = IdType.AUTO)
    private Integer rentalId;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime rentalDate;

    private Integer inventoryId;

    private Integer customerId;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime returnDate;

    private Integer staffId;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime lastUpdate;
}
```

```java
// 在 RentalMapper 中
@Mapper
public interface RentalMapper extends BaseMapper<Rental> {
    // 使用原生 SQL 连接查询客户租赁历史
    @Select("SELECT r.*, c.first_name, c.last_name, f.title " +
            "FROM rental r " +
            "JOIN customer c ON r.customer_id = c.customer_id " +
            "JOIN inventory i ON r.inventory_id = i.inventory_id " +
            "JOIN film f ON i.film_id = f.film_id " +
            "WHERE r.customer_id = #{customerId} " +
            "ORDER BY r.rental_date DESC LIMIT #{limit}")
    List<Map<String, Object>> selectRentalHistory(@Param("customerId") Integer customerId, @Param("limit") Integer limit);
}
```

## 高级特性

### 条件构造器使用
MyBatis-Plus 提供了强大的条件构造器，可以轻松构建复杂查询：

```java
// 查询姓氏包含特定字符的活跃客户
QueryWrapper<Customer> wrapper = new QueryWrapper<>();
wrapper.like("last_name", "SM").eq("active", true);

// 更复杂的查询
wrapper.gt("customer_id", 100)  // ID大于100
      .orderByDesc("create_date")  // 按创建日期降序排列
      .last("LIMIT 10");  // 添加SQL片段
```

### 自动填充功能
在实体类中添加自动填充字段：

```java
@TableField(fill = FieldFill.INSERT)  // 插入时自动填充
@JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
private LocalDateTime createTime;

@TableField(fill = FieldFill.INSERT_UPDATE)  // 插入和更新时自动填充
@JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
private LocalDateTime updateTime;
```

创建自动填充处理器：

```java
@Component
public class MyMetaObjectHandler implements MetaObjectHandler {
    @Override
    public void insertFill(MetaObject metaObject) {
        this.strictInsertFill(metaObject, "createTime", LocalDateTime.class, LocalDateTime.now());
        this.strictInsertFill(metaObject, "updateTime", LocalDateTime.class, LocalDateTime.now());
    }

    @Override
    public void updateFill(MetaObject metaObject) {
        this.strictUpdateFill(metaObject, "updateTime", LocalDateTime.class, LocalDateTime.now());
    }
}
```

### 乐观锁插件
当多用户同时操作同一数据时，使用乐观锁防止数据冲突：

```java
// 在实体类中添加版本字段
@TableField(fill = FieldFill.INSERT)
@Version
private Integer version;
```

在配置类中添加乐观锁插件：

```java
@Bean
public MybatisPlusInterceptor mybatisPlusInterceptor() {
    MybatisPlusInterceptor interceptor = new MybatisPlusInterceptor();
    // 添加乐观锁插件
    interceptor.addInnerInterceptor(new OptimisticLockerInnerInterceptor());
    // 添加分页插件
    interceptor.addInnerInterceptor(new PaginationInnerInterceptor(DbType.MYSQL));
    return interceptor;
}
```

## 总结

通过以上步骤，我们完成了：
1. 配置 MyBatis-Plus 与 Sakila 数据库的集成
2. 创建了实体类、Mapper、Service 和 Controller
3. 实现了基础的 CRUD 操作
4. 使用了分页查询、条件查询等高级功能
5. 了解了条件构造器的使用方法

MyBatis-Plus 大幅减少了数据访问层的样板代码，让开发者专注于业务逻辑实现。在实际企业开发中，这种工具能够显著提升开发效率。