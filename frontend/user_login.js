
  const API_BASE = "http://localhost:8089/api/students";
  const AUTH_BASE = "http://localhost:8089/api/auth";
  const GOOGLE_CLIENT_ID = "489484268154-mllmp32m1cpk8db03sfcq8bstl01ogpt.apps.googleusercontent.com";
  const REDIRECT_URI = "http://localhost:8089/api/auth/google/callback";
  const TRANSITION_MS = 250;

  // â”€â”€ UTILS â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€
  function showError(errEl, message) {
    errEl.querySelector('span').textContent = message;
    errEl.classList.add('visible');
    const input = errEl.closest('.field').querySelector('input');
    if (input) input.classList.add('is-error');
  }

  function clearError(errEl) {
    errEl.querySelector('span').textContent = '';
    errEl.classList.remove('visible');
    const input = errEl.closest('.field').querySelector('input');
    if (input) input.classList.remove('is-error');
  }

  function isValidEmail(val) {
    return /^[^\s@]+@[^\s@]+\.[^\s@]{2,}$/.test(val.trim());
  }

  async function parseApiResponse(response) {
    const ct = response.headers.get('content-type') || '';
    return ct.includes('application/json') ? response.json() : response.text();
  }

  function handleSocial(provider) {
    if (provider === 'Google') {
      // Redirect to Google OAuth
      const scope = encodeURIComponent('email profile');
      const authUrl = `https://accounts.google.com/o/oauth2/v2/auth?client_id=${GOOGLE_CLIENT_ID}&redirect_uri=${encodeURIComponent(REDIRECT_URI)}&response_type=code&scope=${scope}&access_type=offline&prompt=consent`;
      window.location.href = authUrl;
    } else {
      alert(`${provider} OAuth is not yet implemented.`);
    }
  }

  // â”€â”€ PANEL SWITCH â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€
  function switchPanel(target) {
    const loginPanel  = document.getElementById('panel-login');
    const signupPanel = document.getElementById('panel-signup');
    const tabLogin    = document.getElementById('tab-login');
    const tabSignup   = document.getElementById('tab-signup');
    const slider      = document.getElementById('tab-slider');
    const toSignup    = target === 'signup';

    const outPanel = toSignup ? loginPanel  : signupPanel;
    const inPanel  = toSignup ? signupPanel : loginPanel;

    // Update tabs
    tabLogin.classList.toggle('active', !toSignup);
    tabSignup.classList.toggle('active', toSignup);
    slider.classList.toggle('to-right', toSignup);

    outPanel.classList.add('fade-out');
    setTimeout(() => {
      outPanel.classList.add('hidden');
      outPanel.classList.remove('fade-out');
      inPanel.classList.remove('hidden');
      inPanel.classList.add('fade-in');
      inPanel.addEventListener('animationend', () => inPanel.classList.remove('fade-in'), { once: true });
    }, TRANSITION_MS);

    resetForms();
  }

  function resetForms() {
    document.querySelectorAll('.field-error').forEach(clearError);
    document.querySelectorAll('input').forEach(el => el.classList.remove('is-error'));
    document.querySelectorAll('.btn-primary').forEach(btn => {
      btn.disabled = true;
      btn.classList.remove('loading');
    });
    document.getElementById('strength-bar').style.display = 'none';
  }

  // â”€â”€ PASSWORD TOGGLE â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€
  document.querySelectorAll('.toggle-pw').forEach(btn => {
    btn.addEventListener('click', () => {
      const input = document.getElementById(btn.getAttribute('data-target'));
      const isPassword = input.type === 'password';
      input.type = isPassword ? 'text' : 'password';
      btn.querySelector('.icon-eye').style.display    = isPassword ? 'none'  : 'block';
      btn.querySelector('.icon-eye-off').style.display = isPassword ? 'block' : 'none';
    });
  });

  // â”€â”€ STRENGTH METER â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€
  const STRENGTH_LEVELS = [
    { label: '',       colors: [] },
    { label: 'Weak',   colors: ['#F87171'] },
    { label: 'Fair',   colors: ['#FB923C', '#FB923C'] },
    { label: 'Good',   colors: ['#FBBF24', '#FBBF24', '#FBBF24'] },
    { label: 'Strong', colors: ['#34D399', '#34D399', '#34D399', '#34D399'] },
  ];

  function calcStrength(pw) {
    let score = 0;
    if (pw.length >= 8)  score++;
    if (pw.length >= 12) score++;
    if (/[A-Z]/.test(pw) && /[a-z]/.test(pw)) score++;
    if (/[0-9]/.test(pw)) score++;
    if (/[^A-Za-z0-9]/.test(pw)) score++;
    return Math.min(Math.ceil(score * 4 / 5), 4);
  }

  function updateStrength(pw) {
    const bar = document.getElementById('strength-bar');
    const lbl = document.getElementById('strength-label');
    const segs = [1,2,3,4].map(i => document.getElementById(`seg-${i}`));

    if (!pw.length) { bar.style.display = 'none'; return; }

    bar.style.display = 'block';
    const level = calcStrength(pw);
    const { label, colors } = STRENGTH_LEVELS[level];

    segs.forEach((seg, i) => {
      seg.style.background = colors[i] || 'rgba(255,255,255,0.07)';
    });

    lbl.textContent = label;
    lbl.style.color = colors[0] || 'transparent';
  }

  // â”€â”€ LOGIN LOGIC â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€
  const loginEmail    = document.getElementById('login-email');
  const loginPassword = document.getElementById('login-password');
  const btnLogin      = document.getElementById('btn-login');
  const loginEmailErr = document.getElementById('login-email-err');
  const loginPwErr    = document.getElementById('login-password-err');

  function updateLoginBtn() {
    // Allow username or email (at least 3 chars) and password (at least 8 chars)
    btnLogin.disabled = !(loginEmail.value.trim().length >= 3 && loginPassword.value.length >= 8);
  }

  loginEmail.addEventListener('input', updateLoginBtn);
  loginPassword.addEventListener('input', updateLoginBtn);
  loginEmail.addEventListener('blur', () => {
    if (loginEmail.value && loginEmail.value.trim().length < 3) showError(loginEmailErr, 'Enter username or email');
    else clearError(loginEmailErr);
  });
  loginPassword.addEventListener('blur', () => {
    if (loginPassword.value && loginPassword.value.length < 8) showError(loginPwErr, 'At least 8 characters required');
    else clearError(loginPwErr);
  });

  document.getElementById('form-login').addEventListener('submit', async (e) => {
    e.preventDefault();
    const usernameOrEmailOk = loginEmail.value.trim().length >= 3;
    const pwOk = loginPassword.value.length >= 8;
    if (!usernameOrEmailOk) { showError(loginEmailErr, 'Enter username or email'); }
    if (!pwOk) { showError(loginPwErr, 'At least 8 characters required'); }
    if (!usernameOrEmailOk || !pwOk) return;

    btnLogin.classList.add('loading');
    btnLogin.disabled = true;
    clearError(loginPwErr);

    try {
      const response = await fetch(`${API_BASE}/login`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ email: loginEmail.value.trim(), password: loginPassword.value })
      });
      const result = await parseApiResponse(response);

      if (!response.ok) {
        showError(loginPwErr, (typeof result === 'string' && result.trim()) ? result : 'Invalid username/email or password');
        return;
      }

      const user = typeof result === 'object' && result !== null ? result : {};
      localStorage.setItem('userEmail', user.email || '');
      if (user.username) localStorage.setItem('userName', user.username);
      if (user.id != null) localStorage.setItem('studentId', String(user.id));
      else localStorage.removeItem('studentId');
      window.location.href = 'dashboard.html';
    } catch {
      showError(loginPwErr, 'Could not connect to server. Try again.');
    } finally {
      btnLogin.classList.remove('loading');
      updateLoginBtn();
    }
  });

  // â”€â”€ SIGNUP LOGIC â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€
  const signupName     = document.getElementById('signup-name');
  const signupUsername = document.getElementById('signup-username');
  const signupEmail    = document.getElementById('signup-email');
  const signupPw       = document.getElementById('signup-password');
  const signupConfirm  = document.getElementById('signup-confirm');
  const btnSignup      = document.getElementById('btn-signup');

  const signupNameErr     = document.getElementById('signup-name-err');
  const signupUsernameErr = document.getElementById('signup-username-err');
  const signupEmailErr    = document.getElementById('signup-email-err');
  const signupPwErr       = document.getElementById('signup-password-err');
  const signupConfirmErr  = document.getElementById('signup-confirm-err');

  function updateSignupBtn() {
    const nameOk     = signupName.value.trim().split(/\s+/).length >= 2;
    const usernameOk = signupUsername.value.trim().length >= 3;
    const emailOk    = isValidEmail(signupEmail.value);
    const pwOk       = signupPw.value.length >= 8;
    const matchOk    = signupConfirm.value === signupPw.value && signupConfirm.value !== '';
    btnSignup.disabled = !(nameOk && usernameOk && emailOk && pwOk && matchOk);
  }

  signupPw.addEventListener('input', () => { updateStrength(signupPw.value); updateSignupBtn(); });
  signupName.addEventListener('input', updateSignupBtn);
  signupUsername.addEventListener('input', updateSignupBtn);
  signupEmail.addEventListener('input', updateSignupBtn);
  signupConfirm.addEventListener('input', updateSignupBtn);

  signupName.addEventListener('blur', () => {
    if (signupName.value && signupName.value.trim().split(/\s+/).length < 2) showError(signupNameErr, 'Please enter your full name');
    else if (signupName.value.trim()) clearError(signupNameErr);
  });
  signupUsername.addEventListener('blur', () => {
    if (signupUsername.value && signupUsername.value.trim().length < 3) showError(signupUsernameErr, 'Username must be at least 3 characters');
    else if (signupUsername.value.trim()) clearError(signupUsernameErr);
  });
  signupEmail.addEventListener('blur', () => {
    if (signupEmail.value && !isValidEmail(signupEmail.value)) showError(signupEmailErr, 'Enter a valid email address');
    else clearError(signupEmailErr);
  });
  signupPw.addEventListener('blur', () => {
    if (signupPw.value && signupPw.value.length < 8) showError(signupPwErr, 'Password must be at least 8 characters');
    else clearError(signupPwErr);
  });
  signupConfirm.addEventListener('blur', () => {
    if (signupConfirm.value && signupConfirm.value !== signupPw.value) showError(signupConfirmErr, 'Passwords do not match');
    else if (signupConfirm.value) clearError(signupConfirmErr);
  });

  document.getElementById('form-signup').addEventListener('submit', async (e) => {
    e.preventDefault();
    let valid = true;
    if (!signupName.value.trim() || signupName.value.trim().split(/\s+/).length < 2) { showError(signupNameErr, 'Please enter your full name'); valid = false; }
    if (!signupUsername.value.trim() || signupUsername.value.trim().length < 3) { showError(signupUsernameErr, 'Username must be at least 3 characters'); valid = false; }
    if (!isValidEmail(signupEmail.value)) { showError(signupEmailErr, 'Enter a valid email address'); valid = false; }
    if (signupPw.value.length < 8) { showError(signupPwErr, 'Password must be at least 8 characters'); valid = false; }
    if (signupConfirm.value !== signupPw.value) { showError(signupConfirmErr, 'Passwords do not match'); valid = false; }
    if (!valid) return;

    btnSignup.classList.add('loading');
    btnSignup.disabled = true;
    clearError(signupEmailErr);
    clearError(signupUsernameErr);

    try {
      const payload = {
        name: signupName.value.trim(),
        username: signupUsername.value.trim(),
        email: signupEmail.value.trim(),
        password: signupPw.value,
        authProvider: 'local',
        studentClass: '', skills: '', preferredDomain: ''
      };
      const response = await fetch(`${API_BASE}/signup`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify(payload)
      });
      const result = await parseApiResponse(response);
      const msg = (typeof result === 'string' && result.trim())
        ? result
        : (response.ok ? 'Account created!' : 'Signup failed. Please try again.');

      if (!response.ok) {
        if (msg.toLowerCase().includes('email')) showError(signupEmailErr, msg);
        else if (msg.toLowerCase().includes('username')) showError(signupUsernameErr, msg);
        else showError(signupPwErr, msg);
        return;
      }

      alert(msg);
      loginEmail.value = payload.username;  // Can login with username
      loginPassword.value = '';
      switchPanel('login');
      updateLoginBtn();
      loginPassword.focus();
    } catch {
      showError(signupEmailErr, 'Could not connect to server. Try again.');
    } finally {
      btnSignup.classList.remove('loading');
      updateSignupBtn();
    }
  });

  // â”€â”€ FORGOT PASSWORD â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€
  async function handleForgotPassword() {
    const email = loginEmail.value.trim();
    if (!email || !isValidEmail(email)) {
      showError(loginEmailErr, 'Enter your email above first');
      loginEmail.focus();
      return;
    }
    clearError(loginEmailErr);
    try {
      const response = await fetch(`${API_BASE}/profile?email=${encodeURIComponent(email)}`);
      if (response.status === 404) { showError(loginEmailErr, 'No account found for this email'); return; }
      if (!response.ok) { alert('Password reset unavailable. Please contact support.'); return; }
      alert('Account found. Password reset is not yet implemented in the backend.');
    } catch {
      alert('Could not connect to server.');
    }
  }

