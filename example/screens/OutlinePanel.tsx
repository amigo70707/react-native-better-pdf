import { Pressable, ScrollView, StyleSheet, Text, View } from 'react-native';
import type { PdfOutlineItem } from 'react-native-better-pdf';
import { colors } from '../ui';

/** The document outline as an indented list; entries without a page are dimmed. */
export function OutlinePanel({
  items,
  onSelect,
  onClose,
}: {
  items: PdfOutlineItem[];
  onSelect: (page: number) => void;
  onClose: () => void;
}) {
  const rows: { item: PdfOutlineItem; depth: number }[] = [];
  const flatten = (list: PdfOutlineItem[], depth: number) => {
    for (const item of list) {
      rows.push({ item, depth });
      flatten(item.children, depth + 1);
    }
  };
  flatten(items, 0);

  return (
    <View style={styles.panel} testID="outline">
      <View style={styles.header}>
        <Text style={styles.title}>Contents ({rows.length})</Text>
        <Pressable onPress={onClose} hitSlop={12}>
          <Text style={styles.close}>Close</Text>
        </Pressable>
      </View>
      <ScrollView>
        {rows.length === 0 && (
          <Text style={styles.empty}>This document has no outline.</Text>
        )}
        {rows.map(({ item, depth }, index) => {
          const { page } = item;
          return (
            <Pressable
              key={index}
              disabled={page === undefined}
              accessibilityRole="button"
              accessibilityLabel={item.title}
              accessibilityHint={
                page === undefined ? undefined : `Page ${page + 1}`
              }
              onPress={() => page !== undefined && onSelect(page)}
              style={[styles.row, { paddingLeft: 16 + depth * 18 }]}
            >
              <Text
                style={[styles.rowTitle, page === undefined && styles.dimmed]}
                numberOfLines={1}
              >
                {item.title}
              </Text>
              <Text style={styles.page}>
                {page === undefined ? '–' : page + 1}
              </Text>
            </Pressable>
          );
        })}
      </ScrollView>
    </View>
  );
}

const styles = StyleSheet.create({
  panel: { ...StyleSheet.absoluteFill, backgroundColor: colors.card },
  header: {
    flexDirection: 'row',
    justifyContent: 'space-between',
    alignItems: 'center',
    padding: 16,
    borderBottomWidth: StyleSheet.hairlineWidth,
    borderColor: colors.muted,
  },
  title: { fontSize: 16, fontWeight: '600', color: colors.text },
  close: { fontSize: 15, color: colors.accent },
  empty: { padding: 16, color: colors.muted },
  row: {
    flexDirection: 'row',
    alignItems: 'center',
    paddingVertical: 12,
    paddingRight: 16,
    gap: 12,
  },
  rowTitle: { flex: 1, fontSize: 15, color: colors.text },
  dimmed: { color: colors.muted },
  page: { fontSize: 13, color: colors.muted, fontVariant: ['tabular-nums'] },
});
