import React, { useEffect, useState } from 'react';
import { RefreshCw, Layers } from 'lucide-react';
import { fetchPositions } from '../api/client';
import type { Position } from '../api/client';
import { format } from 'date-fns';

const Positions: React.FC = () => {
  const [positions, setPositions] = useState<Position[]>([]);
  const [loading, setLoading] = useState(true);
  const [filterAccount, setFilterAccount] = useState('');
  const [searchInstrument, setSearchInstrument] = useState('');

  const load = () => {
    setLoading(true);
    fetchPositions()
      .then(setPositions)
      .catch(() => setPositions([]))
      .finally(() => setLoading(false));
  };

  useEffect(() => { load(); }, []);

  // Unique accounts for dropdown filter
  const accounts = [...new Set(positions.map((p) => p.accountId))].sort();

  const filtered = positions
    .filter((p) => filterAccount === '' || p.accountId === filterAccount)
    .filter((p) =>
      searchInstrument === '' ||
      p.instrumentId.toLowerCase().includes(searchInstrument.toLowerCase())
    )
    .sort((a, b) => a.instrumentId.localeCompare(b.instrumentId));

  // Summary stats per account
  const accountSummary: Record<string, { instruments: number; totalQty: number }> = {};
  positions.forEach((p) => {
    if (!accountSummary[p.accountId]) {
      accountSummary[p.accountId] = { instruments: 0, totalQty: 0 };
    }
    accountSummary[p.accountId].instruments++;
    accountSummary[p.accountId].totalQty += p.quantity;
  });

  return (
    <div>
      <div className="page-header">
        <h1>Open Positions</h1>
        <p>Current holdings across all accounts and instruments</p>
      </div>

      {/* Account summary cards */}
      {!loading && accounts.length > 0 && (
        <div className="stats-grid" style={{ marginBottom: 24 }}>
          {accounts.slice(0, 4).map((accId) => {
            const summary = accountSummary[accId];
            return (
              <div
                key={accId}
                className="card"
                style={{
                  cursor: 'pointer',
                  borderColor: filterAccount === accId ? 'var(--brand-primary)' : 'var(--border)',
                  boxShadow: filterAccount === accId ? 'var(--shadow-glow)' : undefined,
                }}
                onClick={() => setFilterAccount(filterAccount === accId ? '' : accId)}
              >
                <div className="card__title">{accId}</div>
                <div style={{ fontSize: '1.4rem', fontWeight: 700, marginTop: 4 }}>
                  {summary.totalQty.toLocaleString()}
                  <span style={{ fontSize: '0.75rem', color: 'var(--text-muted)', fontWeight: 400, marginLeft: 6 }}>
                    shares
                  </span>
                </div>
                <div style={{ fontSize: '0.8rem', color: 'var(--text-muted)', marginTop: 4 }}>
                  {summary.instruments} instrument{summary.instruments !== 1 ? 's' : ''}
                </div>
              </div>
            );
          })}
        </div>
      )}

      {/* Filters */}
      <div className="card" style={{ marginBottom: 20 }}>
        <div style={{ display: 'flex', gap: 12, flexWrap: 'wrap', alignItems: 'center' }}>
          <input
            type="text"
            className="form-input"
            placeholder="Filter by instrument…"
            value={searchInstrument}
            onChange={(e) => setSearchInstrument(e.target.value)}
            style={{ maxWidth: 240 }}
          />
          <select
            className="form-select"
            value={filterAccount}
            onChange={(e) => setFilterAccount(e.target.value)}
            style={{ maxWidth: 240 }}
          >
            <option value="">All Accounts</option>
            {accounts.map((acc) => (
              <option key={acc} value={acc}>{acc}</option>
            ))}
          </select>
          <button className="btn btn-ghost" onClick={load} style={{ marginLeft: 'auto' }}>
            <RefreshCw size={14} />
            Refresh
          </button>
        </div>
      </div>

      {/* Positions table */}
      <div className="card">
        {loading ? (
          <div style={{ display: 'flex', flexDirection: 'column', gap: 8 }}>
            {[...Array(8)].map((_, i) => (
              <div key={i} className="skeleton" style={{ height: 44, borderRadius: 6 }} />
            ))}
          </div>
        ) : filtered.length === 0 ? (
          <div className="empty-state">
            <Layers size={40} />
            <p>No positions found. Submit trades to build your portfolio.</p>
          </div>
        ) : (
          <div className="table-wrapper">
            <table>
              <thead>
                <tr>
                  <th>Position ID</th>
                  <th>Account</th>
                  <th>Instrument</th>
                  <th style={{ textAlign: 'right' }}>Quantity</th>
                  <th>Last Updated</th>
                </tr>
              </thead>
              <tbody>
                {filtered.map((position) => (
                  <tr key={position.id}>
                    <td><span className="mono">{position.id.slice(0, 8)}…</span></td>
                    <td className="mono">{position.accountId}</td>
                    <td style={{ fontWeight: 600, color: 'var(--text-primary)' }}>
                      {position.instrumentId}
                    </td>
                    <td style={{ textAlign: 'right', fontFamily: 'var(--font-mono)', fontWeight: 600 }}>
                      <span style={{
                        color: position.quantity > 0 ? 'var(--success)' : 'var(--danger)',
                      }}>
                        {position.quantity > 0 ? '+' : ''}{position.quantity.toLocaleString()}
                      </span>
                    </td>
                    <td style={{ color: 'var(--text-muted)', fontSize: '0.8rem' }}
                        title={format(new Date(position.lastUpdated), 'PPpp')}>
                      {format(new Date(position.lastUpdated), 'dd MMM yyyy HH:mm')}
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        )}
        {!loading && filtered.length > 0 && (
          <div style={{ marginTop: 14, fontSize: '0.8rem', color: 'var(--text-muted)' }}>
            {filtered.length} position{filtered.length !== 1 ? 's' : ''}
            {filterAccount ? ` for ${filterAccount}` : ''}
          </div>
        )}
      </div>
    </div>
  );
};

export default Positions;
