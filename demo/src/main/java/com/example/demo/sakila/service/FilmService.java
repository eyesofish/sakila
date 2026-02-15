package com.example.demo.sakila.service;

import com.example.demo.sakila.dto.FilmDto;
import com.example.demo.sakila.dto.FilmTitleCategoryDto;
import com.example.demo.sakila.entity.Film;
import com.example.demo.sakila.repository.FilmRepository;
import com.example.demo.sakila.repository.projection.FilmTitleCategoryView;
import java.math.BigDecimal;
import java.util.List;
import java.util.stream.Collectors;
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

	@Transactional(readOnly = true)
	public Page<FilmDto> pageByTitle(String title, int page, int size) {
		String safeTitle = StringUtils.hasText(title) ? title : "";
		PageRequest pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "rentalRate"));
		return filmRepository.findByTitleContainingIgnoreCase(safeTitle, pageable).map(FilmService::toDto);
	}

	@Transactional(readOnly = true)
	public List<FilmTitleCategoryDto> listByCategory(String category) {
		List<FilmTitleCategoryView> views = filmRepository.findByCategory(category);
		return views.stream()
				.map(v -> new FilmTitleCategoryDto(v.getTitle(), v.getCategory()))
				.collect(Collectors.toList());
	}

	@Transactional
	public FilmDto updateRentalRate(long filmId, BigDecimal newRate) {
		Film film = filmRepository.findById(filmId)
				.orElseThrow(() -> new IllegalArgumentException("film not found :" + filmId));
		film.setRentalRate(newRate);
		return toDto(filmRepository.save(film));
	}

	private static FilmDto toDto(Film film) {
		return new FilmDto(film.getId(), film.getTitle(), film.getRentalRate(), film.getLength());
	}

	@Transactional(readOnly = true)
	public Double debugSlowSql() {
		return filmRepository.debugSleep250ms();
	}
}
