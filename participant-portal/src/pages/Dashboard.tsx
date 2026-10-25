import React, { useEffect, useState } from 'react';
import { TrendingUp, ArrowRightLeft, Layers, Bell, CheckCircle, Clock } from 'lucide-react';
import { fetchTrades, fetchPositions, fetchNotifications } from '../api/client';
import type { Trade, Position, Notification } from '../api/client';
import { formatDistanceToNow } from 'date-fns';

const StatCard: React.FC<{
  title: string;
  value: string | number;
  icon: React.ReactNode;
  color: string;
  delta?: string;
  deltaDir?: 'up' | 'down';
}> = ({ title, value, icon, color, delta, deltaDir }) => (
  <div className="card" style={{ borderTop: `2px solid ${color}` }}>
    <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'flex-start' }}>
      <div>
        <div className="card__title">{title}</div>
        <div className="card__value" style={{ color }}>{value}</div>
        {delta && (
          <div className={`card__delta ${deltaDir ?? 'up'}`}>
            {deltaDir === 'up' ? '↑' : '↓'} {delta}
          </div>
        )}
      </div>
      <div style={{
        padding: 10,
        borderRadius: 10,
        background: `${color}18`,
        color,
      }}>
        {icon}
      </div>
    </div>
  </div>
);

const Dashboard: React.FC = () => {
  const [trades, setTrades] = useState<Trade[]>([]);
  const [positions, setPositions] = useState<Position[]>([]);
  const [notifications, setNotifications] = useState<Notification[]>([]);
  const [loading, setLoading] = useState(true);

  useEffect(() => {
    // Fetch in parallel — degrade gracefully if backend is not running
    Promise.allSettled([
      fetchTrades(),
      fetchPositions(),
      fetchNotifications(),
    ]).then(([tradesRes, positionsRes, notifRes]) => {
      if (tradesRes.status === 'fulfilled') setTrades(tradesRes.value);
      if (positionsRes.status === 'fulfilled') setPositions(positionsRes.value);
      if (notifRes.status === 'fulfilled') setNotifications(notifRes.value);
      setLoading(false);
    });
  }, []);

  const settledCount = trades.filter((t) => t.status === 'SETTLED').length;
  const submittedCount = trades.filter((t) => t.status === 'SUBMITTED').length;
  const successRate = trades.length > 0
    ? ((settledCount / trades.length) * 100).toFixed(1)
    : '—';

  const recentTrades = [...trades]
    .sort((a, b) => new Date(b.createdAt).getTime() - new Date(a.createdAt).getTime())
    .slice(0, 6);

  return (
    <div>
      <div className="page-header">
        <h1>Portfolio Overview</h1>
        <p>Real-time view of your trades, positions and activity</p>
      </div>

      <div className="stats-grid">
        <StatCard
          title="Total Trades"
          value={loading ? '—' : trades.length}
          icon={<ArrowRightLeft size={20} />}
          color="#3b82f6"
          delta="Today"
        />
        <StatCard
          title="Settled"
          value={loading ? '—' : settledCount}
          icon={<CheckCircle size={20} />}
          color="#10b981"
          delta="Completed"
          deltaDir="up"
        />
        <StatCard
          title="Pending"
          value={loading ? '—' : submittedCount}
          icon={<Clock size={20} />}
          color="#f59e0b"
          delta="Awaiting clearing"
        />
        <StatCard
          title="Success Rate"
          value={loading ? '—' : `${successRate}%`}
          icon={<TrendingUp size={20} />}
          color="#6366f1"
          delta="SLO: ≥ 99.9%"
          deltaDir="up"
        />
        <StatCard
          title="Open Positions"
          value={loading ? '—' : positions.length}
          icon={<Layers size={20} />}
          color="#0ea5e9"
          delta="Instruments"
        />
        <StatCard
          title="Notifications"
          value={loading ? '—' : notifications.length}
          icon={<Bell size={20} />}
          color="#a78bfa"
        />
      </div>

      <div className="content-grid">
        {/* Recent trades */}
        <div className="card full-width">
          <div className="section-header">
            <span className="section-title">Recent Trades</span>
          </div>
          {loading ? (
            <div style={{ display: 'flex', flexDirection: 'column', gap: 8 }}>
              {[...Array(5)].map((_, i) => (
                <div key={i} className="skeleton" style={{ height: 42, borderRadius: 6 }} />
              ))}
            </div>
          ) : recentTrades.length === 0 ? (
            <div className="empty-state">
              <ArrowRightLeft size={36} />
              <p>No trades yet. Submit your first trade to get started.</p>
            </div>
          ) : (
            <div className="table-wrapper">
              <table>
                <thead>
                  <tr>
                    <th>Trade ID</th>
                    <th>Instrument</th>
                    <th>Qty</th>
                    <th>Price</th>
                    <th>Status</th>
                    <th>Time</th>
                  </tr>
                </thead>
                <tbody>
                  {recentTrades.map((trade) => (
                    <tr key={trade.id}>
                      <td><span className="mono">{trade.id.slice(0, 8)}…</span></td>
                      <td style={{ fontWeight: 600, color: 'var(--text-primary)' }}>{trade.instrumentId}</td>
                      <td>{trade.quantity.toLocaleString()}</td>
                      <td style={{ fontFamily: 'var(--font-mono)', fontSize: '0.82rem' }}>
                        ${Number(trade.price).toFixed(2)}
                      </td>
                      <td>
                        <span className={`status-badge ${trade.status.toLowerCase()}`}>
                          {trade.status}
                        </span>
                      </td>
                      <td style={{ color: 'var(--text-muted)', fontSize: '0.8rem' }}>
                        {formatDistanceToNow(new Date(trade.createdAt), { addSuffix: true })}
                      </td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          )}
        </div>
      </div>
    </div>
  );
};

export default Dashboard;
