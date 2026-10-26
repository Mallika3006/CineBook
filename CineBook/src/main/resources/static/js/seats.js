/**
 * CineBook Interactive Seat Map Selection Module (seats.html)
 * Fetches real-time seat layout & availability from GET /api/showtimes/{id}/seats,
 * enforces maximum seats per order, updates breakdown immediately, and revalidates before checkout.
 */
import {
    apiGet,
    apiPost,
    escapeHtml,
    formatCurrency,
    formatDateTime,
    getStoredUser,
    showToast
} from './api.js';
import { initNavigation } from './navigation.js';

export async function initSeatSelectionPage() {
    initNavigation();

    const params = new URLSearchParams(window.location.search);
    const showtimeId = params.get('showtimeId');

    const headerBanner = document.getElementById('seat-showtime-header');
    const seatGridContainer = document.getElementById('auditorium-seat-map');
    const summaryContainer = document.getElementById('seat-selection-summary');
    const conflictBanner = document.getElementById('seat-conflict-banner');

    if (!showtimeId) {
        seatGridContainer.innerHTML = `<div class="alert alert-error">Missing showtimeId parameter. <a href="/movies.html">Browse Movies</a></div>`;
        return;
    }

    let seatMapData = null;
    const selectedSeatIds = new Set();

    async function loadSeatMap(preserveSelection = false) {
        try {
            seatMapData = await apiGet(`/api/showtimes/${encodeURIComponent(showtimeId)}/seats`);
            const st = seatMapData.showtime;

            headerBanner.innerHTML = `
                <div style="display:flex; align-items:center; justify-content:space-between; flex-wrap:wrap; gap:1rem;">
                    <div style="display:flex; align-items:center; gap:1rem;">
                        <img
                            src="${escapeHtml(st.moviePosterUrl)}"
                            alt="${escapeHtml(st.movieTitle)}"
                            style="width:54px; height:80px; object-fit:cover; border-radius:6px;"
                            onerror="this.onerror=null;this.src='/assets/posters/fallback-poster.svg';"
                        />
                        <div>
                            <h1 style="font-size:1.4rem; margin-bottom:0.2rem;">${escapeHtml(st.movieTitle)}</h1>
                            <p class="text-secondary" style="font-size:0.9rem;">
                                ${escapeHtml(st.theatreName)} (${escapeHtml(st.screenName)}) • ${escapeHtml(st.format)}
                            </p>
                            <p style="font-size:0.86rem; font-weight:600; color:var(--cb-accent);">
                                ${escapeHtml(formatDateTime(st.startTime))}
                            </p>
                        </div>
                    </div>
                    <a href="/movie-details.html?id=${encodeURIComponent(st.movieId)}" class="btn btn-secondary btn-sm">
                        ← Change Showtime
                    </a>
                </div>
            `;

            if (preserveSelection) {
                // Remove any seat from selection that became BOOKED or HELD
                for (const seat of seatMapData.seats) {
                    if (selectedSeatIds.has(seat.id) && seat.availabilityStatus !== 'AVAILABLE') {
                        selectedSeatIds.delete(seat.id);
                    }
                }
            } else {
                selectedSeatIds.clear();
            }

            renderAuditoriumSeats();
            renderSummaryPanel();
        } catch (err) {
            seatGridContainer.innerHTML = `<div class="alert alert-error">${escapeHtml(err.message)}</div>`;
        }
    }

    function renderAuditoriumSeats() {
        const seats = seatMapData.seats || [];
        const st = seatMapData.showtime;

        const tiers = [
            { type: 'RECLINER', label: `Royal Recliner — ${formatCurrency(st.reclinerPrice)}` },
            { type: 'PREMIUM', label: `Prime Executive — ${formatCurrency(st.premiumPrice)}` },
            { type: 'REGULAR', label: `Classic Regular — ${formatCurrency(st.ticketPrice)}` }
        ];

        let html = `
            <div class="cinema-screen-container">
                <div class="cinema-screen-arc"></div>
                <div class="cinema-screen-label">All Eyes This Way Please — Cinema Screen</div>
            </div>
        `;

        for (const tier of tiers) {
            const tierSeats = seats.filter(s => s.seatType === tier.type);
            if (tierSeats.length === 0) continue;

            const rowsMap = new Map();
            for (const s of tierSeats) {
                if (!rowsMap.has(s.rowLabel)) rowsMap.set(s.rowLabel, []);
                rowsMap.get(s.rowLabel).push(s);
            }

            html += `<div class="seat-tier-section">`;
            html += `<div class="seat-tier-title">${escapeHtml(tier.label)}</div>`;

            for (const [rowLabel, rowSeats] of rowsMap.entries()) {
                rowSeats.sort((a, b) => a.seatNumber - b.seatNumber);
                html += `<div class="seat-row" role="group" aria-label="Row ${escapeHtml(rowLabel)}">`;
                html += `<span class="seat-row-label">${escapeHtml(rowLabel)}</span>`;

                for (const seat of rowSeats) {
                    const isSelected = selectedSeatIds.has(seat.id);
                    const status = seat.availabilityStatus;
                    let stateClass = '';
                    let disabledAttr = '';

                    if (status === 'BOOKED') {
                        stateClass = 'booked';
                        disabledAttr = 'disabled';
                    } else if (status === 'HELD') {
                        stateClass = 'held';
                        disabledAttr = 'disabled';
                    } else if (status !== 'AVAILABLE') {
                        stateClass = 'unavailable';
                        disabledAttr = 'disabled';
                    } else if (isSelected) {
                        stateClass = 'selected';
                    }

                    const aisleClass = (seat.seatNumber === 3 || seat.seatNumber === 7) ? 'seat-aisle-after' : '';
                    html += `
                        <button
                            type="button"
                            class="seat-btn ${stateClass} ${aisleClass}"
                            data-seat-id="${seat.id}"
                            data-seat-label="${escapeHtml(seat.seatLabel)}"
                            ${disabledAttr}
                            aria-label="Seat ${escapeHtml(seat.seatLabel)} ${escapeHtml(seat.seatType)} ${formatCurrency(seat.price)} ${status}"
                            title="${escapeHtml(seat.seatLabel)} (${escapeHtml(seat.seatType)}) — ${formatCurrency(seat.price)}"
                        >
                            ${escapeHtml(seat.seatNumber)}
                        </button>
                    `;
                }

                html += `<span class="seat-row-label">${escapeHtml(rowLabel)}</span>`;
                html += `</div>`;
            }
            html += `</div>`;
        }

        html += `
            <div class="seat-legend">
                <span class="legend-item">
                    <span class="legend-box" style="background:var(--seat-available-bg); border-color:var(--seat-available-border);"></span>
                    Available
                </span>
                <span class="legend-item">
                    <span class="legend-box" style="background:var(--seat-selected-bg); border-color:var(--seat-selected-border);"></span>
                    Selected
                </span>
                <span class="legend-item">
                    <span class="legend-box" style="background:var(--seat-held-bg); border-color:var(--seat-held-border);"></span>
                    Temporarily Held
                </span>
                <span class="legend-item">
                    <span class="legend-box" style="background:var(--seat-booked-bg); border-color:var(--seat-booked-border);"></span>
                    Booked
                </span>
            </div>
        `;

        seatGridContainer.innerHTML = html;

        seatGridContainer.querySelectorAll('.seat-btn:not(:disabled)').forEach(btn => {
            btn.addEventListener('click', () => {
                const id = Number(btn.getAttribute('data-seat-id'));
                if (selectedSeatIds.has(id)) {
                    selectedSeatIds.delete(id);
                } else {
                    const maxSeats = seatMapData.maxSeatsPerBooking || 10;
                    if (selectedSeatIds.size >= maxSeats) {
                        showToast(`You can select up to ${maxSeats} seats in a single booking.`, 'warning');
                        return;
                    }
                    selectedSeatIds.add(id);
                }
                renderAuditoriumSeats();
                renderSummaryPanel();
            });
        });
    }

    function renderSummaryPanel() {
        const selectedSeats = (seatMapData.seats || []).filter(s => selectedSeatIds.has(s.id));
        const count = selectedSeats.length;

        const subtotal = selectedSeats.reduce((acc, s) => acc + Number(s.price), 0);
        const feePct = Number(seatMapData.convenienceFeePercent || 8.5);
        const gstPct = Number(seatMapData.gstOnFeePercent || 18.0);
        const convFee = +(subtotal * (feePct / 100)).toFixed(2);
        const gst = +(convFee * (gstPct / 100)).toFixed(2);
        const totalFees = +(convFee + gst).toFixed(2);
        const grandTotal = +(subtotal + totalFees).toFixed(2);

        if (count === 0) {
            summaryContainer.innerHTML = `
                <h3 style="margin-bottom:0.75rem;">Booking Summary</h3>
                <p class="text-secondary" style="font-size:0.9rem; margin-bottom:1.25rem;">
                    Click on available seats in the auditorium map to build your order.
                </p>
                <div style="background:var(--cb-surface-alt); padding:1rem; border-radius:var(--radius-sm); font-size:0.84rem; color:var(--cb-text-secondary);">
                    • Up to ${escapeHtml(seatMapData.maxSeatsPerBooking)} seats per booking<br/>
                    • Instant e-ticket with QR code<br/>
                    • Cancel up to 60 mins before showtime
                </div>
                <button type="button" class="btn btn-primary btn-block btn-lg" style="margin-top:1.25rem;" disabled>
                    Select Seats to Continue
                </button>
            `;
            return;
        }

        const seatBadges = selectedSeats.map(s =>
            `<span class="badge badge-dark">${escapeHtml(s.seatLabel)} (${escapeHtml(s.seatType)})</span>`
        ).join(' ');

        summaryContainer.innerHTML = `
            <h3 style="margin-bottom:0.75rem;">Booking Summary</h3>
            <div style="margin-bottom:1rem;">
                <div class="text-secondary" style="font-size:0.8rem; margin-bottom:0.35rem;">Selected Seats (${count})</div>
                <div style="display:flex; flex-wrap:wrap; gap:0.35rem;">${seatBadges}</div>
            </div>
            <div style="border-top:1px solid var(--cb-border); padding-top:0.85rem;">
                ${selectedSeats.map(s => `
                    <div class="summary-row">
                        <span>Seat ${escapeHtml(s.seatLabel)} (${escapeHtml(s.seatType)})</span>
                        <span>${formatCurrency(s.price)}</span>
                    </div>
                `).join('')}
                <div class="summary-row" style="font-weight:600; padding-top:0.4rem; border-top:1px solid var(--cb-border);">
                    <span>Tickets Subtotal</span>
                    <span>${formatCurrency(subtotal)}</span>
                </div>
                <div class="summary-row text-secondary" style="font-size:0.84rem;">
                    <span>Convenience Fee (${feePct}% + ${gstPct}% GST)</span>
                    <span>${formatCurrency(totalFees)}</span>
                </div>
                <div class="summary-total">
                    <span>Payable Estimate</span>
                    <span class="text-accent">${formatCurrency(grandTotal)}</span>
                </div>
            </div>
            <button type="button" id="proceed-checkout-btn" class="btn btn-primary btn-block btn-lg" style="margin-top:1.25rem;">
                Proceed to Checkout (${count} ${count === 1 ? 'Seat' : 'Seats'})
            </button>
        `;

        const proceedBtn = document.getElementById('proceed-checkout-btn');
        proceedBtn.addEventListener('click', handleProceedToCheckout);
    }

    async function handleProceedToCheckout() {
        conflictBanner.classList.add('hidden');
        const user = getStoredUser();
        const seatIdsArray = Array.from(selectedSeatIds);

        if (!user) {
            sessionStorage.setItem('cinebook_pending_checkout', JSON.stringify({
                showtimeId: Number(showtimeId),
                seatIds: seatIdsArray
            }));
            const returnUrl = `/seats.html?showtimeId=${encodeURIComponent(showtimeId)}`;
            showToast('Please log in to reserve your selected seats.', 'info');
            window.location.href = `/login.html?redirect=${encodeURIComponent(returnUrl)}`;
            return;
        }

        const btn = document.getElementById('proceed-checkout-btn');
        btn.disabled = true;
        btn.textContent = 'Verifying seat availability...';

        try {
            // Revalidate and place short-lived seat hold on the backend
            await apiPost('/api/bookings/holds', {
                showtimeId: Number(showtimeId),
                seatIds: seatIdsArray
            });

            sessionStorage.setItem('cinebook_checkout_state', JSON.stringify({
                showtimeId: Number(showtimeId),
                seatIds: seatIdsArray,
                idempotencyKey: `cb-${showtimeId}-${user.id}-${Date.now()}`
            }));

            window.location.href = `/checkout.html?showtimeId=${encodeURIComponent(showtimeId)}&seats=${seatIdsArray.join(',')}`;
        } catch (err) {
            conflictBanner.textContent = err.message || 'One or more seats were just claimed by another customer. Refreshing seat layout...';
            conflictBanner.classList.remove('hidden');
            await loadSeatMap(true);
        }
    }

    await loadSeatMap(false);

    // Restore pending selection if returning from login
    const pendingRaw = sessionStorage.getItem('cinebook_pending_checkout');
    if (pendingRaw) {
        try {
            const pending = JSON.parse(pendingRaw);
            if (Number(pending.showtimeId) === Number(showtimeId) && Array.isArray(pending.seatIds)) {
                sessionStorage.removeItem('cinebook_pending_checkout');
                for (const id of pending.seatIds) {
                    const seatObj = (seatMapData.seats || []).find(s => s.id === Number(id));
                    if (seatObj && seatObj.availabilityStatus === 'AVAILABLE') {
                        selectedSeatIds.add(Number(id));
                    }
                }
                renderAuditoriumSeats();
                renderSummaryPanel();
            }
        } catch {
            sessionStorage.removeItem('cinebook_pending_checkout');
        }
    }
}
