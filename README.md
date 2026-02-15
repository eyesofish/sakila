# Sakila 数据库超简单介绍

Sakila 是一个“录像出租店”示例数据库，方便练习 SQL。下面用最白话的方式帮你记住它的骨架，导入 MySQL Docker 也一并给步骤。

## 核心表，一口气记住

- 电影资料：`film`（片名、时长、租金等），`language`（语言字典），演员在 `actor`；电影和演员多对多用 `film_actor`；类型标签在 `category`，和电影的对应关系放 `film_category`；`film_text` 只是为了全文检索存标题+简介。
- 门店和员工：`store` 是门店，指定一名 `staff` 做店长；每个 `staff` 也属于某个 `store` 并有登录帐号。
- 地址体系：`country` → `city` → `address`，三张表串起来，门店、员工、顾客都引用同样的地址链。
- 顾客：`customer` 记录顾客信息，关联所属门店 (`store_id`) 和住址 (`address_id`)，`active` 标志表示还能不能租。
- 库存（最关键的纽带）：`inventory` 表示“某店里某部电影的一张实体光盘”，外键连到 `film` 和 `store`。
- 业务流水：`rental` 记录“谁在什么时候从哪家店租了哪张盘，由哪个店员办的”；`payment` 记录付款，连到 `rental`、`customer`、`staff`。先有租借再有付款。
- 其他：`store` 和 `staff` 也引用 `address`；脚本里还有 `payment`、`rental` 的触发器/索引等用于性能与数据一致性。

## 在 Docker MySQL 中快速导入

```bash
# 1) 启动容器（示例密码：pass）
docker run -d --name sakila-mysql -e MYSQL_ROOT_PASSWORD=pass -p 3306:3306 mysql:8

# 2) 导入表结构
docker exec -i sakila-mysql mysql -uroot -ppass < mysql-sakila-db/mysql-sakila-schema.sql

# 3) 导入示例数据
docker exec -i sakila-mysql mysql -uroot -ppass < mysql-sakila-db/mysql-sakila-insert-data.sql
```

完成后，容器里的 `sakila` 库就建好并填充了示例数据。

## 重置或清空（可选）

- 只清空数据：`mysql-sakila-db/mysql-sakila-delete-data.sql`
- 连同表一起删除：`mysql-sakila-db/mysql-sakila-drop-objects.sql`
