import { Pressable, StyleSheet, Text } from 'react-native';

export const colors = {
  canvas: '#E4E4E7',
  card: '#FFFFFF',
  text: '#18181B',
  muted: '#71717A',
  accent: '#2563EB',
  danger: '#DC2626',
};

export function Button({
  title,
  onPress,
  selected = false,
}: {
  title: string;
  onPress: () => void;
  selected?: boolean;
}) {
  return (
    <Pressable
      onPress={onPress}
      style={({ pressed }) => [
        styles.button,
        selected && styles.selected,
        pressed && styles.pressed,
      ]}
    >
      <Text style={[styles.title, selected && styles.selectedTitle]}>
        {title}
      </Text>
    </Pressable>
  );
}

const styles = StyleSheet.create({
  button: { paddingHorizontal: 10, paddingVertical: 7, borderRadius: 8 },
  selected: { backgroundColor: colors.accent },
  pressed: { opacity: 0.5 },
  title: { color: colors.accent, fontSize: 14, fontWeight: '600' },
  selectedTitle: { color: 'white' },
});
