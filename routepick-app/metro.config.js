const { getDefaultConfig, mergeConfig } = require('@react-native/metro-config');

const defaultConfig = getDefaultConfig(__dirname);

const config = {
  transformer: {
    getTransformOptions: async () => ({
      transform: {
        experimentalImportSupport: false,
        inlineRequires: true,
      },
    }),
  },
  resolver: {
    alias: {
      '@': './src',
      '@/components': './src/components',
      '@/screens': './src/screens',
      '@/services': './src/services',
      '@/utils': './src/utils',
      '@/navigation': './src/navigation',
      '@/store': './src/store',
      '@/types': './src/types',
      '@/assets': './src/assets',
      '@/config': './src/config',
    },
  },
};

module.exports = mergeConfig(defaultConfig, config);