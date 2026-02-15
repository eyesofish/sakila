package com.example.demo.sakila.repository;

import com.example.demo.sakila.entity.Film;
import com.example.demo.sakila.repository.projection.FilmTitleCategoryView;
import java.util.List;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface FilmRepository extends JpaRepository<Film, Long> {// 操作的是Film实体类,主键为Long
    Page<Film> findByTitleContainingIgnoreCase(String title, Pageable pageable);
    // 它会自动翻译成 SQL：

    // where lower(title) like lower('%xxx%') ，这里的title 必须对应Entity 里的title
    @Query(value = """
            select f.title as title, c.name as category
            from film f
            join film_category fc on fc.film_id = f.film_id
            join category c on c.category_id = fc.category_id
            where c.name = :category
            """, nativeQuery = true)
    List<FilmTitleCategoryView> findByCategory(@Param("category") String category);

    @Query(value = "select sleep(0.25)", nativeQuery = true)
    Double debugSleep250ms();
}