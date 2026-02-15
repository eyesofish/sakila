package com.example.demo.sakila.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.example.demo.sakila.dto.FilmDto;
import com.example.demo.sakila.dto.FilmTitleCategoryDto;
import com.example.demo.sakila.entity.Film;
import com.example.demo.sakila.mapper.FilmMapper;
import java.math.BigDecimal;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
public class FilmService {
    private final FilmMapper filmMapper;

    public FilmService(FilmMapper filmMapper) {
        this.filmMapper = filmMapper;
    }

    @Transactional(readOnly = true)
    public IPage<FilmDto> pageByTitle(String title, int page, int size) {
        int safePage = Math.max(page, 0);
        int safeSize = size > 0 ? size : 20;

        LambdaQueryWrapper<Film> wrapper = new LambdaQueryWrapper<>();
        if (StringUtils.hasText(title)) {
            wrapper.like(Film::getTitle, title);
        }
        wrapper.orderByDesc(Film::getRentalRate);

        IPage<Film> filmPage = filmMapper.selectPage(new Page<>(safePage + 1L, safeSize), wrapper);
        Page<FilmDto> dtoPage = new Page<>(filmPage.getCurrent(), filmPage.getSize(), filmPage.getTotal());
        dtoPage.setRecords(filmPage.getRecords().stream().map(FilmService::toDto).toList());
        return dtoPage;
    }

    @Transactional(readOnly = true)
    public List<FilmTitleCategoryDto> listByCategory(String category) {
        return filmMapper.selectByCategory(category).stream()
                .map(row -> new FilmTitleCategoryDto(readText(row, "title"), readText(row, "category")))
                .collect(Collectors.toList());
    }

    @Transactional
    public FilmDto updateRentalRate(long filmId, BigDecimal newRate) {
        Film film = filmMapper.selectById(filmId);
        if (film == null) {
            throw new IllegalArgumentException("film not found :" + filmId);
        }
        film.setRentalRate(newRate);
        filmMapper.updateById(film);
        return toDto(film);
    }

    @Transactional(readOnly = true)
    public Double debugSlowSql() {
        return filmMapper.debugSleep250ms();
    }

    private static String readText(Map<String, Object> row, String key) {
        Object value = row.get(key);
        if (value == null) {
            value = row.get(key.toUpperCase(Locale.ROOT));
        }
        return value == null ? null : value.toString();
    }

    private static FilmDto toDto(Film film) {
        return new FilmDto(film.getId(), film.getTitle(), film.getRentalRate(), film.getLength());
    }
}
