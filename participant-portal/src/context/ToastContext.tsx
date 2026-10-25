import React, { createContext, useContext, useState, useCallback } from 'react';

export type ToastType = 'success' | 'error' | 'info';

interface Toast {
  id: string;
  type: ToastType;
  title: string;
  body?: string;
}

interface ToastContextValue {
  toast: (type: ToastType, title: string, body?: string) => void;
}

const ToastContext = createContext<ToastContextValue>({ toast: () => {} });

export const useToast = () => useContext(ToastContext);

export const ToastProvider: React.FC<{ children: React.ReactNode }> = ({ children }) => {
  const [toasts, setToasts] = useState<Toast[]>([]);

  const toast = useCallback((type: ToastType, title: string, body?: string) => {
    const id = crypto.randomUUID();
    setToasts((prev) => [...prev, { id, type, title, body }]);
    setTimeout(() => {
      setToasts((prev) => prev.filter((t) => t.id !== id));
    }, 4500);
  }, []);

  return (
    <ToastContext.Provider value={{ toast }}>
      {children}
      <div className="toast-container" role="status" aria-live="polite">
        {toasts.map((t) => (
          <div key={t.id} className={`toast ${t.type}`}>
            <div>
              <p className="toast__title">{t.title}</p>
              {t.body && <p className="toast__body">{t.body}</p>}
            </div>
          </div>
        ))}
      </div>
    </ToastContext.Provider>
  );
};
