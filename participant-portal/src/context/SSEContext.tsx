import React, { createContext, useContext, useEffect, useState } from 'react';
import { useAuth } from './AuthContext';
import { useToast } from './ToastContext';

interface SSEContextType {
  lastEvent: SSEMessage | null;
}

export interface SSEMessage {
  id: string;
  accountId: string;
  tradeId: string;
  message: string;
  status: string;
  createdAt: string;
}

const SSEContext = createContext<SSEContextType | undefined>(undefined);

const GATEWAY_BASE_URL = import.meta.env.VITE_API_BASE_URL || 'http://localhost:8080';

export const SSEProvider: React.FC<{ children: React.ReactNode }> = ({ children }) => {
  const { isAuthenticated, username, token } = useAuth();
  const { toast } = useToast();
  const [lastEvent, setLastEvent] = useState<SSEMessage | null>(null);

  useEffect(() => {
    if (!isAuthenticated || !username || !token) return;

    // Use username as accountId for the stream URL
    const url = `${GATEWAY_BASE_URL}/api/v1/notifications/stream/${username}?access_token=${token}`;
    
    const eventSource = new EventSource(url);

    eventSource.onopen = () => {
      console.log('SSE connection opened for account:', username);
    };

    eventSource.addEventListener('NOTIFICATION', (event: MessageEvent) => {
      try {
        const data: SSEMessage = JSON.parse(event.data);
        console.log('Received real-time notification:', data);
        setLastEvent(data);
        
        // Show a global toast for real-time notifications
        toast('info', 'Real-Time Update', data.message);
      } catch (err) {
        console.error('Failed to parse SSE message', err);
      }
    });

    eventSource.onerror = (err) => {
      console.error('SSE connection error:', err);
      // EventSource automatically attempts to reconnect
    };

    return () => {
      eventSource.close();
      console.log('SSE connection closed for account:', username);
    };
  }, [isAuthenticated, username, token, toast]);

  return (
    <SSEContext.Provider value={{ lastEvent }}>
      {children}
    </SSEContext.Provider>
  );
};

export const useSSE = () => {
  const context = useContext(SSEContext);
  if (context === undefined) {
    throw new Error('useSSE must be used within an SSEProvider');
  }
  return context;
};
