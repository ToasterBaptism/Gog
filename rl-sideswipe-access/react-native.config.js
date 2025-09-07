module.exports = {
  project: {
    android: {
      packageName: 'com.rlsideswipe.access',
    },
  },
  dependencies: {
    'react-native-permissions': {
      platforms: {
        android: null, // disable Android platform auto linking
      },
    },
    'react-native-vector-icons': {
      platforms: {
        android: null, // disable Android platform auto linking
      },
    },
  },
};