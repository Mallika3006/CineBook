package com.cinebook.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.math.BigDecimal;

@ConfigurationProperties(prefix = "cinebook")
public class AppProperties {

    private String timezone = "Asia/Kolkata";
    private String defaultCity = "Bengaluru";
    private final Jwt jwt = new Jwt();
    private final Booking booking = new Booking();
    private final Showtime showtime = new Showtime();
    private final Admin admin = new Admin();
    private final DemoCustomer demoCustomer = new DemoCustomer();
    private final Cors cors = new Cors();

    public String getTimezone() {
        return timezone;
    }

    public void setTimezone(String timezone) {
        this.timezone = timezone;
    }

    public String getDefaultCity() {
        return defaultCity;
    }

    public void setDefaultCity(String defaultCity) {
        this.defaultCity = defaultCity;
    }

    public Jwt getJwt() {
        return jwt;
    }

    public Booking getBooking() {
        return booking;
    }

    public Showtime getShowtime() {
        return showtime;
    }

    public Admin getAdmin() {
        return admin;
    }

    public DemoCustomer getDemoCustomer() {
        return demoCustomer;
    }

    public Cors getCors() {
        return cors;
    }

    public static class Jwt {
        private String secret = "";
        private long expirationMs = 86400000L;
        private String cookieName = "cinebook_auth_token";
        private boolean cookieSecure = false;

        public String getSecret() {
            return secret;
        }

        public void setSecret(String secret) {
            this.secret = secret;
        }

        public long getExpirationMs() {
            return expirationMs;
        }

        public void setExpirationMs(long expirationMs) {
            this.expirationMs = expirationMs;
        }

        public String getCookieName() {
            return cookieName;
        }

        public void setCookieName(String cookieName) {
            this.cookieName = cookieName;
        }

        public boolean isCookieSecure() {
            return cookieSecure;
        }

        public void setCookieSecure(boolean cookieSecure) {
            this.cookieSecure = cookieSecure;
        }
    }

    public static class Booking {
        private int maxSeatsPerOrder = 10;
        private BigDecimal convenienceFeePercent = new BigDecimal("8.50");
        private BigDecimal gstOnFeePercent = new BigDecimal("18.00");
        private int holdDurationSeconds = 300;
        private int cancellationCutoffMinutes = 60;

        public int getMaxSeatsPerOrder() {
            return maxSeatsPerOrder;
        }

        public void setMaxSeatsPerOrder(int maxSeatsPerOrder) {
            this.maxSeatsPerOrder = maxSeatsPerOrder;
        }

        public BigDecimal getConvenienceFeePercent() {
            return convenienceFeePercent;
        }

        public void setConvenienceFeePercent(BigDecimal convenienceFeePercent) {
            this.convenienceFeePercent = convenienceFeePercent;
        }

        public BigDecimal getGstOnFeePercent() {
            return gstOnFeePercent;
        }

        public void setGstOnFeePercent(BigDecimal gstOnFeePercent) {
            this.gstOnFeePercent = gstOnFeePercent;
        }

        public int getHoldDurationSeconds() {
            return holdDurationSeconds;
        }

        public void setHoldDurationSeconds(int holdDurationSeconds) {
            this.holdDurationSeconds = holdDurationSeconds;
        }

        public int getCancellationCutoffMinutes() {
            return cancellationCutoffMinutes;
        }

        public void setCancellationCutoffMinutes(int cancellationCutoffMinutes) {
            this.cancellationCutoffMinutes = cancellationCutoffMinutes;
        }
    }

    public static class Showtime {
        private int turnaroundBufferMinutes = 25;

        public int getTurnaroundBufferMinutes() {
            return turnaroundBufferMinutes;
        }

        public void setTurnaroundBufferMinutes(int turnaroundBufferMinutes) {
            this.turnaroundBufferMinutes = turnaroundBufferMinutes;
        }
    }

    public static class Admin {
        private boolean bootstrapEnabled = false;
        private String name = "CineBook System Admin";
        private String email = "admin@cinebook.local";
        private String password = "";

        public boolean isBootstrapEnabled() {
            return bootstrapEnabled;
        }

        public void setBootstrapEnabled(boolean bootstrapEnabled) {
            this.bootstrapEnabled = bootstrapEnabled;
        }

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }

        public String getEmail() {
            return email;
        }

        public void setEmail(String email) {
            this.email = email;
        }

        public String getPassword() {
            return password;
        }

        public void setPassword(String password) {
            this.password = password;
        }
    }

    public static class DemoCustomer {
        private boolean enabled = false;
        private String name = "Aarav Sharma";
        private String email = "customer@cinebook.local";
        private String password = "";

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }

        public String getEmail() {
            return email;
        }

        public void setEmail(String email) {
            this.email = email;
        }

        public String getPassword() {
            return password;
        }

        public void setPassword(String password) {
            this.password = password;
        }
    }

    public static class Cors {
        private String allowedOrigins = "http://localhost:8080,http://127.0.0.1:8080";

        public String getAllowedOrigins() {
            return allowedOrigins;
        }

        public void setAllowedOrigins(String allowedOrigins) {
            this.allowedOrigins = allowedOrigins;
        }
    }
}
