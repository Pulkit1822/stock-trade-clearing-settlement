import React, { useEffect, useState } from 'react';
import { Bell, RefreshCw, CheckCircle, ArrowRightLeft, TrendingUp } from 'lucide-react';
import { fetchNotifications } from '../api/client';
import type { Notification } from '../api/client';
import { formatDistanceToNow, format } from 'date-fns';

const TYPE_META: Record<Notification['type'], { label: string; color: string; icon: React.ReactNode }> = {
  SETTLEMENT_COMPLETE: {
    label: 'Settlement Complete',
    color: 'var(--success)',
    icon: <CheckCircle size={16} />,
  },
  TRADE_SUBMITTED: {
    label: 'Trade Submitted',
    color: 'var(--info)',
    icon: <ArrowRightLeft size={16} />,
  },
  POSITION_UPDATED: {
    label: 'Position Updated',
    color: 'var(--warning)',
    icon: <TrendingUp size={16} />,
  },
};

const Notifications: React.FC = () => {
  const [notifications, setNotifications] = useState<Notification[]>([]);
  const [loading, setLoading] = useState(true);
  const [filterType, setFilterType] = useState<string>('ALL');

  const load = () => {
    setLoading(true);
    fetchNotifications()
      .then(setNotifications)
      .catch(() => setNotifications([]))
      .finally(() => setLoading(false));
  };

  useEffect(() => { load(); }, []);

  const sorted = [...notifications].sort(
    (a, b) => new Date(b.createdAt).getTime() - new Date(a.createdAt).getTime()
  );

  const filtered = sorted.filter(
    (n) => filterType === 'ALL' || n.type === filterType
  );

  const typeOptions: Array<{ value: string; label: string }> = [
    { value: 'ALL', label: 'All Events' },
    { value: 'SETTLEMENT_COMPLETE', label: 'Settlement' },
    { value: 'TRADE_SUBMITTED', label: 'Trade Submitted' },
    { value: 'POSITION_UPDATED', label: 'Position Updates' },
  ];

  return (
    <div>
      <div className="page-header">
        <h1>Notifications</h1>
        <p>Settlement confirmations and platform event feed</p>
      </div>

      {/* Filter */}
      <div className="card" style={{ marginBottom: 20 }}>
        <div style={{ display: 'flex', gap: 8, flexWrap: 'wrap', alignItems: 'center' }}>
          {typeOptions.map(({ value, label }) => (
            <button
              key={value}
              id={`notif-filter-${value.toLowerCase()}`}
              onClick={() => setFilterType(value)}
              className={`btn ${filterType === value ? 'btn-primary' : 'btn-ghost'}`}
              style={{ padding: '7px 14px', fontSize: '0.8rem' }}
            >
              {label}
            </button>
          ))}
          <button className="btn btn-ghost" onClick={load} style={{ marginLeft: 'auto' }}>
            <RefreshCw size={14} />
          </button>
        </div>
      </div>

      {/* Notification feed */}
      <div className="card">
        {loading ? (
          <div style={{ display: 'flex', flexDirection: 'column', gap: 12 }}>
            {[...Array(6)].map((_, i) => (
              <div key={i} className="skeleton" style={{ height: 72, borderRadius: 8 }} />
            ))}
          </div>
        ) : filtered.length === 0 ? (
          <div className="empty-state">
            <Bell size={40} />
            <p>No notifications yet. Notifications appear after trades are submitted.</p>
          </div>
        ) : (
          <div style={{ display: 'flex', flexDirection: 'column', gap: 2 }}>
            {filtered.map((notif, idx) => {
              const meta = TYPE_META[notif.type] ?? {
                label: notif.type, color: 'var(--text-muted)', icon: <Bell size={16} />
              };
              return (
                <div
                  key={notif.id}
                  style={{
                    display: 'flex',
                    alignItems: 'flex-start',
                    gap: 16,
                    padding: '14px 0',
                    borderBottom: idx < filtered.length - 1 ? '1px solid var(--border)' : 'none',
                    animation: 'fadeIn 0.3s ease',
                  }}
                >
                  {/* Type icon */}
                  <div style={{
                    width: 36,
                    height: 36,
                    borderRadius: '50%',
                    background: `color-mix(in srgb, ${meta.color} 15%, transparent)`,
                    border: `1px solid color-mix(in srgb, ${meta.color} 30%, transparent)`,
                    display: 'flex',
                    alignItems: 'center',
                    justifyContent: 'center',
                    color: meta.color,
                    flexShrink: 0,
                  }}>
                    {meta.icon}
                  </div>

                  {/* Content */}
                  <div style={{ flex: 1, minWidth: 0 }}>
                    <div style={{ display: 'flex', alignItems: 'center', gap: 8, flexWrap: 'wrap' }}>
                      <span style={{ fontWeight: 600, fontSize: '0.875rem', color: meta.color }}>
                        {meta.label}
                      </span>
                      <span className="mono" style={{ fontSize: '0.75rem', color: 'var(--text-muted)' }}>
                        Trade: {notif.tradeId.slice(0, 12)}…
                      </span>
                    </div>
                    <p style={{ color: 'var(--text-secondary)', fontSize: '0.85rem', marginTop: 3, lineHeight: 1.5 }}>
                      {notif.message}
                    </p>
                    <div style={{ display: 'flex', gap: 16, marginTop: 5, fontSize: '0.76rem', color: 'var(--text-muted)' }}>
                      <span>Account: <strong style={{ color: 'var(--text-secondary)' }}>{notif.accountId}</strong></span>
                      <span title={format(new Date(notif.createdAt), 'PPpp')}>
                        {formatDistanceToNow(new Date(notif.createdAt), { addSuffix: true })}
                      </span>
                    </div>
                  </div>
                </div>
              );
            })}
          </div>
        )}
        {!loading && filtered.length > 0 && (
          <div style={{ marginTop: 16, fontSize: '0.8rem', color: 'var(--text-muted)' }}>
            {filtered.length} event{filtered.length !== 1 ? 's' : ''}
          </div>
        )}
      </div>
    </div>
  );
};

export default Notifications;
