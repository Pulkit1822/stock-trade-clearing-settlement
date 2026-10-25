import React, { useEffect, useState } from 'react';
import { AlertTriangle, ShieldCheck, DollarSign, RefreshCw } from 'lucide-react';
import { fetchAccounts, fetchMarginStatus, depositCollateral } from '../api/client';
import type { MarginStatus, Account } from '../api/client';
import { useToast } from '../context/ToastContext';
import { format } from 'date-fns';

const Margin: React.FC = () => {
  const [accounts, setAccounts] = useState<Account[]>([]);
  const [selectedAccount, setSelectedAccount] = useState<string>('');
  const [status, setStatus] = useState<MarginStatus | null>(null);
  const [loading, setLoading] = useState(false);
  const [depositAmount, setDepositAmount] = useState<string>('');
  const [depositing, setDepositing] = useState(false);
  const { toast } = useToast();

  useEffect(() => {
    fetchAccounts().then((data) => {
      setAccounts(data);
      if (data.length > 0) {
        setSelectedAccount(data[0].accountId);
      }
    });
  }, []);

  const loadStatus = (accountId: string) => {
    if (!accountId) return;
    setLoading(true);
    fetchMarginStatus(accountId)
      .then(setStatus)
      .catch(() => setStatus(null))
      .finally(() => setLoading(false));
  };

  useEffect(() => {
    if (selectedAccount) {
      loadStatus(selectedAccount);
    }
  }, [selectedAccount]);

  const handleDeposit = async (e: React.FormEvent) => {
    e.preventDefault();
    const amount = parseFloat(depositAmount);
    if (!selectedAccount || isNaN(amount) || amount <= 0) {
      toast('error', 'Invalid Deposit', 'Please enter a valid deposit amount.');
      return;
    }

    setDepositing(true);
    try {
      await depositCollateral(selectedAccount, amount);
      toast('success', 'Collateral Deposited', `Successfully deposited $${amount.toLocaleString()} to ${selectedAccount}`);
      setDepositAmount('');
      loadStatus(selectedAccount);
    } catch (error) {
      toast('error', 'Deposit Failed', 'Failed to deposit collateral. Please try again.');
    } finally {
      setDepositing(false);
    }
  };

  const hasOpenMarginCall = status?.openMarginCalls && status.openMarginCalls.some(mc => mc.status === 'OPEN');
  const totalDeficit = status?.openMarginCalls?.filter(mc => mc.status === 'OPEN').reduce((sum, mc) => sum + mc.deficitAmount, 0) || 0;

  return (
    <div>
      <div className="page-header">
        <h1>Margin & Risk Management</h1>
        <p>Monitor collateral levels and resolve margin calls</p>
      </div>

      <div className="card" style={{ marginBottom: 24 }}>
        <div style={{ display: 'flex', gap: 12, alignItems: 'center' }}>
          <label style={{ fontWeight: 600 }}>Select Account:</label>
          <select
            className="form-select"
            value={selectedAccount}
            onChange={(e) => setSelectedAccount(e.target.value)}
            style={{ maxWidth: 300 }}
          >
            {accounts.map(acc => (
              <option key={acc.accountId} value={acc.accountId}>{acc.accountId}</option>
            ))}
          </select>
          <button className="btn btn-ghost" onClick={() => loadStatus(selectedAccount)} disabled={loading} style={{ marginLeft: 'auto' }}>
            <RefreshCw size={14} className={loading ? 'spin' : ''} />
            Refresh
          </button>
        </div>
      </div>

      {status ? (
        <div className="stats-grid" style={{ marginBottom: 24 }}>
          <div className="card">
            <div className="card__title">Total Collateral</div>
            <div style={{ fontSize: '1.8rem', fontWeight: 700, marginTop: 8, color: 'var(--success)' }}>
              ${status.totalCollateral.toLocaleString(undefined, { minimumFractionDigits: 2 })}
            </div>
          </div>
          <div className="card">
            <div className="card__title">Required Margin</div>
            <div style={{ fontSize: '1.8rem', fontWeight: 700, marginTop: 8 }}>
              ${status.requiredMargin.toLocaleString(undefined, { minimumFractionDigits: 2 })}
            </div>
          </div>
          <div className="card">
            <div className="card__title">Margin Deficit</div>
            <div style={{ fontSize: '1.8rem', fontWeight: 700, marginTop: 8, color: totalDeficit > 0 ? 'var(--danger)' : 'var(--text-primary)' }}>
              ${totalDeficit.toLocaleString(undefined, { minimumFractionDigits: 2 })}
            </div>
          </div>
        </div>
      ) : loading ? (
        <div className="card" style={{ marginBottom: 24 }}>
          <div className="skeleton" style={{ height: 100, borderRadius: 8 }} />
        </div>
      ) : (
        <div className="card" style={{ marginBottom: 24, textAlign: 'center', color: 'var(--text-muted)' }}>
          <p>No margin status available for this account.</p>
        </div>
      )}

      <div style={{ display: 'grid', gridTemplateColumns: '2fr 1fr', gap: 24 }}>
        <div className="card">
          <div style={{ display: 'flex', alignItems: 'center', gap: 8, marginBottom: 16 }}>
            {hasOpenMarginCall ? (
              <AlertTriangle size={20} color="var(--danger)" />
            ) : (
              <ShieldCheck size={20} color="var(--success)" />
            )}
            <h2 style={{ fontSize: '1.1rem', margin: 0 }}>Active Margin Calls</h2>
          </div>

          {status?.openMarginCalls && status.openMarginCalls.length > 0 ? (
            <div className="table-wrapper">
              <table>
                <thead>
                  <tr>
                    <th>Call ID</th>
                    <th>Date Issued</th>
                    <th style={{ textAlign: 'right' }}>Deficit Amount</th>
                    <th>Status</th>
                  </tr>
                </thead>
                <tbody>
                  {status.openMarginCalls.map(mc => (
                    <tr key={mc.id}>
                      <td className="mono">{mc.id.slice(0, 8)}…</td>
                      <td>{format(new Date(mc.createdAt), 'dd MMM yyyy HH:mm')}</td>
                      <td style={{ textAlign: 'right', fontWeight: 600 }}>
                        ${mc.deficitAmount.toLocaleString(undefined, { minimumFractionDigits: 2 })}
                      </td>
                      <td>
                        <span className="badge badge-error">
                          {mc.status}
                        </span>
                      </td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          ) : (
            <div className="empty-state" style={{ padding: '2rem 1rem' }}>
              <ShieldCheck size={32} color="var(--success)" style={{ opacity: 0.5 }} />
              <p>Account is in good standing. No active margin calls.</p>
            </div>
          )}
        </div>

        <div className="card" style={{ height: 'fit-content' }}>
          <h2 style={{ fontSize: '1.1rem', margin: '0 0 16px 0', display: 'flex', alignItems: 'center', gap: 8 }}>
            <DollarSign size={20} />
            Deposit Collateral
          </h2>
          <form onSubmit={handleDeposit} style={{ display: 'flex', flexDirection: 'column', gap: 16 }}>
            <div>
              <label className="form-label">Deposit Amount ($)</label>
              <input
                type="number"
                className="form-input"
                min="0.01"
                step="0.01"
                placeholder="100000.00"
                value={depositAmount}
                onChange={e => setDepositAmount(e.target.value)}
                required
              />
            </div>
            <button
              type="submit"
              className="btn btn-primary"
              disabled={depositing || !depositAmount}
              style={{ width: '100%', justifyContent: 'center' }}
            >
              {depositing ? 'Processing...' : 'Submit Deposit'}
            </button>
          </form>
        </div>
      </div>
    </div>
  );
};

export default Margin;
