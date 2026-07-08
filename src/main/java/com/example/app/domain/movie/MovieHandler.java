package com.example.app.domain.movie;

import com.example.app.domain.movie.dto.MovieAverageRatingResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.function.ServerRequest;
import org.springframework.web.servlet.function.ServerResponse;

@Component
public class MovieHandler {
    private final MovieService movieService;

    public MovieHandler(MovieService movieService) {
        this.movieService = movieService;
    }

    public ServerResponse getAverageRating(ServerRequest request) {
        Long movieId = Long.valueOf(request.pathVariable("movieId"));

        return ServerResponse
                .ok()
                .body(new MovieAverageRatingResponse(movieId, movieService.getAverageRating(movieId)));
    }
}
