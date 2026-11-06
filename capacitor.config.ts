import type { CapacitorConfig } from '@capacitor/cli';

const config: CapacitorConfig = {
  appId: 'com.rykocollection.app',
  appName: 'RykoCollection',
  webDir: 'dist',
  bundledWebRuntime: false,
  backgroundColor: '#000000',
  android: {
    allowMixedContent: false,
    backgroundColor: '#000000'
  }
};

export default config;
