import React, { Component, ReactNode } from 'react';
import { View, Text, StyleSheet, ScrollView } from 'react-native';

interface Props {
  children: ReactNode;
}

interface State {
  hasError: boolean;
  error?: Error;
  errorInfo?: string;
}

class ErrorBoundary extends Component<Props, State> {
  constructor(props: Props) {
    super(props);
    this.state = { hasError: false };
  }

  static getDerivedStateFromError(error: Error): State {
    return { hasError: true, error };
  }

  componentDidCatch(error: Error, errorInfo: any) {
    console.error('ErrorBoundary caught an error:', error, errorInfo);
    this.setState({
      error,
      errorInfo: errorInfo.componentStack,
    });
  }

  render() {
    if (this.state.hasError) {
      return (
        <View style={styles.container}>
          <Text style={styles.title}>Something went wrong</Text>
          <Text style={styles.subtitle}>The app encountered an error</Text>
          
          <ScrollView style={styles.errorContainer}>
            <Text style={styles.errorTitle}>Error Details:</Text>
            <Text style={styles.errorText}>
              {this.state.error?.toString()}
            </Text>
            
            {this.state.errorInfo && (
              <>
                <Text style={styles.errorTitle}>Component Stack:</Text>
                <Text style={styles.errorText}>
                  {this.state.errorInfo}
                </Text>
              </>
            )}
          </ScrollView>
        </View>
      );
    }

    return this.props.children;
  }
}

const styles = StyleSheet.create({
  container: {
    flex: 1,
    backgroundColor: '#f5f5f5',
    padding: 20,
    justifyContent: 'center',
  },
  title: {
    fontSize: 24,
    fontWeight: 'bold',
    color: '#d32f2f',
    textAlign: 'center',
    marginBottom: 10,
  },
  subtitle: {
    fontSize: 16,
    color: '#666',
    textAlign: 'center',
    marginBottom: 20,
  },
  errorContainer: {
    backgroundColor: '#fff',
    borderRadius: 8,
    padding: 15,
    maxHeight: 400,
  },
  errorTitle: {
    fontSize: 16,
    fontWeight: 'bold',
    color: '#333',
    marginTop: 10,
    marginBottom: 5,
  },
  errorText: {
    fontSize: 12,
    color: '#666',
    fontFamily: 'monospace',
  },
});

export default ErrorBoundary;