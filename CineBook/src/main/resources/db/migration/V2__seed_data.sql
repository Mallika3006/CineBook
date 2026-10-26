-- ============================================================================
-- Flyway Migration V2: CineBook Rich Demo Seed Data
-- Includes Genres, 10 Movies, Theatres across Bengaluru/Mumbai/Hyderabad,
-- Screens, Physical Seat Layouts (Regular/Premium/Recliner), and Future Showtimes.
-- No confirmed bookings are seeded so initial seat maps are clean and predictable.
-- ============================================================================

-- 1. SEED GENRES
INSERT INTO genres (id, name) VALUES
    (1, 'Sci-Fi'),
    (2, 'Action'),
    (3, 'Thriller'),
    (4, 'Drama'),
    (5, 'Animation'),
    (6, 'Comedy'),
    (7, 'Adventure'),
    (8, 'Mystery')
ON CONFLICT (name) DO NOTHING;

SELECT setval('genres_id_seq', (SELECT COALESCE(MAX(id), 1) FROM genres));

-- 2. SEED MOVIES (10 Sample Movies with local high-contrast SVG posters & backdrops)
INSERT INTO movies (
    id, title, description, poster_url, backdrop_url, genre_id,
    language, duration_minutes, release_date, age_rating,
    cast_details, director, trailer_url, release_status, popularity_score, is_active
) VALUES
(
    1,
    'Chronos Protocol',
    'When a quantum astrophysicist in Bengaluru discovers a temporal echo transmitted from forty-eight hours in the future, she must assemble an orbital strike team to prevent a cascading solar grid collapse before the countdown reaches zero.',
    '/assets/posters/movie-1.svg',
    '/assets/posters/backdrop-1.svg',
    1,
    'English',
    148,
    CURRENT_DATE - INTERVAL '10 days',
    'UA 13+',
    'Elena Rostova, Vikramaditya Rao, Marcus Vance, Kenji Takahashi',
    'Aarav Menon',
    'https://www.youtube.com/watch?v=dQw4w9WgXcQ',
    'NOW_SHOWING',
    98,
    TRUE
),
(
    2,
    'Kaalacakra: The Last Guardian',
    'Set across ancient temple citadels of the Western Ghats and modern-day Karnataka, a forensic epigraphist decodes a thousand-year-old bronze inscription that awakens a dormant guardian order sworn to protect the realm.',
    '/assets/posters/movie-2.svg',
    '/assets/posters/backdrop-2.svg',
    2,
    'Kannada',
    162,
    CURRENT_DATE - INTERVAL '5 days',
    'UA 16+',
    'Darshan Gowda, Ananya Bhat, Prakash Hegde, Rajeshwari Nair',
    'Prashanth Shetty',
    'https://www.youtube.com/watch?v=dQw4w9WgXcQ',
    'NOW_SHOWING',
    96,
    TRUE
),
(
    3,
    'Velvet Horizon',
    'A gripping high-stakes aviation thriller following the crew of Flight CB-409 on a non-stop polar route when an encrypted cyber-intrusion locks the flight deck controls at 41,000 feet.',
    '/assets/posters/movie-3.svg',
    '/assets/posters/backdrop-3.svg',
    3,
    'Hindi',
    134,
    CURRENT_DATE - INTERVAL '14 days',
    'UA 13+',
    'Kabir Malhotra, Tara Deshmukh, Sameer Kochhar, Zoya Siddiqui',
    'Rohan Kapoor',
    'https://www.youtube.com/watch?v=dQw4w9WgXcQ',
    'NOW_SHOWING',
    92,
    TRUE
),
(
    4,
    'Monsoon Rhapsody',
    'Two estranged classical musicians reunite in a rain-drenched coastal conservatory in Kochi to compose a final symphony that bridges three generations of family secrets and unspoken love.',
    '/assets/posters/movie-4.svg',
    '/assets/posters/backdrop-4.svg',
    4,
    'Malayalam',
    140,
    CURRENT_DATE - INTERVAL '20 days',
    'U',
    'Nivin Varghese, Parvathy Kurup, Siddique Menon',
    'Anjali Krishnan',
    'https://www.youtube.com/watch?v=dQw4w9WgXcQ',
    'NOW_SHOWING',
    89,
    TRUE
),
(
    5,
    'Starlight Odyssey: Ark of Orion',
    'An enchanting hand-crafted animated adventure about a clockwork navigator and a bioluminescent sky-fox who sail across floating cloud archipelagos to relight the fallen constellations.',
    '/assets/posters/movie-5.svg',
    '/assets/posters/backdrop-5.svg',
    5,
    'English',
    112,
    CURRENT_DATE - INTERVAL '7 days',
    'U',
    'Clara Jensen, Liam O''Connor, Mei-Ling Zhou',
    'Sophia Lindqvist',
    'https://www.youtube.com/watch?v=dQw4w9WgXcQ',
    'NOW_SHOWING',
    94,
    TRUE
),
(
    6,
    'Deccan Express 99',
    'A razor-sharp mystery aboard a luxury heritage train travelling from Secunderabad to Goa where eight passengers hold interlocking alibis for a missing imperial diamond.',
    '/assets/posters/movie-6.svg',
    '/assets/posters/backdrop-6.svg',
    8,
    'Telugu',
    146,
    CURRENT_DATE - INTERVAL '3 days',
    'UA 13+',
    'Siddharth Reddy, Keerthy Varma, Jagapathi Babu, Vennela Kishore',
    'Karthikeya Varma',
    'https://www.youtube.com/watch?v=dQw4w9WgXcQ',
    'NOW_SHOWING',
    91,
    TRUE
),
(
    7,
    'Startup Shaadi & Chaos',
    'When two rival founders pitching to the same venture capital fund in Indiranagar discover their parents have secretly arranged their wedding for the same weekend as Demo Day, hilarity ensues.',
    '/assets/posters/movie-7.svg',
    '/assets/posters/backdrop-7.svg',
    6,
    'Hindi',
    126,
    CURRENT_DATE - INTERVAL '12 days',
    'UA 7+',
    'Ayushmann Khanna, Kriti Sharma, Pankaj Tripathi, Supriya Pathak',
    'Neha Dhupia Verma',
    'https://www.youtube.com/watch?v=dQw4w9WgXcQ',
    'NOW_SHOWING',
    87,
    TRUE
),
(
    8,
    'Chola: Winds of the Ocean',
    'An epic maritime adventure chronicling a twelfth-century naval expedition across the Bay of Bengal facing typhoons, pirate armadas, and diplomatic intrigue.',
    '/assets/posters/movie-8.svg',
    '/assets/posters/backdrop-8.svg',
    7,
    'Tamil',
    158,
    CURRENT_DATE - INTERVAL '9 days',
    'UA 13+',
    'Vikram Sethupathi, Aishwarya Rajesh, Prakash Raj',
    'Mani Ratnam Subramanian',
    'https://www.youtube.com/watch?v=dQw4w9WgXcQ',
    'NOW_SHOWING',
    95,
    TRUE
),
(
    9,
    'Solaris Syndicate',
    'In 2084, an orbital salvage crew uncovers an abandoned deep-space foundry still manufacturing autonomous sentinels. Coming soon to IMAX 3D and Dolby Atmos.',
    '/assets/posters/movie-9.svg',
    '/assets/posters/backdrop-9.svg',
    1,
    'English',
    152,
    CURRENT_DATE + INTERVAL '14 days',
    'UA 16+',
    'Daniel Kaluuya, Rebecca Ferguson, Dev Patel',
    'Denis Vance',
    'https://www.youtube.com/watch?v=dQw4w9WgXcQ',
    'COMING_SOON',
    93,
    TRUE
),
(
    10,
    'Namma Metro Midnight',
    'A hyper-kinetic neo-noir thriller unfolding over a single night across Bengaluru after the last metro train pulls into Majestic station.',
    '/assets/posters/movie-10.svg',
    '/assets/posters/backdrop-10.svg',
    3,
    'Kannada',
    138,
    CURRENT_DATE + INTERVAL '21 days',
    'UA 16+',
    'Rishab Raj, Rukmini Vasanth, Achyuth Kumar',
    'Hemanth Rao',
    'https://www.youtube.com/watch?v=dQw4w9WgXcQ',
    'COMING_SOON',
    90,
    TRUE
)
ON CONFLICT (id) DO NOTHING;

SELECT setval('movies_id_seq', (SELECT COALESCE(MAX(id), 1) FROM movies));

-- 3. SEED THEATRES
INSERT INTO theatres (id, name, address, city, is_active) VALUES
    (1, 'CineBook IMAX — Vega City', 'Bannerghatta Main Rd, Dollar Layout, BTM 2nd Stage, Bengaluru 560076', 'Bengaluru', TRUE),
    (2, 'CineBook Luxe — Orion Mall', 'Brigade Gateway, Dr Rajkumar Rd, Malleshwaram, Bengaluru 560055', 'Bengaluru', TRUE),
    (3, 'CineBook Premiere — Phoenix Marketcity', 'Whitefield Main Rd, Mahadevapura, Bengaluru 560048', 'Bengaluru', TRUE),
    (4, 'CineBook Heritage — Brigade Road', 'Brigade Rd, Shanthala Nagar, Ashok Nagar, Bengaluru 560001', 'Bengaluru', TRUE),
    (5, 'CineBook Icon — Lower Parel', 'Senapati Bapat Marg, Lower Parel, Mumbai 400013', 'Mumbai', TRUE),
    (6, 'CineBook Grand — Banjara Hills', 'Road No. 2, Banjara Hills, Hyderabad 500034', 'Hyderabad', TRUE)
ON CONFLICT (id) DO NOTHING;

SELECT setval('theatres_id_seq', (SELECT COALESCE(MAX(id), 1) FROM theatres));

-- 4. SEED SCREENS (80 seats per screen: Rows A-C Regular [30], Rows D-F Premium [30], Rows G-H Recliner [20])
INSERT INTO screens (id, theatre_id, name, capacity, is_active) VALUES
    (1, 1, 'Audi 1 — IMAX Laser', 80, TRUE),
    (2, 1, 'Audi 2 — Dolby Atmos', 80, TRUE),
    (3, 2, 'Screen 1 — Gold Luxe', 80, TRUE),
    (4, 2, 'Screen 2 — 4K RGB Laser', 80, TRUE),
    (5, 3, 'Audi 1 — IMAX 3D', 80, TRUE),
    (6, 3, 'Audi 2 — Prime', 80, TRUE),
    (7, 4, 'Royal Screen 1', 80, TRUE),
    (8, 5, 'Icon Audi 1', 80, TRUE),
    (9, 6, 'Grand Screen 1', 80, TRUE)
ON CONFLICT (id) DO NOTHING;

SELECT setval('screens_id_seq', (SELECT COALESCE(MAX(id), 1) FROM screens));

-- 5. SEED PHYSICAL SEATS FOR EVERY SCREEN (8 rows x 10 seats = 80 seats per screen)
INSERT INTO seats (screen_id, row_label, seat_number, seat_type, is_active)
SELECT
    s.id AS screen_id,
    r.row_label,
    n.seat_number,
    CASE
        WHEN r.row_label IN ('A', 'B', 'C') THEN 'REGULAR'
        WHEN r.row_label IN ('D', 'E', 'F') THEN 'PREMIUM'
        ELSE 'RECLINER'
    END AS seat_type,
    TRUE
FROM screens s
CROSS JOIN (
    VALUES ('A'), ('B'), ('C'), ('D'), ('E'), ('F'), ('G'), ('H')
) AS r(row_label)
CROSS JOIN generate_series(1, 10) AS n(seat_number)
ON CONFLICT (screen_id, row_label, seat_number) DO NOTHING;

-- 6. SEED SHOWTIMES OVER THE NEXT 7 DAYS (Day 0 +10h/14h/18h/21h through Day +6)
-- Uses date_trunc('day', NOW() AT TIME ZONE 'Asia/Kolkata') AT TIME ZONE 'Asia/Kolkata'
-- and starts from tomorrow + late evening today so all seeded showtimes are in the future.
INSERT INTO showtimes (
    movie_id, screen_id, start_time, end_time, format,
    ticket_price, premium_price, recliner_price, status
)
SELECT
    m_cfg.movie_id,
    m_cfg.screen_id,
    ((CURRENT_DATE + (d.day_offset || ' days')::INTERVAL + m_cfg.start_tod::INTERVAL) AT TIME ZONE 'Asia/Kolkata') AS start_time,
    ((CURRENT_DATE + (d.day_offset || ' days')::INTERVAL + m_cfg.start_tod::INTERVAL + ((m.duration_minutes + 25) || ' minutes')::INTERVAL) AT TIME ZONE 'Asia/Kolkata') AS end_time,
    m_cfg.format,
    m_cfg.regular_price,
    m_cfg.premium_price,
    m_cfg.recliner_price,
    'SCHEDULED'
FROM (
    VALUES
        -- Movie 1: Chronos Protocol
        (1, 1, '10:30:00', 'IMAX 2D', 280.00, 380.00, 520.00),
        (1, 1, '18:15:00', 'IMAX 3D', 320.00, 440.00, 600.00),
        (1, 3, '13:45:00', 'DOLBY ATMOS', 240.00, 340.00, 480.00),
        (1, 5, '21:15:00', 'IMAX 2D', 290.00, 390.00, 540.00),
        (1, 8, '19:00:00', 'IMAX 2D', 300.00, 420.00, 560.00),
        -- Movie 2: Kaalacakra: The Last Guardian
        (2, 2, '10:00:00', 'DOLBY ATMOS', 220.00, 310.00, 450.00),
        (2, 2, '17:30:00', 'DOLBY ATMOS', 250.00, 350.00, 490.00),
        (2, 4, '14:15:00', '2D', 200.00, 280.00, 420.00),
        (2, 7, '20:30:00', '2D', 210.00, 290.00, 430.00),
        -- Movie 3: Velvet Horizon
        (3, 3, '10:15:00', '2D', 200.00, 280.00, 400.00),
        (3, 4, '18:00:00', 'DOLBY ATMOS', 240.00, 330.00, 470.00),
        (3, 6, '21:30:00', '2D', 220.00, 310.00, 450.00),
        (3, 8, '15:00:00', '2D', 230.00, 320.00, 460.00),
        -- Movie 4: Monsoon Rhapsody
        (4, 6, '11:00:00', '2D', 180.00, 260.00, 380.00),
        (4, 7, '16:45:00', '2D', 190.00, 270.00, 390.00),
        -- Movie 5: Starlight Odyssey: Ark of Orion
        (5, 5, '10:45:00', '3D', 230.00, 320.00, 460.00),
        (5, 5, '14:30:00', 'IMAX 3D', 290.00, 390.00, 530.00),
        (5, 2, '14:00:00', '3D', 220.00, 300.00, 440.00),
        -- Movie 6: Deccan Express 99
        (6, 6, '14:45:00', '2D', 200.00, 290.00, 420.00),
        (6, 9, '19:30:00', 'DOLBY ATMOS', 240.00, 340.00, 480.00),
        -- Movie 7: Startup Shaadi & Chaos
        (7, 4, '10:45:00', '2D', 190.00, 270.00, 390.00),
        (7, 7, '13:15:00', '2D', 190.00, 270.00, 390.00),
        (7, 3, '21:45:00', '2D', 220.00, 310.00, 450.00),
        -- Movie 8: Chola: Winds of the Ocean
        (8, 1, '14:15:00', 'IMAX 2D', 270.00, 370.00, 510.00),
        (8, 5, '17:30:00', 'IMAX 2D', 280.00, 380.00, 520.00),
        (8, 6, '18:00:00', 'DOLBY ATMOS', 240.00, 330.00, 470.00)
) AS m_cfg(movie_id, screen_id, start_tod, format, regular_price, premium_price, recliner_price)
JOIN movies m ON m.id = m_cfg.movie_id
CROSS JOIN generate_series(0, 6) AS d(day_offset)
WHERE ((CURRENT_DATE + (d.day_offset || ' days')::INTERVAL + m_cfg.start_tod::INTERVAL) AT TIME ZONE 'Asia/Kolkata') > NOW() + INTERVAL '30 minutes';
