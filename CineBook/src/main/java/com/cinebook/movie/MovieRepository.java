package com.cinebook.movie;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Repository;

import java.sql.Date;
import java.sql.Timestamp;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Repository
public class MovieRepository {

    private final JdbcTemplate jdbcTemplate;
    private final NamedParameterJdbcTemplate namedJdbc;

    public static final RowMapper<Movie> MOVIE_ROW_MAPPER = (rs, rowNum) -> {
        Movie m = new Movie();
        m.setId(rs.getLong("id"));
        m.setTitle(rs.getString("title"));
        m.setDescription(rs.getString("description"));
        m.setPosterUrl(rs.getString("poster_url"));
        m.setBackdropUrl(rs.getString("backdrop_url"));
        m.setGenreId(rs.getLong("genre_id"));
        m.setGenreName(rs.getString("genre_name"));
        m.setLanguage(rs.getString("language"));
        m.setDurationMinutes(rs.getInt("duration_minutes"));
        Date relDate = rs.getDate("release_date");
        if (relDate != null) {
            m.setReleaseDate(relDate.toLocalDate());
        }
        m.setAgeRating(rs.getString("age_rating"));
        m.setCastDetails(rs.getString("cast_details"));
        m.setDirector(rs.getString("director"));
        m.setTrailerUrl(rs.getString("trailer_url"));
        m.setReleaseStatus(rs.getString("release_status"));
        m.setPopularityScore(rs.getInt("popularity_score"));
        m.setActive(rs.getBoolean("is_active"));
        Timestamp cTs = rs.getTimestamp("created_at");
        Timestamp uTs = rs.getTimestamp("updated_at");
        if (cTs != null) {
            m.setCreatedAt(cTs.toInstant().atOffset(ZoneOffset.UTC));
        }
        if (uTs != null) {
            m.setUpdatedAt(uTs.toInstant().atOffset(ZoneOffset.UTC));
        }
        return m;
    };

    private static final String BASE_SELECT = """
            SELECT m.id, m.title, m.description, m.poster_url, m.backdrop_url,
                   m.genre_id, g.name AS genre_name, m.language, m.duration_minutes,
                   m.release_date, m.age_rating, m.cast_details, m.director,
                   m.trailer_url, m.release_status, m.popularity_score, m.is_active,
                   m.created_at, m.updated_at
            FROM movies m
            JOIN genres g ON g.id = m.genre_id
            """;

    public MovieRepository(JdbcTemplate jdbcTemplate, NamedParameterJdbcTemplate namedJdbc) {
        this.jdbcTemplate = jdbcTemplate;
        this.namedJdbc = namedJdbc;
    }

    public List<Movie> searchMovies(
            String search,
            Long genreId,
            String language,
            String releaseStatus,
            boolean includeInactive,
            String sortBy,
            int page,
            int size
    ) {
        StringBuilder where = new StringBuilder(" WHERE 1=1 ");
        MapSqlParameterSource params = buildFilterParams(search, genreId, language, releaseStatus, includeInactive, where);

        String orderClause = switch (sortBy != null ? sortBy.toLowerCase() : "popularity") {
            case "releasedate", "release_date" -> " ORDER BY m.release_date DESC, m.id ASC ";
            case "title" -> " ORDER BY m.title ASC ";
            default -> " ORDER BY m.popularity_score DESC, m.release_date DESC, m.id ASC ";
        };

        params.addValue("limit", size);
        params.addValue("offset", page * size);

        String sql = BASE_SELECT + where + orderClause + " LIMIT :limit OFFSET :offset";
        return namedJdbc.query(sql, params, MOVIE_ROW_MAPPER);
    }

    public long countMovies(
            String search,
            Long genreId,
            String language,
            String releaseStatus,
            boolean includeInactive
    ) {
        StringBuilder where = new StringBuilder(" WHERE 1=1 ");
        MapSqlParameterSource params = buildFilterParams(search, genreId, language, releaseStatus, includeInactive, where);
        String sql = "SELECT COUNT(*) FROM movies m " + where;
        Long count = namedJdbc.queryForObject(sql, params, Long.class);
        return count != null ? count : 0L;
    }

    private MapSqlParameterSource buildFilterParams(
            String search,
            Long genreId,
            String language,
            String releaseStatus,
            boolean includeInactive,
            StringBuilder where
    ) {
        MapSqlParameterSource params = new MapSqlParameterSource();
        if (!includeInactive) {
            where.append(" AND m.is_active = TRUE ");
        }
        if (search != null && !search.isBlank()) {
            where.append(" AND LOWER(m.title) LIKE :search ");
            params.addValue("search", "%" + search.trim().toLowerCase() + "%");
        }
        if (genreId != null) {
            where.append(" AND m.genre_id = :genreId ");
            params.addValue("genreId", genreId);
        }
        if (language != null && !language.isBlank()) {
            where.append(" AND LOWER(m.language) = LOWER(:language) ");
            params.addValue("language", language.trim());
        }
        if (releaseStatus != null && !releaseStatus.isBlank()) {
            where.append(" AND m.release_status = :releaseStatus ");
            params.addValue("releaseStatus", releaseStatus.trim().toUpperCase());
        }
        return params;
    }

    public Optional<Movie> findById(Long id) {
        String sql = BASE_SELECT + " WHERE m.id = ?";
        List<Movie> list = jdbcTemplate.query(sql, MOVIE_ROW_MAPPER, id);
        return list.stream().findFirst();
    }

    public Movie save(Movie movie) {
        String sql = """
                INSERT INTO movies (
                    title, description, poster_url, backdrop_url, genre_id,
                    language, duration_minutes, release_date, age_rating,
                    cast_details, director, trailer_url, release_status,
                    popularity_score, is_active, created_at, updated_at
                ) VALUES (
                    :title, :description, :posterUrl, :backdropUrl, :genreId,
                    :language, :durationMinutes, :releaseDate, :ageRating,
                    :castDetails, :director, :trailerUrl, :releaseStatus,
                    :popularityScore, :active, NOW(), NOW()
                )
                """;
        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("title", movie.getTitle())
                .addValue("description", movie.getDescription())
                .addValue("posterUrl", movie.getPosterUrl())
                .addValue("backdropUrl", movie.getBackdropUrl())
                .addValue("genreId", movie.getGenreId())
                .addValue("language", movie.getLanguage())
                .addValue("durationMinutes", movie.getDurationMinutes())
                .addValue("releaseDate", Date.valueOf(movie.getReleaseDate()))
                .addValue("ageRating", movie.getAgeRating())
                .addValue("castDetails", movie.getCastDetails())
                .addValue("director", movie.getDirector())
                .addValue("trailerUrl", movie.getTrailerUrl())
                .addValue("releaseStatus", movie.getReleaseStatus())
                .addValue("popularityScore", movie.getPopularityScore())
                .addValue("active", movie.isActive());

        KeyHolder kh = new GeneratedKeyHolder();
        namedJdbc.update(sql, params, kh, new String[]{"id"});
        Long id = kh.getKey() != null ? kh.getKey().longValue() : ((Number) Map.copyOf(kh.getKeys()).get("id")).longValue();
        return findById(id).orElseThrow();
    }

    public Movie update(Long id, Movie movie) {
        String sql = """
                UPDATE movies SET
                    title = :title,
                    description = :description,
                    poster_url = :posterUrl,
                    backdrop_url = :backdropUrl,
                    genre_id = :genreId,
                    language = :language,
                    duration_minutes = :durationMinutes,
                    release_date = :releaseDate,
                    age_rating = :ageRating,
                    cast_details = :castDetails,
                    director = :director,
                    trailer_url = :trailerUrl,
                    release_status = :releaseStatus,
                    popularity_score = :popularityScore,
                    is_active = :active,
                    updated_at = NOW()
                WHERE id = :id
                """;
        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("id", id)
                .addValue("title", movie.getTitle())
                .addValue("description", movie.getDescription())
                .addValue("posterUrl", movie.getPosterUrl())
                .addValue("backdropUrl", movie.getBackdropUrl())
                .addValue("genreId", movie.getGenreId())
                .addValue("language", movie.getLanguage())
                .addValue("durationMinutes", movie.getDurationMinutes())
                .addValue("releaseDate", Date.valueOf(movie.getReleaseDate()))
                .addValue("ageRating", movie.getAgeRating())
                .addValue("castDetails", movie.getCastDetails())
                .addValue("director", movie.getDirector())
                .addValue("trailerUrl", movie.getTrailerUrl())
                .addValue("releaseStatus", movie.getReleaseStatus())
                .addValue("popularityScore", movie.getPopularityScore())
                .addValue("active", movie.isActive());

        namedJdbc.update(sql, params);
        return findById(id).orElseThrow();
    }

    public void updateStatus(Long id, boolean active) {
        jdbcTemplate.update("UPDATE movies SET is_active = ?, updated_at = NOW() WHERE id = ?", active, id);
    }

    public boolean hasShowtimesOrBookings(Long movieId) {
        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM showtimes WHERE movie_id = ?",
                Integer.class,
                movieId
        );
        return count != null && count > 0;
    }

    public void deleteById(Long id) {
        jdbcTemplate.update("DELETE FROM movies WHERE id = ?", id);
    }
}
