import React, { createContext, useContext, useState, useCallback, useEffect } from 'react';
import { useNavigate } from 'react-router-dom';
import axios from 'axios';

// ── Types ────────────────────────────────────────────────────────────────────

interface AuthContextType {
  isAuthenticated: boolean;
  token: string | null;
  username: string | null;
  loginError: string | null;
  isLoggingIn: boolean;
  login: (username: string, password: string) => Promise<void>;
  logout: () => void;
}

// ── Context ───────────────────────────────────────────────────────────────────

const AuthContext = createContext<AuthContextType | undefined>(undefined);

const GATEWAY_BASE_URL = import.meta.env.VITE_API_BASE_URL || 'http://localhost:8080';
const TOKEN_STORAGE_KEY = 'access_token';
const USERNAME_STORAGE_KEY = 'username';

// ── Provider ──────────────────────────────────────────────────────────────────

export const AuthProvider: React.FC<{ children: React.ReactNode }> = ({ children }) => {
  const [token, setToken] = useState<string | null>(
    localStorage.getItem(TOKEN_STORAGE_KEY)
  );
  const [username, setUsername] = useState<string | null>(
    localStorage.getItem(USERNAME_STORAGE_KEY)
  );
  const [loginError, setLoginError] = useState<string | null>(null);
  const [isLoggingIn, setIsLoggingIn] = useState(false);

  const navigate = useNavigate();

  // Sync token to localStorage whenever it changes
  useEffect(() => {
    if (token) {
      localStorage.setItem(TOKEN_STORAGE_KEY, token);
    } else {
      localStorage.removeItem(TOKEN_STORAGE_KEY);
      localStorage.removeItem(USERNAME_STORAGE_KEY);
    }
  }, [token]);

  /**
   * POST /api/auth/login → receives back { token: "..." }
   * Stores the JWT in localStorage and redirects to the dashboard.
   */
  const login = useCallback(async (usernameInput: string, password: string) => {
    setIsLoggingIn(true);
    setLoginError(null);

    try {
      const response = await axios.post<{ token: string }>(
        `${GATEWAY_BASE_URL}/api/auth/login`,
        { username: usernameInput, password },
        { headers: { 'Content-Type': 'application/json' } }
      );

      const receivedToken = response.data.token;
      localStorage.setItem(USERNAME_STORAGE_KEY, usernameInput);
      setUsername(usernameInput);
      setToken(receivedToken);
      navigate('/', { replace: true });
    } catch (error: unknown) {
      if (axios.isAxiosError(error) && error.response?.status === 401) {
        setLoginError('Invalid username or password.');
      } else {
        setLoginError('Login failed. Please check that the service is running and try again.');
      }
    } finally {
      setIsLoggingIn(false);
    }
  }, [navigate]);

  /**
   * Clears the local session. No server-side logout call needed for stateless JWT.
   */
  const logout = useCallback(() => {
    setToken(null);
    setUsername(null);
    navigate('/login', { replace: true });
  }, [navigate]);

  return (
    <AuthContext.Provider
      value={{ isAuthenticated: !!token, token, username, loginError, isLoggingIn, login, logout }}
    >
      {children}
    </AuthContext.Provider>
  );
};

// ── Hook ──────────────────────────────────────────────────────────────────────

export const useAuth = () => {
  const context = useContext(AuthContext);
  if (context === undefined) {
    throw new Error('useAuth must be used within an AuthProvider');
  }
  return context;
};
