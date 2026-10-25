import React, { useState } from 'react';
import { PlayCircle, CheckCircle, Clock } from 'lucide-react';
import { triggerNetting } from '../api/client';
import { useToast } from '../context/ToastContext';
import { format } from 'date-fns';

const Netting: React.FC = () => {
  const [businessDate, setBusinessDate] = useState<string>(format(new Date(), 'yyyy-MM-dd'));
  const [loading, setLoading] = useState(false);
  const { toast } = useToast();

  const handleTriggerNetting = async () => {
    setLoading(true);
    try {
      const response = await triggerNetting(businessDate);
      toast('success', 'Netting Complete', `EOD Netting completed successfully for ${response.businessDate || businessDate}`);
    } catch (error) {
      toast('error', 'Netting Failed', 'Failed to execute the netting process. Please check the logs.');
    } finally {
      setLoading(false);
    }
  };

  return (
    <div>
      <div className="page-header">
        <h1>Netting Operations</h1>
        <p>Trigger end-of-day multilateral netting</p>
      </div>

      <div className="card" style={{ maxWidth: 600, margin: '0 auto', padding: '2rem' }}>
        <div style={{ textAlign: 'center', marginBottom: 32 }}>
          <div style={{
            display: 'inline-flex',
            alignItems: 'center',
            justifyContent: 'center',
            width: 64,
            height: 64,
            borderRadius: '50%',
            background: 'var(--bg-elevated)',
            border: '2px solid var(--brand-primary)',
            color: 'var(--brand-primary)',
            marginBottom: 16
          }}>
            <CheckCircle size={32} />
          </div>
          <h2 style={{ margin: '0 0 8px 0', fontSize: '1.5rem' }}>EOD Netting Cycle</h2>
          <p style={{ color: 'var(--text-muted)', margin: 0, lineHeight: 1.5 }}>
            Running the netting cycle will aggregate all SETTLED trades for the selected business date into net settlement obligations.
          </p>
        </div>

        <div style={{ display: 'flex', flexDirection: 'column', gap: 20 }}>
          <div>
            <label className="form-label" style={{ display: 'flex', alignItems: 'center', gap: 6 }}>
              <Clock size={14} />
              Business Date
            </label>
            <input
              type="date"
              className="form-input"
              value={businessDate}
              onChange={e => setBusinessDate(e.target.value)}
              max={format(new Date(), 'yyyy-MM-dd')}
              style={{ padding: '12px' }}
            />
            <span style={{ fontSize: '0.8rem', color: 'var(--text-muted)', marginTop: 8, display: 'block' }}>
              Select the date to run netting for. It defaults to today.
            </span>
          </div>

          <button
            className="btn btn-primary"
            onClick={handleTriggerNetting}
            disabled={loading || !businessDate}
            style={{ width: '100%', justifyContent: 'center', padding: '12px', fontSize: '1rem' }}
          >
            {loading ? (
              'Processing...'
            ) : (
              <>
                <PlayCircle size={18} />
                Trigger Netting Process
              </>
            )}
          </button>
        </div>
      </div>
    </div>
  );
};

export default Netting;
