/**
 * CineBook Movies Module — Home Page, Movie Discovery Page & Movie Details Page
 */
import {
    apiGet,
    escapeHtml,
    formatCurrency,
    formatDate,
    formatTime,
    getLocalIsoDate,
    getSelectedCity
} from './api.js';
import { initNavigation } from './navigation.js';

export function renderMovieCard(movie) {
    const isNowShowing = movie.releaseStatus === 'NOW_SHOWING';
    const statusBadge = isNowShowing
        ? `<span class="badge badge-success">Now Showing</span>`
        : `<span class="badge badge-info">Coming Soon</span>`;
    const actionLabel = isNowShowing ? 'Book Tickets' : 'View Details';

    return `
        <article class="movie-card">
            <a href="/movie-details.html?id=${encodeURIComponent(movie.id)}" class="movie-poster-wrap">
                <img
                    src="${escapeHtml(movie.posterUrl)}"
                    alt="${escapeHtml(movie.title)} Poster"
                    loading="lazy"
                    onerror="this.onerror=null;this.src='/assets/posters/fallback-poster.svg';"
                />
                <div class="movie-card-badges">
                    ${statusBadge}
                    <span class="badge badge-dark">${escapeHtml(movie.ageRating || 'UA')}</span>
                </div>
            </a>
            <div class="movie-card-body">
                <h3 class="movie-card-title" title="${escapeHtml(movie.title)}">
                    <a href="/movie-details.html?id=${encodeURIComponent(movie.id)}">${escapeHtml(movie.title)}</a>
                </h3>
                <div class="movie-card-meta">
                    <span>${escapeHtml(movie.genreName)}</span>
                    <span>•</span>
                    <span>${escapeHtml(movie.language)}</span>
                    <span>•</span>
                    <span>${escapeHtml(movie.durationMinutes)} mins</span>
                </div>
                <div class="movie-card-footer">
                    <a href="/movie-details.html?id=${encodeURIComponent(movie.id)}" class="btn ${isNowShowing ? 'btn-primary' : 'btn-secondary'} btn-block btn-sm">
                        ${actionLabel}
                    </a>
                </div>
            </div>
        </article>
    `;
}

/**
 * 1. Home Page Initialization
 */
export async function initHomePage() {
    initNavigation();

    const trendingGrid = document.getElementById('trending-movies-grid');
    const recommendedGrid = document.getElementById('recommended-movies-grid');
    const comingSoonGrid = document.getElementById('coming-soon-movies-grid');
    const genreFilterContainer = document.getElementById('home-genre-pills');
    const heroContainer = document.getElementById('hero-banner-container');

    try {
        const [genres, nowShowingPage, comingSoonPage] = await Promise.all([
            apiGet('/api/genres'),
            apiGet('/api/movies?releaseStatus=NOW_SHOWING&sortBy=popularity&page=0&size=8'),
            apiGet('/api/movies?releaseStatus=COMING_SOON&sortBy=releaseDate&page=0&size=4')
        ]);

        const nowShowing = nowShowingPage.content || [];
        const comingSoon = comingSoonPage.content || [];

        if (nowShowing.length > 0 && heroContainer) {
            const featured = nowShowing[0];
            heroContainer.innerHTML = `
                <div class="container hero-inner">
                    <div>
                        <span class="hero-tag">Spotlight Feature • IMAX &amp; Dolby Atmos</span>
                        <h1 class="hero-title">${escapeHtml(featured.title)}</h1>
                        <p class="hero-desc">${escapeHtml(featured.description)}</p>
                        <div class="hero-meta">
                            <span class="badge badge-dark">${escapeHtml(featured.genreName)}</span>
                            <span class="badge badge-dark">${escapeHtml(featured.language)}</span>
                            <span class="badge badge-dark">${escapeHtml(featured.durationMinutes)} mins</span>
                            <span class="badge badge-dark">${escapeHtml(featured.ageRating)}</span>
                        </div>
                        <div style="display:flex; gap:0.85rem; flex-wrap:wrap;">
                            <a href="/movie-details.html?id=${encodeURIComponent(featured.id)}" class="btn btn-primary btn-lg">
                                Book Tickets Now
                            </a>
                            <a href="/movies.html" class="btn btn-ghost-light btn-lg">
                                Explore All Movies
                            </a>
                        </div>
                    </div>
                    <div class="hero-poster-card">
                        <img
                            src="${escapeHtml(featured.posterUrl)}"
                            alt="${escapeHtml(featured.title)}"
                            onerror="this.onerror=null;this.src='/assets/posters/fallback-poster.svg';"
                        />
                    </div>
                </div>
            `;
        }

        if (genreFilterContainer && genres) {
            genreFilterContainer.innerHTML = `
                <a href="/movies.html" class="btn btn-primary btn-sm">All Genres</a>
                ${genres.map(g => `<a href="/movies.html?genreId=${encodeURIComponent(g.id)}" class="btn btn-secondary btn-sm">${escapeHtml(g.name)}</a>`).join('')}
            `;
        }

        if (trendingGrid) {
            const trending = nowShowing.slice(0, 4);
            trendingGrid.innerHTML = trending.length
                ? trending.map(renderMovieCard).join('')
                : `<div class="empty-state"><h3>No trending movies found</h3></div>`;
        }

        if (recommendedGrid) {
            const recommended = nowShowing.slice(0, 8);
            recommendedGrid.innerHTML = recommended.length
                ? recommended.map(renderMovieCard).join('')
                : `<div class="empty-state"><h3>No recommended movies available</h3></div>`;
        }

        if (comingSoonGrid) {
            comingSoonGrid.innerHTML = comingSoon.length
                ? comingSoon.map(renderMovieCard).join('')
                : `<div class="empty-state"><h3>No upcoming releases announced yet</h3></div>`;
        }
    } catch (err) {
        if (trendingGrid) {
            trendingGrid.innerHTML = `<div class="alert alert-error">Failed to load movies: ${escapeHtml(err.message)}</div>`;
        }
    }
}

/**
 * 2. Movie Discovery Page Initialization
 */
export async function initMoviesDiscoveryPage() {
    initNavigation();

    const params = new URLSearchParams(window.location.search);
    const searchInput = document.getElementById('filter-search');
    const genreSelect = document.getElementById('filter-genre');
    const languageSelect = document.getElementById('filter-language');
    const statusSelect = document.getElementById('filter-status');
    const sortSelect = document.getElementById('filter-sort');
    const resetBtn = document.getElementById('filter-reset-btn');
    const grid = document.getElementById('discovery-movies-grid');
    const paginationWrap = document.getElementById('discovery-pagination');
    const countLabel = document.getElementById('discovery-count-label');

    let currentPage = 0;
    const pageSize = 8;

    if (searchInput && params.get('search')) searchInput.value = params.get('search');
    if (languageSelect && params.get('language')) languageSelect.value = params.get('language');
    if (statusSelect && params.get('releaseStatus')) statusSelect.value = params.get('releaseStatus');
    if (sortSelect && params.get('sortBy')) sortSelect.value = params.get('sortBy');

    try {
        const genres = await apiGet('/api/genres');
        if (genreSelect && genres) {
            genreSelect.innerHTML = `<option value="">All Genres</option>` +
                genres.map(g => `<option value="${g.id}">${escapeHtml(g.name)}</option>`).join('');
            if (params.get('genreId')) {
                genreSelect.value = params.get('genreId');
            }
        }
    } catch {
        // ignore genre fetch error
    }

    async function loadMovies(page = 0) {
        currentPage = page;
        grid.innerHTML = Array.from({ length: 4 }).map(() => `<div class="skeleton skeleton-card"></div>`).join('');

        const q = new URLSearchParams();
        if (searchInput.value.trim()) q.set('search', searchInput.value.trim());
        if (genreSelect.value) q.set('genreId', genreSelect.value);
        if (languageSelect.value) q.set('language', languageSelect.value);
        if (statusSelect.value) q.set('releaseStatus', statusSelect.value);
        q.set('sortBy', sortSelect.value || 'popularity');
        q.set('page', String(currentPage));
        q.set('size', String(pageSize));

        try {
            const res = await apiGet(`/api/movies?${q.toString()}`);
            const movies = res.content || [];
            if (countLabel) {
                countLabel.textContent = `Showing ${movies.length} of ${res.totalElements} movies`;
            }

            if (movies.length === 0) {
                grid.innerHTML = `
                    <div class="empty-state" style="grid-column: 1 / -1;">
                        <h3>No matching movies found</h3>
                        <p>Try clearing one of your search filters or switching the language selection.</p>
                    </div>
                `;
                paginationWrap.innerHTML = '';
                return;
            }

            grid.innerHTML = movies.map(renderMovieCard).join('');
            renderPagination(res.page, res.totalPages);
        } catch (err) {
            grid.innerHTML = `<div class="alert alert-error" style="grid-column: 1 / -1;">${escapeHtml(err.message)}</div>`;
        }
    }

    function renderPagination(page, totalPages) {
        if (!paginationWrap || totalPages <= 1) {
            if (paginationWrap) paginationWrap.innerHTML = '';
            return;
        }
        let buttons = '';
        buttons += `<button type="button" class="btn btn-secondary btn-sm" ${page === 0 ? 'disabled' : ''} data-page="${page - 1}">Previous</button>`;
        for (let i = 0; i < totalPages; i++) {
            buttons += `<button type="button" class="btn ${i === page ? 'btn-primary' : 'btn-secondary'} btn-sm" data-page="${i}">${i + 1}</button>`;
        }
        buttons += `<button type="button" class="btn btn-secondary btn-sm" ${page >= totalPages - 1 ? 'disabled' : ''} data-page="${page + 1}">Next</button>`;
        paginationWrap.innerHTML = buttons;

        paginationWrap.querySelectorAll('[data-page]').forEach(btn => {
            btn.addEventListener('click', () => {
                loadMovies(Number(btn.getAttribute('data-page')));
                window.scrollTo({ top: 0, behavior: 'smooth' });
            });
        });
    }

    [genreSelect, languageSelect, statusSelect, sortSelect].forEach(el => {
        if (el) el.addEventListener('change', () => loadMovies(0));
    });

    if (searchInput) {
        let timer = null;
        searchInput.addEventListener('input', () => {
            clearTimeout(timer);
            timer = setTimeout(() => loadMovies(0), 260);
        });
    }

    if (resetBtn) {
        resetBtn.addEventListener('click', () => {
            searchInput.value = '';
            genreSelect.value = '';
            languageSelect.value = '';
            statusSelect.value = '';
            sortSelect.value = 'popularity';
            loadMovies(0);
        });
    }

    await loadMovies(0);
}

/**
 * 3. Movie Details & Date/Theatre Showtime Selection Page
 */
export async function initMovieDetailsPage() {
    initNavigation();

    const params = new URLSearchParams(window.location.search);
    const movieId = params.get('id');
    let selectedDate = params.get('date') || getLocalIsoDate(0);

    const heroEl = document.getElementById('movie-details-hero');
    const dateStripEl = document.getElementById('movie-date-strip');
    const showtimesContainer = document.getElementById('movie-theatres-container');

    if (!movieId) {
        heroEl.innerHTML = `<div class="container"><div class="alert alert-error">No movie ID specified. <a href="/movies.html">Return to Movies</a></div></div>`;
        return;
    }

    try {
        const movie = await apiGet(`/api/movies/${encodeURIComponent(movieId)}`);
        document.title = `${movie.title} — Tickets & Showtimes | CineBook`;

        heroEl.innerHTML = `
            <div class="container movie-hero-grid">
                <div class="movie-hero-poster">
                    <img
                        src="${escapeHtml(movie.posterUrl)}"
                        alt="${escapeHtml(movie.title)}"
                        onerror="this.onerror=null;this.src='/assets/posters/fallback-poster.svg';"
                    />
                </div>
                <div>
                    <div style="display:flex; gap:0.5rem; margin-bottom:0.85rem; flex-wrap:wrap;">
                        <span class="badge ${movie.releaseStatus === 'NOW_SHOWING' ? 'badge-success' : 'badge-info'}">
                            ${movie.releaseStatus === 'NOW_SHOWING' ? 'Now Showing' : 'Coming Soon'}
                        </span>
                        <span class="badge badge-dark">${escapeHtml(movie.ageRating)}</span>
                        <span class="badge badge-dark">${escapeHtml(movie.language)}</span>
                        <span class="badge badge-dark">${escapeHtml(movie.genreName)}</span>
                    </div>
                    <h1 style="color:#FFFFFF; font-size:2.4rem; margin-bottom:0.75rem;">${escapeHtml(movie.title)}</h1>
                    <p style="color:#CBD5E1; font-size:1rem; max-width:740px; margin-bottom:1.35rem; line-height:1.65;">
                        ${escapeHtml(movie.description)}
                    </p>
                    <div style="display:grid; grid-template-columns: repeat(auto-fit, minmax(180px, 1fr)); gap:1rem; background:rgba(255,255,255,0.06); padding:1rem 1.25rem; border-radius:var(--radius-md); margin-bottom:1.25rem;">
                        <div>
                            <div style="font-size:0.75rem; color:#94A3B8; text-transform:uppercase;">Duration &amp; Release</div>
                            <div style="font-weight:600; font-size:0.92rem;">${escapeHtml(movie.durationMinutes)} mins • ${formatDate(movie.releaseDate)}</div>
                        </div>
                        <div>
                            <div style="font-size:0.75rem; color:#94A3B8; text-transform:uppercase;">Director</div>
                            <div style="font-weight:600; font-size:0.92rem;">${escapeHtml(movie.director || 'CineBook Studios')}</div>
                        </div>
                        <div>
                            <div style="font-size:0.75rem; color:#94A3B8; text-transform:uppercase;">Lead Cast</div>
                            <div style="font-weight:600; font-size:0.92rem;">${escapeHtml(movie.castDetails || 'Ensemble Cast')}</div>
                        </div>
                    </div>
                    ${movie.trailerUrl ? `<a href="${escapeHtml(movie.trailerUrl)}" target="_blank" rel="noopener noreferrer" class="btn btn-ghost-light btn-sm">Watch Official Trailer ↗</a>` : ''}
                </div>
            </div>
        `;

        renderDateStrip();
        await loadShowtimesForMovie();

        window.addEventListener('cinebook:cityChanged', () => {
            loadShowtimesForMovie();
        });
    } catch (err) {
        heroEl.innerHTML = `<div class="container"><div class="alert alert-error">${escapeHtml(err.message)}</div></div>`;
    }

    function renderDateStrip() {
        if (!dateStripEl) return;
        const days = [];
        for (let i = 0; i < 7; i++) {
            const iso = getLocalIsoDate(i);
            const d = new Date(`${iso}T12:00:00`);
            days.push({
                iso,
                dayName: i === 0 ? 'TODAY' : i === 1 ? 'TOM' : d.toLocaleDateString('en-US', { weekday: 'short' }),
                dayNum: d.getDate(),
                monthName: d.toLocaleDateString('en-US', { month: 'short' })
            });
        }

        dateStripEl.innerHTML = days.map(d => `
            <button type="button" class="date-pill ${d.iso === selectedDate ? 'active' : ''}" data-date="${d.iso}">
                <span class="day-name">${escapeHtml(d.dayName)}</span>
                <span class="day-num">${escapeHtml(d.dayNum)}</span>
                <span class="month-name">${escapeHtml(d.monthName)}</span>
            </button>
        `).join('');

        dateStripEl.querySelectorAll('[data-date]').forEach(btn => {
            btn.addEventListener('click', () => {
                selectedDate = btn.getAttribute('data-date');
                const url = new URL(window.location.href);
                url.searchParams.set('date', selectedDate);
                window.history.replaceState({}, '', url);
                renderDateStrip();
                loadShowtimesForMovie();
            });
        });
    }

    async function loadShowtimesForMovie() {
        if (!showtimesContainer) return;
        const city = getSelectedCity();
        showtimesContainer.innerHTML = `<div class="card"><p class="text-secondary">Loading showtimes in ${escapeHtml(city)}...</p></div>`;

        try {
            const showtimes = await apiGet(`/api/movies/${encodeURIComponent(movieId)}/showtimes?date=${encodeURIComponent(selectedDate)}&city=${encodeURIComponent(city)}`);

            if (!showtimes || showtimes.length === 0) {
                showtimesContainer.innerHTML = `
                    <div class="empty-state">
                        <h3>No showtimes scheduled for ${formatDate(selectedDate)} in ${escapeHtml(city)}</h3>
                        <p>Try selecting another date from the strip above or switching your city to Bengaluru.</p>
                    </div>
                `;
                return;
            }

            // Group showtimes by theatre
            const byTheatre = new Map();
            for (const st of showtimes) {
                if (!byTheatre.has(st.theatreId)) {
                    byTheatre.set(st.theatreId, {
                        theatreId: st.theatreId,
                        theatreName: st.theatreName,
                        theatreAddress: st.theatreAddress,
                        shows: []
                    });
                }
                byTheatre.get(st.theatreId).shows.push(st);
            }

            showtimesContainer.innerHTML = Array.from(byTheatre.values()).map(group => `
                <div class="theatre-showtime-card">
                    <div class="theatre-info">
                        <h3>${escapeHtml(group.theatreName)}</h3>
                        <p class="theatre-address">${escapeHtml(group.theatreAddress)}</p>
                        <div style="margin-top:0.5rem; display:flex; gap:0.4rem;">
                            <span class="badge badge-info">M-Ticket</span>
                            <span class="badge badge-success">F&amp;B Available</span>
                        </div>
                    </div>
                    <div class="showtime-pills-wrap">
                        ${group.shows.map(renderShowtimePill).join('')}
                    </div>
                </div>
            `).join('');
        } catch (err) {
            showtimesContainer.innerHTML = `<div class="alert alert-error">${escapeHtml(err.message)}</div>`;
        }
    }
}

export function renderShowtimePill(st) {
    const isUnavailable = st.availabilityBadge === 'SOLD_OUT' || st.availabilityBadge === 'UNAVAILABLE';
    const extraClass = st.availabilityBadge === 'FILLING_FAST'
        ? 'filling-fast'
        : isUnavailable ? 'sold-out' : '';

    const statusSub = st.availabilityBadge === 'SOLD_OUT'
        ? 'Sold Out'
        : st.availabilityBadge === 'FILLING_FAST'
            ? `Filling Fast (${st.availableSeats} left)`
            : `${st.availableSeats} seats`;

    if (isUnavailable) {
        return `
            <button type="button" class="showtime-btn sold-out" disabled title="Unavailable">
                <span class="showtime-time">${escapeHtml(formatTime(st.startTime))}</span>
                <span class="showtime-meta">${escapeHtml(st.format)} • ${escapeHtml(statusSub)}</span>
            </button>
        `;
    }

    return `
        <a href="/seats.html?showtimeId=${encodeURIComponent(st.id)}" class="showtime-btn ${extraClass}" title="Screen: ${escapeHtml(st.screenName)} | Starting at ${formatCurrency(st.ticketPrice)}">
            <span class="showtime-time">${escapeHtml(formatTime(st.startTime))}</span>
            <span class="showtime-meta">${escapeHtml(st.format)} • ${formatCurrency(st.ticketPrice)}</span>
            <span class="showtime-meta" style="font-size:0.68rem;">${escapeHtml(st.screenName)} (${escapeHtml(statusSub)})</span>
        </a>
    `;
}
