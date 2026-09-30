import { useState } from 'react';
import { ScrollView, StyleSheet, Text, View } from 'react-native';
import { PdfView } from 'react-native-better-pdf';
import { Button, colors } from '../ui';

/**
 * A PDF inside a scrolling form, the typical `scrollEnabled={false}` use:
 * dragging on the document scrolls the form. "Inner scroll" turns the
 * document's own scrolling back on.
 */
export function EmbeddedScreen() {
  const [innerScroll, setInnerScroll] = useState(false);
  const [page, setPage] = useState(0);
  return (
    <ScrollView contentContainerStyle={styles.content}>
      <Text style={styles.heading}>Terms and conditions</Text>
      <Text style={styles.body}>
        Please read the document below. Drag on it to scroll this form.
      </Text>
      <View style={styles.row}>
        <Button
          title={innerScroll ? 'Inner scroll on' : 'Inner scroll off'}
          onPress={() => setInnerScroll((value) => !value)}
        />
        <Text style={styles.page}>{`Document page ${page + 1}`}</Text>
      </View>
      <PdfView
        // Metro bundles PDF assets through require().
        // eslint-disable-next-line @typescript-eslint/no-require-imports
        source={require('../assets/sample.pdf')}
        testID="embedded-pdf"
        scrollEnabled={innerScroll}
        onPageChange={(event) => setPage(event.page)}
        style={styles.pdf}
      />
      {Array.from({ length: 6 }, (_, index) => (
        <Text key={index} style={styles.body}>
          {`Form field ${index + 1}`}
        </Text>
      ))}
      <Text style={styles.heading}>End of form</Text>
    </ScrollView>
  );
}

const styles = StyleSheet.create({
  content: { padding: 16, gap: 12 },
  heading: { fontSize: 18, fontWeight: '700', color: colors.text },
  body: { fontSize: 15, color: colors.text, paddingVertical: 12 },
  row: { flexDirection: 'row', alignItems: 'center', gap: 12 },
  page: { fontSize: 14, color: colors.muted, fontVariant: ['tabular-nums'] },
  pdf: { height: 420, backgroundColor: colors.canvas },
});
