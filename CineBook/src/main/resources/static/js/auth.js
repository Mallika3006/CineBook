/**
 * CineBook Authentication & Profile Module
 */
import {
    apiGet,
    apiPost,
    apiPut,
    setAuthSession,
    getStoredUser,
    escapeHtml,
    formatDate,
    showToast
} from './api.js';
import { initNavigation, requireAuth } from './navigation.js';

export function initLoginPage() {
    initNavigation();
    const form = document.getElementById('login-form');
    const errorBox = document.getElementById('login-error');
    const submitBtn = document.getElementById('login-submit-btn');

    if (!form) return;

    form.addEventListener('submit', async (e) => {
        e.preventDefault();
        errorBox.classList.add('hidden');

        const email = document.getElementById('email').value.trim();
        const password = document.getElementById('password').value;

        if (!email || !password) {
            errorBox.textContent = 'Please enter both your email address and password.';
            errorBox.classList.remove('hidden');
            return;
        }

        submitBtn.disabled = true;
        submitBtn.textContent = 'Signing in...';

        try {
            const res = await apiPost('/api/auth/login', { email, password });
            setAuthSession(res.token, res.user);
            showToast(`Welcome back, ${res.user.fullName}!`, 'success');

            const params = new URLSearchParams(window.location.search);
            const redirect = params.get('redirect');
            setTimeout(() => {
                if (redirect && redirect.startsWith('/')) {
                    window.location.href = redirect;
                } else if (res.user.role === 'ADMIN') {
                    window.location.href = '/admin/dashboard.html';
                } else {
                    window.location.href = '/index.html';
                }
            }, 300);
        } catch (err) {
            errorBox.textContent = err.message || 'Invalid email or password.';
            errorBox.classList.remove('hidden');
            submitBtn.disabled = false;
            submitBtn.textContent = 'Sign In to CineBook';
        }
    });
}

export function initRegisterPage() {
    initNavigation();
    const form = document.getElementById('register-form');
    const errorBox = document.getElementById('register-error');
    const submitBtn = document.getElementById('register-submit-btn');

    if (!form) return;

    form.addEventListener('submit', async (e) => {
        e.preventDefault();
        errorBox.classList.add('hidden');

        const fullName = document.getElementById('fullName').value.trim();
        const email = document.getElementById('email').value.trim();
        const password = document.getElementById('password').value;
        const confirmPassword = document.getElementById('confirmPassword').value;

        if (fullName.length < 2) {
            errorBox.textContent = 'Full name must be at least 2 characters.';
            errorBox.classList.remove('hidden');
            return;
        }
        const strongRegex = /^(?=.*[a-z])(?=.*[A-Z])(?=.*\d).{8,}$/;
        if (!strongRegex.test(password)) {
            errorBox.textContent = 'Password must be at least 8 characters and include uppercase, lowercase, and a number.';
            errorBox.classList.remove('hidden');
            return;
        }
        if (password !== confirmPassword) {
            errorBox.textContent = 'Password and confirm password do not match.';
            errorBox.classList.remove('hidden');
            return;
        }

        submitBtn.disabled = true;
        submitBtn.textContent = 'Creating account...';

        try {
            const res = await apiPost('/api/auth/register', {
                fullName,
                email,
                password,
                confirmPassword
            });
            setAuthSession(res.token, res.user);
            showToast('Account created! Redirecting...', 'success');

            const params = new URLSearchParams(window.location.search);
            const redirect = params.get('redirect');
            setTimeout(() => {
                window.location.href = (redirect && redirect.startsWith('/')) ? redirect : '/index.html';
            }, 350);
        } catch (err) {
            errorBox.textContent = err.message || 'Registration failed. Please check your details.';
            errorBox.classList.remove('hidden');
            submitBtn.disabled = false;
            submitBtn.textContent = 'Create Account';
        }
    });
}

export async function initProfilePage() {
    initNavigation();
    const localUser = requireAuth(false);
    if (!localUser) return;

    const errorBox = document.getElementById('profile-error');
    const successBox = document.getElementById('profile-success');
    const form = document.getElementById('profile-form');
    const submitBtn = document.getElementById('profile-submit-btn');

    try {
        const freshUser = await apiGet('/api/users/me');
        document.getElementById('fullName').value = freshUser.fullName || '';
        document.getElementById('email').value = freshUser.email || '';
        document.getElementById('roleBadge').textContent = freshUser.role;
        document.getElementById('memberSince').textContent = formatDate(freshUser.createdAt);
    } catch (err) {
        errorBox.textContent = err.message;
        errorBox.classList.remove('hidden');
    }

    form.addEventListener('submit', async (e) => {
        e.preventDefault();
        errorBox.classList.add('hidden');
        successBox.classList.add('hidden');

        const fullName = document.getElementById('fullName').value.trim();
        const currentPassword = document.getElementById('currentPassword').value;
        const newPassword = document.getElementById('newPassword').value;

        submitBtn.disabled = true;
        submitBtn.textContent = 'Saving...';

        try {
            const updated = await apiPut('/api/users/me', {
                fullName,
                currentPassword: currentPassword || null,
                newPassword: newPassword || null
            });
            const stored = getStoredUser() || {};
            setAuthSession(null, { ...stored, fullName: updated.fullName });
            successBox.textContent = 'Profile updated successfully.';
            successBox.classList.remove('hidden');
            document.getElementById('currentPassword').value = '';
            document.getElementById('newPassword').value = '';
            initNavigation();
        } catch (err) {
            errorBox.textContent = err.message;
            errorBox.classList.remove('hidden');
        } finally {
            submitBtn.disabled = false;
            submitBtn.textContent = 'Save Profile Changes';
        }
    });
}
