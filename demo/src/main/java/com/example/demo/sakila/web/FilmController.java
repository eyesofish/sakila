package com.example.demo.sakila.web;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.example.demo.sakila.dto.FilmDto;
import com.example.demo.sakila.dto.FilmTitleCategoryDto;
import com.example.demo.sakila.service.FilmService;
import java.math.BigDecimal;
import java.util.List;
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

    @GetMapping
    public IPage<FilmDto> page(
            @RequestParam(defaultValue = "") String title,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return filmService.pageByTitle(title, page, size);

    }

    @GetMapping("/by-category")
    public List<FilmTitleCategoryDto> byCategory(@RequestParam String category) {
        return filmService.listByCategory(category);
    }

    @PostMapping("/{id}/rental-rate")
    public FilmDto updateRentalRate(@PathVariable long id, @RequestParam BigDecimal rate) {
        return filmService.updateRentalRate(id, rate);
    }

    @GetMapping("/debug/slow-sql")
    public Double debugSlowSql() {
        return filmService.debugSlowSql();
    }
}
