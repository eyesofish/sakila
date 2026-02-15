package com.example.demo.sakila.dto;

import java.math.BigDecimal;

public record FilmDto(
	Long id,
	String title,
	BigDecimal rentalRate,
	Integer length
) {}

