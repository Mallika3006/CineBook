/**
 * CineBook Navigation, City Selector & Footer Info Modal Module
 */
import {
    getStoredUser,
    clearAuthSession,
    getSelectedCity,
    setSelectedCity,
    apiPost,
    escapeHtml,
    showToast
} from './api.js';

const INFO_CONTENT = {
    about: {
        title: 'About CineBook',
        body: 'CineBook is a full-stack cinema discovery and real-time seat reservation platform engineered with Java 21, Spring Boot 3.5, Spring Security, JDBC, and PostgreSQL. It provides concurrency-safe interactive seat booking across IMAX, Dolby Atmos, and Luxe auditoriums.'
    },
    contact: {
        title: 'Contact & Concierge Support',
        body: 'CineBook Customer Desk — MG Road & Brigade Gateway, Bengaluru, Karnataka 560001. Support Email: support@cinebook.local | Helpline: +91 (80) 4090-CINE (09:00 AM – 11:00 PM IST).'
    },
    help: {
        title: 'Help & Booking FAQ',
        body: '• Maximum Seats: Up to 10 seats per booking.\n• Seat Holds: Selected seats are verified atomically on the server.\n• Cancellations: Eligible confirmed bookings can be cancelled up to 60 minutes prior to showtime start from the My Bookings page.'
    },
    terms: {
        title: 'Terms of Service',
        body: 'All ticket prices and convenience fees are calculated exclusively on the CineBook backend server. Age ratings (U, UA 7+, UA 13+, UA 16+, A) are enforced at cinema entry. Duplicate booking requests are deduplicated using idempotency keys.'
    },
    privacy: {
        title: 'Privacy & Security Policy',
        body: 'CineBook protects customer accounts using BCrypt password hashing and signed JWT authentication tokens. Ticket QR codes encode only non-sensitive booking verification tokens and never expose passwords or payment credentials.'
    }
};

export function initNavigation() {
    renderHeader();
    renderFooter();
}

function renderHeader() {
    const headerEl = document.getElementById('site-header');
    if (!headerEl) return;

    const user = getStoredUser();
    const currentCity = getSelectedCity();
    const path = window.location.pathname;

    const cities = ['Bengaluru', 'Mumbai', 'Hyderabad'];
    const cityOptions = cities.map(c =>
        `<option value="${escapeHtml(c)}" ${c === currentCity ? 'selected' : ''}>${escapeHtml(c)}</option>`
    ).join('');

    let authLinksHtml = '';
    if (user) {
        const adminLink = user.role === 'ADMIN'
            ? `<a href="/admin/dashboard.html" class="nav-link ${path.startsWith('/admin') ? 'active' : ''}">Admin Console</a>`
            : '';
        authLinksHtml = `
            ${adminLink}
            <a href="/my-bookings.html" class="nav-link ${path.endsWith('my-bookings.html') ? 'active' : ''}">My Bookings</a>
            <a href="/profile.html" class="nav-link ${path.endsWith('profile.html') ? 'active' : ''}">${escapeHtml(user.fullName.split(' ')[0])}</a>
            <button type="button" id="nav-logout-btn" class="btn btn-ghost-light btn-sm">Logout</button>
        `;
    } else {
        authLinksHtml = `
            <a href="/login.html" class="btn btn-ghost-light btn-sm">Log In</a>
            <a href="/register.html" class="btn btn-primary btn-sm">Sign Up</a>
        `;
    }

    headerEl.innerHTML = `
        <div class="site-header">
            <div class="container navbar-inner">
                <a href="/index.html" class="brand-logo" aria-label="CineBook Home">
                    <span class="brand-logo-mark">CB</span>
                    <span>Cine<em>Book</em></span>
                </a>

                <form id="global-search-form" class="nav-search-wrap" role="search">
                    <div class="nav-search-form">
                        <input
                            type="search"
                            id="global-search-input"
                            class="nav-search-input"
                            placeholder="Search movies by title, genre, or language..."
                            aria-label="Search movies"
                        />
                    </div>
                </form>

                <div class="nav-actions">
                    <select id="global-city-select" class="city-select" aria-label="Select City">
                        ${cityOptions}
                    </select>
                    <nav class="nav-links" aria-label="Primary Navigation">
                        <a href="/movies.html" class="nav-link ${path.endsWith('movies.html') ? 'active' : ''}">Movies</a>
                        <a href="/showtimes.html" class="nav-link ${path.endsWith('showtimes.html') ? 'active' : ''}">Theatres & Showtimes</a>
                        ${authLinksHtml}
                    </nav>
                </div>
            </div>
        </div>
        <div class="sub-nav no-print">
            <div class="container sub-nav-inner">
                <ul class="sub-nav-list">
                    <li><a href="/movies.html?releaseStatus=NOW_SHOWING">Now Showing</a></li>
                    <li><a href="/movies.html?releaseStatus=COMING_SOON">Coming Soon</a></li>
                    <li><a href="/showtimes.html">IMAX &amp; Dolby Atmos</a></li>
                    <li><a href="/movies.html?language=Kannada">Kannada Cinema</a></li>
                    <li><a href="/movies.html?language=English">English</a></li>
                    <li><a href="/movies.html?language=Hindi">Hindi</a></li>
                </ul>
                <span class="text-muted" style="font-size:0.78rem;">Instant Seat Confirmation • Server-Verified Pricing</span>
            </div>
        </div>
    `;

    const citySelect = document.getElementById('global-city-select');
    if (citySelect) {
        citySelect.addEventListener('change', (e) => {
            setSelectedCity(e.target.value);
            showToast(`Switched city to ${e.target.value}`, 'info');
        });
    }

    const searchForm = document.getElementById('global-search-form');
    if (searchForm) {
        searchForm.addEventListener('submit', (e) => {
            e.preventDefault();
            const q = document.getElementById('global-search-input').value.trim();
            window.location.href = `/movies.html${q ? `?search=${encodeURIComponent(q)}` : ''}`;
        });
    }

    const logoutBtn = document.getElementById('nav-logout-btn');
    if (logoutBtn) {
        logoutBtn.addEventListener('click', async () => {
            try {
                await apiPost('/api/auth/logout', {});
            } catch {
                // ignore logout network failure
            }
            clearAuthSession();
            showToast('You have been logged out.', 'info');
            setTimeout(() => {
                window.location.href = '/index.html';
            }, 350);
        });
    }
}

function renderFooter() {
    const footerEl = document.getElementById('site-footer');
    if (!footerEl) return;

    footerEl.innerHTML = `
        <footer class="site-footer no-print">
            <div class="container">
                <div class="footer-grid">
                    <div>
                        <a href="/index.html" class="brand-logo" style="margin-bottom:0.75rem;">
                            <span class="brand-logo-mark">CB</span>
                            <span>Cine<em>Book</em></span>
                        </a>
                        <p style="font-size:0.9rem; max-width:360px; line-height:1.6;">
                            Experience cinema the way it was meant to be seen. Discover movies, compare showtimes, and reserve your exact seats in real time.
                        </p>
                    </div>
                    <div>
                        <h4 class="footer-title">Explore</h4>
                        <ul class="footer-links">
                            <li><a href="/movies.html">Browse All Movies</a></li>
                            <li><a href="/movies.html?releaseStatus=NOW_SHOWING">Now Showing</a></li>
                            <li><a href="/movies.html?releaseStatus=COMING_SOON">Coming Soon</a></li>
                            <li><a href="/showtimes.html">Theatres &amp; Showtimes</a></li>
                        </ul>
                    </div>
                    <div>
                        <h4 class="footer-title">Account</h4>
                        <ul class="footer-links">
                            <li><a href="/my-bookings.html">My Bookings &amp; Tickets</a></li>
                            <li><a href="/profile.html">Account Settings</a></li>
                            <li><a href="/login.html">Customer Login</a></li>
                            <li><a href="/register.html">Create Account</a></li>
                        </ul>
                    </div>
                    <div>
                        <h4 class="footer-title">Company &amp; Legal</h4>
                        <ul class="footer-links">
                            <li><button type="button" data-info-modal="about">About CineBook</button></li>
                            <li><button type="button" data-info-modal="contact">Contact Us</button></li>
                            <li><button type="button" data-info-modal="help">Help &amp; FAQ</button></li>
                            <li><button type="button" data-info-modal="terms">Terms of Service</button></li>
                            <li><button type="button" data-info-modal="privacy">Privacy Policy</button></li>
                        </ul>
                    </div>
                </div>
                <div class="footer-bottom">
                    <span>&copy; ${new Date().getFullYear()} CineBook Entertainment Pvt. Ltd. All rights reserved.</span>
                    <span>Powered by Java 21, Spring Boot 3.5 &amp; PostgreSQL</span>
                </div>
            </div>
        </footer>
    `;

    footerEl.querySelectorAll('[data-info-modal]').forEach(btn => {
        btn.addEventListener('click', () => {
            const key = btn.getAttribute('data-info-modal');
            openInfoModal(INFO_CONTENT[key] || INFO_CONTENT.about);
        });
    });
}

function openInfoModal({ title, body }) {
    const existing = document.getElementById('cb-info-modal');
    if (existing) existing.remove();

    const backdrop = document.createElement('div');
    backdrop.id = 'cb-info-modal';
    backdrop.className = 'modal-backdrop';
    backdrop.innerHTML = `
        <div class="modal-card" role="dialog" aria-modal="true" aria-labelledby="cb-modal-title">
            <div class="modal-header">
                <h3 id="cb-modal-title">${escapeHtml(title)}</h3>
                <button type="button" class="btn btn-secondary btn-sm" id="cb-modal-close">Close</button>
            </div>
            <p style="white-space:pre-line; color:var(--cb-text-secondary); line-height:1.65;">${escapeHtml(body)}</p>
        </div>
    `;
    document.body.appendChild(backdrop);

    backdrop.querySelector('#cb-modal-close').addEventListener('click', () => backdrop.remove());
    backdrop.addEventListener('click', (e) => {
        if (e.target === backdrop) backdrop.remove();
    });
}

export function requireAuth(adminOnly = false) {
    const user = getStoredUser();
    if (!user) {
        const redirect = encodeURIComponent(window.location.pathname + window.location.search);
        window.location.href = `/login.html?redirect=${redirect}`;
        return null;
    }
    if (adminOnly && user.role !== 'ADMIN') {
        window.location.href = '/access-denied.html';
        return null;
    }
    return user;
}
