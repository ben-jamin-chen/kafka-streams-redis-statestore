package com.example.app.domain.movie;

import com.example.app.domain.movie.dto.MovieAverageRatingResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.enums.ParameterIn;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springdoc.core.annotations.RouterOperation;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.servlet.function.RouterFunction;
import org.springframework.web.servlet.function.RouterFunctions;
import org.springframework.web.servlet.function.ServerResponse;

@Configuration
public class MovieRoutes {
    private static final Logger logger = LoggerFactory.getLogger(MovieRoutes.class);

    @Bean
    @RouterOperation(path = "/v1/movie/{movieId}/rating", method = RequestMethod.GET,
            operation = @Operation(operationId = "getMovieAverageRating",
                    summary = "Returns the average rating for a particular movie",
                    parameters = @Parameter(in = ParameterIn.PATH, name = "movieId",
                            description = "Movie identifier", required = true, example = "362"),
                    responses = {
                            @ApiResponse(responseCode = "200", description = "successful operation",
                                    content = @Content(schema = @Schema(implementation = MovieAverageRatingResponse.class))),
                            @ApiResponse(responseCode = "500", description = "internal server error")}))
    public RouterFunction<ServerResponse> movieRouterFunction(MovieHandler movieHandler) {
        return RouterFunctions.route()
                .GET("/v1/movie/{movieId}/rating", movieHandler::getAverageRating)
                .onError(Exception.class, (ex, request) -> {
                    logger.error("Failed due to exception: {}", ex.getMessage());
                    return ServerResponse.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
                })
                .build();
    }
}
