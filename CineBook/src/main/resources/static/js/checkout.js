/**
 * CineBook Checkout Page Module (checkout.html)
 * Obtains authoritative server-calculated prices from POST /api/bookings/price-preview
 * and submits booking with an idempotency key to POST /api/bookings.
 */
import {
    apiGet,
    apiPost,
    escapeHtml,
    formatCurrency,
    formatDateTime,
    showToast
} from './api.js';
import { initNavigation, requireAuth } from './navigation.js';

export async function initCheckoutPage() {
    initNavigation();
    const user = requireAuth(false);
    if (!user) return;

    const params = new URLSearchParams(window.location.search);
    const showtimeId = Number(params.get('showtimeId'));
    const seatIdsParam = params.get('seats') || '';
    const seatIds = seatIdsParam.split(',').map(s => Number(s.trim())).filter(n => n > 0);

    const detailsContainer = document.getElementById('checkout-details-container');
    const priceContainer = document.getElementById('checkout-price-container');
    const errorBox = document.getElementById('checkout-error');

    if (!showtimeId || seatIds.length === 0) {
        detailsContainer.innerHTML = `<div class="alert alert-error">Invalid checkout session. <a href="/movies.html">Return to Movies</a></div>`;
        return;
    }

    let storedState = {};
    try {
        storedState = JSON.parse(sessionStorage.getItem('cinebook_checkout_state') || '{}');
    } catch {
        storedState = {};
    }
    const idempotencyKey = storedState.idempotencyKey || `cb-${showtimeId}-${user.id}-${Date.now()}`;

    try {
        const [showtime, preview] = await Promise.all([
            apiGet(`/api/showtimes/${encodeURIComponent(showtimeId)}`),
            apiPost('/api/bookings/price-preview', { showtimeId, seatIds })
        ]);

        detailsContainer.innerHTML = `
            <div style="display:flex; gap:1.25rem; align-items:flex-start; flex-wrap:wrap;">
                <img
                    src="${escapeHtml(showtime.moviePosterUrl)}"
                    alt="${escapeHtml(showtime.movieTitle)}"
                    style="width:110px; border-radius:var(--radius-sm);"
                    onerror="this.onerror=null;this.src='/assets/posters/fallback-poster.svg';"
                />
                <div style="flex:1;">
                    <span class="badge badge-dark" style="margin-bottom:0.4rem;">${escapeHtml(showtime.format)} • ${escapeHtml(showtime.movieLanguage)}</span>
                    <h2 style="font-size:1.5rem; margin-bottom:0.35rem;">${escapeHtml(showtime.movieTitle)}</h2>
                    <p style="font-weight:600; color:var(--cb-text-primary);">${escapeHtml(showtime.theatreName)}</p>
                    <p class="text-secondary" style="font-size:0.86rem; margin-bottom:0.5rem;">${escapeHtml(showtime.theatreAddress)}</p>
                    <p style="font-weight:700; color:var(--cb-accent); font-size:0.95rem;">
                        ${escapeHtml(showtime.screenName)} • ${escapeHtml(formatDateTime(showtime.startTime))}
                    </p>
                </div>
            </div>

            <div style="margin-top:1.5rem; padding-top:1.25rem; border-top:1px solid var(--cb-border);">
                <h3 style="font-size:1.05rem; margin-bottom:0.75rem;">Selected Seats (${preview.seats.length})</h3>
                <div style="display:flex; flex-wrap:wrap; gap:0.5rem;">
                    ${preview.seats.map(s => `
                        <div style="padding:0.45rem 0.85rem; border-radius:var(--radius-sm); background:var(--cb-surface-alt); border:1px solid var(--cb-border); font-size:0.86rem; font-weight:600;">
                            Seat ${escapeHtml(s.seatLabel)} • ${escapeHtml(s.seatType)} (${formatCurrency(s.priceAtBooking)})
                        </div>
                    `).join('')}
                </div>
            </div>

            <div class="alert alert-info" style="margin-top:1.5rem; margin-bottom:0;">
                <strong>Demo Checkout Mode:</strong> This environment uses a clearly labelled Demo Checkout confirmation flow. No real credit card or UPI charge is collected, and the payment integration layer (<code>PaymentGateway</code>) is ready for external gateway adapters.
            </div>
        `;

        priceContainer.innerHTML = `
            <h3 style="margin-bottom:1rem;">Server-Verified Price Breakdown</h3>
            ${preview.seats.map(s => `
                <div class="summary-row">
                    <span>Seat ${escapeHtml(s.seatLabel)} (${escapeHtml(s.seatType)})</span>
                    <span>${formatCurrency(s.priceAtBooking)}</span>
                </div>
            `).join('')}
            <div class="summary-row" style="font-weight:600; border-top:1px solid var(--cb-border); padding-top:0.6rem;">
                <span>Tickets Subtotal</span>
                <span>${formatCurrency(preview.subtotal)}</span>
            </div>
            <div class="summary-row text-secondary" style="font-size:0.86rem;">
                <span>Booking Convenience Fee</span>
                <span>${formatCurrency(preview.convenienceFee)}</span>
            </div>
            <div class="summary-row text-secondary" style="font-size:0.86rem;">
                <span>Integrated GST (18% on Fee)</span>
                <span>${formatCurrency(preview.gstAmount)}</span>
            </div>
            <div class="summary-total">
                <span>Total Amount</span>
                <span class="text-accent">${formatCurrency(preview.totalAmount)}</span>
            </div>

            <div style="margin-top:1.15rem; font-size:0.8rem; color:var(--cb-text-secondary); line-height:1.5;">
                By clicking <strong>Confirm Demo Booking</strong>, you agree to CineBook's booking terms. Cancellations are permitted up to 60 minutes before showtime start.
            </div>

            <button type="button" id="confirm-booking-btn" class="btn btn-primary btn-block btn-lg" style="margin-top:1.15rem;">
                Confirm Demo Booking • ${formatCurrency(preview.totalAmount)}
            </button>
            <a href="/seats.html?showtimeId=${encodeURIComponent(showtimeId)}" class="btn btn-secondary btn-block btn-sm" style="margin-top:0.65rem;">
                ← Modify Seat Selection
            </a>
        `;

        const confirmBtn = document.getElementById('confirm-booking-btn');
        confirmBtn.addEventListener('click', async () => {
            errorBox.classList.add('hidden');
            confirmBtn.disabled = true;
            confirmBtn.textContent = 'Reserving seats & confirming booking...';

            try {
                const booking = await apiPost(
                    '/api/bookings',
                    {
                        showtimeId,
                        seatIds,
                        idempotencyKey
                    },
                    { 'X-Idempotency-Key': idempotencyKey }
                );
                sessionStorage.removeItem('cinebook_checkout_state');
                showToast(`Booking confirmed! Reference: ${booking.bookingReference}`, 'success');
                window.location.href = `/booking-confirmation.html?bookingId=${encodeURIComponent(booking.id)}`;
            } catch (err) {
                errorBox.innerHTML = `
                    <strong>Booking could not be completed:</strong> ${escapeHtml(err.message)}
                    <div style="margin-top:0.5rem;">
                        <a href="/seats.html?showtimeId=${encodeURIComponent(showtimeId)}" class="btn btn-secondary btn-sm">
                            Return to Seat Map &amp; Choose Available Seats
                        </a>
                    </div>
                `;
                errorBox.classList.remove('hidden');
                confirmBtn.disabled = false;
                confirmBtn.textContent = `Retry Confirm Booking • ${formatCurrency(preview.totalAmount)}`;
            }
        });
    } catch (err) {
        errorBox.textContent = err.message;
        errorBox.classList.remove('hidden');
    }
}
