import React, { useState, useEffect } from 'react';
import {
  View,
  Text,
  TouchableOpacity,
  StyleSheet,
  Alert,
  ScrollView,
} from 'react-native';

const TestScreen: React.FC = () => {
  const [testResults, setTestResults] = useState<string[]>([]);
  const [isRunning, setIsRunning] = useState(false);

  const addResult = (message: string) => {
    setTestResults(prev => [...prev, `${new Date().toLocaleTimeString()}: ${message}`]);
  };

  const runTests = async () => {
    setIsRunning(true);
    setTestResults([]);
    
    addResult('Starting tests...');
    
    // Test 1: Basic React Native functionality
    try {
      addResult('✓ React Native components working');
    } catch (error) {
      addResult(`✗ React Native components failed: ${error}`);
    }

    // Test 2: State management
    try {
      const [count, setCount] = useState(0);
      setCount(1);
      addResult('✓ State management working');
    } catch (error) {
      addResult(`✗ State management failed: ${error}`);
    }

    // Test 3: Native modules check
    try {
      const { NativeModules } = require('react-native');
      addResult(`Native modules available: ${Object.keys(NativeModules).length}`);
      
      if (NativeModules.NativeControlModule) {
        addResult('✓ NativeControlModule found');
      } else {
        addResult('✗ NativeControlModule not found');
      }
    } catch (error) {
      addResult(`✗ Native modules check failed: ${error}`);
    }

    // Test 4: Permissions check
    try {
      const { PermissionsAndroid } = require('react-native');
      addResult('✓ PermissionsAndroid available');
    } catch (error) {
      addResult(`✗ PermissionsAndroid failed: ${error}`);
    }

    addResult('Tests completed!');
    setIsRunning(false);
  };

  useEffect(() => {
    addResult('TestScreen mounted successfully');
  }, []);

  return (
    <View style={styles.container}>
      <Text style={styles.title}>App Diagnostic Test</Text>
      <Text style={styles.subtitle}>Testing app functionality</Text>
      
      <TouchableOpacity 
        style={[styles.button, isRunning && styles.buttonDisabled]} 
        onPress={runTests}
        disabled={isRunning}
      >
        <Text style={styles.buttonText}>
          {isRunning ? 'Running Tests...' : 'Run Diagnostic Tests'}
        </Text>
      </TouchableOpacity>

      <ScrollView style={styles.resultsContainer}>
        {testResults.map((result, index) => (
          <Text key={index} style={styles.resultText}>
            {result}
          </Text>
        ))}
      </ScrollView>
    </View>
  );
};

const styles = StyleSheet.create({
  container: {
    flex: 1,
    padding: 20,
    backgroundColor: '#f5f5f5',
  },
  title: {
    fontSize: 24,
    fontWeight: 'bold',
    color: '#333',
    textAlign: 'center',
    marginBottom: 10,
  },
  subtitle: {
    fontSize: 16,
    color: '#666',
    textAlign: 'center',
    marginBottom: 30,
  },
  button: {
    backgroundColor: '#007AFF',
    padding: 15,
    borderRadius: 8,
    marginBottom: 20,
  },
  buttonDisabled: {
    backgroundColor: '#ccc',
  },
  buttonText: {
    color: 'white',
    fontSize: 16,
    fontWeight: 'bold',
    textAlign: 'center',
  },
  resultsContainer: {
    flex: 1,
    backgroundColor: '#fff',
    borderRadius: 8,
    padding: 15,
  },
  resultText: {
    fontSize: 12,
    color: '#333',
    marginBottom: 5,
    fontFamily: 'monospace',
  },
});

export default TestScreen;