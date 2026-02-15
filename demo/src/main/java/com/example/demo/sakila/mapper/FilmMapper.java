package com.example.demo.sakila.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.example.demo.sakila.entity.Film;
import java.util.List;
import java.util.Map;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface FilmMapper extends BaseMapper<Film> {

    @Select("""
            select f.title as title, c.name as category
            from film f
            join film_category fc on fc.film_id = f.film_id
            join category c on c.category_id = fc.category_id
            where c.name = #{category}
            """)
    List<Map<String, Object>> selectByCategory(@Param("category") String category);

    @Select("select sleep(0.25)")
    Double debugSleep250ms();
}
