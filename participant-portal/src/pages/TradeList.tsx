import React, { useEffect, useState } from 'react';
import { RefreshCw, ArrowRightLeft } from 'lucide-react';
import { fetchTrades } from '../api/client';
import type { Trade } from '../api/client';
import { formatDistanceToNow, format } from 'date-fns';

const TradeList: React.FC = () => {
  const [trades, setTrades] = useState<Trade[]>([]);
  const [loading, setLoading] = useState(true);
  const [filterStatus, setFilterStatus] = useState<string>('ALL');
  const [searchTerm, setSearchTerm] = useState('');

  const load = () => {
    setLoading(true);
    fetchTrades()
      .then(setTrades)
      .catch(() => setTrades([]))
      .finally(() => setLoading(false));
  };

  useEffect(() => { load(); }, []);

  const filtered = trades
    .filter((t) => filterStatus === 'ALL' || t.status === filterStatus)
    .filter((t) =>
      searchTerm === '' ||
      t.id.toLowerCase().includes(searchTerm.toLowerCase()) ||
      t.instrumentId.toLowerCase().includes(searchTerm.toLowerCase()) ||
      t.buyerAccountId.toLowerCase().includes(searchTerm.toLowerCase()) ||
      t.sellerAccountId.toLowerCase().includes(searchTerm.toLowerCase())
    )
    .sort((a, b) => new Date(b.createdAt).getTime() - new Date(a.createdAt).getTime());

  const counts = {
    ALL: trades.length,
    SUBMITTED: trades.filter((t) => t.status === 'SUBMITTED').length,
    SETTLED: trades.filter((t) => t.status === 'SETTLED').length,
    FAILED: trades.filter((t) => t.status === 'FAILED').length,
  };

  return (
    <div>
      <div className="page-header">
        <h1>Trade History</h1>
        <p>All trade submissions and their clearing status</p>
      </div>

      {/* Filter bar */}
      <div className="card" style={{ marginBottom: 20 }}>
        <div style={{ display: 'flex', gap: 12, flexWrap: 'wrap', alignItems: 'center' }}>
          <input
            type="text"
            className="form-input"
            placeholder="Search by trade ID, instrument, or account…"
            value={searchTerm}
            onChange={(e) => setSearchTerm(e.target.value)}
            style={{ maxWidth: 360 }}
          />
          <div style={{ display: 'flex', gap: 8, marginLeft: 'auto' }}>
            {(['ALL', 'SUBMITTED', 'SETTLED', 'FAILED'] as const).map((status) => (
              <button
                key={status}
                id={`filter-${status.toLowerCase()}`}
                onClick={() => setFilterStatus(status)}
                className={`btn ${filterStatus === status ? 'btn-primary' : 'btn-ghost'}`}
                style={{ padding: '7px 14px', fontSize: '0.8rem' }}
              >
                {status} <span style={{ opacity: 0.7, marginLeft: 4 }}>{counts[status]}</span>
              </button>
            ))}
            <button className="btn btn-ghost" onClick={load} title="Refresh">
              <RefreshCw size={14} className={loading ? 'spin' : ''} />
            </button>
          </div>
        </div>
      </div>

      {/* Table */}
      <div className="card">
        {loading ? (
          <div style={{ display: 'flex', flexDirection: 'column', gap: 8 }}>
            {[...Array(8)].map((_, i) => (
              <div key={i} className="skeleton" style={{ height: 44, borderRadius: 6 }} />
            ))}
          </div>
        ) : filtered.length === 0 ? (
          <div className="empty-state">
            <ArrowRightLeft size={40} />
            <p>{searchTerm || filterStatus !== 'ALL' ? 'No trades match your filter.' : 'No trades yet.'}</p>
          </div>
        ) : (
          <div className="table-wrapper">
            <table>
              <thead>
                <tr>
                  <th>Trade ID</th>
                  <th>Instrument</th>
                  <th>Buyer</th>
                  <th>Seller</th>
                  <th>Qty</th>
                  <th>Price</th>
                  <th>Total</th>
                  <th>Status</th>
                  <th>Submitted</th>
                </tr>
              </thead>
              <tbody>
                {filtered.map((trade) => (
                  <tr key={trade.id}>
                    <td><span className="mono">{trade.id.slice(0, 8)}…</span></td>
                    <td style={{ fontWeight: 600, color: 'var(--text-primary)' }}>{trade.instrumentId}</td>
                    <td className="mono">{trade.buyerAccountId}</td>
                    <td className="mono">{trade.sellerAccountId}</td>
                    <td style={{ textAlign: 'right' }}>{trade.quantity.toLocaleString()}</td>
                    <td style={{ fontFamily: 'var(--font-mono)', fontSize: '0.82rem', textAlign: 'right' }}>
                      ${Number(trade.price).toFixed(2)}
                    </td>
                    <td style={{ fontFamily: 'var(--font-mono)', fontSize: '0.82rem', textAlign: 'right', color: 'var(--brand-accent)' }}>
                      ${(trade.quantity * trade.price).toLocaleString(undefined, { minimumFractionDigits: 2 })}
                    </td>
                    <td>
                      <span className={`status-badge ${trade.status.toLowerCase()}`}>
                        {trade.status}
                      </span>
                    </td>
                    <td title={format(new Date(trade.createdAt), 'PPpp')} style={{ color: 'var(--text-muted)', fontSize: '0.8rem' }}>
                      {formatDistanceToNow(new Date(trade.createdAt), { addSuffix: true })}
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        )}
        {!loading && filtered.length > 0 && (
          <div style={{ marginTop: 14, fontSize: '0.8rem', color: 'var(--text-muted)' }}>
            Showing {filtered.length} of {trades.length} trades
          </div>
        )}
      </div>
    </div>
  );
};

export default TradeList;
