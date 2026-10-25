import React from 'react';
import ReactDOM from 'react-dom/client';
import App from './App.tsx';

// Remove Vite's default style reset — we have our own in index.css
ReactDOM.createRoot(document.getElementById('root')!).render(
  <React.StrictMode>
    <App />
  </React.StrictMode>
);
