import { StrictMode } from 'react';
import { createRoot } from 'react-dom/client';
import '@fontsource/mukta/400.css';
import '@fontsource/mukta/500.css';
import '@fontsource/mukta/600.css';
import '@fontsource/mukta/700.css';
import 'material-symbols/rounded.css';
import 'maplibre-gl/dist/maplibre-gl.css';
import './index.css';
import { App } from './App';

createRoot(document.getElementById('root')!).render(
  <StrictMode>
    <App />
  </StrictMode>,
);
