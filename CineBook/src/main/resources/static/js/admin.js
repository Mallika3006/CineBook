/**
 * CineBook Admin Console Module
 * Powers Dashboard Overview, Movie CRUD, Theatre CRUD, Screen & Seat Layout Management,
 * Showtime Scheduling (with overlap validation), Booking Management, and SQL Analytics Reports + CSV Export.
 */
import {
    apiGet,
    apiPost,
    apiPut,
    apiPatch,
    apiDelete,
    escapeHtml,
    formatCurrency,
    formatDate,
    formatDateTime,
    getLocalIsoDate,
    getAuthToken,
    showToast
} from './api.js';
import { initNavigation, requireAuth } from './navigation.js';

export function renderAdminSidebar(activePage) {
    const sidebar = document.getElementById('admin-sidebar');
    if (!sidebar) return;

    const links = [
        { key: 'dashboard', href: '/admin/dashboard.html', label: 'Overview & KPIs' },
        { key: 'movies', href: '/admin/movies.html', label: 'Movies Catalog' },
        { key: 'movie-form', href: '/admin/movie-form.html', label: '+ Add / Edit Movie' },
        { key: 'theatres', href: '/admin/theatres.html', label: 'Theatres' },
        { key: 'screens', href: '/admin/screens.html', label: 'Screens & Seat Maps' },
        { key: 'showtimes', href: '/admin/showtimes.html', label: 'Showtimes Schedule' },
        { key: 'bookings', href: '/admin/bookings.html', label: 'Customer Bookings' },
        { key: 'reports', href: '/admin/reports.html', label: 'Reports & CSV Export' }
    ];

    sidebar.innerHTML = `
        <div class="admin-sidebar-title">Admin Control Center</div>
        <ul class="admin-nav-list">
            ${links.map(l => `
                <li class="admin-nav-item">
                    <a href="${l.href}" class="${l.key === activePage ? 'active' : ''}">${escapeHtml(l.label)}</a>
                </li>
            `).join('')}
        </ul>
    `;
}

/**
 * 5.1 Admin Overview Dashboard
 */
export async function initAdminDashboardPage() {
    initNavigation();
    if (!requireAuth(true)) return;
    renderAdminSidebar('dashboard');

    const kpiContainer = document.getElementById('admin-kpi-grid');
    const recentBookingsBody = document.getElementById('admin-recent-bookings-body');
    const topMoviesBody = document.getElementById('admin-top-movies-body');

    try {
        const [summary, recentBookings, moviePerf] = await Promise.all([
            apiGet('/api/admin/reports/summary'),
            apiGet('/api/admin/bookings'),
            apiGet('/api/admin/reports/movie-performance')
        ]);

        kpiContainer.innerHTML = `
            <div class="kpi-card">
                <div class="kpi-label">Total Movies</div>
                <div class="kpi-value">${summary.totalMovies}</div>
                <div class="kpi-sub">${summary.activeMovies} active in catalog</div>
            </div>
            <div class="kpi-card">
                <div class="kpi-label">Active Showtimes</div>
                <div class="kpi-value">${summary.activeShowtimes}</div>
                <div class="kpi-sub">Scheduled future screenings</div>
            </div>
            <div class="kpi-card">
                <div class="kpi-label">Total Bookings</div>
                <div class="kpi-value">${summary.totalBookings}</div>
                <div class="kpi-sub">${summary.confirmedBookings} confirmed / ${summary.cancelledBookings} cancelled</div>
            </div>
            <div class="kpi-card">
                <div class="kpi-label">Confirmed Seats Sold</div>
                <div class="kpi-value">${summary.confirmedSeatsSold}</div>
                <div class="kpi-sub">Active reserved seats</div>
            </div>
            <div class="kpi-card">
                <div class="kpi-label">Confirmed Booking Value</div>
                <div class="kpi-value text-accent">${formatCurrency(summary.confirmedBookingValue)}</div>
                <div class="kpi-sub">Gross confirmed ticket + fee value</div>
            </div>
            <div class="kpi-card">
                <div class="kpi-label">Scheduled Occupancy</div>
                <div class="kpi-value">${summary.overallOccupancyPercent}%</div>
                <div class="kpi-sub">Sold vs total scheduled capacity</div>
            </div>
        `;

        const topRecent = (recentBookings || []).slice(0, 8);
        recentBookingsBody.innerHTML = topRecent.length
            ? topRecent.map(b => `
                <tr>
                    <td class="font-mono">${escapeHtml(b.bookingReference)}</td>
                    <td>${escapeHtml(b.customerName)}<br/><span class="text-muted" style="font-size:0.78rem;">${escapeHtml(b.customerEmail)}</span></td>
                    <td>${escapeHtml(b.movieTitle)}</td>
                    <td>${escapeHtml(b.theatreName)} (${escapeHtml(b.screenName)})</td>
                    <td>${escapeHtml((b.seats || []).map(s => s.seatLabel).join(', '))}</td>
                    <td>${formatCurrency(b.totalAmount)}</td>
                    <td><span class="badge ${b.status === 'CONFIRMED' ? 'badge-success' : 'badge-danger'}">${escapeHtml(b.status)}</span></td>
                </tr>
            `).join('')
            : `<tr><td colspan="7" class="text-secondary" style="text-align:center;">No bookings recorded yet.</td></tr>`;

        topMoviesBody.innerHTML = (moviePerf || []).slice(0, 6).map(m => `
            <tr>
                <td><strong>${escapeHtml(m.movieTitle)}</strong></td>
                <td>${escapeHtml(m.genreName)} • ${escapeHtml(m.language)}</td>
                <td>${m.totalShowtimes}</td>
                <td>${m.ticketsSold}</td>
                <td><strong>${formatCurrency(m.confirmedRevenue)}</strong></td>
            </tr>
        `).join('');
    } catch (err) {
        kpiContainer.innerHTML = `<div class="alert alert-error">${escapeHtml(err.message)}</div>`;
    }
}

/**
 * 5.2 Admin Movies Management
 */
export async function initAdminMoviesPage() {
    initNavigation();
    if (!requireAuth(true)) return;
    renderAdminSidebar('movies');

    const tbody = document.getElementById('admin-movies-tbody');
    const searchInput = document.getElementById('admin-movie-search');

    async function loadMovies() {
        const q = searchInput && searchInput.value.trim() ? `&search=${encodeURIComponent(searchInput.value.trim())}` : '';
        try {
            const page = await apiGet(`/api/movies?includeInactive=true&size=50${q}`);
            const movies = page.content || [];
            tbody.innerHTML = movies.map(m => `
                <tr>
                    <td>${m.id}</td>
                    <td>
                        <div style="display:flex; align-items:center; gap:0.75rem;">
                            <img src="${escapeHtml(m.posterUrl)}" alt="" style="width:36px; height:52px; object-fit:cover; border-radius:4px;" onerror="this.onerror=null;this.src='/assets/posters/fallback-poster.svg';"/>
                            <div>
                                <strong>${escapeHtml(m.title)}</strong>
                                <div class="text-muted" style="font-size:0.78rem;">${escapeHtml(m.releaseStatus)} • ${escapeHtml(m.ageRating)}</div>
                            </div>
                        </div>
                    </td>
                    <td>${escapeHtml(m.genreName)}</td>
                    <td>${escapeHtml(m.language)}</td>
                    <td>${m.durationMinutes} mins</td>
                    <td>${formatDate(m.releaseDate)}</td>
                    <td>
                        <span class="badge ${m.active ? 'badge-success' : 'badge-danger'}">
                            ${m.active ? 'Active' : 'Inactive'}
                        </span>
                    </td>
                    <td>
                        <div style="display:flex; gap:0.4rem; flex-wrap:wrap;">
                            <a href="/admin/movie-form.html?id=${m.id}" class="btn btn-secondary btn-sm">Edit</a>
                            <button type="button" class="btn btn-secondary btn-sm" data-toggle-id="${m.id}" data-active="${m.active}">
                                ${m.active ? 'Deactivate' : 'Activate'}
                            </button>
                            <button type="button" class="btn btn-danger btn-sm" data-delete-id="${m.id}">Delete</button>
                        </div>
                    </td>
                </tr>
            `).join('');

            tbody.querySelectorAll('[data-toggle-id]').forEach(btn => {
                btn.addEventListener('click', async () => {
                    const id = btn.getAttribute('data-toggle-id');
                    const current = btn.getAttribute('data-active') === 'true';
                    try {
                        await apiPatch(`/api/admin/movies/${id}/status`, { active: !current });
                        showToast(`Movie status updated.`, 'success');
                        loadMovies();
                    } catch (err) {
                        showToast(err.message, 'error');
                    }
                });
            });

            tbody.querySelectorAll('[data-delete-id]').forEach(btn => {
                btn.addEventListener('click', async () => {
                    const id = btn.getAttribute('data-delete-id');
                    try {
                        await apiDelete(`/api/admin/movies/${id}`);
                        showToast('Movie deleted.', 'success');
                        loadMovies();
                    } catch (err) {
                        showToast(err.message, 'error');
                    }
                });
            });
        } catch (err) {
            tbody.innerHTML = `<tr><td colspan="8" class="text-danger">${escapeHtml(err.message)}</td></tr>`;
        }
    }

    if (searchInput) {
        searchInput.addEventListener('input', () => loadMovies());
    }
    await loadMovies();
}

/**
 * 5.2b Admin Movie Add/Edit Form
 */
export async function initAdminMovieFormPage() {
    initNavigation();
    if (!requireAuth(true)) return;
    renderAdminSidebar('movie-form');

    const params = new URLSearchParams(window.location.search);
    const editId = params.get('id');
    const form = document.getElementById('admin-movie-form');
    const heading = document.getElementById('movie-form-heading');
    const genreSelect = document.getElementById('genreId');
    const errorBox = document.getElementById('movie-form-error');

    const genres = await apiGet('/api/genres');
    genreSelect.innerHTML = genres.map(g => `<option value="${g.id}">${escapeHtml(g.name)}</option>`).join('');

    if (editId) {
        heading.textContent = `Edit Movie #${editId}`;
        try {
            const m = await apiGet(`/api/movies/${encodeURIComponent(editId)}`);
            document.getElementById('title').value = m.title || '';
            document.getElementById('description').value = m.description || '';
            document.getElementById('posterUrl').value = m.posterUrl || '';
            document.getElementById('backdropUrl').value = m.backdropUrl || '';
            genreSelect.value = String(m.genreId);
            document.getElementById('language').value = m.language || 'English';
            document.getElementById('durationMinutes').value = m.durationMinutes || 120;
            document.getElementById('releaseDate').value = m.releaseDate || getLocalIsoDate(0);
            document.getElementById('ageRating').value = m.ageRating || 'UA 13+';
            document.getElementById('castDetails').value = m.castDetails || '';
            document.getElementById('director').value = m.director || '';
            document.getElementById('trailerUrl').value = m.trailerUrl || '';
            document.getElementById('releaseStatus').value = m.releaseStatus || 'NOW_SHOWING';
            document.getElementById('active').checked = m.active !== false;
        } catch (err) {
            errorBox.textContent = err.message;
            errorBox.classList.remove('hidden');
        }
    } else {
        document.getElementById('releaseDate').value = getLocalIsoDate(0);
    }

    form.addEventListener('submit', async (e) => {
        e.preventDefault();
        errorBox.classList.add('hidden');

        const payload = {
            title: document.getElementById('title').value.trim(),
            description: document.getElementById('description').value.trim(),
            posterUrl: document.getElementById('posterUrl').value.trim(),
            backdropUrl: document.getElementById('backdropUrl').value.trim(),
            genreId: Number(genreSelect.value),
            language: document.getElementById('language').value.trim(),
            durationMinutes: Number(document.getElementById('durationMinutes').value),
            releaseDate: document.getElementById('releaseDate').value,
            ageRating: document.getElementById('ageRating').value.trim(),
            castDetails: document.getElementById('castDetails').value.trim(),
            director: document.getElementById('director').value.trim(),
            trailerUrl: document.getElementById('trailerUrl').value.trim(),
            releaseStatus: document.getElementById('releaseStatus').value,
            active: document.getElementById('active').checked
        };

        try {
            if (editId) {
                await apiPut(`/api/admin/movies/${editId}`, payload);
                showToast('Movie updated successfully!', 'success');
            } else {
                await apiPost('/api/admin/movies', payload);
                showToast('New movie created!', 'success');
            }
            window.location.href = '/admin/movies.html';
        } catch (err) {
            errorBox.textContent = err.message;
            errorBox.classList.remove('hidden');
        }
    });
}

/**
 * 5.3 Admin Theatre Management
 */
export async function initAdminTheatresPage() {
    initNavigation();
    if (!requireAuth(true)) return;
    renderAdminSidebar('theatres');

    const tbody = document.getElementById('admin-theatres-tbody');
    const form = document.getElementById('admin-theatre-form');
    const errorBox = document.getElementById('theatre-form-error');
    let editingTheatreId = null;

    async function loadTheatres() {
        const list = await apiGet('/api/theatres?includeInactive=true');
        tbody.innerHTML = list.map(t => `
            <tr>
                <td>${t.id}</td>
                <td><strong>${escapeHtml(t.name)}</strong></td>
                <td>${escapeHtml(t.city)}</td>
                <td>${escapeHtml(t.address)}</td>
                <td><span class="badge ${t.active ? 'badge-success' : 'badge-danger'}">${t.active ? 'Active' : 'Inactive'}</span></td>
                <td>
                    <button type="button" class="btn btn-secondary btn-sm" data-edit='${escapeHtml(JSON.stringify(t))}'>Edit</button>
                </td>
            </tr>
        `).join('');

        tbody.querySelectorAll('[data-edit]').forEach(btn => {
            btn.addEventListener('click', () => {
                const t = JSON.parse(btn.getAttribute('data-edit'));
                editingTheatreId = t.id;
                document.getElementById('theatre-name').value = t.name;
                document.getElementById('theatre-city').value = t.city;
                document.getElementById('theatre-address').value = t.address;
                document.getElementById('theatre-active').checked = t.active;
                document.getElementById('theatre-submit-btn').textContent = `Update Theatre #${t.id}`;
            });
        });
    }

    form.addEventListener('submit', async (e) => {
        e.preventDefault();
        errorBox.classList.add('hidden');
        const payload = {
            name: document.getElementById('theatre-name').value.trim(),
            city: document.getElementById('theatre-city').value.trim(),
            address: document.getElementById('theatre-address').value.trim(),
            active: document.getElementById('theatre-active').checked
        };
        try {
            if (editingTheatreId) {
                await apiPut(`/api/admin/theatres/${editingTheatreId}`, payload);
                showToast('Theatre updated.', 'success');
            } else {
                await apiPost('/api/admin/theatres', payload);
                showToast('Theatre created.', 'success');
            }
            editingTheatreId = null;
            form.reset();
            document.getElementById('theatre-active').checked = true;
            document.getElementById('theatre-submit-btn').textContent = 'Create Theatre';
            await loadTheatres();
        } catch (err) {
            errorBox.textContent = err.message;
            errorBox.classList.remove('hidden');
        }
    });

    await loadTheatres();
}

/**
 * 5.4 Admin Screen & Seat Layout Management
 */
export async function initAdminScreensPage() {
    initNavigation();
    if (!requireAuth(true)) return;
    renderAdminSidebar('screens');

    const theatreSelect = document.getElementById('screen-theatre-select');
    const tbody = document.getElementById('admin-screens-tbody');
    const form = document.getElementById('admin-screen-form');
    const errorBox = document.getElementById('screen-form-error');
    const previewContainer = document.getElementById('admin-seat-preview');

    const theatres = await apiGet('/api/theatres?includeInactive=true');
    theatreSelect.innerHTML = theatres.map(t => `<option value="${t.id}">${escapeHtml(t.name)} (${escapeHtml(t.city)})</option>`).join('');

    async function loadScreens() {
        const screens = await apiGet('/api/admin/screens');
        tbody.innerHTML = screens.map(s => `
            <tr>
                <td>${s.id}</td>
                <td>${escapeHtml(s.theatreName)} (${escapeHtml(s.city)})</td>
                <td><strong>${escapeHtml(s.name)}</strong></td>
                <td>${s.capacity} seats</td>
                <td><span class="badge ${s.active ? 'badge-success' : 'badge-danger'}">${s.active ? 'Active' : 'Inactive'}</span></td>
                <td>
                    <button type="button" class="btn btn-secondary btn-sm" data-inspect-screen="${s.id}" data-screen-name="${escapeHtml(s.name)}">
                        Inspect Seat Layout
                    </button>
                </td>
            </tr>
        `).join('');

        tbody.querySelectorAll('[data-inspect-screen]').forEach(btn => {
            btn.addEventListener('click', async () => {
                const sid = btn.getAttribute('data-inspect-screen');
                const sname = btn.getAttribute('data-screen-name');
                const seats = await apiGet(`/api/admin/screens/${sid}/seats`);
                previewContainer.classList.remove('hidden');
                previewContainer.innerHTML = `
                    <h3 style="margin-bottom:0.75rem;">Physical Seat Map — ${escapeHtml(sname)} (${seats.length} seats)</h3>
                    <div style="display:flex; flex-wrap:wrap; gap:0.35rem;">
                        ${seats.map(seat => `
                            <span class="badge ${seat.seatType === 'RECLINER' ? 'badge-warning' : seat.seatType === 'PREMIUM' ? 'badge-info' : 'badge-dark'}">
                                ${escapeHtml(seat.seatLabel)} (${escapeHtml(seat.seatType.charAt(0))})
                            </span>
                        `).join('')}
                    </div>
                `;
            });
        });
    }

    form.addEventListener('submit', async (e) => {
        e.preventDefault();
        errorBox.classList.add('hidden');
        try {
            await apiPost('/api/admin/screens', {
                theatreId: Number(theatreSelect.value),
                name: document.getElementById('screen-name').value.trim(),
                regularRows: Number(document.getElementById('regular-rows').value),
                premiumRows: Number(document.getElementById('premium-rows').value),
                reclinerRows: Number(document.getElementById('recliner-rows').value),
                seatsPerRow: Number(document.getElementById('seats-per-row').value),
                active: true
            });
            showToast('Screen and numbered seat layout generated!', 'success');
            form.reset();
            await loadScreens();
        } catch (err) {
            errorBox.textContent = err.message;
            errorBox.classList.remove('hidden');
        }
    });

    await loadScreens();
}

/**
 * 5.5 Admin Showtime Scheduling Page
 */
export async function initAdminShowtimesPage() {
    initNavigation();
    if (!requireAuth(true)) return;
    renderAdminSidebar('showtimes');

    const movieSelect = document.getElementById('st-movie-select');
    const screenSelect = document.getElementById('st-screen-select');
    const form = document.getElementById('admin-showtime-form');
    const errorBox = document.getElementById('showtime-form-error');
    const tbody = document.getElementById('admin-showtimes-tbody');
    const filterDate = document.getElementById('st-filter-date');

    filterDate.value = getLocalIsoDate(0);

    const [moviesPage, screens] = await Promise.all([
        apiGet('/api/movies?size=50'),
        apiGet('/api/admin/screens')
    ]);

    movieSelect.innerHTML = (moviesPage.content || []).map(m =>
        `<option value="${m.id}">${escapeHtml(m.title)} (${m.durationMinutes}m • ${escapeHtml(m.language)})</option>`
    ).join('');

    screenSelect.innerHTML = screens.map(s =>
        `<option value="${s.id}">${escapeHtml(s.theatreName)} — ${escapeHtml(s.name)} (${escapeHtml(s.city)})</option>`
    ).join('');

    // Default start time tomorrow at 18:30
    const tomorrow = getLocalIsoDate(1);
    document.getElementById('st-start-time').value = `${tomorrow}T18:30`;

    async function loadShowtimes() {
        const d = filterDate.value ? `?date=${encodeURIComponent(filterDate.value)}` : '';
        const list = await apiGet(`/api/showtimes${d}`);
        tbody.innerHTML = list.map(st => `
            <tr>
                <td>${st.id}</td>
                <td><strong>${escapeHtml(st.movieTitle)}</strong><br/><span class="text-muted" style="font-size:0.78rem;">${escapeHtml(st.format)}</span></td>
                <td>${escapeHtml(st.theatreName)}<br/><span class="text-muted" style="font-size:0.78rem;">${escapeHtml(st.screenName)} (${escapeHtml(st.theatreCity)})</span></td>
                <td>${escapeHtml(formatDateTime(st.startTime))}</td>
                <td>${escapeHtml(formatDateTime(st.endTime))}</td>
                <td>${formatCurrency(st.ticketPrice)} / ${formatCurrency(st.premiumPrice)} / ${formatCurrency(st.reclinerPrice)}</td>
                <td>${st.bookedSeats} / ${st.screenCapacity}</td>
                <td><span class="badge ${st.status === 'SCHEDULED' ? 'badge-success' : 'badge-danger'}">${escapeHtml(st.status)}</span></td>
                <td>
                    ${st.status === 'SCHEDULED' ? `
                        <button type="button" class="btn btn-danger btn-sm" data-cancel-st="${st.id}">Cancel Show</button>
                    ` : '—'}
                </td>
            </tr>
        `).join('');

        tbody.querySelectorAll('[data-cancel-st]').forEach(btn => {
            btn.addEventListener('click', async () => {
                const id = btn.getAttribute('data-cancel-st');
                try {
                    await apiPost(`/api/admin/showtimes/${id}/cancel`, {});
                    showToast('Showtime cancelled and seats released.', 'success');
                    loadShowtimes();
                } catch (err) {
                    showToast(err.message, 'error');
                }
            });
        });
    }

    form.addEventListener('submit', async (e) => {
        e.preventDefault();
        errorBox.classList.add('hidden');

        const localDt = document.getElementById('st-start-time').value;
        const isoStartTime = `${localDt}:00+05:30`;

        try {
            await apiPost('/api/admin/showtimes', {
                movieId: Number(movieSelect.value),
                screenId: Number(screenSelect.value),
                startTime: isoStartTime,
                format: document.getElementById('st-format').value,
                ticketPrice: Number(document.getElementById('st-regular-price').value),
                premiumPrice: Number(document.getElementById('st-premium-price').value),
                reclinerPrice: Number(document.getElementById('st-recliner-price').value)
            });
            showToast('Showtime scheduled with overlap verification!', 'success');
            await loadShowtimes();
        } catch (err) {
            errorBox.textContent = err.message;
            errorBox.classList.remove('hidden');
        }
    });

    filterDate.addEventListener('change', loadShowtimes);
    await loadShowtimes();
}

/**
 * 5.6 Admin Booking Management Page
 */
export async function initAdminBookingsPage() {
    initNavigation();
    if (!requireAuth(true)) return;
    renderAdminSidebar('bookings');

    const refInput = document.getElementById('admin-booking-ref');
    const statusSelect = document.getElementById('admin-booking-status');
    const tbody = document.getElementById('admin-bookings-tbody');

    async function loadBookings() {
        const q = new URLSearchParams();
        if (refInput.value.trim()) q.set('reference', refInput.value.trim());
        if (statusSelect.value) q.set('status', statusSelect.value);

        const list = await apiGet(`/api/admin/bookings?${q.toString()}`);
        tbody.innerHTML = list.length ? list.map(b => `
            <tr>
                <td class="font-mono"><strong>${escapeHtml(b.bookingReference)}</strong></td>
                <td>${escapeHtml(b.customerName)}<br/><span class="text-muted" style="font-size:0.78rem;">${escapeHtml(b.customerEmail)}</span></td>
                <td>${escapeHtml(b.movieTitle)}</td>
                <td>${escapeHtml(b.theatreName)} (${escapeHtml(b.screenName)})</td>
                <td>${escapeHtml(formatDateTime(b.showStartTime))}</td>
                <td>${escapeHtml((b.seats || []).map(s => s.seatLabel).join(', '))}</td>
                <td>${formatCurrency(b.totalAmount)}</td>
                <td><span class="badge ${b.status === 'CONFIRMED' ? 'badge-success' : 'badge-danger'}">${escapeHtml(b.status)}</span></td>
                <td>
                    <div style="display:flex; gap:0.4rem;">
                        <a href="/booking-confirmation.html?bookingId=${b.id}" class="btn btn-secondary btn-sm">Ticket</a>
                        ${b.status === 'CONFIRMED' ? `<button type="button" class="btn btn-danger btn-sm" data-admin-cancel="${b.id}">Cancel</button>` : ''}
                    </div>
                </td>
            </tr>
        `).join('') : `<tr><td colspan="9" style="text-align:center;" class="text-secondary">No matching bookings found.</td></tr>`;

        tbody.querySelectorAll('[data-admin-cancel]').forEach(btn => {
            btn.addEventListener('click', async () => {
                const id = btn.getAttribute('data-admin-cancel');
                try {
                    await apiPost(`/api/bookings/${id}/cancel`, {});
                    showToast('Booking cancelled.', 'success');
                    loadBookings();
                } catch (err) {
                    showToast(err.message, 'error');
                }
            });
        });
    }

    refInput.addEventListener('input', loadBookings);
    statusSelect.addEventListener('change', loadBookings);
    await loadBookings();
}

/**
 * 5.7 Admin Reports & CSV Export Page
 */
export async function initAdminReportsPage() {
    initNavigation();
    if (!requireAuth(true)) return;
    renderAdminSidebar('reports');

    const revBody = document.getElementById('report-revenue-tbody');
    const occBody = document.getElementById('report-occupancy-tbody');
    const movieBody = document.getElementById('report-movie-tbody');
    const exportBtn = document.getElementById('export-csv-btn');

    try {
        const [revRows, occRows, movieRows] = await Promise.all([
            apiGet('/api/admin/reports/revenue'),
            apiGet('/api/admin/reports/occupancy'),
            apiGet('/api/admin/reports/movie-performance')
        ]);

        revBody.innerHTML = revRows.length ? revRows.map(r => `
            <tr>
                <td>${formatDate(r.bookingDate)}</td>
                <td>${r.totalBookings}</td>
                <td>${r.confirmedBookings}</td>
                <td>${r.cancelledBookings}</td>
                <td>${r.ticketsSold}</td>
                <td>${formatCurrency(r.grossSubtotal)}</td>
                <td>${formatCurrency(r.totalFees)}</td>
                <td><strong>${formatCurrency(r.confirmedRevenue)}</strong></td>
            </tr>
        `).join('') : `<tr><td colspan="8" style="text-align:center;" class="text-secondary">No bookings recorded in the selected date window.</td></tr>`;

        occBody.innerHTML = occRows.slice(0, 20).map(o => `
            <tr>
                <td><strong>${escapeHtml(o.movieTitle)}</strong></td>
                <td>${escapeHtml(o.theatreName)} (${escapeHtml(o.screenName)})</td>
                <td>${escapeHtml(formatDateTime(o.startTime))}</td>
                <td>${o.seatsSold} / ${o.screenCapacity}</td>
                <td>${o.availableSeats}</td>
                <td><strong>${o.occupancyPercent}%</strong></td>
            </tr>
        `).join('');

        movieBody.innerHTML = movieRows.map(m => `
            <tr>
                <td><strong>${escapeHtml(m.movieTitle)}</strong></td>
                <td>${escapeHtml(m.genreName)}</td>
                <td>${escapeHtml(m.language)}</td>
                <td>${m.totalShowtimes}</td>
                <td>${m.confirmedBookings}</td>
                <td>${m.cancelledBookings}</td>
                <td>${m.ticketsSold}</td>
                <td><strong>${formatCurrency(m.confirmedRevenue)}</strong></td>
            </tr>
        `).join('');
    } catch (err) {
        showToast(err.message, 'error');
    }

    if (exportBtn) {
        exportBtn.addEventListener('click', async () => {
            try {
                const token = getAuthToken();
                const res = await fetch('/api/admin/reports/export', {
                    headers: token ? { 'Authorization': `Bearer ${token}` } : {},
                    credentials: 'include'
                });
                const csvText = await res.text();
                const blob = new Blob([csvText], { type: 'text/csv;charset=utf-8;' });
                const url = URL.createObjectURL(blob);
                const a = document.createElement('a');
                a.href = url;
                a.download = `cinebook-movie-performance-${getLocalIsoDate(0)}.csv`;
                document.body.appendChild(a);
                a.click();
                a.remove();
                URL.revokeObjectURL(url);
                showToast('CSV report downloaded.', 'success');
            } catch (err) {
                showToast('Failed to export CSV: ' + err.message, 'error');
            }
        });
    }
}
