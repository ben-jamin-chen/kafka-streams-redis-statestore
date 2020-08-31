package com.kafkastreams.redisstatestore.restapi.dto;

public class MovieAverageRatingResponse {
    public Long movieId;
    public Double rating;

    public MovieAverageRatingResponse(Long movieId, Double rating)
    {
        this.movieId = movieId;
        this.rating = rating;
    }
}
