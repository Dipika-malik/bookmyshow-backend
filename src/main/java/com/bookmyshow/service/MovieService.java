package com.bookmyshow.service;

import com.bookmyshow.config.RedisConfig;
import com.bookmyshow.dto.request.MovieRequest;
import com.bookmyshow.dto.response.MovieResponse;
import com.bookmyshow.exception.ResourceNotFoundException;
import com.bookmyshow.model.Movie;
import com.bookmyshow.repository.MovieRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.cache.annotation.Caching;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

/**
 * Business logic for movie management.
 *
 * Mapper pattern:
 *   toResponse() converts a Movie entity to MovieResponse DTO.
 *   This is the "anti-corruption layer" — controllers never touch entities directly.
 *
 * @Transactional(readOnly = true) on get methods:
 *   - Hibernate skips dirty checking (no need to track changes)
 *   - Slightly better performance for read-only operations
 *
 * Caching (see RedisConfig):
 *   Reads are cached in Redis; every write evicts the single-movie entry and all
 *   cached lists, because a new/edited movie can change any search result.
 *   Cached lists use Collectors.toList() (an ArrayList) rather than Stream.toList(),
 *   whose immutable JDK-internal list type cannot be rebuilt by Jackson from the cache.
 */
@Service
@RequiredArgsConstructor
public class MovieService {

    private final MovieRepository movieRepository;

    @Cacheable(value = RedisConfig.MOVIE_LISTS_CACHE, key = "'all'")
    @Transactional(readOnly = true)
    public List<MovieResponse> getAllMovies() {
        return toResponses(movieRepository.findAll());
    }

    @Cacheable(value = RedisConfig.MOVIE_CACHE, key = "#id")
    @Transactional(readOnly = true)
    public MovieResponse getMovieById(Long id) {
        Movie movie = movieRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Movie", "id", id));
        return toResponse(movie);
    }

    @Cacheable(value = RedisConfig.MOVIE_LISTS_CACHE,
            key = "'search:' + #title + ':' + #city + ':' + #language")
    @Transactional(readOnly = true)
    public List<MovieResponse> searchMovies(String title, String city, String language) {
        // City + title search (most common use case from BookMyShow home screen)
        if (city != null && title != null) {
            return toResponses(movieRepository.findByTitleInCity(title, city));
        }
        if (city != null) {
            return toResponses(movieRepository.findMoviesByCity(city));
        }
        if (title != null) {
            return toResponses(movieRepository.findByTitleContainingIgnoreCase(title));
        }
        if (language != null) {
            return toResponses(movieRepository.findByLanguageIgnoreCase(language));
        }
        return toResponses(movieRepository.findAll());
    }

    @CacheEvict(value = RedisConfig.MOVIE_LISTS_CACHE, allEntries = true)
    @Transactional
    public MovieResponse createMovie(MovieRequest request) {
        Movie movie = Movie.builder()
                .title(request.getTitle())
                .description(request.getDescription())
                .durationMinutes(request.getDurationMinutes())
                .genre(request.getGenre())
                .language(request.getLanguage())
                .releaseDate(request.getReleaseDate())
                .rating(request.getRating())
                .posterUrl(request.getPosterUrl())
                .build();

        return toResponse(movieRepository.save(movie));
    }

    @Caching(evict = {
            @CacheEvict(value = RedisConfig.MOVIE_CACHE, key = "#id"),
            @CacheEvict(value = RedisConfig.MOVIE_LISTS_CACHE, allEntries = true)
    })
    @Transactional
    public MovieResponse updateMovie(Long id, MovieRequest request) {
        Movie movie = movieRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Movie", "id", id));

        movie.setTitle(request.getTitle());
        movie.setDescription(request.getDescription());
        movie.setDurationMinutes(request.getDurationMinutes());
        movie.setGenre(request.getGenre());
        movie.setLanguage(request.getLanguage());
        movie.setReleaseDate(request.getReleaseDate());
        movie.setRating(request.getRating());
        movie.setPosterUrl(request.getPosterUrl());

        return toResponse(movieRepository.save(movie));
    }

    @Caching(evict = {
            @CacheEvict(value = RedisConfig.MOVIE_CACHE, key = "#id"),
            @CacheEvict(value = RedisConfig.MOVIE_LISTS_CACHE, allEntries = true)
    })
    @Transactional
    public void deleteMovie(Long id) {
        if (!movieRepository.existsById(id)) {
            throw new ResourceNotFoundException("Movie", "id", id);
        }
        movieRepository.deleteById(id);
    }

    private List<MovieResponse> toResponses(List<Movie> movies) {
        return movies.stream()
                .map(this::toResponse)
                .collect(Collectors.toList());
    }

    // Converts minutes to "Xh Ym" format
    private String formatDuration(int minutes) {
        int hours = minutes / 60;
        int mins = minutes % 60;
        return hours > 0
                ? String.format("%dh %dm", hours, mins)
                : String.format("%dm", mins);
    }

    public MovieResponse toResponse(Movie movie) {
        return MovieResponse.builder()
                .id(movie.getId())
                .title(movie.getTitle())
                .description(movie.getDescription())
                .durationMinutes(movie.getDurationMinutes())
                .durationFormatted(formatDuration(movie.getDurationMinutes()))
                .genre(movie.getGenre())
                .language(movie.getLanguage())
                .releaseDate(movie.getReleaseDate())
                .rating(movie.getRating())
                .posterUrl(movie.getPosterUrl())
                .build();
    }
}
