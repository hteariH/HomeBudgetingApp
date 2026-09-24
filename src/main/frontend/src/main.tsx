import React from 'react';
import ReactDOM from 'react-dom/client';
import './index.css';
import App from './App';
import { bootLocation } from './app/boot';

// Before the router reads the address - see boot.ts.
bootLocation();

ReactDOM.createRoot(document.getElementById('root') as HTMLElement).render(
    <React.StrictMode>
        <App />
    </React.StrictMode>,
);
