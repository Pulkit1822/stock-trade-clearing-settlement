import React, { useEffect, useState } from 'react';
import { Briefcase, Plus, AlertCircle, RefreshCcw } from 'lucide-react';
import { fetchCorporateActions, submitCorporateAction, fetchInstruments } from '../api/client';
import type { CorporateAction, Instrument } from '../api/client';
import { formatDistanceToNow } from 'date-fns';

const CorporateActions: React.FC = () => {
  const [actions, setActions] = useState<CorporateAction[]>([]);
  const [instruments, setInstruments] = useState<Instrument[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

  // Form State
  const [isSubmitting, setIsSubmitting] = useState(false);
  const [selectedInstrument, setSelectedInstrument] = useState('');
  const [actionType, setActionType] = useState<'STOCK_SPLIT' | 'DIVIDEND'>('STOCK_SPLIT');
  const [actionValue, setActionValue] = useState<number | ''>('');

  const loadData = () => {
    setLoading(true);
    Promise.all([fetchCorporateActions(), fetchInstruments()])
      .then(([actionsData, instrumentsData]) => {
        setActions(actionsData);
        setInstruments(instrumentsData);
        setError(null);
      })
      .catch((err) => {
        console.error(err);
        setError('Failed to load corporate actions. Please try again.');
      })
      .finally(() => setLoading(false));
  };

  useEffect(() => {
    loadData();
  }, []);

  const handleSubmit = (e: React.FormEvent) => {
    e.preventDefault();
    if (!selectedInstrument || !actionValue) return;

    setIsSubmitting(true);
    submitCorporateAction(selectedInstrument, actionType, Number(actionValue))
      .then(() => {
        setSelectedInstrument('');
        setActionValue('');
        loadData();
      })
      .catch((err) => {
        console.error(err);
        setError('Failed to submit corporate action.');
      })
      .finally(() => setIsSubmitting(false));
  };

  return (
    <div>
      <div className="page-header" style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
        <div>
          <h1>Corporate Actions</h1>
          <p>Manage and process stock splits and dividends</p>
        </div>
        <button className="btn btn-secondary" onClick={loadData} disabled={loading}>
          <RefreshCcw size={16} style={{ marginRight: 8 }} />
          Refresh
        </button>
      </div>

      {error && (
        <div className="error-alert" style={{ marginBottom: 20 }}>
          <AlertCircle size={20} />
          <span>{error}</span>
        </div>
      )}

      <div className="content-grid" style={{ gridTemplateColumns: '1fr 350px' }}>
        {/* Actions Table */}
        <div className="card full-width">
          <div className="section-header">
            <span className="section-title">Action History</span>
          </div>
          {loading ? (
            <div className="empty-state">Loading actions...</div>
          ) : actions.length === 0 ? (
            <div className="empty-state">
              <Briefcase size={36} />
              <p>No corporate actions found.</p>
            </div>
          ) : (
            <div className="table-wrapper">
              <table>
                <thead>
                  <tr>
                    <th>ID</th>
                    <th>Instrument</th>
                    <th>Type</th>
                    <th>Value</th>
                    <th>Status</th>
                    <th>Date</th>
                  </tr>
                </thead>
                <tbody>
                  {actions.map((action) => (
                    <tr key={action.id}>
                      <td className="mono" style={{ fontSize: '0.8rem' }}>{action.corporateActionId.substring(0, 8)}</td>
                      <td style={{ fontWeight: 600 }}>{action.instrumentId}</td>
                      <td>
                        <span style={{ 
                          padding: '2px 8px', 
                          borderRadius: 4, 
                          fontSize: '0.8rem',
                          background: action.actionType === 'DIVIDEND' ? 'rgba(16, 185, 129, 0.1)' : 'rgba(59, 130, 246, 0.1)',
                          color: action.actionType === 'DIVIDEND' ? '#10b981' : '#3b82f6',
                          fontWeight: 500
                        }}>
                          {action.actionType.replace('_', ' ')}
                        </span>
                      </td>
                      <td className="mono">
                        {action.actionType === 'DIVIDEND' ? '$' : ''}{Number(action.actionValue).toFixed(2)}
                        {action.actionType === 'STOCK_SPLIT' ? ' Ratio' : ''}
                      </td>
                      <td>
                        <span className={`status-badge ${action.status.toLowerCase()}`}>
                          {action.status}
                        </span>
                      </td>
                      <td style={{ color: 'var(--text-muted)', fontSize: '0.8rem' }}>
                        {action.publishedAt ? formatDistanceToNow(new Date(action.publishedAt), { addSuffix: true }) : '—'}
                      </td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          )}
        </div>

        {/* Action Submission Form */}
        <div className="card">
          <div className="section-header">
            <span className="section-title">Announce Action</span>
          </div>
          <form onSubmit={handleSubmit} style={{ display: 'flex', flexDirection: 'column', gap: 16 }}>
            <div className="form-group">
              <label>Instrument</label>
              <select 
                value={selectedInstrument} 
                onChange={(e) => setSelectedInstrument(e.target.value)}
                required
                disabled={loading || isSubmitting}
              >
                <option value="">Select Instrument</option>
                {instruments.map(inst => (
                  <option key={inst.id} value={inst.instrumentId}>
                    {inst.instrumentId} - {inst.name}
                  </option>
                ))}
              </select>
            </div>
            <div className="form-group">
              <label>Action Type</label>
              <select 
                value={actionType} 
                onChange={(e) => setActionType(e.target.value as any)}
                required
                disabled={loading || isSubmitting}
              >
                <option value="STOCK_SPLIT">Stock Split</option>
                <option value="DIVIDEND">Dividend Payout</option>
              </select>
            </div>
            <div className="form-group">
              <label>
                {actionType === 'DIVIDEND' ? 'Dividend Amount ($)' : 'Split Ratio (e.g., 2.0 for 2-for-1)'}
              </label>
              <input
                type="number"
                step="0.01"
                min="0.01"
                value={actionValue}
                onChange={(e) => setActionValue(e.target.value ? Number(e.target.value) : '')}
                required
                disabled={loading || isSubmitting}
                placeholder={actionType === 'DIVIDEND' ? '0.50' : '2.0'}
              />
            </div>
            <button type="submit" className="btn btn-primary" disabled={loading || isSubmitting || !selectedInstrument || !actionValue} style={{ marginTop: 8 }}>
              {isSubmitting ? 'Processing...' : (
                <>
                  <Plus size={16} style={{ marginRight: 8 }} />
                  Publish Action
                </>
              )}
            </button>
            <div style={{ fontSize: '0.8rem', color: 'var(--text-muted)', display: 'flex', gap: 6, marginTop: 8, padding: 12, background: 'var(--bg-tertiary)', borderRadius: 6 }}>
              <AlertCircle size={14} style={{ flexShrink: 0, marginTop: 2 }} />
              <span>Published actions are processed immediately across all eligible positions.</span>
            </div>
          </form>
        </div>
      </div>
    </div>
  );
};

export default CorporateActions;
