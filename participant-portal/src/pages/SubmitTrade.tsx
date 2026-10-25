import React, { useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { Send, AlertCircle } from 'lucide-react';
import { submitTrade } from '../api/client';
import type { TradeRequest } from '../api/client';
import { useToast } from '../context/ToastContext';

interface FormData {
  instrumentId: string;
  buyerAccountId: string;
  sellerAccountId: string;
  quantity: string;
  price: string;
}

interface FormErrors {
  instrumentId?: string;
  buyerAccountId?: string;
  sellerAccountId?: string;
  quantity?: string;
  price?: string;
}

const DEMO_INSTRUMENTS = ['AAPL', 'MSFT', 'GOOGL', 'AMZN', 'TSLA', 'NVDA', 'META'];

const SubmitTrade: React.FC = () => {
  const navigate = useNavigate();
  const { toast } = useToast();

  const [formData, setFormData] = useState<FormData>({
    instrumentId: '',
    buyerAccountId: '',
    sellerAccountId: '',
    quantity: '',
    price: '',
  });

  const [errors, setErrors] = useState<FormErrors>({});
  const [submitting, setSubmitting] = useState(false);
  const [submittedTrade, setSubmittedTrade] = useState<{ id: string } | null>(null);

  const validate = (): boolean => {
    const newErrors: FormErrors = {};

    if (!formData.instrumentId.trim()) newErrors.instrumentId = 'Instrument is required';
    if (!formData.buyerAccountId.trim()) newErrors.buyerAccountId = 'Buyer account is required';
    if (!formData.sellerAccountId.trim()) newErrors.sellerAccountId = 'Seller account is required';
    if (formData.buyerAccountId.trim() === formData.sellerAccountId.trim()) {
      newErrors.sellerAccountId = 'Buyer and Seller accounts must be different';
    }

    const qty = Number(formData.quantity);
    if (!formData.quantity || isNaN(qty) || qty <= 0 || !Number.isInteger(qty)) {
      newErrors.quantity = 'Quantity must be a positive whole number';
    }

    const price = Number(formData.price);
    if (!formData.price || isNaN(price) || price <= 0) {
      newErrors.price = 'Price must be a positive number';
    }

    setErrors(newErrors);
    return Object.keys(newErrors).length === 0;
  };

  const handleChange = (field: keyof FormData) => (
    e: React.ChangeEvent<HTMLInputElement | HTMLSelectElement>
  ) => {
    setFormData((prev) => ({ ...prev, [field]: e.target.value }));
    if (errors[field]) setErrors((prev) => ({ ...prev, [field]: undefined }));
  };

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!validate()) return;

    setSubmitting(true);
    try {
      const request: TradeRequest = {
        instrumentId: formData.instrumentId.toUpperCase(),
        buyerAccountId: formData.buyerAccountId.trim(),
        sellerAccountId: formData.sellerAccountId.trim(),
        quantity: Number(formData.quantity),
        price: Number(formData.price),
      };

      const trade = await submitTrade(request);
      setSubmittedTrade({ id: trade.id });
      toast('success', 'Trade Submitted', `Trade ${trade.id.slice(0, 8)}… is now in clearing.`);
    } catch (err: unknown) {
      const message = err instanceof Error ? err.message : 'Unknown error';
      toast('error', 'Submission Failed', message);
    } finally {
      setSubmitting(false);
    }
  };

  const totalValue =
    formData.quantity && formData.price
      ? (Number(formData.quantity) * Number(formData.price)).toLocaleString('en-US', {
          style: 'currency',
          currency: 'USD',
          minimumFractionDigits: 2,
        })
      : '—';

  if (submittedTrade) {
    return (
      <div>
        <div className="page-header">
          <h1>Trade Submitted</h1>
        </div>
        <div className="card" style={{ maxWidth: 540, textAlign: 'center', padding: '48px 32px' }}>
          <div style={{
            width: 64, height: 64,
            borderRadius: '50%',
            background: 'rgba(16,185,129,0.12)',
            display: 'flex', alignItems: 'center', justifyContent: 'center',
            margin: '0 auto 20px',
          }}>
            <Send size={28} color="var(--success)" />
          </div>
          <h2 style={{ fontSize: '1.2rem', marginBottom: 8 }}>Trade Accepted</h2>
          <p style={{ color: 'var(--text-secondary)', marginBottom: 24, lineHeight: 1.6 }}>
            Your trade has been submitted to the clearing pipeline.<br />
            It will be settled once positions are validated.
          </p>
          <div style={{ background: 'var(--bg-elevated)', borderRadius: 'var(--radius-sm)', padding: '12px 16px', marginBottom: 24 }}>
            <span className="mono">{submittedTrade.id}</span>
          </div>
          <div style={{ display: 'flex', gap: 12, justifyContent: 'center' }}>
            <button className="btn btn-primary" onClick={() => navigate('/trades')}>
              View All Trades
            </button>
            <button className="btn btn-ghost" onClick={() => {
              setSubmittedTrade(null);
              setFormData({ instrumentId: '', buyerAccountId: '', sellerAccountId: '', quantity: '', price: '' });
            }}>
              Submit Another
            </button>
          </div>
        </div>
      </div>
    );
  }

  return (
    <div>
      <div className="page-header">
        <h1>Submit Trade</h1>
        <p>Submit a matched trade execution to the clearing platform</p>
      </div>

      <div className="content-grid">
        <form onSubmit={handleSubmit} noValidate style={{ display: 'contents' }}>
          {/* Left: instrument + accounts */}
          <div className="card" style={{ display: 'flex', flexDirection: 'column', gap: 20 }}>
            <div className="section-title">Trade Details</div>

            <div className="form-group">
              <label className="form-label" htmlFor="instrument-select">Instrument</label>
              <select
                id="instrument-select"
                className={`form-select${errors.instrumentId ? ' error' : ''}`}
                value={formData.instrumentId}
                onChange={handleChange('instrumentId')}
              >
                <option value="">— Select instrument —</option>
                {DEMO_INSTRUMENTS.map((inst) => (
                  <option key={inst} value={inst}>{inst}</option>
                ))}
              </select>
              {errors.instrumentId && <span className="form-error">{errors.instrumentId}</span>}
            </div>

            <div className="form-grid">
              <div className="form-group">
                <label className="form-label" htmlFor="buyer-input">Buyer Account ID</label>
                <input
                  id="buyer-input"
                  type="text"
                  className={`form-input${errors.buyerAccountId ? ' error' : ''}`}
                  placeholder="e.g. ACC-001"
                  value={formData.buyerAccountId}
                  onChange={handleChange('buyerAccountId')}
                />
                {errors.buyerAccountId && <span className="form-error">{errors.buyerAccountId}</span>}
              </div>
              <div className="form-group">
                <label className="form-label" htmlFor="seller-input">Seller Account ID</label>
                <input
                  id="seller-input"
                  type="text"
                  className={`form-input${errors.sellerAccountId ? ' error' : ''}`}
                  placeholder="e.g. ACC-002"
                  value={formData.sellerAccountId}
                  onChange={handleChange('sellerAccountId')}
                />
                {errors.sellerAccountId && <span className="form-error">{errors.sellerAccountId}</span>}
              </div>
            </div>

            <div className="form-grid">
              <div className="form-group">
                <label className="form-label" htmlFor="qty-input">Quantity (shares)</label>
                <input
                  id="qty-input"
                  type="number"
                  min="1"
                  step="1"
                  className={`form-input${errors.quantity ? ' error' : ''}`}
                  placeholder="100"
                  value={formData.quantity}
                  onChange={handleChange('quantity')}
                />
                {errors.quantity && <span className="form-error">{errors.quantity}</span>}
              </div>
              <div className="form-group">
                <label className="form-label" htmlFor="price-input">Price (USD)</label>
                <input
                  id="price-input"
                  type="number"
                  min="0.01"
                  step="0.01"
                  className={`form-input${errors.price ? ' error' : ''}`}
                  placeholder="150.00"
                  value={formData.price}
                  onChange={handleChange('price')}
                />
                {errors.price && <span className="form-error">{errors.price}</span>}
              </div>
            </div>
          </div>

          {/* Right: summary + submit */}
          <div style={{ display: 'flex', flexDirection: 'column', gap: 16 }}>
            <div className="card">
              <div className="section-title" style={{ marginBottom: 20 }}>Order Summary</div>
              <div style={{ display: 'flex', flexDirection: 'column', gap: 14 }}>
                {[
                  { label: 'Instrument', value: formData.instrumentId || '—' },
                  { label: 'Buyer', value: formData.buyerAccountId || '—' },
                  { label: 'Seller', value: formData.sellerAccountId || '—' },
                  { label: 'Quantity', value: formData.quantity ? Number(formData.quantity).toLocaleString() : '—' },
                  { label: 'Price', value: formData.price ? `$${Number(formData.price).toFixed(2)}` : '—' },
                ].map(({ label, value }) => (
                  <div key={label} style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
                    <span style={{ color: 'var(--text-muted)', fontSize: '0.85rem' }}>{label}</span>
                    <span style={{ fontWeight: 600, fontSize: '0.9rem' }}>{value}</span>
                  </div>
                ))}
                <div className="divider" />
                <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
                  <span style={{ color: 'var(--text-secondary)', fontSize: '0.9rem', fontWeight: 600 }}>Total Value</span>
                  <span style={{ fontSize: '1.2rem', fontWeight: 700, color: 'var(--brand-primary)' }}>{totalValue}</span>
                </div>
              </div>
            </div>

            <div className="card" style={{ background: 'rgba(245,158,11,0.07)', borderColor: 'rgba(245,158,11,0.25)' }}>
              <div style={{ display: 'flex', gap: 10, alignItems: 'flex-start' }}>
                <AlertCircle size={16} color="var(--warning)" style={{ flexShrink: 0, marginTop: 1 }} />
                <p style={{ color: 'var(--warning)', fontSize: '0.8rem', lineHeight: 1.6 }}>
                  Trade submissions are final. Ensure buyer and seller account IDs are correct
                  before submitting. The trade will enter the clearing pipeline immediately.
                </p>
              </div>
            </div>

            <button
              type="submit"
              id="submit-trade-btn"
              className="btn btn-primary"
              disabled={submitting}
              style={{ width: '100%', justifyContent: 'center', padding: '14px' }}
            >
              <Send size={16} />
              {submitting ? 'Submitting…' : 'Submit Trade to Clearing'}
            </button>
          </div>
        </form>
      </div>
    </div>
  );
};

export default SubmitTrade;
