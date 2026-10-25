import axios from 'axios';

// All requests route through the API Gateway on :8080.
// In production, override with VITE_API_BASE_URL env variable.
const API_BASE_URL = import.meta.env.VITE_API_BASE_URL || 'http://localhost:8080';

const api = axios.create({
  baseURL: API_BASE_URL,
  timeout: 15000,
  headers: { 'Content-Type': 'application/json' },
});

// Attach JWT token from localStorage on every request.
api.interceptors.request.use((config) => {
  const token = localStorage.getItem('access_token');
  if (token) config.headers.Authorization = `Bearer ${token}`;
  return config;
});

// Handle 401 Unauthorized responses globally
api.interceptors.response.use(
  (response) => response,
  (error) => {
    if (error.response && error.response.status === 401) {
      localStorage.removeItem('access_token');
      window.location.href = '/login';
    }
    
    // Extract exact backend message for UI display
    if (error.response?.data?.message) {
      error.message = error.response.data.message;
    } else if (error.response?.data?.error) {
      error.message = error.response.data.error;
    }
    
    return Promise.reject(error);
  }
);

// ── Trade API ────────────────────────────────────────────────────────────────

export interface TradeRequest {
  instrumentId: string;
  buyerAccountId: string;
  sellerAccountId: string;
  quantity: number;
  price: number;
}

export interface Trade {
  id: string;
  instrumentId: string;
  buyerAccountId: string;
  sellerAccountId: string;
  quantity: number;
  price: number;
  status: 'SUBMITTED' | 'SETTLED' | 'FAILED';
  createdAt: string;
  updatedAt: string;
}

export const submitTrade = (request: TradeRequest): Promise<Trade> =>
  api.post<Trade>('/api/v1/trades', request).then((r) => r.data);

export const fetchTrades = (): Promise<Trade[]> =>
  api.get<Trade[]>('/api/v1/trades').then((r) => r.data);

// ── Position API ─────────────────────────────────────────────────────────────

export interface Position {
  id: string;
  accountId: string;
  instrumentId: string;
  quantity: number;
  lastUpdated: string;
}

export const fetchPositions = (accountId?: string): Promise<Position[]> => {
  const url = accountId
    ? `/api/v1/positions?accountId=${accountId}`
    : '/api/v1/positions';
  return api.get<Position[]>(url).then((r) => r.data);
};

// ── Notification API ─────────────────────────────────────────────────────────

export interface Notification {
  id: string;
  accountId: string;
  tradeId: string;
  message: string;
  type: 'SETTLEMENT_COMPLETE' | 'TRADE_SUBMITTED' | 'POSITION_UPDATED';
  createdAt: string;
}

export const fetchNotifications = (): Promise<Notification[]> =>
  api.get<Notification[]>('/api/v1/notifications').then((r) => r.data);

// ── Instruments API ───────────────────────────────────────────────────────────

export interface Instrument {
  id: string;
  instrumentId: string;
  name: string;
  assetClass: string;
  currency: string;
  status: 'ACTIVE' | 'INACTIVE';
}

export const fetchInstruments = (): Promise<Instrument[]> =>
  api.get<Instrument[]>('/instruments').then((r) => r.data);

// ── Accounts API ──────────────────────────────────────────────────────────────

export interface Account {
  id: string;
  accountId: string;
  participantId: string;
  balance: number;
  currency: string;
  status: 'ACTIVE' | 'SUSPENDED' | 'CLOSED';
}

export const fetchAccounts = (): Promise<Account[]> =>
  api.get<Account[]>('/accounts').then((r) => r.data);

// ── Margin API ───────────────────────────────────────────────────────────────

export interface MarginCall {
  id: string;
  accountId: string;
  deficitAmount: number;
  status: 'OPEN' | 'RESOLVED';
  createdAt: string;
}

export interface MarginStatus {
  accountId: string;
  totalCollateral: number;
  requiredMargin: number;
  openMarginCalls: MarginCall[];
}

export const fetchMarginStatus = (accountId: string): Promise<MarginStatus> =>
  api.get<MarginStatus>(`/api/v1/margin/status/${accountId}`).then((r) => r.data);

export const depositCollateral = (accountId: string, amount: number): Promise<void> =>
  api.post(`/api/v1/margin/collateral/${accountId}/deposit?amount=${amount}`).then((r) => r.data);

// ── Netting API ──────────────────────────────────────────────────────────────

export const triggerNetting = (businessDate?: string): Promise<any> => {
  const url = businessDate 
    ? `/api/v1/netting/trigger?businessDate=${businessDate}`
    : '/api/v1/netting/trigger';
  return api.post(url).then((r) => r.data);
};

// ── Corporate Actions API ────────────────────────────────────────────────────

export interface CorporateAction {
  id: string;
  corporateActionId: string;
  instrumentId: string;
  actionType: 'STOCK_SPLIT' | 'DIVIDEND';
  actionValue: number;
  status: 'PENDING' | 'PUBLISHED' | 'CANCELLED';
  publishedAt: string | null;
}

export const fetchCorporateActions = (): Promise<CorporateAction[]> =>
  api.get<CorporateAction[]>('/api/v1/corporate-actions').then((r) => r.data);

export const submitCorporateAction = (
  instrumentId: string,
  actionType: 'STOCK_SPLIT' | 'DIVIDEND',
  actionValue: number
): Promise<CorporateAction> =>
  api.post<CorporateAction>(
    `/api/v1/corporate-actions?instrumentId=${instrumentId}&actionType=${actionType}&actionValue=${actionValue}`
  ).then((r) => r.data);

export default api;
