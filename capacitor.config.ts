import type { CapacitorConfig } from '@capacitor/cli';

const config: CapacitorConfig = {
  appId: 'com.fahrmony.app.personal',
  appName: 'Fahrmony',
  webDir: 'dist',
  server: {
    androidScheme: 'https',
  },
};

export default config;
