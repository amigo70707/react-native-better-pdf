import { StatusBar } from 'expo-status-bar';
import { useEffect, useState } from 'react';
import { Linking, StyleSheet, View } from 'react-native';
import { SafeAreaProvider, SafeAreaView } from 'react-native-safe-area-context';
import type { Library } from './bench/BenchViewer';
import { BENCHMARK_FIXTURES, type FixtureName } from './documents';
import { BenchmarkScreen, type BenchRequest } from './screens/BenchmarkScreen';
import { EmbeddedScreen } from './screens/EmbeddedScreen';
import { ViewerScreen } from './screens/ViewerScreen';
import { Button, colors } from './ui';

type Tab = 'viewer' | 'embedded' | 'benchmark';

/**
 * `betterpdfexample://bench?library=better&fixture=large&run=3[&scrollSeconds=300][&source=remote]`
 * starts a benchmark run.
 */
function parseBenchLink(url: string | null): BenchRequest | null {
  if (url == null) return null;
  const match = /^[a-z]+:\/\/bench\?(.*)$/.exec(url);
  if (match == null) return null;
  const params = new Map(
    (match[1] ?? '')
      .split('&')
      .map(
        (pair) => pair.split('=').map(decodeURIComponent) as [string, string],
      ),
  );
  const library = params.get('library');
  const fixture = params.get('fixture');
  if (library !== 'better' && library !== 'react-native-pdf') return null;
  if (!BENCHMARK_FIXTURES.some((name) => name === fixture)) return null;
  return {
    library: library satisfies Library,
    fixture: fixture as FixtureName,
    runId: Number(params.get('run') ?? Date.now()),
    scrollWindowMs: params.has('scrollSeconds')
      ? Number(params.get('scrollSeconds')) * 1000
      : undefined,
    remote: params.get('source') === 'remote',
  };
}

export default function App() {
  const [tab, setTab] = useState<Tab>('viewer');
  const [autoRun, setAutoRun] = useState<BenchRequest | null>(null);

  useEffect(() => {
    const handle = (url: string | null) => {
      const request = parseBenchLink(url);
      if (request == null) return;
      setTab('benchmark');
      setAutoRun(request);
    };
    Linking.getInitialURL().then(handle);
    const subscription = Linking.addEventListener('url', (event) =>
      handle(event.url),
    );
    return () => subscription.remove();
  }, []);

  return (
    <SafeAreaProvider>
      <SafeAreaView style={styles.fill} edges={['top', 'bottom']}>
        <StatusBar style="dark" />
        <View style={styles.tabs}>
          <Button
            title="Viewer"
            selected={tab === 'viewer'}
            onPress={() => setTab('viewer')}
          />
          <Button
            title="Embedded"
            selected={tab === 'embedded'}
            onPress={() => setTab('embedded')}
          />
          <Button
            title="Benchmark"
            selected={tab === 'benchmark'}
            onPress={() => setTab('benchmark')}
          />
        </View>
        {tab === 'viewer' && <ViewerScreen />}
        {tab === 'embedded' && <EmbeddedScreen />}
        {tab === 'benchmark' && <BenchmarkScreen autoRun={autoRun} />}
      </SafeAreaView>
    </SafeAreaProvider>
  );
}

const styles = StyleSheet.create({
  fill: { flex: 1, backgroundColor: colors.card },
  tabs: { flexDirection: 'row', gap: 8, padding: 8, justifyContent: 'center' },
});
