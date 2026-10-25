import React, { useState } from 'react';
import { ShieldCheck, Eye, EyeOff, AlertCircle, Loader2 } from 'lucide-react';
import { useAuth } from '../context/AuthContext';

// ── Login Page ────────────────────────────────────────────────────────────────
//
// Replaces the single "Login with SSO" button with a proper username/password
// form. Credentials are sent to POST /api/auth/login on the API Gateway,
// which returns a signed HS256 JWT stored in localStorage.
//
// Supported test accounts (configured in AuthController):
//   admin / admin123
//   trader / trader123
//   ops / ops123

const Login: React.FC = () => {
  const { login, loginError, isLoggingIn } = useAuth();

  const [username, setUsername] = useState('');
  const [password, setPassword] = useState('');
  const [showPassword, setShowPassword] = useState(false);

  const handleSubmit = async (event: React.FormEvent<HTMLFormElement>) => {
    event.preventDefault();
    await login(username, password);
  };

  const inputStyle: React.CSSProperties = {
    width: '100%',
    padding: '10px 14px',
    background: 'var(--bg-main)',
    border: '1px solid var(--border)',
    borderRadius: 'var(--radius-sm)',
    color: 'var(--text-main)',
    fontSize: '0.95rem',
    outline: 'none',
    boxSizing: 'border-box',
    transition: 'border-color 0.2s',
  };

  const labelStyle: React.CSSProperties = {
    display: 'block',
    marginBottom: '6px',
    fontSize: '0.85rem',
    fontWeight: 500,
    color: 'var(--text-muted)',
    textAlign: 'left',
  };

  return (
    <div
      style={{
        minHeight: '100vh',
        display: 'flex',
        alignItems: 'center',
        justifyContent: 'center',
        padding: '24px',
        background: 'var(--bg-main)',
      }}
    >
      <div
        style={{
          background: 'var(--bg-elevated)',
          border: '1px solid var(--border)',
          borderRadius: 'var(--radius-lg)',
          padding: '40px',
          width: '100%',
          maxWidth: '400px',
          display: 'flex',
          flexDirection: 'column',
          alignItems: 'center',
        }}
      >
        {/* Icon */}
        <div
          style={{
            background: 'rgba(99, 102, 241, 0.1)',
            padding: '16px',
            borderRadius: '50%',
            marginBottom: '24px',
          }}
        >
          <ShieldCheck size={48} color="var(--primary)" />
        </div>

        {/* Heading */}
        <h1
          style={{
            fontSize: '1.5rem',
            marginBottom: '4px',
            color: 'var(--text-main)',
            textAlign: 'center',
          }}
        >
          Participant Portal
        </h1>
        <p
          style={{
            color: 'var(--text-muted)',
            marginBottom: '32px',
            fontSize: '0.875rem',
            textAlign: 'center',
          }}
        >
          Post-Trade Clearing Platform
        </p>

        {/* Login Form */}
        <form
          id="login-form"
          onSubmit={handleSubmit}
          style={{ width: '100%' }}
          noValidate
        >
          {/* Username */}
          <div style={{ marginBottom: '16px' }}>
            <label htmlFor="username" style={labelStyle}>
              Username
            </label>
            <input
              id="username"
              type="text"
              autoComplete="username"
              required
              value={username}
              onChange={(e) => setUsername(e.target.value)}
              placeholder="e.g. admin"
              style={inputStyle}
              onFocus={(e) => (e.target.style.borderColor = 'var(--primary)')}
              onBlur={(e) => (e.target.style.borderColor = 'var(--border)')}
              disabled={isLoggingIn}
            />
          </div>

          {/* Password */}
          <div style={{ marginBottom: '24px' }}>
            <label htmlFor="password" style={labelStyle}>
              Password
            </label>
            <div style={{ position: 'relative' }}>
              <input
                id="password"
                type={showPassword ? 'text' : 'password'}
                autoComplete="current-password"
                required
                value={password}
                onChange={(e) => setPassword(e.target.value)}
                placeholder="••••••••"
                style={{ ...inputStyle, paddingRight: '42px' }}
                onFocus={(e) => (e.target.style.borderColor = 'var(--primary)')}
                onBlur={(e) => (e.target.style.borderColor = 'var(--border)')}
                disabled={isLoggingIn}
              />
              <button
                id="toggle-password-visibility"
                type="button"
                onClick={() => setShowPassword((prev) => !prev)}
                style={{
                  position: 'absolute',
                  right: '12px',
                  top: '50%',
                  transform: 'translateY(-50%)',
                  background: 'none',
                  border: 'none',
                  cursor: 'pointer',
                  color: 'var(--text-muted)',
                  padding: 0,
                  display: 'flex',
                  alignItems: 'center',
                }}
                aria-label={showPassword ? 'Hide password' : 'Show password'}
              >
                {showPassword ? <EyeOff size={16} /> : <Eye size={16} />}
              </button>
            </div>
          </div>

          {/* Inline Error */}
          {loginError && (
            <div
              role="alert"
              style={{
                display: 'flex',
                alignItems: 'center',
                gap: '8px',
                background: 'rgba(239, 68, 68, 0.1)',
                border: '1px solid rgba(239, 68, 68, 0.3)',
                borderRadius: 'var(--radius-sm)',
                padding: '10px 14px',
                marginBottom: '16px',
                color: '#f87171',
                fontSize: '0.875rem',
              }}
            >
              <AlertCircle size={16} style={{ flexShrink: 0 }} />
              {loginError}
            </div>
          )}

          {/* Submit */}
          <button
            id="submit-login"
            type="submit"
            disabled={isLoggingIn || !username || !password}
            style={{
              background: isLoggingIn ? 'var(--primary-hover)' : 'var(--primary)',
              color: 'white',
              border: 'none',
              padding: '12px 24px',
              borderRadius: 'var(--radius-sm)',
              fontSize: '1rem',
              fontWeight: 500,
              cursor: isLoggingIn || !username || !password ? 'not-allowed' : 'pointer',
              width: '100%',
              opacity: isLoggingIn || !username || !password ? 0.7 : 1,
              transition: 'background 0.2s, opacity 0.2s',
              display: 'flex',
              alignItems: 'center',
              justifyContent: 'center',
              gap: '8px',
            }}
            onMouseOver={(e) => {
              if (!isLoggingIn) e.currentTarget.style.background = 'var(--primary-hover)';
            }}
            onMouseOut={(e) => {
              if (!isLoggingIn) e.currentTarget.style.background = 'var(--primary)';
            }}
          >
            {isLoggingIn && (
              <Loader2
                size={16}
                style={{ animation: 'spin 1s linear infinite' }}
              />
            )}
            {isLoggingIn ? 'Signing in…' : 'Sign In'}
          </button>
        </form>

        {/* Footer hint */}
        <p
          style={{
            marginTop: '24px',
            fontSize: '0.75rem',
            color: 'var(--text-muted)',
            textAlign: 'center',
          }}
        >
          Secured with HS256 JWT · PostTrade Clearing Platform
        </p>
      </div>

      {/* Spinner keyframes (inlined to avoid needing a CSS file change) */}
      <style>{`
        @keyframes spin { from { transform: rotate(0deg); } to { transform: rotate(360deg); } }
      `}</style>
    </div>
  );
};

export default Login;
