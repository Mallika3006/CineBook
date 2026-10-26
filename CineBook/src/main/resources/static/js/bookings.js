/**
 * CineBook Booking Confirmation, QR Ticket & My Bookings Module
 */
import {
    apiGet,
    apiPost,
    escapeHtml,
    formatCurrency,
    formatDateTime,
    generateQrSvg,
    showToast
} from './api.js';
import { initNavigation, requireAuth } from './navigation.js';

/**
 * 1. Booking Confirmation & Printable Ticket Page
 */
export async function initBookingConfirmationPage() {
    initNavigation();
    const user = requireAuth(false);
    if (!user) return;

    const params = new URLSearchParams(window.location.search);
    const bookingId = params.get('bookingId');
    const container = document.getElementById('ticket-confirmation-container');

    if (!bookingId) {
        container.innerHTML = `<div class="alert alert-error">Missing bookingId parameter. <a href="/my-bookings.html">Go to My Bookings</a></div>`;
        return;
    }

    try {
        const ticket = await apiGet(`/api/bookings/${encodeURIComponent(bookingId)}/ticket`);
        const isConfirmed = ticket.status === 'CONFIRMED';
        const qrSvg = generateQrSvg(ticket.qrPayload, 168);

        container.innerHTML = `
            <div class="ticket-card">
                <div class="ticket-header">
                    <div>
                        <span class="badge ${isConfirmed ? 'badge-success' : 'badge-danger'}" style="margin-bottom:0.4rem;">
                            ${isConfirmed ? 'BOOKING CONFIRMED' : 'BOOKING CANCELLED'}
                        </span>
                        <h1 style="color:#FFFFFF; font-size:1.55rem;">Booking Ref: <span class="font-mono">${escapeHtml(ticket.bookingReference)}</span></h1>
                    </div>
                    <div style="text-align:right;">
                        <div style="font-size:0.78rem; color:#94A3B8;">Issued To</div>
                        <div style="font-weight:700;">${escapeHtml(ticket.customerName)}</div>
                    </div>
                </div>

                <div class="ticket-body">
                    <div>
                        <div style="display:flex; gap:1.25rem; align-items:flex-start; margin-bottom:1.25rem;">
                            <img
                                src="${escapeHtml(ticket.moviePosterUrl)}"
                                alt="${escapeHtml(ticket.movieTitle)}"
                                style="width:92px; border-radius:var(--radius-sm);"
                                onerror="this.onerror=null;this.src='/assets/posters/fallback-poster.svg';"
                            />
                            <div>
                                <h2 style="font-size:1.4rem; margin-bottom:0.25rem;">${escapeHtml(ticket.movieTitle)}</h2>
                                <p class="text-secondary" style="font-size:0.88rem; margin-bottom:0.4rem;">
                                    ${escapeHtml(ticket.movieLanguage)} • ${escapeHtml(ticket.format)} • ${escapeHtml(ticket.movieAgeRating)}
                                </p>
                                <p style="font-weight:700;">${escapeHtml(ticket.theatreName)} (${escapeHtml(ticket.screenName)})</p>
                                <p class="text-secondary" style="font-size:0.84rem;">${escapeHtml(ticket.theatreAddress)}</p>
                                <p style="font-weight:700; color:var(--cb-accent); margin-top:0.4rem;">
                                    Showtime: ${escapeHtml(formatDateTime(ticket.showStartTime))}
                                </p>
                            </div>
                        </div>

                        <div style="background:var(--cb-surface-alt); padding:1rem; border-radius:var(--radius-sm); border:1px solid var(--cb-border); margin-bottom:1.25rem;">
                            <div style="font-size:0.78rem; text-transform:uppercase; color:var(--cb-text-secondary); font-weight:700; margin-bottom:0.4rem;">
                                Reserved Seats (${ticket.seats.length})
                            </div>
                            <div style="display:flex; flex-wrap:wrap; gap:0.45rem;">
                                ${ticket.seats.map(s => `
                                    <span class="badge badge-dark">${escapeHtml(s.seatLabel)} • ${escapeHtml(s.seatType)} (${formatCurrency(s.priceAtBooking)})</span>
                                `).join('')}
                            </div>
                        </div>

                        <div style="border-top:1px solid var(--cb-border); padding-top:0.85rem;">
                            <div class="summary-row">
                                <span>Tickets Subtotal</span>
                                <span>${formatCurrency(ticket.subtotal)}</span>
                            </div>
                            <div class="summary-row text-secondary">
                                <span>Convenience Fee &amp; GST</span>
                                <span>${formatCurrency(ticket.fees)}</span>
                            </div>
                            <div class="summary-total">
                                <span>Total Amount (${escapeHtml(ticket.paymentStatus)})</span>
                                <span class="text-accent">${formatCurrency(ticket.totalAmount)}</span>
                            </div>
                            <div class="text-muted" style="font-size:0.78rem; margin-top:0.4rem;">
                                Payment Ref: <span class="font-mono">${escapeHtml(ticket.paymentReference)}</span> (${escapeHtml(ticket.paymentProvider)})
                            </div>
                        </div>
                    </div>

                    <div class="ticket-qr-box">
                        ${qrSvg}
                        <div style="font-size:0.75rem; font-weight:700; margin-top:0.5rem;">M-TICKET VERIFICATION QR</div>
                        <div class="font-mono text-muted" style="font-size:0.68rem; word-break:break-all; margin-top:0.25rem;">
                            Token: ${escapeHtml(ticket.verificationToken.substring(0, 16))}...
                        </div>
                    </div>
                </div>

                <div class="no-print" style="padding:1.25rem 2rem; background:var(--cb-surface-alt); border-top:1px solid var(--cb-border); display:flex; justify-content:space-between; flex-wrap:wrap; gap:0.75rem;">
                    <div style="display:flex; gap:0.65rem;">
                        <button type="button" id="print-ticket-btn" class="btn btn-primary btn-sm">Print / Save Ticket PDF</button>
                        <a href="/my-bookings.html" class="btn btn-secondary btn-sm">View My Bookings</a>
                    </div>
                    <a href="/index.html" class="btn btn-secondary btn-sm">Back to Home</a>
                </div>
            </div>
        `;

        document.getElementById('print-ticket-btn').addEventListener('click', () => window.print());
    } catch (err) {
        container.innerHTML = `<div class="alert alert-error">${escapeHtml(err.message)}</div>`;
    }
}

/**
 * 2. My Bookings Page (Upcoming, Past, Cancelled tabs + Cancel Booking action)
 */
export async function initMyBookingsPage() {
    initNavigation();
    const user = requireAuth(false);
    if (!user) return;

    const container = document.getElementById('my-bookings-list');
    const tabButtons = document.querySelectorAll('[data-booking-tab]');
    let allBookings = [];
    let activeTab = 'upcoming';

    async function loadBookings() {
        container.innerHTML = `<div class="card"><p class="text-secondary">Loading your bookings...</p></div>`;
        try {
            allBookings = await apiGet('/api/bookings/my');
            renderFilteredBookings();
        } catch (err) {
            container.innerHTML = `<div class="alert alert-error">${escapeHtml(err.message)}</div>`;
        }
    }

    function renderFilteredBookings() {
        const now = new Date();
        const filtered = allBookings.filter(b => {
            const start = new Date(b.showStartTime);
            if (activeTab === 'cancelled') return b.status === 'CANCELLED';
            if (activeTab === 'past') return b.status === 'CONFIRMED' && start <= now;
            return b.status === 'CONFIRMED' && start > now;
        });

        if (filtered.length === 0) {
            container.innerHTML = `
                <div class="empty-state">
                    <h3>No ${escapeHtml(activeTab)} bookings found</h3>
                    <p>Ready for your next cinema experience? Explore movies playing near you.</p>
                    <a href="/movies.html" class="btn btn-primary btn-sm">Browse Movies</a>
                </div>
            `;
            return;
        }

        container.innerHTML = filtered.map(b => {
            const seatList = (b.seats || []).map(s => s.seatLabel).join(', ');
            const statusBadge = b.status === 'CONFIRMED'
                ? `<span class="badge badge-success">Confirmed</span>`
                : `<span class="badge badge-danger">Cancelled</span>`;

            return `
                <article class="card" style="margin-bottom:1.15rem; display:grid; grid-template-columns: 96px 1fr auto; gap:1.25rem; align-items:center;">
                    <img
                        src="${escapeHtml(b.moviePosterUrl)}"
                        alt="${escapeHtml(b.movieTitle)}"
                        style="width:96px; height:140px; object-fit:cover; border-radius:var(--radius-sm);"
                        onerror="this.onerror=null;this.src='/assets/posters/fallback-poster.svg';"
                    />
                    <div>
                        <div style="display:flex; align-items:center; gap:0.6rem; margin-bottom:0.35rem; flex-wrap:wrap;">
                            ${statusBadge}
                            <span class="font-mono text-secondary" style="font-size:0.82rem; font-weight:700;">Ref: ${escapeHtml(b.bookingReference)}</span>
                        </div>
                        <h3 style="font-size:1.2rem; margin-bottom:0.2rem;">${escapeHtml(b.movieTitle)}</h3>
                        <p style="font-size:0.9rem; font-weight:600;">${escapeHtml(b.theatreName)} • ${escapeHtml(b.screenName)} (${escapeHtml(b.format)})</p>
                        <p style="font-size:0.86rem; color:var(--cb-accent); font-weight:700; margin:0.25rem 0;">
                            Showtime: ${escapeHtml(formatDateTime(b.showStartTime))}
                        </p>
                        <p class="text-secondary" style="font-size:0.86rem;">
                            Seats: <strong>${escapeHtml(seatList)}</strong> • Total Paid: <strong>${formatCurrency(b.totalAmount)}</strong>
                        </p>
                    </div>
                    <div style="display:flex; flex-direction:column; gap:0.5rem; min-width:150px;">
                        <a href="/booking-confirmation.html?bookingId=${encodeURIComponent(b.id)}" class="btn btn-primary btn-sm">
                            View QR Ticket
                        </a>
                        ${b.cancellable ? `
                            <button type="button" class="btn btn-danger btn-sm" data-cancel-id="${b.id}" data-cancel-ref="${escapeHtml(b.bookingReference)}">
                                Cancel Booking
                            </button>
                        ` : ''}
                    </div>
                </article>
            `;
        }).join('');

        container.querySelectorAll('[data-cancel-id]').forEach(btn => {
            btn.addEventListener('click', () => {
                const id = btn.getAttribute('data-cancel-id');
                const ref = btn.getAttribute('data-cancel-ref');
                openCancelConfirmModal(id, ref);
            });
        });
    }

    function openCancelConfirmModal(bookingId, bookingRef) {
        const modal = document.createElement('div');
        modal.className = 'modal-backdrop';
        modal.innerHTML = `
            <div class="modal-card" role="dialog" aria-modal="true">
                <div class="modal-header">
                    <h3>Confirm Booking Cancellation</h3>
                </div>
                <p style="color:var(--cb-text-secondary); margin-bottom:1.25rem;">
                    Are you sure you want to cancel booking <strong class="font-mono">${escapeHtml(bookingRef)}</strong>?
                    Your reserved seats will be released immediately so other customers can book them.
                </p>
                <div style="display:flex; justify-content:flex-end; gap:0.75rem;">
                    <button type="button" class="btn btn-secondary btn-sm" id="cancel-modal-dismiss">Keep Booking</button>
                    <button type="button" class="btn btn-danger btn-sm" id="cancel-modal-confirm">Yes, Cancel Booking</button>
                </div>
            </div>
        `;
        document.body.appendChild(modal);

        modal.querySelector('#cancel-modal-dismiss').addEventListener('click', () => modal.remove());
        modal.querySelector('#cancel-modal-confirm').addEventListener('click', async (e) => {
            const confirmBtn = e.target;
            confirmBtn.disabled = true;
            confirmBtn.textContent = 'Cancelling...';
            try {
                await apiPost(`/api/bookings/${encodeURIComponent(bookingId)}/cancel`, {});
                modal.remove();
                showToast(`Booking ${bookingRef} has been cancelled and seats released.`, 'success');
                await loadBookings();
            } catch (err) {
                modal.remove();
                showToast(err.message || 'Could not cancel booking.', 'error');
            }
        });
    }

    tabButtons.forEach(btn => {
        btn.addEventListener('click', () => {
            tabButtons.forEach(b => b.classList.remove('btn-primary'));
            tabButtons.forEach(b => b.classList.add('btn-secondary'));
            btn.classList.remove('btn-secondary');
            btn.classList.add('btn-primary');
            activeTab = btn.getAttribute('data-booking-tab');
            renderFilteredBookings();
        });
    });

    await loadBookings();
}
