/**
 * CineBook Theatres & Showtime Discovery Module (showtimes.html)
 */
import {
    apiGet,
    escapeHtml,
    formatDate,
    getLocalIsoDate,
    getSelectedCity
} from './api.js';
import { initNavigation } from './navigation.js';
import { renderShowtimePill } from './movies.js';

export async function initShowtimesPage() {
    initNavigation();

    const params = new URLSearchParams(window.location.search);
    let selectedDate = params.get('date') || getLocalIsoDate(0);

    const dateStripEl = document.getElementById('showtimes-date-strip');
    const formatFilter = document.getElementById('showtimes-format-filter');
    const sortFilter = document.getElementById('showtimes-sort-filter');
    const container = document.getElementById('showtimes-listing-container');
    const cityHeading = document.getElementById('showtimes-city-heading');

    function renderDateStrip() {
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
                renderDateStrip();
                loadShowtimes();
            });
        });
    }

    async function loadShowtimes() {
        const city = getSelectedCity();
        if (cityHeading) {
            cityHeading.textContent = `Theatres & Showtimes in ${city}`;
        }
        container.innerHTML = `<div class="card"><p class="text-secondary">Loading schedules for ${formatDate(selectedDate)}...</p></div>`;

        try {
            let list = await apiGet(`/api/showtimes?date=${encodeURIComponent(selectedDate)}&city=${encodeURIComponent(city)}&status=SCHEDULED`);
            if (formatFilter && formatFilter.value) {
                list = list.filter(s => s.format === formatFilter.value);
            }

            if (sortFilter && sortFilter.value === 'priceAsc') {
                list.sort((a, b) => Number(a.ticketPrice) - Number(b.ticketPrice));
            } else if (sortFilter && sortFilter.value === 'priceDesc') {
                list.sort((a, b) => Number(b.ticketPrice) - Number(a.ticketPrice));
            } else {
                list.sort((a, b) => new Date(a.startTime) - new Date(b.startTime));
            }

            if (list.length === 0) {
                container.innerHTML = `
                    <div class="empty-state">
                        <h3>No matching showtimes on ${formatDate(selectedDate)} in ${escapeHtml(city)}</h3>
                        <p>Try picking the next date tab or clearing the format filter.</p>
                    </div>
                `;
                return;
            }

            // Group by Theatre -> Movie
            const byTheatre = new Map();
            for (const st of list) {
                if (!byTheatre.has(st.theatreId)) {
                    byTheatre.set(st.theatreId, {
                        theatreName: st.theatreName,
                        theatreAddress: st.theatreAddress,
                        movies: new Map()
                    });
                }
                const tGroup = byTheatre.get(st.theatreId);
                if (!tGroup.movies.has(st.movieId)) {
                    tGroup.movies.set(st.movieId, {
                        movieId: st.movieId,
                        movieTitle: st.movieTitle,
                        movieLanguage: st.movieLanguage,
                        movieAgeRating: st.movieAgeRating,
                        shows: []
                    });
                }
                tGroup.movies.get(st.movieId).shows.push(st);
            }

            container.innerHTML = Array.from(byTheatre.values()).map(t => `
                <section class="card" style="margin-bottom:1.25rem;">
                    <div style="border-bottom:1px solid var(--cb-border); padding-bottom:0.85rem; margin-bottom:1rem;">
                        <h2 style="font-size:1.22rem;">${escapeHtml(t.theatreName)}</h2>
                        <p class="text-secondary" style="font-size:0.86rem;">${escapeHtml(t.theatreAddress)}</p>
                    </div>
                    ${Array.from(t.movies.values()).map(m => `
                        <div style="display:grid; grid-template-columns: 240px 1fr; gap:1rem; padding:0.75rem 0; border-bottom:1px dashed var(--cb-border); align-items:center;">
                            <div>
                                <a href="/movie-details.html?id=${encodeURIComponent(m.movieId)}" style="font-weight:700; font-size:1rem; color:var(--cb-text-primary);">
                                    ${escapeHtml(m.movieTitle)}
                                </a>
                                <div class="text-muted" style="font-size:0.8rem;">
                                    ${escapeHtml(m.movieLanguage)} • ${escapeHtml(m.movieAgeRating)}
                                </div>
                            </div>
                            <div class="showtime-pills-wrap">
                                ${m.shows.map(renderShowtimePill).join('')}
                            </div>
                        </div>
                    `).join('')}
                </section>
            `).join('');
        } catch (err) {
            container.innerHTML = `<div class="alert alert-error">${escapeHtml(err.message)}</div>`;
        }
    }

    if (formatFilter) formatFilter.addEventListener('change', loadShowtimes);
    if (sortFilter) sortFilter.addEventListener('change', loadShowtimes);
    window.addEventListener('cinebook:cityChanged', loadShowtimes);

    renderDateStrip();
    await loadShowtimes();
}
