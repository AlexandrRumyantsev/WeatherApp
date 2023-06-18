package com.example.weatherapp.adapters

data class WeatherModel(
    val city: String,
    val time: String,
    val day: String,
    val condition: String,
    val currentTemp: String,
    val minTemp: String,
    val maxTemp: String,
    val imageUrl: String,
    val windSpeed: String,
    val wet: String,
    val hours: String
)
